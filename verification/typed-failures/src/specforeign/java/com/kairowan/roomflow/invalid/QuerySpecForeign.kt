package com.kairowan.roomflow.invalid

import com.kairowan.room_flow.typed.query

fun rejectForeignDefinition() = ProbeEntityTable.query().where(OtherProbeTable.id.eq(1L))
