package com.kairowan.room_flow.typed

import android.database.Cursor
import com.kairowan.room_flow.sql.quoteIdentifier

/** Created by generated table metadata; E and V prevent mixing entities and field value types. */
class EntityColumn<E : Any, V> internal constructor(
    internal val owner: EntityTable<E>,
    val sqlName: String,
    internal val affinity: String,
    internal val nullable: Boolean,
    internal val keyPosition: Int,
    internal val autoGenerate: Boolean,
    internal val getter: (E) -> V,
    internal val reader: (Cursor, Int) -> V
) {
    internal val quoted: String get() = quoteIdentifier(sqlName)

    fun eq(value: V): SqlCondition<E> = comparison("=", value)
    fun notEq(value: V): SqlCondition<E> = comparison("!=", value)
    fun greaterThan(value: V): SqlCondition<E> = comparison(">", value.also { require(it != null) })
    fun lessThan(value: V): SqlCondition<E> = comparison("<", value.also { require(it != null) })
    fun greaterThanOrEqual(value: V): SqlCondition<E> = comparison(">=", value.also { require(it != null) })
    fun lessThanOrEqual(value: V): SqlCondition<E> = comparison("<=", value.also { require(it != null) })
    fun between(lower: V, upper: V): SqlCondition<E> = greaterThanOrEqual(lower).and(lessThanOrEqual(upper))
    fun isNull(): SqlCondition<E> = SqlCondition(owner, "$quoted IS NULL", emptyList())
    fun isNotNull(): SqlCondition<E> = SqlCondition(owner, "$quoted IS NOT NULL", emptyList())

    /** Empty collection matches nothing; NULL members explicitly include IS NULL. No implicit chunking. */
    fun isIn(values: Collection<V>): SqlCondition<E> {
        val nonNull = values.filterNotNull()
        val condition = if (nonNull.isEmpty()) SqlCondition(owner, "0", emptyList()) else {
            require(nonNull.size <= 900) { "IN exceeds the conservative 900 bind limit" }
            SqlCondition(owner, "$quoted IN (${nonNull.joinToString { "?" }})", nonNull.map(::snapshotValue))
        }
        return if (values.any { it == null }) condition.or(isNull()) else condition
    }

    /** Complement of explicit membership: empty matches all; NULL is excluded only if listed. */
    fun notIn(values: Collection<V>): SqlCondition<E> {
        val condition = !isIn(values)
        return if (values.any { it == null }) condition else condition.or(isNull())
    }

    fun asc(): SqlOrder<E> = SqlOrder(this, false)
    fun desc(): SqlOrder<E> = SqlOrder(this, true)

    private fun comparison(operator: String, value: V): SqlCondition<E> = when {
        value == null && operator == "=" -> isNull()
        value == null && operator == "!=" -> isNotNull()
        else -> SqlCondition(owner, "$quoted $operator ?", listOf(snapshotValue(value)))
    }
}

/** Literal substring, not a user-provided LIKE pattern; case behavior is SQLite's own LIKE behavior. */
fun <E : Any, V : String?> EntityColumn<E, V>.contains(value: String): SqlCondition<E> {
    return literalLike(value, "%", "%")
}

fun <E : Any, V : String?> EntityColumn<E, V>.startsWith(value: String): SqlCondition<E> = literalLike(value, "", "%")

fun <E : Any, V : String?> EntityColumn<E, V>.endsWith(value: String): SqlCondition<E> = literalLike(value, "%", "")

private fun <E : Any, V : String?> EntityColumn<E, V>.literalLike(value: String, prefix: String, suffix: String): SqlCondition<E> {
    val escaped = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return SqlCondition(owner, "$quoted LIKE ? ESCAPE '\\'", listOf(prefix + escaped + suffix))
}

internal fun snapshotValue(value: Any?): Any? {
    require(value !is Double || value.isFinite()) { "Non-finite SQL value" }
    require(value !is Float || value.isFinite()) { "Non-finite SQL value" }
    return if (value is ByteArray) value.copyOf() else value
}
