package com.kairowan.room_flow.core

import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteTableLockedException
import com.kairowan.room_flow.metrics.RoomFlowMetrics
import com.kairowan.room_flow.metrics.QueryObservation
import kotlinx.coroutines.CancellationException
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

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

    /** 是否允许显式调用 tunePragmas 时设置 synchronous=NORMAL。 */
    @Volatile
    var allowPragmaTuning: Boolean = true

    /** 仅在可信诊断环境显式开启；异常消息、cause 和 suppressed 可能含敏感数据。 */
    @Volatile
    var logExceptionDetails: Boolean = false

    /** 可选 rawQueryList/rawQueryEach 诊断；快速非阻塞回调，由宿主按生命周期设置/清空。 */
    @Volatile
    var onQueryObserved: ((QueryObservation) -> Unit)? = null

    /** 默认无日志；注入接收器后仍默认不传原始异常，消息/标签由调用方保证不含敏感数据。 */
    fun setLogger(logger: Logger) = Trace.setLogger(logger)
}

/**
 * 统一的“数据库繁忙”重试包装器：
 * - 只对 Android SQLite 的数据库/表锁异常进行指数退避重试；
 * - 其他异常直接抛出；
 * - 最终仍失败则抛出最后一次异常。
 */
suspend inline fun <T> withBusyRetry(
    retries: Int = RoomFlowConfig.busyRetries,
    initialDelayMs: Long = RoomFlowConfig.busyInitialDelayMs,
    maxDelayMs: Long = RoomFlowConfig.busyMaxDelayMs,
    crossinline block: suspend () -> T
): T {
    require(retries >= 0) { "retries 必须 >= 0" }
    require(initialDelayMs > 0 && maxDelayMs >= initialDelayMs) { "重试延迟范围无效" }
    var attempt = 0
    var delayMs = initialDelayMs
    while (true) {
        try {
            return block()
        } catch (t: CancellationException) {
            throw t
        } catch (t: Exception) {
            if (t !is SQLiteDatabaseLockedException && t !is SQLiteTableLockedException) throw t
            if (attempt == retries) throw t
            RoomFlowMetrics.recordBusyRetry()
            Trace.w("RoomFlow", "数据库繁忙，准备重试 #$attempt，延时 ${delayMs}ms", t)
            delay(delayMs)
            delayMs = if (delayMs > maxDelayMs / 2) maxDelayMs else delayMs * 2
            attempt++
        }
    }
}

/**
 * 可在数据库 onOpen 时调用：设置推荐的 PRAGMA。
 * - synchronous=NORMAL：在移动设备上较为平衡的安全/性能；
 * - WAL 和外键由 Room/OpenHelper 管理。NORMAL 在断电时可能丢失最近提交，重要数据不要启用此调优。
 * 多次调用是安全的（幂等）。
 */
fun tunePragmas(db: SupportSQLiteDatabase) {
    if (!RoomFlowConfig.allowPragmaTuning) return
    // ponytail: journal mode 和外键由 Room/OpenHelper 管理，不修改连接池级别配置。
    db.execSQL("PRAGMA synchronous=NORMAL")
    if (RoomFlowConfig.verboseMaintenanceLog) {
        Trace.d("RoomFlow", "已应用 PRAGMA synchronous=NORMAL")
    }
}
