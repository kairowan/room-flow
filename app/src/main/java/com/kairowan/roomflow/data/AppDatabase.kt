package com.kairowan.roomflow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kairowan.room_flow.SelfHealingRoom
import com.kairowan.room_flow.core.tunePragmas


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
                        .addMigrations(*MIGRATIONS) // 升级数据库想保存数据就用这个，如果不想保存数据直接注视这行代码就好了
                        .fallbackToDestructiveMigration()
                        .fallbackToDestructiveMigrationOnDowngrade()
                        .addCallback(object : Callback() {
                            override fun onOpen(db: SupportSQLiteDatabase) = tunePragmas(db)
                        })
                }.also { INSTANCE = it }
            }
    }
}