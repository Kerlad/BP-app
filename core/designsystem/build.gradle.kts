plugins {
    id("bp.android.library")
    id("bp.android.compose")
}

dependencies {
    api(projects.core.model)

    testImplementation(libs.junit4)
}
