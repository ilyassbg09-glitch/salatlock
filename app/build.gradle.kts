import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.salatlock.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.salatlock.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "2.0"
        // Adresse du site où l'app vérifie s'il existe une nouvelle version.
        val site = (project.findProperty("salatlock.site") as String?) ?: "https://REPLACE_ME.github.io/salatlock"
        buildConfigField("String", "SITE_URL", "\"$site\"")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Clé de signature : gardée HORS de GitHub (fichier keystore.properties + salatlock.keystore).
    // Sans elle, impossible de publier des mises à jour qui s'installent par-dessus.
    val ksFile = rootProject.file("keystore.properties")
    val ks = Properties().apply { if (ksFile.exists()) ksFile.inputStream().use { load(it) } }
    signingConfigs {
        create("shared") {
            storeFile = file(ks.getProperty("storeFile", "salatlock.keystore"))
            storePassword = ks.getProperty("storePassword", "")
            keyAlias = ks.getProperty("keyAlias", "salatlock")
            keyPassword = ks.getProperty("keyPassword", "")
        }
    }
    buildTypes {
        debug { if (ksFile.exists()) signingConfig = signingConfigs.getByName("shared") }
        release {
            isMinifyEnabled = false
            if (ksFile.exists()) signingConfig = signingConfigs.getByName("shared")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    androidResources { noCompress += "tflite" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("com.batoulapps.adhan:adhan:1.2.1")
}
