package com.applock.e2e

/**
 * The mode of an app-op as read from `appops get`, distinguishing a KNOWN mode from an UNREADABLE result
 * (M7 WP2 change E, round 9). The Gate-2 suites snapshot the overlay app-op before forcing it to `allow` and
 * restore it on teardown; conflating an unreadable result with a real "default" would let a shell error or an
 * unexpected OEM format revoke an existing device grant on restore. Callers must NOT mutate/restore the op
 * unless the snapshot is [Known].
 */
sealed interface AppOpMode {
    data class Known(val mode: String) : AppOpMode
    data object Unknown : AppOpMode
}

/**
 * Parses `appops get <pkg> <op>` output for the single op's PACKAGE mode. `appops` prints the op via its
 * uppercase debug name (e.g. `SYSTEM_ALERT_WINDOW: allow`), so [opShortName] (e.g. `system_alert_window`) is
 * matched case-INSENSITIVELY; a `Uid mode:` line is skipped so the package mode that `appops set` restores is
 * the one captured. An explicit `No operations` response means the op is unset, i.e. the default mode
 * ([AppOpMode.Known] `"default"`). Empty output, a shell error, or an unrecognized format is [AppOpMode.Unknown],
 * so a caller never mistakes an unreadable result for a real `"default"` and clobbers an existing grant.
 */
fun parseAppOpMode(opShortName: String, output: String): AppOpMode {
    val trimmed = output.trim()
    if (trimmed.isEmpty()) return AppOpMode.Unknown
    if (trimmed.contains("No operations", ignoreCase = true)) return AppOpMode.Known("default")
    val regex = Regex("${Regex.escape(opShortName)}:\\s*(\\w+)", RegexOption.IGNORE_CASE)
    val mode = output.lineSequence()
        .filterNot { it.trimStart().startsWith("Uid mode:", ignoreCase = true) }
        .firstNotNullOfOrNull { regex.find(it)?.groupValues?.get(1) }
    return if (mode != null) AppOpMode.Known(mode) else AppOpMode.Unknown
}
