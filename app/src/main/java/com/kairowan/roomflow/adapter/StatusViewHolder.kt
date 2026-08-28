package com.kairowan.roomflow.adapter

import androidx.recyclerview.widget.RecyclerView
import com.kairowan.roomflow.databinding.ItemStatusBinding

class StatusViewHolder(private val binding: ItemStatusBinding) : RecyclerView.ViewHolder(binding.root) {
    fun bind(text: String) {
        binding.tvStatus.text = text
    }
}
