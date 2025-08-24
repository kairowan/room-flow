package com.kairowan.room_flow

import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery

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
 * @Description: TODO 安全地构建并执行部分字段更新（UPDATE ... SET ... WHERE ...）。
 */
class UpdateBuilder internal constructor(private val table: String) {
    private val sets = mutableListOf<Pair<String, Any?>>()
    private var whereClause: String? = null
    private val whereArgs = mutableListOf<Any?>()

    fun set(vararg pairs: Pair<String, Any?>): UpdateBuilder = apply { sets += pairs }

    fun set(map: Map<String, Any?>): UpdateBuilder = apply { sets += map.toList() }

    fun where(clause: String, vararg args: Any?): UpdateBuilder = apply {
        this.whereClause = clause
        this.whereArgs.clear()
        this.whereArgs.addAll(args.toList())
    }

    internal fun toQuery(): SimpleSQLiteQuery {
        require(sets.isNotEmpty()) { "未设置任何列，无法执行 update()" }

        val capacity = sets.size + whereArgs.size
        val sb = StringBuilder("UPDATE ").append(table).append(" SET ")
        val bind = ArrayList<Any?>(capacity)

        sets.forEachIndexed { idx, (col, value) ->
            if (idx > 0) sb.append(", ")
            sb.append(col).append(" = ?")
            bind += value
        }

        whereClause?.let { sb.append(" WHERE ").append(it) }
        bind.addAll(whereArgs)

        return SimpleSQLiteQuery(sb.toString(), bind.toTypedArray())
    }
}

fun RoomDatabase.update(table: String, block: UpdateBuilder.() -> Unit): Int {
    val b = UpdateBuilder(table).apply(block)
    val q = b.toQuery()
    val db = openHelper.writableDatabase
    val st = db.compileStatement(q.sql)
    try {
        q.bindTo(st)
        return st.executeUpdateDelete()
    } finally {
        st.close()
    }
}