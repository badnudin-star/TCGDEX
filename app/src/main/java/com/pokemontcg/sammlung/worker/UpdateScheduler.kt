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

    /**
     * Marks the periodic 03:00 work so the sync banner can tell it apart from manually started
     * syncs: the periodic work is permanently ENQUEUED between its runs and must not show up
     * as a running update.
     */
    const val PERIODIC_SYNC_TAG = "catalog_sync_periodic"

    /**
     * Catalog and prices are refreshed once per day at 03:00 local time. The hour is fixed on
     * purpose: the daily price report is only meaningful when every device collects its price
     * points in the same nightly window.
     */
    const val DAILY_UPDATE_HOUR = 3

    fun calculateInitialDelay(
        targetHour: Int = DAILY_UPDATE_HOUR,
        nowCalendar: Calendar = Calendar.getInstance()
    ): Long {
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

    fun schedulePeriodicSync(context: Context, wifiOnly: Boolean) {
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val initialDelayMs = calculateInitialDelay()

        val request = PeriodicWorkRequestBuilder<ResumableCatalogWorker>(24, TimeUnit.HOURS)
            .addTag(ResumableCatalogWorker.SYNC_TAG)
            .addTag(PERIODIC_SYNC_TAG)
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
