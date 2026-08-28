# 性能基线与优化边界

2026-08-28，专用 API 35 arm64-v8a 模拟器，Room 2.6.1，Debug instrumentation。
每个查询预热 3 次、记录 9 次；50 行页面，深分页读取最后 50 行。使用独立普通文件库，测试后删除。
这些是热缓存开发基线，不是发布版/真机性能承诺；9 样本的 p95 即最大值，不用于显著性推断。

| 场景（中位毫秒） | 1 万行 | 10 万行 |
| --- | ---: | ---: |
| 首页 | 0.131 | 0.109 |
| 深分页 OFFSET | 0.157 | 0.407 |
| 深分页主键游标 SQL | 0.136 | 0.110 |
| age 过滤 COUNT，无索引 | 0.198 | 1.343 |
| age 过滤 COUNT，有索引 | 0.076 | 0.074 |

EXPLAIN QUERY PLAN 确认过滤计数使用 `COVERING INDEX benchmark_age`。
100 个独立队列提交总耗时为 62.1 / 56.0 ms，最大排队等待为 58.5 / 53.1 ms；不应从两次测量反推数据量越大写入越快。
两个冷 Flow 订阅者在初值 + 10 次逐次写入下执行 22 次 SQL，使用 `shareIn` 后为 11 次。
10 万行 SUM 查询的对应整段耗时为 59.9 / 35.5 ms，包含提交、失效调度与下游等待。

同日 Room 2.8.4 在相同模拟器上复测（消费工程构建结束后单独运行）：

| 场景（中位毫秒） | 1 万行 | 10 万行 |
| --- | ---: | ---: |
| 首页 | 0.136 | 0.108 |
| 深分页 OFFSET | 0.167 | 0.445 |
| 深分页主键游标 SQL | 0.136 | 0.097 |
| age 过滤 COUNT，无索引 | 0.203 | 1.346 |
| age 过滤 COUNT，有索引 | 0.084 | 0.072 |

2.8.4 队列 100 次提交为 77.7 / 63.6 ms；冷流/共享流仍为 22 / 11 次查询，10 万行整段耗时为 51.1 / 31.1 ms。
两个版本的测量支持同一优化方向，但不是隔离变量的版本性能对比，不能据此判定哪个 Room 版本更快。

## 已采用的最小优化

- 示例只向后加载、按非空唯一主键 id 降序，改用 `WHERE id < ? ORDER BY id DESC LIMIT ?`。
  首次不使用游标条件；取消走 rawQueryList，失效仍刷新，已有 generation 防止旧结果覆盖。
  回归覆盖主键间隙、负值、极大值、删除已读行、插入更新行、空表和可空字段。
- 通用 RawPagingSource 保留 OFFSET 和 COUNT 快照，不能在保持任意跳页/占位符契约时直接替换为 keyset。
- 不自动为业务表创建索引；索引需要调用方通过 Migration 管理并评估写入成本。
- 不自动全局共享 Flow。仅当调用方确有多个消费者时在明确生命周期内 shareIn/stateIn；
  示例当前单订阅路径不新增多余缓存。distinctUntilChanged 只过滤输出，不能减少已执行的 SQL。

## 历史测量与后续复验

PerformanceBaselineTest 已按所有者要求随测试目录删除；以上数据为历史测量，不是当前仓库可直接执行的基准。后续复验须在宿主或独立验证环境安排，不自行恢复测试目录。

历史报告每行 `ROOMFLOW_BENCH` 输出原始 9 样本、median/p95、进程 CPU 时间及观测 Java heap 使用量。
CPU 是进程范围，包含其他 app 线程；heap 是采样值，包含测试开销，既不是分配量，也不是精确峰值/泄漏证据。
设备启动、磁盘缓存、JIT/GC、后台任务都会影响结果；需真机 Release/Android Benchmark 后才能设性能门槛。

依据：[SQLite OFFSET/keyset](https://sqlite.org/rowvalue.html)、[协程 shareIn](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/share-in.html)。
