package ru.bpdiary.feature.transfer

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.bpdiary.core.model.Arm
import ru.bpdiary.domain.csv.ColumnRole
import ru.bpdiary.domain.csv.CsvEncoding
import ru.bpdiary.domain.csv.CsvLimitException
import ru.bpdiary.domain.csv.CsvOptions
import ru.bpdiary.domain.csv.CsvSource
import ru.bpdiary.domain.csv.ImportCsvUseCase
import ru.bpdiary.domain.csv.ImportReport
import java.io.IOException
import java.time.LocalDate

private const val KEY_ENCODING = "encoding"
private const val KEY_DELIMITER = "delimiter"
private const val KEY_HEADER = "header"
private const val KEY_COLUMNS = "columns"
private const val KEY_DAY_FIRST = "dayFirst"
private const val KEY_ARM_COMMENT = "armFromComment"
private const val KEY_ROWS = "rows"
private const val KEY_REPLACE = "replace"
private const val KEY_FRACTION = "fraction"
private const val KEY_IMPORTED = "imported"
private const val KEY_DUPLICATES = "duplicates"
private const val KEY_ERRORS = "errors"
private const val KEY_ARM_FROM_COMMENT = "reportArmComment"
private const val KEY_ARM_DEFAULTED = "armDefaulted"
private const val KEY_DEFAULT_ARM = "defaultArm"
private const val KEY_CUT = "cut"
private const val KEY_AHEAD = "ahead"
private const val KEY_NO_TIME = "noTime"
private const val KEY_FROM = "from"
private const val KEY_TO = "to"
private const val NO_DAY = Long.MIN_VALUE
private const val COLUMN_SEPARATOR = ","

/** How many rows pass between two progress updates: the progress is a courtesy, not worth slowing the import down. */
private const val PROGRESS_EVERY_ROWS = 500

internal fun CsvOptions.toData(
    rows: Int,
    replaceAll: Boolean,
): Data =
    workDataOf(
        KEY_ENCODING to encoding.name,
        KEY_DELIMITER to delimiter.code,
        KEY_HEADER to hasHeader,
        KEY_COLUMNS to columns.joinToString(COLUMN_SEPARATOR) { it.name },
        KEY_DAY_FIRST to dayFirst,
        KEY_ARM_COMMENT to armFromComment,
        KEY_ROWS to rows,
        KEY_REPLACE to replaceAll,
    )

internal fun Data.toOptions(): CsvOptions =
    CsvOptions(
        encoding = CsvEncoding.valueOf(getString(KEY_ENCODING).orEmpty()),
        delimiter = getInt(KEY_DELIMITER, 0).toChar(),
        hasHeader = getBoolean(KEY_HEADER, false),
        columns =
            getString(KEY_COLUMNS)
                .orEmpty()
                .split(COLUMN_SEPARATOR)
                .filter { it.isNotEmpty() }
                .map { ColumnRole.valueOf(it) },
        dayFirst = getBoolean(KEY_DAY_FIRST, true),
        armFromComment = getBoolean(KEY_ARM_COMMENT, true),
    )

internal fun ImportReport.toData(): Data =
    workDataOf(
        KEY_IMPORTED to imported,
        KEY_DUPLICATES to duplicates,
        KEY_ERRORS to errorCount,
        KEY_ARM_FROM_COMMENT to armFromComment,
        KEY_ARM_DEFAULTED to armDefaulted,
        KEY_DEFAULT_ARM to defaultArm.name,
        KEY_CUT to commentsCut,
        KEY_AHEAD to aheadOfClock,
        KEY_NO_TIME to withoutTime,
        KEY_FROM to (from?.toEpochDay() ?: NO_DAY),
        KEY_TO to (to?.toEpochDay() ?: NO_DAY),
    )

/** The bad rows stay in a file: the output of a worker is limited to 10 KB. */
internal fun Data.toReport(): ImportReport =
    ImportReport(
        imported = getInt(KEY_IMPORTED, 0),
        duplicates = getInt(KEY_DUPLICATES, 0),
        errorCount = getInt(KEY_ERRORS, 0),
        armFromComment = getInt(KEY_ARM_FROM_COMMENT, 0),
        armDefaulted = getInt(KEY_ARM_DEFAULTED, 0),
        defaultArm = Arm.valueOf(getString(KEY_DEFAULT_ARM) ?: Arm.LEFT.name),
        commentsCut = getInt(KEY_CUT, 0),
        aheadOfClock = getInt(KEY_AHEAD, 0),
        withoutTime = getInt(KEY_NO_TIME, 0),
        from = getLong(KEY_FROM, NO_DAY).toDay(),
        to = getLong(KEY_TO, NO_DAY).toDay(),
        errors = emptyList(),
    )

private fun Long.toDay(): LocalDate? = if (this == NO_DAY) null else LocalDate.ofEpochDay(this)

internal fun Data.toFraction(): Float? = if (KEY_FRACTION in keyValueMap) getFloat(KEY_FRACTION, 0f) else null

/**
 * Reads and writes outside the screen (docs/13 §13.4): turning the phone or leaving the app does not stop an import of
 * 200 000 rows. It works on the copy of the file in the cache. Being one transaction, a repeated run after a killed
 * process finds nothing half-done.
 */
@HiltWorker
class ImportWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val import: ImportCsvUseCase,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                val result =
                    try {
                        Result.success(run().toData())
                    } catch (e: CancellationException) {
                        // Stopped by the system: it runs the work again and needs the file for that.
                        throw e
                    } catch (_: IOException) {
                        Result.failure()
                    } catch (_: CsvLimitException) {
                        Result.failure()
                    } catch (_: IllegalStateException) {
                        Result.failure()
                    } catch (_: android.database.SQLException) {
                        Result.failure()
                    }
                // Nothing about readings stays in the cache (docs/09 §9.2); the bad rows are only numbers and reasons.
                importSource(applicationContext).delete()
                result
            }

        private suspend fun run(): ImportReport {
            val source = CsvSource { importSource(applicationContext).inputStream() }
            val total = inputData.getInt(KEY_ROWS, 0).coerceAtLeast(1)
            return importErrors(applicationContext).bufferedWriter().use { errors ->
                import(
                    source = source,
                    options = inputData.toOptions(),
                    replaceAll = inputData.getBoolean(KEY_REPLACE, false),
                    onRow = { read ->
                        if (read % PROGRESS_EVERY_ROWS == 0) {
                            setProgressAsync(workDataOf(KEY_FRACTION to read.toFloat() / total))
                        }
                    },
                    onError = { errors.appendLine(it.toLine()) },
                )
            }
        }
    }
