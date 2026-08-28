package com.kairowan.room_flow.crud

import androidx.room.RoomDatabase
import com.kairowan.room_flow.core.RoomFlowConfig
import com.kairowan.room_flow.core.withBusyRetry
import com.kairowan.room_flow.metrics.runMeasuredTransaction
import com.kairowan.room_flow.write.BusyRetry
import kotlinx.coroutines.withContext

/** 整个事务重试；block 不能包含网络请求等不可重试的外部副作用。 */
suspend inline fun <T> RoomDatabase.withTransactionRetry(crossinline block: suspend () -> T): T =
    runMeasuredTransaction(BusyRetry()) { block() }

/** 事务内保持当前线程且不局部重试；事务外切换 IO。长 SQL 使用 rawQueryList，Cursor 在 block 内关闭。 */
suspend inline fun <T> RoomDatabase.readQuery(crossinline block: () -> T): T =
    if (inOpenTransaction()) block()
    else withContext(RoomFlowConfig.ioDispatcher) { withBusyRetry { block() } }

/** Room.inTransaction() can open the database; a context check must not trigger main-thread IO. */
@PublishedApi
internal fun RoomDatabase.inOpenTransaction(): Boolean = isOpen && inTransaction()

/** 原生写入也经过 Room 事务，使失效通知与提交保持一致。 */
suspend inline fun <T> RoomDatabase.write(crossinline block: () -> T): T =
    withTransactionRetry { block() }
