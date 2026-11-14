package ru.bpdiary.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextLimitTest {
    @Test
    fun `a short text stays as it is`() {
        assertEquals("abc", "abc".takeWholeChars(120))
    }

    @Test
    fun `a cut never leaves a lone high surrogate`() {
        val text = "a".repeat(119) + "😀" + "tail"
        val cut = text.takeWholeChars(120)
        assertEquals(119, cut.length)
        assertEquals("a".repeat(119), cut)
    }

    @Test
    fun `a pair that fits is kept`() {
        val text = "a".repeat(118) + "😀" + "tail"
        assertEquals(120, text.takeWholeChars(120).length)
    }
}
