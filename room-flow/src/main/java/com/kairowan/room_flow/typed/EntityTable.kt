package com.kairowan.room_flow.typed

import android.database.Cursor
import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import com.kairowan.room_flow.sql.quoteIdentifier
import com.kairowan.room_flow.sql.rawQueryList

/** Base for KSP-generated metadata. Instantiate generated XxxTable objects, not hand-written mappings. */
abstract class EntityTable<E : Any>(val sqlName: String) {
    internal val columns = mutableListOf<EntityColumn<E, *>>()
    internal val quoted: String get() = quoteIdentifier(sqlName)
    internal val keys: List<EntityColumn<E, *>> get() = columns.filter { it.keyPosition > 0 }.sortedBy { it.keyPosition }

    protected fun <V> column(
        sqlName: String,
        affinity: String,
        nullable: Boolean,
        keyPosition: Int,
        autoGenerate: Boolean,
        getter: (E) -> V,
        reader: (Cursor, Int) -> V
    ): EntityColumn<E, V> {
        quoteIdentifier(sqlName)
        require(columns.none { it.sqlName.equals(sqlName, ignoreCase = true) }) { "Duplicate column" }
        return EntityColumn(this, sqlName, affinity, nullable, keyPosition, autoGenerate, getter, reader)
            .also { columns += it }
    }

    abstract fun readEntity(cursor: Cursor): E

    internal fun owns(column: EntityColumn<E, *>) {
        require(column.owner === this && columns.any { it === column }) { "Column belongs to another table" }
    }

    internal fun owns(condition: SqlCondition<E>) {
        require(condition.owner === this) { "Condition belongs to another table" }
    }

    internal fun query(sql: String, args: List<Any?>): SimpleSQLiteQuery {
        require(args.size <= 900) { "Query exceeds the conservative 900 bind limit" }
        return SimpleSQLiteQuery(sql, args.map(::snapshotValue).toTypedArray())
    }

    /** Check the actual opened file before access; this never creates/changes schema or repairs a database. */
    internal suspend fun validate(db: RoomDatabase) {
        require(columns.isNotEmpty() && keys.isNotEmpty()) { "Entity metadata needs columns and a primary key" }
        val kind = db.rawQueryList("SELECT type FROM sqlite_master WHERE name = ? COLLATE NOCASE AND type IN ('table', 'view')", listOf(sqlName)) { it.getString(0) }
        require(kind == listOf("table")) { "Entity table is missing or is not a normal table: $sqlName" }
        val actual = db.rawQueryList("PRAGMA table_info($quoted)") {
            it.getString(1).lowercase() to Triple(it.getString(2).uppercase(), it.getInt(3) != 0, it.getInt(5))
        }.toMap()
        require(actual.size == columns.size && columns.all {
            actual[it.sqlName.lowercase()] == Triple(it.affinity, !it.nullable, it.keyPosition)
        }) { "Opened database does not match generated entity columns: $sqlName; use a proper Room migration" }
        // ponytail: validate each operation, without caching schema across lifecycle/DDL changes.
        // This is not a complete Room schema validator (indices/defaults/triggers still belong to Room).
    }
}
