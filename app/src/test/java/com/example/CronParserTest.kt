package com.example

import com.example.scheduler.CronParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CronParserTest {
    @Test
    fun `validates standard 5-field cron expressions`() {
        assertTrue(CronParser.isValid("0 7 * * *"))
        assertTrue(CronParser.isValid("*/15 8-17 * * 1-5"))
        assertFalse(CronParser.isValid("0 7 * *"))
        assertFalse(CronParser.isValid("75 7 * * *"))
    }

    @Test
    fun `computes next run accurately`() {
        val tz = TimeZone.getTimeZone("UTC")
        val cal = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 13, 6, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val next = CronParser.nextRunMillis("0 7 * * *", cal.timeInMillis, tz)
        val out = Calendar.getInstance(tz).apply { timeInMillis = next }
        assertEquals(7, out.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, out.get(Calendar.MINUTE))
        assertEquals(13, out.get(Calendar.DAY_OF_MONTH))
    }
}
