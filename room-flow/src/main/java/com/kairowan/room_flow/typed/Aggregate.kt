package com.kairowan.room_flow.typed

import android.database.Cursor

/** A trusted scalar aggregate over one generated column; no public Kotlin raw SQL constructor. */
class Aggregate<E : Any, R> internal constructor(
    internal val column: EntityColumn<E, *>,
    internal val expression: String,
    internal val read: (Cursor, Int) -> R
)

fun <E : Any, V> EntityColumn<E, V>.minimum(): Aggregate<E, V?> =
    Aggregate(this, "MIN($quoted)") { cursor, index -> if (cursor.isNull(index)) null else reader(cursor, index) }

fun <E : Any, V> EntityColumn<E, V>.maximum(): Aggregate<E, V?> =
    Aggregate(this, "MAX($quoted)") { cursor, index -> if (cursor.isNull(index)) null else reader(cursor, index) }

fun <E : Any, V> EntityColumn<E, V>.countDistinct(): Aggregate<E, Long> =
    Aggregate(this, "COUNT(DISTINCT $quoted)") { cursor, index -> cursor.getLong(index) }

/** Exact integer sum; rejects REAL columns up front and propagates SQLite integer overflow. */
fun <E : Any, V : Number?> EntityColumn<E, V>.sumLong(): Aggregate<E, Long?> {
    require(affinity == "INTEGER") { "sumLong requires an INTEGER column; use sumDouble for REAL" }
    return Aggregate(this, "SUM($quoted)") { cursor, index ->
        if (cursor.isNull(index)) null else {
            require(cursor.getType(index) == Cursor.FIELD_TYPE_INTEGER) { "Non-integer value in integer SUM" }
            cursor.getLong(index)
        }
    }
}

/** Approximate floating-point sum; NULL for empty/all-NULL input. */
fun <E : Any, V : Number?> EntityColumn<E, V>.sumDouble(): Aggregate<E, Double?> =
    Aggregate(this, "SUM(CAST($quoted AS REAL))", ::readFiniteDouble)

fun <E : Any, V : Number?> EntityColumn<E, V>.average(): Aggregate<E, Double?> =
    Aggregate(this, "AVG($quoted)", ::readFiniteDouble)

private fun readFiniteDouble(cursor: Cursor, index: Int): Double? =
    if (cursor.isNull(index)) null else cursor.getDouble(index).also {
        require(it.isFinite()) { "Non-finite aggregate result" }
    }
