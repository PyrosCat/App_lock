package com.applock.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M1 architecture rules — ADR-016 (Konsist). These run in the ordinary unit-test job.
 *
 * R1 + R3 carry frozen baselines (freeze existing, block new, burn down). R2 + R4 were authored
 * dormant in WP3 and **activated in WP6** once the ADR-001/011 target layers landed:
 *  - R2 (layer dependency direction) carries a frozen baseline of the five inner->outer edges that
 *    only the M2/M3 interface extraction (SDS §5.5) can remove; they burn down then.
 *  - R4 (entry-point placement) exempts the two ADR-018 FQCN-pinned components.
 */
class ArchitectureRulesTest {

    // Production (main) source only. Scoping this way keeps this test file's own occurrences
    // of "Graph." / DAO names (in strings and baselines) from tripping the rules.
    private fun productionFiles(): List<KoFileDeclaration> =
        Konsist.scopeFromProject().files
            .filter { "/src/main/" in it.path.replace('\\', '/') }

    private fun KoFileDeclaration.residesAt(relPathUnderPackageRoot: String): Boolean =
        path.replace('\\', '/').endsWith("com/applock/$relPathUnderPackageRoot")

    private fun KoFileDeclaration.relPath(): String =
        path.replace('\\', '/').substringAfter("com/applock/")

    // ---- R1 — the Graph service locator must not exist (ADR-015 realized; WP5) -----------
    //
    // WP5 deleted core/Graph.kt and migrated every consumer to Hilt. The WP3 interim rule
    // ("no NEW Graph lookups", with a frozen baseline) is now terminal: no production file may
    // reference com.applock.core.Graph, and core/Graph.kt must not come back.
    @Test
    fun `R1 - the Graph service locator no longer exists or is referenced`() {
        val referencing = productionFiles()
            .filter { referencesGraph(it) }
            .map { it.path }
        assertTrue(
            "ADR-015 R1: com.applock.core.Graph was removed in WP5 — do not reintroduce the " +
                "service locator. Take dependencies via Hilt (@Inject / the di/ module) instead. " +
                "Offending file(s):\n" + referencing.joinToString("\n") { "  - $it" },
            referencing.isEmpty(),
        )

        val graphFile = productionFiles()
            .filter { it.residesAt("core/Graph.kt") }
            .map { it.path }
        assertTrue(
            "ADR-015 R1: core/Graph.kt must not exist — it was deleted in WP5. Found:\n" +
                graphFile.joinToString("\n") { "  - $it" },
            graphFile.isEmpty(),
        )
    }

    // A real Graph consumer: cross-package call sites must import com.applock.core.Graph;
    // the only same-package case is core/ itself. This is more precise than a raw text match
    // (a stray "Graph." in a comment of some unrelated new file won't false-positive).
    private fun referencesGraph(file: KoFileDeclaration): Boolean {
        val importsGraph = file.imports.any { it.name == "com.applock.core.Graph" }
        val corePackageUse = "/com/applock/core/" in file.path.replace('\\', '/') &&
            file.text.contains("Graph.")
        return importsGraph || corePackageUse
    }

    // ---- R2 — layer dependency direction (ADR-001/011) — ACTIVE from WP6 ------------------
    //
    // Core layers ranked innermost(0)..outermost(3). A core-layer file may import another core
    // layer only when the target rank is strictly lower (same layer is always allowed; same rank
    // but a different layer — e.g. data <-> security — is a violation). platform/, di/, the two
    // ADR-018 pinned entry points and the root Application are adapters/wiring, exempt from R2
    // (R4 governs platform placement). The five inner->outer edges that clean architecture removes
    // only via the M2/M3 interface extraction (SDS §5.5) are grandfathered by r2Baseline and burn
    // down then; any NEW inner->outer edge fails.
    private val coreLayerRank = mapOf(
        "domain" to 0,
        "data" to 1,
        "security" to 1,
        "service" to 2,
        "presentation" to 3,
    )

    // fromFile (path under com/applock) -> the outer layer it is grandfathered to reach today.
    private val r2Baseline = setOf(
        "domain/LockPolicyManager.kt" to "data", // reads ProtectedAppDao (repo interface -> M2/M3)
        "data/AppLockDatabase.kt" to "security", // DatabaseKeyProvider (key-provider interface -> M2)
        "data/VaultRepository.kt" to "security", // EncryptedFileStore (crypto-store interface -> M2/M3)
        "service/ApplicationLockEngine.kt" to "presentation", // launches LockScreenActivity (nav -> M2/M3)
        "service/IntruderCaptureManager.kt" to "presentation", // PendingIntent to MainActivity (nav -> M2/M3)
    )

