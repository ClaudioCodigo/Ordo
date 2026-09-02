package dev.claudiocodigo.nexo.domain.serviceorder

import dev.claudiocodigo.nexo.domain.caldav.EventColor
import dev.claudiocodigo.nexo.domain.caldav.RemoteEvent
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import dev.claudiocodigo.nexo.domain.publication.OutboxAction
import dev.claudiocodigo.nexo.domain.publication.OutboxOperation
import dev.claudiocodigo.nexo.domain.publication.OutboxStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class OperationalOrderProjectionTest {

    @Test
    fun project_unifiesLinkedRemoteAndLocalIntoSingleCard() {
        val orderId = UUID.randomUUID()
        val key = RemoteOccurrenceKey("acct1", "/cal/", "/cal/e1.ics", null)
        val remoteEvent = makeRemoteEvent("acct1", "/cal/", "/cal/e1.ics", "Manutenção Preventiva", rawColor = null, start = 1000L)
        val structuredOrder = StructuredServiceOrder(
            id = orderId,
            occurrenceKey = key,
            title = "Manutenção Preventiva - OS 100",
            clientName = "Hospital São Lucas",
            unitName = "UTI 2",
            status = ServiceOrderStatus.EM_ANDAMENTO
        )

        val cards = OperationalOrderProjection.project(
            remoteEvents = listOf(remoteEvent),
            structuredOrders = listOf(structuredOrder),
            outboxOperations = emptyList()
        )

        assertEquals(1, cards.size)
        val card = cards.first()
        assertTrue(card.isLinked)
        assertEquals(orderId, card.localOrderId)
        assertEquals("acct1", card.remoteAccountId)
        assertEquals("Manutenção Preventiva - OS 100", card.title)
        assertEquals("Hospital São Lucas", card.clientName)
        assertEquals(OperationalStatus.EM_ANDAMENTO, card.status)
        assertEquals(CardNavigationTarget.EVENTO_REMOTO, card.navigationTarget)
    }

    @Test
    fun project_excludesUnlinkedProvisionalLocalOrdersFromCards() {
        val remoteEvent = makeRemoteEvent("acct1", "/cal/", "/cal/e2.ics", "Atendimento Geral", rawColor = null, start = 2000L)
        val provisionalOrder = StructuredServiceOrder(
            id = UUID.randomUUID(),
            occurrenceKey = null,
            title = "OS Provisória Avulsa",
            clientName = "Cliente X",
            unitName = "",
            createdAt = 3000L
        )

        val cards = OperationalOrderProjection.project(
            remoteEvents = listOf(remoteEvent),
            structuredOrders = listOf(provisionalOrder),
            outboxOperations = emptyList()
        )

        assertEquals(1, cards.size)
        assertEquals("Atendimento Geral", cards[0].title)
        assertFalse(cards[0].isLinked)
        assertEquals(CardNavigationTarget.EVENTO_REMOTO, cards[0].navigationTarget)
    }

    @Test
    fun project_sortsOldestFirstAndPlacesConcludedAtEnd() {
        val eventEarlyConcluded = makeRemoteEvent("acct1", "/cal/", "/cal/e1.ics", "OS Concluída Cedo", rawColor = "#008000", start = 1000L)
        val eventEarlyOpen = makeRemoteEvent("acct1", "/cal/", "/cal/e2.ics", "OS Aberta 09h", rawColor = null, start = 2000L)
        val eventLateOpen = makeRemoteEvent("acct1", "/cal/", "/cal/e3.ics", "OS Aberta 14h", rawColor = null, start = 5000L)

        val cards = OperationalOrderProjection.project(
            remoteEvents = listOf(eventEarlyConcluded, eventLateOpen, eventEarlyOpen),
            structuredOrders = emptyList(),
            outboxOperations = emptyList()
        )

        assertEquals(3, cards.size)
        // Open items first (oldest first: 2000L, then 5000L)
        assertEquals("OS Aberta 09h", cards[0].title)
        assertEquals("OS Aberta 14h", cards[1].title)
        // Concluded item (green) at the end
        assertEquals("OS Concluída Cedo", cards[2].title)
        assertEquals(OperationalStatus.VALIDADO_EXTERNAMENTE, cards[2].status)
    }

    @Test
    fun project_redColorHasHighestPrecedenceOverCompletionAndOutbox() {
        val orderId = UUID.randomUUID()
        val key = RemoteOccurrenceKey("acct1", "/cal/", "/cal/e1.ics", null)
        val remoteEvent = makeRemoteEvent("acct1", "/cal/", "/cal/e1.ics", "OS Rejeitada", rawColor = "#B22222", start = 1000L)
        val order = StructuredServiceOrder(
            id = orderId,
            occurrenceKey = key,
            title = "OS Rejeitada",
            clientName = "Cliente",
            unitName = "",
            status = ServiceOrderStatus.CONCLUIDA
        )
        val outbox = OutboxOperation(
            orderId = orderId,
            action = OutboxAction.UPDATE,
            payloadIcs = "",
            ifMatchEtag = "\"etag\"",
            status = OutboxStatus.PENDING
        )

        val cards = OperationalOrderProjection.project(
            remoteEvents = listOf(remoteEvent),
            structuredOrders = listOf(order),
            outboxOperations = listOf(outbox)
        )

        assertEquals(OperationalStatus.REQUER_ATENCAO, cards.first().status)
    }

    @Test
    fun project_internalCompletionWithoutGreenIsAwaitingExternalValidation() {
        val orderId = UUID.randomUUID()
        val key = RemoteOccurrenceKey("acct1", "/cal/", "/cal/e1.ics", null)
        val remoteEvent = makeRemoteEvent("acct1", "/cal/", "/cal/e1.ics", "OS Finalizada", rawColor = null, start = 1000L)
        val order = StructuredServiceOrder(
            id = orderId,
            occurrenceKey = key,
            title = "OS Finalizada",
            clientName = "Cliente",
            unitName = "",
            status = ServiceOrderStatus.CONCLUIDA
        )

        val cards = OperationalOrderProjection.project(
            remoteEvents = listOf(remoteEvent),
            structuredOrders = listOf(order),
            outboxOperations = emptyList()
        )

        assertEquals(OperationalStatus.AGUARDANDO_VALIDACAO_EXTERNA, cards.first().status)
    }

    @Test
    fun project_nonCompletedConclusionRemainsInProgressEvenWithStaleCompletedStatus() {
        val orderId = UUID.randomUUID()
        val key = RemoteOccurrenceKey("acct1", "/cal/", "/cal/e-nao-concluida.ics", null)
        val remoteEvent = makeRemoteEvent("acct1", "/cal/", "/cal/e-nao-concluida.ics", "OS não concluída", rawColor = null, start = 1000L)
        val order = StructuredServiceOrder(
            id = orderId,
            occurrenceKey = key,
            title = "OS não concluída",
            clientName = "Cliente",
            unitName = "Local",
            status = ServiceOrderStatus.CONCLUIDA,
            conclusionState = ConclusionState.NAO_CONCLUIDO
        )

        val card = OperationalOrderProjection.project(
            remoteEvents = listOf(remoteEvent),
            structuredOrders = listOf(order),
            outboxOperations = emptyList()
        ).single()

        assertEquals(OperationalStatus.EM_ANDAMENTO, card.status)
        assertFalse(OperationalOrderProjection.isCardConcluded(card))
    }

    @Test
    fun project_greenRemoteColorBecomesValidatedExternally() {
        val remoteEvent = makeRemoteEvent("acct1", "/cal/", "/cal/e1.ics", "OS Aprovada", rawColor = "#008000", start = 1000L)
        val cards = OperationalOrderProjection.project(
            remoteEvents = listOf(remoteEvent),
            structuredOrders = emptyList(),
            outboxOperations = emptyList()
        )

        assertEquals(OperationalStatus.VALIDADO_EXTERNAMENTE, cards.first().status)
    }

    private fun makeRemoteEvent(
        accountId: String,
        calHref: String,
        href: String,
        summary: String,
        rawColor: String?,
        start: Long
    ): RemoteEvent {
        return RemoteEvent(
            accountId = accountId,
            calendarHref = calHref,
            href = href,
            uid = href,
            etag = "\"etag\"",
            sequence = 0,
            rawIcs = "",
            summary = summary,
            description = "",
            location = null,
            start = start,
            end = start + 3600_000,
            allDay = false,
            color = EventColor.NAO_CLASSIFICADO,
            rawEventColor = rawColor,
            timeZone = null,
            recurrenceText = null,
            lastModified = null,
            lastSyncMillis = 1000L
        )
    }
}
