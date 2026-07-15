package com.applock.core.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Keystore-backed encrypted blob storage under private app storage
 * (FR-081 photo storage, FR-107 vault encryption). Each blob is an
 * [EncryptedFile] (AES-256-GCM streaming) inside `filesDir/<dir>/`; callers
 * choose the directory so vault payloads and intruder photos stay separate.
 *
 * Plaintext only ever exists in the streams handed to callers — nothing
 * decrypted is written to disk.
 */
class EncryptedFileStore(private val context: Context) {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    /**
     * Opens a stream that encrypts everything written to it. Any half-written
     * file from a previous crash is replaced ([EncryptedFile] refuses to
     * overwrite, so stale files are deleted first).
     */
    fun openOutput(dir: String, fileName: String): OutputStream {
        val file = fileFor(dir, fileName)
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        return encryptedFile(file).openFileOutput()
    }

    fun openInput(dir: String, fileName: String): InputStream =
        encryptedFile(fileFor(dir, fileName)).openFileInput()

    fun exists(dir: String, fileName: String): Boolean = fileFor(dir, fileName).exists()

    fun sizeOnDisk(dir: String, fileName: String): Long = fileFor(dir, fileName).length()

    /** FR-115: removing the ciphertext is the secure delete — without the
     *  Keystore-wrapped key the bytes are unrecoverable anyway. */
    fun delete(dir: String, fileName: String): Boolean = fileFor(dir, fileName).delete()

    /**
     * Decodes an encrypted image entirely in memory (FR-118 preview,
     * FR-085 intruder photos). Two sequential passes: bounds first, then a
     * decode subsampled to stay near [maxDimension] so full-camera JPEGs
     * don't exhaust the heap. Returns null for unreadable/non-image blobs.
     */
    fun decodeBitmap(dir: String, fileName: String, maxDimension: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openInput(dir, fileName).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sampleSize = 1
            while (
                bounds.outWidth / (sampleSize * 2) >= maxDimension ||
                bounds.outHeight / (sampleSize * 2) >= maxDimension
            ) {
                sampleSize *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            openInput(dir, fileName).use { BitmapFactory.decodeStream(it, null, options) }
        }
    } catch (e: Exception) {
        null
    }

    private fun fileFor(dir: String, fileName: String): File {
        // Names are app-generated (UUIDs/timestamps), but never trust them
        // enough to escape the store directory.
        require(!fileName.contains('/') && !fileName.contains('\\') && fileName != "..") {
            "invalid blob name"
        }
        return File(File(context.filesDir, dir), fileName)
    }

    private fun encryptedFile(file: File): EncryptedFile =
        EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()
}
