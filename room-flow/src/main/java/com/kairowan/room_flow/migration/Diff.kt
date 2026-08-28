package com.kairowan.room_flow.migration

data class Diff(
    val missingTables: List<Table>,
    val missingColumns: Map<String, List<Column>>,
    val unsupportedChanges: List<String> = emptyList(),
    val uncheckedFeatures: List<String> = listOf("CHECK/COLLATE/触发器/部分索引及其他建表约束；必须使用 Room 迁移测试验证")
)
