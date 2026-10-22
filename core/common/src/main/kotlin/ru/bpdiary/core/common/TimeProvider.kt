package ru.bpdiary.core.common

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** The only source of time for domain code (D-09). */
interface TimeProvider {
    fun now(): Instant

    fun zone(): ZoneId
}

/** Reads the system zone on every call, so a zone change at runtime is picked up. */
class SystemTimeProvider : TimeProvider {
    override fun now(): Instant = Clock.systemUTC().instant()

    override fun zone(): ZoneId = ZoneId.systemDefault()
}

class FixedTimeProvider(
    private val instant: Instant,
    private val zone: ZoneId,
) : TimeProvider {
    override fun now(): Instant = instant

    override fun zone(): ZoneId = zone

    companion object {
        /** Standard test time: 2026-10-20 09:12 in Europe/Moscow. */
        fun default(): FixedTimeProvider =
            FixedTimeProvider(Instant.parse("2026-10-20T06:12:00Z"), ZoneId.of("Europe/Moscow"))
    }
}

/** For tests that need time to move: starts at [start] and only changes through [advance]. */
class ManualTimeProvider(
    start: Instant,
    private val zone: ZoneId,
) : TimeProvider {
    private var current = start

    fun advance(by: Duration) {
        current = current.plus(by)
    }

    override fun now(): Instant = current

    override fun zone(): ZoneId = zone
}
