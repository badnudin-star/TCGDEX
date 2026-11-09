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
        "Crown" to "Ultra Rare",
        // Localized rarity labels from the TCGdex API (de/fr/es/it/pt), mapped to the
        // canonical English GROUP (generated from meta/translations in tcgdex/cards-database,
        // MIT, resolved through the English key). They join the English groups so the chip
        // row never shows language variants; the catalog builder normalizes new imports
        // to the same canonical keys.
        "ACE SPEC Raro" to "Ultra Rare",
        "ASS-KLASSE" to "Ultra Rare",
        "Allenatore d'arte completa" to "Holo Rare",
        "Arte Completa de Treinador" to "Holo Rare",
        "Atemberaubend" to "Holo Rare",
        "Brilhante Ultra Rara" to "Holo Rare",
        "Chromatique ultra rare" to "Holo Rare",
        "Colección Clásica" to "Rare",
        "Coleção Clásica" to "Rare",
        "Collection Classique" to "Rare",
        "Collezione Classica" to "Rare",
        "Commune" to "Common",
        "Comum" to "Common",
        "Comune" to "Common",
        "Común" to "Common",
        "Corona" to "Ultra Rare",
        "Couronne" to "Ultra Rare",
        "Cuatro Diamantes" to "Holo Rare",
        "Deux Chromatique" to "Holo Rare",
        "Deux Chromatiques" to "Holo Rare",
        "Deux Diamants" to "Uncommon",
        "Deux Étoiles" to "Holo Rare",
        "Doppelselten" to "Holo Rare",
        "Dos Diamantes" to "Uncommon",
        "Dos Estrellas" to "Holo Rare",
        "Dresseur Full Art" to "Holo Rare",
        "Entrenador de arte completo" to "Holo Rare",
        "HIGH-TECH rare" to "Ultra Rare",
        "Hiper rara" to "Ultra Rare",
        "Holo Rara" to "Holo Rare",
        "Holo Rara V" to "Holo Rare",
        "Holo Rara VMAX" to "Holo Rare",
        "Holo Rara VSTAR" to "Holo Rare",
        "Holografisch Selten" to "Holo Rare",
        "Holografisch Selten V" to "Holo Rare",
        "Holografisch Selten VMAX" to "Holo Rare",
        "Holografisch Selten VSTAR" to "Holo Rare",
        "Hyperselten" to "Ultra Rare",
        "Häufig" to "Common",
        "Illustration spéciale rare" to "Illustration Rare",
        "Ilustração Rara" to "Illustration Rare",
        "Ilustração Rara Especial" to "Illustration Rare",
        "Incomum" to "Uncommon",
        "Increíbles" to "Holo Rare",
        "Keine" to "None",
        "Klassische Kollektion" to "Rare",
        "LEGENDE" to "Rare",
        "LEGGENDA" to "Rare",
        "LEYENDA" to "Rare",
        "LÉGENDE" to "Rare",
        "Magnifique" to "Holo Rare",
        "Magnifique rare" to "Ultra Rare",
        "Mega Hiper Rara" to "Holo Rare",
        "Mega Hiper Raro" to "Holo Rare",
        "Mega Hyper Selten" to "Holo Rare",
        "Mega Iper Raro" to "Holo Rare",
        "Méga Hyper Rare" to "Holo Rare",
        "Nessuna" to "None",
        "Ninguno" to "None",
        "Non comune" to "Uncommon",
        "Olografica Rara" to "Holo Rare",
        "Olografica Rara V" to "Holo Rare",
        "Olografica Rara VMAX" to "Holo Rare",
        "Olografica Rara VSTAR" to "Holo Rare",
        "Peu Commune" to "Uncommon",
        "Policrome" to "Holo Rare",
        "Quatre Diamant" to "Holo Rare",
        "Quatre Diamants" to "Holo Rare",
        "Radieux Rare" to "Holo Rare",
        "Rara" to "Rare",
        "Rara AS TÁCTICO" to "Ultra Rare",
        "Rara Bianco e Nero" to "Rare",
        "Rara Blanca y Negra" to "Rare",
        "Rara Doble" to "Holo Rare",
        "Rara Dupla" to "Holo Rare",
        "Rara Holo" to "Holo Rare",
        "Rara Holo LV.X" to "Holo Rare",
        "Rara Holo V" to "Holo Rare",
        "Rara Holo VMAX" to "Holo Rare",
        "Rara Holo VSTAR" to "Holo Rare",
        "Rara Híper" to "Ultra Rare",
        "Rara Ilustración" to "Illustration Rare",
        "Rara Ilustración Especial" to "Illustration Rare",
        "Rara Lucente" to "Holo Rare",
        "Rara Olografica" to "Holo Rare",
        "Rara Olografica LV.X" to "Holo Rare",
        "Rara Preto e Branco" to "Rare",
        "Rara Prime" to "Rare",
        "Rara Primigenia" to "Rare",
        "Rara Radiante" to "Holo Rare",
        "Rara Secreta" to "Ultra Rare",
        "Rara Ultra Variocolor" to "Holo Rare",
        "Rara doppia" to "Holo Rare",
        "Rara illustrazione" to "Illustration Rare",
        "Rara illustrazione speciale" to "Illustration Rare",
        "Rara iper" to "Ultra Rare",
        "Raras Incríveis" to "Holo Rare",
        "Rare Noir Blanc" to "Rare",
        "Rare Prime" to "Rare",
        "Rare Secreta" to "Ultra Rare",
        "Sans Rareté" to "None",
        "Schwarz-Weiß Selten" to "Rare",
        "Segreto rara" to "Ultra Rare",
        "Selten" to "Rare",
        "Selten, Holografisch" to "Holo Rare",
        "Selten, Holografisch LV.X" to "Holo Rare",
        "Selten, Illustration" to "Illustration Rare",
        "Selten, Strahlend" to "Holo Rare",
        "Selten, besondere Illustration" to "Illustration Rare",
        "Shiny rara" to "Holo Rare",
        "Shiny rara V" to "Holo Rare",
        "Shiny rara VMAX" to "Holo Rare",
        "Super rara" to "Rare",
        "Tres Diamantes" to "Holo Rare",
        "Tres Estrellas" to "Holo Rare",
        "Trois Chromatiques" to "Three Shiny",
        "Trois Diamant" to "Holo Rare",
        "Trois Diamants" to "Holo Rare",
        "Trois Étoiles" to "Holo Rare",
        "Ultra Rara" to "Ultra Rare",
        "Ultra Selten" to "Ultra Rare",
        "Ultrarara" to "Ultra Rare",
        "Un Chromatique" to "Holo Rare",
        "Un Diamant" to "Uncommon",
        "Un Diamante" to "Uncommon",
        "Una Estrella" to "Holo Rare",
        "Une Diamant" to "Uncommon",
        "Une Étoile" to "Holo Rare",
        "Ungewöhnlich" to "Uncommon",
        "Versteckt Selten" to "Ultra Rare",
        "Vollkunsttrainer" to "Holo Rare",
        "deux Diamant" to "Uncommon",
        "deux Diamants" to "Uncommon",
        "deux Étoiles" to "Holo Rare",
        "rara ASSO TATTICO" to "Ultra Rare",
        "selten, Primus" to "Rare",
        "ultrarara cromatica" to "Holo Rare",
        "ultraselten, Schillernd" to "Holo Rare",
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
