package com.pokemontcg.sammlung.worker

import com.pokemontcg.sammlung.data.local.entities.SyncCheckpointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCheckpointTest {

    private class FakeCheckpointDao {
        val checkpoints = mutableMapOf<String, SyncCheckpointEntity>()

        fun getCheckpoint(unit: String): SyncCheckpointEntity? = checkpoints[unit]

        fun saveCheckpoint(checkpoint: SyncCheckpointEntity) {
            checkpoints[checkpoint.unit] = checkpoint
        }

        fun clearOldRunCheckpoints(currentRunId: Long) {
            checkpoints.entries.removeIf { it.value.runId != currentRunId }
        }
    }

    @Test
    fun syncUnitsRunCatalogBeforeNames() {
        assertEquals(
            listOf("snapshot", "catalog", "names", "prices"),
            ResumableCatalogWorker.SYNC_UNITS
        )
    }

    @Test
    fun testResumeFlowSkipsCompletedUnits() {
        val dao = FakeCheckpointDao()
        val runId = 1000L
        val units = ResumableCatalogWorker.SYNC_UNITS

        // First run completes the snapshot and catalog units.
        dao.saveCheckpoint(SyncCheckpointEntity("snapshot", runId, "DONE", 1, 1000L))
        dao.saveCheckpoint(SyncCheckpointEntity("catalog", runId, "DONE", 1, 1000L))

        val executedUnits = mutableListOf<String>()

        for (unit in units) {
            val cp = dao.getCheckpoint(unit)
            if (SyncCheckpointPolicy.shouldSkip(cp, runId)) {
                // Skip
                continue
            }
            executedUnits.add(unit)
            dao.saveCheckpoint(SyncCheckpointEntity(unit, runId, "DONE", 1, 1005L))
        }

        // Executed units should only be the remaining coarse units ("names", "prices").
        assertEquals(2, executedUnits.size)
        assertFalse(executedUnits.contains("snapshot"))
        assertFalse(executedUnits.contains("catalog"))
        assertTrue(executedUnits.contains("names"))
        assertTrue(executedUnits.contains("prices"))
    }

    @Test
    fun testNewRunClearsOldCheckpointsWhenStale() {
        val dao = FakeCheckpointDao()
        val oldRunId = 500L
        dao.saveCheckpoint(SyncCheckpointEntity("snapshot", oldRunId, "DONE", 1, 1000L))

        val newRunId = 2000L
        dao.clearOldRunCheckpoints(newRunId)

        assertEquals(0, dao.checkpoints.size)
    }

    @Test
    fun testFailureAtThirdUnitResumesThereAndSchedulesRetry() {
        val runId = 1000L
        val units = ResumableCatalogWorker.SYNC_UNITS
        val checkpoints = mutableMapOf<String, SyncCheckpointEntity>()
        val firstRunCalls = mutableListOf<String>()

        units.forEachIndexed { index, unit ->
            if (SyncCheckpointPolicy.shouldSkip(checkpoints[unit], runId)) return@forEachIndexed
            firstRunCalls += unit
            if (index == 2) {
                checkpoints[unit] = SyncCheckpointEntity(unit, runId, "FAILED", attempts = 1, updatedAt = 100L)
                return@forEachIndexed
            }
            checkpoints[unit] = SyncCheckpointEntity(unit, runId, "DONE", attempts = 1, updatedAt = 100L)
        }

        assertEquals(listOf("snapshot", "catalog", "names", "prices"), firstRunCalls)
        assertTrue(SyncCheckpointPolicy.shouldScheduleRetry(checkpoints.values.toList()))

        val resumedCalls = mutableListOf<String>()
        units.forEach { unit ->
            if (!SyncCheckpointPolicy.shouldSkip(checkpoints[unit], runId)) resumedCalls += unit
        }
        assertEquals(listOf("names"), resumedCalls)
    }

    @Test
    fun testProgressPercentCoversEmptyPartialAndCompleteRuns() {
        assertEquals(100, SyncCheckpointPolicy.progressPercent(0, 0))
        assertEquals(0, SyncCheckpointPolicy.progressPercent(0, 4))
        assertEquals(50, SyncCheckpointPolicy.progressPercent(2, 4))
        assertEquals(100, SyncCheckpointPolicy.progressPercent(4, 4))
    }

    @Test
    fun testTimeSliceEnforcement() {
        val startTime = System.currentTimeMillis()
        val maxSlice = ResumableCatalogWorker.MAX_TIME_SLICE_MS

        var simulatedTime = startTime + (9 * 60 * 1000L) // 9 minutes later
        val isExceeded = (simulatedTime - startTime) >= maxSlice

        assertTrue(isExceeded)
    }
}
