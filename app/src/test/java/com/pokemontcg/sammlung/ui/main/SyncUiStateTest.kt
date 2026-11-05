package com.pokemontcg.sammlung.ui.main

import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncUiStateTest {

    @Test
    fun partialFailureRequiresSuccessfulWorkWithFailureFlag() {
        assertTrue(isPartialSyncFailure(WorkInfo.State.SUCCEEDED, hasFailures = true))
        assertFalse(isPartialSyncFailure(WorkInfo.State.SUCCEEDED, hasFailures = false))
        assertFalse(isPartialSyncFailure(WorkInfo.State.FAILED, hasFailures = true))
        assertFalse(isPartialSyncFailure(WorkInfo.State.RUNNING, hasFailures = true))
    }

    @Test
    fun progressPercentAcceptsOnlyValidWorkManagerValues() {
        assertNull(normalizeProgressPercent(-1))
        assertNull(normalizeProgressPercent(101))
        assertEquals(0, normalizeProgressPercent(0))
        assertEquals(50, normalizeProgressPercent(50))
        assertEquals(100, normalizeProgressPercent(100))
    }

    @Test
    fun exactDownloadPercentUsesOnlyRealByteTotals() {
        assertEquals(0, exactDownloadPercent(0L, 100L))
        assertEquals(50, exactDownloadPercent(50L, 100L))
        assertEquals(100, exactDownloadPercent(150L, 100L))
        assertNull(exactDownloadPercent(null, 100L))
        assertNull(exactDownloadPercent(50L, null))
        assertNull(exactDownloadPercent(50L, 0L))
    }
}
