package dev.claudiocodigo.nexo.domain.serviceorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceOrderExtractionTest {

    @Test
    fun extractSummary_parsesOsNumberAndSegmentsCleanly() {
        val raw = "OS 15428 - Hospital São Lucas - Manutenção Nobreak"
        val result = ServiceOrderExtractor.extractSummary(raw)

        assertEquals("15428", result.externalId)
        assertEquals("Hospital São Lucas - Manutenção Nobreak", result.title)
        assertEquals(raw, result.rawSummary)
    }

    @Test
    fun extractSummary_mapsQuestionMarksAndSemOsToNullExternalId() {
        val raw1 = "???? - Banco Central - Troca de Bateria"
        val result1 = ServiceOrderExtractor.extractSummary(raw1)
        assertNull(result1.externalId)
        assertEquals("Banco Central - Troca de Bateria", result1.title)

        val raw2 = "SEM OS - Unidade Centro - Diagnóstico"
        val result2 = ServiceOrderExtractor.extractSummary(raw2)
        assertNull(result2.externalId)
        assertEquals("Unidade Centro - Diagnóstico", result2.title)
    }

    @Test
    fun extractDescription_parsesStructuredLabels() {
        val raw = """
            OS: 15428
            Demanda: Equipamento reiniciando sozinho.
            Causa: Baterias desgastadas e estufadas.
            Solução: Substituído banco de 4 baterias 12V 7Ah.
            Pendências: Nenhuma.
        """.trimIndent()

        val result = ServiceOrderExtractor.extractDescription(raw)

        assertEquals(ServiceOrderPreset.DIAGNOSTICO_CORRECAO, result.preset)
        assertEquals("Equipamento reiniciando sozinho.", result.originalDemand)
        assertEquals("Baterias desgastadas e estufadas.", result.closureCause)
        assertEquals("Substituído banco de 4 baterias 12V 7Ah.", result.closureSolution)
        assertEquals("Nenhuma.", result.closurePending)
        assertEquals(raw, result.rawDescription)
    }

    @Test
    fun extractDescription_preservesUnstructuredTextAsOriginDemand() {
        val raw = "Favor verificar o nobreak da sala de TI que está apitando intermitentemente desde ontem à tarde."
        val result = ServiceOrderExtractor.extractDescription(raw)

        assertEquals(raw, result.originalDemand)
        assertNull(result.closureCause)
        assertNull(result.closureSolution)
        assertEquals(0, result.updates.size)
    }
}
