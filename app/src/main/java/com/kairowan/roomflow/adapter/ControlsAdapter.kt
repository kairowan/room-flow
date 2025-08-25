package com.kairowan.roomflow.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kairowan.roomflow.databinding.ItemControlRowBinding


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

class ControlsAdapter(
    private val rows: List<ControlRow>,
    private val onAction: (String) -> Unit
) : RecyclerView.Adapter<ControlsAdapter.VH>() {

    data class Control(val id: String, val title: String)
    data class ControlRow(val left: Control, val right: Control?)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding =
            ItemControlRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(rows[position], onAction)
    }

    override fun getItemCount(): Int = rows.size

    class VH(private val binding: ItemControlRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: ControlRow, onAction: (String) -> Unit) {
            binding.btnLeft.text = row.left.title
            binding.btnLeft.setOnClickListener { onAction(row.left.id) }

            if (row.right != null) {
                binding.btnRight.text = row.right.title
                binding.btnRight.isEnabled = true
                binding.btnRight.visibility = android.view.View.VISIBLE
                binding.btnRight.setOnClickListener { onAction(row.right.id) }
            } else {
                binding.btnRight.text = ""
                binding.btnRight.isEnabled = false
                binding.btnRight.visibility = android.view.View.INVISIBLE
                binding.btnRight.setOnClickListener(null)
            }
        }
    }
}