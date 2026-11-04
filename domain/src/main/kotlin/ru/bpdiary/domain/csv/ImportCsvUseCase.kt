package ru.bpdiary.domain.csv

import kotlinx.coroutines.flow.first
import ru.bpdiary.core.common.TimeProvider
import ru.bpdiary.core.model.Reading
import ru.bpdiary.domain.ReadingImporter
import ru.bpdiary.domain.ReadingRepository
import ru.bpdiary.domain.SettingsRepository
import java.time.Instant

private const val SECONDS_IN_MINUTE = 60L

/** The duplicate key of docs/13 §13.2: arm and comment are left out, so an edited comment does not make a double. */
private data class ReadingKey(
    val minute: Long,
    val sys: Int,
    val dia: Int,
    val pulse: Int?,
)

private fun Reading.key() = ReadingKey(takenAt.epochSecond / SECONDS_IN_MINUTE, sys, dia, pulse)

/**
 * The file into the diary: read, drop the duplicates, write in one transaction. «Заменить всё» replaces the readings
 * only; a failure leaves the diary as it was (docs/13 §13.2).
 */
class ImportCsvUseCase(
    private val analyzer: CsvAnalyzer,
    private val readings: ReadingRepository,
    private val importer: ReadingImporter,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) {
    /** [onRow] is called after each row of the file, [onError] for each row that cannot be imported. */
    suspend operator fun invoke(
        source: CsvSource,
        options: CsvOptions,
        replaceAll: Boolean,
        onRow: (rowsRead: Int) -> Unit = {},
        onError: (RowError) -> Unit = {},
    ): ImportReport {
        val defaultArm = settings.lastArm.first()
        val seen = HashSet<ReadingKey>()
        if (!replaceAll) {
            readings.observeRange(Instant.EPOCH, Instant.ofEpochMilli(Long.MAX_VALUE)).first().mapTo(seen) { it.key() }
        }
        val rows = ArrayList<MappedRow>()
        val errors = ArrayList<RowError>()
        var errorCount = 0
        var duplicates = 0
        var read = 0
        analyzer.mapped(source, options, defaultArm) { result ->
            when (result) {
                is RowResult.Valid -> {
                    if (seen.add(result.row.reading.key())) rows += result.row else duplicates++
                }

                is RowResult.Invalid -> {
                    errorCount++
                    if (errors.size < CsvLimits.MAX_ERRORS_SHOWN) errors += result.error
                    onError(result.error)
                }
            }
            onRow(++read)
        }
        val saved = rows.map { it.reading }
        importer.importAll(saved, replaceAll)
        val days = saved.map { it.takenAt.atZone(time.zone()).toLocalDate() }
        return ImportReport(
            imported = rows.size,
            duplicates = duplicates,
            errorCount = errorCount,
            armFromComment = rows.count { it.armFromComment },
            armDefaulted = rows.count { it.armDefaulted },
            defaultArm = defaultArm,
            commentsCut = rows.count { it.commentCut },
            aheadOfClock = rows.count { it.aheadOfClock },
            withoutTime = rows.count { it.withoutTime },
            from = days.minOrNull(),
            to = days.maxOrNull(),
            errors = errors,
        )
    }
}
