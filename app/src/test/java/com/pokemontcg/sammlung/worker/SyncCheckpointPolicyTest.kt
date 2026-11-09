package com.pokemontcg.sammlung.worker

import com.pokemontcg.sammlung.data.local.entities.SyncCheckpointEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the 24h catalog-download blockage: a run whose snapshot unit was
 * marked DONE after a schema-mismatch answer must be re-checked immediately when the user
 * triggers a manual sync.
 */
class SyncCheckpointPolicyTest {

    private fun checkpoint(unit: String, state: String, updatedAt: Long) =
        SyncCheckpointEntity(unit = unit, runId = 1L, state = state, attempts = 1, updatedAt = updatedAt)

    @Test
    fun manualSyncAlwaysStartsANewRunEvenInsideTheStaleWindow() {
        // The reported bug: snapshot DONE (blocked by a schema mismatch answer) plus a
        // FAILED starter-catalog unit pinned the snapshot skip for 24 hours.
        val checkpoints = listOf(
            checkpoint("snapshot", "DONE", 1_000L),
            checkpoint("catalog", "FAILED", 1_000L),
            checkpoint("names", "DONE", 1_000L),
            checkpoint("prices", "DONE", 1_000L)
        )
        val now = 1_000L + 60 * 60 * 1000L // one hour later, well inside the 24h window
        assertFalse(SyncCheckpointPolicy.startsNewRun(checkpoints, now))
        assertTrue(SyncCheckpointPolicy.startsNewRun(checkpoints, now, forceNewRun = true))
    }

    @Test
    fun continuationWithoutForceKeepsResumingInsideTheWindow() {
        val checkpoints = listOf(checkpoint("snapshot", "DONE", 1_000L), checkpoint("catalog", "FAILED", 1_000L))
        assertFalse(SyncCheckpointPolicy.startsNewRun(checkpoints, 2_000L))
    }

    @Test
    fun allDoneStartsANewRunWithoutForce() {
        val checkpoints = listOf(
            checkpoint("snapshot", "DONE", 1_000L),
            checkpoint("catalog", "DONE", 1_000L),
            checkpoint("names", "DONE", 1_000L),
            checkpoint("prices", "DONE", 1_000L)
        )
        assertTrue(SyncCheckpointPolicy.startsNewRun(checkpoints, 2_000L))
    }

    @Test
    fun staleRunsStartANewRunAfter24Hours() {
        val checkpoints = listOf(checkpoint("snapshot", "DONE", 1_000L), checkpoint("catalog", "FAILED", 1_000L))
        val after24h = 1_000L + SyncCheckpointPolicy.STALE_RUN_MS
        assertTrue(SyncCheckpointPolicy.startsNewRun(checkpoints, after24h))
    }

    @Test
    fun emptyCheckpointsAlwaysStartANewRun() {
        assertTrue(SyncCheckpointPolicy.startsNewRun(emptyList(), 0L))
        assertTrue(SyncCheckpointPolicy.startsNewRun(emptyList(), 0L, forceNewRun = false))
    }
}
