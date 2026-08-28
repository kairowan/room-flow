package com.kairowan.room_flow.metrics

import androidx.room.RoomDatabase
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** 仅在操作存活期间标记同库嵌套；随协程传播，不把线程切换误认为新事务。 */
internal class TransactionMetricsContext(val databases: Set<RoomDatabase>) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TransactionMetricsContext>
}
