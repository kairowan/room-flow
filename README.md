
---

# 项目简介（Description）

**room-flow** 是一个面向 Android Room 的 Kotlin 扩展库，围绕 `Room + Flow` 的真实痛点提供开箱即用的能力：

* 类型安全的 **Row 映射器**（`row.get<T>("col")`）；
* 按列部分更新的 **PartialUpdate DSL**；
* 无痛的 **批量写入队列**（合并/去重/可配置重试）；
* 表级失效感知的 **Flow 查询** 与 **原生 SQL Flow**；
* 基于原生 SQL 的 **Paging 辅助**（或手写“无限滚动”分页）；
* 智能 **WAL checkpoint 调度器**（按页数/空闲窗口触发，降低 I/O 抖动）；
* 一键 **维护工具**（`integrityCheck / analyze / vacuum / checkpoint`）；
* 多数据库/分库 **路由器**（读写分离/用户维度分库），并聚合 `InvalidationTracker`；
* **迁移助手**（schema diff / 迁移计划 / dry-run）；
* **跨进程失效通知**（`ContentObserver`/IPC）；
* **SQLCipher 适配**（密钥轮换 & PRAGMA）；
* **View 版调试面板**（WAL 页数、锁重试、最近 SQL 采样等指标）。

> 目标：让你在不放弃 Room 的前提下，快速补齐“工程化能力”。

---

# README

## 目录

