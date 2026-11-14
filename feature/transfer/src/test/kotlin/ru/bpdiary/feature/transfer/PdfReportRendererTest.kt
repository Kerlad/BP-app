package ru.bpdiary.feature.transfer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.bpdiary.app.feature.transfer.R
import ru.bpdiary.core.common.FixedTimeProvider
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Tag
import ru.bpdiary.core.model.Thresholds
import ru.bpdiary.core.ui.labelRes
import ru.bpdiary.domain.report.buildDoctorReport
import java.io.File
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru")
class PdfReportRendererTest {
    private val time = FixedTimeProvider.default()
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val today = time.now().atZone(time.zone()).toLocalDate()

    private fun readings(count: Int) =
        (1..count).map {
            Reading(
                takenAt = time.now().minusSeconds(it * 3_600L),
                zoneOffset = ZoneOffset.ofHours(3),
                sys = 110 + it % 50,
                dia = 70 + it % 30,
                pulse = if (it % 7 == 0) null else 60 + it % 30,
                arm = if (it % 2 == 0) Arm.LEFT else Arm.RIGHT,
                tags = if (it % 5 == 0) setOf(Tag.HEADACHE, Tag.AFTER_MEDICINE) else emptySet(),
                comment = if (it % 3 == 0) "после прогулки, " + "очень длинный комментарий ".repeat(8) else "",
            )
        }

    private fun report(count: Int) =
        buildDoctorReport(readings(count), Thresholds(), today.minusDays(29)..today, time.now(), time.zone())

    /** Bitmap sheets; with BP_DUMP_REPORT set to a folder, the pages are written there as PNG. */
    private class BitmapSheets : Sheets {
        val bitmaps = ArrayList<Bitmap>()

        override fun start(number: Int): Canvas {
            val bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            bitmaps += bitmap
            return Canvas(bitmap)
        }

        override fun finish() = Unit
    }

    private fun sheets(count: Int): BitmapSheets {
        val sheets = BitmapSheets()
        drawReport(resources, report(count), sheets)
        dump(count, sheets)
        return sheets
    }

    private fun dump(
        count: Int,
        sheets: BitmapSheets,
    ) {
        val dir = System.getenv("BP_DUMP_REPORT") ?: return
        sheets.bitmaps.forEachIndexed { i, bitmap ->
            File(dir, "report_${count}_page_${i + 1}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    private fun pages(count: Int): Int = sheets(count).bitmaps.size

    @Test
    fun `a sheet is drawn for every page of the report`() {
        // The first page holds 25 rows, the next ones 50 (docs/08 §8.2: no more than 50 on a page).
        assertEquals(1, pages(0))
        assertEquals(1, pages(1))
        assertEquals(2, pages(50))
        assertEquals(2, pages(51))
        assertEquals(5, pages(200))
    }

    /** NATIVE graphics: the legacy mode measures no real glyphs, and the check would pass for any text. */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `every pair of tags fits the tags column`() {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = SMALL_SIZE }
        val labels = Tag.entries.map { resources.getString(it.labelRes()) }
        val limit = COL_TAGS - 2 * CELL_PAD
        labels.forEach { a ->
            labels.forEach { b ->
                val text = if (a == b) a else listOf(a, b).joinToString(SEPARATOR)
                assertTrue("«$text» is ${paint.measureText(text)} wide, limit $limit", paint.measureText(text) <= limit)
            }
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the widest date fits the date column`() {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = SMALL_SIZE }
        val width = paint.measureText("28.08.2026")
        assertTrue("date is $width wide", width > 20f)
        assertTrue("date is $width wide, limit ${COLUMNS[0] - 2 * CELL_PAD}", width <= COLUMNS[0] - 2 * CELL_PAD)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `arm and status words fit their columns`() {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = SMALL_SIZE }
        val arms = listOf(R.string.csv_arm_left, R.string.csv_arm_right).map { resources.getString(it) }
        val statuses = Status.entries.map { resources.getString(it.labelRes()) }
        arms.forEach { assertTrue("«$it»", paint.measureText(it) <= COLUMNS[5] - 2 * CELL_PAD) }
        statuses.forEach {
            assertTrue("«$it» ${paint.measureText(it)}", paint.measureText(it) <= COLUMNS[6] - 2 * CELL_PAD)
        }
    }

    @Test
    fun `the columns add up to the width of the content`() {
        assertEquals(PAGE_WIDTH - 72f, COLUMNS.sum(), 0.001f)
    }

    @Test
    fun `the sheets are A4`() {
        assertTrue(sheets(60).bitmaps.all { it.width == 595 && it.height == 842 })
    }

    @Test
    fun `a report with long comments and every tag is drawn without failing`() {
        // What the page looks like is checked by eye on a device: Robolectric draws no text.
        assertEquals(3, pages(80))
    }
}
