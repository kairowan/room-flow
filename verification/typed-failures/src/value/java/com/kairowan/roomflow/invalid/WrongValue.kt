package com.kairowan.roomflow.invalid

import androidx.room.RoomDatabase
import com.kairowan.room_flow.typed.update

fun rejectWrongValue(db: RoomDatabase) = db.update(ProbeEntityTable).set(ProbeEntityTable.name, 123)
