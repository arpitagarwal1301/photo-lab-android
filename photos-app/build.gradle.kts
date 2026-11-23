plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.spikeai.photos"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.spikeai.photos"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        ndk { abiFilters += "arm64-v8a" } // real phones only; keeps the APK small
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }
    // Models are NOT bundled: the app downloads them on first launch (or scripts/install-phone.sh copies them over USB).
}

dependencies {
    implementation(project(":embed-core"))
    implementation(project(":search-core"))
    implementation(libs.onnxruntime.android)
    implementation(libs.mlkit.text)
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
}
