package dev.claudiocodigo.nexo.domain.serviceorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class ServiceOrderRendererTest {

    @Test
    fun renderUpdate_includesHeaderDemandUpdatesAndPendencies() {
        val updateDate = Instant.parse("2026-08-26T12:00:00Z").toEpochMilli()
        val update = ServiceOrderUpdate(
            id = UUID.randomUUID(),
            sequenceOrder = 1,
            text = "Chegada ao local e início dos testes.",
            executionDate = updateDate
        )

        val order = StructuredServiceOrder(
            id = UUID.randomUUID(),
            externalId = "15428",
            title = "Manutenção",
            clientName = "Hospital São Lucas",
            unitName = "Centro Cirúrgico",
            technician = "João Silva",
            category = "Nobreak",
            preset = ServiceOrderPreset.DIAGNOSTICO_CORRECAO,
            originalDemand = "Nobreak desarmando na troca de rede.",
            updates = listOf(update),
            closurePending = "Aguardando chegada de novo fusível."
        )

        val rendered = ServiceOrderRenderer.renderUpdate(order, updateDate)

        assertTrue(rendered.contains("OS: 15428"))
        assertTrue(rendered.contains("Cliente: Hospital São Lucas"))
        assertTrue(rendered.contains("Local: Centro Cirúrgico"))
        assertTrue(rendered.contains("Técnico: João Silva"))
        assertFalse(rendered.contains("Categoria:"))
        assertTrue(rendered.contains("Demanda:\nNobreak desarmando na troca de rede."))
        assertTrue(rendered.contains("Atualizações:\n[26/08/2026]: Chegada ao local e início dos testes."))
        assertTrue(rendered.contains("Pendências:\nAguardando chegada de novo fusível."))
        assertFalse(rendered.contains("Estado: Concluído"))
    }

    @Test
    fun renderUpdate_includesNonCompletedStateWithoutCompletionDate() {
        val order = StructuredServiceOrder(
            title = "Atendimento",
            clientName = "PIER",
            unitName = "ARM5",
            originalDemand = "Revisar equipamento",
            conclusionState = ConclusionState.NAO_CONCLUIDO
        )

        val rendered = ServiceOrderRenderer.renderUpdate(order)

        assertTrue(rendered.contains("Estado: Não concluído"))
        assertFalse(rendered.contains("Data de Conclusão:"))
    }

    @Test
    fun renderCompletion_consolidatesCauseAndSolutionAndPlacesEstadoBetweenHeaderAndDemand() {
        val completionDate = Instant.parse("2026-08-27T15:00:00Z").toEpochMilli()
        val update = ServiceOrderUpdate(
            id = UUID.randomUUID(),
            sequenceOrder = 1,
            text = "Update intermediário local",
            executionDate = completionDate
        )

        val order = StructuredServiceOrder(
            id = UUID.randomUUID(),
            externalId = "15428",
            title = "Manutenção",
            clientName = "Hospital São Lucas",
            unitName = "Centro Cirúrgico",
            preset = ServiceOrderPreset.DIAGNOSTICO_CORRECAO,
            originalDemand = "Nobreak desarmando na troca de rede.",
            updates = listOf(update),
            closureCause = "Bateria com célula em curto-circuito.",
            closureSolution = "Substituídas as 4 baterias e calibrado inversor.",
            closurePending = "Nenhuma",
            conclusionState = ConclusionState.CONCLUIDO
        )

        val rendered = ServiceOrderRenderer.renderCompletion(order, completionDate)

        assertTrue(rendered.contains("OS: 15428"))
        assertTrue(rendered.contains("Cliente: Hospital São Lucas"))
        assertTrue(rendered.contains("Local: Centro Cirúrgico"))
        assertFalse(rendered.contains("Categoria:"))
        assertTrue(rendered.contains("Estado: Concluído"))
        assertTrue(rendered.contains("Data de Conclusão: 27/08/2026"))
        assertTrue(rendered.contains("Demanda:\nNobreak desarmando na troca de rede."))
        assertTrue(rendered.contains("Causa:\nBateria com célula em curto-circuito."))
        assertTrue(rendered.contains("Solução:\nSubstituídas as 4 baterias e calibrado inversor."))
        assertTrue(rendered.contains("Pendências:\nNenhuma"))
        assertFalse(rendered.contains("Update intermediário local"))

        // Verify order of sections
        val estadoIdx = rendered.indexOf("Estado: Concluído")
        val demandaIdx = rendered.indexOf("Demanda:")
        val causaIdx = rendered.indexOf("Causa:")
        val solucaoIdx = rendered.indexOf("Solução:")
        val pendenciasIdx = rendered.indexOf("Pendências:")

        assertTrue(estadoIdx < demandaIdx)
        assertTrue(demandaIdx < causaIdx)
        assertTrue(causaIdx < solucaoIdx)
        assertTrue(solucaoIdx < pendenciasIdx)
    }

    @Test
    fun renderCompletion_formatsConclusionStatesCorrectly() {
        val completionDate = Instant.parse("2026-08-31T10:00:00Z").toEpochMilli()
        val orderPending = StructuredServiceOrder(
            id = UUID.randomUUID(),
            externalId = "15479",
            title = "REVISAR CÂMERAS",
            clientName = "PIER",
            unitName = "Armazém 5",
            technician = "Claudio",
            originalDemand = "REVISAR TODAS AS CÂMERAS DO ARM 5",
            closureSolution = "Revisão efetuada.",
            conclusionState = ConclusionState.CONCLUIDO_COM_PENDENCIAS
        )

        val renderedPending = ServiceOrderRenderer.renderCompletion(orderPending, completionDate)
        assertTrue(renderedPending.contains("Estado: Concluído com pendências"))

        val orderNotDone = orderPending.copy(conclusionState = ConclusionState.NAO_CONCLUIDO)
        val renderedNotDone = ServiceOrderRenderer.renderCompletion(orderNotDone, completionDate)
        assertTrue(renderedNotDone.contains("Estado: Não concluído"))
    }

    @Test
    fun renderSummary_formatsFiveSegmentStandardSummary() {
        val order = StructuredServiceOrder(
            id = UUID.randomUUID(),
            externalId = "15479",
            title = "REVISAR CÂMERAS",
            clientName = "PIER",
            unitName = "ARM5",
            category = "CFTV"
        )

        val summary = ServiceOrderRenderer.renderSummary(order)
        assertEquals("PIER - 15479 - CFTV - REVISAR CÂMERAS - ARM5", summary)
    }

    @Test
    fun renderSummary_formatsProvisionalSummaryWithPlaceholderAndTechnician() {
        val order = StructuredServiceOrder(
            id = UUID.randomUUID(),
            externalId = null,
            title = "REVISAR CÂMERAS",
            clientName = "PIER",
            unitName = "ARM5",
            technician = "Claudio",
            category = "CFTV"
        )

        val summary = ServiceOrderRenderer.renderSummary(order)
        assertEquals("PIER - ???? - Claudio - CFTV - REVISAR CÂMERAS - ARM5", summary)
    }

    @Test
    fun renderCompletion_goldenResolutionWithObservations() {
        val completionDate = Instant.parse("2026-08-31T12:00:00Z").toEpochMilli()
        val order = StructuredServiceOrder(
            id = UUID.randomUUID(),
            externalId = "15479",
            title = "REVISAR CÂMERAS",
            clientName = "PIER",
            unitName = "Armazém 5",
            technician = "Claudio",
            flow = ServiceOrderFlow.RESOLUTION,
            originalDemand = "REVISAR TODAS AS CÂMERAS DO ARM 5",
            closureCause = "N/A",
            closureSolution = "Foi realizado a revisão e limpeza dos sensores.",
            closurePending = "Nenhuma",
            observations = "Equipamento adicional testado.",
            conclusionState = ConclusionState.CONCLUIDO
        )

        val rendered = ServiceOrderRenderer.renderCompletion(order, completionDate)
        val expected = """
            OS: 15479
            Cliente: PIER
            Local: Armazém 5
            Técnico: Claudio

            Estado: Concluído
            Data de Conclusão: 31/08/2026

            Demanda:
            REVISAR TODAS AS CÂMERAS DO ARM 5

            Causa:
            N/A

            Solução:
            Foi realizado a revisão e limpeza dos sensores.

            Pendências:
            Nenhuma

            Observações:
            Equipamento adicional testado.
        """.trimIndent()

        assertEquals(expected, rendered)
    }

    @Test
    fun renderCompletion_goldenRequestFlow() {
        val completionDate = Instant.parse("2026-08-31T12:00:00Z").toEpochMilli()
        val order = StructuredServiceOrder(
            id = UUID.randomUUID(),
            externalId = "15480",
            title = "INSTALAR PONTO DE REDE",
            clientName = "PIER",
            unitName = "Armazém 5",
            technician = "Claudio",
            flow = ServiceOrderFlow.REQUEST,
            originalDemand = "INSTALAR NOVO PONTO DE REDE NO ESCRITÓRIO",
            closureSolution = "Passado cabo Cat6 e conectorizado no switch.",
            closurePending = null,
            conclusionState = ConclusionState.CONCLUIDO
        )

        val rendered = ServiceOrderRenderer.renderCompletion(order, completionDate)
        val expected = """
            OS: 15480
            Cliente: PIER
            Local: Armazém 5
            Técnico: Claudio

            Estado: Concluído
            Data de Conclusão: 31/08/2026

            Solicitação:
            INSTALAR NOVO PONTO DE REDE NO ESCRITÓRIO

            Ação Realizada:
            Passado cabo Cat6 e conectorizado no switch.

            Pendências:
            Nenhuma
        """.trimIndent()

        assertEquals(expected, rendered)
    }

    @Test
    fun renderUpdate_goldenUpdateFlowWithHistoryAndObservations() {
        val previousHistory = """
            OS: 15480
            Cliente: PIER
            Local: Armazém 5
            Técnico: Claudio

            Solicitação:
            INSTALAR NOVO PONTO DE REDE NO ESCRITÓRIO
        """.trimIndent()

        val order = StructuredServiceOrder(
            id = UUID.randomUUID(),
            externalId = "15480",
            title = "INSTALAR PONTO DE REDE",
            clientName = "PIER",
            unitName = "Armazém 5",
            technician = "Claudio",
            flow = ServiceOrderFlow.UPDATE,
            updateDraft = "Passagem de cabeamento concluída. Retorno amanhã para conectorizar.",
            observations = "Necessário acesso ao rack principal.",
            baseSnapshot = RemoteBaseSnapshot(
                etag = "\"etag-123\"",
                rawIcs = "",
                rawSummary = "PIER - 15480 - REDE - INSTALAR PONTO - ARM5",
                rawDescription = previousHistory
            )
        )

        val rendered = ServiceOrderRenderer.renderUpdate(order)
        val expected = """
            OS: 15480
            Cliente: PIER
            Local: Armazém 5
            Técnico: Claudio

            Atualização:
            Passagem de cabeamento concluída. Retorno amanhã para conectorizar.

            Observações:
            Necessário acesso ao rack principal.

            ----- Histórico Remoto Preservado -----

            OS: 15480
            Cliente: PIER
            Local: Armazém 5
            Técnico: Claudio

            Solicitação:
            INSTALAR NOVO PONTO DE REDE NO ESCRITÓRIO
        """.trimIndent()

        assertEquals(expected, rendered)
    }
}
