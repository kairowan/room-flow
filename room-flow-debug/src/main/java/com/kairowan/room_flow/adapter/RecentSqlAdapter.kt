package com.kairowan.room_flow.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kairowan.room_flow.debug.R

class RecentSqlAdapter : RecyclerView.Adapter<RecentSqlViewHolder>() {
    private val data = mutableListOf<String>()

    fun submit(list: List<String>) {
        data.clear()
        data.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentSqlViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_sql, parent, false)
        return RecentSqlViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecentSqlViewHolder, position: Int) {
        holder.tv.text = data[position]
    }

    override fun getItemCount(): Int = data.size
}
