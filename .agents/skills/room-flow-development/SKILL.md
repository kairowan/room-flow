---
name: room-flow-development
description: Maintain the room-flow Android Room extension library and its demo; apply this repository's Kotlin style, database safety, coroutine contracts, compatibility policy, and regression checks when fixing or extending this project.
---

# room-flow 项目开发

## 先定位

- 在仓库根阅读 `AGENTS.md`、`PLAN.md` 和相关 `README.md` API；用 rg 找实现与所有调用者，再追踪事务、失效与资源生命周期。
- `room-flow/` 是公开库，包 `com.kairowan.room_flow`；`app/` 是本地集成示例，包 `com.kairowan.roomflow`。示例必须依赖本地模块。
- `room-flow-debug/` 是可选调试 UI 模块，示例仅 debugImplementation；核心不得引入 AppCompat/Material/RecyclerView 或调试 Activity。
- 公开父类/接口所属依赖必须通过 api 暴露给消费者；在独立制品消费工程检查，不依靠示例应用自己声明的依赖掩盖缺失。
- 当前维护 Room 2 的 Android OpenHelper 路径，最低 Android API 24。不要把 Room 3、SQLiteDriver、KMP 或 SQLCipher 完整支持当成已有能力。
- SQLite Framework 的最低依赖约束为 2.5.0；不可降回有 onOpen 异常误删库缺陷的 2.4.0。修改依赖必须重跑真实文件保留/备份测试。
- Kotlin/KSP 版本保持匹配；示例按 roomVersion 为 2.6 选择 KSP1、2.7+ 选择 KSP2。不得全局强制同一模式：2.6/KSP2 有 JVM signature 错误，2.8/KSP1 有 schema 序列化类路径冲突。
- 先复用已有 helper、Room/SQLite/协程能力。不为单一修复创建框架、通用仓储、DI 或新依赖。

## Kotlin 与文件风格

- 4 空格、UTF-8、LF、结尾换行；类/文件 PascalCase，函数/属性 camelCase，常量 UPPER_SNAKE_CASE。
- 使用明确 imports，避免通配符；一条语句一行，不使用分号压缩控制流。
- 优先 val、表达式函数、use/withContext 和标准集合操作；公开 API 明确返回类型及线程/取消/关闭约定。
- 实现放入现有功能包。小的私有辅助函数可留在调用文件，辅助类必须遵循下方独立文件规则；避免新增 Base/Manager/Utils 抽象。
- 注释解释意图、边界和风险，不重复代码；不新增 ASCII 作者头、无内容 TODO 或装饰分隔线。
- 保留无关旧代码和用户改动，只有触及的代码遵循新风格，不做整仓格式化。
- 有意限制写 `ponytail:` 注释，并注明上限和未来升级路径。

## 类、接口与文件组织（强制）

- 一个 Kotlin 文件最多声明一个具名类型：一个 class 或一个 interface，文件名与该类型名一致。普通 class、data class、enum class、sealed class、abstract class 均遵守此规则，不能在同一文件中并列多个类。
- 类之间默认禁止嵌套，包括 nested class、inner class 和局部具名类；不能因为类较小、私有或仅被一个类使用而合并到同一文件。
- 唯一例外：独立的数据字段/数据模型类，职责仅为描述数据结构时，可以在该 class 内嵌套多个 data class，以表达该模型的组成。内部不能借此混入普通业务类或 interface；普通业务类内的数据模型仍须拆成独立文件。
- interface 必须单独放在同名文件中，不能与实现类、其他类或其他 interface 共用文件，也不能嵌入其他类型内部。
- 新增或调整类型时检查上述规则；不要仅为落实此约定批量拆分无关旧代码。用户只要求更新 Skill 时，不自动重构现有类。

## 必须保持的数据库契约

