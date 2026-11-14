package ru.bpdiary.domain.csv

import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Tag
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.domain.classify
import java.time.format.DateTimeFormatter

private const val BOM = "\uFEFF"
private const val CRLF = "\r\n"
private const val DELIMITER = ';'
private const val COMMENT_SIGN = "# "
private const val TAG_SEPARATOR = " · "
private val DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu")
private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val TIMESTAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssxxx")

/**
 * The one export format (docs/13 §13.3): UTF-8 with BOM, `;`, CRLF, the `#` lines, a Russian header and the lossless
 * `Метка времени` as the last column. [out] is expected to encode UTF-8.
 */
fun writeCsv(
    out: Appendable,
    readings: List<Reading>,
    thresholds: Thresholds,
    texts: CsvExportTexts,
) {
    out.append(BOM)
    texts.metaLines.forEach { out.append(COMMENT_SIGN).append(it).append(CRLF) }
    out.append(texts.columns.joinToString(DELIMITER.toString()) { escape(it) }).append(CRLF)
    readings.forEach { reading ->
        val moment = reading.takenAt.atOffset(reading.zoneOffset)
        val fields =
            listOf(
                DATE.format(moment),
                TIME.format(moment),
                reading.sys.toString(),
                reading.dia.toString(),
                reading.pulse?.toString().orEmpty(),
                texts.arms.getValue(reading.arm),
                texts.statuses.getValue(classify(reading.sys, reading.dia, thresholds)),
                Tag.entries.filter { it in reading.tags }.joinToString(TAG_SEPARATOR) { texts.tags.getValue(it) },
                reading.comment.withFormulaGuard(),
                TIMESTAMP.format(moment),
            )
        out.append(fields.joinToString(DELIMITER.toString()) { escape(it) }).append(CRLF)
    }
}

/** Quotes only where needed, so that the file stays readable by eye. */
private fun escape(field: String): String {
    val quote =
        field.any { it == DELIMITER || it == '"' || it == '\r' || it == '\n' } ||
            field.firstOrNull() == ' ' ||
            field.lastOrNull() == ' '
    return if (quote) "\"" + field.replace("\"", "\"\"") + "\"" else field
}
