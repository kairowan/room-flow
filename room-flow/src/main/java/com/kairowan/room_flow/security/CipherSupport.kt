package com.kairowan.room_flow.security

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.kairowan.room_flow.core.Trace

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
        try {
            db.execSQL("PRAGMA rekey = '$newKey';")
        } catch (t: Throwable) {
            Trace.e("RoomFlow", "SQLCipher rekey 失败", t)
        }
    }
}