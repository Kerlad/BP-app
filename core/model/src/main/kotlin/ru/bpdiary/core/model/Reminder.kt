package ru.bpdiary.core.model

import java.time.DayOfWeek
import java.time.LocalTime

data class Reminder(
    val id: Long = 0,
    val time: LocalTime,
    val label: String,
    /** An empty set is invalid. */
    val days: Set<DayOfWeek>,
    val enabled: Boolean,
)
