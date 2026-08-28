package com.kairowan.room_flow.metrics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 仅记录已接入的 RoomFlow 操作；不持有数据库、SQL、参数或用户标识。 */
class DatabaseMetrics {
    private val state = MutableStateFlow(MetricsSnapshot())
    val snapshot = state.asStateFlow()

    internal fun update(transform: (MetricsSnapshot) -> MetricsSnapshot) { state.update(transform) }
}
