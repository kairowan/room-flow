package com.kairowan.roomflow.invalid

import com.kairowan.room_flow.typed.projection

fun rejectWrongProjectionValue() = projection(ProbeEntityTable.id, ProbeEntityTable.name) { id: Long, name: Int -> id + name }
