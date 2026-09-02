package dev.claudiocodigo.nexo.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Verifies explicit Room migrations through schema v4 against real schemas.
 *
 * Validates that:
 * - the v1 `service_orders` table and its rows survive untouched through 1 -> 2 and 2 -> 3;
 * - the v2 remote mirror tables survive 2 -> 3 untouched;
 * - the new Phase 3 tables and additive columns exist and are queryable.
 */
@RunWith(AndroidJUnit4::class)
class NexoDatabaseMigrationTest {

    private val testDb = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NexoDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2_preservesDraftsAndCreatesRemoteTables() {
        helper.createDatabase(testDb, 1).use { db ->
            db.execSQL(
                "INSERT INTO service_orders " +
                    "(id, externalId, title, description, status, clientName, unitName, scheduledDate, createdAt, updatedAt) " +
                    "VALUES ('00000000-0000-0000-0000-000000000001', '15428', 'Manutenção', 'Descrição', 'EM_ANDAMENTO', 'Hospital X', 'U1', 1735689600000, 1000, 2000)"
            )
        }

        val db = helper.runMigrationsAndValidate(testDb, 2, true, NexoDatabaseMigrations.MIGRATION_1_2)

        db.query("SELECT * FROM service_orders WHERE id = '00000000-0000-0000-0000-000000000001'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("15428", cursor.getString(cursor.getColumnIndexOrThrow("externalId")))
            assertEquals("Manutenção", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        }

        assertNotNull(db)
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3_preservesExistingDataAndAddsPhase3Tables() {
        helper.createDatabase(testDb, 2).use { db ->
            db.execSQL(
                "INSERT INTO service_orders " +
                    "(id, externalId, title, description, status, clientName, unitName, scheduledDate, createdAt, updatedAt) " +
                    "VALUES ('00000000-0000-0000-0000-000000000002', '15429', 'Troca Bateria', 'Demanda', 'PENDENTE', 'Hospital Y', 'U2', 1735689600000, 1000, 2000)"
            )
            db.execSQL(
                "INSERT INTO calendar_accounts (id, server, user, createdAt, updatedAt) " +
                    "VALUES ('acct-1', 'https://cloud.example.com', 'maria', 1000, 2000)"
            )
            db.execSQL(
                "INSERT INTO calendars (accountId, href, displayName, supportsVeEvent, hasWritePrivilege, isSelected, updatedAt) " +
                    "VALUES ('acct-1', '/cal/1/', 'Trabalho', 1, 1, 1, 1000)"
            )
        }

        val db = helper.runMigrationsAndValidate(testDb, 3, true, NexoDatabaseMigrations.MIGRATION_2_3)

        // Seeded service order survives with default values for new columns
        db.query("SELECT * FROM service_orders WHERE id = '00000000-0000-0000-0000-000000000002'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("15429", cursor.getString(cursor.getColumnIndexOrThrow("externalId")))
            assertEquals("DIAGNOSTICO_CORRECAO", cursor.getString(cursor.getColumnIndexOrThrow("preset")))
            assertEquals("LOCAL_DRAFT", cursor.getString(cursor.getColumnIndexOrThrow("publicationState")))
        }

        // Remote account and calendar survive
        db.query("SELECT server FROM calendar_accounts WHERE id = 'acct-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("https://cloud.example.com", cursor.getString(0))
        }

        // New Phase 3 tables are queryable and insertable
        db.execSQL(
            "INSERT INTO service_order_links (accountId, calendarHref, eventHref, recurrenceId, orderId, linkedAt) " +
                "VALUES ('acct-1', '/cal/1/', '/cal/1/e1.ics', '', '00000000-0000-0000-0000-000000000002', 1000)"
        )
        db.query("SELECT orderId FROM service_order_links WHERE eventHref = '/cal/1/e1.ics'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("00000000-0000-0000-0000-000000000002", cursor.getString(0))
        }

        assertNotNull(db)
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate1To3_composedPreservesData() {
        helper.createDatabase(testDb, 1).use { db ->
            db.execSQL(
                "INSERT INTO service_orders " +
                    "(id, externalId, title, description, status, clientName, unitName, scheduledDate, createdAt, updatedAt) " +
                    "VALUES ('00000000-0000-0000-0000-000000000003', '15430', 'Instalação', 'Desc', 'CONCLUIDA', 'Cliente Z', 'U3', 1735689600000, 1000, 2000)"
            )
        }

        val db = helper.runMigrationsAndValidate(
            testDb,
            3,
            true,
            NexoDatabaseMigrations.MIGRATION_1_2,
            NexoDatabaseMigrations.MIGRATION_2_3
        )

        db.query("SELECT * FROM service_orders WHERE id = '00000000-0000-0000-0000-000000000003'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("15430", cursor.getString(cursor.getColumnIndexOrThrow("externalId")))
            assertEquals("CONCLUIDA", cursor.getString(cursor.getColumnIndexOrThrow("status")))
        }

        assertNotNull(db)
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate3To4_preservesDraftAndAddsNonFinalDefault() {
        helper.createDatabase(testDb, 3).use { db ->
            db.execSQL(
                "INSERT INTO service_orders " +
                    "(id, externalId, title, description, status, clientName, unitName, scheduledDate, createdAt, updatedAt, preset, originalDemand, publicationState) " +
                    "VALUES ('00000000-0000-0000-0000-000000000004', '15431', 'Retorno', 'Demanda', 'EM_ANDAMENTO', 'Cliente', 'Local', NULL, 1000, 2000, 'DIAGNOSTICO_CORRECAO', 'Demanda', 'LOCAL_DRAFT')"
            )
        }

        val db = helper.runMigrationsAndValidate(testDb, 4, true, NexoDatabaseMigrations.MIGRATION_3_4)

        db.query("SELECT title, status, conclusionState FROM service_orders WHERE id = '00000000-0000-0000-0000-000000000004'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Retorno", cursor.getString(0))
            assertEquals("EM_ANDAMENTO", cursor.getString(1))
            assertEquals("NAO_DEFINIDO", cursor.getString(2))
        }
        db.close()
    }
}
