package com.pokemontcg.sammlung.ui.cards

/** Extracts common international and Asian/Pokémon promo card-number formats from OCR text. */
internal fun extractCardNumber(text: String): String? {
    // Prefer explicit promo/set prefixes so e.g. "SV-P 001/024" retains its set identity.
    val prefixed = Regex(
        "(?i)\\b((?:SWSH-P|HGSS-P|SV-P|SM-P|XY-P|BW-P|DP-P|S-P|SVP|SWSH|HGSS|PROMO|SV|TG|PR|SM|XY|BW|DP|EX)\\s*0*[0-9]{1,4})\\b"
    ).find(text)
    if (prefixed != null) {
        return prefixed.value.filterNot { it.isWhitespace() }.uppercase()
    }

    // Fraction-style numbering is common across modern international sets.
    val fraction = Regex("(?i)\\b([0-9]{1,3}\\s*/\\s*[0-9]{1,3})\\b").find(text)
    if (fraction != null) return fraction.value.filterNot { it.isWhitespace() }

    return null
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
