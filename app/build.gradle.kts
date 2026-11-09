plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 共用簽章 hoonsor-release.jks：由 CI 從 Secrets 解出；本機或沒有 Secrets 時退回 debug 簽章
val signingPath: String? = System.getenv("SIGNING_KEYSTORE_PATH")?.takeIf { it.isNotBlank() && file(it).exists() }

android {
    namespace = "com.hoonsor.screentranslate"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hoonsor.screentranslate"
        minSdk = 30          // Android 11：AccessibilityService.takeScreenshot() 的最低版本
        targetSdk = 35
        // 發新版：改這兩行後 push vX.Y.Z tag（tag 必須與 versionName 相同，CI 會檢查）
        versionCode = 4
        versionName = "0.3.1"
    }

    // 金鑰與密碼都不放進 repo（repo 是公開的），一律從環境變數讀
    signingConfigs {
        if (signingPath != null) {
            create("hoonsor") {
                storeFile = file(signingPath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (signingPath != null) signingConfigs.getByName("hoonsor") else signingConfigs.getByName("debug")
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
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7") // App 內更新的 LifecycleEventEffect

    // 協程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // 網路
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ML Kit OCR（模型打包在 APK 內，離線可用、第一次使用不必等下載）
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")

    // 單元測試（App 內更新的版本比較、Release 解析；org.json 在 JVM 上要真正的實作）
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
