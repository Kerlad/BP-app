import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import java.util.Properties

plugins {
    id("bp.android.application")
    id("bp.android.compose")
    id("bp.android.hilt")
    id("bp.android.navigation")
}

// Release secrets (docs/11 §11.3): `keystore.properties` in the repository root (not in git) or, on CI, environment
// variables. Without them the release build is simply not signed, which is enough to build and check it.
val keystoreProperties =
    Properties().apply {
        rootProject
            .file("keystore.properties")
            .takeIf { it.exists() }
            ?.inputStream()
            ?.use { load(it) }
    }

fun releaseSecret(
    property: String,
    environment: String,
): String? = keystoreProperties.getProperty(property) ?: providers.environmentVariable(environment).orNull

val releaseStoreFile = releaseSecret("storeFile", "BP_STORE_FILE")

/** The merged manifest of the release build must not ask for what the app promises not to use (D-04, D-12). */
abstract class VerifyReleaseManifestTask : DefaultTask() {
    @get:InputFile
    abstract val manifest: RegularFileProperty

    @TaskAction
    fun verify() {
        val text = manifest.get().asFile.readText()
        val forbidden =
            listOf(
                "android.permission.INTERNET" to "the app is offline (D-04)",
                "android.permission.USE_EXACT_ALARM" to "only SCHEDULE_EXACT_ALARM is allowed (D-12)",
                "android.permission.FOREGROUND_SERVICE" to "no foreground service is used",
            )
        val found = forbidden.filter { (name, _) -> text.contains("\"$name") }
        check(found.isEmpty()) {
            "The release manifest contains forbidden permissions:\n" +
                found.joinToString("\n") { (name, why) -> "  $name: $why" }
        }
        check(!text.contains("usesCleartextTraffic=\"true\"")) { "Cleartext traffic must stay off" }
    }
}

extensions.configure<ApplicationAndroidComponentsExtension> {
    onVariants(selector().withBuildType("release")) { variant ->
        val verify =
            tasks.register<VerifyReleaseManifestTask>("verifyReleaseManifest") {
                group = "verification"
                description = "Fails when the merged release manifest asks for INTERNET or a foreground service."
                manifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            }
        tasks.named("check") { dependsOn(verify) }
    }
}

android {
    defaultConfig {
        applicationId = "ru.bpdiary.app"
        // Both come from CI at a release (docs/11 §11.4): the commit count and the tag. Never edited by hand.
        versionCode = providers.environmentVariable("BP_VERSION_CODE").orNull?.toIntOrNull() ?: 1
        versionName = providers.environmentVariable("BP_VERSION_NAME").orNull ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The orchestrator wipes the app data before every test: each end-to-end scenario starts on a fresh install.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    // The app forces the Russian locale (MainActivity), so every device needs the default resources.
    bundle {
        language {
            enableSplit = false
        }
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseSecret("storePassword", "BP_STORE_PASSWORD")
                keyAlias = releaseSecret("keyAlias", "BP_KEY_ALIAS")
                keyPassword = releaseSecret("keyPassword", "BP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("boolean", "DEMO_DATA", "true")
        }
        release {
            buildConfigField("boolean", "DEMO_DATA", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        // The build that `:benchmark` measures: like release, signed with the debug key, installed next to it.
        // It also carries the data seeder in src/benchmark; nothing of that is in release.
        create("benchmark") {
            initWith(getByName("release"))
            applicationIdSuffix = ".benchmark"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            isDebuggable = false
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.domain)
    implementation(projects.data.database)
    implementation(projects.data.datastore)
    implementation(projects.data.repository)
    implementation(projects.data.reminders)
    implementation(projects.feature.home)
    implementation(projects.feature.entry)
    implementation(projects.feature.diary)
    implementation(projects.feature.charts)
    implementation(projects.feature.reminders)
    implementation(projects.feature.levels)
    implementation(projects.feature.settings)
    implementation(projects.feature.onboarding)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.core.splashscreen)
    // Installs the baseline profile at the first start and lets the benchmarks compile with it.
    implementation(libs.androidx.profileinstaller)

    // 2 000 demo readings for the benchmarks only (docs/10 §10.7).
    "benchmarkImplementation"(testFixtures(projects.core.common))

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(testFixtures(projects.core.ui))
    testImplementation(testFixtures(projects.domain))
    testImplementation(testFixtures(projects.core.common))
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.espresso.core)

    // End-to-end scenarios of docs/10 §10.5 on an emulator (`connectedDebugAndroidTest`).
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestUtil(libs.androidx.test.orchestrator)
}
