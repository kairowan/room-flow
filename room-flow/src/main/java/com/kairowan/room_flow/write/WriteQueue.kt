package com.kairowan.room_flow.write

import androidx.room.RoomDatabase
import com.kairowan.room_flow.core.RoomFlowConfig
import com.kairowan.room_flow.core.Trace
import com.kairowan.room_flow.crud.inOpenTransaction
import com.kairowan.room_flow.metrics.RoomFlowMetrics
import com.kairowan.room_flow.metrics.runMeasuredTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 有界串行事务队列。满队列/关闭后提交返回失败的 Deferred；close 会取消排队及执行中的工作。
 * await 成功表示已提交。取消与提交并发时，取消不保证撤销已经提交的数据。
 * 同 key 只分组、不丢弃任务；可重试 block 不得包含外部副作用。
 */
class WriteQueue(
    private val db: RoomDatabase,
    private val capacity: Int = 64,
    private val retryPolicy: RetryPolicy = BusyRetry(),
    dispatcher: CoroutineDispatcher = RoomFlowConfig.ioDispatcher,
    private val onWriteCommitted: (() -> Unit)? = null,
    private val maxBatchSize: Int = 1_000
) : AutoCloseable {
    init {
        require(capacity in 1 until Int.MAX_VALUE) { "capacity 必须是有限正数" }
        require(maxBatchSize > 0) { "maxBatchSize 必须为正数" }
    }

    private val parent = SupervisorJob()
    private val scope = CoroutineScope(parent + dispatcher)
    private val channel = Channel<suspend () -> Unit>(capacity, onUndeliveredElement = { RoomFlowMetrics.queuePending(db, -1) })
    private val coalesceMap = linkedMapOf<Any, MutableList<suspend () -> Unit>>()
    private var coalescedCount = 0
    private var accepting = true
    private val executing = ThreadLocal<Boolean>()

    init {
        scope.launch(executing.asContextElement(true)) {
            for (op in channel) op()
        }.invokeOnCompletion { failure ->
            if (failure != null) parent.cancel(CancellationException("WriteQueue 消费者已停止", failure))
            else parent.complete()
        }
    }

    fun <T> submit(block: suspend () -> T): Deferred<T> = enqueue(block)

    fun <I, R> submitAll(
        items: List<I>,
        keySelector: (I) -> Any = { "default" },
        op: suspend (List<I>) -> List<R>
    ): Deferred<List<R>> {
        require(items.size <= maxBatchSize) { "批次超过 maxBatchSize；请显式分批并决定事务边界" }
        val snapshot = items.toList()
        return submit { snapshot.groupBy(keySelector).values.flatMap { op(it) } }
    }

    private fun <T> enqueue(
        block: suspend () -> T,
        onAccepted: () -> Unit = {}
    ): Deferred<T> = synchronized(coalesceMap) {
        if (!accepting || !parent.isActive) {
            RoomFlowMetrics.queueRejected(db)
            return@synchronized CompletableDeferred<T>().apply { completeExceptionally(QueueClosedException()) }
        }
        val result = CompletableDeferred<T>(parent)
        if (executing.get() == true || db.inOpenTransaction()) {
            RoomFlowMetrics.queueRejected(db)
            result.completeExceptionally(IllegalStateException("禁止从队列任务或数据库事务中再次入队，请直接执行 DAO 操作"))
            return@synchronized result
        }
        val enqueuedAt = System.nanoTime()
        RoomFlowMetrics.queuePending(db, 1)
        val sent = channel.trySend {
            RoomFlowMetrics.queuePending(db, -1)
            if (result.isActive) {
                RoomFlowMetrics.queueRunning(db, 1)
                RoomFlowMetrics.queueStarted(db, (System.nanoTime() - enqueuedAt) / 1_000_000.0)
                try {
                    val value = withContext(result) {
                        db.runMeasuredTransaction(retryPolicy, block)
                    }
                    // 回调发生在 commit 后，但回调失败不能把已提交事务伪装成失败。
                    try {
                        onWriteCommitted?.invoke()
                    } catch (failure: Exception) {
                        Trace.e("RoomFlow", "提交后回调失败", failure)
                    }
                    result.complete(value)
                } catch (failure: Throwable) {
                    result.completeExceptionally(failure)
                    if (failure is Error) throw failure
                } finally {
                    RoomFlowMetrics.queueRunning(db, -1)
                }
            }
        }
        if (sent.isSuccess) {
            result.invokeOnCompletion { RoomFlowMetrics.queueFinished(db, it) }
            onAccepted()
        } else {
            RoomFlowMetrics.queuePending(db, -1)
            RoomFlowMetrics.queueRejected(db)
            result.completeExceptionally(if (sent.isClosed) QueueClosedException() else QueueFullException())
        }
        result
    }

    /** 暂存最多 capacity 个任务；需要 flushCoalesced 才执行，不自动去重。 */
    fun coalesce(key: Any, task: suspend () -> Unit) {
        check(executing.get() != true && !db.inOpenTransaction()) { "禁止从队列任务或数据库事务中暂存任务" }
        synchronized(coalesceMap) {
            check(accepting && parent.isActive) { "WriteQueue 已关闭或正在排空" }
            check(coalescedCount < capacity) { "合并缓冲区已满，请先 flushCoalesced" }
            coalesceMap.getOrPut(key) { mutableListOf() }.add(task)
            coalescedCount++
        }
    }

    /** 一次 flush 是一个原子事务；入队失败时保留暂存任务以便重试。 */
    fun flushCoalesced(): Deferred<Unit> = synchronized(coalesceMap) {
        val snapshot = coalesceMap.values.flatMap { it.toList() }
        enqueue(
            block = { snapshot.forEach { it() } },
            onAccepted = {
                coalesceMap.clear()
                coalescedCount = 0
            }
        )
    }

    override fun close() {
        synchronized(coalesceMap) {
            accepting = false
            channel.cancel(CancellationException("WriteQueue 已关闭"))
            scope.cancel()
            coalesceMap.clear()
            coalescedCount = 0
        }
    }

    /** 取消后等待所有任务退出，再由数据库所有者关库。不能从本队列内部等待自身。 */
    suspend fun closeAndJoin() {
        check(executing.get() != true && !db.inOpenTransaction()) { "不能在队列任务或数据库事务内等待关闭" }
        close()
        parent.join()
    }

    /**
     * 拒绝新任务后排空已接收任务。先显式 flush 暂存任务，否则拒绝启动排空而不改变状态。
     * 单项失败不阻止其余任务；调用方仍须 await 各项结果。取消等待不取消排空，需中止时另调 closeAndJoin。
     */
    suspend fun drainAndJoin(): Unit {
        check(executing.get() != true && !db.inOpenTransaction()) { "不能在队列任务或数据库事务内等待排空" }
        synchronized(coalesceMap) {
            check(coalescedCount == 0) { "排空前必须显式 flushCoalesced 并检查提交结果" }
            accepting = false
            channel.close()
        }
        parent.join()
    }
}
