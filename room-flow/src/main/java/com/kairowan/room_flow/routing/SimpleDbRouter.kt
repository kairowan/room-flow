package com.kairowan.room_flow.routing

import androidx.room.RoomDatabase
import java.util.concurrent.ConcurrentHashMap

/** 默认库及用户库路由；未注册的用户不能回落到默认库。 */
class SimpleDbRouter(private val defaultDb: RoomDatabase) : DbRouter {
    private val dbByUser = ConcurrentHashMap<String, RoomDatabase>()

    fun registerUserDb(userId: String, db: RoomDatabase) {
        require(userId.isNotBlank()) { "用户 ID 不能为空" }
        val previous = dbByUser.putIfAbsent(userId, db)
        check(previous == null || previous === db) { "请先注销旧的用户数据库，不能静默替换" }
    }

    /** 只移除路由，不关闭可能共享的数据库；已取得的引用仍由调用方协调停用。 */
    fun unregisterUserDb(userId: String): RoomDatabase? = dbByUser.remove(userId)

    override fun readable(ctx: RouteContext): RoomDatabase =
        ctx.userId?.let { requireNotNull(dbByUser[it]) { "用户数据库尚未注册" } } ?: defaultDb

    override fun writable(ctx: RouteContext): RoomDatabase = readable(ctx)
}
