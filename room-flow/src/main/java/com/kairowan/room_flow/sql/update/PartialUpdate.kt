package com.kairowan.room_flow.sql.update

import androidx.room.RoomDatabase
import com.kairowan.room_flow.metrics.RoomFlowMetrics
import java.util.concurrent.Callable

fun RoomDatabase.update(table: String, block: UpdateBuilder.() -> Unit): Int {
    val b = UpdateBuilder(table).apply(block)
    val q = b.toQuery()
    val changed = runInTransaction(Callable {
        openHelper.writableDatabase.compileStatement(q.sql).use { statement ->
            q.bindTo(statement)
            statement.executeUpdateDelete()
        }
    })
    RoomFlowMetrics.sampleSql(q.sql)
    return changed
}
