package com.pokemontcg.sammlung.ui.cards

import com.pokemontcg.sammlung.data.local.entities.CardEntity

/**
 * Resolves scanner candidates. A readable number is tried first; if it yields no catalog
 * match, the bounded OCR name/set fallback is still attempted so a misread number does not
 * dead-end the scan.
 */
internal suspend fun resolveScannerMatches(
    detectedNumber: String?,
    rawOcrText: String,
    lookupByNumber: suspend (String) -> List<CardEntity>,
    lookupByOcrText: suspend (String) -> List<CardEntity>
): List<CardEntity> =
    if (detectedNumber.isNullOrBlank()) {
        lookupByOcrText(rawOcrText)
    } else {
        lookupByNumber(detectedNumber).ifEmpty {
            lookupByOcrText(rawOcrText)
        }
    }
