package dev.claudiocodigo.nexo.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.claudiocodigo.nexo.data.local.NexoDatabase
import dev.claudiocodigo.nexo.data.local.entity.ServiceOrderEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ServiceOrderDaoTest {
    private lateinit var database: NexoDatabase
    private lateinit var dao: ServiceOrderDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NexoDatabase::class.java).build()
        dao = database.serviceOrderDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun upsertKeepsOneRecordAndUpdatesIt() = runTest {
        val id = UUID.randomUUID()
        val first = entity(id, title = "Primeiro")
        val second = first.copy(title = "Atualizado")

        dao.upsertServiceOrder(first)
        dao.upsertServiceOrder(second)

        val stored = dao.getServiceOrderById(id)
        assertNotNull(stored)
        assertEquals("Atualizado", stored?.title)
        assertEquals(1, dao.getAllServiceOrders().first().size)
    }

    private fun entity(id: UUID, title: String) = ServiceOrderEntity(
        id = id,
        externalId = null,
        title = title,
        description = "",
        status = "PENDENTE",
        clientName = "Cliente",
        unitName = "Unidade",
        scheduledDate = null,
        createdAt = 1L,
        updatedAt = 1L
    )
}
