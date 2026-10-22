plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "uz.netspeed"
    compileSdk = 35

    defaultConfig {
        applicationId = "uz.netspeed"
        minSdk = 26          // Android 8.0+
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Release'ни ҳам debug калити билан имзолаймиз — телефонга дарҳол ўрнатиш учун
            signingConfig = signingConfigs.getByName("debug")
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

// Ташқи кутубхоналар керак эмас — фақат Android'нинг ўзи
