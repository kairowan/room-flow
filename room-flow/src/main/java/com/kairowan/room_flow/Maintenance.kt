package com.kairowan.room_flow

import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.withContext

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
 * @Description: TODO 数据库维护工具
 */
/** 触发 WAL checkpoint(TRUNCATE)，返回被 checkpoint 的页数。 */
suspend fun RoomDatabase.walCheckpointTruncate(): Int = withContext(RoomFlowConfig.ioDispatcher) {
    withBusyRetry {
        val c =
            openHelper.writableDatabase.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(TRUNCATE)"))
        c.use {
            // PRAGMA 返回 (busy, log, checkpointed) 三列；这里取第 3 列（索引 2）。
            if (it.moveToFirst()) it.getInt(2) else 0
        }
    }
}

/** 完整性检查：若所有行均为 "ok"（忽略大小写），则返回 true。 */
suspend fun RoomDatabase.integrityCheck(): Boolean = withContext(RoomFlowConfig.ioDispatcher) {
    withBusyRetry {
        openHelper.readableDatabase.query(SimpleSQLiteQuery("PRAGMA integrity_check")).use {
            var ok = true
            while (it.moveToNext()) {
                val s = it.getString(0)
                if (!s.equals("ok", ignoreCase = true)) ok = false
            }
            ok
        }
    }
}

/** 让 SQLite 重新分析统计信息（可帮助优化查询计划）。 */
suspend fun RoomDatabase.analyze() = withContext(RoomFlowConfig.ioDispatcher) {
    withBusyRetry { openHelper.writableDatabase.execSQL("ANALYZE;") }
}

/** 整理数据库文件（可能比较耗时/阻塞，择时执行）。 */
suspend fun RoomDatabase.vacuum() = withContext(RoomFlowConfig.ioDispatcher) {
    withBusyRetry { openHelper.writableDatabase.execSQL("VACUUM;") }
}

/** 通过 PRAGMA page_count/page_size 估算数据库大小（字节）。 */
suspend fun RoomDatabase.estimatedDbSizeBytes(): Long = withContext(RoomFlowConfig.ioDispatcher) {
    withBusyRetry {
        var pageCount = 0L
        var pageSize = 0L
        openHelper.readableDatabase.query(SimpleSQLiteQuery("PRAGMA page_count")).use {
            if (it.moveToFirst()) pageCount = it.getLong(0)
        }
        openHelper.readableDatabase.query(SimpleSQLiteQuery("PRAGMA page_size")).use {
            if (it.moveToFirst()) pageSize = it.getLong(0)
        }
        pageCount * pageSize
    }
}