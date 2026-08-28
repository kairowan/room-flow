package com.kairowan.room_flow.sql

import android.database.Cursor
import android.os.CancellationSignal
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** 保留当前 Room 事务线程；调用者负责选择 IO/事务上下文，且不能返回 Cursor 等待关闭的资源。 */
internal suspend fun <T> withQueryCancellation(block: (CancellationSignal) -> T): T =
    suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        try {
            signal.throwIfCanceled()
            val value = block(signal)
            signal.throwIfCanceled()
            continuation.resume(value)
        } catch (failure: Exception) {
            continuation.resumeWithException(failure)
        }
    }

internal fun <T> Cursor.mapCancellable(signal: CancellationSignal, mapper: (Cursor) -> T): List<T> =
    buildList {
        while (true) {
            signal.throwIfCanceled()
            if (!moveToNext()) break
            add(mapper(this@mapCancellable))
        }
    }
