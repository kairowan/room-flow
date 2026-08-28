package com.kairowan.room_flow.typed

/** Immutable, parameterized predicate. No raw SQL escape hatch in the typed API. */
class SqlCondition<E : Any> internal constructor(
    internal val owner: EntityTable<E>,
    internal val sql: String,
    internal val args: List<Any?>
) {
    /** SQL NOT: preserves SQL's three-valued NULL logic, unlike membership-complement notIn. */
    operator fun not(): SqlCondition<E> = SqlCondition(owner, "NOT ($sql)", args)

    infix fun and(other: SqlCondition<E>): SqlCondition<E> = combine("AND", other)
    infix fun or(other: SqlCondition<E>): SqlCondition<E> = combine("OR", other)

    private fun combine(operator: String, other: SqlCondition<E>): SqlCondition<E> {
        require(owner === other.owner) { "Conditions must belong to the same table" }
        require(args.size + other.args.size <= 900) { "Query exceeds the conservative 900 bind limit" }
        return SqlCondition(owner, "($sql) $operator (${other.sql})", args + other.args)
    }
}
