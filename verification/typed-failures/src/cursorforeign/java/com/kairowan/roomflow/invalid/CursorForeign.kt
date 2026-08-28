package com.kairowan.roomflow.invalid

import com.kairowan.room_flow.typed.query

fun rejectForeignCursor() = ProbeEntityTable.query().seekAfter(OtherProbe(1), 20)
