plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

publishing {
    publications {
        register<MavenPublication>("verification") {
            groupId = "com.kairowan.verification"
            artifactId = "room-flow-debug"
            version = "0.0.0-room${providers.gradleProperty("roomVersion").getOrElse(libs.versions.room.get())}-LOCAL"
            afterEvaluate { from(components["release"]) }
        }
    }
    repositories {
        maven {
            name = "verification"
            url = uri(providers.gradleProperty("verificationRepository").orNull?.let(::file)
                ?: rootProject.layout.buildDirectory.dir("verification-repository").get().asFile)
        }
    }
}

android {
    namespace = "com.kairowan.room_flow.debug"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    publishing { singleVariant("release") { withSourcesJar() } }
}

dependencies {
    api(project(":room-flow"))
    implementation(libs.androidx.core.ktx)
    api(libs.androidx.appcompat)
    implementation(libs.material)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    api("androidx.recyclerview:recyclerview:1.3.2")
}
