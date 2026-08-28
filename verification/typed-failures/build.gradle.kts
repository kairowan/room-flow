@file:OptIn(com.google.devtools.ksp.KspExperimental::class)

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.kairowan.roomflow.invalid"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    val failure = providers.gradleProperty("typedFailure").get()
    require(failure in listOf("foreign", "value", "ignored", "embedded", "custom", "generic", "specforeign", "specvalue",
        "projectionforeign", "projectionvalue", "aggregatevalue", "cursorforeign"))
    sourceSets.getByName("main").java.srcDir("src/$failure/java")
}

dependencies {
    implementation(project(":room-flow"))
    ksp(project(":room-flow-compiler"))
}

ksp {
    useKsp2.set(providers.gradleProperty("roomVersion").getOrElse("2.6.1") != "2.6.1")
}
