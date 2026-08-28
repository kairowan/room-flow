package com.kairowan.roomflow.invalid

import com.kairowan.room_flow.typed.query

fun rejectWrongOptionalValue() = ProbeEntityTable.query().whereIfNotNull(123) { ProbeEntityTable.name.eq(it) }
