package ru.bpdiary.core.common

import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Reading
import java.time.LocalTime

/**
 * Literal port of `makeData()` from the design prototype (`BPApp.dc.html`).
 * The order of [Rng] calls matters and must not change: the stream is shared by all fields.
 */
object DemoData {
    private const val SEED = 11L
    private const val MULTIPLIER = 16807L
    private const val MODULUS = 2147483647L
    private const val DAYS = 30
    private const val EVENING_FROM_HOUR = 12
    private const val MINUTE_BASE = 5
    private const val MINUTE_SPREAD = 40
    private val HOURS = listOf(7, 21)

    private class Rng(
        seed: Long = SEED,
    ) {
        private var s = seed

        fun next(): Double {
            s = s * MULTIPLIER % MODULUS
            return s.toDouble() / MODULUS
        }
    }

    /** Newest first, ids 1..n assigned oldest first (as in the prototype). */
    fun readings(time: TimeProvider = FixedTimeProvider.default()): List<Reading> {
        val rng = Rng()
        val now = time.now()
        val zone = time.zone()
        val today = now.atZone(zone).toLocalDate()
        val out = mutableListOf<Reading>()
        for (d in DAYS - 1 downTo 0) {
            for (h in HOURS) {
                val minute = MINUTE_BASE + Math.floor(rng.next() * MINUTE_SPREAD).toInt()
                val takenAt =
                    today
                        .minusDays(d.toLong())
                        .atTime(LocalTime.of(h, minute))
                        .atZone(zone)
                        .toInstant()
                if (takenAt.isAfter(now)) continue
                val ev = if (h > EVENING_FROM_HOUR) 5 else 0
                val tr = -(DAYS - 1 - d) * 0.3
                val sp = if (rng.next() < 0.12) 14 else 0
                val dp = if (rng.next() < 0.06) -24 else 0
                val sys = Math.round(134 + ev + tr + sp + dp + (rng.next() - .5) * 16).toInt()
                val dia = Math.round(86 + ev * .6 + tr * .5 + sp * .5 + dp * .6 + (rng.next() - .5) * 10).toInt()
                val pulse = Math.round(71 + (rng.next() - .5) * 18).toInt()
                out +=
                    Reading(
                        id = out.size + 1L,
                        takenAt = takenAt,
                        zoneOffset = zone.rules.getOffset(takenAt),
                        sys = sys,
                        dia = dia,
                        pulse = pulse,
                        arm = Arm.LEFT,
                    )
            }
        }
        return out.reversed()
    }

    private const val BULK_SEED = 4321L
    private const val BULK_PER_DAY = 2
    private val BULK_HOURS = listOf(7, 21)

    /**
     * [count] readings for the benchmarks, newest first, two a day (07:xx and 21:xx) going back from today.
     * Not the prototype's data: the values only have to look like a diary, and the same call gives the same rows.
     * Ids are not set; the database assigns them.
     */
    fun bulk(
        count: Int,
        time: TimeProvider = FixedTimeProvider.default(),
    ): List<Reading> {
        val rng = Rng(BULK_SEED)
        val now = time.now()
        val zone = time.zone()
        val today = now.atZone(zone).toLocalDate()
        val out = mutableListOf<Reading>()
        var day = 0
        while (out.size < count) {
            for (h in BULK_HOURS.reversed()) {
                val minute = MINUTE_BASE + (rng.next() * MINUTE_SPREAD).toInt()
                val takenAt =
                    today
                        .minusDays(day.toLong())
                        .atTime(LocalTime.of(h, minute))
                        .atZone(zone)
                        .toInstant()
                // Today's evening reading is still to come when the clock is earlier.
                val sys = 105 + (rng.next() * 60).toInt()
                val dia = 65 + (rng.next() * 40).toInt().coerceAtMost(sys - 65 - 1)
                val pulse = 55 + (rng.next() * 40).toInt()
                if (takenAt.isAfter(now) || out.size >= count) continue
                out +=
                    Reading(
                        takenAt = takenAt,
                        zoneOffset = zone.rules.getOffset(takenAt),
                        sys = sys,
                        dia = dia,
                        pulse = pulse,
                        arm = Arm.LEFT,
                    )
            }
            day++
        }
        return out
    }
}
