package ru.bpdiary.domain.csv

import ru.bpdiary.core.common.TimeProvider
import ru.bpdiary.core.model.Arm
import java.io.StringReader

private const val SAMPLE_CHARS = 64 * 1024
private const val SAMPLE_ROWS = 20
private const val PREVIEW_ROWS = 5
private val DELIMITERS = listOf(';', ',', '\t', '|')

/** Encoding, dialect and columns of a file (docs/13 §13.2). Nothing is final: the preview lets the user fix it. */
class CsvAnalyzer(
    private val vocabulary: CsvVocabulary,
    private val time: TimeProvider,
) {
    private val columns = CsvColumnDetector(vocabulary, time)

    /** Everything found by itself; [encoding] and [delimiter] are given when the user has changed them. */
    fun detect(
        source: CsvSource,
        encoding: CsvEncoding = detectEncoding(source),
        delimiter: Char? = null,
    ): CsvOptions {
        val text = sample(source, encoding)
        val chosen = delimiter ?: DELIMITERS.maxBy { score(text, it) }
        val rows = text.rows(chosen)
        val hasHeader = rows.size > 1 && columns.isHeader(rows)
        val found =
            if (hasHeader) {
                columns.fromHeader(rows.first())
            } else {
                columns.fromValues(rows.map { it.fields })
            }
        val options = CsvOptions(encoding, chosen, hasHeader, found)
        return options.copy(dayFirst = dayOrder(source, options).dayFirst ?: true)
    }

    /** The whole file with these options, for the summary of the preview. */
    fun inspect(
        source: CsvSource,
        options: CsvOptions,
        defaultArm: Arm,
    ): CsvInspection {
        val mapper = mapper(options, defaultArm)
        val errors = ArrayList<RowError>()
        var header: List<String>? = null
        val preview = ArrayList<List<String>>()
        var rows = 0
        var valid = 0
        var errorCount = 0
        val scan = DayOrderScan()
        val dateColumns = options.dateColumns()
        readRows(source, options) { row, isHeader ->
            if (isHeader) {
                header = row.fields
            } else {
                rows++
                if (preview.size < PREVIEW_ROWS) preview += row.fields
                dateColumns.forEach { scan.add(row.fields.getOrNull(it).orEmpty()) }
                val result = mapper.map(row)
                if (result is RowResult.Invalid) {
                    errorCount++
                    if (errors.size < CsvLimits.MAX_ERRORS_SHOWN) errors += result.error
                } else {
                    valid++
                }
            }
        }
        return CsvInspection(
            header = header,
            preview = preview,
            rows = rows,
            valid = valid,
            errors = errors,
            errorCount = errorCount,
            ambiguousDayOrder = scan.ambiguous,
            problem = problem(options, rows, valid, errors),
        )
    }

    /** The rows of the file as readings or errors, in the order of the file. */
    fun mapped(
        source: CsvSource,
        options: CsvOptions,
        defaultArm: Arm,
        onRow: (RowResult) -> Unit,
    ) {
        val mapper = mapper(options, defaultArm)
        readRows(source, options) { row, isHeader -> if (!isHeader) onRow(mapper.map(row)) }
    }

    private fun mapper(
        options: CsvOptions,
        defaultArm: Arm,
    ) = CsvRowMapper(options, vocabulary, time.now(), time.zone(), defaultArm)

    private fun readRows(
        source: CsvSource,
        options: CsvOptions,
        onRow: (CsvRow, isHeader: Boolean) -> Unit,
    ) {
        source.reader(options.encoding).use { reader ->
            CsvReader(reader, options.delimiter).rows().forEachIndexed { index, row ->
                onRow(row, index == 0 && options.hasHeader)
            }
        }
    }

    private fun problem(
        options: CsvOptions,
        rows: Int,
        valid: Int,
        errors: List<RowError>,
    ): CsvProblem? =
        when {
            rows == 0 -> {
                CsvProblem.EMPTY
            }

            ColumnRole.SYS !in options.columns || ColumnRole.DIA !in options.columns -> {
                CsvProblem.NO_PRESSURE_COLUMNS
            }

            options.dateColumns().isEmpty() || (valid == 0 && errors.all { it.problem == RowProblem.BAD_DATE }) -> {
                CsvProblem.NO_DATES
            }

            else -> {
                null
            }
        }

    private fun CsvOptions.dateColumns(): List<Int> =
        columns.indices.filter {
            columns[it] == ColumnRole.DATE || columns[it] == ColumnRole.DATE_TIME || columns[it] == ColumnRole.TIMESTAMP
        }

    private fun dayOrder(
        source: CsvSource,
        options: CsvOptions,
    ): DayOrderScan {
        val scan = DayOrderScan()
        val dateColumns = options.dateColumns()
        readRows(source, options) { row, isHeader ->
            if (!isHeader) dateColumns.forEach { scan.add(row.fields.getOrNull(it).orEmpty()) }
        }
        return scan
    }

    private fun sample(
        source: CsvSource,
        encoding: CsvEncoding,
    ): Sample =
        source.reader(encoding).use { reader ->
            val buffer = CharArray(SAMPLE_CHARS)
            var size = 0
            var read = reader.read(buffer, size, SAMPLE_CHARS - size)
            while (read != -1 && size + read < SAMPLE_CHARS) {
                size += read
                read = reader.read(buffer, size, SAMPLE_CHARS - size)
            }
            if (read != -1) size += read
            Sample(String(buffer, 0, size), size == SAMPLE_CHARS)
        }

    private data class Sample(
        val text: String,
        val cut: Boolean,
    ) {
        /** The first rows; the last one of a sample cut at its size may be incomplete. */
        fun rows(delimiter: Char): List<CsvRow> =
            try {
                val rows = CsvReader(StringReader(text), delimiter).rows().take(SAMPLE_ROWS + 1).toList()
                if (cut && rows.size <= SAMPLE_ROWS) rows.dropLast(1) else rows.take(SAMPLE_ROWS)
            } catch (_: CsvLimitException) {
                emptyList()
            }
    }

    /** How many rows have the commonest number of fields, when that number is above one. */
    private fun score(
        sample: Sample,
        delimiter: Char,
    ): Int =
        sample
            .rows(delimiter)
            .groupingBy { it.fields.size }
            .eachCount()
            .filterKeys { it > 1 }
            .values
            .maxOrNull() ?: 0
}
