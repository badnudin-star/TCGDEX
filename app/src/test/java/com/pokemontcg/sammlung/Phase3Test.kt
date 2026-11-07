package com.pokemontcg.sammlung

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class Phase3Test {

    @Test
    fun testExtractCardNumberRegexPattern() {
        val numberRegex = Regex("([0-9]{1,3}\\s*/\\s*[0-9]{1,3})")
        val sampleVisionText = "Pikachu HP 60 \n 25 / 108 \n Illust. Ken Sugimori"

        val match = numberRegex.find(sampleVisionText)
        assertNotNull(match)
        val cleanNumber = match?.value?.replace(" ", "")
        assertEquals("25/108", cleanNumber)
    }
}
