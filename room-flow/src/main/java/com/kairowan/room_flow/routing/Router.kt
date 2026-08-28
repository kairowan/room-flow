package com.kairowan.room_flow.routing

import androidx.room.RoomDatabase
import com.kairowan.room_flow.flow.observeTables
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.merge

/** 将多个数据库的表失效事件聚合为一个 Flow。 */
fun aggregateInvalidations(dbs: Collection<RoomDatabase>, vararg tables: String): Flow<Unit> =
    merge(*dbs.distinct().map { it.observeTables(*tables) }.toTypedArray()).conflate()
