package com.pokemontcg.sammlung.ui.cards

/** Minimal card metadata used to rank OCR candidates without making an automatic selection. */
data class CardMatchCandidate(
    val id: String,
    val name: String,
    val setName: String,
    val number: String,
    /** Catalog language of the candidate row ("" when unknown); used as a ranking tie-break. */
    val language: String = ""
)

/**
 * Ranks catalog candidates using the scanned number first, then card-name and set-name clues,
 * then language plausibility. This is only a presentation hint: callers must still ask the
 * user to confirm a candidate.
 */
internal fun rankCardMatches(
    candidates: List<CardMatchCandidate>,
    detectedNumber: String?,
    rawOcrText: String
): List<CardMatchCandidate> {
    if (candidates.size < 2) return candidates

    val ocrTokens = tokens(rawOcrText)
    val normalizedDetectedNumber = detectedNumber?.let(::normalizeNumber)
    val languageClue = detectOcrLanguageClue(rawOcrText)

    fun tokenMatches(value: String): Int =
        tokens(value).count { token ->
            token.length >= 3 && (
                token in ocrTokens ||
                    // CJK names come back as one long run; substring overlap in both
                    // directions still ranks the right card highest.
                    (token.any(::isCjkChar) &&
                        ocrTokens.any { ocrToken -> ocrToken.any(::isCjkChar) &&
                            (ocrToken.contains(token) || token.contains(ocrToken)) })
                )
        }

    return candidates.sortedWith(
        compareByDescending<CardMatchCandidate> {
            normalizedDetectedNumber != null && normalizeNumber(it.number) == normalizedDetectedNumber
        }.thenByDescending {
            tokenMatches(it.name)
        }.thenByDescending {
            tokenMatches(it.setName)
        }.thenByDescending {
            // The OCR script pins Asian languages directly (kana -> ja, hangul -> ko,
            // han without kana -> zh); the same card exists in many catalog languages
            // and the script clue separates the print actually in front of the camera.
            matchesLanguageClue(it.language, languageClue)
        }.thenByDescending {
            // Latin script is ambiguous across a dozen languages: the app is German-first,
            // so German rows lead when neither names nor script can tell them apart.
            it.language == "de"
        }.thenByDescending {
            it.language == "en"
        }.thenBy {
            it.id
        }
    )
}

/** True when the candidate language matches the OCR script clue ("zh" covers zh-tw/zh-cn). */
private fun matchesLanguageClue(language: String, clue: String?): Boolean = when (clue) {
    null -> false
    "zh" -> language == "zh-tw" || language == "zh-cn"
    else -> language == clue
}

private fun tokens(value: String): Set<String> =
    Regex("[\\p{L}\\p{N}]{2,}").findAll(value.uppercase())
        .map { it.value }
        // Latin fragments stay at three characters; short CJK names are already precise.
        .filter { it.length >= 3 || it.any(::isCjkChar) }
        .toSet()

private fun normalizeNumber(value: String): String =
    value.filterNot { it.isWhitespace() }.uppercase()
