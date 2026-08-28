package com.kairowan.room_flow.maintenance.checkpoint

import android.os.SystemClock
import androidx.room.RoomDatabase
import com.kairowan.room_flow.core.RoomFlowConfig
import com.kairowan.room_flow.core.Trace
import com.kairowan.room_flow.maintenance.walCheckpointTruncate
import com.kairowan.room_flow.metrics.RoomFlowMetrics
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 仅支持普通文件 WAL；所有写入方须接入 onWriteCommitted，空闲判断才有意义。 */
class WalCheckpointScheduler(
    private val db: RoomDatabase,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + RoomFlowConfig.ioDispatcher),
    private val pollIntervalMs: Long = 2_000,
    private val minIdleMs: Long = 3_000,
    private val minIntervalMs: Long = 5_000,
    private val walPagesThreshold: Int = 128
) {
    init {
        require(pollIntervalMs > 0 && minIdleMs >= 0 && minIntervalMs >= 0 && walPagesThreshold > 0)
    }

    private val _walPages = MutableStateFlow(0)
    private val _lastCheckpointAt = MutableStateFlow(0L)
    private val _checkpointCount = MutableStateFlow(0L)
    private val _lastWriteAt = MutableStateFlow(SystemClock.elapsedRealtime())
    private var lastCheckpointElapsed = 0L
    val walPages = _walPages.asStateFlow()
    val lastCheckpointAt = _lastCheckpointAt.asStateFlow()
    val checkpointCount = _checkpointCount.asStateFlow()
    private var job: Job? = null

    fun onWriteCommitted() { _lastWriteAt.value = SystemClock.elapsedRealtime() }

    @Synchronized
    fun start() {
        if (job?.isCompleted == false) return
        job = scope.launch(RoomFlowConfig.ioDispatcher) {
            while (isActive) {
                try {
                    val pages = estimatedWalPages()
                    _walPages.value = pages
                    val now = SystemClock.elapsedRealtime()
                    if (pages >= walPagesThreshold &&
                        now - _lastWriteAt.value >= minIdleMs &&
                        now - lastCheckpointElapsed >= minIntervalMs) {
                        db.walCheckpointTruncate()
                        lastCheckpointElapsed = SystemClock.elapsedRealtime()
                        _lastCheckpointAt.value = System.currentTimeMillis()
                        _checkpointCount.value += 1
                        _walPages.value = estimatedWalPages()
                        RoomFlowMetrics.recordCheckpoint()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Trace.w("RoomFlow", "WAL 调度器轮询失败", failure)
                }
                delay(pollIntervalMs)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        // 保留 Job，防止上次阻塞 checkpoint 尚未退出时 start 又启动第二个。
    }

    /** 调用方停止其他 start 调用后等待轮询退出，再关闭数据库。 */
    suspend fun stopAndJoin() {
        val stopping = synchronized(this) {
            job?.also { it.cancel() }
        }
        stopping?.join()
    }

    private fun estimatedWalPages(): Int {
        val database = db.openHelper.writableDatabase
        if (!database.isWriteAheadLoggingEnabled) return 0
        val path = database.path
        if (path.isNullOrEmpty() || path == ":memory:") return 0
        val file = File("$path-wal")
        if (file.length() < 32) return 0
        // ponytail: 文件长度仅估算已分配帧，可能包含复用的旧帧；精确统计需要 driver 提供无副作用接口。
        return RandomAccessFile(file, "r").use {
            it.seek(8)
            val rawPageSize = it.readInt()
            val pageSize = if (rawPageSize == 1) 65_536 else rawPageSize
            check(pageSize in 512..65_536 && pageSize.countOneBits() == 1) { "无效 WAL 页大小" }
            ((it.length() - 32).coerceAtLeast(0) / (pageSize + 24)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }
}
