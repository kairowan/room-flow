package com.kairowan.room_flow.migration

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.os.Looper
import android.os.CancellationSignal
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.kairowan.room_flow.backup.BackupIdentity
import com.kairowan.room_flow.backup.atomicCopy
import com.kairowan.room_flow.backup.copyWithProgress
import com.kairowan.room_flow.backup.requiredBackupBytes
import com.kairowan.room_flow.backup.checkColdJournal
import com.kairowan.room_flow.backup.RestorePreview
import com.kairowan.room_flow.core.RoomFlowConfig
import com.kairowan.room_flow.sql.quoteIdentifier
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.DigestOutputStream
import java.security.MessageDigest

/** 只为简单新增表/列提供建议；不执行迁移，复杂结构变更必须手写 Migration/AutoMigration。 */
object MigrationAssistant {
    fun parseRoomSchemaJson(schemaJson: String): List<Table> {
        val database = JSONObject(schemaJson).getJSONObject("database")
        require((database.optJSONArray("views")?.length() ?: 0) == 0) { "视图迁移请使用 Migration/AutoMigration" }
        val entities = database.getJSONArray("entities")
        return (0 until entities.length()).map { i ->
            val entity = entities.getJSONObject(i)
            require(!entity.has("ftsVersion")) { "FTS 迁移请使用 Migration/AutoMigration" }
            val name = entity.getString("tableName")
            val primaryKey = entity.optJSONObject("primaryKey")?.optJSONArray("columnNames")
                ?.let { array -> (0 until array.length()).map { array.getString(it) } }.orEmpty()
            val fields = entity.getJSONArray("fields")
            val columns = (0 until fields.length()).map { j ->
                val field = fields.getJSONObject(j)
                val column = field.getString("columnName")
                Column(
                    column, field.getString("affinity"), field.getBoolean("notNull"),
                    if (field.has("defaultValue") && !field.isNull("defaultValue")) field.getString("defaultValue") else null,
                    primaryKey.indexOf(column) + 1
                )
            }
            val indices = entity.optJSONArray("indices")
            val indexList = (0 until (indices?.length() ?: 0)).map { j -> indices!!.getJSONObject(j) }
            val foreignKeys = entity.optJSONArray("foreignKeys")
            Table(
                name, columns, entity.getString("createSql"),
                indexList.map { it.getString("createSql") },
                indexList.map { index ->
                    val names = index.getJSONArray("columnNames").let { a -> (0 until a.length()).map { a.getString(it) } }
                    val orders = index.optJSONArray("orders")
                    Index(index.getString("name"), index.getBoolean("unique"), names,
                        names.indices.map { orders?.optString(it, "ASC") ?: "ASC" })
                }.toSet(),
                (0 until (foreignKeys?.length() ?: 0)).map { j ->
                    val foreign = foreignKeys!!.getJSONObject(j)
                    fun names(key: String): List<String> = foreign.getJSONArray(key).let { a ->
                        (0 until a.length()).map { a.getString(it) }
                    }
                    ForeignKey(foreign.getString("table"), foreign.getString("onDelete"), foreign.getString("onUpdate"),
                        names("columns"), names("referencedColumns"))
                }.toSet()
            )
        }
    }

