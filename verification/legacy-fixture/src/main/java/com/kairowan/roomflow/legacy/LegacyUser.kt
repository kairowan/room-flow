package com.kairowan.roomflow.legacy

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class LegacyUser(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sex: String,
    val lastActive: Long
)
