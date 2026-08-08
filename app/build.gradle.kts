plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    alias(libs.plugins.license.report)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.applock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.applock"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // WP4 (M1, ADR-017 / FR-234): build provenance exposed to the app via BuildConfig.
        // SCHEMA_VERSION mirrors the Room schema version in AppLockDatabase (currently 2) —
        // keep the two in lockstep when the DB is bumped.
        buildConfigField("int", "SCHEMA_VERSION", "2")
        // FR-227 secure-config injection *mechanism* (no secrets exist yet): a Gradle property
        // flows into BuildConfig, absent-safe. CI injects the real UTC build time
        // (-PbuildTime=…); local builds fall back to "unknown" so they stay reproducible. This
        // same absent-safe property path is the sanctioned route for any FUTURE secret — never
        // hard-code a secret into source or version control (they belong in Gradle properties /
        // CI secrets, injected here).
        buildConfigField(
            "String",
            "BUILD_TIME",
            "\"${providers.gradleProperty("buildTime").getOrElse("unknown")}\"",
        )
    }

    // WP4 (M1, ADR-017): four build environments as a flavor dimension, crossed with the
    // debug/release build types → 8 assembleable variants. FR-226's "Testing" maps to `qa`
    // because Gradle reserves the `test*` name space. Non-prod flavors take an applicationId
    // suffix so they install side-by-side with prod; ENVIRONMENT is surfaced via BuildConfig.
    //
    // prod keeps applicationId `com.applock` PERMANENTLY (isDefault, no suffix): it is the
    // upgrade / (future) Play-listing identity and the left half of the externally-persisted
    // accessibility-service and device-admin component strings — see ADR-017 (this half) and
    // ADR-018 (the FQCN half). Changing it strands every existing install on upgrade.
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev" // human-facing env identity (About/bug reports)
            buildConfigField("String", "ENVIRONMENT", "\"dev\"")
        }
        create("qa") {
            dimension = "environment"
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-qa"
            buildConfigField("String", "ENVIRONMENT", "\"qa\"")
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "ENVIRONMENT", "\"staging\"")
        }
        create("prod") {
            dimension = "environment"
            isDefault = true // default variant for anchor tasks (lint/run) — the shipping identity
            buildConfigField("String", "ENVIRONMENT", "\"prod\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true // WP4: custom BuildConfig fields (ENVIRONMENT / BUILD_TIME / SCHEMA_VERSION)
    }

    lint {
        // WP1 (M1): pre-existing findings are frozen as accepted debt so only NEW
        // issues fail CI. Burn the baseline down opportunistically (tracked at the
        // M2/M3 gate reviews) — never regenerate it to silence a new finding.
        baseline = file("lint-baseline.xml")
    }

    packaging {
        resources {
            // WP5 (M1): Hilt/Dagger 2.56 pulls in org.jspecify:jspecify, whose multi-release-JAR
            // OSGi manifest collides with the same path in bcprov-jdk18on. It is build metadata,
            // absent from the runtime APK either way — exclude it so mergeJavaResource does not
            // fail on the duplicate.
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

// WP3 (M1, ADR-016): detekt = style/complexity + ktlint formatting ruleset. Architecture
// rules (Konsist) live in the unit-test source set, not here. Same baseline philosophy as
// lint: pre-existing findings frozen in config/detekt/baseline.xml; only NEW findings fail.
detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
    buildUponDefaultConfig = true
    autoCorrect = false
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(true)
        sarif.required.set(false)
        txt.required.set(false)
    }
}
tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    jvmTarget = "17"
}

// WP4 (M1, FR-247 partial): dependency + license inventory. `generateLicenseReport` writes
// app/build/reports/dependency-license/ (HTML index + THIRD-PARTY-NOTICES text); CI archives it
// as a build artifact. Scoped to the production shipping classpath so the inventory reflects
// exactly what ships. Full security-status / CVE tracking is M6 (FR-247 → `implemented` there).
licenseReport {
    configurations = arrayOf("prodReleaseRuntimeClasspath")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    implementation(libs.bouncycastle.provider)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.kotlinx.coroutines.android)

    // WP5 (M1, ADR-015): Hilt DI — replaces the core/Graph service locator.
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.konsist) // WP3 (ADR-016): architecture rules as unit tests

    detektPlugins(libs.detekt.formatting) // ktlint ruleset inside detekt
}
