package com.pokemontcg.sammlung.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.Response

interface TCGdexApi {
    /** Full brief-card catalogue. TCGdex returns all records when pagination parameters are omitted. */
    @GET("cards")
    suspend fun getAllCards(): List<CardBrief>

    /** The filtered list endpoint returns brief objects; full details are fetched by ID. */
    @GET("cards")
    suspend fun getCards(
        @Query("dexId") dexId: Int,
        @Query("pagination:page") page: Int = 1,
        @Query("pagination:itemsPerPage") pageSize: Int = 100
    ): List<CardBrief>

    @GET("cards/{id}")
    suspend fun getCard(@Path("id") id: String): CardData

    @GET("sets")
    suspend fun getSets(): List<SetData>

    /** Keeps the error body readable for discovering the current API language list. */
    @GET("sets")
    suspend fun getSetsResponse(): Response<List<SetData>>

    @GET("sets/{id}")
    suspend fun getSet(@Path("id") id: String): SetData
}

data class CardBrief(
    @SerializedName("id") val id: String,
    @SerializedName("localId") val localId: String,
    @SerializedName("name") val name: String,
    @SerializedName("image") val image: String?
)

data class CardData(
    @SerializedName("id") val id: String,
    @SerializedName("localId") val localId: String,
    @SerializedName("name") val name: String,
    @SerializedName("image") val image: String?,
    @SerializedName("category") val category: String?,
    @SerializedName("rarity") val rarity: String?,
    @SerializedName("set") val set: SetDataBrief?,
    @SerializedName("dexId") val dexId: List<Int>?,
    @SerializedName("pricing") val pricing: Pricing?
)

data class SetDataBrief(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("logo") val logo: String?,
    @SerializedName("symbol") val symbol: String?,
    @SerializedName("cardCount") val cardCount: CardCount?,
    @SerializedName("releaseDate") val releaseDate: String?,
    @SerializedName("serie") val serie: SerieBrief?
)

data class SetData(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("logo") val logo: String?,
    @SerializedName("symbol") val symbol: String?,
    @SerializedName("cardCount") val cardCount: CardCount?,
    @SerializedName("releaseDate") val releaseDate: String?,
    @SerializedName("serie") val serie: SerieBrief?,
    @SerializedName("tcgOnline") val tcgOnline: String?,
    @SerializedName("cards") val cards: List<CardBrief>? = null
)

data class SerieBrief(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String?
)

data class CardCount(
    @SerializedName("official") val official: Int,
    @SerializedName("total") val total: Int
)

data class Pricing(
    @SerializedName("cardmarket") val cardmarket: CardmarketPrice?,
    @SerializedName("tcgplayer") val tcgplayer: TcgplayerPrice?
)

data class CardmarketPrice(
    @SerializedName("avg") val avg: Double?,
    @SerializedName("low") val low: Double?,
    @SerializedName("trend") val trend: Double?,
    @SerializedName("avg-holo") val avgHolo: Double?,
    @SerializedName("low-holo") val lowHolo: Double?,
    @SerializedName("trend-holo") val trendHolo: Double?,
    @SerializedName("unit") val unit: String?
)

data class TcgplayerPrice(
    @SerializedName("normal") val normal: PriceVariant?,
    @SerializedName("holofoil") val holofoil: PriceVariant?,
    @SerializedName("reverse-holofoil") val reverseHolofoil: PriceVariant?,
    @SerializedName("reverse") val reverse: PriceVariant?,
    @SerializedName("unit") val unit: String?
)

data class PriceVariant(
    @SerializedName("marketPrice") val marketPrice: Double?,
    @SerializedName("lowPrice") val lowPrice: Double?
)

interface PokeApi {
    @GET("pokemon-species")
    suspend fun getPokemonList(
        @Query("limit") limit: Int = 1025,
        @Query("offset") offset: Int = 0
    ): PokemonListResponse
}

data class PokemonListResponse(
    @SerializedName("results") val results: List<PokemonResult>
)

data class PokemonResult(
    @SerializedName("name") val name: String,
    @SerializedName("url") val url: String
)
