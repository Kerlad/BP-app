package ru.bpdiary.feature.diary

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import ru.bpdiary.app.feature.diary.R
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.ui.BpDateFormatter
import ru.bpdiary.core.ui.labelRes
import ru.bpdiary.domain.dayLabel
import java.time.LocalDate

/** Russian texts of the diary from resources (docs/05 §5.3). */
class DiaryTexts(
    private val resources: Resources,
    private val dates: BpDateFormatter = BpDateFormatter(resources),
) {
    /** «Сегодня», «Вчера», «18 октября, воскресенье». */
    fun header(
        day: LocalDate,
        today: LocalDate,
    ): String = dates.dayHeader(dayLabel(day, today))

    fun time(row: DiaryRow): String = dates.time(row.time)

    /** «Повышенное · левая рука · Головная боль · после зарядки»: empty parts are left out. */
    fun note(row: DiaryRow): String {
        val parts =
            listOf(resources.getString(row.status.labelRes()), arm(row.arm)) +
                row.tags.sortedBy { it.ordinal }.map { resources.getString(it.labelRes()) } +
                row.comment.trim()
        return parts.filter { it.isNotEmpty() }.joinToString(resources.getString(R.string.diary_note_separator))
    }

    /** «Копировать значения». */
    fun copy(row: DiaryRow): String =
        if (row.pulse != null) {
            resources.getString(R.string.diary_copy_with_pulse, row.sys, row.dia, row.pulse)
        } else {
            resources.getString(R.string.diary_copy_without_pulse, row.sys, row.dia)
        }

    fun pulseDescription(pulse: Int): String = resources.getString(R.string.diary_pulse_description, pulse)

    fun filter(filter: DiaryFilter): String =
        resources.getString(
            when (filter) {
                DiaryFilter.ALL -> R.string.diary_filter_all
                DiaryFilter.HIGH -> R.string.diary_filter_high
                DiaryFilter.NORMAL -> R.string.diary_filter_normal
                DiaryFilter.LOW -> R.string.diary_filter_low
            },
        )

    private fun arm(arm: Arm): String =
        resources.getString(if (arm == Arm.LEFT) R.string.diary_arm_left else R.string.diary_arm_right)
}

@Composable
fun rememberDiaryTexts(): DiaryTexts {
    val resources = LocalResources.current
    return remember(resources) { DiaryTexts(resources) }
}
