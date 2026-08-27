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
        assertTrue(rendered.contains("Hospital São Lucas - Centro Cirúrgico"))
        assertTrue(rendered.contains("Técnico: João Silva"))
        assertTrue(rendered.contains("Demanda:\nNobreak desarmando na troca de rede."))
        assertTrue(rendered.contains("Atualizações:\n[26/08/2026]: Chegada ao local e início dos testes."))
        assertTrue(rendered.contains("Pendências:\nAguardando chegada de novo fusível."))
        assertFalse(rendered.contains("Estado: Concluído"))
    }

    @Test
    fun renderCompletion_consolidatesCauseAndSolutionAndOmitsIntermediateUpdates() {
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
            closurePending = "Nenhuma"
        )

        val rendered = ServiceOrderRenderer.renderCompletion(order, completionDate)

        assertTrue(rendered.contains("Estado: Concluído"))
        assertTrue(rendered.contains("Data de Conclusão: 27/08/2026"))
        assertTrue(rendered.contains("Causa:\nBateria com célula em curto-circuito."))
        assertTrue(rendered.contains("Solução:\nSubstituídas as 4 baterias e calibrado inversor."))
        assertTrue(rendered.contains("Pendências:\nNenhuma"))
        // Intermediate updates are kept local and omitted from the final remote description
        assertFalse(rendered.contains("Update intermediário local"))
    }
}
