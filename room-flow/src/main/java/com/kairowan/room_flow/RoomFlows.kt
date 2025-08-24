package com.kairowan.room_flow

import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

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
 * @Description: TODO 基于 Room InvalidationTracker 的 Flow 工具：
 */
/** 监听一个或多个表的“失效”事件，每次变更时发射 Unit。 */
fun RoomDatabase.observeTables(vararg tables: String): Flow<Unit> = channelFlow {
    val observer = object : InvalidationTracker.Observer(tables) {
        override fun onInvalidated(tables: Set<String>) {
            trySend(Unit)
        }
    }
    invalidationTracker.addObserver(observer)
    // 初始发射一次，方便收集端立即拉取数据
    trySend(Unit)
    awaitClose { invalidationTracker.removeObserver(observer) }
}.conflate()

/**
 * 构造一个 Flow：当 [tables] 中任意表失效时，使用 [query] 函数重新查询并发射。
 * - [query] 需为阻塞式查询（内部会自动放到 dispatcher 并做繁忙重试）；
 * - 初始也会执行一次查询并发射。
 */
fun <T> RoomDatabase.flowQuery(
    vararg tables: String,
    dispatcher: CoroutineDispatcher = RoomFlowConfig.ioDispatcher,
    query: RoomDatabase.() -> T
): Flow<T> = channelFlow {
    val dbRef = this@flowQuery
    val obs = object : InvalidationTracker.Observer(tables) {
        override fun onInvalidated(tables: Set<String>) {
            launch(dispatcher) {
                val result = withBusyRetry { dbRef.query() }
                trySend(result)
            }
        }
    }
    invalidationTracker.addObserver(obs)
    // 初始发射一次
    launch(dispatcher) {
        val initial = withBusyRetry { query() }
        trySend(initial)
    }
    awaitClose { invalidationTracker.removeObserver(obs) }
}.conflate()