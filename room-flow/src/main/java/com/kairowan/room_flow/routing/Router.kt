package com.kairowan.room_flow.routing

import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import java.util.concurrent.ConcurrentHashMap

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
 * @Description: TODO 多数据库/分库路由 & 失效聚合
 */

/**
 * 路由上下文：按用户/读写等维度路由
 */
data class RouteContext(
    val userId: String? = null,
    val role: Role = Role.READ,
    val hints: Map<String, Any?> = emptyMap()
)

enum class Role { READ, WRITE }

interface DbRouter {
    fun readable(ctx: RouteContext = RouteContext()): RoomDatabase
    fun writable(ctx: RouteContext = RouteContext()): RoomDatabase
}

/**
 * 简单分库实现（默认库 + 按 userId 分库）
 */
class SimpleDbRouter(private val defaultDb: RoomDatabase) : DbRouter {
    private val dbByUser = ConcurrentHashMap<String, RoomDatabase>()
    fun registerUserDb(userId: String, db: RoomDatabase) {
        dbByUser[userId] = db
    }

    override fun readable(ctx: RouteContext): RoomDatabase =
        ctx.userId?.let { dbByUser[it] } ?: defaultDb

    override fun writable(ctx: RouteContext): RoomDatabase =
        ctx.userId?.let { dbByUser[it] } ?: defaultDb
}

/**
 * 将多个数据库的 Invalidation 事件聚合为一个 Flow<Unit>
 */
fun aggregateInvalidations(dbs: Collection<RoomDatabase>, vararg tables: String): Flow<Unit> =
    channelFlow {
        val observers = mutableListOf<Pair<RoomDatabase, InvalidationTracker.Observer>>()
        dbs.forEach { db ->
            val ob = object : InvalidationTracker.Observer(tables) {
                override fun onInvalidated(tables: Set<String>) {
                    trySend(Unit)
                }
            }
            db.invalidationTracker.addObserver(ob)
            observers += db to ob
        }
        trySend(Unit)
        awaitClose { observers.forEach { (db, ob) -> db.invalidationTracker.removeObserver(ob) } }
    }.conflate()