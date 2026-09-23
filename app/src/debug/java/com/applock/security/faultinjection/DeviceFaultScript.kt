package com.applock.security.faultinjection

/** The scripted behaviour of one lockout-store read on a device. */
enum class DeviceReadScript {
    /** Reads the real store. */
    Normal,

    /** Throws before it reads, as a decryption or keystore fault does. */
    Throw,

    /** Waits for a release file, then reads the real store. */
    HoldThenRead,

    /** Waits for a release file, then throws. */
    HoldThenThrow,

    /** Reads the real store, then waits for a release file before it returns the value. */
    ReadThenHold,
}

/** The scripted behaviour of one lockout-store write on a device. */
enum class DeviceWriteScript {
    /** Writes to the real store and returns its result. */
    Normal,

    /**
     * Returns false without a call to the real store. A real disk failure also changes the in-process preferences
     * cache; this injected failure changes nothing. The real-platform case is a file fault, for example a read-only
     * preferences directory.
     */
    ReturnFalseBeforeCommit,

    /** Throws without a call to the real store, as an encryption fault in the editor does. */
    Throw,

    /** Waits for a release file, then continues with the script that follows it on the same line. */
    HoldBeforeCommit,

    /** Writes to the real store, then waits for a release file before it returns the result. */
    CommitThenHold,

    /** Writes to the real store, then returns false: an injected acknowledgement ambiguity. */
    CommitThenReportFalse,
}

/** One write rule. [then] is the continuation of a [DeviceWriteScript.HoldBeforeCommit]; it is null otherwise. */
data class WriteRule(val script: DeviceWriteScript, val then: DeviceWriteScript? = null)

/**
 * A device fault script, parsed from the text of `files/r007/faults`. Each line is one rule:
 *
 * ```
 * READ  <index|*> <DeviceReadScript>
 * WRITE <index|*> <DeviceWriteScript> [<DeviceWriteScript after a HoldBeforeCommit>]
 * ```
 *
 * The index counts the operations of one kind in one process, from 0. A rule for an exact index takes precedence
 * over a rule for `*`. A blank line or a line that starts with `#` is ignored. An operation without a rule is
 * [DeviceReadScript.Normal] or [DeviceWriteScript.Normal].
 */
class DeviceFaultScript private constructor(
    private val reads: Map<Int, DeviceReadScript>,
    private val writes: Map<Int, WriteRule>,
) {
    fun read(index: Int): DeviceReadScript = reads[index] ?: reads[ANY] ?: DeviceReadScript.Normal

    fun write(index: Int): WriteRule = writes[index] ?: writes[ANY] ?: WriteRule(DeviceWriteScript.Normal)

    companion object {
        private const val ANY = -1

        val EMPTY = DeviceFaultScript(emptyMap(), emptyMap())

        /** Parses [text]. Throws [IllegalArgumentException] that names the line of the first invalid rule. */
        fun parse(text: String): DeviceFaultScript {
            val reads = HashMap<Int, DeviceReadScript>()
            val writes = HashMap<Int, WriteRule>()
            text.lines().forEachIndexed { number, raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
                val fields = line.split(Regex("\\s+"))
                val lineNumber = number + 1
                require(fields.size in 3..4) { "line $lineNumber: expected 3 or 4 fields: $line" }
                val index = parseIndex(fields[1], lineNumber)
                when (fields[0]) {
                    "READ" -> {
                        require(fields.size == 3) { "line $lineNumber: a READ rule has 3 fields: $line" }
                        reads[index] = parseEnum<DeviceReadScript>(fields[2], lineNumber)
                    }
                    "WRITE" -> writes[index] = parseWriteRule(fields, lineNumber)
                    else -> throw IllegalArgumentException("line $lineNumber: unknown operation ${fields[0]}")
                }
            }
            return DeviceFaultScript(reads, writes)
        }

        private fun parseWriteRule(fields: List<String>, lineNumber: Int): WriteRule {
            val script = parseEnum<DeviceWriteScript>(fields[2], lineNumber)
            if (script != DeviceWriteScript.HoldBeforeCommit) {
                require(fields.size == 3) { "line $lineNumber: only HoldBeforeCommit takes a continuation" }
                return WriteRule(script)
            }
            val then = if (fields.size == 4) parseEnum(fields[3], lineNumber) else DeviceWriteScript.Normal
            require(then != DeviceWriteScript.HoldBeforeCommit && then != DeviceWriteScript.CommitThenHold) {
                "line $lineNumber: a hold continues with a script that does not hold again"
            }
            return WriteRule(script, then)
        }

        private fun parseIndex(field: String, lineNumber: Int): Int =
            if (field == "*") {
                ANY
            } else {
                requireNotNull(field.toIntOrNull()?.takeIf { it >= 0 }) {
                    "line $lineNumber: the index is a number from 0, or *: $field"
                }
            }

        private inline fun <reified T : Enum<T>> parseEnum(field: String, lineNumber: Int): T =
            requireNotNull(enumValues<T>().firstOrNull { it.name == field }) {
                "line $lineNumber: unknown ${T::class.simpleName} $field"
            }
    }
}
