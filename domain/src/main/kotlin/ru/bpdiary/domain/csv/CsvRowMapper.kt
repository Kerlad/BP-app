package ru.bpdiary.domain.csv

import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Tag
import ru.bpdiary.domain.Reason
import ru.bpdiary.domain.ValidationRules
import ru.bpdiary.domain.takeWholeChars
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

private const val PART_SEPARATOR = " · "
private val TAG_SEPARATORS = Regex("""\s*(?:·|,|;)\s*""")
private val WORD_SEPARATORS = Regex("""[^\p{L}\p{N}]+""")
private val REQUIRED_COLUMNS = setOf(ColumnRole.SYS, ColumnRole.DIA)

/** A row that became a reading, and what the report has to say about it. */
data class MappedRow(
    val reading: Reading,
    val armFromComment: Boolean,
    val armDefaulted: Boolean,
    val commentCut: Boolean,
    val aheadOfClock: Boolean,
    val withoutTime: Boolean,
)

sealed interface RowResult {
    data class Valid(
        val row: MappedRow,
    ) : RowResult

    data class Invalid(
        val error: RowError,
    ) : RowResult
}

/** A bad row is an ordinary outcome: no stack trace is filled in, a file may hold thousands of them. */
private class BadRow(
    val problem: RowProblem,
) : Exception(null, null, false, false)

/** One row of the file to one reading, with the validation of manual entry but an optional pulse (docs/13 §13.2). */
class CsvRowMapper(
    private val options: CsvOptions,
    private val vocabulary: CsvVocabulary,
    private val now: Instant,
    zone: ZoneId,
    private val defaultArm: Arm,
) {
    private val dates = CsvDateParser(zone, now.atZone(zone).year, options.dayFirst)
    private val latest = now.plus(Duration.ofHours(CsvLimits.MAX_FUTURE_HOURS))
    private val lastRequired =
        options.columns.indices
            .filter { options.columns[it] in REQUIRED_COLUMNS }
            .maxOrNull() ?: 0

    fun map(row: CsvRow): RowResult =
        try {
            RowResult.Valid(read(row))
        } catch (bad: BadRow) {
            RowResult.Invalid(RowError(row.line, bad.problem))
        }

    private fun read(row: CsvRow): MappedRow {
        val moment = moment(row)
        val notes = notes(row)
        val arm = arm(row, notes.comment)
        val comment = notes.comment.takeWholeChars(CsvLimits.MAX_COMMENT_CHARS)
        val reading =
            Reading(
                takenAt = moment.instant,
                zoneOffset = moment.offset,
                sys = number(row, ColumnRole.SYS, RowProblem.BAD_SYS),
                dia = number(row, ColumnRole.DIA, RowProblem.BAD_DIA),
                pulse = pulse(row),
                arm = arm.first,
                tags = notes.tags,
                comment = comment,
            )
        ValidationRules.validate(reading, latest, requirePulse = false)?.let { throw BadRow(it.reason.toProblem()) }
        return MappedRow(
            reading = reading,
            armFromComment = arm.second == ArmSource.COMMENT,
            armDefaulted = arm.second == ArmSource.DEFAULT,
            commentCut = comment.length < notes.comment.length,
            aheadOfClock = moment.instant.isAfter(now),
            withoutTime = !moment.hasTime,
        )
    }

    private fun number(
        row: CsvRow,
        role: ColumnRole,
        problem: RowProblem,
    ): Int = single(row, role).toIntOrNull() ?: throw BadRow(problem)

    private fun pulse(row: CsvRow): Int? =
        if (single(row, ColumnRole.PULSE).isEmpty()) null else number(row, ColumnRole.PULSE, RowProblem.BAD_PULSE)

    private fun single(
        row: CsvRow,
        role: ColumnRole,
    ): String {
        val index = options.columns.indexOf(role)
        return if (index < 0) {
            ""
        } else {
            row.fields
                .getOrNull(index)
                .orEmpty()
                .trim()
        }
    }

    /** `Метка времени` when it can be read, otherwise date + time, otherwise the one date column. */
    private fun moment(row: CsvRow): CsvMoment {
        if (row.fields.size <= lastRequired) throw BadRow(RowProblem.NOT_ENOUGH_COLUMNS)
        val date = single(row, ColumnRole.DATE)
        val time = single(row, ColumnRole.TIME)
        return dates.parse(single(row, ColumnRole.TIMESTAMP))
            ?: when {
                date.isNotEmpty() && time.isNotEmpty() -> dates.parse("$date $time")
                date.isNotEmpty() -> dates.parse(date)
                else -> dates.parse(single(row, ColumnRole.DATE_TIME))
            }
            ?: throw BadRow(RowProblem.BAD_DATE)
    }

    private data class Notes(
        val tags: Set<Tag>,
        val comment: String,
    )

    /** Tags from their column; an unknown tag goes to the comment, a comment that is exactly a tag goes to the tags. */
    private fun notes(row: CsvRow): Notes {
        val parts = single(row, ColumnRole.TAGS).split(TAG_SEPARATORS).filter { it.isNotBlank() }
        val written =
            options.columns.indices
                .filter { options.columns[it] == ColumnRole.COMMENT }
                .map {
                    row.fields
                        .getOrNull(it)
                        .orEmpty()
                        .withoutFormulaGuard()
                }.filter { it.isNotBlank() }
                .joinToString(PART_SEPARATOR)
        val wholeTag = tagOf(written)
        val comment = parts.filter { tagOf(it) == null } + if (wholeTag == null) listOf(written) else emptyList()
        return Notes(
            tags = (parts.mapNotNull { tagOf(it) } + listOfNotNull(wholeTag)).toSet(),
            comment = comment.filter { it.isNotBlank() }.joinToString(PART_SEPARATOR),
        )
    }

    private fun tagOf(text: String): Tag? {
        val clean = text.trim()
        return vocabulary.tagLabels.entries
            .firstOrNull { (tag, label) -> clean.equals(label, true) || clean.equals(tag.name, true) }
            ?.key
    }

    private enum class ArmSource { COLUMN, COMMENT, DEFAULT }

    private fun arm(
        row: CsvRow,
        comment: String,
    ): Pair<Arm, ArmSource> {
        val fromColumn = vocabulary.armOf(single(row, ColumnRole.ARM))
        val fromComment = if (options.armFromComment) vocabulary.armOf(comment) else null
        return when {
            fromColumn != null -> fromColumn to ArmSource.COLUMN
            fromComment != null -> fromComment to ArmSource.COMMENT
            else -> defaultArm to ArmSource.DEFAULT
        }
    }
}

private fun Reason.toProblem(): RowProblem =
    when (this) {
        Reason.SYS_RANGE -> RowProblem.BAD_SYS
        Reason.DIA_RANGE -> RowProblem.BAD_DIA
        Reason.PULSE_RANGE, Reason.PULSE_MISSING -> RowProblem.BAD_PULSE
        Reason.SYS_NOT_GREATER -> RowProblem.SYS_NOT_GREATER
        Reason.FUTURE_TIME -> RowProblem.TOO_FAR_AHEAD
    }

/** Whole words only: «правильно» sets no arm. Words for both arms in one text set none either. */
private fun CsvVocabulary.armOf(text: String): Arm? {
    val words = text.lowercase().split(WORD_SEPARATORS).filter { it.isNotEmpty() }
    val left = words.any { it in leftWords }
    val right = words.any { it in rightWords }
    return when {
        left && !right -> Arm.LEFT
        right && !left -> Arm.RIGHT
        else -> null
    }
}
