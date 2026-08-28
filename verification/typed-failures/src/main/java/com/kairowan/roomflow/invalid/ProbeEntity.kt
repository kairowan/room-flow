package com.kairowan.roomflow.invalid

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.kairowan.room_flow.typed.RoomFlowEntity

@RoomFlowEntity
@Entity
data class ProbeEntity(@PrimaryKey val id: Long, val name: String) {
    @Ignore var temporary: String = ""
}