- 不因 SQLiteException、锁冲突、迁移失败或密钥错误自动删库。破坏性操作必须是明确的产品选择，不能称为自愈。
- 挂起事务用 Room withTransaction；不要用 begin/endTransaction 包 suspend lambda，也不能认为 IO dispatcher 固定线程。
- 事务重试只针对数据库 busy/locked，整个事务重做；用户 block 不能携带不可重试外部副作用。
- CompletableDeferred 只能在 commit 后成功；异常必须回滚并传播。关闭/取消必须终结排队任务，不留下永久 await。
- 原生写入必须参与 Room 事务/失效机制；Flow 串行重查，不能以并发 launch + conflate 代替顺序保证。
- 参数值绑定；标识符校验/转义；WHERE 是调用者可信 SQL。全表修改需要显式确认。
- Cursor/Statement 必须关闭；不要跨挂起边界泄露游标。读写磁盘不能在主线程。
- 长原生查询使用 rawQueryList/rawQueryFlow，分页复用 withQueryCancellation；取消信号覆盖执行与遍历，映射后关闭 Cursor 再返回。普通 IO/withTimeout 不是原生取消。
- 事务内查询保持 Room 事务线程；mapper 不返回 Cursor，不做不可取消的长阻塞工作。不要给每个查询调用者各写一套取消桥接。
- readQuery 与 rawQueryList 一样：调用点已在 Room 事务线程时直接执行，不局部重试事务片段。外层事务决定回滚/整体重试。
- RoomDatabase 事务上下文检查复用 inOpenTransaction（isOpen 后才检查 inTransaction），不能在主线程判断时隐式开库。SupportSQLiteDatabase 已打开的连接不适用这项包装。
- 备份恢复必须协调全部连接和 WAL；不能把打开数据库的主文件 copyTo 当在线备份。
- 迁移不猜默认值、不遗漏主键/外键/索引；复杂变更明确要求手写 Migration/AutoMigration。
- 保留 CancellationException；记录异常不是吞异常的理由。日志不记录密钥、绑定值或用户数据。
- Trace 默认无日志接收器；自定义接收器默认不接收 Throwable 详情。logExceptionDetails 只允许可信诊断时显式开启，消息/标签仍由调用方脱敏。
- rawQueryEach 是有副作用的逐行消费，不自动重试，也不得套在会重试的外层事务；导出写临时产物，成功后才发布，不积累整表或泄露 Cursor。

## 已有数据库接入与 SQL 规范

