pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com.android.*")
                includeGroupByRegex("com.google.*")
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
        google {
            content {
                includeGroupByRegex("com.android.*")
                includeGroupByRegex("com.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "bp-diary"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    ":app",
    ":core:model",
    ":core:common",
    ":core:designsystem",
    ":core:ui",
    ":domain",
    ":data:database",
    ":data:datastore",
    ":data:repository",
    ":data:reminders",
    ":feature:home",
    ":feature:entry",
    ":feature:diary",
    ":feature:charts",
    ":feature:reminders",
    ":feature:levels",
    ":feature:settings",
    ":feature:onboarding",
)
