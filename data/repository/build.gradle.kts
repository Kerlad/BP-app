plugins {
    id("bp.android.library")
}

dependencies {
    implementation(projects.data.database)
    implementation(projects.data.datastore)
    implementation(projects.domain)
    implementation(projects.core.common)
}
