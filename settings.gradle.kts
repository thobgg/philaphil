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

rootProject.name = "philaphil"
// shared/  — geteilter Kern (Katalog-Datenbank, Commons-Bilder, Oberflaeche), Android + Desktop-JVM
// app/     — Android-Huelle (Activity, Manifest, Launcher, Signatur)
// desktop/ — Linux/Windows-Huelle (Fenster, Pakete deb/exe)
include(":shared")
include(":app")
include(":desktop")
