package com.pokemontcg.sammlung.data.model

/**
 * Collapses the ~47 rarity strings TCGdex uses into a small set of canonical English terms so
 * the wishlist rarity filter stays usable.
 *
 * TCGdex stores rarity per localized card record as an English string, but with spelling drift
 * ("Double rare" vs "Holo Rare V") and Japanese symbol rarities ("One Diamond", "Two Star").
 * Without collapsing, the filter chips show every variant separately.
 *
 * The exact map covers every rarity known today (extracted from the TCGdex cards database);
 * unknown future values fall through to substring rules so they still land in a sensible group
 * instead of growing the chip row again. "None" groups cards without a printed rarity.
 */
object RarityGrouper {

    /** Display order of the canonical groups in the filter chip row. */
    val CANONICAL_ORDER: List<String> = listOf(
        "None",
        "Common",
        "Uncommon",
        "Rare",
        "Holo Rare",
        "Ultra Rare",
        "Illustration Rare",
        "Promo"
    )

    private val exact: Map<String, String> = mapOf(
        // No printed rarity.
        "None" to "None",
        // Plain tiers.
        "Common" to "Common",
        "Uncommon" to "Uncommon",
        "Rare" to "Rare",
        // Regular-holo and full-art style rares all share the holo group.
        "Holo Rare" to "Holo Rare",
        "Rare Holo" to "Holo Rare",
        "Rare Holo LV.X" to "Holo Rare",
        "Holo Rare V" to "Holo Rare",
        "Holo Rare VMAX" to "Holo Rare",
        "Holo Rare VSTAR" to "Holo Rare",
        "Double rare" to "Holo Rare",
        "Triple Rare" to "Holo Rare",
        "Shiny rare" to "Holo Rare",
        "Shiny rare V" to "Holo Rare",
        "Shiny rare VMAX" to "Holo Rare",
        "Shiny Ultra Rare" to "Holo Rare",
        "Radiant Rare" to "Holo Rare",
        "Amazing Rare" to "Holo Rare",
        "Character Rare" to "Holo Rare",
        "Character Super Rare" to "Holo Rare",
        "Full Art Trainer" to "Holo Rare",
        "Mega Hyper Rare" to "Holo Rare",
        "Mega Attack Rare" to "Holo Rare",
        // Premium tiers beyond regular holo.
        "Ultra Rare" to "Ultra Rare",
        "Hyper rare" to "Ultra Rare",
        "Secret Rare" to "Ultra Rare",
        "ACE SPEC Rare" to "Ultra Rare",
        // Alternate artwork rares.
        "Illustration rare" to "Illustration Rare",
        "Special illustration rare" to "Illustration Rare",
        // Promotional prints.
        "Promo" to "Promo",
        // Exotic/exclusive one-offs without a meaningful rarity of their own.
        "Rare PRIME" to "Rare",
        "LEGEND" to "Rare",
        "Black White Rare" to "Rare",
        "RGB Rare" to "Rare",
        "Pikachu Rare" to "Rare",
        "Futuristic Rare" to "Rare",
        "Classic Collection" to "Rare",
        // Japanese symbol rarities: diamonds are the lower print runs, stars/shinies the holo
        // tier, crowns the premium tier of Japanese sets.
        "One Diamond" to "Uncommon",
        "Two Diamond" to "Uncommon",
        "Three Diamond" to "Holo Rare",
        "Four Diamond" to "Holo Rare",
        "One Star" to "Holo Rare",
        "Two Star" to "Holo Rare",
        "Three Star" to "Holo Rare",
        "One Shiny" to "Holo Rare",
        "Two Shiny" to "Holo Rare",
        "Crown" to "Ultra Rare"
    )

    /** Maps one raw catalog rarity to its canonical English group label. */
    fun canonical(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return "None"
        exact[value]?.let { return it }
        val lower = value.lowercase()
        return when {
            // "uncommon" must be tested before "common" because it contains it.
            "illustration" in lower -> "Illustration Rare"
            "holo" in lower -> "Holo Rare"
            "shiny" in lower -> "Holo Rare"
            "promo" in lower -> "Promo"
            "uncommon" in lower -> "Uncommon"
            "common" in lower -> "Common"
            "ultra" in lower || "hyper" in lower || "secret" in lower || "ace" in lower -> "Ultra Rare"
            "rare" in lower -> "Rare"
            else -> value
        }
    }

    /**
     * Expands selected canonical groups back to the raw rarity strings stored in the catalog,
     * so the SQL `IN` filter still matches the unnormalized values.
     */
    fun rawValuesFor(selectedGroups: Set<String>, availableRaw: List<String>): Set<String> =
        if (selectedGroups.isEmpty()) {
            emptySet()
        } else {
            availableRaw.filter { canonical(it) in selectedGroups }.toSet()
        }

    /** Canonical chip labels for the rarities present in the catalog, known groups first. */
    fun chipGroups(availableRaw: List<String>): List<String> {
        val present = availableRaw.map { canonical(it) }.toSet()
        val known = CANONICAL_ORDER.filter { it in present }
        val others = (present - CANONICAL_ORDER.toSet()).sorted()
        return known + others
    }
}
