package com.applock.smoke

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.applock.R
import com.applock.presentation.applist.MainActivity
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WP8 (M1) smoke: a fresh install launches straight to PIN setup.
 *
 * The on-device counterpart to the JVM navigation logic — it proves the real Hilt graph builds,
 * [MainActivity] inflates its Compose UI, and with no PIN stored the first screen is PIN setup
 * (not the self-gate). Credentials are cleared first so the assertion is independent of any state
 * a previous test or launch left behind.
 */
@RunWith(AndroidJUnit4::class)
class PinSetupLaunchTest {

    // Empty rule (not createAndroidComposeRule<MainActivity>()): we clear credentials and grant the
    // notification permission in @Before, then launch the activity ourselves inside the test, so the
    // clean-state setup runs *before* MainActivity reads isPinSet() in onCreate.
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun freshInstallState() {
        context.deleteSharedPreferences("applock_credentials")
        // POST_NOTIFICATIONS is a runtime permission from API 33; grant it silently so MainActivity's
        // request dialog can't steal focus from the Compose content. It does not exist on older levels.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @Test
    fun freshInstallLandsOnPinSetup() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText(context.getString(R.string.set_pin_title)).assertIsDisplayed()
        }
    }
}
