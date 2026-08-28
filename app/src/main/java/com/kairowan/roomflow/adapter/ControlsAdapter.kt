package com.kairowan.roomflow.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kairowan.roomflow.databinding.ItemControlRowBinding

class ControlsAdapter(
    private val rows: List<ControlRow>,
    private val onAction: (String) -> Unit
) : RecyclerView.Adapter<ControlsViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ControlsViewHolder {
        val binding = ItemControlRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ControlsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ControlsViewHolder, position: Int) {
        holder.bind(rows[position], onAction)
    }

    override fun getItemCount(): Int = rows.size
}
