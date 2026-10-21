import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal const val ROOT_NAMESPACE = "ru.bpdiary.app"

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.version(alias: String): String = libs.findVersion(alias).get().requiredVersion

/** `:feature:home` -> `ru.bpdiary.app.feature.home`; the app module keeps the root namespace. */
internal fun Project.moduleNamespace(): String {
    val suffix = path.removePrefix(":").replace(":", ".").replace("-", "")
    return if (suffix == "app") ROOT_NAMESPACE else "$ROOT_NAMESPACE.$suffix"
}
