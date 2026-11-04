package ru.bpdiary.domain.csv

import ru.bpdiary.core.common.TimeProvider
import ru.bpdiary.domain.ValidationRules

private const val ENOUGH = 0.8
private val SPACES = Regex("""\s+""")
private val PRESSURE_RANGES =
    listOf(
        ColumnRole.SYS to ValidationRules.SYS_RANGE,
        ColumnRole.DIA to ValidationRules.DIA_RANGE,
        ColumnRole.PULSE to ValidationRules.PULSE_RANGE,
    )

/** What each column of a file holds: by its name, or by its values when the file has no header (docs/13 §13.2). */
internal class CsvColumnDetector(
    private val vocabulary: CsvVocabulary,
    private val time: TimeProvider,
) {
    private val roleWords: List<Pair<Set<String>, ColumnRole>> =
        with(vocabulary) {
            listOf(
                timestamp to ColumnRole.TIMESTAMP,
                dateTime to ColumnRole.DATE_TIME,
                date to ColumnRole.DATE,
                time to ColumnRole.TIME,
                sys to ColumnRole.SYS,
                dia to ColumnRole.DIA,
                pulse to ColumnRole.PULSE,
                arm to ColumnRole.ARM,
                tags to ColumnRole.TAGS,
                comment to ColumnRole.COMMENT,
                ignored to ColumnRole.IGNORE,
            )
        }

    /** Text where the other rows have whole numbers, or the known column names. */
    fun isHeader(rows: List<CsvRow>): Boolean {
        val first = rows.first().fields
        val others = rows.drop(1).map { it.fields }
        val numeric =
            first.indices.filter { column ->
                val values = others.mapNotNull { it.getOrNull(column)?.trim() }.filter { it.isNotEmpty() }
                values.isNotEmpty() && values.count { it.toIntOrNull() != null } >= values.size * ENOUGH
            }
        val textOverNumbers = numeric.isNotEmpty() && numeric.all { first[it].trim().toIntOrNull() == null }
        return textOverNumbers || first.count { roleOf(it) != null } > 1
    }

    /** By the names; a second column of the same meaning is not imported, comments are joined. */
    fun fromHeader(header: CsvRow): List<ColumnRole> {
        val seen = HashSet<ColumnRole>()
        return header.fields.map { name ->
            val role = roleOf(name) ?: ColumnRole.IGNORE
            if (role == ColumnRole.COMMENT || seen.add(role)) role else ColumnRole.IGNORE
        }
    }

    private fun roleOf(name: String): ColumnRole? {
        val clean = name.trim().lowercase().replace(SPACES, " ")
        return roleWords.firstOrNull { (words, _) -> clean in words }?.second
    }

    /** The positional heuristic on the values of the first rows. */
    fun fromValues(rows: List<List<String>>): List<ColumnRole> {
        val width = rows.maxOfOrNull { it.size } ?: 0
        val roles = MutableList(width) { ColumnRole.IGNORE }
        val values = (0 until width).map { column -> rows.map { it.getOrNull(column).orEmpty().trim() } }
        assignDates(values, roles)
        assignPressure(values, roles)
        val armWords = vocabulary.leftWords + vocabulary.rightWords
        roles.indices.filter { roles[it] == ColumnRole.IGNORE }.forEach { column ->
            val filled = values[column].filter { it.isNotEmpty() }
            roles[column] =
                when {
                    filled.isEmpty() -> ColumnRole.IGNORE
                    ColumnRole.ARM !in roles && filled.mostly { it.lowercase() in armWords } -> ColumnRole.ARM
                    else -> ColumnRole.COMMENT
                }
        }
        return roles
    }

    private fun assignDates(
        values: List<List<String>>,
        roles: MutableList<ColumnRole>,
    ) {
        val year = time.now().atZone(time.zone()).year
        val parsers = listOf(true, false).map { CsvDateParser(time.zone(), year, dayFirst = it) }
        val dates =
            values.indices.filter { column ->
                parsers.any { parser -> values[column].mostly { parser.parse(it) != null } }
            }
        val second = values.indices.firstOrNull { values[it].mostly(::isTimeOnly) } ?: dates.getOrNull(1)
        dates.firstOrNull()?.let { first ->
            roles[first] = if (second != null) ColumnRole.DATE else ColumnRole.DATE_TIME
            if (second != null) roles[second] = ColumnRole.TIME
        }
    }

    /** The first numbers from the left that fit the ranges, when the first is mostly above the second. */
    private fun assignPressure(
        values: List<List<String>>,
        roles: MutableList<ColumnRole>,
    ) {
        val found = LinkedHashMap<ColumnRole, Int>()
        roles.indices.filter { roles[it] == ColumnRole.IGNORE }.forEach { column ->
            PRESSURE_RANGES.getOrNull(found.size)?.let { (role, range) ->
                val filled = values[column].filter { it.isNotEmpty() }
                if (filled.mostly { (it.toIntOrNull() ?: Int.MIN_VALUE) in range }) found[role] = column
            }
        }
        val sys = found[ColumnRole.SYS]?.let { values[it] }.orEmpty()
        val dia = found[ColumnRole.DIA]?.let { values[it] }.orEmpty()
        val pairs = sys.zip(dia)
        val ordered = pairs.count { (s, d) -> (s.toIntOrNull() ?: 0) > (d.toIntOrNull() ?: 0) }
        if (pairs.isNotEmpty() && ordered >= pairs.size * ENOUGH) {
            found.forEach { (role, column) -> roles[column] = role }
        }
    }

    private fun List<String>.mostly(test: (String) -> Boolean): Boolean = isNotEmpty() && count(test) >= size * ENOUGH
}
