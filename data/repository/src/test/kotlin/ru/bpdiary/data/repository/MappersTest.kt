package ru.bpdiary.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Reminder
import ru.bpdiary.core.model.Tag
import ru.bpdiary.data.database.ReadingEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

class MappersTest {
    private fun reading(
        offset: ZoneOffset = ZoneOffset.ofHours(3),
        pulse: Int? = 71,
        tags: Set<Tag> = emptySet(),
    ) = Reading(
        id = 5,
        takenAt = Instant.parse("2026-10-20T06:12:00Z"),
        zoneOffset = offset,
        sys = 128,
        dia = 82,
        pulse = pulse,
        arm = Arm.RIGHT,
        tags = tags,
        comment = "after tea",
        profileId = 1,
    )

    // --- tags -----------------------------------------------------------------------------------

    @Test
    fun `tags are written as a json array in enum order`() {
        assertEquals("[]", encodeTags(emptySet()))
        assertEquals(
            "[\"GOOD\",\"AFTER_EXERCISE\"]",
            encodeTags(setOf(Tag.AFTER_EXERCISE, Tag.GOOD)),
        )
    }

    @Test
    fun `tags round trip for every subset size`() {
        for (n in 0..Tag.entries.size) {
            val tags = Tag.entries.take(n).toSet()
            assertEquals(tags, decodeTags(encodeTags(tags)))
        }
        assertEquals(Tag.entries.toSet(), decodeTags(encodeTags(Tag.entries.toSet())))
    }

    @Test
    fun `unknown and malformed tag keys are dropped`() {
        assertEquals(setOf(Tag.HEADACHE), decodeTags("[\"HEADACHE\",\"FROM_THE_FUTURE\"]"))
        assertEquals(emptySet<Tag>(), decodeTags(""))
        assertEquals(emptySet<Tag>(), decodeTags("[]"))
        assertEquals(emptySet<Tag>(), decodeTags("garbage"))
        assertEquals(setOf(Tag.GOOD), decodeTags(" [ \"GOOD\" , \"\" ] "))
    }

    // --- readings -------------------------------------------------------------------------------

    @Test
    fun `reading round trips through the entity`() {
        val original = reading(tags = setOf(Tag.HEADACHE, Tag.AFTER_MEDICINE))
        assertEquals(original, original.toEntity(createdAt = 1, updatedAt = 2).toDomain())
    }

    @Test
    fun `zone offset is stored in seconds and restored`() {
        for (offset in listOf(ZoneOffset.UTC, ZoneOffset.ofHours(-5), ZoneOffset.ofHoursMinutes(5, 30))) {
            val entity = reading(offset = offset).toEntity(createdAt = 0, updatedAt = 0)
            assertEquals(offset.totalSeconds, entity.tzOffsetSeconds)
            assertEquals(offset, entity.toDomain().zoneOffset)
        }
    }

    @Test
    fun `null pulse stays null`() {
        val entity = reading(pulse = null).toEntity(createdAt = 0, updatedAt = 0)
        assertNull(entity.pulse)
        assertNull(entity.toDomain().pulse)
    }

    @Test
    fun `entity stores epoch millis and timestamps`() {
        val entity = reading().toEntity(createdAt = 10, updatedAt = 20, deletedAt = 30)
        assertEquals(Instant.parse("2026-10-20T06:12:00Z").toEpochMilli(), entity.takenAt)
        assertEquals(Triple(10L, 20L, 30L), Triple(entity.createdAt, entity.updatedAt, entity.deletedAt))
        assertEquals("RIGHT", entity.arm)
    }

    @Test
    fun `unknown arm falls back to left`() {
        val entity: ReadingEntity = reading().toEntity(createdAt = 0, updatedAt = 0).copy(arm = "BOTH")
        assertEquals(Arm.LEFT, entity.toDomain().arm)
    }

    // --- reminders ------------------------------------------------------------------------------

    @Test
    fun `day masks use Monday as bit zero`() {
        assertEquals(1, daysToMask(setOf(DayOfWeek.MONDAY)))
        assertEquals(64, daysToMask(setOf(DayOfWeek.SUNDAY)))
        assertEquals(31, daysToMask(DayOfWeek.entries.take(5).toSet()))
        assertEquals(127, daysToMask(DayOfWeek.entries.toSet()))
        assertEquals(0, daysToMask(emptySet()))
    }

    @Test
    fun `day masks round trip for all 128 values`() {
        for (mask in 0..127) assertEquals(mask, daysToMask(daysFromMask(mask)))
    }

    @Test
    fun `reminder round trips and keeps minutes of day`() {
        val reminder =
            Reminder(
                id = 3,
                time = LocalTime.of(21, 5),
                label = "Evening",
                days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                enabled = false,
            )
        val entity = reminder.toEntity()
        assertEquals(21 * 60 + 5, entity.minutesOfDay)
        assertEquals(17, entity.daysMask)
        assertEquals(reminder, entity.toDomain())
        assertEquals(LocalTime.of(0, 0), entity.copy(minutesOfDay = 0).toDomain().time)
        assertEquals(LocalTime.of(23, 59), entity.copy(minutesOfDay = 1439).toDomain().time)
    }
}
