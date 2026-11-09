pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Maven Central via Google's mirror first (repo.maven.apache.org rate-limits shared proxies).
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Maven Central via Google's mirror first (repo.maven.apache.org rate-limits shared proxies).
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        mavenCentral()
    }
}

rootProject.name = "AwadyTV"
include(":app")
