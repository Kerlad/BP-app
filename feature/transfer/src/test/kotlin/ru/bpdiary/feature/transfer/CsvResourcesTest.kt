package ru.bpdiary.feature.transfer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Tag
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.domain.csv.ColumnRole
import ru.bpdiary.domain.csv.CsvAnalyzer
import ru.bpdiary.domain.csv.CsvSource
import ru.bpdiary.domain.csv.ImportCsvUseCase
import ru.bpdiary.domain.csv.RowError
import ru.bpdiary.domain.csv.RowProblem
import ru.bpdiary.domain.csv.writeCsv
import ru.bpdiary.domain.fake.FakeReadingRepository
import ru.bpdiary.domain.fake.FakeSettingsRepository
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The words live in resources: here they meet the samples of docs/13, which the domain tests cannot do. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru")
class CsvResourcesTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val time = FixedTimeProvider.default()
    private val moscow = ZoneOffset.ofHours(3)
    private val texts = ImportTexts(resources)

    private fun sample(name: String) = File("../../docs/samples/$name").readBytes()

    private fun reading(
        at: String,
        sys: Int,
        dia: Int,
        pulse: Int,
    ) = Reading(
        takenAt = Instant.parse(at),
        zoneOffset = moscow,
        sys = sys,
        dia = dia,
        pulse = pulse,
        arm = Arm.LEFT,
    )

    @Test
    fun `the export with the words from resources matches the sample byte for byte`() {
        val list =
            listOf(
                reading("2026-10-07T03:48:00Z", 118, 76, 79).copy(comment = "вчера 1-й день препарата"),
                reading("2026-10-09T03:49:00Z", 118, 83, 70),
                reading("2026-10-09T03:50:00Z", 121, 86, 73),
                reading("2026-10-10T06:51:00Z", 130, 88, 85).copy(
                    arm = Arm.RIGHT,
                    tags = setOf(Tag.AFTER_EXERCISE),
                    comment = "суббота, до приёма",
                ),
                reading("2026-10-11T08:10:00Z", 146, 92, 71).copy(
                    tags = setOf(Tag.AFTER_MEDICINE, Tag.HEADACHE),
                    comment = "сказал \"подожди 20 минут\"",
                ),
                reading("2026-10-14T03:44:00Z", 109, 67, 72).copy(tags = setOf(Tag.GOOD)),
            )
        val today = LocalDate.of(2026, 10, 20)
        val words = csvExportTexts(resources, today, LocalDate.of(2026, 10, 7)..today, list.size, Thresholds())
        val out = StringBuilder()
        writeCsv(out, list, Thresholds(), words)

        assertEquals(String(sample("export-sample.csv"), Charsets.UTF_8), out.toString())
        assertArrayEquals(sample("export-sample.csv"), out.toString().toByteArray(Charsets.UTF_8))
    }

    @Test
    fun `the reference file gives 27 readings, no errors and one left arm`() =
        runTest {
            val bytes = sample("import-sample-ru-comma.csv")
            val source = CsvSource { bytes.inputStream() }
            val analyzer = CsvAnalyzer(csvVocabulary(resources), time)
            val found = analyzer.detect(source)
            assertEquals(ColumnRole.DATE_TIME, found.columns.first())

            val readings = FakeReadingRepository(time)
            val settings = FakeSettingsRepository()
            val result = ImportCsvUseCase(analyzer, readings, readings, settings)(source, found, false)
            assertEquals(27, result.imported)
            assertEquals(0, result.errorCount)
            assertEquals(1, result.armFromComment)
        }

    @Test
    fun `our own export comes back through the words from resources`() {
        val bytes = sample("export-sample.csv")
        val source = CsvSource { bytes.inputStream() }
        val analyzer = CsvAnalyzer(csvVocabulary(resources), time)
        val found = analyzer.detect(source)
        assertEquals(true, found.hasHeader)
        assertEquals(ColumnRole.TIMESTAMP, found.columns.last())
        assertEquals(ColumnRole.IGNORE, found.columns[6])
        val inspection = analyzer.inspect(source, found, Arm.LEFT)
        assertEquals(6, inspection.valid)
        assertNull(inspection.problem)
    }

    @Test
    fun `the report reads as in docs 13`() {
        assertEquals(
            listOf(
                "Импортировано 27 измерений",
                "Пропущено дублей: 0",
                "Строк с ошибками: 0",
                "Рука определена по комментарию: 1",
                "Рука не указана, проставлена «Левая»: 26",
                "Период: 07.10.2026 — 20.10.2026",
            ),
            texts.report(report),
        )
    }

    @Test
    fun `the lines that have nothing to say stay out, the plural follows the number`() {
        val lines =
            texts.report(report.copy(imported = 1, commentsCut = 2, aheadOfClock = 3, withoutTime = 4, from = null))
        assertEquals("Импортировано 1 измерение", lines.first())
        assertEquals("Комментарий обрезан до 120 символов: 2", lines[5])
        assertEquals("Время позже текущего: 3", lines[6])
        assertEquals("Без времени, проставлено 12:00: 4", lines[7])
        assertEquals(8, lines.size)
    }

    @Test
    fun `an unclosed quote names the line where it opened`() {
        assertEquals(
            "Ошибка в строке 2: слишком длинный текст или не закрыта кавычка.",
            texts.fileProblem(FileProblem.UNCLOSED_QUOTE, 2),
        )
    }

    @Test
    fun `a partly imported file tells how many rows were left`() {
        assertNull(texts.partial(report))
        assertEquals(
            "Импортировано 27 из 37. 10 строк пропущено — показать?",
            texts.partial(report.copy(errorCount = 10)),
        )
    }

    @Test
    fun `a bad row names its line and reason`() {
        assertEquals(
            "Строка 4: не удалось распознать дату",
            texts.rowError(RowError(4, RowProblem.BAD_DATE)),
        )
    }

    @Test
    fun `the options and the report survive the trip through the worker`() {
        val changed = options.copy(dayFirst = false, armFromComment = false)
        assertEquals(changed, changed.toData(27, true).toOptions())
        assertEquals(report, report.toData().toReport())
        assertEquals(report.copy(from = null, to = null), report.copy(from = null, to = null).toData().toReport())
        assertNull(report.toData().toFraction())
    }

    @Test
    fun `a bad row survives its line in the file, a broken line is dropped`() {
        val error = RowError(12, RowProblem.BAD_PULSE)
        assertEquals(error, rowErrorOf(error.toLine()))
        assertNull(rowErrorOf("не строка"))
    }

    @Test
    fun `the file name is the one of docs 13`() {
        assertEquals(
            "davlenie_2026-09-20_2026-10-20.csv",
            exportFileName(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 10, 20)),
        )
    }
}
