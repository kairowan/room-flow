package com.kairowan.room_flow.crud

import androidx.room.RoomDatabase
import com.kairowan.room_flow.core.RoomFlowConfig
import com.kairowan.room_flow.core.withBusyRetry
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
 * @Description: TODO 常用的事务与读/写操作，并引入“繁忙重试”策略。
 *  - withTransactionRetry：包裹事务逻辑，出现 lock/busy 自动退避重试；
 *  - readQuery：IO 线程安全执行只读查询；
 *  - write： 非事务写入，建议优先使用事务版本。
 */

/** 事务 + 繁忙重试。 */
suspend inline fun <T> RoomDatabase.withTransactionRetry(
    crossinline block: suspend () -> T
): T = withContext(RoomFlowConfig.ioDispatcher) {
    withBusyRetry {
        beginTransaction()
        try {
            val r = block()
            setTransactionSuccessful()
            r
        } finally {
            endTransaction()
        }
    }
}

/**
 * 读查询（IO 线程 + 繁忙重试）
 */
suspend inline fun <T> RoomDatabase.readQuery(crossinline block: () -> T): T =
    withContext(RoomFlowConfig.ioDispatcher) {
        withBusyRetry { block() }
    }

/**
 * 非事务写（IO 线程 + 繁忙重试）
 */
suspend inline fun <T> RoomDatabase.write(crossinline block: () -> T): T =
    withContext(RoomFlowConfig.ioDispatcher) {
        withBusyRetry { block() }
    }