package com.kairowan.room_flow.typed

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteQuery

/** Conditional partial update. Primary keys are immutable through this API. */
class EntityUpdate<E : Any> internal constructor(private val db: RoomDatabase, private val table: EntityTable<E>) {
    private val changes = linkedMapOf<EntityColumn<E, *>, Any?>()
    private var condition: SqlCondition<E>? = null
    private var allRowsAllowed = false

    fun <V> set(column: EntityColumn<E, V>, value: V): EntityUpdate<E> = apply {
        table.owns(column)
        require(column.keyPosition == 0) { "Primary key changes require an explicit migration/DAO transaction" }
        require(column !in changes) { "Duplicate update column" }
        require(value != null || column.nullable) { "NULL for non-null column" }
        changes[column] = snapshotValue(value)
    }

    fun where(value: SqlCondition<E>): EntityUpdate<E> = apply {
        table.owns(value)
        condition = condition?.and(value) ?: value
    }

    fun allRows(): EntityUpdate<E> = apply { allRowsAllowed = true }

    fun build(): SupportSQLiteQuery {
        require(changes.isNotEmpty()) { "No update values" }
        require(condition != null || allRowsAllowed) { "Unscoped update requires allRows()" }
        val sets = changes.keys.joinToString { "${it.quoted} = ?" }
        val where = condition?.let { " WHERE (${it.sql})" }.orEmpty()
        return table.query("UPDATE ${table.quoted} SET $sets$where", changes.values.toList() + condition?.args.orEmpty())
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