* [特性](#特性)
* [模块与分包](#模块与分包)
* [环境要求](#环境要求)
* [安装](#安装)
* [快速开始](#快速开始)
* [核心用法](#核心用法)
* [维护与调试](#维护与调试)
* [高级主题](#高级主题)
* [常见问题](#常见问题)
* [Roadmap](#roadmap)
* [License](#license)

---

## 特性

* ✅ **Row 映射器**：`row.get<T>("column")`，列名/类型安全，避免游标列序号错位。
* ✅ **Partial Update DSL**：安全构建 `UPDATE ... SET ... WHERE ...`。
* ✅ **WriteQueue**：批量写、去重合并、可配置重试（指数退避 / 固定间隔）。
* ✅ **Flow**：`observeTables` / `flowQuery` / `rawQueryFlow`，支持表级失效。
* ✅ **Paging 辅助**：`pagerFromRaw`（或**手写无限滚动**示例）。
* ✅ **WAL 调度器**：按 WAL 页数、写入速率、空闲窗口 **自动 checkpoint**。
* ✅ **维护工具**：`integrityCheck / analyze / vacuum / walCheckpointTruncate / estimatedDbSizeBytes`。
* ✅ **路由**：多库路由（读写分离/用户分库），并可聚合多库失效为单一流。
* ✅ **迁移助手**：对比 Room schema JSON 与实际库结构，输出人类可读差异与迁移计划。
* ✅ **跨进程**：基于 `ContentObserver` 的跨进程失效通知。
* ✅ **SQLCipher**：密钥轮换、PRAGMA 兼容、自定义 busy 重试。
* ✅ **调试面板（View）**：实时查看指标与最近 SQL 概要。

---

## 模块与分包

```
roomflow/                      // 核心库
└─ src/main/java/com/kairowan/room_flow/
   ├─ core/                    // Trace / RoomFlowConfig / withBusyRetry / tunePragmas
   ├─ metrics/                 // RoomFlowMetrics
   ├─ crud/                    // withTransactionRetry / readQuery / write
   ├─ flow/                    // observeTables / flowQuery
   ├─ sql/                     // execSQL / rawQuery / rawQueryFlow
   │  ├─ mapper/               // RowMapper.kt
   │  └─ update/               // PartialUpdate.kt
   ├─ paging/                  // pagerFromRaw
   ├─ write/                   // WriteQueue / RetryPolicy
   ├─ maintenance/             // integrity/vacuum/analyze/checkpoint/size
   │  └─ checkpoint/           // WalCheckpointScheduler
   ├─ routing/                 // DbRouter / SimpleDbRouter / aggregateInvalidations
   ├─ multiprocess/            // CrossProcessInvalidation
   ├─ security/                // CipherSupport
   └─ migration/               // MigrationAssistant

roomflow-debugpanel-view/      // 可选：调试面板(View)
```

---

## 环境要求

* **AndroidX Room** ≥ 2.6.1
* **Kotlin** ≥ 1.9（建议 2.0+）
* **JDK**：建议 17
* **minSdk**：24+（库本身兼容 21+，示例以 24 起步）
* 构建工具：KSP（推荐）或 KAPT

---

## 安装

### 1) settings.gradle

```gradle
include(":roomflow")
include(":roomflow-debugpanel-view") // 可选：仅调试使用
```

### 2) app/build.gradle.kts（KSP 版）

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp")
}

android {
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { viewBinding = true }
}

dependencies {
    implementation(project(":roomflow"))
    debugImplementation(project(":roomflow-debugpanel-view"))

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.paging:paging-runtime-ktx:3.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas") // 建议开启，便于迁移助手/AutoMigration
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}
```

> 顶层 `build.gradle.kts` 建议声明 KSP 版本：
> `id("com.google.devtools.ksp") version "2.0.10-1.0.24" apply false`

---

## 快速开始

### 1) 定义数据库（支持“自愈”删库重建，无需手动 +1 版本）

```kotlin
@Database(
    entities = [User::class],
    version = AppDatabase.DB_VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    companion object {
        const val DB_VERSION = 1

        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(ctx: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                // 自愈版：首次打开校验失败 → 自动删库重建（会清数据）
                SelfHealingRoom.build(ctx, AppDatabase::class.java, "app.db") { b ->
                    b.fallbackToDestructiveMigration()
                     .fallbackToDestructiveMigrationOnDowngrade()
                }.also { INSTANCE = it }
            }
    }
}
```

> 想**保数据**：仍需**手动 +1 版本号**并**提供 `Migration/AutoMigration`**，详见下文。

### 2) 在 UI 中使用（示例）

```kotlin
// 批量写入
val writeQueue = WriteQueue(db)
writeQueue.submitAll(listOfUsers, keySelector = { it.name.first() }) { group ->
    dao.upsertList(group)
    group
}.await()

// Partial Update
db.update("users") {
    set("name" to "Alice")
    where("id = ?", 123L)
}

// 表失效 Flow + 重新查询
lifecycleScope.launch {
    db.flowQuery("users") { dao.countAll() }
      .collect { count -> tv.text = "Users=$count" }
}
```

---

## 核心用法

### Row 映射器（类型安全）

```kotlin
val users: List<User> = db.rawQuery("SELECT id,name,age,lastActive FROM users")
    .use { c -> c.mapRows { row ->
        User(
            id = row.get("id"),
            name = row.get("name"),
            age = row.getOrNull("age"),
            lastActive = row.get("lastActive")
        )
    } }
```

### Partial Update DSL

```kotlin
db.update("users") {
    set("name" to "Bob", "age" to 28)
    where("id = ?", 1001L)
}
```

### Flow：表级失效/Flow 查询/原生 SQL Flow

```kotlin
// 1) 仅监听表变更
db.observeTables("users").collect { /* 表变更了 */ }

// 2) 表变更触发重新计算
db.flowQuery("users") { dao.countAll() }.collect { count -> ... }

// 3) 原生 SQL + Flow（适合复杂聚合）
db.rawQueryFlow("SELECT COUNT(*) FROM users WHERE age>=?", arrayOf(18))
  .collect { c -> c.use { if (c.moveToFirst()) println(c.getInt(0)) } }
```

### 批量写入（合并/重试）

```kotlin
WriteQueue(db, retryPolicy = RetryPolicy.Exponential(maxRetries = 3))
    .submitAll(items, keySelector = { it.id }) { group ->
        // 同 key 的多次更新会合并到同一次事务里
        dao.upsertList(group)
        group
    }.await()
```

### Paging 辅助 或 手写“无限滚动”

```kotlin
// Pager 版本
val pager = db.pagerFromRaw(
    pageSize = 20,
    tables = arrayOf("users"),
    queryProvider = { limit, offset ->
        SimpleSQLiteQuery("SELECT * FROM users ORDER BY id DESC LIMIT ? OFFSET ?", arrayOf(limit, offset))
    },
    mapper = { c -> /* Cursor -> Item */ }
)

// 或者使用 README 示例中的 UserPlainAdapter + LIMIT/OFFSET 手写分页
```

---

## 维护与调试

```kotlin
// 维护
db.integrityCheck()
db.analyze()
db.vacuum()
db.walCheckpointTruncate()
val size = db.estimatedDbSizeBytes()

// 智能 WAL 调度
val scheduler = WalCheckpointScheduler(db)
scheduler.start()   // 根据 WAL 页数/空闲窗口自动 checkpoint
scheduler.stop()

// 调试面板（View）
startActivity(Intent(this, RoomFlowDebugPanelActivity::class.java))
```

---

## 高级主题

### 多库/分库路由

```kotlin
val router = SimpleDbRouter(primary = db)
router.registerUserDb("u1", dbU1)

val rdb = router.readable(RouteContext(userId = "u1"))
val wdb = router.writable(RouteContext(userId = "u1"))

// 聚合多库失效
router.aggregateInvalidations("users").collect { /* 任一库 users 表变更 */ }
```

### 迁移助手

```kotlin
val diff = MigrationAssistant.compareSchema(db, schemaJsonFromRoom)
val plan = MigrationAssistant.planMigration(diff) // 生成建议 SQL
```

### 跨进程失效通知

```kotlin
val hub = CrossProcessInvalidation(context, "your.provider.authority")
hub.changes("users").collect { /* 收到其他进程的变更 */ }
hub.notifyChanged("users")
```

### SQLCipher 支持（可选）

```kotlin
val factory = SupportFactory(passphrase) 
val db = CipherSupport.applyFactory(Room.databaseBuilder(ctx, AppDatabase::class.java, "secure.db"), factory)
    .build()
CipherSupport.rekey(db.openHelper.writableDatabase, "new-key")
```

---

## 常见问题

**Q1. 运行时报 `AppDatabase_Impl does not exist`？**
A：你没启用 Room 编译器或者不在同一模块。确保：

```kotlin
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")
```

并使用 KSP 插件；`schemas/` 目录存在（若设置了 `room.schemaLocation`）。

**Q2. `Room cannot verify the data integrity`？**
A：你改了表结构但没升 `version`/没提供迁移。

* 开发期：用 **自愈构建** 或 `fallbackToDestructiveMigration()`（会清库）。
* 线上：**version +1** 并提供 `Migration`/`AutoMigration`。

**Q3. PagingSource 报 `invalidate()` 不能重写？**
A：Paging 3 里 `invalidate()` 是 final。注册 `registerInvalidatedCallback{}` 在回调里移除 `InvalidationTracker.Observer` 即可。

---

## Roadmap

* [ ] RowMapper 的 KSP 编译期生成版（零反射、零列名字符串）
* [ ] 更细粒度的 SQL 采样策略 & 导出
* [ ] WAL 调度器策略可配置（窗口、阈值）
* [ ] 多库路由的读写一致性策略示例（主从延迟容忍）
* [ ] Compose 版调试面板

---
---
