plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") // 需要在项目级gradle添加ksp插件
}

android {
    namespace = "com.example.ntuschedule"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.ntuschedule"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.3"
    }

    buildTypes {
        release {
            isMinifyEnabled = true // 开启代码混淆，极大幅度减小体积
            isShrinkResources = true // 移除无用资源
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // ---------- 👇 帮你加的 Java 和 Kotlin 版本对齐配置 👇 ----------
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // ---------- 👆 加好了 👆 ----------

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.1" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3") // MD3设计语言
    implementation("io.coil-kt:coil-compose:2.6.0")
    // Room 数据库
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // 网络与解析
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.17.2")

    // Gson用于List与String转换 (Room TypeConverter)
    implementation("com.google.code.gson:gson:2.10.1")
    // 页面导航组件
    implementation("androidx.navigation:navigation-compose:2.7.7")
    // DataStore本地存储组件（用来记第一周）
    implementation("androidx.datastore:datastore-preferences:1.0.0")
}