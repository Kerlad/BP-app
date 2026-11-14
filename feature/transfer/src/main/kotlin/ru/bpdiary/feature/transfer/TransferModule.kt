package ru.bpdiary.feature.transfer

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.bpdiary.core.common.TimeProvider
import ru.bpdiary.domain.ReadingImporter
import ru.bpdiary.domain.ReadingRepository
import ru.bpdiary.domain.SettingsRepository
import ru.bpdiary.domain.csv.CsvAnalyzer
import ru.bpdiary.domain.csv.ImportCsvUseCase
import java.util.Locale

@Module
@InstallIn(SingletonComponent::class)
internal object TransferProvidesModule {
    /** The lint check is about language splits of a bundle, and the app has them off (app/build.gradle.kts). */
    @SuppressLint("AppBundleLocaleChanges")
    @Provides
    fun resources(
        @ApplicationContext context: Context,
    ): Resources {
        // The app is Russian whatever the phone says (see MainActivity): plurals follow the configuration locale.
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("ru")) }
        return context.createConfigurationContext(config).resources
    }

    @Provides
    fun analyzer(
        resources: Resources,
        time: TimeProvider,
    ): CsvAnalyzer = CsvAnalyzer(csvVocabulary(resources), time)

    @Provides
    fun importUseCase(
        analyzer: CsvAnalyzer,
        readings: ReadingRepository,
        importer: ReadingImporter,
        settings: SettingsRepository,
    ): ImportCsvUseCase = ImportCsvUseCase(analyzer, readings, importer, settings)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TransferBindingsModule {
    @Binds
    abstract fun importWorkspace(impl: AndroidImportWorkspace): ImportWorkspace

    @Binds
    abstract fun exportFiles(impl: AndroidExportFiles): ExportFiles

    @Binds
    abstract fun reportRenderer(impl: PdfReportRenderer): ReportRenderer
}
