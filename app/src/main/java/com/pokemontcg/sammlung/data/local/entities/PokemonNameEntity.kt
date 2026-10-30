package com.pokemontcg.sammlung.data.local.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "pokemon_names",
    primaryKeys = ["language", "name"],
    indices = [
        Index(value = ["language", "name"]),
        Index(value = ["dexId"])
    ]
)
data class PokemonNameEntity(
    val language: String,
    val name: String,
    val dexId: Int
)
