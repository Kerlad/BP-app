package ru.bpdiary.feature.transfer

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.bpdiary.app.feature.transfer.R
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.ui.labelRes
import ru.bpdiary.domain.LinePoint
import ru.bpdiary.domain.report.DoctorReport
import ru.bpdiary.domain.report.ReportPage
import ru.bpdiary.domain.report.ReportRow
import ru.bpdiary.domain.report.StatLabel
import ru.bpdiary.domain.report.StatRow
import ru.bpdiary.domain.report.StatValue
import ru.bpdiary.domain.yLine
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// A4 in points (1/72 inch).
internal const val PAGE_WIDTH = 595
internal const val PAGE_HEIGHT = 842
private const val MARGIN = 36f
private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

// Page 1 head.
private const val TITLE_BASELINE = 54f
private const val PERIOD_BASELINE = 74f
private const val LEVELS_BASELINE = 88f
private const val RULE_Y = 98f
private const val TITLE_SIZE = 17f
private const val TEXT_SIZE = 10f
internal const val SMALL_SIZE = 8.5f
private const val HAIR = 0.6f
private const val STROKE_SERIES = 1.6f

// Chart: the units of the Charts screen (docs/06 §6.5), the same 320 x 200 box stretched into a rectangle.
private const val CHART_UNITS_X = 320f
private const val CHART_UNITS_Y = 200f
private const val CHART_TITLE_BASELINE = 118f
private const val CHART_TOP = 126f
private const val CHART_HEIGHT = 150f
private const val CHART_PLOT_START = 30f
private const val CHART_PLOT_END = 318f
private const val GRID_TOP = 160
private const val GRID_BOTTOM = 60
private const val GRID_STEP = 20
private const val DOT_RADIUS = 1.8f
private const val MAX_DOTS = 120
private const val LABEL_SHIFT = 3f
private const val DASH_ON = 4f
private const val DASH_OFF = 3f
private const val AXIS_LABEL_GAP = 11f
private const val LEGEND_SWATCH = 14f
private const val LEGEND_GAP = 6f
private const val LEGEND_STEP = 16f
private const val LEGEND_HEIGHT_BASELINE = 118f
private const val LABEL_X = 2f
private const val LABEL_X_NARROW = 8f
private const val THREE_DIGITS = 100

// Statistics.
private const val STATS_TITLE_BASELINE = 304f
private const val STATS_FIRST_BASELINE = 322f
private const val STATS_STEP = 14f
private const val STATS_COLUMN_GAP = 20f
private const val STATS_VALUE_X = 92f
private const val STATS_PAIR = 2
private const val STATS_NAME_GAP = 10f

// Table.
private const val TABLE_TOP_FIRST = 384f
private const val TABLE_TOP_NEXT = 62f
private const val HEADER_ROW = 18f
private const val ROW_HEIGHT = 13.8f
internal const val CELL_PAD = 3f
private const val TEXT_RISE = 9.8f
private const val HEADER_RISE = 12.5f
private const val RUNNING_HEADER_BASELINE = 48f
internal const val SEPARATOR = " · "

// Foot.
private const val DISCLAIMER_FIRST_BASELINE = 784f
private const val DISCLAIMER_STEP = 10f
private const val DISCLAIMER_SIZE = 7.5f
private const val FOOT_BASELINE = 826f

