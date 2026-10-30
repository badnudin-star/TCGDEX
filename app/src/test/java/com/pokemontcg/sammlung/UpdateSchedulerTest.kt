package com.pokemontcg.sammlung

import com.pokemontcg.sammlung.worker.UpdateScheduler
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class UpdateSchedulerTest {

    @Test
    fun testInitialDelayCalculationSameDayFuture() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val targetHour = 3
        val delay = UpdateScheduler.calculateInitialDelay(targetHour, now)
        val expectedOneHourMs = 60 * 60 * 1000L

        assertEquals(expectedOneHourMs, delay)
    }

    @Test
    fun testInitialDelayCalculationNextDayPast() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 5)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val targetHour = 3
        val delay = UpdateScheduler.calculateInitialDelay(targetHour, now)
        val expected22HoursMs = 22 * 60 * 60 * 1000L

        assertEquals(expected22HoursMs, delay)
    }
}
