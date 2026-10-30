package com.pokemontcg.sammlung

import com.pokemontcg.sammlung.ui.cards.isCurrentScannerRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerRequestGuardTest {
    @Test
    fun acceptsLatestRequestForSameActiveScannerEntry() {
        assertTrue(isCurrentScannerRequest("scanner", "entry-1", "entry-1", 3L, 3L))
    }

    @Test
    fun rejectsRequestAfterNavigationToAnotherRoute() {
        assertFalse(isCurrentScannerRequest("home", "entry-1", "entry-1", 1L, 1L))
    }

    @Test
    fun rejectsLateRequestFromPreviousScannerEntry() {
        assertFalse(isCurrentScannerRequest("scanner", "entry-2", "entry-1", 1L, 1L))
    }

    @Test
    fun rejectsMissingRequestEntry() {
        assertFalse(isCurrentScannerRequest("scanner", "entry-1", null, 1L, 1L))
    }

    @Test
    fun rejectsOlderRequestWhenNewerOcrLookupStarted() {
        assertFalse(isCurrentScannerRequest("scanner", "entry-1", "entry-1", 8L, 7L))
    }

    @Test
    fun rejectsZeroSequence() {
        assertFalse(isCurrentScannerRequest("scanner", "entry-1", "entry-1", 0L, 0L))
    }
}
