package dev.claudiocodigo.nexo.data.local.entity

import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
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
}
