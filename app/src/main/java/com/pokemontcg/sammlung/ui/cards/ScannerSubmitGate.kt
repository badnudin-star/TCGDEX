package com.pokemontcg.sammlung.ui.cards

/**
 * Aggregates consecutive OCR fingerprints from the live camera and decides when a scan
 * result is stable enough to submit to the catalog search.
 *
 * A readable card number is a strong signal and needs only two confirmations; the fuzzy
 * name-token fallback is noisier and needs three. A changed fingerprint resets the
 * counter, and a fingerprint that was already submitted is never submitted twice — the
 * user must point the camera somewhere else (or come back via a different fingerprint)
 * before the same result fires again.
 */
internal class ScannerSubmitGate(
    private val numberConfirmations: Int = 2,
    private val nameConfirmations: Int = 3,
    private val minNameFingerprintLength: Int = 8
) {
    private var lastFingerprint: String? = null
    private var lastSubmitted: String? = null
    private var confirmationCount = 0

    /** Returns true exactly once per stable card-number fingerprint. */
    @Synchronized
    fun submitNumber(cardNumber: String): Boolean {
        if (cardNumber.isBlank()) {
            reset()
            return false
        }
        return submit("number:" + cardNumber.trim().uppercase(), numberConfirmations)
    }

    /** Returns true exactly once per stable name-token fingerprint. */
    @Synchronized
    fun submitNameTokens(tokens: List<String>): Boolean {
        val fingerprint = tokens.joinToString(" ")
        if (tokens.isEmpty() || fingerprint.length < minNameFingerprintLength) {
            reset()
            return false
        }
        return submit("name:$fingerprint", nameConfirmations)
    }

    /** Drops the current confirmation streak (e.g. when no usable text was seen). */
    @Synchronized
    fun reset() {
        lastFingerprint = null
        confirmationCount = 0
    }

    private fun submit(fingerprint: String, requiredConfirmations: Int): Boolean {
        if (fingerprint == lastSubmitted) return false
        if (fingerprint == lastFingerprint) {
            confirmationCount++
        } else {
            lastFingerprint = fingerprint
            confirmationCount = 1
        }
        if (confirmationCount >= requiredConfirmations) {
            lastSubmitted = fingerprint
            return true
        }
        return false
    }
}
