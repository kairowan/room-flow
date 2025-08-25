package com.kairowan.room_flow

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File


/**
 * @author 浩楠
 * @date 2025/8/25
 *      _              _           _     _   ____  _             _ _
 *     / \   _ __   __| |_ __ ___ (_) __| | / ___|| |_ _   _  __| (_) ___
 *    / _ \ | '_ \ / _` | '__/ _ \| |/ _` | \___ \| __| | | |/ _` | |/ _ \
 *   / ___ \| | | | (_| | | | (_) | | (_| |  ___) | |_| |_| | (_| | | (_) |
 *  /_/   \_\_| |_|\__,_|_|  \___/|_|\__,_| |____/ \__|\__,_|\__,_|_|\___/
 *  描述: TODO  Room 构建器
 */
object SelfHealingRoom {

    /**
     * @param configure 传入一个 lambda 来配置 builder（addMigrations / callbacks / factories 等）
     */
    fun <T : RoomDatabase> build(
        context: Context,
        klass: Class<T>,
        name: String,
        configure: (RoomDatabase.Builder<T>) -> RoomDatabase.Builder<T> = { it }
    ): T {
        val builder = configure(Room.databaseBuilder(context.applicationContext, klass, name))
        try {
            val db = builder.build()
            db.openHelper.writableDatabase
            return db
        } catch (e: IllegalStateException) {
            if (e.message?.contains("Room cannot verify the data integrity") == true) {
                deleteDbFiles(context, name)
                val fresh = configure(Room.databaseBuilder(context.applicationContext, klass, name)).build()
                fresh.openHelper.writableDatabase
                return fresh
            }
            throw e
        } catch (e: SQLiteException) {
            deleteDbFiles(context, name)
            val fresh = configure(Room.databaseBuilder(context.applicationContext, klass, name)).build()
            fresh.openHelper.writableDatabase
            return fresh
        }
    }

    private fun deleteDbFiles(context: Context, name: String) {
        runCatching { context.deleteDatabase(name) }
        val base = context.getDatabasePath(name)
        listOf(base.path, "${base.path}-wal", "${base.path}-shm").forEach { path ->
            runCatching { File(path).delete() }
        }
    }
}