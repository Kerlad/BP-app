plugins {
    id("bp.android.library")
    id("bp.android.hilt")
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(projects.data.database)
    implementation(projects.data.datastore)
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.domain)
    implementation(libs.room.ktx)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.runtime)
    testImplementation(libs.paging.testing)
    testImplementation(testFixtures(projects.domain))
}
