package com.turntable.app.data.repository

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.turntable.app.data.model.*

@Database(
    entities = [
        TurntableEntity::class,
        TurntableSegmentEntity::class,
        TurntableFlowEntity::class,
        TurntableFlowStageEntity::class,
        TurntableSessionEntity::class,
        TurntableSpinRecordEntity::class,
        TurntableBoxEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class TurntableDatabase : RoomDatabase() {
    abstract fun dao(): TurntableDao

    companion object {
        @Volatile
        private var INSTANCE: TurntableDatabase? = null

        fun getInstance(context: Context): TurntableDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TurntableDatabase::class.java,
                    "turntable_db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
