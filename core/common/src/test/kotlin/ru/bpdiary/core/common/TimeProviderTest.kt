package ru.bpdiary.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

class TimeProviderTest {
    @Test
    fun `fixed provider returns the standard test time`() {
        val tp = FixedTimeProvider.default()
        assertEquals(Instant.parse("2026-10-20T06:12:00Z"), tp.now())
        assertEquals(ZoneId.of("Europe/Moscow"), tp.zone())
    }

    @Test
    fun `system provider reads the default zone on every call`() {
        val original = TimeZone.getDefault()
        try {
            val tp = SystemTimeProvider()
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals(ZoneId.of("Asia/Tokyo"), tp.zone())
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
            assertEquals(ZoneId.of("Europe/Berlin"), tp.zone())
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
