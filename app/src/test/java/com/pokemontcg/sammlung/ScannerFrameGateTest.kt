package com.pokemontcg.sammlung

import com.pokemontcg.sammlung.ui.cards.ScannerFrameGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerFrameGateTest {
    @Test
    fun onlyOneFrameCanBeInFlight() {
        val gate = ScannerFrameGate()
        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())
    }

    @Test
    fun gateCanBeReacquiredAfterRelease() {
        val gate = ScannerFrameGate()
        assertTrue(gate.tryAcquire())
        gate.release()
        assertTrue(gate.tryAcquire())
    }

    @Test
    fun repeatedReleaseDoesNotLeaveGateLocked() {
        val gate = ScannerFrameGate()
        gate.release()
        gate.release()
        assertTrue(gate.tryAcquire())
    }
}
