package com.kairowan.roomflow.invalid

import com.kairowan.room_flow.typed.projection

fun rejectForeignProjection() = projection(ProbeEntityTable.id, OtherProbeTable.id) { a, b -> a + b }
