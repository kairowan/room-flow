package com.kairowan.roomflow.data

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.kairowan.room_flow.typed.QuerySpec
import com.kairowan.room_flow.typed.contains
import com.kairowan.room_flow.typed.query

/** Business query definitions; legacy raw SQL examples remain explicitly separate. */
object UserQueries {
    fun matching(nameContains: String? = null): QuerySpec<User> = UserTable.query()
        .whereIfNotNull(nameContains) { UserTable.name.contains(it) }
        .orderBy(UserTable.id.desc())

    fun adults(nameContains: String? = null): QuerySpec<User> = matching(nameContains)
        .where(UserTable.age.greaterThanOrEqual(18))

    /** NULL means no name filter; empty text matches all names. %, _ and backslash are literal characters. */
    fun page(beforeId: Long?, size: Int, nameContains: String? = null): SupportSQLiteQuery {
        require(size in 1..500) { "分页大小必须在 1..500 之间" }
        val conditions = mutableListOf<String>()
        val args = mutableListOf<Any?>()
        if (beforeId != null) {
            conditions += "id < ?"
            args += beforeId
        }
        if (nameContains != null) {
            conditions += "name LIKE ? ESCAPE '\\'"
            args += "%" + nameContains.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        }
        val where = if (conditions.isEmpty()) "" else conditions.joinToString(" AND ", prefix = " WHERE ")
        args += size
        // ponytail: fixed unique-id ordering; arbitrary sorting needs an explicit whitelist and matching cursor.
        return SimpleSQLiteQuery(
            "SELECT id, name, age, sex, lastActive FROM users$where ORDER BY id DESC LIMIT ?",
            args.toTypedArray()
        )
    }

    /** Intentionally unbounded: use rawQueryEach for export, never load this query into a UI list. */
    fun exportAll(): SupportSQLiteQuery =
        SimpleSQLiteQuery("SELECT id, name, age, sex, lastActive FROM users ORDER BY id")
}
