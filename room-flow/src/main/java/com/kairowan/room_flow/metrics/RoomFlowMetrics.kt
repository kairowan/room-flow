package com.kairowan.room_flow.metrics

import androidx.room.RoomDatabase
import java.util.WeakHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
 * @Description: TODO 指标：busy 重试、事务计数/平均耗时、checkpoint 次数、最近 SQL 采样
 */
object RoomFlowMetrics {
    private val databases = WeakHashMap<RoomDatabase, DatabaseMetrics>()
    val overview = DatabaseMetrics()

    /** 按实例隔离；相同文件的两个实例也分别计数，弱引用不阻止数据库被回收。 */
    @Synchronized
    fun forDatabase(db: RoomDatabase): DatabaseMetrics = databases.getOrPut(db) { DatabaseMetrics() }

    private fun update(db: RoomDatabase, transform: (MetricsSnapshot) -> MetricsSnapshot) {
        forDatabase(db).update(transform)
        overview.update(transform)
    }

    internal fun queuePending(db: RoomDatabase, delta: Int) = update(db) { it.copy(pending = it.pending + delta) }
    internal fun queueRunning(db: RoomDatabase, delta: Int) = update(db) { it.copy(running = it.running + delta) }
    internal fun queueRejected(db: RoomDatabase) = update(db) { it.copy(rejected = it.rejected + 1) }
    internal fun queueStarted(db: RoomDatabase, waitMs: Double) = update(db) {
        it.copy(started = it.started + 1, totalWaitMs = it.totalWaitMs + waitMs, maxWaitMs = maxOf(it.maxWaitMs, waitMs))
    }
    internal fun queueFinished(db: RoomDatabase, failure: Throwable?) = update(db) {
        when (failure) {
            null -> it.copy(completed = it.completed + 1)
            is CancellationException -> it.copy(cancelled = it.cancelled + 1)
            else -> it.copy(failed = it.failed + 1)
        }
    }

    /** 手动上报一个成功包装操作，不代表捕获 SQLite COMMIT 事件。 */
    fun recordTx(db: RoomDatabase, ms: Double) = recordOperation(db, ms, 0.0, 1, null)

    internal fun recordOperation(db: RoomDatabase, ms: Double, blockMs: Double, attempts: Long, failure: Throwable?) {
        if (failure == null) recordTx(ms)
        update(db) {
            it.copy(
                transactions = it.transactions + if (failure == null) 1 else 0,
                totalTransactionMs = it.totalTransactionMs + if (failure == null) ms else 0.0,
                maxTransactionMs = if (failure == null) maxOf(it.maxTransactionMs, ms) else it.maxTransactionMs,
                slowTransactions = it.slowTransactions + if (failure == null && ms >= 100.0) 1 else 0,
                failedTransactions = it.failedTransactions + if (failure != null && failure !is CancellationException) 1 else 0,
                cancelledTransactions = it.cancelledTransactions + if (failure is CancellationException) 1 else 0,
                transactionAttempts = it.transactionAttempts + attempts,
                transactionRetries = it.transactionRetries + (attempts - 1).coerceAtLeast(0),
                totalOperationMs = it.totalOperationMs + ms,
                maxOperationMs = maxOf(it.maxOperationMs, ms),
                totalBlockMs = it.totalBlockMs + blockMs
            )
        }
    }
    private val _busyRetryCount = MutableStateFlow(0L)
    private val _txCount = MutableStateFlow(0L)
    private val _txAvgMs = MutableStateFlow(0.0)
    private val _checkpointCount = MutableStateFlow(0L)
    private val _recentSql = ArrayDeque<String>()
    private val _recentSqlFlow = MutableStateFlow<List<String>>(emptyList())

    val busyRetryCount = _busyRetryCount.asStateFlow()
    val txCount = _txCount.asStateFlow()
    val txAvgMs = _txAvgMs.asStateFlow()
    val checkpointCount = _checkpointCount.asStateFlow()
    val recentSql = _recentSqlFlow.asStateFlow()

    private const val SAMPLE_MAX = 50
    private val SQL_OPERATIONS = setOf("SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "ALTER", "DROP", "PRAGMA", "ANALYZE", "VACUUM")

    fun recordBusyRetry() {
        _busyRetryCount.update { it + 1 }
    }

    @Synchronized
    fun recordTx(ms: Double) {
        require(ms.isFinite() && ms >= 0) { "事务时长必须为有限非负数" }
        val n = _txCount.value + 1
        _txAvgMs.value = ((_txAvgMs.value * (n - 1)) + ms) / n
        _txCount.value = n
    }

    fun recordCheckpoint() {
        _checkpointCount.update { it + 1 }
    }

    fun sampleSql(sql: String) {
        synchronized(_recentSql) {
            // ponytail: 仅采样语句种类，避免 SQL 字面量或密钥泄露；详细 SQL 由调用方独立安全审计。
            val operation = sql.trimStart().takeWhile { it.isLetter() }.uppercase()
            _recentSql.addLast(operation.takeIf { it in SQL_OPERATIONS } ?: "SQL")
            while (_recentSql.size > SAMPLE_MAX) _recentSql.removeFirst()
            _recentSqlFlow.value = _recentSql.toList()
        }
    }
}