- 接入 SDK 不等于迁移 schema；默认复用宿主已有 Room 实例、文件名/路径、版本及 builder 配置。不得为了接入改版本、重建库或替换加密工厂、转换器、回调。
- 宿主负责 schema 和 Migration；SDK 不拦截所有 DAO，也不把进程内 WriteQueue 当所有线程/进程的全局锁。检查实际解析的 Room runtime/compiler 与 SQLite 依赖版本。
- 静态查询优先 @Dao/@Query。用户已明确要求实体类型安全构造器：typed API 通过可选 room-flow-compiler 生成 XxxTable，不能退化为字段字符串/运行时反射，也不替代 Room 的建库/DAO/迁移处理器。
- RoomFlowEntity 第一版仅支持公开顶层平面 data class、常见标量、@ColumnInfo 列名、主键/复合主键；@Ignore 放在类体属性而非主构造参数。复杂映射必须编译失败，不猜测 Embedded/Relation/TypeConverters/FTS 的行为。新增支持必须有生成代码和数据回归。
- EntityTable/EntityColumn 保留实体和字段值类型；条件、排序、赋值都验证表归属，防御 unchecked cast。动态 SQL 在运行时绑定值，build 目录仅生成静态映射代码。
- 二次封装复用不可变 QuerySpec：不持有数据库/Context/Cursor、不保存延迟执行的业务回调；where/orderBy/page 派生新定义，BLOB 值防御性复制。可选条件 null 跳过，空值不自动省略。
- EntitySelect 保留可变旧契约，忽略 where/page 返回值仍生效；跨调用复用 toSpec 而非共享构造器。SQL 生成集中在 QuerySpec，不能给新旧调用路径复制两套实现。
- firstOrNull/exists 遵循当前分页偏移，count 返回当前窗口 Long，totalCount 显式忽略分页；不得先全量 list 再统计。pageResult 覆盖原分页并用一次 Room withTransaction 读取 items/total，不增加外层重试；同快照仅覆盖本次调用，说明读事务和 COUNT 对性能的影响。
- 多列 DTO 复用 Projection 的字段类型和 map/zip 组合，检查表归属，按列位置映射重复列；不引入反射/任意 SQL mapper。project 捕获定义，ProjectedSelect 不可变；用户 mapper 必须快速纯函数、无写库/外部副作用，执行复用 rawQueryEach 的无重试路径。
- 统计复用 Aggregate：空集/全 NULL、整数溢出、浮点非有限数明确处理；sumLong 限 INTEGER，sumDouble/average 不承诺金额精度。统计/分组遵循输入窗口，groupBy 仅单键单指标；不混淆输入分页和分组结果分页，不提前新增 JOIN/HAVING 插件。
- notIn 为 isIn 的集合补集，NULL 未列入排除列表就保留；!condition 则保留 SQL 三值逻辑。新增条件必须写清 NULL/空集合/通配符语义，继续绑定值及检查 900 参数上限。
- seekAfter 用 QuerySpec 当前排序加完整主键，处理 ASC/DESC 混排及 NULL；每页由同一个 base 派生，null 明确表示第一页，不在空页后重新用 null 翻页。不承诺跨页快照、游标授权/序列化或排序字段被修改时无重复遗漏。
- observe 在调用时捕获定义，冷流订阅时查询，复用 observeTables/原生取消串行重查；不新增全局 Scope/缓存，不在事务内等待 Flow，不将表失效当逐写事件。结果无限制时提醒业务显式分页。
- 用户扩展使用类型化条件和普通扩展函数；示例 Repository 只借用数据库，不关闭/重建它。业务权限检查返回结果，不把查询描述当权限隔离保证；不开放任意 SQL 片段绕过保护。
- typed 插入默认 ABORT，不做 REPLACE；实体更新用全主键匹配，部分更新/删除缺条件需显式 allRows；不允许通过部分更新修改主键。无 page 就无 LIMIT，大结果用 each，不自动分批改变事务。
- where 不是防误删证明（notIn(emptyList()) 匹配全部）。业务有行数预期时使用 typed execute(maxAffectedRows)，在执行语句的同一事务内检查直接影响行数，超限必须回滚，不先 COUNT 后独立写、不静默截断。上限不统计触发器/级联行数，build/raw 不携带该执行保护。
- 表身份校验只检查表/视图命名空间，不能因合法的同名触发器而误拒宿主表；仍校验生成字段和主键。
- 原生字符串查询/Flow 在入口复制 BLOB 参数；Flow 订阅和失效重查复用该快照。自定义 SupportSQLiteQuery 的绑定生命周期仍由提供者负责，不能宣称任意查询都不可变。
- typed 生成器是 JVM 构建期模块，不进入 Android AAR；不新增运行时 kotlin-reflect 或编译器依赖。KSP 1/2 两边界均需生成与执行回归，非法类型/跨实体字段需编译失败验收。
- 复杂 SQL 仍可集中到独立 XxxQueries 文件，返回 SupportSQLiteQuery；这一路径不提供编译期字段保证，不能与 typed API 混称。
- SQL 关键字大写、显式列名；值绑定，动态表名/列名/排序方向只允许白名单。标识符转义不能替代白名单，WHERE 字符串不是不可信输入的安全容器。
- 分页必须有稳定且包含唯一键的顺序、合法且有上限的页大小；按实际 SQLite 能力处理大集合绑定上限。明确空 IN（通常无匹配）、NULL、空字符串及 LIKE 通配符的语义。
- UserQueries.page 示例固定 id 降序、1..500 条；nameContains 的 NULL 表示不过滤、空串匹配全部、%、_、反斜杠按字面量处理。大小写沿用 SQLite LIKE 语义，不承诺 Unicode 语言学搜索。
- 全量导出仅通过 rawQueryEach 逐行消费；UI 列表使用有界查询。更新/删除有条件，全表操作显式表达；部分更新复用现有 UpdateBuilder。
- 父表 upsert 使用 Room @Upsert/明确 UPDATE；不能将 INSERT OR REPLACE 当无副作用更新。修改冲突策略必须验证子记录、默认值、返回值语义；@Upsert 更新已有行时返回 -1，不是原行 id。
- 历史 Migration 和旧版测试 fixture 必须冻结 SQL/字段定义；不得引用随当前业务演进的 UserQueries/DAO 常量。只在 Room Migration 回调中执行升级，不吞异常继续提交。
- 迁移检查包含字段值、主键、NULL/默认值、精度、关系、索引、自动主键及每个支持历史版本的升级；结构通过/行数相等/哈希正确都不等于业务完整。备份恢复后以匹配旧 schema 的数据库打开，再重新升级。
- 宿主发布验收仍需真实旧 APK → SDK APK 的覆盖安装验证。旧 APK 不依赖 SDK，验证使用隔离文件，不能在验收端自动重建丢失的历史库掩盖丢数据；不因此自行恢复本仓库已删除的测试目录。
- 真机验证须有用户授权、明确设备及 Android user；使用独立随机 verification applicationId，不覆盖/清空/卸载原示例应用。测试成功后保留测试包、文件与报告，清理另行说明。不得在真机执行模拟器专用的 force-stop 故障脚本。
- 真机测试停滞先区分线程锁等待与系统冻结；保留初次超时及原始 instrumentation 状态。仅在已授权独立测试包中验证前台恢复，不弱化超时/跳过用例来掩盖失败，也不把前台通过说成后台长时可靠性验证。

