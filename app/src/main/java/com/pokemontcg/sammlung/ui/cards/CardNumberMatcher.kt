package com.pokemontcg.sammlung.ui.cards

/**
 * Maps OCR-confusable letters to the digits they usually stand for inside card numbers.
 * Only applied to number-shaped OCR fragments, never to card names.
 */
internal val OCR_DIGIT_LOOKALIKES = mapOf(
    'O' to '0', 'o' to '0', 'Q' to '0',
    'I' to '1', 'l' to '1', 'i' to '1',
    'Z' to '2', 'z' to '2',
    'S' to '5', 's' to '5',
    'G' to '6',
    'B' to '8',
    'g' to '9', 'q' to '9'
)

/** Characters an OCR pass may produce where a digit was printed. */
private const val DIGIT_LIKE = "0-9OoQqIlizZSsGgBb"

/** Normalizes OCR lookalikes to digits, e.g. "12/1O2" -> "12/102". */
internal fun normalizeOcrDigits(value: String): String = buildString {
    for (c in value) append(OCR_DIGIT_LOOKALIKES[c] ?: c)
}

/**
 * Extracts common international and Asian/Pokémon promo card-number formats from OCR text.
 * OCR lookalike letters inside number positions are normalized to digits, but a candidate
 * only counts when it contains at least one real digit — otherwise words like "SVI" (set
 * name) or "I/O" would turn into fake numbers.
 */
internal fun extractCardNumber(text: String): String? {
    // Prefer explicit promo/set prefixes so e.g. "SV-P 001" retains its set identity. The
    // prefix itself must stay untouched — only the digit tail gets lookalike normalization
    // (otherwise "SWSH" would mutate into "5W5H"). A tail of a single lookalike letter is
    // rejected: set names like "SVI" would otherwise turn into "SV1".
    val prefixed = Regex(
        "(?i)\\b((?:SWSH-P|HGSS-P|SV-P|SM-P|XY-P|BW-P|DP-P|S-P|SVP|SWSH|HGSS|PROMO|SV|TG|PR|SM|XY|BW|DP|EX))(\\s*0*[$DIGIT_LIKE]{1,4})\\b"
    ).find(text)
    if (prefixed != null) {
        val digitTail = prefixed.groupValues[2].filterNot { it.isWhitespace() }
        if (containsRealDigit(digitTail) || digitTail.length >= 2) {
            return prefixed.groupValues[1].uppercase() + normalizeOcrDigits(digitTail).uppercase()
        }
    }

    // Fraction-style numbering is common across modern international sets. OCR often reads
    // digits as letters ("12/1O2"), so lookalikes are accepted and normalized afterwards.
    val fraction = Regex("(?i)\\b([$DIGIT_LIKE]{1,3}\\s*/\\s*[$DIGIT_LIKE]{1,3})\\b").find(text)
    if (fraction != null) {
        val compact = normalizeOcrDigits(fraction.value.filterNot { it.isWhitespace() })
        if (containsRealDigit(fraction.value) && isPlausibleFraction(compact)) {
            return compact
        }
    }

    return null
}

private fun containsRealDigit(value: String): Boolean = value.any { it.isDigit() }

/** Card numbers are "n/m" with 0 < n <= m; filters noise like "I/O" normalized to "1/0". */
private fun isPlausibleFraction(normalized: String): Boolean {
    val parts = normalized.split('/', limit = 2)
    if (parts.size != 2) return false
    val numerator = parts[0].toIntOrNull() ?: return false
    val denominator = parts[1].toIntOrNull() ?: return false
    return denominator > 0 && numerator in 1..denominator
}


/** Extracts useful card/set-name clues when OCR cannot read a printed card number. */
internal fun extractOcrSearchTokens(text: String, limit: Int = 4): List<String> {
    if (limit <= 0) return emptyList()
    val ignored = setOf(
        "POKEMON", "POKÉMON", "BASIC", "STAGE", "EVOLVE", "HP", "WEAKNESS",
        "RESISTANCE", "RETREAT", "ABILITY", "ATTACK", "TRAINER", "ENERGY",
        "ILLUSTRATOR", "RULE", "CARD", "PROMO", "NUMBER", "SWITCH", "DRAW",
        "TRADING", "GAME", "COLLECTION", "OFFICIAL", "COPYRIGHT"
    )
    return Regex("[\\p{L}\\p{N}]{4,}")
        .findAll(text.uppercase())
        .map { it.value }
        .filterNot { it in ignored }
        .distinct()
        .take(limit)
        .toList()
}
