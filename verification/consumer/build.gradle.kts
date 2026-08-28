@file:OptIn(com.google.devtools.ksp.KspExperimental::class)

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

providers.gradleProperty("consumerBuildDir").orNull?.let { layout.buildDirectory.set(file(it)) }
val withDebugArtifact = providers.gradleProperty("withDebugArtifact").orNull == "true"
val releaseVersion = providers.gradleProperty("releaseVersion").orNull
val artifactGroup = if (releaseVersion == null) "com.kairowan.verification" else "com.github.kairowan.room-flow"

android {
    namespace = "com.kairowan.roomflow.verification"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    if (withDebugArtifact) sourceSets.getByName("main").java.srcDir("src/debugArtifact/java")
}

dependencies {
    val room = providers.gradleProperty("roomVersion").getOrElse(libs.versions.room.get())
    val artifact = if (withDebugArtifact) "room-flow-debug" else "room-flow"
    implementation("$artifactGroup:$artifact:${releaseVersion ?: "0.0.0-room$room-LOCAL"}") { isChanging = releaseVersion == null }
    ksp("$artifactGroup:room-flow-compiler:${releaseVersion ?: "0.0.0-LOCAL"}") { isChanging = releaseVersion == null }
    if (releaseVersion != null) {
        // One published baseline artifact must also work in a host selecting newer Room.
        constraints {
            implementation("androidx.room:room-runtime:$room")
            implementation("androidx.room:room-ktx:$room")
        }
    }
}
ksp {
    useKsp2.set(providers.gradleProperty("roomVersion").getOrElse("2.6.1") != "2.6.1")
}
configurations.configureEach { resolutionStrategy.cacheChangingModulesFor(0, "seconds") }

tasks.register("checkConsumerDependencies") {
    dependsOn("assembleRelease")
    doLast {
        val modules = configurations.getByName("releaseRuntimeClasspath").incoming.resolutionResult.allComponents
            .mapNotNull { it.moduleVersion }
        val sqlite = modules.single { it.group == "androidx.sqlite" && it.name == "sqlite-framework" }
        val version = sqlite.version.substringBefore('-').split('.').map(String::toInt)
        check(version[0] > 2 || version[0] == 2 && version[1] >= 5) { "SQLite Framework 安全底线丢失: $sqlite" }
        val hasUi = modules.any { it.group in setOf("androidx.appcompat", "androidx.recyclerview", "com.google.android.material") }
        check(hasUi == withDebugArtifact)
        check(modules.none { it.group == "com.google.devtools.ksp" || it.name.contains("compiler") || it.name == "kotlin-reflect" })
        check(modules.any { it.group == artifactGroup && it.name == "room-flow" })
        val expectedRoom = providers.gradleProperty("roomVersion").getOrElse(libs.versions.room.get())
        check(modules.any { it.group == "androidx.room" && it.name.startsWith("room-runtime") && it.version == expectedRoom })
        println("Artifact consumer: Room $expectedRoom, SQLite ${sqlite.version}, debug=$withDebugArtifact; POM-only=${providers.gradleProperty("pomOnly").orNull == "true"}")
    }
}
