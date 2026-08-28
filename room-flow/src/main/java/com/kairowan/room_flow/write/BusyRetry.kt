package com.kairowan.room_flow.write

import com.kairowan.room_flow.core.RoomFlowConfig
import com.kairowan.room_flow.core.withBusyRetry

class BusyRetry(
    private val retries: Int = RoomFlowConfig.busyRetries,
    private val initialDelayMs: Long = RoomFlowConfig.busyInitialDelayMs,
    private val maxDelayMs: Long = RoomFlowConfig.busyMaxDelayMs
) : RetryPolicy {
    override suspend fun <T> run(block: suspend () -> T): T =
        withBusyRetry(retries, initialDelayMs, maxDelayMs, block)
}
