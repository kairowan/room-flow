package com.kairowan.roomflow.invalid

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kairowan.room_flow.typed.RoomFlowEntity
import java.util.UUID

@RoomFlowEntity
@Entity
data class CustomProbe(@PrimaryKey val id: Long, val custom: UUID)
