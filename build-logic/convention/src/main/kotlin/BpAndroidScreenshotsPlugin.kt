import org.gradle.api.Plugin
import org.gradle.api.Project

/** Roborazzi screenshot tests (`recordRoborazziDebug`, `verifyRoborazziDebug`). Applied here so no module sets a version. */
class BpAndroidScreenshotsPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.github.takahirom.roborazzi")
    }
}
