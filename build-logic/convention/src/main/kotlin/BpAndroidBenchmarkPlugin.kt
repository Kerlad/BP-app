import com.android.build.api.dsl.TestExtension
import com.android.build.api.variant.TestAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** The `:benchmark` module: a self-instrumenting test APK that measures the `benchmark` build of `:app`. */
class BpAndroidBenchmarkPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.test")
        extensions.configure<TestExtension> {
            configureAndroidCommon(this)
            // Not the plain module namespace: the test APK is self-instrumenting and must not share its package
            // with the `.benchmark` build of the app it measures.
            namespace = "${moduleNamespace()}.macro"
            defaultConfig.targetSdk = version("targetSdk").toInt()
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            targetProjectPath = ":app"
            // The test APK instruments itself, not the app: Macrobenchmark stops and starts the app process.
            experimentalProperties["android.experimental.self-instrumenting"] = true
            buildTypes {
                create("benchmark") {
                    isDebuggable = true
                    signingConfig = signingConfigs.getByName("debug")
                    matchingFallbacks += "release"
                }
            }
        }
        // Only the benchmark build is measured: no debug variant of this module.
        extensions.configure<TestAndroidComponentsExtension> {
            beforeVariants { variant -> variant.enable = variant.buildType == "benchmark" }
        }
    }
}
