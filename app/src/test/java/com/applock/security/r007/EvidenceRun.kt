package com.applock.security.r007

import org.junit.runner.Description
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * The evidence run of this test JVM. The run owns a new directory under `build/r007-evidence/p2-jvm` in the module
 * directory. The directory name holds the UTC start time and a random suffix. `run.txt` in it holds the run header.
 *
 * At the first use, the run writes a snapshot of the tested source tree into its directory:
 * - `source.diff`: the output of `git diff HEAD --binary`, the staged and unstaged changes to tracked files.
 * - `untracked.sha256`: each untracked file that git does not ignore, with its SHA-256.
 * - `untracked/`: a copy of each of those files under `app/src/`.
 *
 * The source fingerprint is a SHA-256 over HEAD, the diff, and that list. An edit to a tracked file or to a
 * non-ignored untracked file changes it. Every git command has a time limit. Without git, the identity fields read
 * "unknown".
 */
internal object EvidenceRun {
    private const val UNKNOWN = "unknown"
    private const val GIT_TIMEOUT_S = 20L
    private const val NAME_CHARS = 60
    private val UNSAFE_NAME = Regex("[^A-Za-z0-9._-]")

    private class SourceIdentity(val rev: String, val tree: String, val fingerprint: String)

    private class Run(val dir: File, val identity: SourceIdentity)

    private val run: Run by lazy {
        val start = Instant.now()
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(start)
        val runId = "$stamp-%08x".format(Random.nextInt())
        val runDir = File("build/r007-evidence/p2-jvm/$runId").apply { mkdirs() }
        val identity = snapshotSource(runDir)
        File(runDir, "run.txt").writeText(
            buildString {
                appendLine("# R-007 P2 JVM evidence run")
                appendLine("# run=$runId")
                appendLine("# utc_start=$start")
                appendLine("# source_rev=${identity.rev}")
                appendLine("# source_tree=${identity.tree}")
                appendLine("# source_fingerprint=${identity.fingerprint}")
                appendLine("# java=${System.getProperty("java.version")} ${System.getProperty("java.vendor")}")
                appendLine("# os=${System.getProperty("os.name")} ${System.getProperty("os.version")}")
            },
        )
        Run(runDir, identity)
    }

    /** The source lines that each evidence file of this run repeats. They hold no time, so reruns of a tree match. */
    fun sourceLines(): List<String> = listOf(
        "# source_rev=${run.identity.rev}",
        "# source_tree=${run.identity.tree}",
        "# source_fingerprint=${run.identity.fingerprint}",
    )

    /**
     * The evidence file of one test: `<class>/<start of the method name>-<hash of the method name>.txt`. The hash keeps
     * the names unique and the path short.
     */
    fun fileFor(description: Description): File {
        val method = description.methodName
        val name = method.replace(UNSAFE_NAME, "_").take(NAME_CHARS) + "-%08x".format(method.hashCode())
        return File(run.dir, description.testClass.simpleName).apply { mkdirs() }.resolve("$name.txt")
    }

    private fun snapshotSource(runDir: File): SourceIdentity {
        val root = git(File("."), "rev-parse", "--show-toplevel")?.let { File(it.toString(Charsets.UTF_8).trim()) }
            ?: return SourceIdentity(UNKNOWN, UNKNOWN, UNKNOWN)
        val rev = git(root, "rev-parse", "HEAD")?.toString(Charsets.UTF_8)?.trim() ?: UNKNOWN
        val diff = git(root, "diff", "HEAD", "--binary")
        val untrackedList = git(root, "ls-files", "--others", "--exclude-standard", "-z")
        if (diff == null || untrackedList == null) return SourceIdentity(rev, UNKNOWN, UNKNOWN)

        File(runDir, "source.diff").writeBytes(diff)
        val untracked = untrackedList.toString(Charsets.UTF_8).split('\u0000').filter { it.isNotEmpty() }.sorted()
        val hashLines = untracked.map { path ->
            val file = File(root, path)
            if (path.startsWith("app/src/")) file.copyTo(File(runDir, "untracked/$path"), overwrite = true)
            "${sha256(file.readBytes())}  $path"
        }
        File(runDir, "untracked.sha256").writeText(hashLines.joinToString("") { "$it\n" })

        val tree = if (diff.isEmpty() && untracked.isEmpty()) "clean" else "dirty"
        val fingerprint = sha256("$rev\n${sha256(diff)}\n${hashLines.joinToString("\n")}".toByteArray())
        return SourceIdentity(rev, tree, fingerprint)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // Runs one git command in [workDir] and returns its stdout, or null when git fails, is missing, or does not end
    // within the time limit. The output goes to temporary files, so no pipe can fill and stall the command, and a
    // command past the limit is killed.
    @Suppress("SwallowedException") // without git the evidence names the source "unknown"
    private fun git(workDir: File, vararg args: String): ByteArray? {
        val stdoutFile = File.createTempFile("r007-git", ".out")
        val stderrFile = File.createTempFile("r007-git", ".err")
        return try {
            val process = ProcessBuilder(listOf("git") + args)
                .directory(workDir)
                .redirectOutput(stdoutFile)
                .redirectError(stderrFile)
                .start()
            if (!process.waitFor(GIT_TIMEOUT_S, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                null
            } else if (process.exitValue() != 0) {
                null
            } else {
                stdoutFile.readBytes()
            }
        } catch (e: IOException) {
            null
        } finally {
            stdoutFile.delete()
            stderrFile.delete()
        }
    }
}
