package com.kairowan.room_flow.maintenance.checkpoint

import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import com.kairowan.room_flow.core.RoomFlowConfig
import com.kairowan.room_flow.core.Trace
import com.kairowan.room_flow.core.withBusyRetry
import com.kairowan.room_flow.maintenance.walCheckpointTruncate
import com.kairowan.room_flow.metrics.RoomFlowMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
 * @Description: TODO 智能 WAL checkpoint 调度
 */
class WalCheckpointScheduler(
    private val db: RoomDatabase,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + RoomFlowConfig.ioDispatcher),
    private val pollIntervalMs: Long = 2_000,
    private val minIdleMs: Long = 3_000,
    private val minIntervalMs: Long = 5_000,
    private val walPagesThreshold: Int = 128
) {
    private val _walPages = MutableStateFlow(0)
    private val _lastCheckpointAt = MutableStateFlow(0L)
    private val _checkpointCount = MutableStateFlow(0L)
    private val _lastWriteAt = MutableStateFlow(0L)

    val walPages = _walPages.asStateFlow()
    val lastCheckpointAt = _lastCheckpointAt.asStateFlow()
    val checkpointCount = _checkpointCount.asStateFlow()

    private var job: Job? = null

    /**
     * 写提交后回调（建议与 WriteQueue.onWriteCommitted 联动）
     */
    fun onWriteCommitted() {
        _lastWriteAt.value = System.currentTimeMillis()
    }

    fun start() {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                try {
                    val (busy, logPages, _) = walStatsPassive()
                    _walPages.value = logPages
                    val now = System.currentTimeMillis()
                    val idleOk = now - _lastWriteAt.value >= minIdleMs
                    val intervalOk = now - _lastCheckpointAt.value >= minIntervalMs
                    val sizeOk = logPages >= walPagesThreshold
                    if (idleOk && intervalOk && sizeOk) {
                        val cp = db.walCheckpointTruncate()
                        _lastCheckpointAt.value = now
                        _checkpointCount.value = _checkpointCount.value + 1
                        RoomFlowMetrics.recordCheckpoint()
                        Trace.d("RoomFlow", "WAL checkpoint: truncatePages=$cp, walPages=$logPages")
                    }
                } catch (t: Throwable) {
                    Trace.w("RoomFlow", "WAL 调度器轮询失败", t)
                } finally {
                    delay(pollIntervalMs)
                }
            }
        }
    }

    fun stop() {
        job?.cancel(); job = null
    }

    private data class WalStats(val busy: Int, val logPages: Int, val checkpointed: Int)

    private suspend fun walStatsPassive(): WalStats = withContext(RoomFlowConfig.ioDispatcher) {
        withBusyRetry {
            db.openHelper.writableDatabase
                .query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(PASSIVE)")).use {
                    if (it.moveToFirst()) WalStats(it.getInt(0), it.getInt(1), it.getInt(2))
                    else WalStats(0, 0, 0)
                }
        }
    }
}