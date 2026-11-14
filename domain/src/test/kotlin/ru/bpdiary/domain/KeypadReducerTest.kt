package ru.bpdiary.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import ru.bpdiary.core.model.Field

class KeypadReducerTest {
    /** Tokens: digits, `d` = Del, `n` = Next. Returns the draft and the last result. */
    private fun press(keys: String): Pair<Draft, DraftResult> {
        var draft = Draft()
        var result: DraftResult = DraftResult.Updated(draft)
        for (c in keys.filterNot { it == ' ' }) {
            val key =
                when (c) {
                    'd' -> Key.Del
                    'n' -> Key.Next
                    else -> Key.Digit(c)
                }
            result = reduce(draft, key)
            if (result is DraftResult.Updated) draft = result.draft
        }
        return draft to result
    }

    @ParameterizedTest(name = "[{0}] -> {1}/{2}/{3} {4}")
    @CsvSource(
        "128,128,'','',DIA",
        "95,95,'','',DIA",
        "12,12,'','',SYS",
        "12882,128,82,'',PULSE",
        "1288271,128,82,71,PULSE",
        "128d,12,'','',SYS",
        "12882ddd,12,'','',SYS",
        "1289,128,9,'',DIA",
        "d,'','','',SYS",
        "12882715,128,82,715,PULSE",
        "128821047,128,82,104,PULSE",
        "1288271,128,82,71,PULSE",
    )
    fun `keypad table`(
        keys: String,
        sys: String,
        dia: String,
        pulse: String,
        field: Field,
    ) {
        val (draft, _) = press(keys)
        assertEquals(Draft(sys, dia, pulse, field), draft)
    }

    @Test
    fun `two digits wait for a third while they can still grow`() {
        val (crisis, _) = press("2 1 0 1 1 0 7 0")
        assertEquals(Draft("210", "110", "70", Field.PULSE), crisis)
        val (max, _) = press("3 0 0 9 5")
        assertEquals(Draft("300", "95", "", Field.PULSE), max)
        val (waiting, _) = press("2 0")
        assertEquals(Draft("20", "", "", Field.SYS), waiting)
    }

    @Test
    fun `fourth digit in pulse is ignored`() {
        assertEquals(DraftResult.Ignored, press("128821047").second)
    }

    @Test
    fun `del on empty sys does nothing`() {
        val draft = Draft()
        val result = reduce(draft, Key.Del)
        assertEquals(DraftResult.Updated(draft), result)
    }

    @Test
    fun `next on pulse saves`() {
        assertEquals(DraftResult.Save, press("1288271n").second)
    }

    @Test
    fun `next on filled sys and dia moves focus`() {
        assertEquals(Field.DIA, press("12n").first.field)
        assertEquals(Field.PULSE, press("12n8n").first.field)
    }

    @Test
    fun `next on empty required field does nothing`() {
        assertEquals(DraftResult.Ignored, press("n").second)
        assertEquals(Field.SYS, press("n").first.field)
        // 142 auto-advances to DIA; a habitual arrow must not skip it.
        val (draft, result) = press("142n")
        assertEquals(DraftResult.Ignored, result)
        assertEquals(Draft("142", "", "", Field.DIA), draft)
    }

    @Test
    fun `scenario from test run gives 142 over 91 and pulse 76`() {
        assertEquals(Draft("142", "91", "76", Field.PULSE), press("142n9176").first)
    }

    @Test
    fun `next on empty pulse still saves`() {
        assertEquals(DraftResult.Save, press("12882n").second)
    }

    @Test
    fun `digit after tapping a filled field replaces its value`() {
        val draft = Draft(sys = "142", dia = "91", pulse = "76", field = Field.PULSE).focus(Field.SYS)
        val after = (reduce(draft, Key.Digit('1')) as DraftResult.Updated).draft
        assertEquals("1", after.sys)
        assertEquals(Field.SYS, after.field)
        // Further digits append and auto-advance as usual.
        val done = (reduce(after, Key.Digit('3')) as DraftResult.Updated).draft
        assertEquals("13", done.sys)
        val full = (reduce(done, Key.Digit('0')) as DraftResult.Updated).draft
        // DIA is filled, so its first digit replaces it (D-11).
        assertEquals(Draft("130", "91", "76", Field.DIA, replace = true), full)
    }

