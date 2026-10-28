plugins {
    id("bp.android.library")
    id("bp.android.compose")
    id("bp.android.screenshots")
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    api(projects.domain)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // ui-test-manifest pulls an old androidx.activity; activity-compose lifts it to the version the app uses.
    debugImplementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
