package com.kairowan.room_flow

import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

/**
 * @author 浩楠
 *
 * @date 2025/8/24
 *
 *      _              _           _     _   ____  _             _ _
 *     / \   _ __   __| |_ __ ___ (_) __| | / ___|| |_ _   _  __| (_) ___
 *    / _ \ | '_ \ / _` | '__/ _ \| |/ _` | \___ \| __| | | |/ _` | |/ _ \
 *   / ___ \| | | | (_| | | | (_) | | (_| |  ___) | |_| |_| | (_| | | (_) |
 *  /_/   \_\_| |_|\__,_|_|  \___/|_|\__,_| |____/ \__|\__,_|\__,_|_|\___/
 * @Description: TODO
 */
object MigrationAssistant {
    data class Column(val name: String, val type: String, val notNull: Boolean)
    data class Table(val name: String, val columns: List<Column>)
    data class Diff(val missingTables: List<Table>, val missingColumns: Map<String, List<Column>>)

    /**
     * 解析 Room schema JSON（取最后一个版本）
     */
    fun parseRoomSchemaJson(schemaJson: String): List<Table> {
        val root = JSONArray(schemaJson)
        val latest = root.getJSONObject(root.length() - 1)
        val entities = latest.getJSONObject("database").getJSONArray("entities")
        val out = mutableListOf<Table>()
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val tableName = e.getString("tableName")
            val fields = e.getJSONArray("fields")
            val cols = mutableListOf<Column>()
            for (j in 0 until fields.length()) {
                val f = fields.getJSONObject(j)
                val name = f.getString("fieldPath")
                val affinity = f.getString("affinity") // TEXT/INTEGER/REAL/BLOB
                val notNull = f.optBoolean("notNull", false)
                cols += Column(name, affinity, notNull)
            }
            out += Table(tableName, cols)
        }
        return out
    }

    private fun tableInfo(db: RoomDatabase, table: String): List<Column> {
        val cols = mutableListOf<Column>()
        db.openHelper.readableDatabase.query(SimpleSQLiteQuery("PRAGMA table_info($table)")).use {
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndexOrThrow("name"))
                val type = it.getString(it.getColumnIndexOrThrow("type"))
                val notNull = it.getInt(it.getColumnIndexOrThrow("notnull")) != 0
                cols += Column(name, type.uppercase(), notNull)
            }
        }
        return cols
    }

    private fun allTables(db: RoomDatabase): List<String> {
        val names = mutableListOf<String>()
        db.openHelper.readableDatabase.query(
            SimpleSQLiteQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%' AND name NOT LIKE 'sqlite_%'")
        ).use { c -> while (c.moveToNext()) names += c.getString(0) }
        return names
    }

    /**
     * 比对 schema 与在线库，输出缺失表/缺失列
     */
    suspend fun compareSchema(db: RoomDatabase, schemaJson: String): Diff =
        withContext(Dispatchers.IO) {
            val expect = parseRoomSchemaJson(schemaJson).associateBy { it.name }
            val existingTables = allTables(db).toSet()
            val missingTables = mutableListOf<Table>()
            val missingColumns = mutableMapOf<String, MutableList<Column>>()
            for ((name, table) in expect) {
                if (!existingTables.contains(name)) {
                    missingTables += table; continue
                }
                val liveCols = tableInfo(db, name).associateBy { it.name.lowercase() }
                for (col in table.columns) {
                    if (!liveCols.containsKey(col.name.lowercase())) {
                        missingColumns.getOrPut(name) { mutableListOf() }.add(col)
                    }
                }
            }
            Diff(missingTables, missingColumns)
        }

    /**
     * 生成简易迁移 SQL（缺表/缺列）
     */
    fun planMigration(diff: Diff): List<String> {
        val sqls = mutableListOf<String>()
        for (t in diff.missingTables) {
            val cols = t.columns.joinToString(", ") {
                "${it.name} ${it.type}${if (it.notNull) " NOT NULL" else ""}"
            }
            sqls += "CREATE TABLE IF NOT EXISTS ${t.name} ($cols);"
        }
        for ((table, cols) in diff.missingColumns) {
            for (c in cols) {
                val notNull = if (c.notNull) " NOT NULL" else ""
                val defaultClause = if (c.notNull) defaultForType(c.type) else ""
                sqls += "ALTER TABLE $table ADD COLUMN ${c.name} ${c.type}$notNull$defaultClause;"
            }
        }
        return sqls
    }

    private fun defaultForType(type: String): String = when (type.uppercase()) {
        "INTEGER" -> " DEFAULT 0"
        "REAL" -> " DEFAULT 0.0"
        "TEXT" -> " DEFAULT ''"
        "BLOB" -> ""
        else -> ""
    }

    /** 备份与回滚（需要调用方提供 dbPath）。 */
    fun backupDb(dbPath: File): File {
        val bak = File(dbPath.parentFile, dbPath.name + ".bak"); dbPath.copyTo(
            bak,
            overwrite = true
        ); return bak
    }

    fun rollbackDb(dbPath: File, backup: File) {
        backup.copyTo(dbPath, overwrite = true)
    }
}