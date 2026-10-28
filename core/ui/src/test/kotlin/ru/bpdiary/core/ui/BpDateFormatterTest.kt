package ru.bpdiary.core.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.bpdiary.core.model.Tag
import ru.bpdiary.domain.DayLabel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class BpDateFormatterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val formatter = BpDateFormatter(context.resources)

    @Test
    fun `today and yesterday`() {
        assertEquals("Сегодня, 09:12", formatter.dayTime(DayLabel.Today, LocalTime.of(9, 12)))
        assertEquals("Вчера, 21:40", formatter.dayTime(DayLabel.Yesterday, LocalTime.of(21, 40)))
    }

    @Test
    fun `older days use the genitive month`() {
        val label = DayLabel.Date(LocalDate.of(2026, 10, 18))
        assertEquals("18 октября, 21:40", formatter.dayTime(label, LocalTime.of(21, 40)))
        assertEquals("1 января", formatter.day(DayLabel.Date(LocalDate.of(2026, 1, 1))))
        assertEquals("31 декабря", formatter.day(DayLabel.Date(LocalDate.of(2026, 12, 31))))
    }

    @Test
    fun `home date header starts with a capital weekday`() {
        assertEquals("Вторник, 20 октября", formatter.weekdayDayMonth(LocalDate.of(2026, 10, 20)))
        assertEquals("Воскресенье, 1 ноября", formatter.weekdayDayMonth(LocalDate.of(2026, 11, 1)))
        assertEquals("Среда, 21 октября", formatter.weekdayDayMonth(LocalDate.of(2026, 10, 21)))
    }

    @Test
    fun `diary headers`() {
        assertEquals("Сегодня", formatter.dayHeader(DayLabel.Today))
        assertEquals("Вчера", formatter.dayHeader(DayLabel.Yesterday))
        assertEquals("18 октября, воскресенье", formatter.dayHeader(DayLabel.Date(LocalDate.of(2026, 10, 18))))
        assertEquals("1 января, четверг", formatter.dayHeader(DayLabel.Date(LocalDate.of(2026, 1, 1))))
    }

    @Test
    fun `short weekdays from Monday`() {
        assertEquals(
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"),
            DayOfWeek.entries.map { formatter.weekdayShort(it) },
        )
    }

    @Test
    fun `every tag has its label`() {
        val labels = Tag.entries.map { context.getString(it.labelRes()) }
        assertEquals(
            listOf("Хорошо", "Головная боль", "Головокружение", "Слабость", "После лекарства", "После нагрузки"),
            labels,
        )
    }
}