## 并发、UI 和维护

- 队列有容量上限与确定的关闭策略；共享指标使用原子更新/锁，禁止非原子读改写。
- closeAndJoin 为取消后等待，drainAndJoin 为拒绝新任务后排空；未 flush 的暂存任务必须显式处理。取消排空等待不取消队列，关库仍须等待所有任务退出。
- 不在队列任务/事务中再次入队并等待。大批次必须显式决定事务边界，不自动分批；提交后回调只做快速本地通知。
- 关库顺序：停止生产者/并发 start，closeAndJoin 队列，stopAndJoin 维护任务，再由所有者关库。路由注销不隐式关闭共享数据库；不承诺撤销旧引用。
- 备份恢复 identity 来自可信目标配置；校验在临时副本执行，清单摘要不等于真实性签名。覆盖前必须保留恢复点，不能覆盖唯一证据。
- 备份空间估算不等于预留空间；100% 复制进度不等于发布完成。CancellationSignal 仅在校验/复制/发布前生效，开始发布后完成清单；回调不得重入或修改文件。
- 关闭全部连接后，非空 journal 仅放行完整 28 字节头全零的冷 PERSIST 日志；热/未知/不完整头、非空 WAL 和残留 SHM 仍拒绝。不删除 journal，不把该检查当成跨进程关库证明或 SQLite 恢复器。
- 指标使用按数据库实例的原子快照，不能强引用数据库或包含账号/SQL 参数；明确统计覆盖范围，不能冒充全量 DAO 监控。
- 可选 QueryObservation 只覆盖 List/Flow/Each 的单次游标消费，含映射/关闭、不含排队退避；不覆盖 DAO/同步 rawQuery/直接分页查询。回调快速且生命周期结束清空，不自动建索引。
- transactions 是成功顶层包装操作，不是物理 commit；嵌套不重复计数/重试，队列结果与事务结果分开。时长口径包括哪些等待必须写清。
- 优化前在宿主或独立环境测量性能；本仓库原 PerformanceBaselineTest 已随测试目录删除。模拟器 Debug 的少量热缓存样本只能作为开发基线；不据此承诺真机峰值或性能 SLA。
- 示例 UserPaging 的 keyset 仅用于唯一 id；QuerySpec.seekAfter 支持显式多字段游标，不擅自改变通用 OFFSET/COUNT/占位符契约。多订阅共享用调用方生命周期 scope，不新增全局缓存。
- 时长用单调时钟；只有展示时间戳用墙上时钟。PRAGMA 的返回状态需要检查，checkpoint busy 不一定抛异常。
- Fragment 的 UI 协程使用 viewLifecycleOwner；onDestroyView 清 binding、取消 View 相关工作，避免重复订阅及旧分页结果覆盖新结果。
- 多库未知用户不静默进入默认共享库；跨进程通知需要真实 Provider 和明确接线，不伪装自动同步。

