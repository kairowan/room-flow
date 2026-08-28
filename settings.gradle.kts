pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "roomflow"
include(":app")
include(":room-flow")
include(":room-flow-debug")
include(":room-flow-compiler")
if (providers.gradleProperty("typedFailure").isPresent) {
    include(":typed-failures")
    project(":typed-failures").projectDir = file("verification/typed-failures")
}

// Only build the SDK-free historical APK during explicit isolated-device verification.
if (providers.gradleProperty("verificationApplicationId").isPresent) {
    include(":legacy-fixture")
    project(":legacy-fixture").projectDir = file("verification/legacy-fixture")
}
