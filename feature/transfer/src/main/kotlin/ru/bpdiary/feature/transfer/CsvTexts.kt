package ru.bpdiary.feature.transfer

import android.content.res.Resources
import androidx.annotation.ArrayRes
import ru.bpdiary.app.feature.transfer.R
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Tag
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.core.ui.labelRes
import ru.bpdiary.domain.csv.ColumnRole
import ru.bpdiary.domain.csv.CsvExportTexts
import ru.bpdiary.domain.csv.CsvProblem
import ru.bpdiary.domain.csv.CsvVocabulary
import ru.bpdiary.domain.csv.ImportReport
import ru.bpdiary.domain.csv.RowError
import ru.bpdiary.domain.csv.RowProblem
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu")

/** The words the import recognises, from resources. */
fun csvVocabulary(resources: Resources): CsvVocabulary {
    fun words(
        @ArrayRes id: Int,
    ) = resources.getStringArray(id).toSet()
    return CsvVocabulary(
        dateTime = words(R.array.csv_words_datetime),
        date = words(R.array.csv_words_date),
        time = words(R.array.csv_words_time),
        timestamp = words(R.array.csv_words_timestamp),
        sys = words(R.array.csv_words_sys),
        dia = words(R.array.csv_words_dia),
        pulse = words(R.array.csv_words_pulse),
        arm = words(R.array.csv_words_arm),
        tags = words(R.array.csv_words_tags),
        comment = words(R.array.csv_words_comment),
        ignored = words(R.array.csv_words_ignored),
        leftWords = words(R.array.csv_words_left),
        rightWords = words(R.array.csv_words_right),
        tagLabels = Tag.entries.associateWith { resources.getString(it.labelRes()) },
    )
}

/** The words of the export file. */
fun csvExportTexts(
    resources: Resources,
    today: LocalDate,
    period: ClosedRange<LocalDate>,
    count: Int,
    th: Thresholds,
): CsvExportTexts =
    CsvExportTexts(
        metaLines =
            listOf(
                resources.getString(R.string.csv_meta_title, DATE.format(today)),
                resources.getString(
                    R.string.csv_meta_period,
                    DATE.format(period.start),
                    DATE.format(period.endInclusive),
                    count,
                ),
                resources.getString(
                    R.string.csv_meta_levels,
                    th.highSys,
                    th.highDia,
                    th.lowSys,
                    th.lowDia,
                    th.pulseLow,
                    th.pulseHigh,
                ),
                resources.getString(R.string.csv_meta_status),
            ),
        columns = resources.getStringArray(R.array.csv_columns).toList(),
        arms = Arm.entries.associateWith { resources.getString(it.labelRes()) },
        statuses = Status.entries.associateWith { resources.getString(it.labelRes()) },
        tags = Tag.entries.associateWith { resources.getString(it.labelRes()) },
    )

internal fun Arm.labelRes(): Int = if (this == Arm.LEFT) R.string.csv_arm_left else R.string.csv_arm_right

/** The file name of docs/13 §13.3: Latin, not every messenger is friendly to Cyrillic names. */
internal fun exportFileName(
    from: LocalDate,
    to: LocalDate,
    format: ExportFormat = ExportFormat.CSV,
): String = "davlenie_${from}_$to.${format.extension}"

