package dev.claudiocodigo.nexo.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit, non-destructive migrations for the Nexo database.
 *
 * Migration 1 -> 2 adds the Fase 2 tables (account, calendar, remote event mirror and sync state).
 * Migration 2 -> 3 adds the Fase 3 structured service order tables and additive columns.
 *
 * Existing rows and local drafts are preserved byte-for-byte; no migration is destructive.
 */
object NexoDatabaseMigrations {

    /** SQL applied by the 1 -> 2 migration. */
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

    /** SQL applied by the 2 -> 3 migration. */
    val MIGRATION_2_3_SQL: List<String> = listOf(
        // service_orders additive columns
        "ALTER TABLE `service_orders` ADD COLUMN `technician` TEXT",
        "ALTER TABLE `service_orders` ADD COLUMN `category` TEXT",
        "ALTER TABLE `service_orders` ADD COLUMN `preset` TEXT NOT NULL DEFAULT 'DIAGNOSTICO_CORRECAO'",
        "ALTER TABLE `service_orders` ADD COLUMN `originalDemand` TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE `service_orders` ADD COLUMN `publicationState` TEXT NOT NULL DEFAULT 'LOCAL_DRAFT'",
        "ALTER TABLE `service_orders` ADD COLUMN `closureCause` TEXT",
        "ALTER TABLE `service_orders` ADD COLUMN `closureSolution` TEXT",
        "ALTER TABLE `service_orders` ADD COLUMN `closurePending` TEXT",
        "ALTER TABLE `service_orders` ADD COLUMN `sequence` INTEGER",
        "ALTER TABLE `service_orders` ADD COLUMN `scheduledStart` INTEGER",
        "ALTER TABLE `service_orders` ADD COLUMN `scheduledEnd` INTEGER",

        // service_order_links
        "CREATE TABLE IF NOT EXISTS `service_order_links` (" +
            "`accountId` TEXT NOT NULL, `calendarHref` TEXT NOT NULL, `eventHref` TEXT NOT NULL, " +
            "`recurrenceId` TEXT NOT NULL, `orderId` TEXT NOT NULL, `linkedAt` INTEGER NOT NULL, " +
            "PRIMARY KEY(`accountId`, `calendarHref`, `eventHref`, `recurrenceId`))",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_service_order_links_orderId` ON `service_order_links` (`orderId`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_service_order_links_accountId_calendarHref_eventHref_recurrenceId` ON `service_order_links` (`accountId`, `calendarHref`, `eventHref`, `recurrenceId`)",

        // service_order_snapshots
        "CREATE TABLE IF NOT EXISTS `service_order_snapshots` (" +
            "`id` TEXT NOT NULL, `orderId` TEXT NOT NULL, `etag` TEXT, `rawIcs` TEXT NOT NULL, " +
            "`rawSummary` TEXT, `rawDescription` TEXT, `capturedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_service_order_snapshots_orderId` ON `service_order_snapshots` (`orderId`)",

        // service_order_updates
        "CREATE TABLE IF NOT EXISTS `service_order_updates` (" +
            "`id` TEXT NOT NULL, `orderId` TEXT NOT NULL, `sequenceOrder` INTEGER NOT NULL, " +
            "`text` TEXT NOT NULL, `executionDate` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_service_order_updates_orderId` ON `service_order_updates` (`orderId`)",
        "CREATE INDEX IF NOT EXISTS `index_service_order_updates_orderId_sequenceOrder` ON `service_order_updates` (`orderId`, `sequenceOrder`)",

        // service_order_items
        "CREATE TABLE IF NOT EXISTS `service_order_items` (" +
            "`id` TEXT NOT NULL, `orderId` TEXT NOT NULL, `action` TEXT NOT NULL, `itemType` TEXT NOT NULL, " +
            "`brand` TEXT, `model` TEXT, `serialNumber` TEXT, `relatedEquipment` TEXT, `location` TEXT, " +
            "`notes` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_service_order_items_orderId` ON `service_order_items` (`orderId`)",

        // service_order_versions
        "CREATE TABLE IF NOT EXISTS `service_order_versions` (" +
            "`id` TEXT NOT NULL, `orderId` TEXT NOT NULL, `versionNumber` INTEGER NOT NULL, " +
            "`formattedDescription` TEXT NOT NULL, `publishedEtag` TEXT, `publishedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_service_order_versions_orderId` ON `service_order_versions` (`orderId`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_service_order_versions_orderId_versionNumber` ON `service_order_versions` (`orderId`, `versionNumber`)",

        // publication_outbox
        "CREATE TABLE IF NOT EXISTS `publication_outbox` (" +
            "`id` TEXT NOT NULL, `orderId` TEXT NOT NULL, `action` TEXT NOT NULL, `payloadIcs` TEXT NOT NULL, " +
            "`ifMatchEtag` TEXT, `status` TEXT NOT NULL, `lastError` TEXT, `retryCount` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_publication_outbox_orderId` ON `publication_outbox` (`orderId`)",
        "CREATE INDEX IF NOT EXISTS `index_publication_outbox_status` ON `publication_outbox` (`status`)"
    )

    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_2_3_SQL.forEach { db.execSQL(it) }
        }
    }
}
