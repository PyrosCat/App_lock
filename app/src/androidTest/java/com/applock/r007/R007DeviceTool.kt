package com.applock.r007

/**
 * Marks an R-007 device tool: an instrumentation class that the host controller runs on request, not a test of the
 * app. CI excludes it with `notAnnotation`, and each tool also skips itself unless the host passes its argument.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class R007DeviceTool
