package com.pokemontcg.sammlung.data.model

import com.pokemontcg.sammlung.R
import org.junit.Assert.assertEquals
import org.junit.Test

class GenerationModelTest {

    @Test
    fun testGenerationOfBoundaries() {
        assertEquals(1, GenerationModel.generationOf(1))
        assertEquals(1, GenerationModel.generationOf(151))

        assertEquals(2, GenerationModel.generationOf(152))
        assertEquals(2, GenerationModel.generationOf(251))

        assertEquals(3, GenerationModel.generationOf(252))
        assertEquals(3, GenerationModel.generationOf(386))

        assertEquals(4, GenerationModel.generationOf(387))
        assertEquals(4, GenerationModel.generationOf(493))

        assertEquals(5, GenerationModel.generationOf(494))
        assertEquals(5, GenerationModel.generationOf(649))

        assertEquals(6, GenerationModel.generationOf(650))
        assertEquals(6, GenerationModel.generationOf(721))

        assertEquals(7, GenerationModel.generationOf(722))
        assertEquals(7, GenerationModel.generationOf(809))

        assertEquals(8, GenerationModel.generationOf(810))
        assertEquals(8, GenerationModel.generationOf(905))

        assertEquals(9, GenerationModel.generationOf(906))
        assertEquals(9, GenerationModel.generationOf(1025))

        assertEquals(10, GenerationModel.generationOf(1026))
        assertEquals(10, GenerationModel.generationOf(0))
        assertEquals(10, GenerationModel.generationOf(-1))
    }

    @Test
    fun testGenerationInfo() {
        val gen1 = GenerationModel.getGenerationInfo(1)
        assertEquals(R.string.generation_name_1, gen1.nameRes)
        assertEquals(R.string.generation_region_kanto, gen1.regionRes)

        val gen9 = GenerationModel.getGenerationInfo(9)
        assertEquals(R.string.generation_name_9, gen9.nameRes)
        assertEquals(R.string.generation_region_paldea, gen9.regionRes)

        val gen10 = GenerationModel.getGenerationInfo(10)
        assertEquals(R.string.generation_name_10, gen10.nameRes)
    }
}
