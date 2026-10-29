package ru.bpdiary.app

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpenRequestTest {
    private fun notification() = Intent().putExtra("open", "entry_new")

    @Test
    fun `the notification extra asks for a new reading`() {
        assertTrue(notification().requestsNewEntry())
    }

    @Test
    fun `a plain launch does not`() {
        assertFalse(Intent(Intent.ACTION_MAIN).requestsNewEntry())
    }

    @Test
    fun `an unknown value does not`() {
        assertFalse(Intent().putExtra("open", "settings").requestsNewEntry())
    }

    @Test
    fun `restoring the task from the recent apps list does not open Entry again`() {
        assertFalse(notification().addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY).requestsNewEntry())
    }
}
