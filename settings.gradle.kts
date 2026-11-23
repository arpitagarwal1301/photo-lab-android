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
    }
}

rootProject.name = "spike-ai"

include(":guard-core")
include(":embed-core")
include(":search-core")
include(":bench")
include(":photos-app")
include(":fixtures-shop")
include(":spikes:b-appfunctions-access")
include(":spikes:d-paparazzi-semantics")
