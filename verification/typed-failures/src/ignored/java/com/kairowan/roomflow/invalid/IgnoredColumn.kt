package com.kairowan.roomflow.invalid

fun rejectIgnoredColumn() = ProbeEntityTable.temporary.eq("not a column")
