package ru.bpdiary.domain.csv

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.domain.fake.FakeReadingRepository
import ru.bpdiary.domain.fake.FakeSettingsRepository
import java.time.LocalDate

class ImportCsvUseCaseTest {
    private val time = FixedTimeProvider.default()
    private val readings = FakeReadingRepository(time)
    private val analyzer = CsvAnalyzer(vocabulary, time)
    private val import = ImportCsvUseCase(analyzer, readings, readings, FakeSettingsRepository())

    private suspend fun importText(text: String): ImportReport {
        val source = sourceOf(text.toByteArray(Charsets.UTF_8))
        return import(source, analyzer.detect(source), replaceAll = false)
    }

    @Test
    fun `a row from before 1970 is found as a duplicate (D-03)`() =
        runTest {
            val text = "дата и время;верхнее;нижнее;пульс\n01.07.1969 10:00;120;80;70\n"
            assertEquals(1, importText(text).imported)
            val second = importText(text)
            assertEquals(0, second.imported)
            assertEquals(1, second.duplicates)
        }

    @Test
    fun `the period is in the days of the readings own offset (D-04)`() =
        runTest {
            // 20:30 UTC on 19 Oct: 20 Oct 01:30 at +05:00 (the diary shows 20 Oct), but 23:30 on 19 Oct in Moscow.
            val text = "дата и время;верхнее;нижнее;пульс\n2026-10-20T01:30:00+05:00;120;80;70\n"
            val report = importText(text)
            assertEquals(1, report.imported)
            assertEquals(LocalDate.of(2026, 10, 20), report.from)
            assertEquals(LocalDate.of(2026, 10, 20), report.to)
        }
}
