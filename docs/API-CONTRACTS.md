# API 契约与升级说明

[返回 README](../README.md)。本文保留线程、取消、资源生命周期、数据安全及旧接口升级的详细约定；快速接入与功能导航见 README。

## 事务与写队列

示例 UserDao 使用 Room `@Upsert`，不是 `INSERT OR REPLACE`，避免替换父行引发子表级联删除。
返回的 Long 仅在插入时为新 rowId，更新已有行时为 `-1`，不能将其直接当更新行的主键。

```kotlin
db.withTransactionRetry {
    dao.upsertList(users)
}

val queue = WriteQueue(db, capacity = 64, onWriteCommitted = {
    scheduler.onWriteCommitted()
})
try {
    queue.submitAll(users, keySelector = { it.id }) { group ->
        dao.upsertList(group)
    }.await()
} finally {
    queue.closeAndJoin()
}
```

- 使用 Room 的协程事务，支持挂起 DAO。
- `await()` 成功只发生在事务提交后；任务失败回滚并传递异常。
- 默认 BusyRetry 只重试 Android SQLite 的数据库/表锁异常，CancellationException 不重试。
- 重试会重新执行整个 block，不要在其中发送网络请求、消息或执行其他不可重试副作用。
- capacity 是有限正数；队列满时返回失败 Deferred，不创建无限等待的发送协程。
- 满队列返回 QueueFullException，关闭后返回 QueueClosedException；关闭过程中已接受的任务仍可能收到取消。
- 禁止队列内部及当前数据库事务线程再次入队，跨 dispatcher 的同队列调用也拒绝。任务直接调用 DAO，不等待另一个队列；独立 Scope/外部事务跨线程依赖环仍需调用方避免。
- submitAll 默认最多 1000 项，可配置 maxBatchSize；超过即拒绝，不偷偷改变整批原子性。此上限不限制单项字节大小或任意 submit 闭包捕获的数据。
- `close()` 取消执行中和排队任务，不保证排空。取消与 commit 竞争时不能撤销已提交的数据。
- 关库前使用 `closeAndJoin()` 等待退出，不能从队列内部调用。阻塞 SQL/不合作的回调不能被协程强制中断；onWriteCommitted 只能做快速本地通知，不做网络/等待/重入。
- `coalesce(key, task)` 只按 key 暂存任务，不做“最后一次覆盖”去重；最多暂存 capacity 个任务。
- `flushCoalesced()` 把本次暂存内容作为一个原子事务；入队失败保留暂存内容。
- `drainAndJoin()` 先拒绝新任务，再等待已接收任务执行完毕；单项失败不阻断后续项，必须分别 await 结果。
  存在未 flush 的暂存任务时直接拒绝排空，队列状态不变；不隐式丢弃或提交这些任务。
  取消排空的等待者不会中止排空。若 `withTimeout { queue.drainAndJoin() }` 超时，应在仍可运行的清理上下文调用
  `closeAndJoin()`，等所有任务真正退出后才关库。不合作的阻塞代码仍可能无限延迟退出。

## 原生 SQL、映射与部分更新

这些是阻塞 API，必须在后台线程调用。原生写入经过 Room 事务，以触发表失效。

```kotlin
withContext(Dispatchers.IO) {
    val users = db.rawQuery("SELECT id, name FROM users WHERE age >= ?", listOf(18))
        .use { cursor ->
            cursor.mapRows { row -> row.get<Long>("id") to row.get<String>("name") }
        }

    db.update("users") {
        set("name" to "Alice")
        where("id = ?", 1L)
    }

    // 必须显式确认全表更新。
    db.update("users") {
        set("age" to 20)
        allRows()
    }
}
```

值使用参数绑定，标识符按 SQLite 规则转义；WHERE 仍是开发者提供的可信 SQL，不能拼接用户输入。
RowMapper 是运行期基本类型映射，并非编译期字段校验；重复列名请用 SQL alias 消歧。
CursorRow 只在当前迭代位置有效，不能保存后再读取。`mapRows` 会关闭 Cursor。

