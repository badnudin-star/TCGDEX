package com.pokemontcg.sammlung.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pokemontcg.sammlung.R
import com.pokemontcg.sammlung.data.importer.CatalogImportResult
import com.pokemontcg.sammlung.data.local.AppDatabase
import com.pokemontcg.sammlung.data.local.dao.SyncCheckpointDao
import com.pokemontcg.sammlung.data.local.user.UserDatabase
import com.pokemontcg.sammlung.data.local.entities.SyncCheckpointEntity
import com.pokemontcg.sammlung.data.repository.PriceRepository
import com.pokemontcg.sammlung.data.repository.CollectionRepository
import com.pokemontcg.sammlung.data.repository.SyncRepository
import com.pokemontcg.sammlung.data.preferences.AppPreferencesRepository
import com.pokemontcg.sammlung.util.AppLogger
import com.pokemontcg.sammlung.util.PriceAlertManager
import com.pokemontcg.sammlung.util.PriceAlertSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class ResumableCatalogWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val UNIQUE_WORK_NAME = "ResumableCatalogUpdateWork"
        const val CONTINUATION_WORK_NAME = "catalog_update_continue"
        const val SYNC_TAG = "catalog_sync"
        const val KEY_PROGRESS_MESSAGE = "progress_message"
        const val KEY_PROGRESS_PERCENT = "progress_percent"
        const val KEY_HAS_FAILURES = "has_failures"
        const val KEY_STARTED_AT = "started_at"
        const val KEY_FINISHED_AT = "finished_at"
        const val MAX_TIME_SLICE_MS = 8L * 60L * 1000L // 8 Minuten
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val db = AppDatabase.getInstance(context)
        val userDb = UserDatabase.getInstance(context)
        val collectionRepository = CollectionRepository(userDb)
        val syncRepository = SyncRepository(db, context)
        val priceRepository = PriceRepository(userDb, collectionRepository, db)
        val preferencesRepository = AppPreferencesRepository(context)
        val checkpointDao: SyncCheckpointDao = db.syncCheckpointDao()

        val startTime = System.currentTimeMillis()

        // Try the bundled starter catalog before requiring network access so the Pokédex
        // can be populated on first launch in airplane mode. Missing assets are non-fatal.
        val startCatalogResult = syncRepository.importStartCatalogIfNeeded()
        AppLogger.log("ResumableCatalogWorker", "Start-Katalog: $startCatalogResult")

        if (!syncRepository.hasNetworkConnection()) {
            AppLogger.log("ResumableCatalogWorker", "Keine Netzwerkverbindung. Warten auf Verbindung.")
            setProgress(workDataOf(
                KEY_PROGRESS_MESSAGE to applicationContext.getString(R.string.ui_sync_waiting_network),
                KEY_PROGRESS_PERCENT to 0,
                KEY_HAS_FAILURES to false
            ))
            return@withContext Result.retry()
        }

        val allCheckpoints = checkpointDao.getAllCheckpointsOnce()
        val latestRunId = allCheckpoints.maxOfOrNull { it.runId } ?: 0L
        val currentRunId = if (SyncCheckpointPolicy.startsNewRun(allCheckpoints, startTime)) {
            val newRunId = System.currentTimeMillis()
            checkpointDao.clearOldRunCheckpoints(newRunId)
            AppLogger.log("ResumableCatalogWorker", "Neuer Sync-Lauf gestartet (runId: $newRunId)")
            newRunId
        } else {
            AppLogger.log("ResumableCatalogWorker", "Bestehender Sync-Lauf fortgesetzt (runId: $latestRunId)")
            latestRunId
        }

        val units = listOf("snapshot", "names", "catalog", "prices")
        var completedCount = 0
        var hasFailures = false

        for ((index, unit) in units.withIndex()) {
            if (isStopped || (System.currentTimeMillis() - startTime >= MAX_TIME_SLICE_MS)) {
                AppLogger.log("ResumableCatalogWorker", "Zeitscheibe (8 Min) abgelaufen oder Worker gestoppt. Folgelauf geplant.")
                scheduleContinuation(context, initialDelaySeconds = 30)
                return@withContext Result.success()
            }

            val existingCp = checkpointDao.getCheckpoint(unit)
            if (SyncCheckpointPolicy.shouldSkip(existingCp, currentRunId)) {
                AppLogger.log("ResumableCatalogWorker", "übersprungen: Einheit $unit im Lauf $currentRunId bereits abgeschlossen")
                completedCount++
                continue
            }

            val attempts = if (existingCp != null && existingCp.runId == currentRunId) existingCp.attempts else 0
            val percent = SyncCheckpointPolicy.progressPercent(index, units.size)

            setProgress(workDataOf(
                KEY_PROGRESS_MESSAGE to applicationContext.getString(
                    R.string.ui_sync_unit_progress,
                    unitLabel(unit),
                    index + 1,
                    units.size
                ),
                KEY_PROGRESS_PERCENT to percent,
                KEY_HAS_FAILURES to false,
                KEY_STARTED_AT to startTime
            ))

            val unitSuccess = try {
                executeUnit(unit, db, userDb, syncRepository, priceRepository, collectionRepository, preferencesRepository)
            } catch (e: Exception) {
                AppLogger.log("ResumableCatalogWorker", "Fehler in Einheit $unit: ${e.message}")
                false
            }

            if (unitSuccess) {
                checkpointDao.saveCheckpoint(
                    SyncCheckpointEntity(
                        unit = unit,
                        runId = currentRunId,
                        state = "DONE",
                        attempts = attempts + 1,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                completedCount++
            } else {
                hasFailures = true
                checkpointDao.saveCheckpoint(
                    SyncCheckpointEntity(
                        unit = unit,
                        runId = currentRunId,
                        state = "FAILED",
                        attempts = attempts + 1,
                        updatedAt = System.currentTimeMillis(),
                        error = "Fehler bei Ausführung von $unit"
                    )
                )
            }
        }

        if (hasFailures) {
            val runCheckpoints = checkpointDao.getCheckpointsForRun(currentRunId)
            if (SyncCheckpointPolicy.shouldScheduleRetry(runCheckpoints)) {
                scheduleContinuation(context, initialDelaySeconds = 30)
                setProgress(workDataOf(
                    KEY_PROGRESS_MESSAGE to applicationContext.getString(R.string.ui_sync_partial_failure),
                    KEY_PROGRESS_PERCENT to SyncCheckpointPolicy.progressPercent(completedCount, units.size),
                    KEY_HAS_FAILURES to true,
                    KEY_STARTED_AT to startTime,
                    KEY_FINISHED_AT to System.currentTimeMillis()
                ))
            } else {
                setProgress(workDataOf(
                    KEY_PROGRESS_MESSAGE to applicationContext.getString(R.string.ui_sync_failed),
                    KEY_PROGRESS_PERCENT to 100,
                    KEY_HAS_FAILURES to true,
                    KEY_STARTED_AT to startTime,
                    KEY_FINISHED_AT to System.currentTimeMillis()
                ))
            }
        } else {
            setProgress(workDataOf(
                KEY_PROGRESS_MESSAGE to applicationContext.getString(R.string.ui_main_sync_title),
                KEY_PROGRESS_PERCENT to 100,
                KEY_HAS_FAILURES to false,
                KEY_STARTED_AT to startTime,
                KEY_FINISHED_AT to System.currentTimeMillis()
            ))
        }

        Result.success()
    }

    private fun unitLabel(unit: String): String = when (unit) {
        "snapshot" -> applicationContext.getString(R.string.ui_sync_unit_snapshot)
        "names" -> applicationContext.getString(R.string.ui_sync_unit_names)
        "catalog" -> applicationContext.getString(R.string.ui_sync_unit_catalog)
        "prices" -> applicationContext.getString(R.string.ui_sync_unit_prices)
        else -> unit
    }

    private suspend fun executeUnit(
        unit: String,
        db: AppDatabase,
        userDb: UserDatabase,
        syncRepository: SyncRepository,
        priceRepository: PriceRepository,
        collectionRepository: CollectionRepository,
        preferencesRepository: AppPreferencesRepository
    ): Boolean {
        return when {
            unit == "snapshot" -> {
                val userCards = collectionRepository.getAllUserCardsOnce()
                val wishlist = collectionRepository.getWishlistOnce()
                val protectedCardIds = (userCards.map { it.cardId } + wishlist.map { it.cardId }).toSet()
                val protectedPokemonIds = userCards.map { it.pokemonId }.toSet()
                syncRepository.importRemoteCatalog(
                    protectedCardIds = protectedCardIds,
                    protectedPokemonIds = protectedPokemonIds
                ) { message, percent ->
                    setProgress(workDataOf(KEY_PROGRESS_MESSAGE to message, KEY_PROGRESS_PERCENT to percent))
                }
            }
            unit == "names" -> syncRepository.ensurePokemonNamesLoaded()
            unit == "catalog" -> {
                // Local starter-catalog fallback for first launch/offline. The remote
                // manifest import is the first unit and never replaces user data.
                when (val result = syncRepository.importStartCatalogIfNeeded()) {
                    is CatalogImportResult.Success,
                    CatalogImportResult.AlreadyUpToDate -> true
                    is CatalogImportResult.RequiresAppUpdate -> true
                    is CatalogImportResult.Error -> false
                }
            }
            unit == "prices" -> {
                priceRepository.recordPricePoints()
                val history = priceRepository.getAllHistoryOnce()
                val userCards = collectionRepository.getAllUserCardsOnce()
                val wishlist = collectionRepository.getWishlistOnce()
                val cardIds = (userCards.map { it.cardId } + wishlist.map { it.cardId }).distinct()
                val cardIdSet = cardIds.toSet()
                val cardEntities = cardIds.chunked(500).flatMap { batch ->
                    db.cardDao().getCardsByIds(batch)
                }
                val cardNames = cardEntities.associate { it.id to it.name }
                val relevantHistory = history.filter { it.cardId in cardIdSet }

                val alertsEnabled = preferencesRepository.priceAlertsEnabledFlow.first()
                val threshold = preferencesRepository.priceAlertThresholdPercentFlow.first()
                val windowDays = preferencesRepository.priceAlertWindowDaysFlow.first()
                val lastAlertTimestamp = preferencesRepository.priceAlertLastTimestampFlow.first()
                val didNotify = PriceAlertManager.checkAndNotifyPriceChange(
                    context = applicationContext,
                    history = relevantHistory,
                    cardNames = cardNames,
                    settings = PriceAlertSettings(
                        enabled = alertsEnabled,
                        thresholdPercent = threshold.toDouble(),
                        windowDays = windowDays,
                        lastAlertTimestamp = lastAlertTimestamp
                    )
                )
                if (didNotify) {
                    preferencesRepository.setPriceAlertLastTimestamp(System.currentTimeMillis())
                }
                true
            }
            else -> true
        }
    }

    @Suppress("SameParameterValue")
    private fun scheduleContinuation(context: Context, initialDelaySeconds: Long = 30L) {
        val request = OneTimeWorkRequestBuilder<ResumableCatalogWorker>()
            .addTag(SYNC_TAG)
            .setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CONTINUATION_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
