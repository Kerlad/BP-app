package ru.bpdiary.domain.csv

import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Tag
import java.io.InputStream
import java.time.LocalDate

/** Limits of the import (docs/13 §13.2): beyond them the file is refused with a clear text. */
object CsvLimits {
    const val MAX_FILE_BYTES = 20L * 1024 * 1024
    const val MAX_FIELD_CHARS = 4 * 1024
    const val MAX_ROWS = 200_000

    /** The bad rows shown as text; the rest is a count and the saved report (docs/13 §13.2). */
    const val MAX_ERRORS_SHOWN = 10

    /** Comments are cut to this length, the report counts them (docs/13 §13.2). */
    const val MAX_COMMENT_CHARS = 120

    /** An imported reading may be ahead of the clock by this much: another app may have used another zone. */
    const val MAX_FUTURE_HOURS = 24L
}

enum class CsvLimit { FIELD, ROWS }

class CsvLimitException(
    val limit: CsvLimit,
) : Exception("CSV limit exceeded: $limit")

/** The file can be opened any number of times: it is read once to analyse it and once to import it. */
fun interface CsvSource {
    fun open(): InputStream
}

enum class CsvEncoding(
    val charsetName: String,
) {
    UTF_8("UTF-8"),
    UTF_16LE("UTF-16LE"),
    UTF_16BE("UTF-16BE"),
    WINDOWS_1251("windows-1251"),
}

/** What a column of the file holds. [TIMESTAMP] is the lossless last column of our own export. */
enum class ColumnRole { DATE_TIME, DATE, TIME, TIMESTAMP, SYS, DIA, PULSE, ARM, TAGS, COMMENT, IGNORE }

/**
 * The words the import recognises, in lower case. They live in resources (golden rule 3) and come in from outside;
 * [tagLabels] are the labels of the feeling tags.
 */
data class CsvVocabulary(
    val dateTime: Set<String>,
    val date: Set<String>,
    val time: Set<String>,
    val timestamp: Set<String>,
    val sys: Set<String>,
    val dia: Set<String>,
    val pulse: Set<String>,
    val arm: Set<String>,
    val tags: Set<String>,
    val comment: Set<String>,
    val ignored: Set<String>,
    val leftWords: Set<String>,
    val rightWords: Set<String>,
    val tagLabels: Map<Tag, String>,
)

/** How to read a file: found by [CsvAnalyzer.detect], the user may change any of it in the preview. */
data class CsvOptions(
    val encoding: CsvEncoding,
    val delimiter: Char,
    val hasHeader: Boolean,
    val columns: List<ColumnRole>,
    /** `dd/MM` rather than `MM/dd`. */
    val dayFirst: Boolean = true,
    val armFromComment: Boolean = true,
)

enum class RowProblem { NOT_ENOUGH_COLUMNS, BAD_DATE, BAD_SYS, BAD_DIA, BAD_PULSE, SYS_NOT_GREATER, TOO_FAR_AHEAD }

data class RowError(
    val line: Int,
    val problem: RowProblem,
)

/** Why the file cannot be imported as it is mapped now (docs/13 §13.5). */
enum class CsvProblem { EMPTY, NO_PRESSURE_COLUMNS, NO_DATES }

/** The preview: the first rows, how many rows can be read with these options, and what is wrong with the others. */
data class CsvInspection(
    val header: List<String>?,
    val preview: List<List<String>>,
    val rows: Int,
    val valid: Int,
    /** The first errors; [errorCount] counts all of them. */
    val errors: List<RowError>,
    val errorCount: Int,
    /** All `dd/MM` dates of the file are at most the 12th: only the user can tell the order. */
    val ambiguousDayOrder: Boolean,
    val problem: CsvProblem?,
)

data class ImportReport(
    val imported: Int,
    val duplicates: Int,
    val errorCount: Int,
    val armFromComment: Int,
    val armDefaulted: Int,
    val defaultArm: Arm,
    val commentsCut: Int,
    val aheadOfClock: Int,
    val withoutTime: Int,
    val from: LocalDate?,
    val to: LocalDate?,
    val errors: List<RowError>,
)

/** Everything the export writes in words; the lines and names come from resources (golden rule 3). */
data class CsvExportTexts(
    /** The `#` lines without the sign. */
    val metaLines: List<String>,
    val columns: List<String>,
    val arms: Map<Arm, String>,
    val statuses: Map<Status, String>,
    val tags: Map<Tag, String>,
)
