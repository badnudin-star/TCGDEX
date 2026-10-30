package com.pokemontcg.sammlung.data.model

data class PokemonGeneration(
    val id: Int,
    val name: String,
    val region: String,
    val startDex: Int,
    val endDex: Int
)

object GenerationModel {

    val GENERATIONS = listOf(
        PokemonGeneration(1, "Generation 1", "Kanto", 1, 151),
        PokemonGeneration(2, "Generation 2", "Johto", 152, 251),
        PokemonGeneration(3, "Generation 3", "Hoenn", 252, 386),
        PokemonGeneration(4, "Generation 4", "Sinnoh", 387, 493),
        PokemonGeneration(5, "Generation 5", "Einall", 494, 649),
        PokemonGeneration(6, "Generation 6", "Kalos", 650, 721),
        PokemonGeneration(7, "Generation 7", "Alola", 722, 809),
        PokemonGeneration(8, "Generation 8", "Galar", 810, 905),
        PokemonGeneration(9, "Generation 9", "Paldea", 906, 1025),
        PokemonGeneration(10, "Weitere", "Sonderformate", 1026, Int.MAX_VALUE)
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
