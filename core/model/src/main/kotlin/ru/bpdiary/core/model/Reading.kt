package ru.bpdiary.core.model

import java.time.Instant
import java.time.ZoneOffset

const val DEFAULT_PROFILE = 1L

data class Reading(
    val id: Long = 0,
    val takenAt: Instant,
    /** Offset at the moment of measurement; needed to compute the local day. */
    val zoneOffset: ZoneOffset,
    val sys: Int,
    val dia: Int,
    /** Null only for imported readings without pulse. */
    val pulse: Int?,
    val arm: Arm,
    val tags: Set<Tag> = emptySet(),
    val comment: String = "",
    val profileId: Long = DEFAULT_PROFILE,
)
