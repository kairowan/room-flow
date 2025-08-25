package com.kairowan.roomflow

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.sqlite.db.SimpleSQLiteQuery
import com.kairowan.room_flow.crud.withTransactionRetry
import com.kairowan.room_flow.flow.flowQuery
import com.kairowan.room_flow.flow.observeTables
import com.kairowan.room_flow.maintenance.analyze
import com.kairowan.room_flow.maintenance.checkpoint.WalCheckpointScheduler
import com.kairowan.room_flow.maintenance.estimatedDbSizeBytes
import com.kairowan.room_flow.maintenance.integrityCheck
import com.kairowan.room_flow.maintenance.vacuum
import com.kairowan.room_flow.maintenance.walCheckpointTruncate
import com.kairowan.room_flow.migration.MigrationAssistant
import com.kairowan.room_flow.multiprocess.CrossProcessInvalidation
import com.kairowan.room_flow.routing.RouteContext
import com.kairowan.room_flow.routing.SimpleDbRouter
import com.kairowan.room_flow.sql.mapper.mapRows
import com.kairowan.room_flow.sql.rawQuery
import com.kairowan.room_flow.sql.update.update
import com.kairowan.room_flow.write.WriteQueue
import com.kairowan.roomflow.adapter.ControlsAdapter
import com.kairowan.roomflow.adapter.ControlsAdapter.Control
import com.kairowan.roomflow.adapter.ControlsAdapter.ControlRow
import com.kairowan.roomflow.adapter.StatusAdapter
import com.kairowan.roomflow.adapter.UserPlainAdapter
import com.kairowan.roomflow.data.AppDatabase
import com.kairowan.roomflow.data.User
import com.kairowan.roomflow.data.UserDao
import com.kairowan.roomflow.databinding.FragmentRoomflowBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


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
class RoomFlowDemoFragment : Fragment() {
    private var _binding: FragmentRoomflowBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var dao: UserDao
    private lateinit var writeQueue: WriteQueue
    private var walScheduler: WalCheckpointScheduler? = null
    private var pagingJob: Job? = null

    private val statusAdapter = StatusAdapter()
    private val userAdapter = UserPlainAdapter()
    private var isLoading = false                  // 是否在加载中
    private var endReached = false                 // 是否到最后一页
    private var page = 0                           // 当前页，从 0 开始
    private val pageSize = 20                      // 每页条数
    private var scrollListenerAdded = false

    companion object {
        private const val A_INSERT_BATCH = "insert_batch"
        private const val A_PARTIAL_UPDATE = "partial_update"
        private const val A_FLOW_QUERY = "flow_query"
        private const val A_RAW_MAPPER = "raw_mapper"
        private const val A_PAGING = "paging"
        private const val A_MAINTENANCE = "maintenance"
        private const val A_WAL_START = "wal_start"
        private const val A_WAL_STOP = "wal_stop"
        private const val A_ROUTER = "router"
        private const val A_CROSS = "cross_process"
        private const val A_MIGRATION = "migration"
        private const val A_DEBUG = "debug_panel"

        fun newInstance() = RoomFlowDemoFragment()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AppDatabase.get(requireContext())
        dao = db.userDao()
        writeQueue = WriteQueue(db)
    }

