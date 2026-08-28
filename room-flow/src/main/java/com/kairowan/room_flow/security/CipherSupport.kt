package com.kairowan.room_flow.security

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper

/**
 * @author 浩楠
 *
 * @date 2025/8/24
 *
 *      _              _           _     _   ____  _             _ _
 *     / \   _ __   __| |_ __ ___ (_) __| | / ___|| |_ _   _  __| (_) ___
 *    / _ \ | '_ \ / _` | '__/ _ \| |/ _` | \___ \| __| | | |/ _` | |/ _ \
 *   / ___ \| | | | (_| | | | (_) | | (_| |  ___) | |_| |_| | (_| | | (_) |
 *  /_/   \_\_| |_|\__,_|_|  \___/|_|\__,_| |____/ \__|\__,_|\__,_|_|\___/
 * @Description: TODO SQLCipher 适配
 */
object CipherSupport {
    fun <T : RoomDatabase> applyFactory(
        builder: RoomDatabase.Builder<T>,
        factory: SupportSQLiteOpenHelper.Factory
    ): RoomDatabase.Builder<T> = builder.openHelperFactory(factory)

    fun rekey(db: SupportSQLiteDatabase, newKey: String) {
        require(newKey.isNotEmpty() && '\u0000' !in newKey) { "密钥不能为空或含 NUL" }
        check(!db.inTransaction()) { "密钥轮换不能在事务内执行" }
        db.query("PRAGMA cipher_version").use { cursor ->
            check(cursor.moveToFirst() && !cursor.getString(0).isNullOrBlank()) { "数据库不是 SQLCipher" }
        }
        // PRAGMA 不支持普通值占位符；仅做 SQLite 字符串字面量转义，绝不记录密钥。
        db.execSQL("PRAGMA rekey = '" + newKey.replace("'", "''") + "'")
    }
}
