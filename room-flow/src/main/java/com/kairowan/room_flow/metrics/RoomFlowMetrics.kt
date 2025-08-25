package com.kairowan.room_flow.metrics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 * @Description: TODO 指标：busy 重试、事务计数/平均耗时、checkpoint 次数、最近 SQL 采样
 */
object RoomFlowMetrics {
    private val _busyRetryCount = MutableStateFlow(0L)
    private val _txCount = MutableStateFlow(0L)
    private val _txAvgMs = MutableStateFlow(0.0)
    private val _checkpointCount = MutableStateFlow(0L)
    private val _recentSql = ArrayDeque<String>()
    private val _recentSqlFlow = MutableStateFlow<List<String>>(emptyList())

    val busyRetryCount = _busyRetryCount.asStateFlow()
    val txCount = _txCount.asStateFlow()
    val txAvgMs = _txAvgMs.asStateFlow()
    val checkpointCount = _checkpointCount.asStateFlow()
    val recentSql = _recentSqlFlow.asStateFlow()

    private const val SAMPLE_MAX = 50

    fun recordBusyRetry() {
        _busyRetryCount.value = _busyRetryCount.value + 1
    }

    fun recordTx(ms: Double) {
        val n = _txCount.value + 1
        _txCount.value = n
        _txAvgMs.value = ((_txAvgMs.value * (n - 1)) + ms) / n
    }

    fun recordCheckpoint() {
        _checkpointCount.value = _checkpointCount.value + 1
    }

    fun sampleSql(sql: String) {
        synchronized(_recentSql) {
            _recentSql.addLast(sql)
            while (_recentSql.size > SAMPLE_MAX) _recentSql.removeFirst()
            _recentSqlFlow.value = _recentSql.toList()
        }
    }
}