    @Test
    fun `R2 - inner layers do not depend on outer layers`() {
        val violations = productionFiles().flatMap { r2ViolationsIn(it) }
        assertTrue(
            "ADR-001/011 R2: a core layer must depend only on strictly-inner layers " +
                "(inner to outer: domain < {data, security} < service < presentation). New " +
                "outward dependencies must go through the proper layer/interface (interface " +
                "extraction lands in M2/M3). Offending edge(s):\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    // Wrong-direction core-layer imports in one file (empty for exempt files / clean files).
    private fun r2ViolationsIn(file: KoFileDeclaration): List<String> {
        val fromLayer = coreLayerOf(file) ?: return emptyList()
        val fromRank = coreLayerRank.getValue(fromLayer)
        val rel = file.relPath()
        return file.imports.mapNotNull { import ->
            val toLayer = coreLayerOfImport(import.name) ?: return@mapNotNull null
            val toRank = coreLayerRank.getValue(toLayer)
            val wrong = toRank > fromRank || (toRank == fromRank && toLayer != fromLayer)
            val grandfathered = r2Baseline.any { it.first == rel && it.second == toLayer }
            if (wrong && !grandfathered) "  - $rel ($fromLayer) -> ${import.name} ($toLayer)" else null
        }
    }

    private val coreLayers = listOf("domain", "data", "security", "service", "presentation")

    // The core layer a production FILE belongs to (null = exempt: platform/di/pinned/root).
    private fun coreLayerOf(file: KoFileDeclaration): String? {
        val p = file.path.replace('\\', '/')
        return coreLayers.firstOrNull { "/com/applock/$it/" in p }
    }

    private fun coreLayerOfImport(name: String): String? =
        coreLayers.firstOrNull { name.startsWith("com.applock.$it.") }

    // ---- R3 — no new DAO/database types referenced from UI (SDS §14; ADR-016) -------------
    //
    // Frozen baseline = the UI files that couple to the data layer's DAO/entity/database types
    // today (DAO access and/or direct Room-entity imports). Repositories are the *sanctioned*
    // access path ("route through a repository"), so *Repository imports are excluded. Any new UI
    // declaration (a file under presentation/, or a ViewModel / *Screen) that references a
    // DAO/entity type fails. Baseline burns down at M3 (MVVM/repository refactor moves UI onto
    // domain/state models).
    private val r3UiDataBaseline = setOf(
        "presentation/applist/AppListViewModel.kt",
        "presentation/intruder/IntruderLogViewModel.kt",
        "presentation/intruder/IntruderLogScreen.kt",
        "presentation/vault/VaultViewModel.kt",
        "presentation/vault/VaultScreen.kt",
    )

    @Test
    fun `R3 - no new DAO or database types referenced from UI`() {
        val offenders = productionFiles()
            .filter { isUiFile(it) }
            .filter { referencesDataLayer(it) }
            .filterNot { file -> r3UiDataBaseline.any { file.residesAt(it) } }
            .map { it.path }

        assertTrue(
            "SDS §14 / ADR-016 R3: UI code must not reference DAO/AppLockDatabase types — route " +
                "through a repository/use-case instead. Offending UI file(s):\n" +
                offenders.joinToString("\n") { "  - $it" },
            offenders.isEmpty(),
        )
    }

    private fun isUiFile(file: KoFileDeclaration): Boolean {
        val p = file.path.replace('\\', '/')
        val name = p.substringAfterLast('/')
        return "/com/applock/presentation/" in p ||
            name.endsWith("ViewModel.kt") || name.endsWith("Screen.kt")
    }

    // DAO/entity/database types (com.applock.data), excluding the repositories (the sanctioned
    // UI access path). Repositories end in "Repository"; DAOs/entities/AppLockDatabase do not.
    private fun referencesDataLayer(file: KoFileDeclaration): Boolean =
        file.imports.any { it.name.startsWith("com.applock.data.") && !it.name.endsWith("Repository") }

    // ---- R4 — Android entry points only in platform/ or presentation/ (ADR-011/018) -------
    //
    // Every Activity/Service/BroadcastReceiver must reside under platform/ or presentation/, so the
    // Android surface stays out of the core layers. EXCEPT the two ADR-018 FQCN-pinned components,
    // which stay at their original packages permanently — renaming them breaks the persisted
    // accessibility grant / device-admin registration on upgrade. Detected by directly-declared
    // supertype (Konsist reads source; that is exactly the component base class in this codebase).
    private val r4PinnedEntryPoints = setOf(
        "applocker/service/AppDetectionService.kt",
        "applocker/admin/UninstallProtectionReceiver.kt",
    )
    private val entryPointSupertype = Regex(
        ":\\s*(Activity|ComponentActivity|FragmentActivity|AppCompatActivity|Service|" +
            "AccessibilityService|BroadcastReceiver|DeviceAdminReceiver)\\b",
    )

    @Test
    fun `R4 - Android entry points reside only in platform or presentation`() {
        val offenders = productionFiles()
            .filter { entryPointSupertype.containsMatchIn(it.text) }
            .filterNot { file -> r4PinnedEntryPoints.any { file.residesAt(it) } }
            .filterNot { file ->
                val p = file.path.replace('\\', '/')
                "/com/applock/platform/" in p || "/com/applock/presentation/" in p
            }
            .map { it.relPath() }

        assertTrue(
            "ADR-011/018 R4: Android entry points (Activity/Service/Receiver) must reside in " +
                "platform/ or presentation/ (except the two ADR-018 FQCN-pinned components). " +
                "Offending:\n" + offenders.joinToString("\n") { "  - $it" },
            offenders.isEmpty(),
        )
    }
}