## 验证与交付

- 项目所有者明确要求删除测试目录；不得自行恢复 `src/test`、`src/androidTest`、`src/debugAndroidTest` 或 Release 测试入口，也不新增 JUnit/Runner 依赖。保留已有构建检查、编译反例、API 基线及历史报告；这些不等于运行时回归。
- 运行 `checkKotlinTypes`：复用 Kotlin 编译器 PSI 检查单类型/同名文件/禁止嵌套，并保留规则自检。该编译器仅用于构建检查，不进入库依赖。
- CI 保留 Room 2.6.1/2.8.4 的构建、制品消费、编译拒绝检查，不再配置设备测试矩阵；同时构建开启 R8 的示例 Release，确认不含调试模块、测试组件或依赖。未实际运行的远程 CI 不标记通过。
- 每类非平凡变更仍需最小可运行检查，优先复用保留的检查入口；需要 Android 运行时验证时在宿主或独立环境安排，并明确记录未验证项，不把空测试任务或构建成功当功能回归通过。事务/失效/分页/文件安全仍须验证失败或取消路径。
- 备份故障检查复用内部 atomicCopy 的字节复制注入点，不向公共 API 暴露测试开关。模拟 IOException/不完整文件集不能宣称通过磁盘满、kill 或断电实测。
- 本地发布通过 scripts/check-artifact-consumer.sh，在独立 verification/consumer 工程检查 AAR、传递依赖及 POM/GMM；只允许本地暂存，不配置远程凭据。
- 两模块 JVM 可见签名由 checkJvmApi 与 verification/api 基线检查，更新需显式 --update 并审阅；不把 JVM 描述符检查当 Kotlin metadata/inline/行为兼容性验证。
- 原设备/覆盖接入/进程中断脚本已随测试清理删除。后续故障验证仅在专用环境执行，force-stop 仅限明确授权的隔离进程；实际进程中断仍不等于断电/磁盘满实测。
- 测试数量与设备结果只在 PLAN 最新阶段集中记录，README 引用它，避免不同文档保留相互矛盾的摘要。
- 商用可用性以 docs/RELEASE-CHECKLIST.md 的发布门槛和实际证据为准。Debug 真机通过、Release 编译通过、CI 已配置分别陈述；不能替代最低系统/Release 运行/后台恢复/宿主历史迁移。未决定许可证、版本、发布坐标或生产签名时不得代替所有者选择或宣称已正式发布。
- 构建基线：`bash scripts/check-room-compatibility.sh --no-daemon --max-workers=2`，连续检查 Room 两边界，不连接设备，不执行单元测试。
- 网络 TLS 错误与代码编译错误分别报告。检查 JDK、Gradle 用户代理及仓库可达性；不得关闭 TLS/证书校验、硬编码个人代理或清空全局缓存。
- `README.md` 只承诺经过验证的版本/模式；行为或签名变更同步示例、迁移提示和 PLAN 验证记录。
- 本 skill 不授权提交、发布、推送或修改其他项目。用户只要求分析时，不实施修改。
