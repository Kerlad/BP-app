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

    private class Rng {
        private var s = SEED

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
                    LocalDate
                        .from(today)
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
}
