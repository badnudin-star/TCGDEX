package com.pokemontcg.sammlung.worker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

class UpdateSchedulerTest {

    @Test
    fun testCalculateInitialDelaySameDayFutureHour() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val delayMs = UpdateScheduler.calculateInitialDelay(targetHour = 14, nowCalendar = now)
        val delayHours = TimeUnit.MILLISECONDS.toHours(delayMs)
        assertEquals(4L, delayHours)
    }

    @Test
    fun testCalculateInitialDelayPastHourNextDay() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 15)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val delayMs = UpdateScheduler.calculateInitialDelay(targetHour = 3, nowCalendar = now)
        val delayHours = TimeUnit.MILLISECONDS.toHours(delayMs)
        assertEquals(12L, delayHours)
    }

    @Test
    fun testCalculateInitialDelaySameHour() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val delayMs = UpdateScheduler.calculateInitialDelay(targetHour = 10, nowCalendar = now)
        val delayHours = TimeUnit.MILLISECONDS.toHours(delayMs)
        assertEquals(24L, delayHours)
    }
}
