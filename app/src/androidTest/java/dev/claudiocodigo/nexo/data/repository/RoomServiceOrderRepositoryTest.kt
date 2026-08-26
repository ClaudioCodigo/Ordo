package dev.claudiocodigo.nexo.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.claudiocodigo.nexo.data.local.NexoDatabase
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
        repository = RoomServiceOrderRepository(database.serviceOrderDao())
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
}
