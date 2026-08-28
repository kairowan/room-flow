package com.kairowan.room_flow.typed

import androidx.sqlite.db.SupportSQLiteQuery

/** Immutable query definition: no database, cursor or execution lifecycle is retained. */
class QuerySpec<E : Any> private constructor(
    internal val table: EntityTable<E>,
    private val condition: SqlCondition<E>?,
    private val orders: List<SqlOrder<E>>,
    private val pageSize: Int?,
    private val offset: Long
) {
    internal constructor(table: EntityTable<E>) : this(table, null, emptyList(), null, 0L)

    fun where(value: SqlCondition<E>): QuerySpec<E> {
        table.owns(value)
        return QuerySpec(table, condition?.and(value) ?: value, orders, pageSize, offset)
    }

    /** NULL skips the predicate; empty strings/collections are still meaningful values. */
    fun <V : Any> whereIfNotNull(value: V?, predicate: (V) -> SqlCondition<E>): QuerySpec<E> =
        if (value == null) this else where(predicate(value))

    fun orderBy(vararg values: SqlOrder<E>): QuerySpec<E> {
        values.forEach { table.owns(it.column) }
        require(values.map { it.column }.distinct().size == values.size) { "Duplicate sort column" }
        return QuerySpec(table, condition, values.toList(), pageSize, offset)
    }

    /** Returns a new 1-based page; the original definition is unchanged. */
    fun page(number: Int, size: Int): QuerySpec<E> {
        require(number >= 1 && size in 1..500) { "Page number >= 1, size in 1..500 required" }
        return QuerySpec(table, condition, orders, size, (number - 1L) * size)
    }

    fun unpaged(): QuerySpec<E> = QuerySpec(table, condition, orders, null, 0L)

    /** Lexicographic cursor over the configured order plus complete PK, using SQLite NULL ordering.
     * Pass null for the first page; derive each page from the original base definition.
     * Replaces OFFSET pagination. A cursor entity is not an authorization token or data snapshot.
     */
    fun seekAfter(entity: E?, size: Int): QuerySpec<E> {
        require(size in 1..500) { "Page size in 1..500 required" }
        if (entity == null) return page(1, size)
        val complete = completeOrder()
        var prefix: SqlCondition<E>? = null
        var after: SqlCondition<E>? = null
        for (order in complete) {
            val column = order.column
            val value = snapshotValue(column.getter(entity))
            val next = when {
                value == null && order.descending -> SqlCondition<E>(table, "0", emptyList())
                value == null -> column.isNotNull()
                else -> {
                    val comparison = SqlCondition(table,
                        "${column.quoted} ${if (order.descending) "<" else ">"} ?", listOf(value))
                    if (order.descending && column.nullable) comparison.or(column.isNull()) else comparison
                }
            }
            val branch = prefix?.and(next) ?: next
            after = after?.or(branch) ?: branch
            val equal = if (value == null) column.isNull() else SqlCondition(table, "${column.quoted} = ?", listOf(value))
            prefix = prefix?.and(equal) ?: equal
        }
        val scoped = where(requireNotNull(after) { "Cursor requires an ordered primary key" })
        return QuerySpec(table, scoped.condition, complete, size, 0L)
    }

    private fun completeOrder(): List<SqlOrder<E>> =
        orders + table.keys.filter { key -> orders.none { it.column === key } }.map { it.asc() }

    /** Independent bound query for inspection/raw APIs; direct execution bypasses typed validation. */
    fun build(): SupportSQLiteQuery = buildProjection(table.columns.joinToString { it.quoted })

    internal fun buildProjection(
        projection: String,
        firstOnly: Boolean = false,
        ordered: Boolean = true,
        count: Boolean = false
    ): SupportSQLiteQuery {
        val args = condition?.args?.toMutableList() ?: mutableListOf()
        val where = condition?.let { " WHERE (${it.sql})" }.orEmpty()
        val order = if (ordered) {
            completeOrder().joinToString(prefix = " ORDER BY ") { "${it.column.quoted} ${if (it.descending) "DESC" else "ASC"}" }
        } else ""
        val limit = (if (firstOnly) 1 else pageSize)?.let {
            args += it
            args += offset
            " LIMIT ? OFFSET ?"
        }.orEmpty()
        val sql = "SELECT $projection FROM ${table.quoted}$where$order$limit"
        // ponytail: one subquery preserves page cardinality, including offsets beyond the last row.
        // Aggregates wrap this input window; JOIN/HAVING need a separate typed model.
        return table.query(if (count) "SELECT COUNT(*) FROM ($sql)" else sql, args)
    }
}

/** Start a reusable definition from generated metadata without acquiring a database. */
fun <E : Any> EntityTable<E>.query(): QuerySpec<E> = QuerySpec(this)
