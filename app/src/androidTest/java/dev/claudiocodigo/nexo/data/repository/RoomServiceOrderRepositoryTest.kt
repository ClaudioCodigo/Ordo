package dev.claudiocodigo.nexo.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.claudiocodigo.nexo.data.local.NexoDatabase
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteOccurrenceKey
import dev.claudiocodigo.nexo.domain.serviceorder.ConclusionState
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderItem
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderPreset
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderUpdate
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderVersion
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RoomServiceOrderRepositoryTest {
    private lateinit var database: NexoDatabase
    private lateinit var repository: RoomServiceOrderRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NexoDatabase::class.java).build()
        repository = RoomServiceOrderRepository(database.serviceOrderDao(), database.serviceOrderStoreDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun saveAndReadRoundTripUsesInternalUuidAndOptionalOfficialNumber() = runTest {
        val id = UUID.randomUUID()
        val order = ServiceOrder(
            id = id,
            externalId = null,
            title = "Atendimento provisório",
            clientName = "Cliente",
            unitName = "Unidade",
            status = ServiceOrderStatus.EM_ANDAMENTO
        )

        repository.saveServiceOrder(order)

        assertEquals(order, repository.getServiceOrderById(id))
        assertEquals(listOf(order), repository.getServiceOrders().first())
    }

    @Test
    fun createOrGetAttendance_isIdempotentForSameOccurrence() = runTest {
        val key = RemoteOccurrenceKey(
            accountId = "acct-1",
            calendarHref = "/cal/1/",
            eventHref = "/cal/1/e1.ics",
            recurrenceId = null
        )

        val firstCall = repository.createOrGetAttendance(
            key = key,
            initialPreset = ServiceOrderPreset.DIAGNOSTICO_CORRECAO,
            title = "OS 15428 - Manutenção",
            clientName = "Hospital X",
            unitName = "Centro Cirúrgico",
            rawSummary = "OS 15428 - Manutenção",
            rawDescription = "Troca de bateria",
            rawIcs = "BEGIN:VCALENDAR\nEND:VCALENDAR",
            etag = "\"etag-1\"",
            startMillis = 1000L,
            endMillis = 2000L
        )

        val secondCall = repository.createOrGetAttendance(
            key = key,
            initialPreset = ServiceOrderPreset.DIAGNOSTICO_CORRECAO,
            title = "OS 15428 - Manutenção",
            clientName = "Hospital X",
            unitName = "Centro Cirúrgico",
            rawSummary = "OS 15428 - Manutenção",
            rawDescription = "Troca de bateria",
            rawIcs = "BEGIN:VCALENDAR\nEND:VCALENDAR",
            etag = "\"etag-1\"",
            startMillis = 1000L,
            endMillis = 2000L
        )

        // Must return the exact same local aggregate and ID
        assertEquals(firstCall.id, secondCall.id)
        assertEquals("Troca de bateria", secondCall.originalDemand)
        assertNotNull(secondCall.baseSnapshot)
        assertEquals("\"etag-1\"", secondCall.baseSnapshot?.etag)
    }

    @Test
    fun distinctRecurrenceIds_createDistinctServiceOrders() = runTest {
        val key1 = RemoteOccurrenceKey("acct-1", "/cal/1/", "/cal/1/series.ics", "20260826T100000Z")
        val key2 = RemoteOccurrenceKey("acct-1", "/cal/1/", "/cal/1/series.ics", "20260827T100000Z")

        val order1 = repository.createOrGetAttendance(
            key1, ServiceOrderPreset.SERVICO_SOLICITADO, "Ocorrência 1", "C1", "U1", null, null, null, null, null, null
        )
        val order2 = repository.createOrGetAttendance(
            key2, ServiceOrderPreset.SERVICO_SOLICITADO, "Ocorrência 2", "C1", "U1", null, null, null, null, null, null
        )

        org.junit.Assert.assertNotEquals(order1.id, order2.id)
        assertEquals(order1.id, repository.getLinkedOrder(key1)?.id)
        assertEquals(order2.id, repository.getLinkedOrder(key2)?.id)
    }

    @Test
    fun saveStructuredOrder_persistsUpdatesItemsAndVersions() = runTest {
        val orderId = UUID.randomUUID()
        val update1 = ServiceOrderUpdate(sequenceOrder = 1, text = "Chegada no local", executionDate = 1000L)
        val update2 = ServiceOrderUpdate(sequenceOrder = 2, text = "Bateria substituída", executionDate = 2000L)
        val item = ServiceOrderItem(
            action = "Substituição",
            itemType = "Bateria",
            brand = "Moura",
            model = "12V 7Ah",
            serialNumber = "SN12345"
        )
        val version = ServiceOrderVersion(
            versionNumber = 1,
            formattedDescription = "OS Concluída com sucesso",
            publishedEtag = "\"etag-v1\""
        )

        val structured = StructuredServiceOrder(
            id = orderId,
            title = "OS Estruturada",
            clientName = "Cliente A",
            unitName = "Unidade B",
            technician = "João",
            category = "Elétrica",
            preset = ServiceOrderPreset.DIAGNOSTICO_CORRECAO,
            originalDemand = "Sem energia",
            status = ServiceOrderStatus.CONCLUIDA,
            updates = listOf(update1, update2),
            items = listOf(item),
            closureCause = "Desgaste natural",
            closureSolution = "Troca realizada",
            closurePending = "Nenhuma",
            versions = listOf(version)
        )

        repository.saveStructuredOrder(structured)

        val retrieved = repository.getStructuredOrderById(orderId)
        assertNotNull(retrieved)
        assertEquals(2, retrieved?.updates?.size)
        assertEquals("Chegada no local", retrieved?.updates?.get(0)?.text)
        assertEquals("Bateria substituída", retrieved?.updates?.get(1)?.text)
        assertEquals(1, retrieved?.items?.size)
        assertEquals("SN12345", retrieved?.items?.get(0)?.serialNumber)
        assertEquals(1, retrieved?.versions?.size)
        assertEquals("OS Concluída com sucesso", retrieved?.versions?.get(0)?.formattedDescription)
        assertEquals("João", retrieved?.technician)
    }

    @Test
    fun saveStructuredOrder_persistsExplicitConclusionStatesAcrossReload() = runTest {
        listOf(
            ConclusionState.NAO_CONCLUIDO,
            ConclusionState.CONCLUIDO_COM_PENDENCIAS
        ).forEach { conclusionState ->
            val order = StructuredServiceOrder(
                id = UUID.randomUUID(),
                title = "OS com estado explícito",
                clientName = "Cliente",
                unitName = "Local",
                originalDemand = "Demanda",
                conclusionState = conclusionState
            )

            repository.saveStructuredOrder(order)

            assertEquals(
                conclusionState,
                repository.getStructuredOrderById(order.id)?.conclusionState
            )
        }
    }
}
