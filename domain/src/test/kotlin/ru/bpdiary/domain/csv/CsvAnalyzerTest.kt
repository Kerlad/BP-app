package ru.bpdiary.domain.csv

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Tag
import ru.bpdiary.domain.csv.ColumnRole.ARM
import ru.bpdiary.domain.csv.ColumnRole.COMMENT
import ru.bpdiary.domain.csv.ColumnRole.DATE
import ru.bpdiary.domain.csv.ColumnRole.DATE_TIME
import ru.bpdiary.domain.csv.ColumnRole.DIA
import ru.bpdiary.domain.csv.ColumnRole.IGNORE
import ru.bpdiary.domain.csv.ColumnRole.PULSE
import ru.bpdiary.domain.csv.ColumnRole.SYS
import ru.bpdiary.domain.csv.ColumnRole.TAGS
import ru.bpdiary.domain.csv.ColumnRole.TIME
import ru.bpdiary.domain.csv.ColumnRole.TIMESTAMP
import java.time.Instant

class CsvAnalyzerTest {
    private val analyzer = CsvAnalyzer(vocabulary, FixedTimeProvider.default())

    private fun inspect(source: CsvSource): Pair<CsvOptions, CsvInspection> {
        val options = analyzer.detect(source)
        return options to analyzer.inspect(source, options, Arm.LEFT)
    }

    private fun valid(source: CsvSource): List<MappedRow> {
        val rows = ArrayList<MappedRow>()
        analyzer.mapped(source, analyzer.detect(source), Arm.LEFT) { if (it is RowResult.Valid) rows += it.row }
        return rows
    }

    @Test
    fun `the reference file gives 27 readings, no errors and one left arm from a comment`() {
        val source = sourceOf(resourceBytes("ru_comma_quoted_noheader.csv"))
        val (options, inspection) = inspect(source)

        assertEquals(CsvEncoding.UTF_8, options.encoding)
        assertEquals(',', options.delimiter)
        assertFalse(options.hasHeader)
        assertEquals(listOf(DATE_TIME, SYS, DIA, PULSE, COMMENT), options.columns)
        assertEquals(27, inspection.rows)
        assertEquals(27, inspection.valid)
        assertEquals(0, inspection.errorCount)
        assertNull(inspection.problem)
        assertEquals(5, inspection.preview.size)

        val rows = valid(source)
        assertEquals(1, rows.count { it.armFromComment })
        val pair = rows.filter { it.reading.takenAt in between("2026-10-09T03:49:00Z", "2026-10-09T03:50:00Z") }
        assertEquals(listOf(118 to 83, 121 to 86), pair.map { it.reading.sys to it.reading.dia })
    }

    private fun between(
        from: String,
        to: String,
    ) = Instant.parse(from)..Instant.parse(to)

    @Test
    fun `an Excel file with semicolons, Windows-1251 and a header`() {
        val text =
            """
            Дата;Время;Верхнее;Нижнее;Пульс;Рука;Статус;Заметка
            07.10.2026;06:48;118;76;79;Правая;В норме;утро
            08.10.2026;19:10;121;80;;Левая;В норме;
            """.trimIndent()
        val source = sourceOf(text, "windows-1251")
        val (options, inspection) = inspect(source)

        assertEquals(CsvEncoding.WINDOWS_1251, options.encoding)
        assertEquals(';', options.delimiter)
        assertTrue(options.hasHeader)
        assertEquals(listOf(DATE, TIME, SYS, DIA, PULSE, ARM, IGNORE, COMMENT), options.columns)
        assertEquals(2, inspection.valid)
        val rows = valid(source)
        assertEquals(Arm.RIGHT, rows[0].reading.arm)
        assertEquals("утро", rows[0].reading.comment)
        assertNull(rows[1].reading.pulse)
    }

    @Test
    fun `tabs and an English header`() {
        val text = "date\ttime\tsys\tdia\theart rate\tnotes\n2026-10-07\t06:48\t118\t76\t79\tok\n"
        val (options, inspection) = inspect(sourceOf(text))
        assertEquals('\t', options.delimiter)
        assertEquals(listOf(DATE, TIME, SYS, DIA, PULSE, COMMENT), options.columns)
        assertEquals(1, inspection.valid)
    }

    @Test
    fun `the timestamp of our own export wins over the human columns`() {
        val text =
            "\uFEFF# Дневник давления\r\n" +
                "Дата;Время;Верхнее;Нижнее;Пульс;Рука;Статус;Самочувствие;Комментарий;Метка времени\r\n" +
                "07.10.2026;06:48;118;76;79;Левая;В норме;Хорошо;;2026-10-07T06:48:00+05:00\r\n"
        val source = sourceOf(text)
        val options = analyzer.detect(source)
        assertEquals(listOf(DATE, TIME, SYS, DIA, PULSE, ARM, IGNORE, TAGS, COMMENT, TIMESTAMP), options.columns)
        val row = valid(source).single().reading
        assertEquals(Instant.parse("2026-10-07T01:48:00Z"), row.takenAt)
        assertEquals(setOf(Tag.GOOD), row.tags)
        assertEquals(18000, row.zoneOffset.totalSeconds)
    }

    @Test
    fun `dates up to the 12th leave the order to the user`() {
        val text = "03/05/2026 06:48,118,76,79\n04/05/2026 06:48,120,80,70\n"
        val (_, ambiguous) = inspect(sourceOf(text))
        assertTrue(ambiguous.ambiguousDayOrder)
    }

