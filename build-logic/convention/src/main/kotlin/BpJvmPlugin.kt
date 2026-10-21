import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class BpJvmPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        configureQuality()

        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(version("jdk").toInt())
        }
        // Type resolution is needed for ForbiddenMethodCall; plain `detekt` runs without it.
        tasks.named("detekt") { dependsOn("detektMain", "detektTest") }
        tasks.withType<Test>().configureEach { useJUnitPlatform() }
        dependencies {
            add("testImplementation", platform(libs.findLibrary("junit-bom").get()))
            add("testImplementation", libs.findLibrary("junit-jupiter").get())
            add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
        }
    }
}
