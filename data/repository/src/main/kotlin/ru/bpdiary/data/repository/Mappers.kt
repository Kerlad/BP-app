package ru.bpdiary.data.repository

import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Reading
import ru.bpdiary.core.model.Reminder
import ru.bpdiary.core.model.Tag
import ru.bpdiary.data.database.ReadingEntity
import ru.bpdiary.data.database.ReminderEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

private const val MINUTES_IN_HOUR = 60

internal fun ReadingEntity.toDomain(): Reading =
    Reading(
        id = id,
        takenAt = Instant.ofEpochMilli(takenAt),
        zoneOffset = ZoneOffset.ofTotalSeconds(tzOffsetSeconds),
        sys = sys,
        dia = dia,
        pulse = pulse,
        arm = arm.toArm(),
        tags = decodeTags(tags),
        comment = comment,
        profileId = profileId,
    )

internal fun Reading.toEntity(
    createdAt: Long,
    updatedAt: Long,
    deletedAt: Long? = null,
): ReadingEntity =
    ReadingEntity(
        id = id,
        takenAt = takenAt.toEpochMilli(),
        tzOffsetSeconds = zoneOffset.totalSeconds,
        sys = sys,
        dia = dia,
        pulse = pulse,
        arm = arm.name,
        tags = encodeTags(tags),
        comment = comment,
        profileId = profileId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

private fun String.toArm(): Arm = Arm.entries.firstOrNull { it.name == this } ?: Arm.LEFT

/** A JSON array of tag keys in enum order, e.g. `["GOOD","HEADACHE"]`. The keys contain no characters to escape. */
internal fun encodeTags(tags: Set<Tag>): String =
    Tag.entries.filter { it in tags }.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"${it.name}\"" }

/** Unknown keys (from a newer version, a backup or a broken file) are dropped instead of failing. */
internal fun decodeTags(json: String): Set<Tag> {
    val keys =
        json
            .trim()
            .removePrefix("[")
            .removeSuffix("]")
            .split(',')
            .map { it.trim().trim('"') }
    return Tag.entries.filter { it.name in keys }.toSet()
}

internal fun ReminderEntity.toDomain(): Reminder =
    Reminder(
        id = id,
        time = LocalTime.of(minutesOfDay / MINUTES_IN_HOUR, minutesOfDay % MINUTES_IN_HOUR),
        label = label,
        days = daysFromMask(daysMask),
        enabled = enabled,
    )

internal fun Reminder.toEntity(): ReminderEntity =
    ReminderEntity(
        id = id,
        minutesOfDay = time.hour * MINUTES_IN_HOUR + time.minute,
        label = label,
        daysMask = daysToMask(days),
        enabled = enabled,
    )

/** Monday is bit 0, Sunday is bit 6. */
internal fun daysToMask(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

internal fun daysFromMask(mask: Int): Set<DayOfWeek> =
    DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
