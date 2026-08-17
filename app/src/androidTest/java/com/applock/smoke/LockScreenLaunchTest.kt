package com.applock.smoke

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.applock.R
import com.applock.presentation.authentication.LockScreenActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WP8 (M1) smoke: the lock screen launches when handed its target-package extra.
 *
 * [LockScreenActivity] finishes immediately unless it is started through the `createIntent()`
 * factory that carries EXTRA_TARGET_PACKAGE — that is its launch contract. Given the extra it must
 * stay resumed (not self-finish) and render the PIN prompt. Lockout counters are cleared first so
 * the countdown branch can't replace the prompt, and no biometrics are enrolled on the emulator so
 * the PIN pad is what shows.
 */
@RunWith(AndroidJUnit4::class)
class LockScreenLaunchTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearLockout() {
        context.deleteSharedPreferences("applock_lockout")
    }

    @Test
    fun lockScreenWithExtraStaysResumedAndShowsPinPrompt() {
        val intent = LockScreenActivity.createIntent(context, "com.android.settings")
        ActivityScenario.launch<LockScreenActivity>(intent).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { assertFalse("must not self-finish when given the extra", it.isFinishing) }
            compose.onNodeWithText(context.getString(R.string.enter_pin)).assertIsDisplayed()
        }
    }
}
