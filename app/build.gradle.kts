@file:OptIn(com.google.devtools.ksp.KspExperimental::class)

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.kairowan.roomflow"
    compileSdk = 36

    defaultConfig {
        val verificationId = providers.gradleProperty("verificationApplicationId").orNull
        require(verificationId == null || verificationId.matches(Regex("com\\.kairowan\\.roomflow\\.verification(\\.[a-z][a-z0-9]*)?"))) {
            "真机验证必须使用独立的 verification applicationId"
        }
        applicationId = verificationId ?: "com.kairowan.roomflow"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // ★ 建议与库保持一致：Java 17 / Kotlin 17
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    val roomVersion = providers.gradleProperty("roomVersion").getOrElse(libs.versions.room.get())
    implementation(project(":room-flow"))
    debugImplementation(project(":room-flow-debug"))
    implementation("androidx.paging:paging-runtime-ktx:3.3.2")
    ksp("androidx.room:room-compiler:$roomVersion")
    ksp(project(":room-flow-compiler"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
}

// ★ 如果你想导出 schema，请确保目录存在；否则先注释掉这块或把 exportSchema=false
ksp {
    // Room 2.6 的处理器不兼容 KSP2；2.7+ 使用 KSP2 隔离 schema 序列化依赖。
    useKsp2.set(providers.gradleProperty("roomVersion").getOrElse(libs.versions.room.get())
        .substringBefore('-').split('.').let { it[0].toInt() > 2 || it[1].toInt() >= 7 })
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}
