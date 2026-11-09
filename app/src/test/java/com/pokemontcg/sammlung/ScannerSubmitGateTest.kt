package com.pokemontcg.sammlung

import com.pokemontcg.sammlung.ui.cards.ScannerSubmitGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerSubmitGateTest {

    @Test
    fun readableNumberNeedsTwoConfirmations() {
        val gate = ScannerSubmitGate()
        assertFalse(gate.submitNumber("025/198"))
        assertTrue(gate.submitNumber("025/198"))
        // Already submitted: must not fire again while the camera keeps seeing it.
        assertFalse(gate.submitNumber("025/198"))
    }

    @Test
    fun nameTokensNeedThreeConfirmations() {
        val gate = ScannerSubmitGate()
        val tokens = listOf("PIKACHU", "VIVID", "VOLTAGE")
        assertFalse(gate.submitNameTokens(tokens))
        assertFalse(gate.submitNameTokens(tokens))
        assertTrue(gate.submitNameTokens(tokens))
        assertFalse(gate.submitNameTokens(tokens))
    }

    @Test
    fun changingFingerprintRestartsTheConfirmationStreak() {
        val gate = ScannerSubmitGate()
        assertFalse(gate.submitNumber("025/198"))
        // Different number resets the count, so the new one needs its own two frames.
        assertFalse(gate.submitNumber("026/198"))
        assertTrue(gate.submitNumber("026/198"))
    }

    @Test
    fun returningToAnAlreadySubmittedFingerprintDoesNotRefire() {
        val gate = ScannerSubmitGate()
        assertFalse(gate.submitNumber("025/198"))
        assertTrue(gate.submitNumber("025/198"))
        // Scan another card in between …
        assertFalse(gate.submitNumber("100/203"))
        assertTrue(gate.submitNumber("100/203"))
        // … then point back at the first card: no duplicate search for it.
        assertFalse(gate.submitNumber("025/198"))
    }

    @Test
    fun unusableInputResetsWithoutSubmitting() {
        val gate = ScannerSubmitGate()
        assertFalse(gate.submitNumber("025/198"))
        assertFalse(gate.submitNameTokens(emptyList()))
        // The streak was reset, so the number needs two fresh confirmations.
        assertFalse(gate.submitNumber("025/198"))
        assertTrue(gate.submitNumber("025/198"))
    }

    @Test
    fun blankNumbersAndShortNameFingerprintsNeverSubmit() {
        val gate = ScannerSubmitGate()
        assertFalse(gate.submitNumber("   "))
        // Shorter than the minimum fingerprint length of eight characters.
        assertFalse(gate.submitNameTokens(listOf("AB")))
        assertFalse(gate.submitNameTokens(listOf("PIKA")))
    }

    @Test
    fun shortCjkNameFingerprintsSubmit() {
        // Two Chinese characters already identify a scanned card; the Latin minimum
        // of eight characters would never let a Chinese name submit.
        val gate = ScannerSubmitGate(nameConfirmations = 2)
        assertFalse(gate.submitNameTokens(listOf("皮卡丘")))
        assertTrue(gate.submitNameTokens(listOf("皮卡丘")))
        // ...but a single character is still too little signal.
        val other = ScannerSubmitGate(nameConfirmations = 2)
        assertFalse(other.submitNameTokens(listOf("梦")))
    }
}
