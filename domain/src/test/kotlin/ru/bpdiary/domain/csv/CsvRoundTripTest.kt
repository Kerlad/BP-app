package ru.bpdiary.domain.csv

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Tag
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.domain.fake.FakeReadingRepository
import ru.bpdiary.domain.fake.FakeSettingsRepository
import java.time.Instant
import java.time.ZoneOffset

class CsvRoundTripTest {
    private val time = FixedTimeProvider.default()
    private val readings = FakeReadingRepository(time)
    private val settings = FakeSettingsRepository()
    private val analyzer = CsvAnalyzer(vocabulary, time)
    private val import = ImportCsvUseCase(analyzer, readings, readings, settings, time)
    private val moscow = ZoneOffset.ofHours(3)

    private fun stored() = readings.observeRange(Instant.EPOCH, Instant.ofEpochMilli(Long.MAX_VALUE))

    private fun export(
        list: List<Reading>,
        metaLines: List<String> = emptyList(),
    ): ByteArray {
        val out = StringBuilder()
        writeCsv(out, list, Thresholds(), exportTexts(metaLines))
        return out.toString().toByteArray(Charsets.UTF_8)
    }

    private suspend fun importBytes(
        bytes: ByteArray,
        replaceAll: Boolean = false,
    ): ImportReport {
        val source = sourceOf(bytes)
        return import(source, analyzer.detect(source), replaceAll)
    }

    private fun reading(
        minute: Long,
        comment: String = "",
        sys: Int = 120,
        dia: Int = 80,
        pulse: Int? = 70,
    ) = Reading(
        takenAt = Instant.parse("2026-09-01T06:00:00Z").plusSeconds(minute * 60),
        zoneOffset = moscow,
        sys = sys,
        dia = dia,
        pulse = pulse,
        arm = Arm.LEFT,
        comment = comment,
    )

    @Test
    fun `the export matches the sample byte for byte`() {
        val list =
            listOf(
                reading(
                    0,
                ).copy(
                    takenAt = Instant.parse("2026-10-07T03:48:00Z"),
                    sys = 118,
                    dia = 76,
                    pulse = 79,
                    comment = "вчера 1-й день препарата",
                ),
                reading(0).copy(takenAt = Instant.parse("2026-10-09T03:49:00Z"), sys = 118, dia = 83, pulse = 70),
                reading(0).copy(takenAt = Instant.parse("2026-10-09T03:50:00Z"), sys = 121, dia = 86, pulse = 73),
                reading(0).copy(
                    takenAt = Instant.parse("2026-10-10T06:51:00Z"),
                    sys = 130,
                    dia = 88,
                    pulse = 85,
                    arm = Arm.RIGHT,
                    tags = setOf(Tag.AFTER_EXERCISE),
                    comment = "суббота, до приёма",
                ),
                reading(0).copy(
                    takenAt = Instant.parse("2026-10-11T08:10:00Z"),
                    sys = 146,
                    dia = 92,
                    pulse = 71,
                    tags = setOf(Tag.AFTER_MEDICINE, Tag.HEADACHE),
                    comment = "сказал \"подожди 20 минут\"",
                ),
                reading(0).copy(
                    takenAt = Instant.parse("2026-10-14T03:44:00Z"),
                    sys = 109,
                    dia = 67,
                    pulse = 72,
                    tags = setOf(Tag.GOOD),
                ),
            )
        val meta =
            listOf(
                "Дневник давления — экспорт 20.10.2026",
                "Период: 07.10.2026 — 20.10.2026, измерений: 6",
                "Уровни: повышенное от 135/85, пониженное до 100/60, пульс в норме 50–90",
                "Статус рассчитан по этим уровням на дату экспорта",
            )
        assertEquals(
            String(resourceBytes("export-sample.csv"), Charsets.UTF_8),
            String(export(list, meta), Charsets.UTF_8),
        )
        assertArrayEquals(resourceBytes("export-sample.csv"), export(list, meta))
    }

    @Test
    fun `quotes appear only where needed`() {
        val text =
            String(
                export(
                    listOf(
                        reading(0, comment = " пробел"),
                        reading(1, comment = "a;b"),
                        reading(2, comment = "просто"),
                    ),
                ),
                Charsets.UTF_8,
            )
        assertTrue(";\" пробел\";" in text, text)
        assertTrue(";\"a;b\";" in text, text)
        assertTrue(";просто;" in text, text)
        assertTrue(text.startsWith(""))
    }

