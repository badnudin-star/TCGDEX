package com.pokemontcg.sammlung.ui.cards

import com.pokemontcg.sammlung.data.local.entities.CardEntity

/**
 * Hybrid resolver: OCR first, image-hash fallback (Variante 1).
 *
 * The camera delivers both text (ML Kit) and optionally a perceptual hash
 * (dHash 16-char hex). Text is preferred: it carries number+name+language.
 * Only when it yields no DB hit does the image hash run, keeping the DB
 * scan bounded and the UI deterministic.
 */
internal suspend fun resolveScannerMatchesWithImageFallback(
    detectedNumber: String?,
    rawOcrText: String,
    imageHash: String?,
    lookupByNumber: suspend (String) -> List<CardEntity>,
    lookupByOcrText: suspend (String) -> List<CardEntity>,
    lookupByImageHash: suspend (String) -> List<CardEntity>
): List<CardEntity> {
    val ocrResult = resolveScannerMatches(detectedNumber, rawOcrText, lookupByNumber, lookupByOcrText)
    if (ocrResult.isNotEmpty()) return ocrResult
    if (!ImageHash.isValidHash(imageHash)) return emptyList()
    return lookupByImageHash(imageHash!!)
}

/**
 * Merges OCR and image results when the caller wants both (parallel mode).
 * Image candidates are appended after OCR ones, deduped, capped at 30.
 */
internal fun mergeOcrAndImageMatches(
    ocrMatches: List<CardEntity>,
    imageMatches: List<CardEntity>,
    limit: Int = 30
): List<CardEntity> {
    if (imageMatches.isEmpty()) return ocrMatches
    if (ocrMatches.isEmpty()) return imageMatches.take(limit)
    val byId = LinkedHashMap<String, CardEntity>()
    (ocrMatches + imageMatches).forEach { byId.putIfAbsent(it.id, it) }
    return byId.values.take(limit)
}
