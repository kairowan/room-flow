package com.kairowan.roomflow.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kairowan.roomflow.databinding.ItemStatusBinding

class StatusAdapter : RecyclerView.Adapter<StatusViewHolder>() {
    private var text: String = "状态输出..."

    fun updateStatus(newText: String) {
        text = newText
        notifyItemChanged(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusViewHolder {
        val binding = ItemStatusBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StatusViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StatusViewHolder, position: Int) {
        holder.bind(text)
    }

    override fun getItemCount(): Int = 1
}
