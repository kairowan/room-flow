package com.kairowan.room_flow

import androidx.room.RoomDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED


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
 * @Description: TODO 写入串行化队列
 */
class WriteQueue(
    private val db: RoomDatabase,
    capacity: Int = UNLIMITED,
    private val retryPolicy: RetryPolicy = RetryPolicy.BusyRetry(),
    dispatcher: CoroutineDispatcher = RoomFlowConfig.ioDispatcher,
    private val onWriteCommitted: (() -> Unit)? = null
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val channel = Channel<suspend () -> Unit>(capacity)
    private val coalesceMap = mutableMapOf<Any, MutableList<suspend () -> Unit>>()

    init {
        scope.launch {
            for (op in channel) {
                try {
                    retryPolicy.run {
                        db.beginTransaction()
                        try {
                            op()
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }
                    }
                    onWriteCommitted?.invoke()
                } catch (t: Throwable) {
                    Trace.e("RoomFlow", "WriteQueue 任务执行失败", t)
                }
            }
        }
    }

    fun <T> submit(block: suspend () -> T): Deferred<T> {
        val d = CompletableDeferred<T>()
        scope.launch {
            channel.send {
                try {
                    d.complete(block())
                } catch (t: Throwable) {
                    d.completeExceptionally(t)
                }
            }
        }
        return d
    }

    fun <I, R> submitAll(
        items: List<I>,
        keySelector: (I) -> Any = { "default" },
        op: suspend (List<I>) -> List<R>
    ): Deferred<List<R>> {
        val d = CompletableDeferred<List<R>>()
        scope.launch {
            channel.send {
                try {
                    val grouped = items.groupBy(keySelector)
                    val result = mutableListOf<R>()
                    for ((_, group) in grouped) result += op(group)
                    onWriteCommitted?.invoke()
                    d.complete(result)
                } catch (t: Throwable) {
                    d.completeExceptionally(t)
                }
            }
        }
        return d
    }

    /** 同 key 合并：先缓存，flush 时每个 key 一次事务。 */
    fun coalesce(key: Any, task: suspend () -> Unit) {
        synchronized(coalesceMap) { coalesceMap.getOrPut(key) { mutableListOf() } += task }
    }

    fun flushCoalesced(): Deferred<Unit> {
        val snapshot: Map<Any, List<suspend () -> Unit>> = synchronized(coalesceMap) {
            val copy = coalesceMap.mapValues { it.value.toList() }; coalesceMap.clear(); copy
        }
        val d = CompletableDeferred<Unit>()
        scope.launch {
            channel.send {
                try {
                    retryPolicy.run {
                        for ((_, list) in snapshot) {
                            db.beginTransaction()
                            try {
                                for (op in list) op(); db.setTransactionSuccessful()
                            } finally {
                                db.endTransaction()
                            }
                        }
                    }
                    onWriteCommitted?.invoke()
                    d.complete(Unit)
                } catch (t: Throwable) {
                    d.completeExceptionally(t)
                }
            }
        }
        return d
    }

    override fun close() {
        channel.close(); scope.cancel()
    }
}

/** 重试策略接口；默认 BusyRetry = withBusyRetry。 */
interface RetryPolicy {
    suspend fun <T> run(block: suspend () -> T): T
    class BusyRetry(
        private val retries: Int = RoomFlowConfig.busyRetries,
        private val initialDelayMs: Long = RoomFlowConfig.busyInitialDelayMs,
        private val maxDelayMs: Long = RoomFlowConfig.busyMaxDelayMs
    ) : RetryPolicy {
        override suspend fun <T> run(block: suspend () -> T): T =
            withBusyRetry(retries, initialDelayMs, maxDelayMs, block)
    }
}