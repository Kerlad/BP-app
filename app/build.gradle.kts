plugins {
    id("bp.android.application")
    id("bp.android.compose")
    id("bp.android.hilt")
    id("bp.android.navigation")
}

android {
    defaultConfig {
        applicationId = "ru.bpdiary.app"
        versionCode = 1
        versionName = "0.1.0"
    }

    // The app forces the Russian locale (MainActivity), so every device needs the default resources.
    bundle {
        language {
            enableSplit = false
        }
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions.unitTests.isIncludeAndroidResources = true

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("boolean", "DEMO_DATA", "true")
        }
        release {
            buildConfigField("boolean", "DEMO_DATA", "false")
        }
        // The build that `:benchmark` measures: like release, signed with the debug key, installed next to it.
        // It also carries the data seeder in src/benchmark; nothing of that is in release.
        create("benchmark") {
            initWith(getByName("release"))
            applicationIdSuffix = ".benchmark"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            isDebuggable = false
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.domain)
    implementation(projects.data.database)
    implementation(projects.data.datastore)
    implementation(projects.data.repository)
    implementation(projects.data.reminders)
    implementation(projects.feature.home)
    implementation(projects.feature.entry)
    implementation(projects.feature.diary)
    implementation(projects.feature.charts)
    implementation(projects.feature.reminders)
    implementation(projects.feature.levels)
    implementation(projects.feature.settings)
    implementation(projects.feature.onboarding)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.core.splashscreen)
    // Installs the baseline profile at the first start and lets the benchmarks compile with it.
    implementation(libs.androidx.profileinstaller)

    // 2 000 demo readings for the benchmarks only (docs/10 §10.7).
    "benchmarkImplementation"(testFixtures(projects.core.common))

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(testFixtures(projects.core.ui))
    testImplementation(testFixtures(projects.domain))
    testImplementation(testFixtures(projects.core.common))
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.espresso.core)
}
