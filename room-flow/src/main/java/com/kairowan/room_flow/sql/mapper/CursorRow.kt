package com.kairowan.room_flow.sql.mapper

import android.database.Cursor

/** 只在当前 Cursor 位置有效，不可保存后跨行读取。 */
class CursorRow(val cursor: Cursor, private val indexOf: Map<String, Int>) {
    fun idx(column: String): Int =
        indexOf[column.lowercase()] ?: error("列不存在: $column，可用列：${indexOf.keys}")

    inline fun <reified T> get(column: String): T =
        getOrNull<T>(column) ?: error("列 $column 的值为 NULL，但期望非空 ${T::class}")

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> getOrNull(column: String): T? {
        val i = idx(column)
        if (cursor.isNull(i)) return null
        return when (T::class) {
            Long::class -> cursor.getLong(i) as T
            Int::class -> cursor.getInt(i) as T
            Short::class -> cursor.getShort(i) as T
            Double::class -> cursor.getDouble(i) as T
            Float::class -> cursor.getFloat(i) as T
            String::class -> cursor.getString(i) as T
            ByteArray::class -> cursor.getBlob(i) as T
            Boolean::class -> (cursor.getInt(i) != 0) as T
            else -> error("不支持的类型 ${T::class}，列: $column")
        }
    }
}
