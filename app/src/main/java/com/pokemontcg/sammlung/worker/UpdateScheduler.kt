package com.pokemontcg.sammlung.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object UpdateScheduler {

    const val PERIODIC_WORK_NAME = "catalog_update_periodic"

    fun calculateInitialDelay(targetHour: Int, nowCalendar: Calendar = Calendar.getInstance()): Long {
        val target = nowCalendar.clone() as Calendar
        target.set(Calendar.HOUR_OF_DAY, targetHour)
        target.set(Calendar.MINUTE, 0)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)

        if (!target.after(nowCalendar)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        return target.timeInMillis - nowCalendar.timeInMillis
    }

    fun schedulePeriodicSync(context: Context, wifiOnly: Boolean, targetHour: Int) {
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val initialDelayMs = calculateInitialDelay(targetHour)

        val request = PeriodicWorkRequestBuilder<ResumableCatalogWorker>(24, TimeUnit.HOURS)
            .addTag(ResumableCatalogWorker.SYNC_TAG)
            .setConstraints(constraints)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
