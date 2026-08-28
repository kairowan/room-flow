package com.kairowan.room_flow.typed

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteQuery

class EntityDelete<E : Any> internal constructor(private val db: RoomDatabase, private val table: EntityTable<E>) {
    private var condition: SqlCondition<E>? = null
    private var allRowsAllowed = false

    fun where(value: SqlCondition<E>): EntityDelete<E> = apply {
        table.owns(value)
        condition = condition?.and(value) ?: value
    }

    fun allRows(): EntityDelete<E> = apply { allRowsAllowed = true }

    fun build(): SupportSQLiteQuery {
        require(condition != null || allRowsAllowed) { "Unscoped delete requires allRows()" }
        val where = condition?.let { " WHERE (${it.sql})" }.orEmpty()
        return table.query("DELETE FROM ${table.quoted}$where", condition?.args.orEmpty())
    }

    suspend fun execute(): Int = db.executeTyped(table, build(), insert = false).toInt()

    /** Roll back if direct affected rows exceed the limit; excludes trigger/cascade row counts.
     * This is not SQL LIMIT; build()/raw execution bypasses this execution-time guard.
     */
    suspend fun execute(maxAffectedRows: Int): Int {
        require(maxAffectedRows >= 0) { "maxAffectedRows must be non-negative" }
        return db.executeTyped(table, build(), insert = false, maxAffectedRows = maxAffectedRows).toInt()
    }
}
