package com.kairowan.roomflow.legacy

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "records",
    foreignKeys = [ForeignKey(entity = LegacyUser::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)]
)
data class LegacyRecord(@PrimaryKey val id: Long, val userId: Long)
