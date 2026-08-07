plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
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
    }

    lint {
        // WP1 (M1): pre-existing findings are frozen as accepted debt so only NEW
        // issues fail CI. Burn the baseline down opportunistically (tracked at the
        // M2/M3 gate reviews) — never regenerate it to silence a new finding.
        baseline = file("lint-baseline.xml")
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

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.konsist) // WP3 (ADR-016): architecture rules as unit tests

    detektPlugins(libs.detekt.formatting) // ktlint ruleset inside detekt
}
