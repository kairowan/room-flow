package com.kairowan.roomflow.verification

import androidx.room.RoomDatabase
import com.kairowan.room_flow.typed.delete
import com.kairowan.room_flow.typed.insert
import com.kairowan.room_flow.typed.query
import com.kairowan.room_flow.typed.select
import com.kairowan.room_flow.typed.update
import com.kairowan.room_flow.typed.projection
import com.kairowan.room_flow.typed.sumLong
import com.kairowan.room_flow.typed.startsWith
import kotlinx.coroutines.flow.first

/** Compile against the published core AAR and processor JAR, not project implementation dependencies. */
suspend fun typedConsumerSmoke(db: RoomDatabase): List<ConsumerEntry> {
    val id = db.insert(ConsumerEntryTable, ConsumerEntry(name = "sample"))
    db.update(ConsumerEntryTable).set(ConsumerEntryTable.name, "changed").where(ConsumerEntryTable.id.eq(id)).execute(maxAffectedRows = 1)
    val definition = ConsumerEntryTable.query()
        .whereIfNotNull(id) { ConsumerEntryTable.id.greaterThanOrEqual(it) }
    val query = db.select(definition.page(1, 20))
    query.firstOrNull()
    query.exists()
    query.count()
    query.totalCount()
    query.observe().first()
    query.project(projection(ConsumerEntryTable.id, ConsumerEntryTable.name) { key, name -> key to name }).pageResult(1, 20)
    query.pageResult(1, 20)
    query.aggregate(ConsumerEntryTable.id.sumLong())
    query.groupBy(ConsumerEntryTable.name, ConsumerEntryTable.id.sumLong())
    query.observeAggregate(ConsumerEntryTable.id.sumLong()).first()
    db.select(definition.seekAfter(null, 20).where(ConsumerEntryTable.name.startsWith("c"))).list()
    val result = db.select(query.toSpec().unpaged()).list()
    db.delete(ConsumerEntryTable).where(ConsumerEntryTable.id.eq(id)).execute(maxAffectedRows = 1)
    return result
}
