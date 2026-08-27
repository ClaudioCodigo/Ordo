package dev.claudiocodigo.nexo.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit, non-destructive migrations for the Nexo database.
 *
 * Migration 1 -> 2 only ADDS the Fase 2 tables (account, calendar, remote
 * event mirror and sync state). The existing `service_orders` table and all
 * local drafts are left untouched, and no migration ever deletes data.
 */
object NexoDatabaseMigrations {

    /** SQL applied by the 1 -> 2 migration. Exposed for the schema-consistency test. */
    val MIGRATION_1_2_SQL: List<String> = listOf(
        // calendar_accounts
        "CREATE TABLE IF NOT EXISTS `calendar_accounts` (" +
            "`id` TEXT NOT NULL, `server` TEXT NOT NULL, `user` TEXT NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        // calendars
        "CREATE TABLE IF NOT EXISTS `calendars` (" +
            "`accountId` TEXT NOT NULL, `href` TEXT NOT NULL, `displayName` TEXT, " +
            "`description` TEXT, `color` TEXT, `supportsVeEvent` INTEGER NOT NULL, " +
            "`hasWritePrivilege` INTEGER NOT NULL, `syncToken` TEXT, " +
            "`isSelected` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
            "PRIMARY KEY(`accountId`, `href`))",
        "CREATE INDEX IF NOT EXISTS `index_calendars_accountId` ON `calendars` (`accountId`)",
        // remote_events
        "CREATE TABLE IF NOT EXISTS `remote_events` (" +
            "`accountId` TEXT NOT NULL, `calendarHref` TEXT NOT NULL, `href` TEXT NOT NULL, " +
            "`uid` TEXT NOT NULL, `etag` TEXT, `sequence` INTEGER, `rawIcs` TEXT NOT NULL, " +
            "`summary` TEXT, `description` TEXT, `location` TEXT, `start` INTEGER, `end` INTEGER, " +
            "`allDay` INTEGER NOT NULL, `color` TEXT NOT NULL, `rawEventColor` TEXT, " +
            "`timeZone` TEXT, `recurrenceText` TEXT, `lastModified` INTEGER, " +
            "`lastSyncMillis` INTEGER NOT NULL, PRIMARY KEY(`accountId`, `calendarHref`, `href`))",
        "CREATE INDEX IF NOT EXISTS `index_remote_events_uid` ON `remote_events` (`uid`)",
        "CREATE INDEX IF NOT EXISTS `index_remote_events_start` ON `remote_events` (`start`)",
        "CREATE INDEX IF NOT EXISTS `index_remote_events_calendarHref` ON `remote_events` (`calendarHref`)",
        // calendar_sync_state
        "CREATE TABLE IF NOT EXISTS `calendar_sync_state` (" +
            "`accountId` TEXT NOT NULL, `calendarHref` TEXT NOT NULL, " +
            "`lastSyncMillis` INTEGER NOT NULL, `lastSuccessMillis` INTEGER, " +
            "`lastResult` TEXT, `lastErrorMessage` TEXT, `syncToken` TEXT, " +
            "PRIMARY KEY(`accountId`, `calendarHref`))"
    )

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_1_2_SQL.forEach { db.execSQL(it) }
        }
    }
}
