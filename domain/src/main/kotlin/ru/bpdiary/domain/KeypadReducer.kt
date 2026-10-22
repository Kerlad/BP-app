package ru.bpdiary.domain

import ru.bpdiary.core.model.Field

private const val MAX_DIGITS = 3

data class Draft(
    val sys: String = "",
    val dia: String = "",
    val pulse: String = "",
    val field: Field = Field.SYS,
) {
    fun value(f: Field): String =
        when (f) {
            Field.SYS -> sys
            Field.DIA -> dia
            Field.PULSE -> pulse
        }

    fun set(
        f: Field,
        v: String,
    ): Draft =
        when (f) {
            Field.SYS -> copy(sys = v)
            Field.DIA -> copy(dia = v)
            Field.PULSE -> copy(pulse = v)
        }

    fun focus(f: Field): Draft = copy(field = f)
}

sealed interface Key {
    data class Digit(
        val d: Char,
    ) : Key

    data object Del : Key

    data object Next : Key
}

sealed interface DraftResult {
    data class Updated(
        val draft: Draft,
    ) : DraftResult

    data object Ignored : DraftResult

    data object Save : DraftResult
}

private fun Field.previous(): Field =
    when (this) {
        Field.SYS -> Field.SYS
        Field.DIA -> Field.SYS
        Field.PULSE -> Field.DIA
    }

private fun Field.next(): Field =
    when (this) {
        Field.SYS -> Field.DIA
        Field.DIA -> Field.PULSE
        Field.PULSE -> Field.PULSE
    }

fun reduce(
    state: Draft,
    key: Key,
): DraftResult =
    when (key) {
        is Key.Del -> {
            val cur = state.value(state.field)
            if (cur.isEmpty() && state.field != Field.SYS) {
                // Move to the previous field and erase its last digit.
                val prev = state.field.previous()
                DraftResult.Updated(state.set(prev, state.value(prev).dropLast(1)).focus(prev))
            } else {
                DraftResult.Updated(state.set(state.field, cur.dropLast(1)))
            }
        }

        is Key.Next -> {
            if (state.field != Field.PULSE) {
                DraftResult.Updated(state.focus(state.field.next()))
            } else {
                DraftResult.Save
            }
        }

        is Key.Digit -> {
            val cur = state.value(state.field)
            if (cur.length >= MAX_DIGITS) {
                DraftResult.Ignored
            } else {
                val new = cur + key.d
                // 3 digits, or 2 digits when the first is not '1' (95 is complete, 12x is not).
                val advance =
                    state.field != Field.PULSE &&
                        (new.length == MAX_DIGITS || (new.length == 2 && new[0] != '1'))
                val updated = state.set(state.field, new)
                DraftResult.Updated(if (advance) updated.focus(state.field.next()) else updated)
            }
        }
    }
