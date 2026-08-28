@file:OptIn(com.google.devtools.ksp.KspExperimental::class)

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.kairowan.roomflow.legacy"
    compileSdk = 36
    defaultConfig {
        val verificationId = providers.gradleProperty("verificationApplicationId").get()
        require(verificationId.matches(Regex("com\\.kairowan\\.roomflow\\.verification(\\.[a-z][a-z0-9]*)?")))
        applicationId = verificationId
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "legacy-fixture"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// Frozen historical app: no SDK, no shared production DAO, no Room-version override.
dependencies {
    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}

ksp {
    useKsp2.set(false)
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.register("checkLegacyDependencies") {
    doLast {
        val components = configurations.getByName("debugRuntimeClasspath").incoming.resolutionResult.allComponents
        check(components.none { it.id.displayName.contains("room-flow") }) { "历史 APK 不得依赖 SDK" }
        check(components.filter { it.moduleVersion?.group == "androidx.room" }.all { it.moduleVersion?.version == "2.6.1" })
    }
}
