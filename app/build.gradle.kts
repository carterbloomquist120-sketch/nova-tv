plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.novatv.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.novatv.launcher"
        minSdk = 22
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // One fixed key so every new build installs over the old one.
    // This is a personal sideloaded app, so the key lives in the repo.
    signingConfigs {
        create("nova") {
            storeFile = file("nova.keystore")
            storePassword = "novatv123"
            keyAlias = "nova"
            keyPassword = "novatv123"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("nova")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("nova")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
