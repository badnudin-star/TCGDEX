package com.pokemontcg.sammlung.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.pokemontcg.sammlung.data.local.user.CardCategory

@Entity(tableName = "pokemon")
data class PokemonEntity(
    @PrimaryKey val id: String,
    val dexId: Int,
    val number: String,
    /** The only name shown in the interface: the official German Pokémon name. */
    val name: String,
    /** Contains localized names from all languages plus the English API slug and Pokédex number. */
    val searchName: String,
    val imageUrl: String?,
    val primaryCardImageUrl: String?,
    /** unused, kept for schema compatibility */
    @ColumnInfo(defaultValue = "'NONE'") val cardType: CardCategory = CardCategory.NONE,
    /** Last successful refresh of this Pokémon's complete card-print list. */
    val cardsUpdatedAt: Long? = null
)

@Entity(tableName = "sets")
data class SetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val series: String,
    val printedTotal: Int,
    val total: Int,
    val releaseDate: String,
    /** TCGdex logo/symbol asset URLs; absent on some older promo sets. */
    val logoUrl: String? = null,
    val symbolUrl: String? = null
)

@Entity(
    tableName = "cards",
    indices = [
        Index(value = ["setId"]),
        Index(value = ["language", "setId"])
    ]
)
data class CardEntity(
    @PrimaryKey val id: String,
    val name: String,
    val supertype: String,
    val number: String,
    val rarity: String?,
    val setId: String,
    val setName: String,
    val series: String,
    val imageUrl: String?,
    val largeImageUrl: String?,
    /** Cardmarket price in EUR. Null means that a market price was not listed. */
    val marketPrice: Double?,
    val lowPrice: Double?,
    val trendPrice: Double?,
    val nationalPokedexNumber: Int?,
    val subtypes: String?,
    val language: String,
    /** Comma-separated National Pokédex IDs for cards featuring more than one Pokémon. */
    val nationalPokedexNumbers: String? = null,
    val setLogoUrl: String? = null,
    val setSymbolUrl: String? = null,
    val setCardCountOfficial: Int? = null,
    val setCardCountTotal: Int? = null,
    val setReleaseDate: String? = null,
    /** Precalculated integer sort number for fast sorting without runtime GLOB/CAST. */
    @ColumnInfo(defaultValue = "0") val sortNumber: Int = 0,
    /** 0 = brief catalog item, 1 = full details loaded. */
    @ColumnInfo(defaultValue = "0") val detailLoaded: Int = 0
)

@Entity(tableName = "last_update")
data class LastUpdateEntity(
    @PrimaryKey val id: Int = 1,
    val lastUpdateTimestamp: Long
)

/** Persisted source coverage report: distinguishes successful checks from unavailable catalogues. */
@Entity(tableName = "source_audit")
data class SourceAuditEntity(
    @PrimaryKey val id: String,
    val sourceName: String,
    val language: String,
    val sourceUrl: String,
    val checkedAt: Long,
    val success: Boolean,
    val discoveredSets: Int,
    val matchedSets: Int,
    val missingSets: Int,
    val discoveredCards: Int,
    val cardsWithImages: Int,
    /** Human-readable diagnostics, including unmatched official product names when available. */
    @ColumnInfo(defaultValue = "''") val note: String = "",
    /** Populated on set-level rows for a rough expected-vs-indexed coverage comparison. */
    @ColumnInfo(defaultValue = "0") val expectedCards: Int = 0,
    @ColumnInfo(defaultValue = "0") val indexedCards: Int = 0,
    @ColumnInfo(defaultValue = "0") val missingCards: Int = 0,
    @ColumnInfo(defaultValue = "0") val cardsMissingImages: Int = 0
)
