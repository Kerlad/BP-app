package ru.bpdiary.feature.reminders

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.bpdiary.domain.DaysSummary
import ru.bpdiary.domain.daysSummary
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.WEDNESDAY

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru")
class RemindersTextsTest {
    private val texts =
        RemindersTexts(ApplicationProvider.getApplicationContext<android.content.Context>().resources)

    private fun days(vararg days: DayOfWeek) = texts.days(daysSummary(days.toSet()))

    @Test
    fun `every day and workdays have their own words`() {
        assertEquals("Ежедневно", texts.days(DaysSummary.Daily))
        assertEquals("Пн – Пт", texts.days(DaysSummary.Workdays))
        assertEquals("Ежедневно", days(*DayOfWeek.entries.toTypedArray()))
    }

    @Test
    fun `other days are listed Monday first, short, separated by commas`() {
        assertEquals("Пн, Ср, Пт", days(FRIDAY, MONDAY, WEDNESDAY))
        assertEquals("Сб, Вс", days(SUNDAY, SATURDAY))
        assertEquals("Ср", days(WEDNESDAY))
    }
}