// Printable colours: a light ground and dark text on white paper, whatever the theme of the phone (docs/08 §8.2).
private val INK = "#22291F".toColorInt()
private val MUTED = "#5A6352".toColorInt()
private val LINE = "#C9CEBD".toColorInt()
private val STRIPE = "#F3F4EE".toColorInt()
private val HEAD_FILL = "#E6E9DA".toColorInt()
private val SYS = "#2F5A26".toColorInt()
private val DIA = "#6B7260".toColorInt()
private val HIGH_LINE = "#B2431F".toColorInt()
private val HIGH_FILL = "#F4D6CB".toColorInt()
private val NORMAL_FILL = "#D9E7CC".toColorInt()
private val LOW_FILL = "#D3E4EF".toColorInt()

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu")
private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// Column widths of the table, in points; they add up to CONTENT_WIDTH.
// Tags get 150: two of them joined by SEPARATOR must fit (A-10); the rest gave way to it.
private const val COL_DATE = 50f
private const val COL_TIME = 34f
private const val COL_SYS = 44f
private const val COL_DIA = 42f
private const val COL_PULSE = 34f
private const val COL_ARM = 34f
private const val COL_STATUS = 58f
internal const val COL_TAGS = 150f
private const val COL_COMMENT = 77f
internal val COLUMNS =
    floatArrayOf(COL_DATE, COL_TIME, COL_SYS, COL_DIA, COL_PULSE, COL_ARM, COL_STATUS, COL_TAGS, COL_COMMENT)

/** Draws the report of docs/08 §8.2 into a PDF; the layout is [report] pages, one A4 sheet each. */
interface ReportRenderer {
    /** The PDF bytes. */
    suspend fun render(report: DoctorReport): ByteArray
}

/** The sheets the report is drawn on: a PDF page in the app, a bitmap in the tests (Robolectric has no PDF). */
internal interface Sheets {
    fun start(number: Int): Canvas

    fun finish()
}

/** One A4 sheet per page of the [report]. */
internal fun drawReport(
    resources: Resources,
    report: DoctorReport,
    sheets: Sheets,
) {
    val painter = Painter(resources, report)
    report.pages.forEach { page ->
        painter.draw(sheets.start(page.number), page)
        sheets.finish()
    }
}

class PdfReportRenderer
    @Inject
    constructor(
        private val resources: Resources,
    ) : ReportRenderer {
        override suspend fun render(report: DoctorReport): ByteArray =
            withContext(Dispatchers.Default) {
                val document = PdfDocument()
                try {
                    drawReport(resources, report, PdfSheets(document))
                    ByteArrayOutputStream().also { document.writeTo(it) }.toByteArray()
                } finally {
                    document.close()
                }
            }
    }

private class PdfSheets(
    private val document: PdfDocument,
) : Sheets {
    private var page: PdfDocument.Page? = null

    override fun start(number: Int): Canvas {
        val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create()
        return document.startPage(info).also { page = it }.canvas
    }

    override fun finish() {
        page?.let { document.finishPage(it) }
        page = null
    }
}

