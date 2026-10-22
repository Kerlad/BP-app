plugins {
    id("bp.jvm")
    `java-test-fixtures`
}

dependencies {
    api(projects.core.model)
    api(projects.core.common)
    api(libs.kotlinx.coroutines.core)

    testFixturesApi(projects.core.model)
    testFixturesApi(projects.core.common)
    testFixturesApi(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(testFixtures(projects.core.common))
}
