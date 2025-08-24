package com.kairowan.room_flow.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kairowan.room_flow.R
import com.kairowan.room_flow.RoomFlowMetrics
import com.kairowan.room_flow.adapter.RecentSqlAdapter
import kotlinx.coroutines.launch

/**
 * @author 浩楠
 *
 * @date 2025/8/24
 *
 *      _              _           _     _   ____  _             _ _
 *     / \   _ __   __| |_ __ ___ (_) __| | / ___|| |_ _   _  __| (_) ___
 *    / _ \ | '_ \ / _` | '__/ _ \| |/ _` | \___ \| __| | | |/ _` | |/ _ \
 *   / ___ \| | | | (_| | | | (_) | | (_| |  ___) | |_| |_| | (_| | | (_) |
 *  /_/   \_\_| |_|\__,_|_|  \___/|_|\__,_| |____/ \__|\__,_|\__,_|_|\___/
 * @Description: TODO
 */
class RoomFlowDebugPanelFragment : Fragment() {

    private lateinit var statRows: List<Pair<TextView, TextView>>
    private lateinit var rv: RecyclerView
    private val adapter = RecentSqlAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_roomflow_debug_panel, container, false)

        // 收集三个 include 的 stat row（按添加顺序：Busy、Tx、Checkpoint）
        val rows = mutableListOf<Pair<TextView, TextView>>()
        fun findRows(v: View) {
            if (v.id == R.id.tvStatLabel && v.parent is View) {
                val label = v as TextView
                val value = (v.parent as View).findViewById<TextView>(R.id.tvStatValue)
                rows += label to value
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) findRows(v.getChildAt(i))
            }
        }
        findRows(root)
        statRows = rows

        // 设置标签名
        statRows.getOrNull(0)?.first?.text = "Busy 重试次数"
        statRows.getOrNull(1)?.first?.text = "事务数量（平均耗时）"
        statRows.getOrNull(2)?.first?.text = "Checkpoint 次数"

        rv = root.findViewById(R.id.rvRecentSql)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Busy 重试次数
                launch {
                    RoomFlowMetrics.busyRetryCount.collect { v ->
                        statRows.getOrNull(0)?.second?.text = v.toString()
                    }
                }
                // 事务数量 + 平均耗时
                launch {
                    RoomFlowMetrics.txCount.collect { cnt ->
                        val avg = RoomFlowMetrics.txAvgMs.value
                        statRows.getOrNull(1)?.second?.text = "$cnt（${"%.2f".format(avg)} ms）"
                    }
                }
                // Checkpoint 次数
                launch {
                    RoomFlowMetrics.checkpointCount.collect { v ->
                        statRows.getOrNull(2)?.second?.text = v.toString()
                    }
                }
                // 最近 SQL（采样）
                launch {
                    RoomFlowMetrics.recentSql.collect { list ->
                        adapter.submit(list)
                        rv.scrollToPosition((adapter.itemCount - 1).coerceAtLeast(0))
                    }
                }
            }
        }
    }
}