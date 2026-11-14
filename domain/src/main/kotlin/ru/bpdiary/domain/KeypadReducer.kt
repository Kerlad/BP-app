package ru.bpdiary.domain

import ru.bpdiary.core.model.Field

private const val MAX_DIGITS = 3
private const val DIGIT_STEP = 10

/**
 * Entry draft. [replace] is true while the focused field was chosen by tapping it (not by an
 * auto-advance): the next digit then replaces the field's value instead of being appended.
 */
data class Draft(
    val sys: String = "",
    val dia: String = "",
    val pulse: String = "",
    val field: Field = Field.SYS,
    val replace: Boolean = false,
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

    /** Focus by a tap on the field: the first digit typed replaces its value (deviation from docs/06 §6.2). */
    fun focus(f: Field): Draft = copy(field = f, replace = true)

    /** Focus moved by the keypad itself (auto-advance, Del): digits append as usual. */
    internal fun advanceTo(f: Field): Draft = copy(field = f, replace = false)
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

private fun Field.canGrow(twoDigits: String): Boolean =
    twoDigits.toInt() * DIGIT_STEP <=
        when (this) {
            Field.SYS -> ValidationRules.SYS_MAX
            Field.DIA -> ValidationRules.DIA_MAX
            Field.PULSE -> 0
        }

private fun Field.next(): Field =
    when (this) {
        Field.SYS -> Field.DIA
        Field.DIA -> Field.PULSE
        Field.PULSE -> Field.PULSE
    }

/**
 * Keypad rules of docs/06 §6.2 with three deviations from the test run of 2026-11-07:
 * 1. Next on an empty required field (SYS, DIA) does nothing ([DraftResult.Ignored]), so a habitual
 *    «→» after an auto-advance cannot skip DIA and push its digits into the pulse.
 * 2. A field chosen by tapping ([Draft.replace]) takes the first digit as a replacement of its value.
 * 3. (UI side) Save with incomplete data highlights the first empty field and focuses it.
 *
 * Deviation from docs/06 §6.2 after the 2026-11-14 review: after two digits the field waits for a third while
 * the two digits can still grow into a valid three-digit value (twoDigits * 10 <= the field's maximum), so
 * 2,1,0 gives 210 and not 21 + 0. The pulse has no auto-advance.
 */
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
                DraftResult.Updated(state.set(prev, state.value(prev).dropLast(1)).advanceTo(prev))
            } else {
                DraftResult.Updated(state.set(state.field, cur.dropLast(1)).copy(replace = false))
            }
        }

        is Key.Next -> {
            if (state.field != Field.PULSE) {
                if (state.value(state.field).isEmpty()) {
                    DraftResult.Ignored
                } else {
                    DraftResult.Updated(state.advanceTo(state.field.next()))
                }
            } else {
                DraftResult.Save
            }
        }

        is Key.Digit -> {
            val cur = if (state.replace) "" else state.value(state.field)
            if (cur.length >= MAX_DIGITS) {
                DraftResult.Ignored
            } else {
                val new = cur + key.d
                // 3 digits, or 2 digits that cannot grow into a valid 3-digit value (95 is complete, 12x is not).
                val advance =
                    state.field != Field.PULSE &&
                        (new.length == MAX_DIGITS || (new.length == 2 && !state.field.canGrow(new)))
                val updated = state.set(state.field, new)
                val result = if (advance) updated.advanceTo(state.field.next()) else updated.copy(replace = false)
                DraftResult.Updated(result)
            }
        }
    }
