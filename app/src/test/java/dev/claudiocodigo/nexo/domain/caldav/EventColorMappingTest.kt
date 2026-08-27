package dev.claudiocodigo.nexo.domain.caldav

import org.junit.Assert.assertEquals
import org.junit.Test

class EventColorMappingTest {

    @Test
    fun `green exact color maps to validated`() {
        assertEquals(EventColor.VALIDADO, ColorClassifier.classify("#008000"))
        assertEquals(EventColor.VALIDADO, ColorClassifier.classify("#008000".lowercase()))
    }

    @Test
    fun `red exact color maps to requires attention`() {
        assertEquals(EventColor.REQUER_ATENCAO, ColorClassifier.classify("#B22222"))
        assertEquals(EventColor.REQUER_ATENCAO, ColorClassifier.classify("RED"))
    }

    @Test
    fun `other or absent colors map to unclassified`() {
        assertEquals(EventColor.NAO_CLASSIFICADO, ColorClassifier.classify("#FFAA00"))
        assertEquals(EventColor.NAO_CLASSIFICADO, ColorClassifier.classify(null))
        assertEquals(EventColor.NAO_CLASSIFICADO, ColorClassifier.classify("blue"))
    }

    @Test
    fun `calendar color does not affect event classification`() {
        // A green calendar collection must never mark a non-colored event green.
        assertEquals(EventColor.NAO_CLASSIFICADO, ColorClassifier.classify(null))
    }
}