    @Test
    fun `export then import restores everything, also special characters`() =
        runTest {
            val comments =
                listOf(
                    "",
                    "a;b",
                    "кавычка \" внутри",
                    "две\r\nстроки",
                    "эмодзи 😀",
                    " края ",
                    "#решётка",
                    "запятая, и всё",
                    "Хорошо!",
                )
            val original =
                (0 until 1000).map { i ->
                    reading(
                        minute = i * 37L,
                        comment = comments[i % comments.size],
                        sys = 100 + i % 80,
                        dia = 60 + i % 30,
                        pulse = if (i % 7 == 0) null else 50 + i % 60,
                    ).copy(
                        arm = if (i % 2 == 0) Arm.LEFT else Arm.RIGHT,
                        tags = Tag.entries.filterIndexed { t, _ -> (i shr t) and 1 == 1 }.toSet(),
                    )
                }

            val report = importBytes(export(original))

            assertEquals(1000, report.imported)
            assertEquals(0, report.errorCount)
            assertEquals(0, report.armDefaulted)
            assertEquals(original, stored().first().map { it.copy(id = 0) })
        }

    @Test
    fun `importing the same file twice adds nothing`() =
        runTest {
            val bytes = resourceBytes("ru_comma_quoted_noheader.csv")
            val first = importBytes(bytes)
            assertEquals(27, first.imported)
            assertEquals(1, first.armFromComment)
            assertEquals(26, first.armDefaulted)
            assertEquals(Arm.LEFT, first.defaultArm)
            assertEquals(java.time.LocalDate.of(2026, 10, 7), first.from)

            val second = importBytes(bytes)
            assertEquals(0, second.imported)
            assertEquals(27, second.duplicates)
            assertEquals(27, stored().first().size)
        }

    @Test
    fun `the same minute with other values is another reading, a changed comment is not`() =
        runTest {
            readings.save(reading(0, comment = "старый"))
            val report =
                importBytes(
                    export(
                        listOf(
                            reading(0, comment = "новый"),
                            reading(0, sys = 125),
                            reading(1),
                        ),
                    ),
                )
            assertEquals(2, report.imported)
            assertEquals(1, report.duplicates)
        }

    @Test
    fun `a soft-deleted reading is not a duplicate`() =
        runTest {
            val id = readings.save(reading(0))
            readings.softDelete(id)
            assertEquals(1, importBytes(export(listOf(reading(0)))).imported)
        }

    @Test
    fun `replace all removes what was there, soft-deleted rows too`() =
        runTest {
            readings.save(reading(500))
            readings.softDelete(readings.save(reading(600)))

            val report = importBytes(export(listOf(reading(0), reading(0), reading(1))), replaceAll = true)

            assertEquals(2, report.imported)
            assertEquals(1, report.duplicates)
            assertEquals(2, readings.storedCount)
        }

    @Test
    fun `replace all with no valid rows keeps the diary`() =
        runTest {
            readings.save(reading(500))
            readings.save(reading(600))

            val report = importBytes("дата и время;верхнее;нижнее\nx;76;118\n".toByteArray(), replaceAll = true)

            assertEquals(0, report.imported)
            assertEquals(2, readings.storedCount)
        }

    @Test
    fun `the report counts what the rows said`() =
        runTest {
            val text =
                """
                дата и время;верхнее;нижнее;пульс;комментарий
                07.10.2026;118;76;79;левая
                20.10.2026 20:00;118;76;79;
                08.10.2026 06:48;76;118;79;
                """.trimIndent()
            val errors = ArrayList<RowError>()
            val source = sourceOf(text)
            val report = import(source, analyzer.detect(source), replaceAll = false, onError = { errors += it })
            assertEquals(2, report.imported)
            assertEquals(1, report.errorCount)
            assertEquals(1, report.withoutTime)
            assertEquals(1, report.aheadOfClock)
            assertEquals(1, report.armFromComment)
            assertEquals(listOf(RowError(4, RowProblem.SYS_NOT_GREATER)), errors)
            assertEquals(errors, report.errors)
        }

    @Test
    fun `nothing valid leaves the period empty`() =
        runTest {
            val report = importBytes("дата и время;верхнее;нижнее\nx;76;118\n".toByteArray())
            assertEquals(0, report.imported)
            assertNull(report.from)
        }

    @Test
    fun `200 000 rows fit the memory and the time`() =
        runTest {
            val text = StringBuilder("дата и время;верхнее;нижнее;пульс\n")
            val start = java.time.LocalDateTime.of(2000, 1, 1, 0, 0)
            val format =
                java.time.format.DateTimeFormatter
                    .ofPattern("dd.MM.yyyy HH:mm")
            repeat(200_000) { i ->
                text.append(format.format(start.plusMinutes(i * 60L))).append(";120;80;70\n")
            }
            val started = System.nanoTime()
            val report = importBytes(text.toString().toByteArray())
            // Hours that the clock change skips fall onto the next hour: those are duplicates.
            assertEquals(200_000, report.imported + report.duplicates)
            assertTrue(report.duplicates < 20, "${report.duplicates}")
            assertTrue((System.nanoTime() - started) / 1_000_000_000 < 60)
        }
}
