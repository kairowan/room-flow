plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

// 默认保留隔离验收坐标；显式 releaseVersion 用于版本化发布，不包含远端凭据。
val releaseVersion = providers.gradleProperty("releaseVersion").orNull

publishing {
    publications {
        register<MavenPublication>("verification") {
            groupId = if (releaseVersion == null) "com.kairowan.verification" else "com.github.kairowan.room-flow"
            artifactId = "room-flow"
            version = releaseVersion ?: "0.0.0-room${providers.gradleProperty("roomVersion").getOrElse(libs.versions.room.get())}-LOCAL"
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
    namespace = "com.kairowan.room_flow"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    val roomVersion = providers.gradleProperty("roomVersion").getOrElse(libs.versions.room.get())
    api("androidx.room:room-runtime:$roomVersion")
    api("androidx.room:room-ktx:$roomVersion")
    api("androidx.sqlite:sqlite-ktx:2.5.0")
    constraints {
        api("androidx.sqlite:sqlite-framework:2.5.0") {
            because("SQLite 2.4 的 onOpen 恢复路径可能忽略 allowDataLossOnRecovery 并删库，b/348458416")
        }
    }
    api ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    api("androidx.paging:paging-common-ktx:3.3.2")
}
