import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension

internal fun Project.configureAndroidCommon(android: CommonExtension) {
    android.apply {
        compileSdk = version("compileSdk").toInt()
        defaultConfig.minSdk = version("minSdk").toInt()
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
        lint.warningsAsErrors = true
        lint.abortOnError = true
        // "Newer version available" checks depend on network access, so the same commit passed offline and
        // failed online. Versions are bumped on purpose after checking release pages (CLAUDE.md).
        lint.disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
    }
    extensions.configure<KotlinBaseExtension> {
        jvmToolchain(version("jdk").toInt())
    }
    configureQuality()
}
