package com.kairowan.room_flow.flow

import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import com.kairowan.room_flow.core.RoomFlowConfig
import com.kairowan.room_flow.core.withBusyRetry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** 订阅后先发射一次，再合并表失效事件；观察者在取消时移除。 */
fun RoomDatabase.observeTables(vararg tables: String): Flow<Unit> {
    require(tables.isNotEmpty() && tables.all { it.isNotBlank() }) { "必须指定观察表" }
    return callbackFlow {
        val observer = object : InvalidationTracker.Observer(tables) {
            override fun onInvalidated(tables: Set<String>) {
                trySend(Unit)
            }
        }
        invalidationTracker.addObserver(observer)
        trySend(Unit)
        awaitClose { invalidationTracker.removeObserver(observer) }
    }.conflate().flowOn(RoomFlowConfig.ioDispatcher)
}

/** 串行重查；通用同步 block 不支持强制中断，长 SQL 使用带 CancellationSignal 的 rawQueryFlow。 */
fun <T> RoomDatabase.flowQuery(
    vararg tables: String,
    dispatcher: CoroutineDispatcher = RoomFlowConfig.ioDispatcher,
    query: RoomDatabase.() -> T
): Flow<T> = observeTables(*tables).map {
    withContext(dispatcher) { withBusyRetry { query() } }
}
