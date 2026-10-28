plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.agp.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.compose.compiler.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.hilt.gradle.plugin)
    implementation(libs.room.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.roborazzi.gradle.plugin)
    implementation(libs.kotlin.serialization.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("bpJvm") {
            id = "bp.jvm"
            implementationClass = "BpJvmPlugin"
        }
        register("bpAndroidApplication") {
            id = "bp.android.application"
            implementationClass = "BpAndroidApplicationPlugin"
        }
        register("bpAndroidLibrary") {
            id = "bp.android.library"
            implementationClass = "BpAndroidLibraryPlugin"
        }
        register("bpAndroidCompose") {
            id = "bp.android.compose"
            implementationClass = "BpAndroidComposePlugin"
        }
        register("bpAndroidHilt") {
            id = "bp.android.hilt"
            implementationClass = "BpAndroidHiltPlugin"
        }
        register("bpAndroidScreenshots") {
            id = "bp.android.screenshots"
            implementationClass = "BpAndroidScreenshotsPlugin"
        }
        register("bpAndroidNavigation") {
            id = "bp.android.navigation"
            implementationClass = "BpAndroidNavigationPlugin"
        }
        register("bpAndroidRoom") {
            id = "bp.android.room"
            implementationClass = "BpAndroidRoomPlugin"
        }
    }
}
