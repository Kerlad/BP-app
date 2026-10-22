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
    fun `next on sys and dia moves focus`() {
        assertEquals(Field.DIA, press("n").first.field)
        assertEquals(Field.PULSE, press("nn").first.field)
    }

    @Test
    fun `tapping a field focuses it`() {
        val draft = Draft(sys = "128", field = Field.DIA).focus(Field.SYS)
        assertEquals(Field.SYS, draft.field)
        assertEquals("128", draft.sys)
    }

    @Test
    fun `typing in refocused field continues its value`() {
        val draft = Draft(sys = "12", field = Field.DIA).focus(Field.SYS)
        assertEquals("128", (reduce(draft, Key.Digit('8')) as DraftResult.Updated).draft.sys)
    }

    @Test
    fun `ignored returns the same draft untouched`() {
        val draft = Draft(pulse = "104", field = Field.PULSE)
        assertSame(DraftResult.Ignored, reduce(draft, Key.Digit('7')))
    }
}
