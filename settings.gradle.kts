import org.gradle.api.initialization.resolve.RepositoriesMode

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

rootProject.name = "ArticlePilot"

include(":app")
include(":core:model")
include(":core:parser")
include(":core:validator")
include(":core:database")
include(":media:downloader")
include(":media:storage")
include(":media:inspection")
include(":media:processor")
include(":media:validator")
include(":browser:session")
include(":browser:webview")
include(":browser:bridge")
include(":automation:engine")
include(":automation:state")
include(":automation:profiles")
include(":automation:selectors")
include(":automation:recovery")
