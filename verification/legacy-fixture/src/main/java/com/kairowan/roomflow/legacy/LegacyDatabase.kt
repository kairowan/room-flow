package com.kairowan.roomflow.legacy

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [LegacyUser::class, LegacyRecord::class], version = 1, exportSchema = true)
abstract class LegacyDatabase : RoomDatabase() {
    abstract fun legacyDao(): LegacyDao
}
