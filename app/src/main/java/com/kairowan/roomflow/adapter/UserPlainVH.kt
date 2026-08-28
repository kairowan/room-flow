package com.kairowan.roomflow.adapter

import androidx.recyclerview.widget.RecyclerView
import com.kairowan.roomflow.data.User
import com.kairowan.roomflow.databinding.ItemUserBinding

class UserPlainVH(private val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {
    fun bind(u: User) {
        binding.tvTitle.text = "#${u.id}  ${u.name}"
        binding.tvSub.text = "age=${u.age ?: "-"}  lastActive=${u.lastActive}"
    }
}
