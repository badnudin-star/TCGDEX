package com.pokemontcg.sammlung.ui.sets

/** Maximum characters shown on a generated set badge. */
private const val BADGE_MAX_LABEL_LENGTH = 4

/** Maximum characters used from the set name when no official abbreviation exists. */
private const val BADGE_FALLBACK_NAME_LENGTH = 3

/**
 * Deterministic label for the generated set badge: the official set abbreviation when the
 * catalog provides one (e.g. "SVI" for Scarlet & Violet Base), otherwise a short uppercase
 * fragment of the set name ("KAR" for "Karmesin & Purpur").
 */
fun setBadgeLabel(abbreviation: String?, setName: String?): String {
    val abbrev = abbreviation?.trim().orEmpty()
    if (abbrev.isNotEmpty()) {
        return abbrev.take(BADGE_MAX_LABEL_LENGTH)
    }
    val name = setName?.trim().orEmpty()
    if (name.isEmpty()) {
        return "?"
    }
    val firstWord = name.split(' ', '-', '&').firstOrNull { it.isNotBlank() } ?: name
    return firstWord.take(BADGE_FALLBACK_NAME_LENGTH).uppercase()
}

/**
 * Stable color slot for a set badge: hashing the set id keeps every set on the same palette
 * index across recompositions and screens, without storing an extra color in the catalog.
 */
fun setBadgeColorIndex(seed: String?, paletteSize: Int): Int {
    if (paletteSize <= 0) {
        return 0
    }
    val text = seed.orEmpty().ifEmpty { "?" }
    val hash = text.fold(0) { acc, char -> acc * 31 + char.code }
    return (hash and Int.MAX_VALUE) % paletteSize
}

/**
 * Display label for a set with its official abbreviation appended, e.g.
 * "Karmesin & Purpur (SVI)". Used next to cards in the collection, wishlist and scanner
 * results so the set is identifiable even when its images are still loading or missing.
 */
fun setLabelWithAbbreviation(setName: String?, setAbbreviation: String?): String {
    val name = setName?.trim().orEmpty()
    val abbreviation = setAbbreviation?.trim().orEmpty()
    return when {
        name.isEmpty() -> abbreviation
        abbreviation.isEmpty() -> name
        else -> "$name ($abbreviation)"
    }
}
