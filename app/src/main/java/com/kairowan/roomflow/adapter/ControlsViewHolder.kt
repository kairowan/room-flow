package com.kairowan.roomflow.adapter

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.kairowan.roomflow.databinding.ItemControlRowBinding

class ControlsViewHolder(private val binding: ItemControlRowBinding) : RecyclerView.ViewHolder(binding.root) {
    fun bind(row: ControlRow, onAction: (String) -> Unit) {
        binding.btnLeft.text = row.left.title
        binding.btnLeft.setOnClickListener { onAction(row.left.id) }

        if (row.right != null) {
            binding.btnRight.text = row.right.title
            binding.btnRight.isEnabled = true
            binding.btnRight.visibility = View.VISIBLE
            binding.btnRight.setOnClickListener { onAction(row.right.id) }
        } else {
            binding.btnRight.text = ""
            binding.btnRight.isEnabled = false
            binding.btnRight.visibility = View.INVISIBLE
            binding.btnRight.setOnClickListener(null)
        }
    }
}
