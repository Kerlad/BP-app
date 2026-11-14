package ru.bpdiary.domain.csv

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.StringReader

class CsvReaderTest {
    private fun rows(
        text: String,
        delimiter: Char = ',',
    ) = CsvReader(StringReader(text), delimiter).rows().toList()

    private fun fields(
        text: String,
        delimiter: Char = ',',
    ) = rows(text, delimiter).map { it.fields }

    @Test
    fun `a doubled quote inside quotes is one quote`() {
        assertEquals(listOf(listOf("a\"b", "c")), fields("\"a\"\"b\",c"))
    }

    @Test
    fun `a delimiter inside quotes does not split the field`() {
        assertEquals(listOf(listOf("суббота, до приёма", "x")), fields("\"суббота, до приёма\",x\n"))
    }

    @Test
    fun `line breaks inside quotes stay in the field`() {
        assertEquals(listOf(listOf("a\r\nb", "c"), listOf("d", "e")), fields("\"a\r\nb\",c\r\nd,e\r\n"))
    }

    @Test
    fun `LF, CRLF and a lone CR all end a row`() {
        assertEquals(listOf(listOf("a"), listOf("b"), listOf("c"), listOf("d")), fields("a\nb\r\nc\rd"))
    }

    @Test
    fun `an empty last field is kept and a file may end without a line break`() {
        assertEquals(listOf(listOf("a", "b", "")), fields("a,b,"))
    }

    @Test
    fun `empty rows and rows of empty fields are skipped silently`() {
        assertEquals(listOf(listOf("a"), listOf("b")), fields("a\n\n,,,\n   \nb\n\n"))
    }

    @Test
    fun `a quote in the middle of an unquoted field is an ordinary character`() {
        assertEquals(listOf(listOf("ab\"c", "d")), fields("ab\"c,d"))
    }

    @Test
    fun `rows starting with a hash are metadata of our own export`() {
        assertEquals(listOf(listOf("a", "#b")), fields("# Дневник давления; экспорт\r\n# Период\r\na,#b\r\n"))
    }

    @Test
    fun `rows know their line in the file`() {
        val rows = rows("# meta\na,b\n\n\"c\nd\",e\nf,g\n")
        assertEquals(listOf(2, 4, 6), rows.map { it.line })
    }

    @Test
    fun `a field above the limit is refused instead of filling the memory`() {
        val error =
            assertThrows<CsvLimitException> { rows("a," + "x".repeat(CsvLimits.MAX_FIELD_CHARS + 1)) }
        assertEquals(CsvLimit.FIELD, error.limit)
    }

    @Test
    fun `a quote that is never closed reports the line where it opened`() {
        val text = "a,b\n1,\"" + "x\n".repeat(CsvLimits.MAX_FIELD_CHARS)
        val error = assertThrows<CsvLimitException> { rows(text) }
        assertEquals(CsvLimit.FIELD, error.limit)
        assertEquals(2, error.line)
    }

    @Test
    fun `more rows than the limit are refused`() {
        val text = "1\n".repeat(CsvLimits.MAX_ROWS + 2)
        val error = assertThrows<CsvLimitException> { rows(text) }
        assertEquals(CsvLimit.ROWS, error.limit)
    }

    @Test
    fun `encoding from the BOM, then strict UTF-8, then Windows-1251`() {
        val text = "Дата;Верхнее"
        assertEquals(CsvEncoding.UTF_8, detectEncoding(sourceOf("\uFEFF$text")))
        assertEquals(CsvEncoding.UTF_8, detectEncoding(sourceOf(text)))
        assertEquals(CsvEncoding.WINDOWS_1251, detectEncoding(sourceOf(text, "windows-1251")))
        assertEquals(CsvEncoding.UTF_16LE, detectEncoding(sourceOf("\uFEFF$text", "UTF-16LE")))
        assertEquals(CsvEncoding.UTF_16BE, detectEncoding(sourceOf("\uFEFF$text", "UTF-16BE")))
    }

    @Test
    fun `the reader drops the BOM`() {
        sourceOf("\uFEFFa,b").reader(CsvEncoding.UTF_8).use { reader ->
            assertEquals(listOf(listOf("a", "b")), CsvReader(reader, ',').rows().map { it.fields }.toList())
        }
    }
}