挂起查询推荐 `rawQueryList`：自动选择 IO（调用点已在 Room 事务线程时保持该线程），连接协程取消与 SQLite CancellationSignal，映射后关闭 Cursor。

```kotlin
val ids = db.rawQueryList("SELECT id FROM users WHERE age >= ?", listOf(18)) { it.getLong(0) }
```

mapper 只读取当前行，不得返回 Cursor、缓存它或执行不可取消的长阻塞操作。`rawQuery(query, signal)` 是同步显式取消入口，仍需调用方 use。
事务内直接在 Room 事务块调用本 API，不要先手动切换到其他 dispatcher 执行同步查询。

大数据逐行消费使用 `rawQueryEach(SimpleSQLiteQuery(sql, args)) { cursor -> ... }`，返回成功消费行数。
它不把整表存入 List，且**不自动重试**（写入导出流等副作用不能重复）。不要再包入会重试的外层事务。
示例 `app/src/main/java/com/kairowan/roomflow/data/UserExport.kt` 提供 JSON Lines 导出，正确处理换行/引号/NULL；Writer 由调用方持有并关闭。
先写临时文件，成功关闭并按业务持久性要求 sync 后再发布；取消/异常时删除本次临时产物，不能交付部分文件。
单次查询保持 SQLite 读取快照，但长时间导出会持有读连接/快照，可能阻碍 WAL 回收；单个超大字段仍可能耗尽内存。
需要分批导出时，应另行明确并发写入下的快照一致性，不自动把单个查询拆成多页。
普通 `readQuery` / `flowQuery` 的任意同步 block 不能强制中断；仅 withContext(IO)/withTimeout 不等于底层 SQL 已停止。
`readQuery` 在 Room 事务线程内直接执行且不局部重试，事务外才切换 IO；不要先切到别的 dispatcher 再执行事务内同步 DAO。
事务检测复用不会主动开库的检查，避免主线程首次调用在切 IO 前开库。`readQuery` 是 inline API，升级后宿主需要重新编译才能使用修复后的方法体。
原生字符串查询和 `rawQueryFlow` 在入口复制 BLOB 参数，后续修改原 ByteArray 不会改变既有查询；自定义 `SupportSQLiteQuery` 必须自行管理不可变绑定及生命周期。

## Flow 与分页

```kotlin
db.flowQuery("users") { dao.countAll() }.collect { count -> /* 更新 UI */ }

db.rawQueryFlow(
    sql = "SELECT id FROM users WHERE age >= ?",
    args = listOf(18),
    tables = arrayOf("users"),
    mapper = { cursor -> cursor.getLong(0) }
).collect { ids -> /* List<Long> */ }

val pager = db.pagerFromRaw(
    pageSize = 20,
    tables = arrayOf("users"),
    countQuery = "SELECT COUNT(*) FROM users",
    queryProvider = { limit, offset ->
        SimpleSQLiteQuery("SELECT id FROM users ORDER BY id LIMIT ? OFFSET ?", arrayOf(limit, offset))
    },
    mapper = { it.getLong(0) }
)
```

必须指定实际观察表；Flow 串行重查并合并失效事件，不保证每次中间状态都发射。
原生 `rawQueryFlow` 和 `RawPagingSource` 的 COUNT/页面查询均连接 CancellationSignal，取消后先关闭 Cursor。
分页 load 取消后该 Source 会失效并移除观察者，下次加载须创建新 Source。
Flow 是冷流，每个订阅者会独立查询；只有确需多个消费者时，在调用方拥有的 scope 中使用
`shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), replay = 1)`。
共享前先决定异常重试/终止策略；scope 必须随页面/ViewModel 结束，不使用全局缓存。distinctUntilChanged 不减少已执行的 SQL。
分页 key 是行偏移，支持首次加载多页；SQL 必须有稳定 ORDER BY。
countQuery 可省略；动态过滤应使用 `countQueryProvider = { SimpleSQLiteQuery("SELECT COUNT(*) FROM users WHERE age >= ?", arrayOf(18)) }`，不可同时指定两者。
COUNT 必须与分页过滤一致，返回一行一列非空整数，值在非负 Int 范围。可传自定义 PagingConfig，启用占位符时必须提供 COUNT。
大量删除后的刷新会按新总数收回偏移；无 COUNT 且旧偏移为空时回到第一页。`RawPagingSource` 支持直接加载/测试，失效后必须新建。
为保证 COUNT 与页数据一致，仍保留事务快照及逐页 COUNT；这不是无成本优化，复杂查询优先使用 Room DAO 的 PagingSource。
大数据深分页的 OFFSET 成本较高，需要时由业务改成 keyset 分页。
示例的顺序加载已采用非空唯一 id 降序的 keyset，不支持任意跳页；表失效仍重新加载首页。
测量方法、原始样本输出及优化边界见 [性能基线](PERFORMANCE.md)。

