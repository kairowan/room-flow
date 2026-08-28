package com.kairowan.roomflow.invalid

import com.kairowan.room_flow.typed.sumLong

fun rejectNonNumericSum() = ProbeEntityTable.name.sumLong()
