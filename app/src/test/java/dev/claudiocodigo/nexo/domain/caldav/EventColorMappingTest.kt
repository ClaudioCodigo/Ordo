package dev.claudiocodigo.nexo.domain.caldav

import org.junit.Assert.assertEquals
import org.junit.Test

class EventColorMappingTest {

    @Test
    fun `green exact and seeded color variants map to validated`() {
        assertEquals(EventColor.VALIDADO, ColorClassifier.classify("#008000"))
        assertEquals(EventColor.VALIDADO, ColorClassifier.classify("#228B22"))
        assertEquals(EventColor.VALIDADO, ColorClassifier.classify("#32CD32"))
        assertEquals(EventColor.VALIDADO, ColorClassifier.classify("DARKOLIVEGREEN"))
        assertEquals(EventColor.VALIDADO, ColorClassifier.classify("green"))
    }

    @Test
    fun `red exact color and variants map to requires attention`() {
        assertEquals(EventColor.REQUER_ATENCAO, ColorClassifier.classify("#B22222"))
        assertEquals(EventColor.REQUER_ATENCAO, ColorClassifier.classify("RED"))
        assertEquals(EventColor.REQUER_ATENCAO, ColorClassifier.classify("#FF0000"))
        assertEquals(EventColor.REQUER_ATENCAO, ColorClassifier.classify("#D32F2F"))
    }

    @Test
    fun `neutral unmapped or absent colors map to unclassified`() {
        assertEquals(EventColor.NAO_CLASSIFICADO, ColorClassifier.classify("#4682B4")) // Steel blue
        assertEquals(EventColor.NAO_CLASSIFICADO, ColorClassifier.classify("#00679E")) // Dark blue
        assertEquals(EventColor.NAO_CLASSIFICADO, ColorClassifier.classify("#9370DB")) // Medium purple
        assertEquals(EventColor.NAO_CLASSIFICADO, ColorClassifier.classify(null))
        assertEquals(EventColor.NAO_CLASSIFICADO, ColorClassifier.classify(""))
    }

    @Test
    fun `custom user color mapping overrides defaults`() {
        val customValidated = setOf("CUSTOM_GREEN", "112233")
        val customAttention = setOf("CUSTOM_RED", "445566")

        assertEquals(EventColor.VALIDADO, ColorClassifier.classify("#112233", customValidated, customAttention))
        assertEquals(EventColor.REQUER_ATENCAO, ColorClassifier.classify("#445566", customValidated, customAttention))
        assertEquals(EventColor.NAO_CLASSIFICADO, ColorClassifier.classify("#008000", customValidated, customAttention))
    }

    @Test
    fun `red attention takes precedence when color exists in both sets`() {
        val dualSet = setOf("CONFLICT_COLOR")
        assertEquals(EventColor.REQUER_ATENCAO, ColorClassifier.classify("CONFLICT_COLOR", customValidated = dualSet, customAttention = dualSet))
    }
}
