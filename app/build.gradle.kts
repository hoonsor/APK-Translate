plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hoonsor.screentranslate"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hoonsor.screentranslate"
        minSdk = 30          // Android 11：AccessibilityService.takeScreenshot() 的最低版本
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // 固定簽章：每次 CI 編出的 APK 都能直接覆蓋安裝，不必先解除安裝
    signingConfigs {
        create("fixed") {
            storeFile = rootProject.file("keystore/screentranslate.jks")
            storePassword = "screentranslate"
            keyAlias = "screentranslate"
            keyPassword = "screentranslate"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // Compose（設定頁）
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // 協程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // 網路
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ML Kit OCR（模型打包在 APK 內，離線可用、第一次使用不必等下載）
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
}