## 维护、离线备份与迁移建议

- `integrityCheck / analyze / vacuum / estimatedDbSizeBytes`：后台执行；大库检查/整理可能耗时。
- `walCheckpointTruncate`：busy 作为失败处理；返回 SQLite 第三列，不代表本次新增搬移页数。成功截断时可能为 0。
- `WalCheckpointScheduler`：所有写入方都要接入 onWriteCommitted；WAL 文件帧数是估计值，可能包含旧帧，不是未 checkpoint 页数。
- 调度使用单调时钟；关库前停止其他 start 调用，再 `stopAndJoin()` 等待旧任务完成。
- `tunePragmas` 仅提供显式 synchronous=NORMAL 调优，不修改 Room 的 journal/外键设置。
  NORMAL 在断电时可能丢最近提交，重要数据不要启用。

```kotlin
val diff = MigrationAssistant.compareSchema(db, trustedRoomSchemaJson)
val suggestedSql = MigrationAssistant.planMigration(diff)
```

解析标准 Room JSON 对象根和 columnName，新增表保留 Room createSql/索引。
只生成简单新增表/列建议；类型、默认值、主键、索引和外键变化会要求人工 Migration。
不猜测 NOT NULL 默认值；不自动执行 SQL，不提供 dry-run 或完整 schema 等价证明。
视图、FTS、复杂约束及表达式默认值需专门迁移，并用 Room 的迁移测试验证。
额外的旧表会列入 unsupportedChanges，不自动 DROP；uncheckedFeatures 明确列出未覆盖的比较范围。

```kotlin
// 先停止所有线程/进程访问并关闭所有连接，再执行；不能只关闭一个实例。
val identity = BackupIdentity("stable-database-and-account-id", schemaVersion, exportedRoomIdentityHash)
val backup = MigrationAssistant.backupDb(dbFile, identity, databaseClosed = true)
val preview = MigrationAssistant.previewRestore(dbFile, backup, identity, databaseClosed = true)
// 应用展示 preview 并确认恢复后执行；此方法内部会再次预检。
val recoveryFile = MigrationAssistant.rollbackDb(dbFile, backup, identity, databaseClosed = true)
```

`estimateBackupSpace(dbFile)` 给出额外空间的保守估算（两份主文件 + 64 KiB），backupDb 自动预检；不预留空间，仍需处理 IOException。
需要进度/取消时调用重载 `backupDb(dbFile, identity, true, signal) { copied, total -> ... }`。
这是同步 IO API；signal 是 Android CancellationSignal，普通协程取消不会自动连接它。
校验/复制/发布前可取消；主文件发布开始后不再检查取消，继续写清单，以成功返回或真实写入异常为准。
进度只描述主文件复制，100% 不等于清单已经发布；回调必须快速、不重入，不改动任何输入文件，回调异常会在发布前中止。
原库保持不变；发布后清单失败仍可能留下无清单备份，保留它供人工处置，不自动覆盖或恢复。
空间估算、进度和取消仅扩展备份创建，不改变恢复的离线校验与恢复点规则。
关闭全部连接后，允许保留完整 28 字节头全零的冷 PERSIST journal；它不会被删除或复制进备份。
热/未知/不完整 journal、非空 WAL、残留 SHM 仍拒绝。该判断不代替调用方停写、关库及多进程协调。

