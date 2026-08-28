package com.kairowan.room_flow

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/** 保留旧入口名称；打开失败关闭实例并抛出异常，此入口不执行删库恢复。 */
object SelfHealingRoom {
    /** 阻塞打开数据库，调用者必须在后台线程执行。 */
    fun <T : RoomDatabase> build(
        context: Context,
        klass: Class<T>,
        name: String,
        configure: (RoomDatabase.Builder<T>) -> RoomDatabase.Builder<T> = { it }
    ): T {
        val db = configure(Room.databaseBuilder(context.applicationContext, klass, name)).build()
        try {
            db.assertNotMainThread()
            db.openHelper.writableDatabase
            return db
        } catch (failure: Throwable) {
            try {
                db.close()
            } catch (closeFailure: Throwable) {
                failure.addSuppressed(closeFailure)
            }
            throw failure
        }
    }
}
