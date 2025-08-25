package com.kairowan.roomflow.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kairowan.roomflow.databinding.ItemStatusBinding


/**
 * @author 浩楠
 * @date 2025/8/25
 *      _              _           _     _   ____  _             _ _
 *     / \   _ __   __| |_ __ ___ (_) __| | / ___|| |_ _   _  __| (_) ___
 *    / _ \ | '_ \ / _` | '__/ _ \| |/ _` | \___ \| __| | | |/ _` | |/ _ \
 *   / ___ \| | | | (_| | | | (_) | | (_| |  ___) | |_| |_| | (_| | | (_) |
 *  /_/   \_\_| |_|\__,_|_|  \___/|_|\__,_| |____/ \__|\__,_|\__,_|_|\___/
 *  描述: TODO
 */
class StatusAdapter : RecyclerView.Adapter<StatusAdapter.VH>() {
    private var text: String = "状态输出..."

    fun updateStatus(newText: String) {
        text = newText
        notifyItemChanged(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemStatusBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(text)
    }

    override fun getItemCount(): Int = 1

    class VH(private val binding: ItemStatusBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(text: String) {
            binding.tvStatus.text = text
        }
    }
}