package ru.bpdiary.domain.csv

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class CsvDatesTest {
    private val moscow = ZoneId.of("Europe/Moscow")

    private fun parser(
        dayFirst: Boolean = true,
        zone: ZoneId = moscow,
    ) = CsvDateParser(zone, currentYear = 2026, dayFirst = dayFirst)

    private fun instant(value: String) = parser().parse(value)?.instant

    @Test
    fun `dotted with and without seconds`() {
        val expected = Instant.parse("2026-10-07T03:48:00Z")
        assertEquals(expected, instant("07.10.2026 06:48"))
        assertEquals(expected.plusSeconds(15), instant("07.10.2026 06:48:15"))
    }

    @Test
    fun `a two-digit year up to next year is 20yy, the others 19yy`() {
        assertEquals(Instant.parse("2027-10-07T03:48:00Z"), instant("07.10.27 06:48"))
        // Moscow was at +04:00 in the summer of 1998.
        assertEquals(Instant.parse("1998-10-07T02:48:00Z"), instant("07.10.98 06:48"))
    }

    @Test
    fun `ISO with an offset keeps the offset, without one the time is local`() {
        val withOffset = parser().parse("2026-10-07T06:48:00+05:00")
        assertEquals(Instant.parse("2026-10-07T01:48:00Z"), withOffset?.instant)
        assertEquals(ZoneOffset.ofHours(5), withOffset?.offset)
        assertEquals(Instant.parse("2026-10-07T03:48:00Z"), instant("2026-10-07 06:48"))
        assertEquals(Instant.parse("2026-10-07T03:48:30Z"), instant("2026-10-07T06:48:30"))
        assertEquals(Instant.parse("2026-10-07T06:48:00Z"), instant("2026-10-07T06:48:00Z"))
    }

    @Test
    fun `slashes follow the order of the file, the 12-hour form is American`() {
        assertEquals(Instant.parse("2026-05-03T03:48:00Z"), parser(dayFirst = true).parse("03/05/2026 06:48")?.instant)
        assertEquals(Instant.parse("2026-03-05T03:48:00Z"), parser(dayFirst = false).parse("03/05/2026 06:48")?.instant)
        assertEquals(Instant.parse("2026-03-05T15:15:00Z"), instant("03/05/2026 06:15 PM"))
        assertEquals(Instant.parse("2026-03-04T21:15:00Z"), instant("03/05/2026 12:15 AM"))
    }

    @Test
    fun `a bare date becomes noon and says so`() {
        val moment = parser().parse("07.10.2026")
        assertEquals(Instant.parse("2026-10-07T09:00:00Z"), moment?.instant)
        assertFalse(moment!!.hasTime)
        assertTrue(parser().parse("07.10.2026 12:00")!!.hasTime)
    }

    @Test
    fun `the offset is that of the moment, not of today`() {
        val berlin = ZoneId.of("Europe/Berlin")
        assertEquals(ZoneOffset.ofHours(2), parser(zone = berlin).parse("20.07.2026 08:00")?.offset)
        assertEquals(ZoneOffset.ofHours(1), parser(zone = berlin).parse("20.12.2026 08:00")?.offset)
    }

    @Test
    fun `nonsense is not a date`() {
        listOf("", "вчера", "31.02.2026 10:00", "07.13.2026", "2026-10-07 25:00", "118").forEach {
            assertNull(parser().parse(it), it)
        }
    }

    @Test
    fun `the file tells the order when a number is above 12`() {
        val scan = DayOrderScan()
        assertNull(scan.dayFirst)
        assertFalse(scan.ambiguous)
        scan.add("03/05/2026 06:48")
        assertTrue(scan.ambiguous)
        scan.add("13/05/2026 06:48")
        assertEquals(true, scan.dayFirst)
        assertFalse(scan.ambiguous)

        val american = DayOrderScan()
        american.add("05/13/2026")
        assertEquals(false, american.dayFirst)
    }
}
