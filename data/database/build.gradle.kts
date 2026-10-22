plugins {
    id("bp.android.library")
    id("bp.android.room")
}

android {
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(projects.core.model)
    api(libs.room.paging)
    api(libs.paging.common)

    testImplementation(projects.domain)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
