package dev.claudiocodigo.nexo.data.local.entity

import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import dev.claudiocodigo.nexo.domain.serviceorder.ConclusionState
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class ServiceOrderEntityTest {
    @Test
    fun unknownStatusFallsBackToPending() {
        val entity = ServiceOrderEntity(
            id = UUID.randomUUID(),
            externalId = null,
            title = "Teste",
            description = "",
            status = "STATUS_ANTIGO",
            clientName = "Cliente",
            unitName = "Unidade",
            scheduledDate = null,
            createdAt = 1L,
            updatedAt = 2L
        )

        assertEquals(ServiceOrderStatus.PENDENTE, entity.toDomain().status)
    }

    @Test
    fun conclusionStatesSurviveEntityRoundTrip() {
        listOf(
            ConclusionState.NAO_CONCLUIDO,
            ConclusionState.CONCLUIDO_COM_PENDENCIAS
        ).forEach { conclusionState ->
            val structured = StructuredServiceOrder(
                id = UUID.randomUUID(),
                title = "Teste",
                clientName = "Cliente",
                unitName = "Unidade",
                conclusionState = conclusionState
            )

            val restored = ServiceOrderEntity.fromStructured(structured).toStructured()

            assertEquals(conclusionState, restored.conclusionState)
        }
    }

    @Test
    fun unknownConclusionStateFallsBackToNotDefined() {
        val entity = ServiceOrderEntity(
            id = UUID.randomUUID(),
            externalId = null,
            title = "Teste",
            description = "",
            status = ServiceOrderStatus.PENDENTE.name,
            clientName = "Cliente",
            unitName = "Unidade",
            scheduledDate = null,
            createdAt = 1L,
            updatedAt = 2L,
            conclusionState = "ESTADO_DESCONHECIDO"
        )

        assertEquals(ConclusionState.NAO_DEFINIDO, entity.toStructured().conclusionState)
    }
}
