package com.kairowan.room_flow.metrics

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.kairowan.room_flow.write.RetryPolicy
import com.kairowan.room_flow.crud.inOpenTransaction
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext

/** 库包装上下文内去重；仅能识别调用点仍在事务线程的外部 Room 事务，不推断物理提交。 */
@PublishedApi
internal suspend fun <T> RoomDatabase.runMeasuredTransaction(policy: RetryPolicy, block: suspend () -> T): T {
    val parents = coroutineContext[TransactionMetricsContext]?.databases.orEmpty()
    if (this in parents || inOpenTransaction()) return withTransaction { block() }
    val started = System.nanoTime()
    var attempts = 0L
    var blockNanos = 0L
    var failure: Throwable? = null
    try {
        return withContext(TransactionMetricsContext(parents + this)) {
            policy.run {
                attempts++
                withTransaction {
                    val entered = System.nanoTime()
                    try { block() } finally { blockNanos += System.nanoTime() - entered }
                }
            }
        }
    } catch (cause: Throwable) {
        failure = cause
        throw cause
    } finally {
        RoomFlowMetrics.recordOperation(this, (System.nanoTime() - started) / 1_000_000.0,
            blockNanos / 1_000_000.0, attempts, failure)
    }
}
