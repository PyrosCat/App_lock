package com.applock.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * WP3 (M1) architecture rules — ADR-016 (Konsist). These run in the ordinary unit-test job.
 *
 * R1 + R3 are active now with **frozen baselines** (freeze existing, block new, burn down),
 * mirroring the WP1 lint baseline and the WP3 detekt baseline. R2 + R4 are authored but
 * dormant (@Ignore) until WP6 moves packages into the ADR-001/011 target layers — see the
 * ADR-016 staged-activation table.
 */
class ArchitectureRulesTest {

    // Production (main) source only. Scoping this way keeps this test file's own occurrences
    // of "Graph." / DAO names (in strings and baselines) from tripping the rules.
    private fun productionFiles(): List<KoFileDeclaration> =
        Konsist.scopeFromProject().files
            .filter { "/src/main/" in it.path.replace('\\', '/') }

    private fun KoFileDeclaration.residesAt(relPathUnderPackageRoot: String): Boolean =
        path.replace('\\', '/').endsWith("com/applock/$relPathUnderPackageRoot")

    // ---- R1 — no new Graph.* lookup sites (ADR-015 interim rule; ADR-016) ----------------
    //
    // Frozen baseline = the 11 files that reference Graph today: the definition + 10 legacy
    // call sites. Any *other* production file that does a `Graph.` lookup fails. `di/` is
    // exempt for when WP5 introduces it. Baseline burns down in WP5; the rule then flips to
    // "Graph must not exist".
    private val r1GraphBaseline = setOf(
        "core/Graph.kt", // the definition
        "AppLockApplication.kt",
        "applocker/service/AppDetectionService.kt",
        "applocker/service/BootReceiver.kt",
        "applocker/service/ProtectionWatchdogService.kt",
        "authentication/ui/LockScreenActivity.kt",
        "privacy/ui/IntruderLogViewModel.kt",
        "ui/AppListViewModel.kt",
        "ui/MainActivity.kt",
        "ui/SettingsScreen.kt",
        "vault/VaultViewModel.kt",
    )

    @Test
    fun `R1 - no new Graph service-locator lookups outside the frozen baseline`() {
        val offenders = productionFiles()
            .filter { referencesGraph(it) }
            .filterNot { file -> isInDiPackage(file) }
            .filterNot { file -> r1GraphBaseline.any { file.residesAt(it) } }
            .map { it.path }

        assertTrue(
            "ADR-015/ADR-016 R1: new Graph.* lookup site(s) introduced — take dependencies via " +
                "constructor injection instead (Graph is being removed in WP5). Offending files:\n" +
                offenders.joinToString("\n") { "  - $it" },
            offenders.isEmpty(),
        )
    }

    private fun isInDiPackage(file: KoFileDeclaration): Boolean =
        "/com/applock/di/" in file.path.replace('\\', '/')

    // A real Graph consumer: cross-package call sites must import com.applock.core.Graph;
    // the only same-package case is core/ itself. This is more precise than a raw text match
    // (a stray "Graph." in a comment of some unrelated new file won't false-positive).
    private fun referencesGraph(file: KoFileDeclaration): Boolean {
        val importsGraph = file.imports.any { it.name == "com.applock.core.Graph" }
        val corePackageUse = "/com/applock/core/" in file.path.replace('\\', '/') &&
            file.text.contains("Graph.")
        return importsGraph || corePackageUse
    }

    // ---- R3 — no new DAO/database types referenced from UI (SDS §14; ADR-016) -------------
    //
    // Frozen baseline = the UI files that currently couple to the core.database package today
    // (DAO access via Graph.database.*Dao() and/or direct Room-entity imports). Any *new* UI
    // declaration (ViewModel / *Screen / a file in a `.ui` package) that touches the data layer
    // fails. Baseline burns down at M3 (MVVM/repository refactor moves UI onto domain models).
    private val r3UiDataBaseline = setOf(
        "ui/AppListViewModel.kt",
        "privacy/ui/IntruderLogViewModel.kt",
        "privacy/ui/IntruderLogScreen.kt",
        "vault/VaultViewModel.kt",
        "vault/ui/VaultScreen.kt",
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
        return "/ui/" in p || name.endsWith("ViewModel.kt") || name.endsWith("Screen.kt")
    }

    private fun referencesDataLayer(file: KoFileDeclaration): Boolean =
        file.imports.any { it.name.startsWith("com.applock.core.database") } ||
            file.text.contains("Graph.database")

    // ---- R2 — layer dependency direction (ADR-001/011) — DORMANT until WP6 ----------------
    @Ignore(
        "Dormant until WP6 (ADR-016): the target layer packages (domain/ service/ data/ " +
            "presentation/) do not exist pre-realignment. Flip to enforced when WP6 lands.",
    )
    @Test
    fun `R2 - inner layers do not depend on outer layers`() {
        // WP6: assert with Konsist's architecture DSL that
        //   presentation -> service/domain,  service -> domain/data/security,  data -> domain,
        //   domain depends on nothing outward. (domain/service/data/presentation not yet created.)
        assertTrue("placeholder — implemented at WP6", true)
    }

    // ---- R4 — platform entry points only in platform//presentation/ — DORMANT until WP6 ---
    @Ignore(
        "Dormant until WP6 (ADR-016): entry points still live in applocker/service, admin/, " +
            "ui/, authentication/ui/ pre-realignment. Flip to enforced when WP6 lands, EXEMPTING " +
            "the two FQCN-pinned components (AppDetectionService, UninstallProtectionReceiver; ADR-018).",
    )
    @Test
    fun `R4 - Android entry points reside only in platform or presentation`() {
        // WP6: services/receivers/activities must reside in platform/ or presentation/, EXCEPT
        // com.applock.applocker.service.AppDetectionService and
        // com.applock.applocker.admin.UninstallProtectionReceiver, which are FQCN-pinned (ADR-018).
        assertTrue("placeholder — implemented at WP6", true)
    }
}