只支持普通 Room SQLite 离线备份，必须在 IO 线程执行。identity 必须来自调用方可信的当前账号/目标 schema，不能从待恢复清单反推。
备份同时生成 `.bak.json`，校验目标文件名、稳定库/账号标识、版本、Room identity_hash 和 SHA-256；旧备份缺少清单默认拒绝，需人工核验迁移。
SHA-256 只检测意外损坏/替换，不提供清单真实性认证；不接受外部不可信备份。Room identity_hash 也不替代完整迁移验证。
校验只打开临时副本，避免 Android 只读 WAL 打开产生辅助文件污染原库或备份；因此需要额外临时磁盘空间。
覆盖现有目标前保存 `.before-restore`（原库可能已损坏），已有恢复点禁止覆盖。恢复后由调用方重开 Room 验证并决定保留/归档，失败时保留全部证据。
显式声明关闭仍是调用者的责任；代码会额外拒绝非空 WAL、热/未知 journal、残留 SHM、
损坏数据库和覆盖已有备份。验证失败保留损坏源文件。进程内串行执行，写临时文件并同步后同目录 rename；
恢复不会消耗备份。调用方还必须禁止其他进程同时备份/恢复或修改目标文件。
不提供在线一致性快照、跨进程停写协调、加密库备份或文件系统断电后的完整持久性保证。

## 路由、跨进程、加密与调试

- SimpleDbRouter：默认库 + 注册用户库，未知用户明确失败；读写指向同一库，无主从复制。
  重复注册不同实例会拒绝；unregisterUserDb 只移除路由并返回旧实例，不隐式关库。注销前停止该账号的生产者并等待队列/调度器退出；旧引用不会自动失效。
- aggregateInvalidations：聚合显式传入的多个数据库的表失效。
- CrossProcessInvalidation：需真实注册的 ContentProvider；调用方手动在提交后 notifyChanged。
  不自动连接 DAO/Room tracker，示例不伪造跨进程成功。
- CipherSupport：仅接受外部 SupportSQLiteOpenHelper.Factory；rekey 检查 cipher_version、
  转义密钥并传递失败。调用方负责独占访问、持久化新密钥及重开验证；未提供完整密钥管理或 SQLCipher 版本认证。
- RoomFlowDebugPanelActivity：可选调试模块提供，未导出；记录库包装事务/重试/自动 checkpoint。
  直接调用 DAO 的事务不自动统计。SQL 只采样语句种类，不保存绑定值或原始 SQL 字面量。
- 自定义日志用 RoomFlowConfig.setLogger。日志接收器失败不会改变数据库提交结果。

`RoomFlowMetrics.forDatabase(db).snapshot` 按数据库实例隔离队列积压、运行中、成功/失败/取消/拒绝、等待时长、事务耗时与慢事务数（≥100ms）。
`RoomFlowMetrics.overview.snapshot` 为进程汇总，调试面板使用单一快照，避免事务数量/均值来自不同更新。
注册表弱引用数据库，不记录库路径、账号或 SQL 内容。只统计接入的包装操作，不自动监测所有 DAO；busy/checkpoint/SQL 类型采样目前仍是进程级。

指标口径（2026-08-28 起）：

- `transactions` 保留旧名字，表示成功返回的顶层包装操作，不是 SQLite 物理 COMMIT 次数；同库嵌套包装不重复计数。
  在外部 Room 事务线程调用包装时不单独统计/重试，失败交给外层；不能用内层返回成功推断外层会提交。
  库自身的同库包装标记随协程传播；仅用公开 API 无法完整识别外部 Room 事务手动跨 dispatcher 后的归属，仍不能把包装计数当提交审计。
- `transactionAttempts/transactionRetries` 包含失败尝试；`failedTransactions/cancelledTransactions` 是顶层操作结果。
- `totalTransactionMs/maxTransactionMs/slowTransactions` 统计成功操作的完整墙钟耗时（单调时钟），包含 Room 调度、等待和重试退避，不含 WriteQueue 排队。
  `totalOperationMs/maxOperationMs` 覆盖所有结束的操作；`totalBlockMs` 是用户 block 耗时，含挂起、不含 begin/commit。
