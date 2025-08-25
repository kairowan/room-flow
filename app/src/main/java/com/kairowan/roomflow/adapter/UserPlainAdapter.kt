package com.kairowan.roomflow.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kairowan.roomflow.data.User
import com.kairowan.roomflow.databinding.ItemUserBinding


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

class UserPlainVH(private val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {
    fun bind(u: User) {
        binding.tvTitle.text = "#${u.id}  ${u.name}"
        binding.tvSub.text  = "age=${u.age ?: "-"}  lastActive=${u.lastActive}"
    }
}