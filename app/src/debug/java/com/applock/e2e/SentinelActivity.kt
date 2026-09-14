package com.applock.e2e

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A full-screen touch recorder for the Gate-2 underlying-touch check (M7 WP2 change E). The overlay lock
 * window is a `TYPE_APPLICATION_OVERLAY` drawn ABOVE this activity. With a real touch, not an inference from
 * window focus, the test proves that an interactive overlay BLOCKS touches to the app beneath and a dismissed
 * (pass-through) overlay lets them through.
 *
 * It lives in the **debug** target source set (packaged in the `com.applock` debug APK), NOT in the androidTest
 * APK: `ActivityScenario.launch(Class)` builds its intent against the target-app context, so the Activity must
 * be declared under `com.applock` to resolve. Being in the target APK also keeps its static [touched] flag in
 * the same process the instrumentation reads. It ships only in debuggable builds (never released).
 */
class SentinelActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val recorder = View(this).apply {
            setOnTouchListener { view, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) touched.set(true)
                view.performClick() // satisfy the accessibility contract; the touch is still consumed
                true
            }
        }
        setContentView(recorder)
    }

    companion object {
        /** Set when a touch reaches the underlying activity. The test resets it before each injection. */
        val touched = AtomicBoolean(false)
    }
}
