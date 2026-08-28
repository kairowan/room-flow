package com.kairowan.room_flow.typed

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteQuery
import com.kairowan.room_flow.flow.observeTables
import com.kairowan.room_flow.sql.rawQueryEach
import com.kairowan.room_flow.sql.rawQueryList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Single-owner builder: build before sharing; repeated where calls AND their conditions. */
class EntitySelect<E : Any> internal constructor(private val db: RoomDatabase, private val table: EntityTable<E>) {
    private var spec = QuerySpec(table)

    internal constructor(db: RoomDatabase, spec: QuerySpec<E>) : this(db, spec.table) {
        this.spec = spec
    }

    fun where(value: SqlCondition<E>): EntitySelect<E> = apply {
        spec = spec.where(value)
    }

    fun orderBy(vararg values: SqlOrder<E>): EntitySelect<E> = apply {
        spec = spec.orderBy(*values)
    }

    /** Optional, 1-based pagination. Unique primary-key columns are appended to make ties deterministic. */
    fun page(number: Int, size: Int): EntitySelect<E> = apply {
        spec = spec.page(number, size)
    }

    fun build(): SupportSQLiteQuery = spec.build()

    /** Snapshot without a database; later mutations of this builder cannot change the definition. */
    fun toSpec(): QuerySpec<E> = spec

    /** Captures the current definition; later changes to this builder do not change the projection. */
    fun <R> project(projection: Projection<E, R>): ProjectedSelect<E, R> = ProjectedSelect(db, spec, projection)

    /** Overrides any prior page, and reads the list and total in one transaction without outer retry. */
    suspend fun pageResult(number: Int, size: Int): PageResult<E> {
        val selected = db.select(spec.page(number, size))
        return db.withTransaction { PageResult(selected.list(), selected.totalCount(), number, size) }
    }

    /** At most one row, preserving the current page offset and deterministic ordering. */
    suspend fun firstOrNull(): E? {
        val query = spec.buildProjection(table.columns.joinToString { it.quoted }, firstOnly = true)
        table.validate(db)
        return db.rawQueryList(query, table::readEntity).firstOrNull()
    }

    /** Checks the current page/window without materializing an entity. */
    suspend fun exists(): Boolean {
        val query = spec.buildProjection("1", firstOnly = true, ordered = false)
        table.validate(db)
        return db.rawQueryList(query) { true }.isNotEmpty()
    }

    /** Number of matching rows in the current page/window, or all matches when unpaged. */
    suspend fun count(): Long = count(spec)

    /** Number of all matching rows, explicitly ignoring page size and offset. */
    suspend fun totalCount(): Long = count(spec.unpaged())

    private suspend fun count(definition: QuerySpec<E>): Long {
        val query = definition.buildProjection("1", ordered = false, count = true)
        table.validate(db)
        return db.rawQueryList(query) { it.getLong(0) }.single()
    }

    /** Aggregates the current input window; unpaged() requests all matching input rows. */
    suspend fun <R> aggregate(value: Aggregate<E, R>): R {
        table.owns(value.column)
        val inner = spec.buildProjection(value.column.quoted)
        val query = wrapQuery(inner, "SELECT ${value.expression} FROM (${inner.sql})")
        table.validate(db)
        return db.rawQueryList(query) { value.read(it, 0) }.single()
    }

    /** Single-key/single-aggregate grouping of the input window; output ordered by key ASC. */
    suspend fun <K, R> groupBy(key: EntityColumn<E, K>, value: Aggregate<E, R>): List<AggregateGroup<K, R>> {
        table.owns(key)
        table.owns(value.column)
        val inner = spec.buildProjection(listOf(key, value.column).distinct().joinToString { it.quoted })
        val query = wrapQuery(inner,
            "SELECT ${key.quoted}, ${value.expression} FROM (${inner.sql}) GROUP BY ${key.quoted} ORDER BY ${key.quoted}")
        table.validate(db)
        return db.rawQueryList(query) { AggregateGroup(key.reader(it, 0), value.read(it, 1)) }
    }

    fun <R> observeAggregate(value: Aggregate<E, R>): Flow<R> {
        table.owns(value.column)
        val snapshot = spec
        val database = db
        return database.observeTables(table.sqlName).map { database.select(snapshot).aggregate(value) }
    }

    /** Cold, serial requery of a snapshot captured NOW; collect outside a database transaction. */
    fun observe(): Flow<List<E>> {
        val snapshot = spec
        val database = db
        return database.observeTables(table.sqlName).map { database.select(snapshot).list() }
    }

    /** Without page(), returns all matching rows; use each for large results. */
    suspend fun list(): List<E> {
        val query = build()
        table.validate(db)
        return db.rawQueryList(query, table::readEntity)
    }

    /** A single-column typed projection, without constructing an incomplete entity. */
    suspend fun <V> values(column: EntityColumn<E, V>): List<V> {
        table.owns(column)
        val query = spec.buildProjection(column.quoted)
        table.validate(db)
        return db.rawQueryList(query) { column.reader(it, 0) }
    }

    /** Sequential consumption; no automatic retries of side effects, no list allocation. */
    suspend fun each(action: (E) -> Unit): Long {
        val query = build()
        table.validate(db)
        return db.rawQueryEach(query) { action(table.readEntity(it)) }
    }
}

private fun wrapQuery(inner: SupportSQLiteQuery, sql: String): SupportSQLiteQuery =
    object : SupportSQLiteQuery by inner {
        override val sql: String = sql
    }