    @Test
    fun `a number above 12 settles the order`() {
        val american = "05/13/2026 06:48,118,76,79\n05/14/2026 06:48,120,80,70\n"
        val (options, inspection) = inspect(sourceOf(american))
        assertFalse(options.dayFirst)
        assertFalse(inspection.ambiguousDayOrder)
        assertEquals(2, inspection.valid)
    }

    @Test
    fun `a line break inside a quoted comment stays in the comment`() {
        val text =
            "\"07.10.2026 06:48\",\"118\",\"76\",\"79\",\"две\r\nстроки\"\r\n" +
                "\"08.10.2026 06:48\",\"120\",\"80\",\"70\",\"\"\r\n"
        val rows = valid(sourceOf(text))
        assertEquals(2, rows.size)
        assertEquals("две\r\nстроки", rows[0].reading.comment)
    }

    @Test
    fun `bad rows are counted with their line and the others are read`() {
        val text =
            """
            дата и время;верхнее;нижнее;пульс
            07.10.2026 06:48;118;76;79
            08.10.2026 06:48;118
            09.10.2026 06:48;76;118;79
            10.10.2026 06:48;118;76;500
            ;118;76;79
            11.10.2026 06:48;120;80;70
            """.trimIndent()
        val (_, inspection) = inspect(sourceOf(text))
        assertEquals(6, inspection.rows)
        assertEquals(2, inspection.valid)
        assertEquals(
            listOf(
                RowError(3, RowProblem.NOT_ENOUGH_COLUMNS),
                RowError(4, RowProblem.SYS_NOT_GREATER),
                RowError(5, RowProblem.BAD_PULSE),
                RowError(6, RowProblem.BAD_DATE),
            ),
            inspection.errors,
        )
    }

    @Test
    fun `a reading far in the future is an error, a little ahead is allowed and reported`() {
        val text =
            """
            дата и время;верхнее;нижнее;пульс
            20.10.2026 20:00;118;76;79
            23.10.2026 20:00;118;76;79
            """.trimIndent()
        val rows = valid(sourceOf(text))
        assertEquals(1, rows.size)
        assertTrue(rows.single().aheadOfClock)
    }

    @Test
    fun `an empty pulse is allowed, the two pressure columns are enough`() {
        val source = sourceOf("07.10.2026 06:48,118,76\n08.10.2026 06:48,120,80\n")
        assertEquals(listOf(DATE_TIME, SYS, DIA), analyzer.detect(source).columns)
        assertNull(valid(source).first().reading.pulse)
    }

    @Test
    fun `the problems of a file that is not a diary`() {
        assertEquals(CsvProblem.EMPTY, inspect(sourceOf("")).second.problem)
        assertEquals(CsvProblem.EMPTY, inspect(sourceOf("\n\n# только шапка\n")).second.problem)
        assertEquals(CsvProblem.NO_PRESSURE_COLUMNS, inspect(sourceOf("a,b\nc,d\ne,f\n")).second.problem)
        assertEquals(CsvProblem.NO_DATES, inspect(sourceOf("x,118,76\ny,120,80\n")).second.problem)
    }

    @Test
    fun `the arm is read from whole words only`() {
        val arm = { comment: String ->
            val text = "07.10.2026 06:48,118,76,79,\"$comment\"\n08.10.2026 06:48,120,80,70,день\n"
            valid(sourceOf(text)).first()
        }
        assertEquals(Arm.LEFT, arm("левая").reading.arm)
        assertTrue(arm("левая").armFromComment)
        assertEquals(Arm.RIGHT, arm("после обеда, правая.").reading.arm)
        assertEquals(Arm.RIGHT, arm("right").reading.arm)
        // Roots do not count, and both arms in one text set none.
        listOf("правильно", "направление", "поправка", "левая и правая").forEach { comment ->
            assertTrue(arm(comment).armDefaulted, comment)
        }
    }

    @Test
    fun `the arm is not read from the comment when the user switched it off`() {
        val source = sourceOf("07.10.2026 06:48,118,76,79,правая\n08.10.2026 06:48,120,80,70,день\n")
        val options = analyzer.detect(source).copy(armFromComment = false)
        val rows = ArrayList<MappedRow>()
        analyzer.mapped(source, options, Arm.LEFT) { rows += (it as RowResult.Valid).row }
        assertEquals(Arm.LEFT, rows.first().reading.arm)
        assertEquals("правая", rows.first().reading.comment)
    }

    @Test
    fun `a comment equal to a tag becomes the tag, unknown tags go to the comment, long comments are cut`() {
        val long = "к".repeat(CsvLimits.MAX_COMMENT_CHARS + 1)
        val text =
            """
            дата и время;верхнее;нижнее;пульс;самочувствие;комментарий
            07.10.2026 06:48;118;76;79;;Головная боль
            08.10.2026 06:48;118;76;79;Хорошо · Усталость;день
            09.10.2026 06:48;118;76;79;AFTER_EXERCISE;$long
            """.trimIndent()
        val rows = valid(sourceOf(text))
        assertEquals(setOf(Tag.HEADACHE), rows[0].reading.tags)
        assertEquals("", rows[0].reading.comment)
        assertEquals(setOf(Tag.GOOD), rows[1].reading.tags)
        assertEquals("Усталость · день", rows[1].reading.comment)
        assertEquals(setOf(Tag.AFTER_EXERCISE), rows[2].reading.tags)
        assertEquals(CsvLimits.MAX_COMMENT_CHARS, rows[2].reading.comment.length)
        assertTrue(rows[2].commentCut)
    }
}
