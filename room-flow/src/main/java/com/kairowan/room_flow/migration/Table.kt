package com.kairowan.room_flow.migration

data class Table(
    val name: String,
    val columns: List<Column>,
    val createSql: String? = null,
    val indexSql: List<String> = emptyList(),
    val indices: Set<Index> = emptySet(),
    val foreignKeys: Set<ForeignKey> = emptySet()
)
