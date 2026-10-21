plugins {
    id("bp.android.application")
    id("bp.android.compose")
    id("bp.android.hilt")
}

android {
    defaultConfig {
        applicationId = "ru.bpdiary.app"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("boolean", "DEMO_DATA", "true")
        }
        release {
            buildConfigField("boolean", "DEMO_DATA", "false")
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
    implementation(libs.androidx.hilt.navigation.compose)
}
