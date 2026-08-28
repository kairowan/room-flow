package com.kairowan.room_flow.migration

data class Column(
    val name: String,
    val type: String,
    val notNull: Boolean,
    val defaultValue: String? = null,
    val primaryKeyPosition: Int = 0
)
