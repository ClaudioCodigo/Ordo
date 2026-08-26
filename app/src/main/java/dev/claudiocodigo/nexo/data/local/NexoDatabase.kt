package dev.claudiocodigo.nexo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.claudiocodigo.nexo.data.local.converters.Converters
import dev.claudiocodigo.nexo.data.local.dao.ServiceOrderDao
import dev.claudiocodigo.nexo.data.local.entity.ServiceOrderEntity

@Database(entities = [ServiceOrderEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class NexoDatabase : RoomDatabase() {
    abstract fun serviceOrderDao(): ServiceOrderDao

    companion object {
        const val DATABASE_NAME = "nexo_db"
    }
}
