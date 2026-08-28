package com.kairowan.room_flow.metrics

/** 单次游标消费（含映射/关闭，不含 IO 排队与重试退避），不包含 SQL、绑定值或异常文本。 */
data class QueryObservation(
    val elapsedMs: Double,
    val rowsConsumed: Long,
    val succeeded: Boolean,
    val cancelled: Boolean,
    val failureCategory: String?
)
