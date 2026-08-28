package com.kairowan.room_flow.migration

data class Index(
    val name: String,
    val unique: Boolean,
    val columns: List<String>,
    val orders: List<String>
)
