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
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        exclusiveContent {
            forRepository {
                ivy("https://nodejs.org/dist") {
                    name = "Node.js Distributions"
                    patternLayout {
                        artifact("v[revision]/[artifact]-v[revision]-[classifier].[ext]")
                    }
                    metadataSources { artifact() }
                }
            }
            filter {
                includeGroup("org.nodejs")
            }
        }
        exclusiveContent {
            forRepository {
                ivy("https://github.com/yarnpkg/yarn/releases/download") {
                    name = "Yarn Distributions"
                    patternLayout {
                        artifact("v[revision]/[artifact]-v[revision].[ext]")
                    }
                    metadataSources { artifact() }
                }
            }
            filter {
                includeGroup("com.yarnpkg")
            }
        }
    }
}

rootProject.name = "multipaz-wallet"
include(":androidApp")
include(":backend")
include(":shared")
include(":webApp")
