package com.pokemontcg.sammlung.data.local.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "card_pokemon",
    primaryKeys = ["cardId", "dexId"],
    indices = [
        Index(value = ["dexId"]),
        Index(value = ["cardId"])
    ]
)
data class CardPokemonEntity(
    val cardId: String,
    val dexId: Int
)
