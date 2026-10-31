package com.pokemontcg.sammlung.data.model

import androidx.annotation.StringRes
import com.pokemontcg.sammlung.R

data class PokemonGeneration(
    val id: Int,
    @StringRes val nameRes: Int,
    @StringRes val regionRes: Int,
    val startDex: Int,
    val endDex: Int
)

object GenerationModel {

    val GENERATIONS = listOf(
        PokemonGeneration(1, R.string.generation_name_1, R.string.generation_region_kanto, 1, 151),
        PokemonGeneration(2, R.string.generation_name_2, R.string.generation_region_johto, 152, 251),
        PokemonGeneration(3, R.string.generation_name_3, R.string.generation_region_hoenn, 252, 386),
        PokemonGeneration(4, R.string.generation_name_4, R.string.generation_region_sinnoh, 387, 493),
        PokemonGeneration(5, R.string.generation_name_5, R.string.generation_region_einall, 494, 649),
        PokemonGeneration(6, R.string.generation_name_6, R.string.generation_region_kalos, 650, 721),
        PokemonGeneration(7, R.string.generation_name_7, R.string.generation_region_alola, 722, 809),
        PokemonGeneration(8, R.string.generation_name_8, R.string.generation_region_galar, 810, 905),
        PokemonGeneration(9, R.string.generation_name_9, R.string.generation_region_paldea, 906, 1025),
        PokemonGeneration(10, R.string.generation_name_10, R.string.generation_region_special_formats, 1026, Int.MAX_VALUE)
    )

    fun generationOf(dexId: Int): Int {
        return when (dexId) {
            in 1..151 -> 1
            in 152..251 -> 2
            in 252..386 -> 3
            in 387..493 -> 4
            in 494..649 -> 5
            in 650..721 -> 6
            in 722..809 -> 7
            in 810..905 -> 8
            in 906..1025 -> 9
            else -> 10
        }
    }

    fun getGenerationInfo(genId: Int): PokemonGeneration {
        return GENERATIONS.find { it.id == genId } ?: GENERATIONS.last()
    }
}
