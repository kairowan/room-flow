package com.kairowan.room_flow.sql.mapper

import android.database.Cursor

/** 将 Cursor 映射为列表，并关闭 Cursor。 */
inline fun <R> Cursor.mapRows(block: (CursorRow) -> R): List<R> =
    CursorRows(this).use { rows -> rows.map(block) }
