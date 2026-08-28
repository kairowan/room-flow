package com.kairowan.room_flow.sql

import android.database.Cursor
import android.os.CancellationSignal
import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.kairowan.room_flow.core.RoomFlowConfig
import com.kairowan.room_flow.core.Trace
import com.kairowan.room_flow.core.withBusyRetry
import com.kairowan.room_flow.crud.inOpenTransaction
import com.kairowan.room_flow.flow.observeTables
import com.kairowan.room_flow.metrics.RoomFlowMetrics
import com.kairowan.room_flow.metrics.QueryObservation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

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
 * @Description: TODO 原生 SQL 工具
 */

/**
 * 执行任意 SQL（写操作）；可带绑定参数
 */
fun RoomDatabase.execSQL(sql: String, args: List<Any?> = emptyList()) {
    val bindings = snapshotBindings(args)
    runInTransaction {
        openHelper.writableDatabase.execSQL(sql, bindings)
    }
    RoomFlowMetrics.sampleSql(sql)
}

/**
 * 执行原生查询（读）
 */
fun RoomDatabase.rawQuery(query: SupportSQLiteQuery): Cursor {
    RoomFlowMetrics.sampleSql(query.sql)
    return this.query(query)
}

/** 同步查询的显式取消入口；调用方仍须 use 关闭 Cursor。 */
fun RoomDatabase.rawQuery(query: SupportSQLiteQuery, signal: CancellationSignal): Cursor {
    RoomFlowMetrics.sampleSql(query.sql)
    return this.query(query, signal)
}

/** 可取消的 IO 查询；mapper 仅映射当前行，不得返回 Cursor 或执行长时间阻塞工作。 */
suspend fun <T> RoomDatabase.rawQueryList(query: SupportSQLiteQuery, mapper: (Cursor) -> T): List<T> {
    suspend fun read(): List<T> {
        val items = mutableListOf<T>()
        consumeRows(query) { items.add(mapper(it)) }
        return items
    }
    // Room 已持有事务时保留其线程；失败交给外层事务决定是否整体重试。
    return if (inOpenTransaction()) read() else withContext(RoomFlowConfig.ioDispatcher) { withBusyRetry { read() } }
}

/**
 * 逐行消费，不积累整表、不自动重试；适用于写入导出流等不可重复的消费。
 * action 同步执行，不能泄露 Cursor/阻塞等待其他协程。异常/取消时输出可能不完整，由调用方丢弃临时产物。
 */
suspend fun RoomDatabase.rawQueryEach(query: SupportSQLiteQuery, action: (Cursor) -> Unit): Long =
    if (inOpenTransaction()) consumeRows(query, action)
    else withContext(RoomFlowConfig.ioDispatcher) { consumeRows(query, action) }

private suspend fun RoomDatabase.consumeRows(query: SupportSQLiteQuery, action: (Cursor) -> Unit): Long =
    withQueryCancellation { signal ->
        val observer = RoomFlowConfig.onQueryObserved
        val started = if (observer != null) System.nanoTime() else 0L
        var rows = 0L
        var failure: Throwable? = null
        try {
            rawQuery(query, signal).use { cursor ->
                while (true) {
                    signal.throwIfCanceled()
                    if (!cursor.moveToNext()) break
                    action(cursor)
                    rows++
                }
            }
            signal.throwIfCanceled()
            rows
        } catch (cause: Throwable) {
            failure = cause
            throw cause
        } finally {
            if (observer != null) {
                val observation = QueryObservation(
                    (System.nanoTime() - started) / 1_000_000.0, rows,
                    failure == null && !signal.isCanceled, signal.isCanceled,
                    failure?.javaClass?.simpleName
                )
                try { observer(observation) } catch (ignored: Exception) {
                    Trace.w("RoomFlow", "查询诊断回调失败", ignored)
                }
            }
        }
    }

suspend fun <T> RoomDatabase.rawQueryList(
    sql: String,
    args: List<Any?> = emptyList(),
    mapper: (Cursor) -> T
): List<T> = rawQueryList(SimpleSQLiteQuery(sql, snapshotBindings(args)), mapper)

/**
 * 执行原生查询（读，基于 SQL 字符串与参数）
 */
fun RoomDatabase.rawQuery(sql: String, args: List<Any?> = emptyList()): Cursor =
    rawQuery(SimpleSQLiteQuery(sql, snapshotBindings(args)))

/**
 * 将 Cursor 映射为列表；调用方负责关闭 Cursor
 */
inline fun <T> Cursor.mapList(mapper: (Cursor) -> T): List<T> {
    val out = ArrayList<T>(count.coerceAtLeast(0))
    while (moveToNext()) out += mapper(this)
    return out
}

/**
 * 原生 SQL 的 Flow：当 [tables] 任一表失效时，重新执行 SQL 并映射为 List<T>
 * @param mapper 从 Cursor 逐行映射为 T 的函数
 */
fun <T> RoomDatabase.rawQueryFlow(
    sql: String,
    args: List<Any?> = emptyList(),
    vararg tables: String,
    mapper: (Cursor) -> T
): Flow<List<T>> {
    val query = SimpleSQLiteQuery(sql, snapshotBindings(args))
    return observeTables(*tables).map { rawQueryList(query, mapper) }
}

// BLOB is the mutable SQLite binding type; preserve raw API value semantics for all other types.
private fun snapshotBindings(args: List<Any?>): Array<Any?> =
    args.map { if (it is ByteArray) it.copyOf() else it }.toTypedArray()

/** SQL 参数不能绑定标识符；使用 SQLite 双引号规则处理关键字和引号。 */
internal fun quoteIdentifier(name: String): String {
    require(name.isNotBlank() && '\u0000' !in name) { "SQL 标识符不能为空或含 NUL" }
    return "\"" + name.replace("\"", "\"\"") + "\""
}
