package com.kairowan.room_flow.write

/** 重试会重新执行整个 block；实现必须保留取消和失败语义。 */
interface RetryPolicy {
    suspend fun <T> run(block: suspend () -> T): T
}
