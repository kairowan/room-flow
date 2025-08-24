package com.kairowan.room_flow

import android.database.Cursor

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
 * @Description: TODO
 */
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

/** Cursor 的行包装与遍历助手。 */
class CursorRows(private val cursor: Cursor) : Iterable<CursorRow>, AutoCloseable {
    private val indexMap: Map<String, Int> = buildMap {
        for (i in 0 until cursor.columnCount) put(cursor.getColumnName(i).lowercase(), i)
    }

    override fun iterator(): Iterator<CursorRow> = object : Iterator<CursorRow> {
        override fun hasNext(): Boolean = cursor.moveToNext()
        override fun next(): CursorRow = CursorRow(cursor, indexMap)
    }

    override fun close() {
        cursor.close()
    }
}

/** 便捷扩展：将 Cursor 映射为列表。 */
inline fun <R> Cursor.mapRows(block: (CursorRow) -> R): List<R> =
    CursorRows(this).use { rows -> rows.map(block) }