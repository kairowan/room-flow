package com.kairowan.room_flow.sql.update

import androidx.sqlite.db.SimpleSQLiteQuery
import com.kairowan.room_flow.sql.quoteIdentifier

/** 绑定更新值并要求明确的 WHERE 或全表更新许可。 */
class UpdateBuilder internal constructor(private val table: String) {
    private val sets = mutableListOf<Pair<String, Any?>>()
    private var whereClause: String? = null
    private val whereArgs = mutableListOf<Any?>()
    private var allowAllRows = false

    fun set(vararg pairs: Pair<String, Any?>): UpdateBuilder = apply { sets += pairs }

    fun set(map: Map<String, Any?>): UpdateBuilder = apply { sets += map.toList() }

    fun where(clause: String, vararg args: Any?): UpdateBuilder = apply {
        require(clause.isNotBlank()) { "WHERE 不能为空" }
        this.whereClause = clause
        this.whereArgs.clear()
        this.whereArgs.addAll(args.toList())
    }

    /** 显式允许不带 WHERE 的全表更新。 */
    fun allRows(): UpdateBuilder = apply { allowAllRows = true }

    internal fun toQuery(): SimpleSQLiteQuery {
        require(sets.isNotEmpty()) { "未设置任何列，无法执行 update()" }
        require(whereClause != null || allowAllRows) { "全表更新必须显式调用 allRows()" }
        require(sets.map { it.first.lowercase() }.distinct().size == sets.size) { "不能重复设置同一列" }

        val capacity = sets.size + whereArgs.size
        val sb = StringBuilder("UPDATE ").append(quoteIdentifier(table)).append(" SET ")
        val bind = ArrayList<Any?>(capacity)

        sets.forEachIndexed { idx, (col, value) ->
            if (idx > 0) sb.append(", ")
            sb.append(quoteIdentifier(col)).append(" = ?")
            bind += value
        }

        whereClause?.let { sb.append(" WHERE ").append(it) }
        bind.addAll(whereArgs)

        return SimpleSQLiteQuery(sb.toString(), bind.toTypedArray())
    }
}
