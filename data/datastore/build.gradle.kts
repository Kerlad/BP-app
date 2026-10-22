plugins {
    id("bp.android.library")
}

dependencies {
    implementation(projects.core.model)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
