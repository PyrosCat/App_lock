package com.applock.e2e

import android.app.Activity

/**
 * A second home launcher for `HomeResolverDeviceTest` (M7 WP2 change F3). The test makes App Lock the default
 * launcher through this activity, to check that a change of default is visible to the resolver. It has no UI.
 *
 * It lives in the **debug** target source set, like [SentinelActivity]. The debug manifest declares it disabled, so
 * it adds no HOME candidate. The test runs in the target process, so it can enable its own component without a
 * shell permission (on a user build, the shell may change component states only in test-only packages). It ships
 * only in debuggable builds.
 */
class FakeHomeActivity : Activity()
