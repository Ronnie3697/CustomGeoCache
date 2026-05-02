pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://api.mapbox.com/downloads/v2/releases/maven") {
            content { includeGroup("org.maplibre.gl") }
        }
        maven("https://jitpack.io")
    }
}

rootProject.name = "CustomGeoCache"
include(":app")