    @Test
    fun `replace works on a full field and on the pulse`() {
        val draft = Draft(pulse = "104", field = Field.SYS).focus(Field.PULSE)
        assertEquals("7", (reduce(draft, Key.Digit('7')) as DraftResult.Updated).draft.pulse)
    }

    @Test
    fun `del after tapping edits without replacing`() {
        val draft = Draft(sys = "142").focus(Field.SYS)
        val after = (reduce(draft, Key.Del) as DraftResult.Updated).draft
        assertEquals("14", after.sys)
        assertEquals("145", (reduce(after, Key.Digit('5')) as DraftResult.Updated).draft.sys)
    }

    /** Review D-11 (approved by the owner 2026-11-14): moving forward into a filled field replaces its value. */
    @Test
    fun `auto advance into a filled field replaces its value`() {
        val draft = Draft(sys = "14", dia = "9", field = Field.SYS)
        val after = (reduce(draft, Key.Digit('2')) as DraftResult.Updated).draft
        assertEquals(true, after.replace)
        assertEquals(Field.DIA, after.field)
    }

    @Test
    fun `editing 142 over 91 by typing 1 3 0 8 5 gives 130 over 85`() {
        var draft = Draft(sys = "142", dia = "91", pulse = "76").focus(Field.SYS)
        "13085".forEach { draft = (reduce(draft, Key.Digit(it)) as DraftResult.Updated).draft }
        assertEquals("130", draft.sys)
        assertEquals("85", draft.dia)
        assertEquals("76", draft.pulse)
        assertEquals(Field.PULSE, draft.field)
        // The pulse is next in line, so its first digit replaces it too.
        assertEquals("7", (reduce(draft, Key.Digit('7')) as DraftResult.Updated).draft.pulse)
    }

    @Test
    fun `next into a filled field replaces, into an empty one appends`() {
        val filled = (reduce(Draft(sys = "142", dia = "91"), Key.Next) as DraftResult.Updated).draft
        assertEquals(true, filled.replace)
        assertEquals("8", (reduce(filled, Key.Digit('8')) as DraftResult.Updated).draft.dia)
        val empty = (reduce(Draft(sys = "142"), Key.Next) as DraftResult.Updated).draft
        assertEquals(false, empty.replace)
    }

    @Test
    fun `del back into the previous field still edits its last digit`() {
        val draft = Draft(sys = "142", dia = "", field = Field.DIA)
        val back = (reduce(draft, Key.Del) as DraftResult.Updated).draft
        assertEquals(Draft("14", "", "", Field.SYS), back)
        assertEquals("145", (reduce(back, Key.Digit('5')) as DraftResult.Updated).draft.sys)
    }

    @Test
    fun `tapping a field focuses it`() {
        val draft = Draft(sys = "128", field = Field.DIA).focus(Field.SYS)
        assertEquals(Field.SYS, draft.field)
        assertEquals("128", draft.sys)
    }

    @Test
    fun `typing after a refocus replaces, then continues`() {
        val draft = Draft(sys = "12", field = Field.DIA).focus(Field.SYS)
        val first = (reduce(draft, Key.Digit('8')) as DraftResult.Updated).draft
        assertEquals("8", first.sys)
        assertEquals("80", (reduce(first, Key.Digit('0')) as DraftResult.Updated).draft.sys)
    }

    @Test
    fun `ignored returns the same draft untouched`() {
        val draft = Draft(pulse = "104", field = Field.PULSE)
        assertSame(DraftResult.Ignored, reduce(draft, Key.Digit('7')))
    }
}
