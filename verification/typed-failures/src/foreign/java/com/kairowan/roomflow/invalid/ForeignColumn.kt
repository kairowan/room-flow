package com.kairowan.roomflow.invalid

import androidx.room.RoomDatabase
import com.kairowan.room_flow.typed.select

fun rejectForeignColumn(db: RoomDatabase) = db.select(ProbeEntityTable).where(OtherProbeTable.id.eq(1L))