    override fun onDestroy() {
        super.onDestroy()
        writeQueue.close()
        walScheduler?.stop()
        pagingJob?.cancel()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRoomflowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // 1) 控件区按钮配置（每行两个按钮）
        val rows = listOf(
            ControlRow(Control(A_INSERT_BATCH, "批量写入(WriteQueue)"), Control(A_PARTIAL_UPDATE, "Partial Update")),
            ControlRow(Control(A_FLOW_QUERY, "表失效Flow"),           Control(A_RAW_MAPPER,  "Raw + RowMapper")),
            ControlRow(Control(A_PAGING, "分页(手写)"),                Control(A_MAINTENANCE, "维护(Integrity/Analyze/Vacuum)")),
            ControlRow(Control(A_WAL_START, "WAL调度 start"),         Control(A_WAL_STOP,    "WAL调度 stop")),
            ControlRow(Control(A_ROUTER, "分库路由示例"),              Control(A_CROSS,       "跨进程通知")),
            ControlRow(Control(A_MIGRATION, "迁移助手(Diff/Plan)"),   Control(A_DEBUG,       "打开调试面板"))
        )
        val controlsAdapter = ControlsAdapter(rows) { id -> onAction(id) }

        // 2) Concat：控件区 + 状态行 + 用户列表
        val concat = ConcatAdapter(controlsAdapter, statusAdapter, userAdapter)
        binding.rvMain.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMain.adapter = concat

        // 3) 添加一次性的“无限滚动监听”：靠近底部则加载下一页
        if (!scrollListenerAdded) {
            scrollListenerAdded = true
            binding.rvMain.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(rv, dx, dy)
                    if (dy <= 0) return
                    val lm = rv.layoutManager as? LinearLayoutManager ?: return
                    val last = lm.findLastVisibleItemPosition()
                    val total = userAdapter.itemCount()
                    val threshold = 5 // 距底部 5 个 item 触发
                    if (!isLoading && !endReached && last >= total - threshold) {
                        loadNextPage()
                    }
                }
            })
        }
    }

    private fun onAction(id: String) {
        when (id) {
            A_INSERT_BATCH   -> actionInsertBatch()
            A_PARTIAL_UPDATE -> actionPartialUpdate()
            A_FLOW_QUERY     -> actionFlowQuery()
            A_RAW_MAPPER     -> actionRawMapper()
            A_PAGING         -> actionPaging()
            A_MAINTENANCE    -> actionMaintenance()
            A_WAL_START      -> actionWalStart()
            A_WAL_STOP       -> actionWalStop()
            A_ROUTER         -> actionRouter()
            A_CROSS          -> actionCrossProcess()
            A_MIGRATION      -> actionMigration()
            A_DEBUG          -> actionDebugPanel()
        }
    }

    // ===================== 每个功能的案例 ===================== //

    /** 1) WriteQueue 批量写入 */
    private fun actionInsertBatch() {
        viewLifecycleOwner.lifecycleScope.launch {
            val list = (1..30).map { i -> User(name = "User-$i", age = (18..35).random(), sex = "男") }
            writeQueue.submitAll(list, keySelector = { it.name.first() }) { group ->
                dao.upsertList(group)
                group
            }.await()
            statusAdapter.updateStatus("批量写入完成：${list.size} 条")
            toast("OK")
        }
    }

    /** 2) Partial Update（把最大 id 的用户改名） */
    private fun actionPartialUpdate() {
        viewLifecycleOwner.lifecycleScope.launch {
            val id = db.rawQuery("SELECT MAX(id) FROM users").use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
            if (id == 0L) { toast("没有可更新的数据"); return@launch }
            db.update("users") { set("name" to "Renamed-$id"); where("id = ?", id) }
            statusAdapter.updateStatus("已更新用户 $id 的 name")
        }
    }

    /** 3) 表失效 Flow：收到变更自动刷新第一页；另起一个 flowQuery 实时展示计数 */
    private fun actionFlowQuery() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 表变则刷新第一页
                launch {
                    db.observeTables("users").collect {
                        statusAdapter.updateStatus("observeTables: users 表变化 → 刷新第一页")
                        reloadFirstPage()
                    }
                }
                // 表变则重查计数
                launch {
                    db.flowQuery("users") { dao.countAll() }.collect { count ->
                        statusAdapter.updateStatus("flowQuery: 当前 users 数量 = $count")
                    }
                }
            }
        }
    }

    /** 4) 原生 SQL + RowMapper 映射 */
    private fun actionRawMapper() {
        viewLifecycleOwner.lifecycleScope.launch {
            val users = db.rawQuery("SELECT id, name, age, lastActive FROM users")
                .use { c -> c.mapRows { row ->
                    User(
                        id = row.get("id"),
                        name = row.get("name"),
                        age = row.getOrNull("age"),
                        sex = "男",
                        lastActive = row.get("lastActive")
                    )
                } }
            statusAdapter.updateStatus("Raw + RowMapper：读取 ${users.size} 条，前1条=${users.firstOrNull()}")
        }
    }

    /** 5) 分页（手写）：清空并加载第一页 */
    private fun actionPaging() {
        reloadFirstPage()
    }

    /** 6) 维护工具 */
    private fun actionMaintenance() {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = db.integrityCheck()
            db.analyze()
            val before = db.estimatedDbSizeBytes()
            db.vacuum()
            val cp = db.walCheckpointTruncate()
            val after = db.estimatedDbSizeBytes()
            statusAdapter.updateStatus("Integrity=$ok  Checkpoint=$cp  Size: $before → $after")
        }
    }

    /** 7) WAL 调度器 */
    private fun actionWalStart() {
        if (walScheduler == null) walScheduler = WalCheckpointScheduler(db)
        walScheduler!!.start()
        statusAdapter.updateStatus("WAL 调度器已启动")
        toast("WAL 调度器已启动")
    }

    private fun actionWalStop() {
        walScheduler?.stop()
        statusAdapter.updateStatus("WAL 调度器已停止")
        toast("WAL 调度器已停止")
    }

    /** 8) 分库路由演示（此处读写仍指向同一库，仅示例 API） */
    private fun actionRouter() {
        val router = SimpleDbRouter(db).apply { registerUserDb("u1", db) }
        val rdb = router.readable(RouteContext(userId = "u1"))
        viewLifecycleOwner.lifecycleScope.launch {
            val cnt = rdb.withTransactionRetry { dao.countAll() }
            statusAdapter.updateStatus("Router：u1 可读库计数=$cnt（示例使用同一库）")
        }
    }

    /** 9) 跨进程通知（authority 改成你 App 的 Provider） */
    private fun actionCrossProcess() {
        val hub = CrossProcessInvalidation(requireContext(), "com.example.roomflow.provider")
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    hub.changes("users").collect {
                        statusAdapter.updateStatus("跨进程：收到 users 变更通知")
                    }
                }
            }
        }
        hub.notifyChanged("users") // 模拟一次通知
        toast("已 notifyChanged(users)")
    }

    /** 10) 迁移助手 */
    private fun actionMigration() {
        viewLifecycleOwner.lifecycleScope.launch {
            val schemaJson = """
                [
                  {
                    "database": {
                      "version": 1,
                      "entities": [
                        {
                          "tableName": "users",
                          "fields": [
                            {"fieldPath":"id","affinity":"INTEGER","notNull":true},
                            {"fieldPath":"name","affinity":"TEXT","notNull":true},
                            {"fieldPath":"age","affinity":"INTEGER","notNull":false},
                            {"fieldPath":"lastActive","affinity":"INTEGER","notNull":true}
                          ]
                        }
                      ]
                    }
                  }
                ]
            """.trimIndent()
            val diff = MigrationAssistant.compareSchema(db, schemaJson)
            val plan = MigrationAssistant.planMigration(diff)
            statusAdapter.updateStatus("Migration diff: 缺表=${diff.missingTables.size} 缺列=${diff.missingColumns.size}；计划SQL=${plan.size}")
        }
    }

    /** 11) 打开 View 版调试面板 */
    private fun actionDebugPanel() {
        try {
            val intent = Intent().setClassName(requireContext(),
                "com.kairowan.room_flow.view.RoomFlowDebugPanelActivity")
            startActivity(intent)
        } catch (t: Throwable) {
            toast("未引入 roomflow-debugpanel-view 模块")
        }
    }


    /** 重新加载第一页（首次/刷新/表失效） */
    private fun reloadFirstPage() {
        isLoading = true
        endReached = false
        page = 0
        viewLifecycleOwner.lifecycleScope.launch {
            val first = queryPage(page, pageSize)
            userAdapter.reset(first)
            statusAdapter.updateStatus("已加载第 1 页：${first.size} 条")
            isLoading = false
            if (first.size < pageSize) endReached = true else page++
        }
    }

    /** 加载下一页并追加 */
    private fun loadNextPage() {
        if (isLoading || endReached) return
        isLoading = true
        viewLifecycleOwner.lifecycleScope.launch {
            val more = queryPage(page, pageSize)
            userAdapter.append(more)
            statusAdapter.updateStatus("追加第 ${page + 1} 页：${more.size} 条，累计=${userAdapter.itemCount()}")
            isLoading = false
            if (more.size < pageSize) endReached = true else page++
        }
    }

    /** 实际的分页查询（原生 SQL + LIMIT/OFFSET + RowMapper） */
    private suspend fun queryPage(page: Int, size: Int): List<User> = withContext(Dispatchers.IO) {
        db.rawQuery(
            SimpleSQLiteQuery(
                "SELECT id, name, age, lastActive FROM users ORDER BY id DESC LIMIT ? OFFSET ?",
                arrayOf(size, page * size)
            )
        ).use { c ->
            c.mapRows { row ->
                User(
                    id = row.get("id"),
                    name = row.get("name"),
                    age = row.getOrNull("age"),
                    sex = "男",
                    lastActive = row.get("lastActive")
                )
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

}