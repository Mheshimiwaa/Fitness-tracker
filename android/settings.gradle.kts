// ---------------------------------------------------------------------------
// Gradle settings — tells Gradle where to find plugins/dependencies and which
// modules make up this build.
//
// Kotlin DSL note: `settings.gradle.kts` is written in Kotlin, so `//` is a
// comment and the file is type-checked.
// ---------------------------------------------------------------------------

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
    // Force all modules to use the repositories declared here (no per-module ones).
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "RepRise"
include(":app")
