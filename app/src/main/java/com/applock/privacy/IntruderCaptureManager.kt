package com.applock.privacy

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.BatteryManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.applock.R
import com.applock.core.database.IntruderEventDao
import com.applock.core.database.IntruderEventEntity
import com.applock.core.database.SecurityEventDao
import com.applock.core.database.SecurityEventEntity
import com.applock.core.database.SecurityEventType
import com.applock.core.security.EncryptedFileStore
import com.applock.ui.MainActivity
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Intruder selfie (FR-081) + intruder event log (FR-082) + local owner
 * notification (FR-084). Called by the lock engine on every authentication
 * failure; [IntruderPolicy] decides which failures become events.
 *
 * The photo is taken with the front camera and no visible camera UI
 * (an [ImageCapture] use case bound to a throwaway lifecycle — no preview
 * surface exists at any point). JPEG bytes go straight from camera memory
 * into an [EncryptedFileStore] stream, so no plaintext image ever touches
 * disk. Camera failure (denied permission, hardware busy, emulator quirks)
 * degrades to an event row without a photo — logging must never depend on
 * the camera (FR-082).
 */
class IntruderCaptureManager(
    private val context: Context,
    private val policy: IntruderPolicy,
    private val intruderEventDao: IntruderEventDao,
    private val securityEventDao: SecurityEventDao,
    private val fileStore: EncryptedFileStore,
    private val scope: CoroutineScope,
) {

    private val captureInFlight = AtomicBoolean(false)

    /** Invoked after every failed unlock; no-ops unless the policy fires. */
    fun onAuthFailure(packageName: String?, authMethod: String, consecutiveFailures: Int) {
        if (!policy.shouldCapture(consecutiveFailures)) return
        if (!captureInFlight.compareAndSet(false, true)) return
        scope.launch {
            try {
                recordIntruderEvent(packageName, authMethod, consecutiveFailures)
            } finally {
                captureInFlight.set(false)
            }
        }
    }

    private suspend fun recordIntruderEvent(
        packageName: String?,
        authMethod: String,
        failedAttempts: Int,
    ) {
        val photoName = try {
            capturePhoto()
        } catch (e: Exception) {
            Log.w(TAG, "Intruder photo capture failed — logging event without photo", e)
            null
        }
        intruderEventDao.insert(
            IntruderEventEntity(
                packageName = packageName,
                authMethod = authMethod,
                failedAttempts = failedAttempts,
                batteryPercent = batteryPercent(),
                orientation = orientationLabel(),
                photoFileName = photoName,
            )
        )
        securityEventDao.insert(
            SecurityEventEntity(
                eventType = SecurityEventType.INTRUDER_CAPTURED,
                packageName = packageName,
            )
        )
        postOwnerNotification()
    }

    /** Returns the encrypted blob name, or null when capture isn't possible. */
    private suspend fun capturePhoto(): String? {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Log.w(TAG, "CAMERA permission not granted — skipping photo")
            return null
        }
        val jpeg = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) { captureJpegBytes() } ?: return null
        val name = "intruder_${System.currentTimeMillis()}.jpg"
        withContext(Dispatchers.IO) {
            fileStore.openOutput(PHOTO_DIR, name).use { it.write(jpeg) }
        }
        return name
    }

    /** Camera binding and capture must run on the main thread. */
    private suspend fun captureJpegBytes(): ByteArray = withContext(Dispatchers.Main) {
        val provider = awaitCameraProvider()
        val selector = when {
            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                CameraSelector.DEFAULT_FRONT_CAMERA
            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                CameraSelector.DEFAULT_BACK_CAMERA
            else -> throw IllegalStateException("no camera available")
        }
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        val lifecycle = EphemeralLifecycleOwner()
        try {
            provider.bindToLifecycle(lifecycle, selector, imageCapture)
            takePicture(imageCapture)
        } finally {
            provider.unbindAll()
            lifecycle.destroy()
        }
    }

    private suspend fun awaitCameraProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }

    private suspend fun takePicture(imageCapture: ImageCapture): ByteArray =
        suspendCancellableCoroutine { cont ->
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        // CAPTURE_MODE JPEG output: single plane of JPEG bytes.
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        image.close()
                        cont.resume(bytes)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        cont.resumeWithException(exception)
                    }
                },
            )
        }

    private fun batteryPercent(): Int =
        context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

    private fun orientationLabel(): String =
        if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            "LANDSCAPE"
        } else {
            "PORTRAIT"
        }

    private fun postOwnerNotification() {
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_INTRUDER,
                context.getString(R.string.intruder_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
        val openApp = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_INTRUDER)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.intruder_notification_title))
            .setContentText(context.getString(R.string.intruder_notification_body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { notifications.notify(INTRUDER_NOTIFICATION_ID, notification) }
    }

    /** Decrypts an intruder photo into memory for the history viewer (FR-085). */
    suspend fun decodePhoto(fileName: String, maxDimension: Int = 1024) =
        withContext(Dispatchers.IO) { fileStore.decodeBitmap(PHOTO_DIR, fileName, maxDimension) }

    fun deletePhoto(fileName: String) {
        fileStore.delete(PHOTO_DIR, fileName)
    }

    /**
     * Camera binding needs a resumed [LifecycleOwner]; outside an activity we
     * provide a private one that lives exactly as long as one capture.
     */
    private class EphemeralLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this).apply {
            currentState = Lifecycle.State.RESUMED
        }

        override val lifecycle: Lifecycle get() = registry

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    companion object {
        private const val TAG = "IntruderCapture"
        private const val PHOTO_DIR = "intruder"
        private const val CHANNEL_INTRUDER = "intruder_alerts"
        private const val INTRUDER_NOTIFICATION_ID = 3
        private const val CAPTURE_TIMEOUT_MS = 10_000L
    }
}
