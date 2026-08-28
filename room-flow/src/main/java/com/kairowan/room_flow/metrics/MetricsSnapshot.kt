package com.kairowan.room_flow.metrics

/** 队列结果与事务包装操作是两个维度；取消结果不保证数据库没有提交。时长均为单调时钟毫秒。 */
data class MetricsSnapshot(
    val pending: Int = 0,
    val running: Int = 0,
    val completed: Long = 0,
    val failed: Long = 0,
    val cancelled: Long = 0,
    val rejected: Long = 0,
    val started: Long = 0,
    val totalWaitMs: Double = 0.0,
    val maxWaitMs: Double = 0.0,
    /** 成功返回的顶层事务包装操作（保留旧字段名），不是全库物理 COMMIT 次数。 */
    val transactions: Long = 0,
    /** 成功操作总耗时，含 Room 调度/事务等待及重试退避，不含 WriteQueue 排队等待。 */
    val totalTransactionMs: Double = 0.0,
    val maxTransactionMs: Double = 0.0,
    val slowTransactions: Long = 0,
    val failedTransactions: Long = 0,
    val cancelledTransactions: Long = 0,
    val transactionAttempts: Long = 0,
    val transactionRetries: Long = 0,
    /** 所有已结束操作（成功/失败/取消）的总耗时。 */
    val totalOperationMs: Double = 0.0,
    val maxOperationMs: Double = 0.0,
    /** 所有尝试的用户 block 耗时，含挂起；不含进入事务前等待与 commit 本身。 */
    val totalBlockMs: Double = 0.0
)
