package com.pokemontcg.sammlung.data.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build()

    private val tcgdexApis = mutableMapOf<String, TCGdexApi>()

    @Synchronized
    fun tcgdexFor(language: String): TCGdexApi = tcgdexApis.getOrPut(language.lowercase()) {
        Retrofit.Builder()
            .baseUrl("https://api.tcgdex.net/v2/${language.lowercase()}/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TCGdexApi::class.java)
    }

    val tcgdex: TCGdexApi by lazy { tcgdexFor("de") }
    val tcgdexEnglish: TCGdexApi by lazy { tcgdexFor("en") }

    val pokeapi: PokeApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://pokeapi.co/api/v2/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PokeApi::class.java)
    }
}
