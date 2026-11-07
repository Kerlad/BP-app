package ru.bpdiary.feature.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withContext
import ru.bpdiary.domain.SettingsRepository
import ru.bpdiary.domain.csv.CsvAnalyzer
import ru.bpdiary.domain.csv.CsvEncoding
import ru.bpdiary.domain.csv.CsvInspection
import ru.bpdiary.domain.csv.CsvLimits
import ru.bpdiary.domain.csv.CsvOptions
import ru.bpdiary.domain.csv.CsvSource
import ru.bpdiary.domain.csv.ImportReport
import ru.bpdiary.domain.csv.RowError
import ru.bpdiary.domain.csv.RowProblem
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject

private const val BUFFER_BYTES = 64 * 1024
private const val IMPORT_DIR = "import"
private const val SOURCE_FILE = "source.csv"
private const val ERRORS_FILE = "errors.txt"
private const val EXPORT_DIR = "export"
private const val WORK_NAME = "csv_import"
private const val ERROR_SEPARATOR = '\t'

internal fun importDir(context: Context) = File(context.cacheDir, IMPORT_DIR)

internal fun importSource(context: Context) = File(importDir(context), SOURCE_FILE)

internal fun importErrors(context: Context) = File(importDir(context), ERRORS_FILE)

internal fun RowError.toLine(): String = "$line$ERROR_SEPARATOR$problem"

internal fun rowErrorOf(line: String): RowError? {
    val parts = line.split(ERROR_SEPARATOR)
    val number = parts.firstOrNull()?.toIntOrNull()
    val problem = RowProblem.entries.firstOrNull { it.name == parts.getOrNull(1) }
    return if (number != null && problem != null) RowError(number, problem) else null
}

sealed interface PickResult {
    data class Copied(
        val name: String,
    ) : PickResult

    data class Failed(
        val problem: FileProblem,
    ) : PickResult
}

sealed interface ImportProgress {
    data class Running(
        val fraction: Float?,
    ) : ImportProgress

    data class Done(
        val report: ImportReport,
    ) : ImportProgress

    data object Failed : ImportProgress
}

/** Everything of the import that touches files and the background work; the view model stays testable with a fake. */
interface ImportWorkspace {
    /** Copies the chosen file to the cache: the permission for it may not outlive the screen. */
    suspend fun pick(uri: String): PickResult

    suspend fun detect(
        encoding: CsvEncoding? = null,
        delimiter: Char? = null,
    ): CsvOptions

    suspend fun inspect(options: CsvOptions): CsvInspection

    fun run(
        options: CsvOptions,
        rows: Int,
        replaceAll: Boolean,
    ): Flow<ImportProgress>

    /** Every bad row of the last run. */
    suspend fun errors(): List<RowError>

    suspend fun saveText(
        uri: String,
        text: String,
    )

    /** Nothing about readings stays in the cache (docs/09 §9.1). */
    fun clear()
}

class AndroidImportWorkspace
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val analyzer: CsvAnalyzer,
        private val settings: SettingsRepository,
    ) : ImportWorkspace {
        private val source = CsvSource { importSource(context).inputStream() }

        override suspend fun pick(uri: String): PickResult =
            withContext(Dispatchers.IO) {
                try {
                    copy(uri.toUri())
                } catch (_: IOException) {
                    PickResult.Failed(FileProblem.UNREADABLE)
                } catch (_: SecurityException) {
                    PickResult.Failed(FileProblem.UNREADABLE)
                }
            }

        private fun copy(uri: Uri): PickResult {
            val target = importSource(context)
            target.parentFile?.mkdirs()
            val input = context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
            val copied =
                input.use { stream ->
                    target.outputStream().use { out ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        var total = 0L
                        var read = stream.read(buffer)
                        while (read != -1 && total <= CsvLimits.MAX_FILE_BYTES) {
                            out.write(buffer, 0, read)
                            total += read
                            read = stream.read(buffer)
                        }
                        total
                    }
                }
            if (copied > CsvLimits.MAX_FILE_BYTES) {
                target.delete()
                return PickResult.Failed(FileProblem.TOO_LARGE)
            }
            return PickResult.Copied(displayName(uri))
        }

        private fun displayName(uri: Uri): String =
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                .orEmpty()

        override suspend fun detect(
            encoding: CsvEncoding?,
            delimiter: Char?,
        ): CsvOptions =
            withContext(Dispatchers.IO) {
                if (encoding != null) analyzer.detect(source, encoding, delimiter) else analyzer.detect(source)
            }

        override suspend fun inspect(options: CsvOptions): CsvInspection {
            val arm = settings.lastArm.first()
            return withContext(Dispatchers.IO) { analyzer.inspect(source, options, arm) }
        }

        override fun run(
            options: CsvOptions,
            rows: Int,
            replaceAll: Boolean,
        ): Flow<ImportProgress> {
            val request =
                OneTimeWorkRequestBuilder<ImportWorker>()
                    .setInputData(options.toData(rows, replaceAll))
                    .build()
            val manager = WorkManager.getInstance(context)
            manager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            return manager
                .getWorkInfoByIdFlow(request.id)
                .filterNotNull()
                .map { it.toProgress() }
                .transformWhile { progress ->
                    emit(progress)
                    progress is ImportProgress.Running
                }
        }

        private fun WorkInfo.toProgress(): ImportProgress =
            when (state) {
                WorkInfo.State.SUCCEEDED -> ImportProgress.Done(outputData.toReport())
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> ImportProgress.Failed
                else -> ImportProgress.Running(progress.toFraction())
            }

        override suspend fun errors(): List<RowError> =
            withContext(Dispatchers.IO) {
                val file = importErrors(context)
                if (file.exists()) file.readLines().mapNotNull { rowErrorOf(it) } else emptyList()
            }

        override suspend fun saveText(
            uri: String,
            text: String,
        ) = withContext(Dispatchers.IO) {
            val out = context.contentResolver.openOutputStream(uri.toUri()) ?: throw FileNotFoundException(uri)
            out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }

        override fun clear() {
            importDir(context).deleteRecursively()
        }
    }

/** Where the export goes: a place the user chose, or a temporary file to share. */
interface ExportFiles {
    suspend fun save(
        uri: String,
        bytes: ByteArray,
    )

    /** A content address that the app receiving the file may read. */
    suspend fun forShare(
        fileName: String,
        bytes: ByteArray,
    ): String

    /** Nothing about readings stays in the cache (docs/09 §9.2). */
    fun clearShared()
}

class AndroidExportFiles
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : ExportFiles {
        override suspend fun save(
            uri: String,
            bytes: ByteArray,
        ) = withContext(Dispatchers.IO) {
            try {
                val out =
                    context.contentResolver.openOutputStream(uri.toUri(), "wt") ?: throw FileNotFoundException(uri)
                out.use { it.write(bytes) }
            } catch (e: SecurityException) {
                throw IOException(e)
            }
        }

        override suspend fun forShare(
            fileName: String,
            bytes: ByteArray,
        ): String =
            withContext(Dispatchers.IO) {
                clearShared()
                val dir = File(context.cacheDir, EXPORT_DIR)
                dir.mkdirs()
                val file = File(dir, fileName)
                file.writeBytes(bytes)
                FileProvider.getUriForFile(context, "${context.packageName}.csvfiles", file).toString()
            }

        override fun clearShared() {
            File(context.cacheDir, EXPORT_DIR).deleteRecursively()
        }
    }
