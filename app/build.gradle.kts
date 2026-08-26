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
    // M7 WP0 (spike, D0): API-36 baseline. AGP 8.13.2 supports up to compileSdk 36.1; the
    // installed platform is android-36.1. targetSdk 36 is the shipping target (v1.0.0 first
    // submits at M10, past the 2026-08-31 Play deadline). M1's API-35 device gate is untouched.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.applock"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // WP8 (M1): the on-device smoke suite (app/src/androidTest) runs under the standard
        // AndroidJUnitRunner — the real @HiltAndroidApp application backs it, so the tests
        // exercise the production Hilt graph (no test-only component or custom runner needed).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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

    // WP8 (M1, ADR-014 / M1_PLAN D4): Gradle-managed devices for the on-device smoke suite
    // (app/src/androidTest). Two device groups run the same variant's androidTest APK:
    //   • `ci`   — API 30 + 35 + 36, run on the GitHub Actions runners (KVM); see .github/workflows/ci.yml.
    //   • `full` — API 26/29/30/33/35 + 36, run locally on the NucBox G5 (the fleet device host).
    // The api36 lane is the M7 WP0 / D0 API-36 shipping target (M7_PLAN §10); a build change, not an ADR.
    // Run a group with e.g. `./gradlew ciGroupProdDebugAndroidTest` /
    // `fullGroupProdDebugAndroidTest`, or a single level with `api33ProdDebugAndroidTest`.
    // Physical devices (the arm64 Moto G 2025) are NOT GMD-managed — GMD is emulator-only; run the
    // same suite there with `./gradlew connectedProdDebugAndroidTest` for real-hardware + native
    // SQLCipher coverage the all-x86_64 matrix cannot give.
    // Operator runbook (incl. the API-29 Argon2 heap workaround): docs/testing/WP8_GMD_MATRIX.md.
    //
    // Image source: ATD (automated-test-device) images are headless + lighter and exist from API 30
    // up, so 30/33 use `aosp-atd`; API 26/29 predate ATD and API 35's ATD image is not relied upon,
    // so those use the standard `aosp` image. API 36 (M7 WP0 / D0) likewise uses `aosp`; its
    // system-image availability is confirmed at the GMD run on the fleet host (this machine only
    // defines the lane). No smoke test needs Google APIs (Argon2 = BouncyCastle, SQLCipher = bundled
    // .so, EncryptedSharedPreferences = the AOSP Keystore), so plain AOSP suffices.
    testOptions {
        animationsDisabled = true
        managedDevices {
            localDevices {
                create("api26") {
                    device = "Pixel 2"
                    apiLevel = 26
                    systemImageSource = "aosp"
                }
                create("api29") {
                    device = "Pixel 2"
                    apiLevel = 29
                    systemImageSource = "aosp"
                }
                create("api30") {
                    device = "Pixel 5"
                    apiLevel = 30
                    systemImageSource = "aosp-atd"
                }
                create("api33") {
                    device = "Pixel 5"
                    apiLevel = 33
                    systemImageSource = "aosp-atd"
                }
                create("api35") {
                    device = "Pixel 5"
                    apiLevel = 35
                    systemImageSource = "aosp"
                }
                // M7 WP0 (D0): the API-36 shipping-target lane (M7_PLAN §10).
                create("api36") {
                    device = "Pixel 5"
                    apiLevel = 36
                    systemImageSource = "aosp"
                }
            }
            groups {
                create("ci") {
                    targetDevices.add(localDevices["api30"])
                    targetDevices.add(localDevices["api35"])
                    targetDevices.add(localDevices["api36"]) // M7 WP0 (§10): API-36 CI lane
                }
                create("full") {
                    targetDevices.add(localDevices["api26"])
                    targetDevices.add(localDevices["api29"])
                    targetDevices.add(localDevices["api30"])
                    targetDevices.add(localDevices["api33"])
                    targetDevices.add(localDevices["api35"])
                    targetDevices.add(localDevices["api36"]) // M7 WP0 (§10): API-36 full lane
                }
            }
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

    // WP8 (M1): on-device instrumentation smoke suite (app/src/androidTest), run on the GMD matrix.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Injects the empty test-host activity used by the Compose test rules into the debug manifest.
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    detektPlugins(libs.detekt.formatting) // ktlint ruleset inside detekt
}
