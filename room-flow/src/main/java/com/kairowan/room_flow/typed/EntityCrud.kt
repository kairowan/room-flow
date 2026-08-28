package com.kairowan.room_flow.typed

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import com.kairowan.room_flow.crud.withTransactionRetry
import com.kairowan.room_flow.metrics.RoomFlowMetrics

fun <E : Any> RoomDatabase.select(table: EntityTable<E>): EntitySelect<E> = EntitySelect(this, table)
fun <E : Any> RoomDatabase.select(spec: QuerySpec<E>): EntitySelect<E> = EntitySelect(this, spec)
fun <E : Any> RoomDatabase.update(table: EntityTable<E>): EntityUpdate<E> = EntityUpdate(this, table)
fun <E : Any> RoomDatabase.delete(table: EntityTable<E>): EntityDelete<E> = EntityDelete(this, table)

/** INSERT with ABORT semantics; never REPLACE. Zero auto-generated Int/Long keys are omitted. */
suspend fun <E : Any> RoomDatabase.insert(table: EntityTable<E>, entity: E): Long {
    val entries = table.columns.mapNotNull { column ->
        val value = column.getter(entity)
        if (column.autoGenerate && (value == 0L || value == 0)) null else column to snapshotValue(value)
    }
    val sql = if (entries.isEmpty()) "INSERT INTO ${table.quoted} DEFAULT VALUES" else {
        "INSERT INTO ${table.quoted} (${entries.joinToString { it.first.quoted }}) VALUES (${entries.joinToString { "?" }})"
    }
    return executeTyped(table, table.query(sql, entries.map { it.second }), insert = true)
}

/** Update every non-key persisted field by the complete primary key, preserving child rows. */
suspend fun <E : Any> RoomDatabase.update(table: EntityTable<E>, entity: E): Int {
    val values = table.columns.filter { it.keyPosition == 0 }
    require(values.isNotEmpty()) { "Entity has no non-key fields to update" }
    val key = table.entityKey(entity)
    val query = table.query(
        "UPDATE ${table.quoted} SET ${values.joinToString { "${it.quoted} = ?" }} WHERE (${key.sql})",
        values.map { it.getter(entity) } + key.args
    )
    return executeTyped(table, query, insert = false).toInt()
}

suspend fun <E : Any> RoomDatabase.delete(table: EntityTable<E>, entity: E): Int =
    delete(table).where(table.entityKey(entity)).execute()

private fun <E : Any> EntityTable<E>.entityKey(entity: E): SqlCondition<E> {
    require(keys.isNotEmpty()) { "Missing primary key" }
    val args = keys.map {
        val value = requireNotNull(it.getter(entity)) { "NULL primary key" }
        require(!it.autoGenerate || (value != 0L && value != 0)) { "Entity has no assigned primary key" }
        snapshotValue(value)
    }
    return SqlCondition(this, keys.joinToString(" AND ") { "${it.quoted} = ?" }, args)
}

internal suspend fun <E : Any> RoomDatabase.executeTyped(
    table: EntityTable<E>,
    query: SupportSQLiteQuery,
    insert: Boolean,
    maxAffectedRows: Int? = null
): Long =
    withTransactionRetry {
        require(maxAffectedRows == null || maxAffectedRows >= 0) { "maxAffectedRows must be non-negative" }
        table.validate(this@executeTyped)
        openHelper.writableDatabase.compileStatement(query.sql).use { statement ->
            query.bindTo(statement)
            val result = if (insert) statement.executeInsert() else statement.executeUpdateDelete().toLong()
            check(maxAffectedRows == null || result <= maxAffectedRows) {
                "Write exceeded maxAffectedRows=$maxAffectedRows (affected=$result); transaction rolled back"
            }
            RoomFlowMetrics.sampleSql(query.sql)
            result
        }
    }