/** Many small drawing steps of one page: splitting them across classes would only pass the same paints around. */
@Suppress("TooManyFunctions")
private class Painter(
    private val resources: Resources,
    private val report: DoctorReport,
) {
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = INK }
    private val bold = TextPaint(text).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private fun string(
        id: Int,
        vararg args: Any,
    ) = resources.getString(id, *args)

    fun draw(
        canvas: Canvas,
        page: ReportPage,
    ) {
        val tableTop =
            if (page.isFirst) {
                head(canvas)
                chart(canvas)
                statistics(canvas)
                TABLE_TOP_FIRST
            } else {
                runningHeader(canvas)
                TABLE_TOP_NEXT
            }
        table(canvas, page.rows, tableTop)
        foot(canvas, page)
    }

    // --- page 1: header, chart, statistics ---------------------------------------------------------

    private fun head(canvas: Canvas) {
        text.textSize = TITLE_SIZE
        bold.textSize = TITLE_SIZE
        canvas.drawText(string(R.string.report_title), MARGIN, TITLE_BASELINE, bold)
        text.textSize = TEXT_SIZE
        val generated = string(R.string.report_generated, DATE.format(report.generatedOn))
        text.textAlign = Paint.Align.RIGHT
        canvas.drawText(generated, PAGE_WIDTH - MARGIN, TITLE_BASELINE, text)
        text.textAlign = Paint.Align.LEFT
        val period = string(R.string.csv_meta_period, DATE.format(report.from), DATE.format(report.to), report.count)
        canvas.drawText(period, MARGIN, PERIOD_BASELINE, text)
        val th = report.thresholds
        val levels =
            string(R.string.csv_meta_levels, th.highSys, th.highDia, th.lowSys, th.lowDia, th.pulseLow, th.pulseHigh)
        text.color = MUTED
        canvas.drawText(levels, MARGIN, LEVELS_BASELINE, text)
        text.color = INK
        stroke.color = LINE
        stroke.strokeWidth = HAIR
        canvas.drawLine(MARGIN, RULE_Y, PAGE_WIDTH - MARGIN, RULE_Y, stroke)
    }

    private fun runningHeader(canvas: Canvas) {
        text.textSize = SMALL_SIZE
        text.color = MUTED
        val period = string(R.string.csv_meta_period, DATE.format(report.from), DATE.format(report.to), report.count)
        canvas.drawText(string(R.string.report_title) + " — " + period, MARGIN, RUNNING_HEADER_BASELINE, text)
        text.color = INK
    }

    private fun chart(canvas: Canvas) {
        val chart = report.chart
        bold.textSize = TEXT_SIZE
        canvas.drawText(string(R.string.report_chart_title), MARGIN, CHART_TITLE_BASELINE, bold)
        legend(canvas)
        if (chart == null) return
        val sx = CONTENT_WIDTH / CHART_UNITS_X
        val sy = CHART_HEIGHT / CHART_UNITS_Y

        fun x(units: Float) = MARGIN + units * sx

        fun y(units: Float) = CHART_TOP + units * sy

        text.textSize = SMALL_SIZE
        text.color = MUTED
        stroke.strokeWidth = HAIR
        stroke.pathEffect = null
        for (v in GRID_TOP downTo GRID_BOTTOM step GRID_STEP) {
            val gy = y(yLine(v))
            stroke.color = LINE
            canvas.drawLine(x(CHART_PLOT_START), gy, x(CHART_PLOT_END), gy, stroke)
            val label = if (v >= THREE_DIGITS) LABEL_X else LABEL_X_NARROW
            canvas.drawText("$v", x(label), gy + LABEL_SHIFT, text)
        }
        stroke.color = HIGH_LINE
        stroke.pathEffect = DashPathEffect(floatArrayOf(DASH_ON, DASH_OFF), 0f)
        listOf(chart.highSysY, chart.highDiaY).forEach {
            canvas.drawLine(x(CHART_PLOT_START), y(it), x(CHART_PLOT_END), y(it), stroke)
        }
        stroke.pathEffect = null
        series(canvas, chart.points, { y(it.yDia) }, ::x, DIA)
        series(canvas, chart.points, { y(it.ySys) }, ::x, SYS)
        if (chart.points.size <= MAX_DOTS) {
            chart.points.forEach { p ->
                fill.color = DIA
                canvas.drawCircle(x(p.x), y(p.yDia), DOT_RADIUS, fill)
                fill.color = if (p.highStatus) HIGH_LINE else SYS
                canvas.drawCircle(x(p.x), y(p.ySys), DOT_RADIUS, fill)
            }
        }
        val first =
            report.pages
                .first()
                .rows
                .firstOrNull()
        val last =
            report.pages
                .last()
                .rows
                .lastOrNull()
        if (first != null && last != null) {
            val base = CHART_TOP + CHART_HEIGHT + AXIS_LABEL_GAP
            text.textAlign = Paint.Align.LEFT
            canvas.drawText(SHORT_DATE.format(first.day), x(CHART_PLOT_START), base, text)
            text.textAlign = Paint.Align.RIGHT
            canvas.drawText(SHORT_DATE.format(last.day), x(CHART_PLOT_END), base, text)
            text.textAlign = Paint.Align.LEFT
        }
        text.color = INK
    }

    private fun series(
        canvas: Canvas,
        points: List<LinePoint>,
        y: (LinePoint) -> Float,
        x: (Float) -> Float,
        color: Int,
    ) {
        val path = Path()
        points.forEachIndexed { i, p ->
            if (i == 0) path.moveTo(x(p.x), y(p)) else path.lineTo(x(p.x), y(p))
        }
        stroke.color = color
        stroke.strokeWidth = STROKE_SERIES
        canvas.drawPath(path, stroke)
    }

    /** Names the lines by word and by line style as well as by colour. */
    private fun legend(canvas: Canvas) {
        text.textSize = SMALL_SIZE
        var right = PAGE_WIDTH - MARGIN
        val entries =
            listOf(
                Triple(string(R.string.report_legend_threshold), HIGH_LINE, true),
                Triple(string(R.string.report_legend_dia), DIA, false),
                Triple(string(R.string.report_legend_sys), SYS, false),
            )
        entries.forEach { (label, color, dashed) ->
            val width = text.measureText(label)
            canvas.drawText(label, right - width, LEGEND_HEIGHT_BASELINE, text)
            val end = right - width - LEGEND_GAP
            stroke.color = color
            stroke.strokeWidth = STROKE_SERIES
            stroke.pathEffect = if (dashed) DashPathEffect(floatArrayOf(DASH_ON, DASH_OFF), 0f) else null
            val ly = LEGEND_HEIGHT_BASELINE - LABEL_SHIFT
            canvas.drawLine(end - LEGEND_SWATCH, ly, end, ly, stroke)
            stroke.pathEffect = null
            right = end - LEGEND_SWATCH - LEGEND_STEP
        }
    }

    private fun statistics(canvas: Canvas) {
        bold.textSize = TEXT_SIZE
        canvas.drawText(string(R.string.report_stats_title), MARGIN, STATS_TITLE_BASELINE, bold)
        text.textSize = TEXT_SIZE
        val lines = report.stats.filter { it.label != StatLabel.IN_RANGE }
        val perColumn = (lines.size + STATS_PAIR - 1) / STATS_PAIR
        val columnWidth = (CONTENT_WIDTH - STATS_COLUMN_GAP) / STATS_PAIR
        lines.forEachIndexed { i, row ->
            val left = MARGIN + (i / perColumn) * (columnWidth + STATS_COLUMN_GAP)
            val baseline = STATS_FIRST_BASELINE + (i % perColumn) * STATS_STEP
            text.color = MUTED
            canvas.drawText(statName(row.label), left, baseline, text)
            text.color = INK
            canvas.drawText(statValue(row), left + STATS_VALUE_X, baseline, text)
        }
        report.stats.firstOrNull { it.label == StatLabel.IN_RANGE }?.let { row ->
            val baseline = STATS_FIRST_BASELINE + perColumn * STATS_STEP
            text.color = MUTED
            val name = statName(row.label)
            canvas.drawText(name, MARGIN, baseline, text)
            text.color = INK
            // The name is longer than the column of the lines above: the value starts after it.
            canvas.drawText(statValue(row), MARGIN + text.measureText(name) + STATS_NAME_GAP, baseline, text)
        }
    }

    private fun statName(label: StatLabel): String =
        string(
            when (label) {
                StatLabel.AVERAGE -> R.string.report_stat_average
                StatLabel.MORNING -> R.string.report_stat_morning
                StatLabel.EVENING -> R.string.report_stat_evening
                StatLabel.PULSE -> R.string.report_stat_pulse
                StatLabel.MAX -> R.string.report_stat_max
                StatLabel.MIN -> R.string.report_stat_min
                StatLabel.IN_RANGE -> R.string.report_stat_in_range
            },
        )

    private fun statValue(row: StatRow): String =
        when (val v = row.value) {
            is StatValue.Pressure -> {
                val base = string(R.string.report_pressure, v.sys, v.dia)
                val day = row.day
                val count = row.count
                when {
                    day != null -> {
                        string(R.string.report_stat_on_day, base, SHORT_DATE.format(day))
                    }

                    count != null && row.label != StatLabel.AVERAGE -> {
                        string(R.string.report_stat_with_count, base, count)
                    }

                    else -> {
                        base
                    }
                }
            }

            is StatValue.Beats -> {
                string(R.string.report_stat_beats, v.pulse)
            }

            is StatValue.Share -> {
                string(R.string.report_stat_share, v.percent, v.high, v.normal, v.low)
            }
        }

    // --- the table -----------------------------------------------------------------------------------

    private fun table(
        canvas: Canvas,
        rows: List<ReportRow>,
        top: Float,
    ) {
        val names = resources.getStringArray(R.array.csv_columns).take(COLUMNS.size)
        fill.color = HEAD_FILL
        canvas.drawRect(MARGIN, top, PAGE_WIDTH - MARGIN, top + HEADER_ROW, fill)
        bold.textSize = SMALL_SIZE
        var x = MARGIN
        names.forEachIndexed { i, name ->
            canvas.drawText(clip(name, bold, COLUMNS[i] - 2 * CELL_PAD), x + CELL_PAD, top + HEADER_RISE, bold)
            x += COLUMNS[i]
        }
        text.textSize = SMALL_SIZE
        rows.forEachIndexed { index, row ->
            val rowTop = top + HEADER_ROW + index * ROW_HEIGHT
            if (index % STATS_PAIR == 1) {
                fill.color = STRIPE
                canvas.drawRect(MARGIN, rowTop, PAGE_WIDTH - MARGIN, rowTop + ROW_HEIGHT, fill)
            }
            row(canvas, row, rowTop)
        }
        stroke.color = LINE
        stroke.strokeWidth = HAIR
        val bottom = top + HEADER_ROW + rows.size * ROW_HEIGHT
        canvas.drawLine(MARGIN, bottom, PAGE_WIDTH - MARGIN, bottom, stroke)
    }

    private fun row(
        canvas: Canvas,
        row: ReportRow,
        rowTop: Float,
    ) {
        val tags = row.tags.joinToString(SEPARATOR) { string(it.labelRes()) }
        val cells =
            listOf(
                DATE.format(row.day),
                TIME.format(row.time),
                "${row.sys}",
                "${row.dia}",
                row.pulse?.toString() ?: "—",
                string(if (row.arm == Arm.LEFT) R.string.csv_arm_left else R.string.csv_arm_right),
                string(row.status.labelRes()),
                tags,
                row.comment.replace(Regex("\\s+"), " "),
            )
        var x = MARGIN
        cells.forEachIndexed { i, cell ->
            if (i == STATUS_COLUMN) {
                fill.color = statusFill(row.status)
                canvas.drawRect(x, rowTop, x + COLUMNS[i], rowTop + ROW_HEIGHT, fill)
            }
            canvas.drawText(clip(cell, text, COLUMNS[i] - 2 * CELL_PAD), x + CELL_PAD, rowTop + TEXT_RISE, text)
            x += COLUMNS[i]
        }
    }

    private fun statusFill(status: Status): Int =
        when (status) {
            Status.HIGH -> HIGH_FILL
            Status.NORMAL -> NORMAL_FILL
            Status.LOW -> LOW_FILL
        }

    private fun clip(
        value: String,
        paint: TextPaint,
        width: Float,
    ): String = TextUtils.ellipsize(value, paint, width, TextUtils.TruncateAt.END).toString()

    // --- foot ------------------------------------------------------------------------------------------

    private fun foot(
        canvas: Canvas,
        page: ReportPage,
    ) {
        text.textSize = DISCLAIMER_SIZE
        text.color = MUTED
        wrap(string(R.string.report_disclaimer), CONTENT_WIDTH).forEachIndexed { i, line ->
            canvas.drawText(line, MARGIN, DISCLAIMER_FIRST_BASELINE + i * DISCLAIMER_STEP, text)
        }
        text.textSize = SMALL_SIZE
        text.textAlign = Paint.Align.RIGHT
        canvas.drawText(string(R.string.report_page, page.number, page.total), PAGE_WIDTH - MARGIN, FOOT_BASELINE, text)
        text.textAlign = Paint.Align.LEFT
        text.color = INK
    }

    private fun wrap(
        value: String,
        width: Float,
    ): List<String> {
        val lines = ArrayList<String>()
        var current = ""
        for (word in value.split(' ')) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (text.measureText(candidate) <= width || current.isEmpty()) {
                current = candidate
            } else {
                lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }
}

private const val STATUS_COLUMN = 6
