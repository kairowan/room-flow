package com.kairowan.room_flow

import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

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
 * @Description: TODO 库级全局配置项：调度器 / 重试参数 / PRAGMA 调优开关 / 日志接入等。
 */
object RoomFlowConfig {
    /** 用于数据库相关工作的调度器（默认 IO）。 */
    @Volatile
    var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /** 当遇到 SQLITE_BUSY / "database is locked" 时的最大重试次数。 */
    @Volatile
    var busyRetries: Int = 6

    /** 初始重试延时（毫秒）。指数退避将从该值起步。 */
    @Volatile
    var busyInitialDelayMs: Long = 10

    /** 最大退避延时（毫秒）。 */
    @Volatile
    var busyMaxDelayMs: Long = 200

    /** 是否对维护/PRAGMA 操作输出更详细日志。 */
    @Volatile
    var verboseMaintenanceLog: Boolean = false

    /** 是否允许库自动设置常见 PRAGMA（如 WAL / foreign_keys 等）。 */
    @Volatile
    var allowPragmaTuning: Boolean = true

    /** 注入自定义日志实现。 */
    fun setLogger(logger: Trace.Logger) = Trace.setLogger(logger)
}

/**
 * 统一的“数据库繁忙”重试包装器：
 * - 只对异常信息包含 "database is locked"/"busy" 等关键字进行指数退避重试；
 * - 其他异常直接抛出；
 * - 最终仍失败则抛出最后一次异常。
 */
suspend inline fun <T> withBusyRetry(
    retries: Int = RoomFlowConfig.busyRetries,
    initialDelayMs: Long = RoomFlowConfig.busyInitialDelayMs,
    maxDelayMs: Long = RoomFlowConfig.busyMaxDelayMs,
    crossinline block: suspend () -> T
): T {
    var attempt = 0
    var delayMs = initialDelayMs.coerceAtLeast(1)
    var last: Throwable? = null
    while (attempt <= retries) {
        try {
            return block()
        } catch (t: Throwable) {
            val msg = t.message?.lowercase() ?: ""
            val isBusy = "database is locked" in msg || "busy" in msg || "database locked" in msg
            if (!isBusy || attempt == retries) {
                last = t
                break
            }
            Trace.w("RoomFlow", "数据库繁忙，准备重试 #$attempt，延时 ${delayMs}ms", t)
            kotlinx.coroutines.delay(delayMs)
            delayMs = (delayMs * 2).coerceAtMost(maxDelayMs)
            attempt++
            last = t
        }
    }
    throw last ?: IllegalStateException("withBusyRetry 失败但未捕获异常")
}

/**
 * 可在数据库 onOpen 时调用：设置推荐的 PRAGMA。
 * - journal_mode=WAL：更好的读写并发；
 * - synchronous=NORMAL：在移动设备上较为平衡的安全/性能；
 * - foreign_keys=ON：启用外键约束。
 * 多次调用是安全的（幂等）。
 */
fun tunePragmas(db: SupportSQLiteDatabase) {
    if (!RoomFlowConfig.allowPragmaTuning) return
    try {
        db.execSQL("PRAGMA journal_mode=WAL;")
        db.execSQL("PRAGMA synchronous=NORMAL;")
        db.execSQL("PRAGMA foreign_keys=ON;")
        if (RoomFlowConfig.verboseMaintenanceLog) {
            Trace.d("RoomFlow", "已应用 PRAGMA: WAL/NORMAL/foreign_keys=ON")
        }
    } catch (t: Throwable) {
        Trace.w("RoomFlow", "应用 PRAGMA 失败", t)
    }
}