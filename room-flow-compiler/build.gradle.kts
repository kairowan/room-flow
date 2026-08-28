plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

kotlin { jvmToolchain(17) }

// Build-time only. No reflection, compiler or KSP dependency enters the Android runtime AAR.
dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:${libs.versions.ksp.get()}")
}

publishing {
    publications {
        register<MavenPublication>("verification") {
            from(components["java"])
            groupId = "com.kairowan.verification"
            artifactId = "room-flow-compiler"
            version = "0.0.0-LOCAL"
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
