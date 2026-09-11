package com.applock.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/** Guards the intentional Compose naming convention from becoming a broad Detekt exemption. */
class DetektPolicyTest {

    @Test
    fun `Compose naming exception stays narrow and out of the baseline`() {
        val root = repositoryRoot()
        val configLines = Files.readAllLines(root.resolve("config/detekt/detekt.yml"))
        val functionNaming = yamlBlock(configLines, "  FunctionNaming:")

        val activation = functionNaming.map(String::trim).filter { it.startsWith("active:") }
        assertEquals("FunctionNaming must remain explicitly enabled", listOf("active: true"), activation)

        val ignoredAnnotations = yamlBlock(functionNaming, "    ignoreAnnotated:")
            .map(String::trim)
            .filter { it.startsWith("- ") }
            .map { it.removePrefix("- ") }
        assertEquals(
            "Only @Composable may bypass ordinary lower-camel-case function naming",
            listOf("Composable"),
            ignoredAnnotations,
        )

        val baselineLines = Files.readAllLines(root.resolve("config/detekt/baseline.xml"))
        val namingDebt = baselineLines.filter { "<ID>FunctionNaming:" in it }
        assertTrue(
            "FunctionNaming entries must not return to the Detekt baseline:\n" +
                namingDebt.joinToString("\n"),
            namingDebt.isEmpty(),
        )
    }

    private fun yamlBlock(lines: List<String>, header: String): List<String> {
        val start = lines.indexOf(header)
        require(start >= 0) { "Missing Detekt configuration block: $header" }
        val headerIndent = header.indexOfFirst { !it.isWhitespace() }
        return lines.drop(start + 1).takeWhile { line ->
            line.isBlank() || line.indexOfFirst { !it.isWhitespace() } > headerIndent
        }
    }

    private fun repositoryRoot(): Path =
        generateSequence(Path.of("").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { candidate ->
                Files.isRegularFile(candidate.resolve("settings.gradle.kts")) &&
                    Files.isRegularFile(candidate.resolve("config/detekt/detekt.yml"))
            }
            ?: error("Cannot locate repository root for Detekt policy test")
}
