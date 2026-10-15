import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Eigener Release-Schlüssel aus key.properties (nicht im Repository). Fehlt die Datei, wird das
// Release mit dem Debug-Schlüssel signiert – installierbar, aber nicht für eine Veröffentlichung gedacht.
val keyProperties = Properties().apply {
    rootProject.file("key.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "de.perfermat.devicecode"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.perfermat.devicecode"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    // Das ML-Kit-Modell bringt native Bibliotheken für jede CPU-Architektur mit.
    flavorDimensions += "abi"
    productFlavors {
        // Läuft auf allen Handys, auch alten 32-Bit- und x86-Geräten (Emulator).
        create("full") {
            dimension = "abi"
        }
        // Nur 64-Bit-ARM: praktisch alle Handys der letzten Jahre, deutlich kleineres APK.
        create("arm64") {
            dimension = "abi"
            ndk { abiFilters += "arm64-v8a" }
        }
    }

    signingConfigs {
        if (keyProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keyProperties.getProperty("storeFile"))
                storePassword = keyProperties.getProperty("storePassword")
                keyAlias = keyProperties.getProperty("keyAlias")
                keyPassword = keyProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.browser)
    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
}
