package com.pokemontcg.sammlung.worker

import com.pokemontcg.sammlung.data.local.entities.SyncCheckpointEntity

internal object SyncCheckpointPolicy {

    const val STALE_RUN_MS = 24L * 60L * 60L * 1000L

    /**
     * A manual sync (user pressed "update now") always starts a new run: a previous run
     * that ended with skipped-but-DONE units (e.g. a snapshot blocked by an app/schema
     * mismatch) must never pin the catalog download for the whole stale window.
     */
    fun startsNewRun(
        checkpoints: List<SyncCheckpointEntity>,
        now: Long,
        forceNewRun: Boolean = false
    ): Boolean {
        if (forceNewRun) return true
        if (checkpoints.isEmpty()) return true
        val newestUpdate = checkpoints.maxOf { it.updatedAt }
        return checkpoints.all { it.state == "DONE" } || now - newestUpdate >= STALE_RUN_MS
    }

    fun shouldSkip(checkpoint: SyncCheckpointEntity?, runId: Long): Boolean =
        checkpoint?.runId == runId && checkpoint.state == "DONE"

    fun shouldScheduleRetry(checkpoints: List<SyncCheckpointEntity>): Boolean =
        checkpoints.any { it.state == "FAILED" && it.attempts < 5 }

    fun progressPercent(completedUnits: Int, totalUnits: Int): Int =
        if (totalUnits == 0) 100 else (completedUnits * 100) / totalUnits
}
