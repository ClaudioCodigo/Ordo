package dev.claudiocodigo.nexo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.claudiocodigo.nexo.data.local.converters.Converters
import dev.claudiocodigo.nexo.data.local.dao.CalendarAccountDao
import dev.claudiocodigo.nexo.data.local.dao.CalendarDao
import dev.claudiocodigo.nexo.data.local.dao.CalendarSyncStateDao
import dev.claudiocodigo.nexo.data.local.dao.RemoteEventDao
import dev.claudiocodigo.nexo.data.local.dao.ServiceOrderDao
import dev.claudiocodigo.nexo.data.local.entity.CalendarAccountEntity
import dev.claudiocodigo.nexo.data.local.entity.CalendarEntity
import dev.claudiocodigo.nexo.data.local.entity.CalendarSyncStateEntity
import dev.claudiocodigo.nexo.data.local.entity.RemoteEventEntity
import dev.claudiocodigo.nexo.data.local.entity.ServiceOrderEntity

@Database(
    entities = [
        ServiceOrderEntity::class,
        CalendarAccountEntity::class,
        CalendarEntity::class,
        RemoteEventEntity::class,
        CalendarSyncStateEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class NexoDatabase : RoomDatabase() {
    abstract fun serviceOrderDao(): ServiceOrderDao
    abstract fun calendarAccountDao(): CalendarAccountDao
    abstract fun calendarDao(): CalendarDao
    abstract fun remoteEventDao(): RemoteEventDao
    abstract fun calendarSyncStateDao(): CalendarSyncStateDao

    companion object {
        const val DATABASE_NAME = "nexo_db"
    }
}
