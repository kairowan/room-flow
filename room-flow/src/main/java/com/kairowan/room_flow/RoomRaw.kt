package com.kairowan.room_flow

import android.database.Cursor
import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

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
 * @Description: TODO 原生 SQL 工具：
 */
/** 执行任意 SQL（写操作）；可带绑定参数。 */
fun RoomDatabase.execSQL(sql: String, args: List<Any?> = emptyList()) =
    this.openHelper.writableDatabase.execSQL(sql, args.toTypedArray())

/** 执行原生查询（读）。 */
fun RoomDatabase.rawQuery(query: SupportSQLiteQuery): Cursor =
    this.openHelper.readableDatabase.query(query)

/** 执行原生查询（读，基于 SQL 字符串与参数）。 */
fun RoomDatabase.rawQuery(sql: String, args: List<Any?> = emptyList()): Cursor =
    rawQuery(SimpleSQLiteQuery(sql, args.toTypedArray()))

/** 将 Cursor 映射为列表；调用方负责关闭 Cursor。 */
inline fun <T> Cursor.mapList(mapper: (Cursor) -> T): List<T> {
    val out = ArrayList<T>(count.coerceAtLeast(0))
    while (moveToNext()) out += mapper(this)
    return out
}

/**
 * 原生 SQL 的 Flow：当 [tables] 任一表失效时，重新执行 SQL 并映射为 List<T>。
 * @param mapper 从 Cursor 逐行映射为 T 的函数。
 */
fun <T> RoomDatabase.rawQueryFlow(
    sql: String,
    args: List<Any?> = emptyList(),
    vararg tables: String,
    mapper: (Cursor) -> T
): Flow<List<T>> = flowQuery(*tables) {
    rawQuery(sql, args).use { c -> c.mapList(mapper) }
}