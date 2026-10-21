plugins {
    id("bp.android.library")
    id("bp.android.compose")
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.domain)
}
