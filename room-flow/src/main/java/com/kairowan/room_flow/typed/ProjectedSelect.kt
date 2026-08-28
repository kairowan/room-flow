package com.kairowan.room_flow.typed

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteQuery
import com.kairowan.room_flow.flow.observeTables
import com.kairowan.room_flow.sql.rawQueryEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Immutable execution view of a definition and projection; mapper exceptions are not retried. */
class ProjectedSelect<E : Any, R> internal constructor(
    private val db: RoomDatabase,
    private val spec: QuerySpec<E>,
    private val projection: Projection<E, R>
) {
    init {
        require(projection.table === spec.table) { "Projection belongs to another table" }
        projection.columns.forEach { spec.table.owns(it) }
    }

    fun build(): SupportSQLiteQuery = spec.buildProjection(projection.columns.joinToString { it.quoted })

    suspend fun list(): List<R> = read(build())

    suspend fun firstOrNull(): R? = read(
        spec.buildProjection(projection.columns.joinToString { it.quoted }, firstOnly = true)
    ).firstOrNull()

    private suspend fun read(query: SupportSQLiteQuery): List<R> {
        spec.table.validate(db)
        val result = mutableListOf<R>()
        // Reuse the no-retry consumer path: a mapper must not be re-invoked after it throws.
        db.rawQueryEach(query) { result += projection.read(it, 0) }
        return result
    }

    suspend fun pageResult(number: Int, size: Int): PageResult<R> {
        val paged = spec.page(number, size)
        return db.withTransaction {
            PageResult(ProjectedSelect(db, paged, projection).list(), db.select(paged).totalCount(), number, size)
        }
    }

    fun observe(): Flow<List<R>> = db.observeTables(spec.table.sqlName).map { list() }
}
