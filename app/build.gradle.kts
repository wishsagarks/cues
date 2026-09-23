// Android application module.
//
// This module is only configured when settings.gradle.kts found an SDK, so a
// machine without one still builds and tests :core normally.
//
// Nothing in here was compiled in the environment that scaffolded it: Google's
// Maven is unreachable from there, so every version below is a considered
// guess. The first build on the laptop is what confirms them. See CL-04 in
// CLEANUP.md.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.cues.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cues.android"
        minSdk = 29
        // Targeting 35 is a decision, not a default. From Android 15 an app
        // cannot simply flip global Do Not Disturb; it contributes an owned
        // rule instead. That is the behaviour Cues wants, so the target stays
        // here rather than being lowered to regain the older setters.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Every decision Cues makes lives here, platform-free and already tested.
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // The on-device drafting path. Carried at equal weight with the grammar
    // parser until measured latency on the loaner phone decides between them.
    implementation(libs.mediapipe.tasks.genai)
}
