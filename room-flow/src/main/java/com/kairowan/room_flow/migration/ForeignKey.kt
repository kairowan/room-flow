package com.kairowan.room_flow.migration

data class ForeignKey(
    val table: String,
    val onDelete: String,
    val onUpdate: String,
    val columns: List<String>,
    val referencedColumns: List<String>
)
