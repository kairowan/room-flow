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
import com.kairowan.room_flow.debug.R
import com.kairowan.room_flow.metrics.RoomFlowMetrics
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
    private var rv: RecyclerView? = null
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
        statRows.getOrNull(1)?.first?.text = "成功事务包装（平均总耗时）"
        statRows.getOrNull(2)?.first?.text = "Checkpoint 次数"
        statRows.getOrNull(3)?.first?.text = "队列：等待 / 执行"
        statRows.getOrNull(4)?.first?.text = "队列结果：失败 / 取消 / 拒绝"
        statRows.getOrNull(5)?.first?.text = "最大排队 / 慢操作（≥100ms）"

        rv = root.findViewById<RecyclerView>(R.id.rvRecentSql).also {
            it.layoutManager = LinearLayoutManager(requireContext())
            it.adapter = adapter
        }
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
                    RoomFlowMetrics.overview.snapshot.collect { snapshot ->
                        val avg = snapshot.totalTransactionMs / snapshot.transactions.coerceAtLeast(1)
                        statRows.getOrNull(1)?.second?.text = "${snapshot.transactions}（${"%.2f".format(avg)} ms）"
                        statRows.getOrNull(3)?.second?.text = "${snapshot.pending} / ${snapshot.running}"
                        statRows.getOrNull(4)?.second?.text = "${snapshot.failed} / ${snapshot.cancelled} / ${snapshot.rejected}"
                        statRows.getOrNull(5)?.second?.text = "${"%.2f".format(snapshot.maxWaitMs)} ms / ${snapshot.slowTransactions}"
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
                        rv?.scrollToPosition((adapter.itemCount - 1).coerceAtLeast(0))
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        rv?.adapter = null
        rv = null
        statRows = emptyList()
        super.onDestroyView()
    }
}
