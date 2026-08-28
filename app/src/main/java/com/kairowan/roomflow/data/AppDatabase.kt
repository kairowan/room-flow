package com.kairowan.roomflow.data

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.kairowan.room_flow.SelfHealingRoom


/**
 * @author 浩楠
 * @date 2025/8/25
 *      _              _           _     _   ____  _             _ _
 *     / \   _ __   __| |_ __ ___ (_) __| | / ___|| |_ _   _  __| (_) ___
 *    / _ \ | '_ \ / _` | '__/ _ \| |/ _` | \___ \| __| | | |/ _` | |/ _ \
 *   / ___ \| | | | (_| | | | (_) | | (_| |  ___) | |_| |_| | (_| | | (_) |
 *  /_/   \_\_| |_|\__,_|_|  \___/|_|\__,_| |____/ \__|\__,_|\__,_|_|\___/
 *  描述: TODO
 */
@Database(
    entities = [User::class],
    version = AppDatabase.DB_VERSION,
    exportSchema = true, // 可以切换到 AutoMigration
    autoMigrations = []
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    companion object {
        const val DB_VERSION = 1

        @Suppress("unused")
        private val MIGRATIONS: Array<Migration> = emptyArray()

        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SelfHealingRoom.build(
                    context = context,
                    klass = AppDatabase::class.java,
                    name = "app.db"
                ) { builder ->
                    builder
                        .addMigrations(*MIGRATIONS) // 结构变更必须升版本并提供 Migration/AutoMigration。
                }.also { INSTANCE = it }
            }
    }
}
