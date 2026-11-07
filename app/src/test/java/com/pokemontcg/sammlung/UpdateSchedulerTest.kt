package com.pokemontcg.sammlung

import com.pokemontcg.sammlung.worker.UpdateScheduler
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

class UpdateSchedulerTest {

    @Test
    fun testDailyRunIsPinnedToThreeInTheMorning() {
        assertEquals(3, UpdateScheduler.DAILY_UPDATE_HOUR)

        val beforeThree = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(
            60 * 60 * 1000L,
            UpdateScheduler.calculateInitialDelay(nowCalendar = beforeThree)
        )
    }

    @Test
    fun testInitialDelayRollsOverToTheNextDay() {
        val afterThree = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 5)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val delayMs = UpdateScheduler.calculateInitialDelay(nowCalendar = afterThree)

        assertEquals(22L, TimeUnit.MILLISECONDS.toHours(delayMs))
    }

    @Test
    fun testMinutesAndSecondsOfTheTargetTimeAreZero() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 37)
            set(Calendar.SECOND, 42)
            set(Calendar.MILLISECOND, 500)
        }

        val target = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis + UpdateScheduler.calculateInitialDelay(nowCalendar = now)
        }

        assertEquals(UpdateScheduler.DAILY_UPDATE_HOUR, target.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, target.get(Calendar.MINUTE))
        assertEquals(0, target.get(Calendar.SECOND))
        assertEquals(0, target.get(Calendar.MILLISECOND))
    }
}