/** Formatted texts of the import that are not plain string resources. */
class ImportTexts(
    private val resources: Resources,
) {
    fun date(day: LocalDate): String = DATE.format(day)

    fun role(role: ColumnRole): String =
        resources.getString(
            when (role) {
                ColumnRole.DATE_TIME -> R.string.import_role_datetime
                ColumnRole.DATE -> R.string.import_role_date
                ColumnRole.TIME -> R.string.import_role_time
                ColumnRole.TIMESTAMP -> R.string.import_role_timestamp
                ColumnRole.SYS -> R.string.import_role_sys
                ColumnRole.DIA -> R.string.import_role_dia
                ColumnRole.PULSE -> R.string.import_role_pulse
                ColumnRole.ARM -> R.string.import_role_arm
                ColumnRole.TAGS -> R.string.import_role_tags
                ColumnRole.COMMENT -> R.string.import_role_comment
                ColumnRole.IGNORE -> R.string.import_role_ignore
            },
        )

    fun delimiter(delimiter: Char): String =
        resources.getString(
            when (delimiter) {
                ',' -> R.string.import_delimiter_comma
                '\t' -> R.string.import_delimiter_tab
                '|' -> R.string.import_delimiter_pipe
                else -> R.string.import_delimiter_semicolon
            },
        )

    fun problem(problem: CsvProblem): String =
        resources.getString(
            when (problem) {
                CsvProblem.EMPTY -> R.string.import_error_empty
                CsvProblem.NO_PRESSURE_COLUMNS -> R.string.import_error_no_columns
                CsvProblem.NO_DATES -> R.string.import_error_no_dates
            },
        )

    fun fileProblem(problem: FileProblem): String =
        resources.getString(
            when (problem) {
                FileProblem.UNREADABLE -> R.string.import_error_open
                FileProblem.TOO_LARGE -> R.string.import_error_large
                FileProblem.TOO_MANY_ROWS -> R.string.import_error_rows
            },
        )

    /** «Строка 4: верхнее значение не больше нижнего». */
    fun rowError(error: RowError): String {
        val problem =
            when (error.problem) {
                RowProblem.NOT_ENOUGH_COLUMNS -> R.string.import_problem_columns
                RowProblem.BAD_DATE -> R.string.import_problem_date
                RowProblem.BAD_SYS -> R.string.import_problem_sys
                RowProblem.BAD_DIA -> R.string.import_problem_dia
                RowProblem.BAD_PULSE -> R.string.import_problem_pulse
                RowProblem.SYS_NOT_GREATER -> R.string.import_problem_order
                RowProblem.TOO_FAR_AHEAD -> R.string.import_problem_ahead
            }
        return resources.getString(R.string.import_row_error, error.line, resources.getString(problem))
    }

    /** The report of docs/13 §13.2; the lines beyond the fixed ones appear only when they have something to say. */
    fun report(report: ImportReport): List<String> =
        buildList {
            add(resources.getQuantityString(R.plurals.import_report_imported, report.imported, report.imported))
            add(resources.getString(R.string.import_report_duplicates, report.duplicates))
            add(resources.getString(R.string.import_report_errors, report.errorCount))
            add(resources.getString(R.string.import_report_arm_comment, report.armFromComment))
            add(
                resources.getString(
                    R.string.import_report_arm_default,
                    resources.getString(report.defaultArm.labelRes()),
                    report.armDefaulted,
                ),
            )
            if (report.commentsCut > 0) add(resources.getString(R.string.import_report_cut, report.commentsCut))
            if (report.aheadOfClock > 0) add(resources.getString(R.string.import_report_ahead, report.aheadOfClock))
            if (report.withoutTime > 0) add(resources.getString(R.string.import_report_no_time, report.withoutTime))
            val from = report.from
            val to = report.to
            if (from != null && to != null) {
                add(resources.getString(R.string.import_report_period, date(from), date(to)))
            }
        }

    /** What «Сохранить отчёт» writes: the report and every bad row. */
    fun reportFile(
        report: ImportReport,
        errors: List<RowError>,
    ): String = (report(report) + "" + errors.map { rowError(it) }).joinToString("\r\n", postfix = "\r\n")

    /** «Импортировано 1 180 из 1 240. 60 строк пропущено — показать?», when some rows were bad. */
    fun partial(report: ImportReport): String? {
        val total = report.imported + report.duplicates + report.errorCount
        return if (report.errorCount == 0) {
            null
        } else {
            resources.getString(R.string.import_error_partial, report.imported, total, report.errorCount)
        }
    }
}
