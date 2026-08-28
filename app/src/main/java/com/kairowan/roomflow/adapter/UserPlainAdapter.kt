package com.kairowan.roomflow.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kairowan.roomflow.data.User
import com.kairowan.roomflow.databinding.ItemUserBinding


class UserPlainAdapter : RecyclerView.Adapter<UserPlainVH>() {
    private val items = mutableListOf<User>()

    /** 全量重置（比如首次加载/刷新） */
    fun reset(newItems: List<User>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    /** 追加一页（用于“无限滚动”） */
    fun append(more: List<User>) {
        if (more.isEmpty()) return
        val start = items.size
        items.addAll(more)
        notifyItemRangeInserted(start, more.size)
    }

    fun itemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserPlainVH {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserPlainVH(binding)
    }

    override fun onBindViewHolder(holder: UserPlainVH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
