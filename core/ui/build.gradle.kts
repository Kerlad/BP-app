plugins {
    id("bp.android.library")
    id("bp.android.compose")
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
}
