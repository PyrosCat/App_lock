package com.applock.security.faultinjection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks that the script names that the host controller accepts (scripts/r007/lib_r007.sh) are the names that
 * [DeviceFaultScript] accepts. A name that only the host accepts would pass host validation and then be ignored as
 * an invalid script on the device.
 */
class FaultScriptHostListTest {

    private val library: String by lazy {
        // Gradle runs unit tests with the module directory (app/) as the working directory.
        val file = listOf(File("../scripts/r007/lib_r007.sh"), File("scripts/r007/lib_r007.sh"))
            .firstOrNull { it.isFile }
        checkNotNull(file) { "scripts/r007/lib_r007.sh not found from ${File(".").absolutePath}" }.readText()
    }

    private fun hostList(name: String): Set<String> {
        val line = Regex("^$name=\"([^\"]*)\"$", RegexOption.MULTILINE).find(library)
        checkNotNull(line) { "$name is not defined in lib_r007.sh" }
        return line.groupValues[1].split(" ").filter { it.isNotEmpty() }.toSet()
    }

    @Test
    fun `the host read scripts are the device read scripts`() {
        assertEquals(DeviceReadScript.entries.map { it.name }.toSet(), hostList("R007_READ_SCRIPTS"))
    }

    @Test
    fun `the host write scripts are the device write scripts`() {
        assertEquals(DeviceWriteScript.entries.map { it.name }.toSet(), hostList("R007_WRITE_SCRIPTS"))
    }

    @Test
    fun `the host hold continuations are the continuations that the parser accepts`() {
        val continuations = hostList("R007_HOLD_CONTINUATIONS")
        DeviceWriteScript.entries.forEach { then ->
            val accepted = runCatching { DeviceFaultScript.parse("WRITE 0 HoldBeforeCommit ${then.name}") }.isSuccess
            assertEquals("continuation ${then.name}", accepted, then.name in continuations)
        }
        assertTrue(continuations.isNotEmpty())
    }
}
