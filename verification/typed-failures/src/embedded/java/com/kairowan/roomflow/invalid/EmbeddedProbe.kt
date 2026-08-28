package com.kairowan.roomflow.invalid

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kairowan.room_flow.typed.RoomFlowEntity

@RoomFlowEntity
@Entity
data class EmbeddedProbe(@PrimaryKey val id: Long, @Embedded val nested: OtherProbe)
