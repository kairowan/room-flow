package com.kairowan.room_flow.multiprocess

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

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
 * @Description: TODO 多进程/ContentObserver 通知
 */
class CrossProcessInvalidation(
    context: Context,
    private val authority: String
) {
    private val context = context.applicationContext

    init {
        require(authority.isNotBlank() && '/' !in authority) { "无效的 Provider authority" }
        require(context.packageManager.resolveContentProvider(authority, 0) != null) { "必须注册真实 ContentProvider" }
    }

    private fun uri(table: String): Uri {
        require(table.isNotBlank()) { "表名不能为空" }
        return Uri.Builder().scheme("content").authority(authority).appendPath("roomflow").appendPath(table).build()
    }
    fun notifyChanged(table: String) {
        context.contentResolver.notifyChange(uri(table), null)
    }

    fun changes(table: String): Flow<Unit> = callbackFlow {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(uri(table), true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()
}
