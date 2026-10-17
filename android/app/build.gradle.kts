// ---------------------------------------------------------------------------
// The :app module — this is the actual Android application.
// ---------------------------------------------------------------------------

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Generates the code that turns @Serializable Kotlin classes into JSON.
    alias(libs.plugins.kotlin.serialization)
    // NOTE: no `kotlin.android` plugin — AGP 9 compiles Kotlin built-in.
}

android {
    // `namespace` replaces the old package attribute in AndroidManifest.xml and
    // is where the generated R (resources) class lives.
    namespace = "com.reprise.app"

    // Compile against the newest installed SDK. compileSdk only affects what
    // APIs are available at compile time; it does not change runtime behaviour.
    //
    // AGP 9 uses Android's "minor SDK" versioning, so the level is expressed
    // with the compileSdk { } block rather than the old `compileSdk = 37`
    // property (which is deprecated and removed in AGP 10).
    // Here: API level 37, minor 2  ->  the `android-37.2` platform.
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.reprise.app"
        // minSdk 24 = Android 7.0, a safe floor that covers ~99% of devices.
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Base URL for the backend used by debug builds (Phase 2 onward).
            // 10.0.2.2 is the Android emulator's alias for the host machine.
            // This is the LOCAL development backend — to run against a real
            // phone on the same Wi-Fi, put your machine's LAN address here
            // (and add it to network_security_config.xml), or forward the port
            // with `adb reverse tcp:4000 tcp:4000` and use 127.0.0.1.
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:4000/\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // The DEPLOYED backend (Vercel — see "Deploying the backend" in
            // README.md). A release build is the one you give to other people:
            // it needs a URL that works from any network, and https, which is
            // why it is not a LAN address.
            //
            // If the project ends up with a different Vercel domain, change
            // this ONE line and rebuild — nothing else depends on the host.
            buildConfigField("String", "API_BASE_URL", "\"https://reprise-api.vercel.app/\"")
            // Signed with the Debug keystore on purpose: this APK is handed to
            // friends to sideload, not published to the Play Store, and using
            // the same key as the debug build means it INSTALLS OVER an already
            // installed debug build instead of colliding with it.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// NOTE: we do not set the Kotlin jvmTarget explicitly. With AGP's built-in
// Kotlin it defaults to `android.compileOptions.targetCompatibility` (Java 17
// above), which is exactly what we want.

dependencies {
    // --- Core / lifecycle ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    // --- Compose (the BOM keeps all Compose artifact versions aligned) ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    // --- Navigation ---
    implementation(libs.androidx.navigation.compose)

    // --- Networking: Retrofit + OkHttp, with kotlinx.serialization for JSON ---
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // --- Local storage: DataStore (preferences) + encrypted token storage ---
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // --- Background work: the daily gym reminder is a WorkManager job ---
    implementation(libs.androidx.work.runtime.ktx)

    // --- Images: profile photos are fetched, cached and decoded by Coil ---
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // --- Debug-only tooling (Layout Inspector / @Preview) ---
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // --- Unit tests ---
    testImplementation(libs.junit)

    // --- Instrumented tests ---
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