- `completed/failed/cancelled/rejected` 是队列 Deferred 结果，`pending/running` 是工作状态。
  提交后取消可能出现 `transactions +1` 同时 `cancelled +1`，并不矛盾，也不表示发生了回滚。

事务包装由 Room 自己的事务 executor 调度，不再先切全局 IO。自定义 RetryPolicy 必须串行执行尝试，不得并行调用 block。
MetricsSnapshot 增加字段会改变 data class 构造器/复制方法的二进制签名；调用方应重新编译，不承诺旧二进制直接兼容。

## 安全日志与查询诊断

默认不向 logcat 或其他接收器输出日志。宿主可 `RoomFlowConfig.setLogger(...)` 接入日志器；默认仅传安全的异常类别，
不传原始 Throwable 的 message/cause/suppressed。`logExceptionDetails = true` 仅用于可信诊断环境，关闭后恢复脱敏。
公开 Trace 的 tag/msg/measure 名称仍由调用者保证不含敏感信息，不会自动识别任意文本中的账号/密码。
日志回调异常不改变数据库操作结果。[Android 日志安全说明](https://developer.android.com/privacy-and-security/risks/log-info-disclosure)

`RoomFlowConfig.onQueryObserved` 默认 null；启用后仅观察 rawQueryList/rawQueryFlow/rawQueryEach 的单次游标消费，
包含执行、遍历、映射和关闭时间，不含 IO 调度/重试等待，不覆盖原生 DAO、同步 rawQuery 或通用分页的直接查询。
`rowsConsumed` 是成功处理的行数，失败时可为部分行数；事件不含 SQL、参数或异常文本。每次重试各记录一次。
游标消费成功与协程最终交付结果有取消竞态，不能把 succeeded 当事务提交凭据。
回调在查询线程同步执行，必须快速、不重入、不写数据库；持有回调的宿主退出时设回 null，异常不会改变查询结果。

手动诊断执行计划可复用现有 API（仅开发环境，对可信 SELECT）：

```kotlin
val plan = db.rawQueryList(
    "EXPLAIN QUERY PLAN SELECT id FROM users WHERE age >= ? ORDER BY id", listOf(18)
) { it.getString(3) }
```

执行计划可能包含表/索引名称，不上传到默认指标；不根据计划自动创建业务索引。

## 旧版调用迁移

可靠性增强阶段也有公开 API 变化：backupDb/rollbackDb 必须显式传 BackupIdentity；无清单旧备份不再自动恢复；
WriteQueue 回调使用命名参数 onWriteCommitted；pagerFromRaw 的可选项建议使用命名参数。调用方需要重新编译。
调试类包名保持不变，但必须增加可选 debug 模块依赖；核心不再传递提供 RecyclerView 的 Paging runtime。

本轮有公开类型限定名变化，使用旧嵌套类型的调用方需要更新 import 并重新编译；不承诺二进制兼容。
不保留违反文件组织规则的嵌套兼容壳。

| 原类型 | 新类型（同一包内独立文件） |
| --- | --- |
| `core.Trace.Logger` | `core.Logger` |
| `write.RetryPolicy.BusyRetry` | `write.BusyRetry` |
| `migration.MigrationAssistant.Column / Index / ForeignKey / Table / Diff` | `migration.Column / Index / ForeignKey / Table / Diff` |
| `adapter.RecentSqlAdapter.VH` | `adapter.RecentSqlViewHolder` |
| 示例 `ControlsAdapter.Control / ControlRow / VH` | `Control / ControlRow / ControlsViewHolder` |
| 示例 `StatusAdapter.VH` | `StatusViewHolder` |

路由类型、CursorRow/CursorRows、UpdateBuilder、UserPlainVH 和 WalCheckpointScheduler 仅拆分或调整文件名，包名和类型名不变。
顶层扩展函数仍保留原文件入口；数据库 schema、事务与业务行为不变。
