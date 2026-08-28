package com.kairowan.roomflow.verification

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kairowan.room_flow.typed.RoomFlowEntity

@RoomFlowEntity
@Entity(tableName = "consumer_entries")
data class ConsumerEntry(@PrimaryKey(autoGenerate = true) val id: Long = 0, @ColumnInfo(name = "display_name") val name: String)
