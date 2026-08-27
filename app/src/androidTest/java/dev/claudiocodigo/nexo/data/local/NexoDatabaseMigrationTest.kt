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
 * Verifies the explicit Room migration 1 -> 2 against the real v1 schema.
 *
 * Requires an emulator/device. Validates that:
 * - the v1 `service_orders` table and its rows survive untouched;
 * - the new Fase 2 tables exist and are queryable after the migration;
 * - a `uid` collision across two different `href`s is preserved.
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
        // Create a v1 database and seed a draft exactly as Fase 1 would.
        helper.createDatabase(testDb, 1).use { db ->
            db.execSQL(
                "INSERT INTO service_orders " +
                    "(id, externalId, title, description, status, clientName, unitName, scheduledDate, createdAt, updatedAt) " +
                    "VALUES ('00000000-0000-0000-0000-000000000001', '15428', 'Manutenção', 'Descrição', 'EM_ANDAMENTO', 'Hospital X', 'U1', 1735689600000, 1000, 2000)"
            )
        }

        // Run the migration to v2.
        val db = helper.runMigrationsAndValidate(testDb, 2, true, NexoDatabaseMigrations.MIGRATION_1_2)

        // The draft must have survived byte-for-byte.
        db.query("SELECT * FROM service_orders WHERE id = '00000000-0000-0000-0000-000000000001'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("15428", cursor.getString(cursor.getColumnIndexOrThrow("externalId")))
            assertEquals("Manutenção", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        }

        // New remote tables must be queryable.
        db.execSQL(
            "INSERT INTO calendars (accountId, href, displayName, supportsVeEvent, hasWritePrivilege, isSelected, updatedAt) " +
                "VALUES ('acct-1', '/cal/1/', 'Trabalho', 1, 1, 1, 1000)"
        )
        db.query("SELECT displayName FROM calendars WHERE accountId = 'acct-1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Trabalho", c.getString(0))
        }

        // Two resources sharing the same uid but distinct hrefs must both persist.
        val ics = "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nUID:same\nEND:VEVENT\nEND:VCALENDAR"
        db.execSQL(
            "INSERT INTO remote_events (accountId, calendarHref, href, uid, rawIcs, allDay, color, lastSyncMillis) " +
                "VALUES ('acct-1', '/cal/1/', '/cal/1/e1.ics', 'same', '$ics', 0, 'NAO_CLASSIFICADO', 1000)"
        )
        db.execSQL(
            "INSERT INTO remote_events (accountId, calendarHref, href, uid, rawIcs, allDay, color, lastSyncMillis) " +
                "VALUES ('acct-1', '/cal/1/', '/cal/1/e2.ics', 'same', '$ics', 0, 'NAO_CLASSIFICADO', 1000)"
        )
        db.query("SELECT COUNT(*) FROM remote_events WHERE uid = 'same'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }

        assertNotNull(db)
        db.close()
    }
}
