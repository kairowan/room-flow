package com.kairowan.roomflow.verification

import androidx.paging.PagingConfig
import androidx.room.RoomDatabase
import com.kairowan.room_flow.crud.withTransactionRetry
import com.kairowan.room_flow.metrics.MetricsSnapshot
import com.kairowan.room_flow.metrics.RoomFlowMetrics
import com.kairowan.room_flow.sql.rawQueryList
import com.kairowan.room_flow.write.WriteQueue

/** 独立构建仅依赖本地发布坐标，不能靠 project 依赖或手动补 Room/Paging 掩盖元数据缺失。 */
class ConsumerSmoke {
    val paging = PagingConfig(pageSize = 20)
    suspend fun read(db: RoomDatabase): List<Long> = db.rawQueryList("SELECT 1") { it.getLong(0) }
    suspend fun transaction(db: RoomDatabase): Int = db.withTransactionRetry { 1 }
    fun metrics(db: RoomDatabase): MetricsSnapshot = RoomFlowMetrics.forDatabase(db).snapshot.value
    fun queue(db: RoomDatabase): WriteQueue = WriteQueue(db)
}
