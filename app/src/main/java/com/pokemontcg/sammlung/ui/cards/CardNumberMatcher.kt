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

/** Set prefixes recognized in printed card numbers, longest first (regex order matters). */
private val SET_PREFIXES =
    "SWSH-P|HGSS-P|SV-P|SM-P|XY-P|BW-P|DP-P|S-P|SVP|SWSH|HGSS|PROMO|SV|TG|PR|SM|XY|BW|DP|EX"

/** Canonical prefixed number, e.g. "SV-P001" -> set "SV-P" + plain number "001". */
private val PREFIXED_NUMBER = Regex("^(?:$SET_PREFIXES)(\\d{1,4})$")

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
        "(?i)\\b((?:$SET_PREFIXES))(\\s*0*[$DIGIT_LIKE]{1,4})\\b"
    ).find(text)
    if (prefixed != null) {
        val digitTail = prefixed.groupValues[2].filterNot { it.isWhitespace() }
        if (containsRealDigit(digitTail) || digitTail.length >= 2) {
            return prefixed.groupValues[1].uppercase() + normalizeOcrDigits(digitTail).uppercase()
        }
    }

    // Japanese promo prints put the set prefix BEHIND the number ("001/SV-P"). The catalog
    // stores these as set id + plain number, so the canonical prefix-first form is rebuilt.
    // This must run BEFORE the fraction branch: "001/S-P" would otherwise be read as the
    // plausible-looking fraction "001/5" (S is an OCR digit lookalike for 5).
    val reversed = Regex(
        "(?i)\\b([$DIGIT_LIKE]{1,3})\\s*/\\s*($SET_PREFIXES)\\b"
    ).find(text)
    if (reversed != null) {
        val numberPart = normalizeOcrDigits(reversed.groupValues[1].filterNot { it.isWhitespace() })
        if (containsRealDigit(numberPart)) {
            return reversed.groupValues[2].uppercase() + numberPart
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

/**
 * Splits a canonical prefixed number ("SV-P001") into the catalog set id ("SV-P") and the
 * plain card number ("001"). Asian promo sets store the prefix as set id and a plain digit
 * string as number, so the scanner search needs both parts. Returns null for fraction-style
 * ("025/198") or unprefixed numbers.
 */
internal fun splitPrefixedNumber(value: String): Pair<String, String>? {
    val match = PREFIXED_NUMBER.find(value.trim().uppercase()) ?: return null
    return match.groupValues[1] to match.groupValues[2]
}

/** Folds Latin diacritics ("ÉVOLI" -> "EVOLI") for accent-insensitive catalog searches. */
internal fun foldOcrAccents(value: String): String =
    java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .uppercase()

private fun containsRealDigit(value: String): Boolean = value.any { it.isDigit() }

/** Card numbers are "n/m" with 0 < n <= m; filters noise like "I/O" normalized to "1/0". */
private fun isPlausibleFraction(normalized: String): Boolean {
    val parts = normalized.split('/', limit = 2)
    if (parts.size != 2) return false
    val numerator = parts[0].toIntOrNull() ?: return false
    val denominator = parts[1].toIntOrNull() ?: return false
    return denominator > 0 && numerator in 1..denominator
}


/**
 * True for CJK characters (Han, Kana, Hangul): these scripts carry far more information
 * per character than Latin text, so tokens and confirmation thresholds can be shorter.
 */
internal fun isCjkChar(c: Char): Boolean =
    c.code in 0x3400..0x9FFF ||   // CJK ideographs (extension A + unified)
        c.code in 0xF900..0xFAFF || // CJK compatibility ideographs
        c.code in 0x3040..0x30FF || // Hiragana + Katakana
        c.code in 0xAC00..0xD7AF    // Hangul syllables

internal fun containsCjk(value: String): Boolean = value.any(::isCjkChar)

/**
 * Extracts useful card/set-name clues when OCR cannot read a printed card number.
 *
 * Latin tokens need four characters to be useful; CJK runs count from two (Chinese card
 * names are short, e.g. "皮卡丘"). One long CJK run — the Chinese recognizer returns whole
 * undivided lines — is split into overlapping trigrams so an embedded card name still
 * produces searchable fragments for the catalog LIKE search.
 */
internal fun extractOcrSearchTokens(text: String, limit: Int = 4): List<String> {
    if (limit <= 0) return emptyList()
    // Layout boilerplate that appears on nearly every card of the respective language:
    // burning these tokens on the 4-token search budget only starves the real name.
    val ignored = setOf(
        // English/international
        "POKEMON", "POKÉMON", "BASIC", "STAGE", "EVOLVE", "HP", "WEAKNESS",
        "RESISTANCE", "RETREAT", "ABILITY", "ATTACK", "TRAINER", "ENERGY",
        "ILLUSTRATOR", "ILLUSTRATEUR", "RULE", "CARD", "PROMO", "NUMBER", "SWITCH", "DRAW",
        "TRADING", "GAME", "COLLECTION", "OFFICIAL", "COPYRIGHT",
        // German
        "STUFE", "POKÉMON-KARTE",
        // French
        "TALENT", "ATTAQUE", "FAIBLESSE", "RETRAITE", "ÉNERGIE", "ENERGIE",
        // Spanish
        "ENERGÍA", "DEBILIDAD", "RETIRADA",
        // Italian / Portuguese
        "ENERGIA", "DEBOLEZZA", "RITIRATA", "FRAQUEZA", "RECUO", "ATAQUE",
        // Japanese / Korean / Chinese layout words
        "ポケモン", "トレーナー", "エネルギー", "グッズ",
        "포켓몬", "트레이너", "에너지",
        "寶可夢", "宝可梦", "訓練家", "训练家", "能量"
    )
    val tokens = LinkedHashSet<String>()
    Regex("[\\p{L}\\p{N}]+")
        .findAll(text.uppercase())
        .forEach { match ->
            val value = match.value
            when {
                containsCjk(value) && value.length > 6 ->
                    value.windowed(3).forEach { gram -> tokens.add(gram) }
                containsCjk(value) && value.length >= 2 -> tokens.add(value)
                value.length >= 4 -> tokens.add(value)
            }
        }
    return tokens
        .filterNot { it in ignored }
        .take(limit)
        .toList()
}