    private fun tableInfo(db: RoomDatabase, table: String): List<Column> =
        db.openHelper.readableDatabase.query("PRAGMA table_info(${quoteIdentifier(table)})").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")
                    add(Column(
                        cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("type")).uppercase(),
                        cursor.getInt(cursor.getColumnIndexOrThrow("notnull")) != 0,
                        if (cursor.isNull(defaultIndex)) null else cursor.getString(defaultIndex),
                        cursor.getInt(cursor.getColumnIndexOrThrow("pk"))
                    ))
                }
            }
        }

    private fun indices(db: RoomDatabase, table: String): Set<Index> =
        db.openHelper.readableDatabase.query("PRAGMA index_list(${quoteIdentifier(table)})").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndexOrThrow("origin")) != "c") continue
                    val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    val unique = cursor.getInt(cursor.getColumnIndexOrThrow("unique")) != 0
                    val columns = mutableListOf<String>()
                    val orders = mutableListOf<String>()
                    db.openHelper.readableDatabase.query("PRAGMA index_xinfo(${quoteIdentifier(name)})").use { info ->
                        while (info.moveToNext()) {
                            if (info.getInt(info.getColumnIndexOrThrow("key")) == 0) continue
                            columns += info.getString(info.getColumnIndexOrThrow("name")) ?: "<expression>"
                            orders += if (info.getInt(info.getColumnIndexOrThrow("desc")) == 0) "ASC" else "DESC"
                        }
                    }
                    add(Index(name, unique, columns, orders))
                }
            }
        }

    private fun foreignKeys(db: RoomDatabase, table: String): Set<ForeignKey> {
        val keys = linkedMapOf<Int, ForeignKey>()
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_list(${quoteIdentifier(table)})").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                val column = cursor.getString(cursor.getColumnIndexOrThrow("from"))
                val referenced = cursor.getString(cursor.getColumnIndexOrThrow("to"))
                val previous = keys[id]
                keys[id] = ForeignKey(
                    cursor.getString(cursor.getColumnIndexOrThrow("table")),
                    cursor.getString(cursor.getColumnIndexOrThrow("on_delete")),
                    cursor.getString(cursor.getColumnIndexOrThrow("on_update")),
                    previous?.columns.orEmpty() + column,
                    previous?.referencedColumns.orEmpty() + referenced
                )
            }
        }
        return keys.values.toSet()
    }

    suspend fun compareSchema(db: RoomDatabase, schemaJson: String): Diff =
        withContext(RoomFlowConfig.ioDispatcher) {
            db.withTransaction {
                val expected = parseRoomSchemaJson(schemaJson)
                val missingTables = mutableListOf<Table>()
                val missingColumns = linkedMapOf<String, List<Column>>()
                val unsupported = mutableListOf<String>()
                db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(0)
                        if (!name.startsWith("sqlite_") && name !in setOf("room_master_table", "android_metadata") &&
                            expected.none { it.name.equals(name, ignoreCase = true) }) {
                            unsupported += "$name: 目标 schema 未包含此表，需要人工确认保留或删除"
                        }
                    }
                }
                for (table in expected) {
                    val live = tableInfo(db, table.name).associateBy { it.name.lowercase() }
                    if (live.isEmpty()) {
                        missingTables += table
                        continue
                    }
                    val missing = table.columns.filter { it.name.lowercase() !in live }
                    if (missing.isNotEmpty()) missingColumns[table.name] = missing
                    for (column in table.columns) {
                        val actual = live[column.name.lowercase()] ?: continue
                        if (actual.copy(name = column.name) != column) unsupported += "${table.name}.${column.name}: 列定义变化"
                    }
                    if (live.keys.any { name -> table.columns.none { it.name.lowercase() == name } }) {
                        unsupported += "${table.name}: 删除或重命名列"
                    }
                    if (indices(db, table.name) != table.indices) unsupported += "${table.name}: 索引变化"
                    if (foreignKeys(db, table.name) != table.foreignKeys) unsupported += "${table.name}: 外键变化"
                }
                Diff(missingTables, missingColumns, unsupported)
            }
        }

    /** 输入必须来自可信 Room schema；返回 SQL 必须人工审阅并通过 Room 的迁移验证。 */
    fun planMigration(diff: Diff): List<String> {
        require(diff.unsupportedChanges.isEmpty()) { "需要手写 Migration: ${diff.unsupportedChanges}" }
        return buildList {
            for (table in diff.missingTables) {
                val create = requireNotNull(table.createSql) { "缺少 Room createSql，不能猜测主键或约束" }
                fun resolve(sql: String): String = sql
                    .replace("`${'$'}{TABLE_NAME}`", quoteIdentifier(table.name))
                    .replace("\"${'$'}{TABLE_NAME}\"", quoteIdentifier(table.name))
                    .replace("${'$'}{TABLE_NAME}", quoteIdentifier(table.name))
                add(resolve(create))
                addAll(table.indexSql.map(::resolve))
            }
            for ((table, columns) in diff.missingColumns) {
                for (column in columns) {
                    require(column.primaryKeyPosition == 0) { "不能用 ADD COLUMN 新增主键" }
                    require(column.type in setOf("INTEGER", "TEXT", "REAL", "BLOB", "NUMERIC")) { "未知列类型" }
                    require(!column.notNull || (column.defaultValue != null && !column.defaultValue.equals("NULL", true))) {
                        "新增 NOT NULL 列 ${column.name} 需要 schema 中明确的非 NULL 默认值"
                    }
                    // ponytail: 只接受常量默认值；表达式/约束改动请使用手写 Migration。
                    val default = column.defaultValue
                    require(default == null || default.matches(Regex("(?i)NULL|[-+]?[0-9]+(\\.[0-9]+)?|'([^']|'')*'|X'[0-9a-f]*'"))) {
                        "默认值需要人工迁移: ${column.name}"
                    }
                    add("ALTER TABLE ${quoteIdentifier(table)} ADD COLUMN ${quoteIdentifier(column.name)} ${column.type}" +
                        (if (column.notNull) " NOT NULL" else "") + (default?.let { " DEFAULT $it" } ?: ""))
                }
            }
        }
    }

    /**
     * 仅支持关闭后的离线文件复制。调用方必须停止所有线程/进程访问；现有备份不会被覆盖。
     * SQLCipher 等加密库需要自己的备份/验证流程。
     */
    // ponytail: 进程内串行离线文件操作；多进程备份需调用方统一协调，不能并发调用。
    @Synchronized
    fun backupDb(dbPath: File, identity: BackupIdentity, databaseClosed: Boolean = false): File =
        backupDb(dbPath, identity, databaseClosed, null)

    /** 保守的额外空间估算（字节），不是空间预留；实际写入仍可能失败。IO 线程调用。 */
    fun estimateBackupSpace(dbPath: File): Long {
        check(Looper.myLooper() != Looper.getMainLooper()) { "空间预检必须在 IO 线程执行" }
        require(dbPath.isFile) { "数据库文件不存在" }
        return requiredBackupBytes(dbPath.length())
    }

    /**
     * signal 在校验/复制和发布前生效；发布开始后完成清单，不再接受取消。
     * progress 仅为主文件复制字节数，回调不得重入备份/改动文件；抛异常会中止发布。
     */
    @Synchronized
    fun backupDb(
        dbPath: File,
        identity: BackupIdentity,
        databaseClosed: Boolean,
        signal: CancellationSignal?,
        onProgress: ((Long, Long) -> Unit)? = null
    ): File {
        checkOffline(dbPath, databaseClosed)
        check(dbPath.isFile) { "数据库文件不存在" }
        signal?.throwIfCanceled()
        val needed = estimateBackupSpace(dbPath)
        val available = dbPath.absoluteFile.parentFile!!.usableSpace
        if (available > 0 && available < needed) throw IOException("备份空间不足：需要至少 $needed 字节，可用 $available 字节")
        val backup = File(dbPath.parentFile, dbPath.name + ".bak")
        val metadata = File(backup.path + ".json")
        check(!backup.exists() && !metadata.exists()) { "备份或清单已存在，请先保存原备份" }
        validate(dbPath, identity, signal)
        val hash = MessageDigest.getInstance("SHA-256")
        atomicCopy(dbPath, backup, beforeCommit = { signal?.throwIfCanceled() }) { input, output ->
            copyWithProgress(input, DigestOutputStream(output, hash), dbPath.length(), { signal?.throwIfCanceled() }, onProgress)
        }
        // 清单最后发布；中断后留下的无清单备份不会被自动恢复，也不会被下次备份覆盖。
        val manifest = JSONObject().put("format", 1).put("databaseId", identity.databaseId)
            .put("version", identity.version).put("roomIdentityHash", identity.roomIdentityHash)
            .put("fileName", dbPath.name).put("sha256", hash.digest().joinToString("") { "%02x".format(it) })
        val temporary = File.createTempFile("roomflow-manifest-", ".tmp", metadata.absoluteFile.parentFile)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(manifest.toString().toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            check(temporary.renameTo(metadata)) { "备份清单保存失败，备份文件已保留" }
        } finally { temporary.delete() }
        return backup
    }

    @Synchronized
    fun previewRestore(dbPath: File, backup: File, identity: BackupIdentity, databaseClosed: Boolean = false): RestorePreview {
        checkOffline(dbPath, databaseClosed)
        checkOffline(backup, databaseClosed)
        require(dbPath.canonicalFile != backup.canonicalFile) { "不能从自身恢复" }
        check(backup.isFile) { "备份不存在" }
        validate(backup, identity)
        val metadata = File(backup.path + ".json")
        check(metadata.isFile && metadata.length() in 1..16_384) { "缺少有效备份清单，拒绝恢复旧格式/未知来源文件" }
        val manifest = JSONObject(metadata.readText())
        check(manifest.getInt("format") == 1 && manifest.getString("databaseId") == identity.databaseId &&
            manifest.getInt("version") == identity.version && manifest.getString("roomIdentityHash") == identity.roomIdentityHash &&
            manifest.getString("fileName") == dbPath.name) { "备份身份、版本或目标文件名不匹配" }
        check(manifest.getString("sha256") == digest(backup)) { "备份摘要不匹配" }
        val recovery = if (dbPath.exists()) File(dbPath.path + ".before-restore") else null
        check(recovery?.exists() != true) { "恢复点已存在，请先保存它，禁止覆盖" }
        return RestorePreview(identity, backup.length(), recovery)
    }

    /** 预检后再次校验再替换；返回覆盖前的原始文件（可能损坏），不要自动删除它。 */
    @Synchronized
    fun rollbackDb(dbPath: File, backup: File, identity: BackupIdentity, databaseClosed: Boolean = false): File? {
        val preview = previewRestore(dbPath, backup, identity, databaseClosed)
        preview.recoveryFile?.let { atomicCopy(dbPath, it) }
        atomicCopy(backup, dbPath)
        return preview.recoveryFile
    }

    private fun checkOffline(file: File, databaseClosed: Boolean) {
        check(Looper.myLooper() != Looper.getMainLooper()) { "备份恢复必须在 IO 线程执行" }
        require(databaseClosed) { "必须先关闭全部数据库连接并停止所有进程访问，再传 databaseClosed=true" }
        check(File(file.path + "-wal").length() == 0L) { "存在未处理的 -wal，拒绝复制/恢复" }
        checkColdJournal(File(file.path + "-journal"))
        // 旧 shm 属于旧连接，恢复时不能和新主文件混用。
        check(!File(file.path + "-shm").exists()) { "存在 shm，请先确认所有数据库连接已关闭" }
    }

    private fun validate(file: File, identity: BackupIdentity, signal: CancellationSignal? = null) {
        // Android 只读打开 WAL 库也可能创建 -shm/-wal；仅打开副本，避免校验改变输入文件集合。
        val copy = File.createTempFile("roomflow-validate-", ".db", file.absoluteFile.parentFile)
        try {
            file.inputStream().use { input ->
                copy.outputStream().use { output ->
                    copyWithProgress(input, output, file.length(), { signal?.throwIfCanceled() })
                }
            }
            validateCopy(copy, identity, signal)
        } finally {
            listOf("", "-wal", "-shm", "-journal").forEach { File(copy.path + it).delete() }
        }
    }

    private fun validateCopy(file: File, identity: BackupIdentity, signal: CancellationSignal?) {
        // 默认 DatabaseErrorHandler 可能删掉损坏文件；验证只能报告错误，不能破坏待恢复证据。
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY) {
            throw SQLiteDatabaseCorruptException("数据库损坏，保留原文件供离线恢复")
        }.use { db ->
            db.rawQuery("PRAGMA integrity_check", null, signal).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "ok" && !cursor.moveToNext()) {
                    "数据库完整性检查失败，拒绝复制"
                }
            }
            check(db.version == identity.version) { "数据库版本不匹配" }
            db.rawQuery("SELECT identity_hash FROM room_master_table WHERE id = 42", null, signal).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == identity.roomIdentityHash) { "Room schema 身份不匹配" }
            }
        }
    }

    private fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                hash.update(buffer, 0, count)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }

}
