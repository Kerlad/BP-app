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
    ":feature:transfer",
    ":feature:onboarding",
)

// The benchmark module is part of the build only on request (`-Pbp.benchmark`, see docs/benchmarks.md): its flag
// android.experimental.self-instrumenting makes the AAPT2 daemon crash when the other modules link their
// unit-test resources (seen with AGP 9.5.0), so the everyday verify command runs without it.
if (providers.gradleProperty("bp.benchmark").isPresent) {
    include(":benchmark")
}
