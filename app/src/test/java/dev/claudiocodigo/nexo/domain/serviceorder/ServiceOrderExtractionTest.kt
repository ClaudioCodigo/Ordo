package dev.claudiocodigo.nexo.domain.serviceorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceOrderExtractionTest {

    @Test
    fun extractSummary_parsesOsNumberAndSegmentsCleanly() {
        val raw = "OS 15428 - Hospital São Lucas - Centro Cirúrgico - Manutenção Nobreak"
        val result = ServiceOrderExtractor.extractSummary(raw)

        assertEquals("15428", result.externalId)
        assertEquals("Hospital São Lucas", result.clientName)
        assertEquals("Centro Cirúrgico", result.unitName)
        assertEquals("Manutenção Nobreak", result.title)
        assertEquals(raw, result.rawSummary)
    }

    @Test
    fun extractSummary_parsesExplicitLabels() {
        val raw = "OS: 15428 | Cli: Hospital ABC | Unid: Bloco 2 | Tec: Claudio | Tit: Troca de Placa"
        val result = ServiceOrderExtractor.extractSummary(raw)

        assertEquals("15428", result.externalId)
        assertEquals("Hospital ABC", result.clientName)
        assertEquals("Bloco 2", result.unitName)
        assertEquals("Claudio", result.technician)
        assertEquals("Troca de Placa", result.title)
    }

    @Test
    fun extractSummary_mapsQuestionMarksAndSemOsToNullExternalId() {
        val raw1 = "???? - Banco Central - Troca de Bateria"
        val result1 = ServiceOrderExtractor.extractSummary(raw1)
        assertNull(result1.externalId)
        assertEquals("Banco Central", result1.clientName)
        assertEquals("Troca de Bateria", result1.title)

        val raw2 = "SEM OS - Unidade Centro - Diagnóstico"
        val result2 = ServiceOrderExtractor.extractSummary(raw2)
        assertNull(result2.externalId)
        assertEquals("Unidade Centro", result2.clientName)
        assertEquals("Diagnóstico", result2.title)
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
        assertEquals(ServiceOrderPreset.SERVICO_SOLICITADO, result.preset)
    }

    @Test
    fun extractSummary_parsesFiveSegmentSummaryWithCategoryAndLocation() {
        val raw = "PIER - 15479 - CFTV - REVISAR CÂMERAS - ARM5"
        val result = ServiceOrderExtractor.extractSummary(raw)

        assertEquals("15479", result.externalId)
        assertEquals("PIER", result.clientName)
        assertEquals("CFTV", result.category)
        assertEquals("REVISAR CÂMERAS", result.title)
        assertEquals("ARM5", result.unitName)
    }

    @Test
    fun extractDescription_parsesReferenceIcsDescriptionFormat() {
        val raw = """
            OS: 15479
            Cliente: PIER - Armazém 5
            Técnico: Claudio

            Demanda:
            REVISAR TODAS AS CÂMERAS DO ARM 5

            Estado: Concluído
            Data de Conclusão: 31/08/2026

            Causa:
            N/A

            Solução:
            Foi realizado a revisão do funcionamento e ângulo de todas as câmeras do armazém 5.

            Pendências:
            Nenhuma
        """.trimIndent()

        val result = ServiceOrderExtractor.extractDescription(raw)

        assertEquals("15479", result.externalId)
        assertEquals("PIER", result.clientName)
        assertEquals("Armazém 5", result.unitName)
        assertEquals("Claudio", result.technician)
        assertEquals("REVISAR TODAS AS CÂMERAS DO ARM 5", result.originalDemand)
        assertEquals(ConclusionState.CONCLUIDO, result.conclusionState)
        assertTrue(result.isCompleted)
        assertNull(result.closureCause) // N/A should map to null
        assertTrue(result.closureSolution!!.startsWith("Foi realizado a revisão"))
        assertEquals("Nenhuma", result.closurePending)
    }

    @Test
    fun extractDescription_nonCompletedStateIsNotMarkedCompleted() {
        val result = ServiceOrderExtractor.extractDescription(
            """
                OS: 15480

                Demanda:
                Retornar ao local com a peça correta.

                Estado: Não concluído
            """.trimIndent()
        )

        assertEquals(ConclusionState.NAO_CONCLUIDO, result.conclusionState)
        assertEquals(false, result.isCompleted)
    }
}
