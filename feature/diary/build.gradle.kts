plugins {
    id("bp.android.library")
    id("bp.android.compose")
    id("bp.android.hilt")
    id("bp.android.screenshots")
    id("bp.android.navigation")
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.domain)

    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.paging.compose)

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(testFixtures(projects.core.ui))
    testImplementation(testFixtures(projects.domain))
    testImplementation(testFixtures(projects.core.common))
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.paging.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
