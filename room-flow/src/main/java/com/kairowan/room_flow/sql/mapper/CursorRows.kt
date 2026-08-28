package com.kairowan.room_flow.sql.mapper

import android.database.Cursor

/** 包装 Cursor 行迭代；使用后由调用方关闭。 */
class CursorRows(private val cursor: Cursor) : Iterable<CursorRow>, AutoCloseable {
    private val indexMap: Map<String, Int> = buildMap {
        for (i in 0 until cursor.columnCount) put(cursor.getColumnName(i).lowercase(), i)
    }

    override fun iterator(): Iterator<CursorRow> = object : Iterator<CursorRow> {
        private var ready = false
        private var exhausted = false

        override fun hasNext(): Boolean {
            if (!ready && !exhausted) {
                ready = cursor.moveToNext()
                exhausted = !ready
            }
            return ready
        }

        override fun next(): CursorRow {
            if (!hasNext()) throw NoSuchElementException()
            ready = false
            return CursorRow(cursor, indexMap)
        }
    }

    override fun close() {
        cursor.close()
    }
}
