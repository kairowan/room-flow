package com.kairowan.room_flow.adapter

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.kairowan.room_flow.debug.R

class RecentSqlViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val tv: TextView = view.findViewById(R.id.tvSql)
}
