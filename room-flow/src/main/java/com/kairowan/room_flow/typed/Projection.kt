package com.kairowan.room_flow.typed

import android.database.Cursor

/** Typed column composition. Mapping callbacks must be pure; no Cursor is exposed to callers. */
class Projection<E : Any, R> internal constructor(
    internal val table: EntityTable<E>,
    internal val columns: List<EntityColumn<E, *>>,
    internal val read: (Cursor, Int) -> R
) {
    fun <T> map(transform: (R) -> T): Projection<E, T> =
        Projection(table, columns) { cursor, offset -> transform(read(cursor, offset)) }

    fun <B, T> zip(other: Projection<E, B>, combine: (R, B) -> T): Projection<E, T> {
        require(table === other.table) { "Projections belong to different tables" }
        return Projection(table, columns + other.columns) { cursor, offset ->
            combine(read(cursor, offset), other.read(cursor, offset + columns.size))
        }
    }
}

fun <E : Any, V> EntityColumn<E, V>.project(): Projection<E, V> =
    Projection(owner, listOf(this), reader)

/** Two-field convenience; zip/map compose larger projections without a family of arity overloads. */
fun <E : Any, A, B, R> projection(
    first: EntityColumn<E, A>,
    second: EntityColumn<E, B>,
    combine: (A, B) -> R
): Projection<E, R> = first.project().zip(second.project(), combine)
