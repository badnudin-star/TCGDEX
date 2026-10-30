package com.pokemontcg.sammlung.ui.cards

/** Minimal card metadata used to rank OCR candidates without making an automatic selection. */
data class CardMatchCandidate(
    val id: String,
    val name: String,
    val setName: String,
    val number: String
)

/**
 * Ranks catalog candidates using the scanned number first, then card-name and set-name clues.
 * This is only a presentation hint: callers must still ask the user to confirm a candidate.
 */
internal fun rankCardMatches(
    candidates: List<CardMatchCandidate>,
    detectedNumber: String?,
    rawOcrText: String
): List<CardMatchCandidate> {
    if (candidates.size < 2) return candidates

    val ocrTokens = tokens(rawOcrText)
    val normalizedDetectedNumber = detectedNumber?.let(::normalizeNumber)

    fun tokenMatches(value: String): Int =
        tokens(value).count { token -> token.length >= 3 && token in ocrTokens }

    return candidates.sortedWith(
        compareByDescending<CardMatchCandidate> {
            normalizedDetectedNumber != null && normalizeNumber(it.number) == normalizedDetectedNumber
        }.thenByDescending {
            tokenMatches(it.name)
        }.thenByDescending {
            tokenMatches(it.setName)
        }.thenBy {
            it.id
        }
    )
}

private fun tokens(value: String): Set<String> =
    Regex("[\\p{L}\\p{N}]{3,}").findAll(value.uppercase())
        .map { it.value }
        .toSet()

private fun normalizeNumber(value: String): String =
    value.filterNot { it.isWhitespace() }.uppercase()
