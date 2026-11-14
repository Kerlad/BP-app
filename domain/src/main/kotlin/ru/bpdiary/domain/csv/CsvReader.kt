package ru.bpdiary.domain.csv

import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

private const val BUFFER_CHARS = 16 * 1024
private const val BYTE_MASK = 0xFF
private const val BOM_FF = 0xFF
private const val BOM_FE = 0xFE
private const val BOM_EF = 0xEF
private const val BOM_BB = 0xBB
private const val BOM_BF = 0xBF
private const val BOM_CODE = 0xFEFF
private const val COMMENT_SIGN = '#'
private const val BOM_BYTES = 3

/** BOM first, then strict UTF-8 over the whole file, then Windows-1251 (docs/13 §13.2). */
fun detectEncoding(source: CsvSource): CsvEncoding {
    val bom = source.open().use { it.readBomBytes() }
    return when {
        bom.size >= BOM_BYTES && bom[0] == BOM_EF && bom[1] == BOM_BB && bom[2] == BOM_BF -> CsvEncoding.UTF_8
        bom.size >= 2 && bom[0] == BOM_FF && bom[1] == BOM_FE -> CsvEncoding.UTF_16LE
        bom.size >= 2 && bom[0] == BOM_FE && bom[1] == BOM_FF -> CsvEncoding.UTF_16BE
        source.decodesAsUtf8() -> CsvEncoding.UTF_8
        else -> CsvEncoding.WINDOWS_1251
    }
}

private fun InputStream.readBomBytes(): List<Int> =
    buildList {
        repeat(BOM_BYTES) {
            val b = read()
            if (b != -1) add(b and BYTE_MASK)
        }
    }

private fun CsvSource.decodesAsUtf8(): Boolean {
    val decoder =
        Charsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
    return try {
        open().use { stream ->
            val reader = InputStreamReader(BufferedInputStream(stream), decoder)
            val buffer = CharArray(BUFFER_CHARS)
            while (reader.read(buffer) != -1) {
                // Only the decoding matters.
            }
        }
        true
    } catch (_: CharacterCodingException) {
        false
    }
}

/** The text of the file without its BOM. The caller closes it. */
fun CsvSource.reader(encoding: CsvEncoding): Reader {
    val reader = BufferedReader(InputStreamReader(open(), Charset.forName(encoding.charsetName)), BUFFER_CHARS)
    reader.mark(1)
    if (reader.read() != BOM_CODE) reader.reset()
    return reader
}

/** A row of the file; [line] is the number of its first line in the file, for the report. */
data class CsvRow(
    val line: Int,
    val fields: List<String>,
)

/**
 * RFC 4180 state machine: streaming, with `""`, delimiters and line breaks inside quotes, LF, CRLF and a lone CR.
 * Empty rows and rows starting with `#` (the header of our own export) are skipped. A quote in the middle of an
 * unquoted field is an ordinary character.
 *
 * @throws CsvLimitException on a field or a number of rows above [CsvLimits]
 */
class CsvReader(
    private val reader: Reader,
    private val delimiter: Char,
) {
    private val field = StringBuilder()
    private var row = ArrayList<String>()
    private var line = 1
    private var rowLine = 1
    private var fieldLine = 1
    private var rows = 0
    private var mode = Mode.PLAIN
    private var afterCr = false

    fun rows(): Sequence<CsvRow> =
        sequence {
            var code = reader.read()
            while (code != -1) {
                feed(code.toChar())?.let { yield(it) }
                code = reader.read()
            }
            if (field.isNotEmpty() || row.isNotEmpty()) finishRow()?.let { yield(it) }
        }

    /** The row that this character completes, if any. */
    private fun feed(ch: Char): CsvRow? {
        val crLf = afterCr && ch == '\n'
        afterCr = ch == '\r'
        if (ch == '\r' || (ch == '\n' && !crLf)) line++
        if (!crLf || mode == Mode.QUOTED) mode = next(mode, ch)
        if (mode != Mode.ROW_DONE) return null
        val done = finishRow()
        rowLine = line
        mode = Mode.PLAIN
        return done
    }

    private fun next(
        mode: Mode,
        ch: Char,
    ): Mode =
        when (mode) {
            Mode.QUOTED -> quoted(ch)
            Mode.AFTER_QUOTE -> if (ch == '"') appendTo(Mode.QUOTED, ch) else plain(ch)
            Mode.COMMENT -> if (ch == '\n' || ch == '\r') comment() else Mode.COMMENT
            Mode.PLAIN, Mode.ROW_DONE -> plain(ch)
        }

    private fun quoted(ch: Char): Mode = if (ch == '"') Mode.AFTER_QUOTE else appendTo(Mode.QUOTED, ch)

    private fun plain(ch: Char): Mode =
        when {
            ch == COMMENT_SIGN && field.isEmpty() && row.isEmpty() -> {
                Mode.COMMENT
            }

            ch == '"' && field.isEmpty() -> {
                fieldLine = line
                Mode.QUOTED
            }

            ch == delimiter -> {
                endField()
                Mode.PLAIN
            }

            ch == '\n' || ch == '\r' -> {
                Mode.ROW_DONE
            }

            else -> {
                appendTo(Mode.PLAIN, ch)
            }
        }

    private fun comment(): Mode {
        rowLine = line
        return Mode.PLAIN
    }

    private fun appendTo(
        mode: Mode,
        ch: Char,
    ): Mode {
        if (field.isEmpty() && mode == Mode.PLAIN) fieldLine = line
        if (field.length >= CsvLimits.MAX_FIELD_CHARS) throw CsvLimitException(CsvLimit.FIELD, fieldLine)
        field.append(ch)
        return mode
    }

    private fun endField() {
        row.add(field.toString())
        field.setLength(0)
    }

    private fun finishRow(): CsvRow? {
        endField()
        val fields = row
        row = ArrayList(fields.size)
        if (fields.all { it.isBlank() }) return null
        // One more than the limit: the first row may be the header.
        if (++rows > CsvLimits.MAX_ROWS + 1) throw CsvLimitException(CsvLimit.ROWS)
        return CsvRow(rowLine, fields)
    }

    private enum class Mode { PLAIN, QUOTED, AFTER_QUOTE, COMMENT, ROW_DONE }
}
