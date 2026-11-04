package ru.bpdiary.domain.csv

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

private const val DEFAULT_HOUR = 12
private const val HALF_DAY = 12
private const val CENTURY = 100
private const val YEAR_20XX = 2000
private const val YEAR_19XX = 1900
private const val TWO_DIGIT_YEAR_LENGTH = 2

/** Groups 1-3 are the date, 4-6 the time; group 7 is the offset of ISO and the AM/PM mark of the slashed form. */
private const val HOUR_GROUP = 4
private const val MINUTE_GROUP = 5
private const val SECOND_GROUP = 6
private const val LAST_GROUP = 7
private const val OFFSET_HOURS_END = 3

private val DOTTED = Regex("""^(\d{1,2})\.(\d{1,2})\.(\d{4}|\d{2})(?:[ T,]+(\d{1,2}):(\d{2})(?::(\d{2}))?)?$""")
private val ISO =
    Regex("""^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{1,2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?\s*(Z|[+-]\d{2}:?\d{2})?)?$""")
private val SLASHED =
    Regex("""^(\d{1,2})/(\d{1,2})/(\d{4})(?: (\d{1,2}):(\d{2})(?::(\d{2}))?(?:\s*([AaPp])[Mm])?)?$""")
private val TIME_ONLY = Regex("""^\d{1,2}:\d{2}(:\d{2})?$""")

/** The parts of a date and a time; hours, minutes and seconds are null when the value has no time. */
private data class Parts(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int?,
    val minute: Int?,
    val second: Int?,
    val offset: ZoneOffset? = null,
)

/** The moment of a reading. [hasTime] is false for a bare date: the time became 12:00 and the report says so. */
data class CsvMoment(
    val instant: Instant,
    val offset: ZoneOffset,
    val hasTime: Boolean,
)

/**
 * The date formats of docs/13 §13.2. Without an offset in the value the time is local to the current zone, with the
 * offset of the zone at that moment, not of today: otherwise readings from across a clock change move by an hour.
 */
class CsvDateParser(
    private val zone: ZoneId,
    private val currentYear: Int,
    private val dayFirst: Boolean,
) {
    fun parse(value: String): CsvMoment? {
        val text = value.trim()
        return try {
            (dotted(text) ?: iso(text) ?: slashed(text))?.toMoment()
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun Parts.toMoment(): CsvMoment {
        val local = LocalDateTime.of(year, month, day, hour ?: DEFAULT_HOUR, minute ?: 0, second ?: 0)
        val offset = offset ?: zone.rules.getOffset(local)
        return CsvMoment(local.toInstant(offset), offset, hasTime = hour != null)
    }

    private fun dotted(text: String): Parts? =
        DOTTED.matchEntire(text)?.let { match ->
            val (day, month, year) = match.destructured
            parts(match, fullYear(year), month.toInt(), day.toInt())
        }

    private fun iso(text: String): Parts? =
        ISO.matchEntire(text)?.let { match ->
            val (year, month, day) = match.destructured
            val raw = match.groupValues[LAST_GROUP]
            val offset = if (raw.isEmpty()) null else ZoneOffset.of(raw.withColon())
            parts(match, year.toInt(), month.toInt(), day.toInt()).copy(offset = offset)
        }

    private fun slashed(text: String): Parts? =
        SLASHED.matchEntire(text)?.let { match ->
            val (first, second, year) = match.destructured
            val mark = match.groupValues[LAST_GROUP]
            // The 12-hour format is the American one; the scan of the file decides the order for the others.
            val monthFirst = if (mark.isNotEmpty()) first.toInt() <= HALF_DAY else !dayFirst
            val day = if (monthFirst) second.toInt() else first.toInt()
            val month = if (monthFirst) first.toInt() else second.toInt()
            val parts = parts(match, year.toInt(), month, day)
            if (mark.isEmpty()) parts else parts.copy(hour = parts.hour?.to24(mark))
        }

    private fun parts(
        match: MatchResult,
        year: Int,
        month: Int,
        day: Int,
    ) = Parts(
        year,
        month,
        day,
        match.groupValues[HOUR_GROUP].toIntOrNull(),
        match.groupValues[MINUTE_GROUP].toIntOrNull(),
        match.groupValues[SECOND_GROUP].toIntOrNull(),
    )

    /** `yy` up to next year is 20yy, the rest 19yy (docs/13 §13.2). */
    private fun fullYear(text: String): Int {
        val value = text.toInt()
        return when {
            text.length != TWO_DIGIT_YEAR_LENGTH -> value
            value <= (currentYear + 1) % CENTURY -> YEAR_20XX + value
            else -> YEAR_19XX + value
        }
    }

    private fun Int.to24(mark: String): Int =
        when {
            mark.equals("p", ignoreCase = true) -> if (this == HALF_DAY) this else this + HALF_DAY
            else -> if (this == HALF_DAY) 0 else this
        }

    private fun String.withColon(): String =
        if (this == "Z" || ':' in this) this else substring(0, OFFSET_HOURS_END) + ":" + substring(OFFSET_HOURS_END)
}

internal fun isTimeOnly(value: String): Boolean = TIME_ONLY.matches(value.trim())

/** The order of `a/b/yyyy` found by scanning the file: a number above 12 can only be a day. */
internal class DayOrderScan {
    private var dayFirstSeen = false
    private var monthFirstSeen = false
    private var slashedSeen = false

    fun add(value: String) {
        val match = SLASHED.matchEntire(value.trim()) ?: return
        slashedSeen = true
        val (first, second) = match.destructured
        if (first.toInt() > HALF_DAY) dayFirstSeen = true
        if (second.toInt() > HALF_DAY) monthFirstSeen = true
    }

    /** True when nothing in the file tells the order. */
    val ambiguous: Boolean get() = slashedSeen && !dayFirstSeen && !monthFirstSeen

    /** Null when the file does not tell. */
    val dayFirst: Boolean?
        get() =
            when {
                dayFirstSeen -> true
                monthFirstSeen -> false
                else -> null
            }
}
