plugins {
    id("bp.jvm")
    `java-test-fixtures`
}

dependencies {
    implementation(projects.core.model)
    testFixturesApi(projects.core.model)
}
