package com.kairowan.roomflow.verification

import android.app.Activity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.kairowan.room_flow.adapter.RecentSqlAdapter
import com.kairowan.room_flow.view.RoomFlowDebugPanelActivity
import com.kairowan.room_flow.view.RoomFlowDebugPanelFragment

class DebugConsumerSmoke {
    fun activityType(): Class<out Activity> = RoomFlowDebugPanelActivity::class.java
    fun fragment(): Fragment = RoomFlowDebugPanelFragment()
    fun adapter(): RecyclerView.Adapter<*> = RecentSqlAdapter()
}
