package com.kairowan.roomflow.invalid

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kairowan.room_flow.typed.RoomFlowEntity

@RoomFlowEntity
@Entity
data class OtherProbe(@PrimaryKey val id: Long)
