# room-flow 修复计划

## 范围和原则

保留 Android / Kotlin / Room 2 工具库定位。优先复用 Room、SQLite、协程已有能力，不增加通用框架。
默认保留用户数据；成功只表示事务已经提交；取消、失败必须传递给调用者。

本轮目标：Room 2.6.1 与 2.8.4 的 Android OpenHelper 模式。中间版本不等于逐版本实测。
SQLiteDriver 模式与 Room 3 是不同接口体系，本轮明确不宣称支持，不静默混入破坏性升级。

## 2026-08-28 0.2.0-rc.1 预发布（当前状态）

- 用户明确要求新 tag 和便捷依赖引入；沿用 JitPack，三个模块使用 `com.github.kairowan.room-flow:<module>:0.2.0-rc.1`。仅预发布，不关闭商用/许可证/生产签名门槛。
- `releaseVersion` 显式切换版本化发布，默认验收坐标保留；同一份 Room 2.6.1 基线制品供两个宿主 Room 版本消费，Debug 正确传递依赖同版本核心，编译器另发 JAR/sources。
- 本地 8 组合（Room 2.6.1/2.8.4 × 核心/Debug × GMM/POM-only）消费通过；默认验收路径 Room 2.6.1 的 4 组合回归通过。最终脚本兼容 macOS Bash 3 的空参数数组；首次运行中编辑脚本导致尾部读取异常，原日志保留，最终版本已另行完整重跑。
- JitPack 使用的三个 `publishToMavenLocal` 任务已在临时 Maven 目录通过；类型规则、模块边界、JVM API、POM/GMM 坐标、源码 JAR 和 ZIP 完整性检查通过。未向个人 `~/.m2` 写入。
- 上次远端 CI `33150542456` 两边界的构建/制品检查完成，编译拒绝步骤因 runner 没有 rg 失败；将单处日志 ERE 匹配改为系统 grep，不跳过非法用法检查、不新增运行时依赖。新 tag 的 [CI 33151205433](https://github.com/kairowan/room-flow/actions/runs/33151205433) 已通过两边界的构建与本地制品步骤，记录时仍在执行编译拒绝检查，未标记全组通过。
- 注释 tag `0.2.0-rc.1` 与提交 `01e3fda154018ec0a0f047d5225751a570ac443e` 已推送；[GitHub 预发布](https://github.com/kairowan/room-flow/releases/tag/0.2.0-rc.1) 已附三个模块的 Maven ZIP 和 SHA-256。重新从 GitHub 下载后，摘要和 ZIP 完整性检查通过。
- JitPack 首次请求 404，随后实际构建成功（exit 0），发布三个独立模块；API 返回 status=ok、isTag=true、对应上述提交。镜像旧 sdkmanager 报缺 JAXB，但 AGP 使用预置 licenses 自动安装 SDK 36 / Build Tools 35 后完成构建；后续 master 删除多余的 sdkmanager 前置调用，不移动已发布 tag。
- 指定 `RELEASE_REPOSITORY=https://jitpack.io` 后，独立消费者 8 组合全部通过（脚本 exit 0，含正常 Gradle 构建缓存）；实际解析远端依赖并验收生成/编译输出、Debug 传递依赖及运行时边界，不重新发布或引用本地 Maven 仓库。远端 Debug POM 另行下载确认依赖同版本核心。这是制品接入验证，不是设备运行回归。
- 本地/远端消费检查、JitPack 日志与 Maven ZIP：`/tmp/roomflow-tag.N2CtyJ`。不恢复测试目录、不操作设备、不改 SDK 业务实现。

## 2026-08-28 README 功能重写与动画

- README 按实际 SDK 组织模块接入、typed CRUD、DTO/统计/游标、原生 SQL、Flow/Paging、事务队列、离线备份迁移、维护诊断和用户扩展，链接对应源码；未虚构发布坐标或扩大兼容承诺。
- 将较长的线程/取消/数据安全/升级约定保留到 `docs/API-CONTRACTS.md`。新增两段功能示意 GIF 及静态 PNG，明确不是真机录屏/性能证据；动画仅播放两轮，生成脚本为 `scripts/render-readme-media.py`。
- 从 README 提取的 18 个 Kotlin 源码片段在临时目录编译，通过 Room 2.6.1/KSP1 和 2.8.4/KSP2；含实体注解及实际 KSP 生成、查询/CRUD/维护/Debug 入口。不执行数据库操作，Gradle 接入片段按现有模块配置核对。
- GIF 帧像素、时长、循环次数和体积检查通过；文档本地链接/锚点与 `git diff --check` 通过。未在远程 GitHub 页面验收，未运行设备测试。
- 编译及临时源码证据：`/tmp/roomflow-readme.ApaWau`。本轮只改文档和媒体生成工具，不修改 SDK 业务源码、不恢复测试目录、不操作设备。

## 2026-08-28 按所有者要求删除测试目录

- 保留 `app/src/androidTest`、`app/src/debugAndroidTest`、`room-flow/src/test` 的删除；清理残留 `app/src/releaseVerification` 和 `verification/legacy-fixture/src/androidTest`。
- 删除对应 Runner/JUnit 依赖、Release 测试签名及 keep 配置，以及依赖已删除用例的设备/覆盖接入/进程中断脚本；正常 Release 的无测试依赖检查始终开启。
- CI 和本地兼容脚本仅保留构建、类型/API、模块边界；CI 另保留独立制品消费和编译拒绝检查，不再运行设备矩阵。
- SDK 业务源码、Room schema、旧版数据库定义、制品消费工程、编译反例与 API 基线保留。Skill 明确禁止自行恢复测试目录。
- 当前构建验证：Room 2.6.1 / 2.8.4 的 AAR、Debug APK、R8 Release APK 均构建通过；两版类型规则（108 文件、9 项规则自检）、JVM API 基线和模块边界检查通过。输出位于 `/tmp/roomflow-test-cleanup.fA602V/build.log`。
- 测试目录与失效构建引用检查、脚本语法检查和 `git diff --check` 通过；未运行远程 CI、制品消费或编译拒绝全组。本轮不安装 APK、不执行单元/设备测试、不操作手机或数据库。

以下阶段均为删除测试前的历史记录。报告与既有制品保留，但相关测试源码/脚本已不在当前仓库，不能据此宣称当前仍具备自动运行时回归；正式发布门槛没有因此降低。

## 2026-08-28 验收后修复（历史记录，正式发布门槛仍未全部关闭）

按复查顺序修复，不用新增功能掩盖发布阻塞：

- [x] 1. 修复 API 24 checkpoint fixture、冷 journal 离线备份、Release 测试设施；未解决的供应链和外部门槛见下文。
- [x] 2. readQuery 保持事务线程且不局部重试；表身份校验不误拒同名触发器。
- [x] 3. 可选写入影响行数上限（事务内失败回滚）；原生字符串查询 BLOB 参数快照。
- [x] 4. 两 Room/KSP 边界及 API 24/35 设备回归，保留真实失败记录；审阅 JVM API 差异。
- [x] 5. 同步 Skill、用法和发布清单，未通过门槛不标记商用验收完成。

### 修复内容与边界

- 备份仅放行完整 28 字节头全零的冷 journal；不删除日志，热/未知/短头、WAL/SHM 保护保留。
  依据 [SQLite 冷/热日志与提交规则](https://www.sqlite.org/lockingv3.html)。JVM 覆盖非零/短头与原字节保全；设备测试覆盖备份、恢复、未知头拒绝。
  API 35 平台会截断日志，因此其新用例明确使用零头 fixture；不是宣称该系统原生保留 PERSIST 文件。API 24 已验证实际残留 journal 与进程故障路径。
- checkpoint 用固定注释前缀让 SQLite 执行真正的 DEFERRED 读事务，避开 Android 将前导 BEGIN 重写为排他事务的快捷路径；仍断言另一连接可写、checkpoint busy 被报告。只改测试 fixture，不改 SDK 事务实现。
- Release 验证采用单 APK 联合 R8，只保留 JUnit 测试及 Runner 反射入口。未全量 keep SDK/Kotlin/AndroidX，未禁用混淆。
  QuerySpec 两版分别映射为 j1.K / o1.J。验证源码、Runner、JUnit、测试签名仅在 releaseVerification=true 时加入，且必须指定独立验证包。
  Debug 专属 UI 用例移动到 debugAndroidTest；正常 Release 禁止引入 Runner/JUnit/调试模块。
- readQuery / raw 查询 / 写事务 / 队列共用 inOpenTransaction，避免检查事务时在主线程首次开库。事务内同步读不切线程、不局部重试。
  readQuery 是 inline；宿主必须重新编译才能获得修复。未更改已打开的 SupportSQLiteDatabase 的事务接口。
- EntityTable 只在表/视图命名空间校验身份，合法同名触发器仍可 CRUD，触发器不被删除。
- typed update/delete 新增 execute(maxAffectedRows)，超限在同一事务回滚，包括数据库级联修改；负数拒绝，0/边界/嵌套事务已验证。
  旧无参 execute 和 notIn 空集合语义不变；限制只计算直接影响行数，不限制级联数量，不是 LIMIT，build/raw 不携带执行保护。
- 原生字符串 SQL/Flow 入口复制 BLOB 参数；Flow 构造后修改原数组/列表、重复订阅和失效重查保持原条件。自定义 SupportSQLiteQuery 仍由提供者管理绑定。
- Wrapper 加入从 [Gradle 官方摘要](https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256) 核对的 SHA-256；这不等于 Maven 依赖验证或漏洞修复。

### 本轮候选验证证据

JDK 17；API 24 专用 emulator-5582，API 35 荣耀 BRP-AN00、Android user 0。
本轮报告根目录 `/tmp/roomflow-fixes.HdgxIj`，均为独立验证包，无卸载/清空原应用。

| 检查 | Room 2.6.1 / KSP1 | Room 2.8.4 / KSP2 |
| --- | --- | --- |
| Debug API 24 | 59 通过、3 opt-in 跳过、0 失败 | 同左 |
| Debug API 35 真机 | 59 通过、3 opt-in 跳过、0 失败 | 同左 |
| 联合 R8 API 24 | 57 通过、3 opt-in 跳过、0 失败 | 同左 |
| 联合 R8 API 35 真机 | 57 通过、3 opt-in 跳过、0 失败 | 同左 |
| JVM | 11/11 | 11/11 |
| API 24 备份复制中断 / 事务提交前中断 | 两项实际 force-stop 后恢复通过 | 同左 |
| 单类型规则 | 138 文件、9 自检通过 | 同左 |

跳过项为独立两 APK 接入、性能基准、分阶段进程故障；进程故障已另行执行，不冒充普通组执行。
本轮未重新执行两 APK 覆盖接入和编译拒绝全组；它们的旧候选证据保留在历史段落，不充作本轮执行结果。
联合 R8 验证扩展了调用图，不等同于正式宿主 APK，也不是灰度或长时后台验收。
JVM 基线审阅并更新：新增两个公开 execute(Int) 重载、内部冷日志/事务检查入口及内部生成签名变化；保留旧公开 execute() 签名，不宣称完整 Kotlin metadata/行为兼容证明。

关键日志：`*-debug-*-candidate.txt`、`api35-release-*-candidate.txt`；
R8 API 24 与 APK 在 `roomflow-device.2z8dM0/2.6.1/`、`roomflow-device.XSTibb/2.8.4/`。
进程故障在 `roomflow-process.FSAmmU/`、`roomflow-process.q2RyaT/`。

过程失败均保留：双 APK Trace 单独保留后仍缺 Kotlin 类；联合 R8 最初缺 Runner 反射构造器；
checkpoint 最初的可写连接 fixture 会抢写锁；API 35 新日志 fixture 起初错误要求平台保留非空文件。
发现原 RoomFlowRegressionTest 从工作区缺失时未接受缩小后的通过结果；从本任务变更历史恢复，
修复前内容 SHA-256 与上轮 `43720d2efba1821b0f29c56c4aeaf321e8deb4eff33a73ab29ca7d90a7ecd99d` 完全相同，重跑完整测试。
新增脚本检查核心回归类实际执行；未删除、跳过原来的失败断言。

### 仍未关闭的发布门槛

- 原审计命中的 13 个构建依赖版本尚未完成兼容升级/适用性复审；本轮没有盲目 force 覆盖 AGP/Kotlin/KSP 传递依赖，也没有把 Wrapper checksum 当作漏洞已修复。
- 真实磁盘满、断电/发布阶段中断、多进程协调、后台长时负载、宿主全部历史迁移和灰度/回滚仍待验收。
- LICENSE/NOTICE、正式版本/坐标/签名和支持承诺由所有者决定；未正式发布、提交、推送或执行远程 CI。

## 历史：2026-08-28 修复前发布清单实测（未通过发布验收）

本次是验证而非业务修复：未修改 SDK、生成器或原有失败测试断言；只调整验收脚本和报告。
API 24 从“未实测”推进为“已实测且发现阻断”，不能继续使用上一阶段 API 35 通过的结果宣称完整兼容。
底层提交为 `8354d180070189d9ca97fb10a713d479c57d1c5b`，工作树包含此前未提交实现；不能仅凭该 commit 重现当前候选版本。
工作树文件摘要、临时验收配置及完整日志在 `/tmp/roomflow-release-check.NUW5Pg`（下文简称报告根目录）。

### 环境与结果

JDK 17；API 35 为已授权荣耀 BRP-AN00、Android user 0；API 24 为本次专用 ARM64 模拟器 emulator-5582 / Android 7.0。
API 24 镜像来自 Google 官方 `arm64-v8a-24_r29.zip`，与官方仓库元数据的 SHA-1 `d264adca13330b5e50665ab44726e4fecc1ddd1f` 一致。
没有以 API 28 替代最低系统，也没有将 CI 配置存在视为 CI 已运行。

| 检查 | Room 2.6.1 / KSP1 | Room 2.8.4 / KSP2 |
| --- | --- | --- |
| API 35 Debug 全组（原始状态复核） | 53 通过、3 opt-in 跳过 | 53 通过、3 opt-in 跳过 |
| API 24 Debug 全组 | 51 通过、2 失败、3 opt-in 跳过 | 51 通过、2 失败、3 opt-in 跳过 |
| API 35 旧 APK 建库 → SDK 覆盖接入/迁移/恢复 | 各 1/1 | 各 1/1 |
| JVM 测试 --rerun-tasks 实际执行 | 10/10 | 10/10 |
| 编译拒绝反例（匹配预期诊断） | 12/12 | 12/12 |
| 独立制品消费（核心/调试 × GMM/纯 POM） | 四种组合通过 | 四种组合通过 |
| 类型规则 / JVM API / 模块边界 / Release R8 构建 | 通过；135 文件、9 项规则自检 | 同左 |
| 隔离 Release 冒烟（API 24、API 35 各运行一次） | 两系统通过 | 两系统通过 |
| Release 全组仪器测试 | 两系统均在 Runner 启动崩溃，未执行数据库用例 | APK 构建通过；未继续运行已知失效的 Runner 路径 |
| API 24 事务提交前真实进程中断、重启验证 | 通过 | 通过 |
| API 24 备份复制中真实进程中断 | 被 journal 预检拒绝，未到注入点 | 未执行；同类 journal 行为已在全组复现 |

普通组的三个跳过为性能基准、进程故障入口、两 APK 专用入口。不把跳过计为通过。
两 APK 专用入口已在 API 35 单独执行，事务故障在 API 24 单独执行；未在实体手机执行 force-stop 或磁盘故障。
初次 JVM 任务取自缓存，因此另外使用 --rerun-tasks 执行两版，最终结果取 unit-*/room-flow/test-results/testDebugUnitTest。

### 新发现及发布阻断

1. **API 24 WAL 测试前置条件不兼容**：RoomFlowRegressionTest.checkpointBusyIsFailureAndSchedulerRecordsOnlySuccess
   在第 551 行只读 SQLiteDatabase 执行 BEGIN DEFERRED TRANSACTION 时抛 SQLiteReadOnlyDatabaseException，尚未调用 SDK checkpoint。
   需修正跨系统的测试构造并验证真实 busy 行为；不能通过跳过该项或把异常当 SDK 正常结果来关闭门槛。
2. **API 24 回滚日志与离线备份边界**：openingErrorDoesNotDeleteExistingDataAndOfflineBackupRestores 第 490 行预期“摘要”异常失败。
   现场保留了非空 `.bak-journal`，而 SDK checkOffline 在摘要校验前拒绝非空 journal。
   专用备份中断 fixture 进一步直接得到“存在未处理的 -journal，拒绝复制/恢复”，其 Room 连接已关闭但 source.db-journal 仍有 12824 字节。
   当前保护是保守拒绝，不是自动删库或数据丢失证据；但旧系统部分日志模式的备份可用性与验证覆盖不满足发布要求。
   需明确冷/热 journal 的安全识别和离线策略，并补齐回归；本次没有删除 journal 来强行使测试通过。
3. **Release 测试基础设施未就绪**：临时 testBuildType=release 配置排除仅调试 UI 的 AdapterBindingTest 后可以编译。
   但测试 Runner 在 onCreate 因 androidx.tracing.Trace 被裁剪而 NoClassDefFoundError，测试尚未触及数据库。
   初次源集排除未作用于 Kotlin 编译，随后改为 Kotlin 编译任务 exclude；各次失败日志保留。未向正式应用添加全量 keep 规则掩盖问题。
4. **构建工具链安全审核未关闭**：对实际解析的运行时、编译器、构建插件依赖查询 OSV。
   每版 233 个去重 Maven 坐标版本；运行时 81 个、编译器 3 个均无本次数据库命中，构建 classpath 151 个中 13 个版本命中，共 47 个去重公告 ID。
   涉及 Kotlin Gradle Plugin 2.0.21、protobuf-java 3.24.4、Netty 4.1.93.Final、commons-compress 1.21、jose4j 0.9.5、Bouncy Castle 1.79、jdom2 2.0.6。
   这是版本匹配结果，不是可利用性证明；上述命中未出现在本次 app runtime 清单中，不能说成 APK 已被利用。
   示例：[Kotlin 构建缓存公告](https://osv.dev/vulnerability/GHSA-r937-wjx7-w2jp)、
   [Protobuf 维护者公告](https://github.com/protocolbuffers/protobuf/security/advisories/GHSA-735f-pc8j-v9w8)、
   [Commons Compress 安全说明](https://commons.apache.org/proper/commons-compress/security.html)。
   下一步需审核影响条件，选择已修复且相互兼容的工具链或经审核的缓解方案，重跑 Room/KSP 边界；本次未强制覆盖传递版本。
5. **其余门槛仍未关闭**：后台冻结恢复/长时负载、真实 ENOSPC/断电/发布阶段中断、多进程、目标宿主全部历史迁移、稳定 Kotlin API/metadata/inline 审核、灰度及回滚。
   仓库未找到正式 LICENSE/NOTICE/CHANGELOG，当前 Maven 坐标仍为 verification/LOCAL；版本、授权方式及正式发布由所有者决定，不代选许可证。
   已导出缓存 POM 中的许可证元数据；缺元数据不等于没有许可证，不能替代完整授权审核。
   另未发现依赖锁/verification-metadata，Wrapper 未配置 distributionSha256Sum，供应链固定仍需补审。

### Release 冒烟的证据及限制

为避开失效的测试 Runner，在报告目录加入临时 ReleaseProbeActivity 和清单，通过原 app 的 Release/R8 管线编译；
使用测试签名、独立 verification.releaseprobea/releaseprobeb 包，保留非 debuggable，不加全量 keep，不引入运行时依赖。
QuerySpec 在两 APK 中分别被重命名为 W0.t / b1.t，确认不是关闭混淆后的验证。
检查包括实际 CRUD、UserSummary DTO、一致性分页、SUM/GROUP BY、游标、Flow 失效、事务回滚、原生查询取消后继续读取。
两版各在 API 24/35 显示 PASS；2.8.4 真机还保存 UI hierarchy 的 PASS 文本，避免依赖可能滚动丢失的 logcat。
这是一个增加了验证调用入口的隔离消费者 APK，不是原样正式 APK或全组 Release 回归，不能替代长时/宿主验证。
正式源码中没有新增该 Activity 或导出测试组件，测试签名 APK 不可作为正式发布包。

Release 冒烟 APK SHA-256：

- 2.6.1：`278fe8cc3a3e5b654d2ad607e7825e3ae1b36cdb30f02bbda87843b4b368ec9d`
- 2.8.4：`3274515ab25eedb76f5d327366316950bd3889e297bd62973a3f568716a37de6`

### 原始证据与脚本调整

报告根目录保留 sources.sha256、probe-src/probe.init.gradle、release.init.gradle、dependencies.init.gradle、镜像元数据、APK 和构建日志。

- `roomflow-adoption.fRptjn/<Room>/`：build.log、seed/adoption、regression-raw.txt、随机包名、完整构建产物。
- `api24-<Room>-debug.txt`：两个版本各自的原始失败状态；`api35-release-crash.txt` / `api24-release-crash.txt`：Runner 崩溃。
- `api35-2.6.1-probe.txt`、`api24-2.6.1-probe.txt`、`emulator-5582-2.8.4-probe.txt`、`api35-2.8.4-probe-ui.xml`：Release 冒烟。
- `roomflow-typed-negative.Gk5y5x` / negative.log：编译反例；`roomflow-consumer.O2GU1L` / consumer-isolated.log：独立消费。
- `roomflow-process.SHN78r/backup-interrupt.txt`：journal 预检失败；`roomflow-process.Q6tHPJ` / `roomflow-process.zYctEY`：事务中断通过。
- `audit-<Room>/dependencies.json`、osv-request/response.json、dependency-summary.txt、advisory-ids.txt、licenses-2.6.1.tsv：依赖审核原始数据。

仅两处验收脚本增强：check-artifact-consumer 的发布构建改为隔离输出和 project-cache（首次共享输出出现缺少 class 文件，已留 consumer.log）；
check-process-interruption 支持验证包和单故障点，使用 cat 读取标记，解决 API 24 无可执行 test 命令的脚本问题。
脚本语法和拒绝实体设备/无关包检查通过；没有修改故障断言，备份失败仍如实保留。
当前阶段原始普通组失败不被“部分冒烟通过”覆盖。

真机及模拟器保留本轮隔离测试包和报告；进程故障 fixture 按原测试逻辑清理自己的临时目录，不操作宿主数据库。
未卸载/清空原应用、未修改系统省电配置、未正式发布、提交、推送或触发远程 CI。

## 2026-08-28 查询能力扩展与商用发布门槛（本阶段完成，正式发布待验收）

本阶段完成查询能力及回归，不将“功能通过”标为“已可正式商用”。正式发布尚须逐项关闭 docs/RELEASE-CHECKLIST.md 的 P0 门槛。

- [x] 类型化 Projection/ProjectedSelect：多列 DTO、map/zip、列位置读取、错表拒绝和 mapper 不自动重试。
- [x] PageResult：同一 Room 事务读取 items/total，保留旧可变查询入口，不添加外层重试。
- [x] between、notIn、!condition、startsWith/endsWith；绑定参数、字面通配符与 NULL 语义明确。
- [x] MIN/MAX/COUNT DISTINCT、整数/浮点 SUM、AVG、单键单指标 GROUP BY 和统计 Flow；空集、整数溢出与非有限数处理。
- [x] QuerySpec.seekAfter：多列混排、NULL、完整复合主键，替换 OFFSET，保留绑定值快照及参数上限。
- [x] 独立 UserSummary 与 UserRepository 示例；Skill、API 基线、使用说明和商用门槛同步。
- [x] 两版 Room 真机功能/两 APK 历史数据接入、JVM 回归、Release 构建与类型规则。
- [x] 两版编译反例和独立制品消费最终结果复核。
- [ ] 正式发布门槛：API 24、Release 实际运行、后台长时/真实存储故障、宿主历史迁移与许可证/版本/API 审核。

不新增运行时依赖，不变更宿主 schema、写入默认策略或迁移逻辑。
本阶段不包含 JOIN/HAVING、多键分组、任意 SQL 表达式插件、游标签名或跨页数据库快照。

### 阶段验收（JDK 17 / 荣耀 BRP-AN00 / API 35 / Android user 0）：

| 检查 | Room 2.6.1 / KSP1 | Room 2.8.4 / KSP2 |
| --- | --- | --- |
| 真机全组原始状态 | 53 通过，3 项 opt-in 跳过 | 53 通过，3 项 opt-in 跳过 |
| 新增 AdvancedQueries/AggregateQueries/CursorQueries | 6/6 | 6/6 |
| 旧 APK 建库、覆盖 SDK 后接入/迁移/恢复 | 各 1/1 | 各 1/1 |
| JVM 回归（本次实际执行） | 10/10 | 10/10 |
| 编译失败反例（须匹配预期诊断） | 12/12 | 12/12 |
| 核心/调试 AAR、编译器 JAR 独立消费：GMM/纯 POM | 四种组合通过 | 四种组合通过 |
| Debug/AndroidTest、Release/R8/vital lint、JVM API、模块边界 | 通过 | 通过 |
| Kotlin PSI 类型规则 | 135 文件、9 项规则自检通过 | 同左 |

执行 scripts/check-existing-database.sh --offline 后另以 am instrument -w -r 重跑全组核对状态，
每版 53 个 status=0、3 个 status=-4、56 个 status=1，runner 汇总 OK (56 tests)。
跳过项为性能基准、模拟器进程故障和两 APK 专用入口；最后一项已单独带参数执行，不将跳过记为通过。
本次两版均无需前台恢复干预，但几秒测试通过不替代后台长时冻结恢复验收。

报告及构建输出：`/var/folders/bk/fbtvw8qj6mj_xw24gvq7qw500000gn/T/roomflow-adoption.XR0jPl`。
按版本保留 build.log、seed.txt、adoption.txt、regression.txt、regression-raw.txt、package.txt 及 APK。
APK 内 META-INF/androidx.room_room-runtime.version 分别核对为 2.6.1/2.8.4。
scripts/check-typed-compilation.sh --offline 的 24 组编译拒绝检查均通过，日志目录：
`/var/folders/bk/fbtvw8qj6mj_xw24gvq7qw500000gn/T/roomflow-typed-negative.yaIPNy`，总日志 `/tmp/roomflow-commercial-negative.log`。
scripts/check-artifact-consumer.sh --offline 在设置 ANDROID_HOME 的环境中完成八种独立消费组合，
Room 2.6.1 解析 SQLite 2.5.0，Room 2.8.4 解析 SQLite 2.6.2；新的 typed 入口由发布 AAR/JAR 实际编译。
本地暂存制品/报告目录：`/var/folders/bk/fbtvw8qj6mj_xw24gvq7qw500000gn/T/roomflow-consumer.N9L66C`，总日志 `/tmp/roomflow-commercial-consumer.log`。
公开 JVM 签名差异仅有新增（审阅文件 `/tmp/roomflow-commercial-api.diff`）；旧签名保留，不等于完整 Kotlin metadata/行为兼容性证明。

保留本轮随机测试包及各自 .test 包和合成数据，不清理：

- 2.6.1：`com.kairowan.roomflow.verification.r650b24236e5a4020b8116565f3029b76`
- 2.8.4：`com.kairowan.roomflow.verification.rfd3272fcb43c451788853a7e45a16b1f`

本轮仅操作上述随机包，没有执行卸载、pm clear、force-stop、磁盘填满或断电。
结束只读查询显示原 com.kairowan.roomflow 已不在当前设备安装列表，无法复核历史 lastUpdateTime；不推断缺失原因，也不宣称已验证原应用数据仍在。
shell 语法、git diff --check 和 Ruby YAML Skill 元数据检查通过；quick_validate.py 因本机 Python 缺 PyYAML 未完成，未为此安装依赖。
未提交、推送、触发远程 CI、使用生产签名或正式发布。

## 2026-08-28 查询复用与二次封装（已完成本阶段）

- [x] 新增无数据库引用的不可变 QuerySpec；查询定义/执行分离，保留 EntitySelect 原有可变行为与签名。
- [x] 新增可选条件、>=/<=、firstOrNull/exists/count/totalCount、定义快照及冷 Flow 观察。
- [x] 用 UserQueries 和独立 UserRepository 示范二次封装；分页示例复用定义，不引入 BaseRepository/新运行时依赖。
- [x] 复用/分页统计/旧接口/绑定值快照/Flow/事务/错结构/业务封装回归，两 Room 边界与独立制品消费。
- [x] 更新 Skill、文档、API 基线和本次实际验收证据。

该阶段仅完成可组合查询与执行入口；多列 DTO 投影已由上方后续阶段补齐，自定义 SQL 运算符插件仍未提供。
count 统计当前分页窗口，totalCount 显式忽略分页；firstOrNull/exists 保留页偏移。查询定义是参数快照，不是数据库快照。
observe 在调用时冻结条件，订阅时首次查询，随后串行重查；没有全局 Scope、自动缓存或副作用回调重试。

### 查询复用阶段实际验收

JDK 17，已授权荣耀 BRP_AN00 / API 35 / Android user 0，使用独立随机测试包。

| 检查 | Room 2.6.1 / KSP1 | Room 2.8.4 / KSP2 |
| --- | --- | --- |
| 真机全组原始状态复核 | 47 通过，3 项 opt-in 跳过 | 47 通过，3 项 opt-in 跳过 |
| 新增 QuerySpecTest | 6/6 | 6/6 |
| 旧 APK 建库、覆盖安装后 SDK 接入/迁移/恢复 | 各 1/1 | 各 1/1 |
| JVM 回归（本次实际执行） | 10/10 | 10/10 |
| 编译失败反例（新增 QuerySpec 跨实体和可选条件类型） | 8/8 | 8/8 |
| 核心/调试 AAR 与编译器 JAR 独立消费，GMM/纯 POM | 四种组合通过 | 四种组合通过 |
| Debug/AndroidTest、Release/R8/vital lint、JVM API、模块边界 | 通过 | 通过 |
| Kotlin PSI 类型规则 | 122 文件、9 项规则自检通过 | 同左 |

最终原始 instrumentation 报告均为 47 个 status=0、3 个 status=-4、50 个 status=1，汇总 `OK (50 tests)`。
跳过的是性能基准、模拟器进程故障、两 APK 专用入口；两 APK 专用入口已单独执行，不把三个跳过计为普通组通过。
新测试覆盖不可变分支/跨数据库复用/BLOB 防御性复制、分页统计和越界、可选条件/归属/参数上限、
旧构造器忽略返回值的兼容、Flow 定义快照/DAO 失效/重复订阅及取消、事务内读和回滚、错结构拒绝，以及业务 Repository。
公开 JVM 签名审阅显示仅新增入口，原签名保留；这仍不等于完整 Kotlin metadata/行为兼容性证明。

执行 `scripts/check-existing-database.sh --offline`、`scripts/check-typed-compilation.sh --offline`、
`scripts/check-artifact-consumer.sh --offline`；独立消费设置 ANDROID_HOME，APK 中实际 Room runtime 版本核对正确。
真机报告和 APK：`/var/folders/bk/fbtvw8qj6mj_xw24gvq7qw500000gn/T/roomflow-adoption.1zp53y`，按版本分目录。
构建/初次回归/最终原始回归分别保留 build.log、regression.txt、regression-raw.txt；还有 seed.txt、adoption.txt、foreground.txt 和 package.txt。
编译反例：`/var/folders/bk/fbtvw8qj6mj_xw24gvq7qw500000gn/T/roomflow-typed-negative.6JAZOL`，总日志 `/tmp/roomflow-queryspec-negative.log`。
独立消费制品：`/var/folders/bk/fbtvw8qj6mj_xw24gvq7qw500000gn/T/roomflow-consumer.mlGLx6`，总日志 `/tmp/roomflow-queryspec-consumer.log`。

真实设备注意事项：初次 2.6.1 运行曾停滞，读取测试进程主线程 wchan 为 `__refrigerator`，
对应内核冻结等待（参考 [Linux 内核冻结说明](https://docs.kernel.org/6.7/power/freezing-of-tasks.html)）；仅将独立应用切回前台后测试恢复通过。
初次 2.8.4 的旧 QueryCancellationTest 出现一次 15 秒超时，日志保留；没有提高超时或跳过它。
随后两版使用 `am instrument -w -r` 完整重跑，并在 UI 测试结束后将对应独立 MainActivity 切回前台：
2.6.1 在本次重跑中途恢复前台后通过，2.8.4 提前保持前台后全部通过（6.871 秒）。最终结果以 regression-raw.txt 为准。
这些证据支持系统冻结影响测试执行的判断，不代表已经验证后台长时可靠性。没有修改系统省电配置或执行 force-stop/磁盘填满/断电；临时调试端口转发已撤销。

保留以下测试包及各自 `.test` 包和合成数据，未做清理：

- 2.6.1：`com.kairowan.roomflow.verification.rbb7ac8a83cf54988936fc7c9eda69603`
- 2.8.4：`com.kairowan.roomflow.verification.r68c2070c2f3f4dc1b4589369d6eb135e`

原 `com.kairowan.roomflow` 未覆盖/卸载/清空，结束复核 lastUpdateTime 仍为 2026-08-28 10:43:53。
Skill 校验、shell 语法及 git diff --check 通过。未新增运行时依赖、提交、推送、正式发布或触发远程 CI。
仍未验证 API 24/其他厂商、Release 真机运行/性能、后台长时冻结恢复、真实断电/ENOSPC/多进程故障或任意宿主全部历史迁移。

## 2026-08-28 类型安全实体 CRUD（已完成本阶段）

- [x] 设计可选 KSP 映射生成：RoomFlowEntity → XxxTable，不使用运行时注解反射，不修改宿主 schema。
- [x] 实现类型化字段/条件/排序、可选分页/不分页/单列投影/逐行读取、实体插入/更新/删除和有条件部分更新/删除。
- [x] 生成器编译失败用例、真实 SQLite 数据/安全/事务回归，Room 2.6.1/KSP1 与 2.8.4/KSP2 验证。
- [x] 同步文档、Skill、公开 API 基线和实际结果；复杂映射边界明确列出。

第一版限平面标量实体；Embedded、Relation、TypeConverters、FTS、继承/泛型实体等明确拒绝，不冒充完整 Room ORM。
不新增核心运行时依赖；仅新增构建期 KSP 模块。沿用用户已授权真机的独立测试包，不操作原应用。

### 类型安全 CRUD 实际验收

设备为已授权荣耀 BRP_AN00 / API 35 / Android user 0。两版本 APK 内的 Room runtime 版本均已核对。

| 检查 | Room 2.6.1 / KSP1 | Room 2.8.4 / KSP2 |
| --- | --- | --- |
| 真机普通功能组（新增 TypedCrudTest 6 项） | 41 通过、3 项 opt-in 跳过 | 41 通过、3 项 opt-in 跳过 |
| 旧版纯 Room APK 建库/写入 | 1/1 | 1/1 |
| 不卸载覆盖安装 SDK APK 后接入/迁移/恢复 | 1/1 | 1/1 |
| JVM 回归（本次实际执行，非缓存报告） | 10/10 | 10/10 |
| 跨实体字段、错误值、忽略字段、Embedded、自定义类型、泛型编译拒绝 | 6/6 | 6/6 |
| Debug/AndroidTest、Release/R8/vital lint、核心/调试 Release AAR | 通过 | 通过 |
| JVM API 基线、模块边界 | 通过，已审阅并新增 typed 签名 | 同左 |
| Kotlin PSI 类型规则 | 117 文件、9 项规则自检通过 | 同左 |
| 独立制品消费：核心/调试 × GMM/纯 POM，含发布的 KSP JAR | 四种组合通过，SQLite 2.5.0 | 四种组合通过，SQLite 2.6.2 |

普通组原始 instrumentation 状态每版为 41 个 status=0、3 个 status=-4，runner 汇总 `OK (44 tests)`。
三个跳过分别为性能基准、模拟器专用进程故障、两 APK 专用入口；最后一项已单独带参数执行通过。
未将跳过、历史报告或只构建未执行的用例记作通过。

新增回归覆盖：ColumnInfo/可空值/BLOB/标量/Ignore、复合主键、可选分页与稳定排序、LIKE 字面转义、
NULL/空 IN、值快照及绑定上限、全表操作保护、错误字段归属、主键保护、INSERT 冲突与父子关联、
事务回滚/Room Flow 失效、逐行消费失败不重试，以及错库/结构不符拒绝且不改动数据。
KSP 编译反例必须匹配预期诊断；构建失败本身不是通过证据。

执行：JDK 17，`scripts/check-existing-database.sh --offline`、`scripts/check-typed-compilation.sh --offline`。
真机报告及 APK：`/var/folders/bk/fbtvw8qj6mj_xw24gvq7qw500000gn/T/roomflow-adoption.fzWXrU`，
按版本保留 build.log、seed.txt、adoption.txt、regression.txt、regression-raw.txt、package.txt 及隔离构建输出。
编译反例日志：`/var/folders/bk/fbtvw8qj6mj_xw24gvq7qw500000gn/T/roomflow-typed-negative.KIqHJ7`。
独立消费使用 `scripts/check-artifact-consumer.sh --offline`，显式设置 ANDROID_HOME 与 JDK 17；
暂存 Maven 制品和八组消费输出：`/var/folders/bk/fbtvw8qj6mj_xw24gvq7qw500000gn/T/roomflow-consumer.YM6RhQ`，
总日志 `/tmp/roomflow-typed-consumer.log`。编译器仅来自该暂存 JAR，各组实际执行 KSP/typed 入口编译；运行时依赖检查无 KSP/编译器/kotlin-reflect 泄漏。
首次独立消费因未设置 SDK 路径失败；补充本次命令环境变量后全部重跑通过，未修改用户或全局 SDK/代理配置。

本次独立测试包及各自 `.test` 包、合成数据保留在手机上，未清理：

- 2.6.1：`com.kairowan.roomflow.verification.r2d1b4d09b62b4c5fa3fb6d2d906e8cb4`
- 2.8.4：`com.kairowan.roomflow.verification.r77c2d11462ee4cd0a61197680ce5034f`

原 `com.kairowan.roomflow` 未覆盖/清空/卸载，结束复核 lastUpdateTime 仍为 2026-08-28 10:43:53。
Skill、README、typed 使用说明和发布清单已同步；单类型规则覆盖编译器与独立消费工程。
仍未验证 API 24/其他厂商、Release 真机运行/长时性能、真实断电/磁盘满/多进程故障及任意宿主历史迁移。
不支持复杂实体转换、Room 3/SQLiteDriver；schema 预检不是完整 Room 校验，更不是数据完整性保证。
未提交、推送、触发远程 CI 或正式发布；详细 API 用法与限制见 `docs/TYPED-CRUD.md`。

## 2026-08-28 已有数据库接入与真机验收（已完成本阶段）

- [x] 独立旧版 Room APK，无 SDK 依赖；测试端共用冻结 fixture，不使用当前业务 SQL 生成旧数据。
- [x] 新增同 schema 接入、DAO/SDK 共用、真实文件数据/关联/默认值/主键、失败回滚、离线备份恢复及历史路径回归。
- [x] 统一示例静态 DAO/动态 UserQueries，修正 REPLACE 父行级联风险，增加 SQL 绑定/通配符/分页上限检查。
- [x] 补充 Skill、接入说明和独立随机 applicationId 的真机脚本。
- [x] Room 2.6.1/2.8.4 构建、旧 APK → SDK APK 真机覆盖安装及普通功能回归。

授权范围为已连接真机上的隔离测试包；不覆盖/卸载/清空原 com.kairowan.roomflow，不执行真机断电、磁盘填满或进程故障注入。
新增 fixture 仅覆盖合成用户/关联数据，不代表任意宿主所有历史数据迁移已获得完整性保证。

### 本阶段最终验证

设备：用户明确授权的荣耀 BRP-AN00（设备报告 BRP_AN00），Android API 35，Android user 0。
旧 APK 固定为 Room 2.6.1，无 SDK 依赖；目标 APK 分别使用 Room 2.6.1/KSP1 和 2.8.4/KSP2。
除了 Gradle 依赖检查，还核对了三个类型 APK 内的 META-INF/androidx.room_room-runtime.version。

| 检查 | 目标 Room 2.6.1 | 目标 Room 2.8.4 |
| --- | --- | --- |
| 旧版纯 Room APK 建库/写入 | 1/1 | 1/1 |
| 不卸载覆盖安装 SDK APK 后接入/迁移/恢复 | 1/1 | 1/1 |
| 真机普通功能回归 | 35 通过，3 项 opt-in 跳过 | 35 通过，3 项 opt-in 跳过 |
| JVM 回归（--rerun-tasks，非缓存复用） | 10/10 | 10/10 |
| Debug/AndroidTest、Release/R8/vital lint、两模块 Release AAR | 通过 | 通过 |
| JVM API 基线、模块边界 | 通过，无核心 API 变更 | 通过，无核心 API 变更 |
| Kotlin PSI 类型规则 | 92 个文件、9 项规则自检通过 | 同左 |

普通组先执行一次，之后以原始 instrumentation 状态再执行一次核对计数：两版均为 35 个 status=0、3 个 status=-4，runner 汇总 `OK (38 tests)`。
跳过项为两 APK 专用入口、性能基准、模拟器专用进程故障入口；两 APK 专用入口已另行带参数执行，不能将三个跳过算作普通组通过。
新增普通回归是同 schema 接入、历史迁移/备份恢复、SQL 查询工厂各 1 项；两 APK 接入单列 1 项。

数据断言覆盖：主键和每列旧值、空串/Unicode/引号及 SQL 注入样式字符串、超过 2^53 的 Long 与 Long.MAX_VALUE、
新增 nullable 列、默认值、父子关联、索引、自动生成主键、schema/identity/version。
缺失 Migration 和 DDL+DML 后注入异常均保留旧文件数据及结构；验证 1→2→3、恢复回 v1 后重开、关闭 v2 后再升 v3。
修复示例 UserDao 的 REPLACE → @Upsert，真机验证更新父记录不级联删除子记录；SQL 工厂验证绑定、%、_、反斜杠转义及分页上下限。
生成的历史 schema 已留在 app/schemas 和 verification/legacy-fixture/schemas。

执行入口：`scripts/check-existing-database.sh --offline`，JDK 17；前置补齐了一项旧版测试传递依赖缓存，未降级 TLS 或修改全局配置。
首次编译发现 Room 2.6 RoomDatabase 不是 Closeable，已改为明确关闭作用域后重跑通过。
最终完整报告及 APK：`/var/folders/bk/fbtvw8qj6mj_xw24gvq7qw500000gn/T/roomflow-adoption.ZZjYov`，按版本分目录：
`build.log`、`seed.txt`、`adoption.txt`、`regression.txt`、`regression-raw.txt`、`unit-fresh.log` 与 `package.txt`。
初次全组构建的 JVM 任务使用缓存，因此随后对两个版本分别使用 --rerun-tasks 实际执行 10 项，不以旧报告冒充本轮运行。

手机上保留以下独立测试包及各自的 `.test` 包、合成历史库/备份/恢复点，便于复核：

- 2.6.1：`com.kairowan.roomflow.verification.rfffcf3e120414e80b44b8f7af14732d1`
- 2.8.4：`com.kairowan.roomflow.verification.r67f420da41b443f091705d5e044a26d4`

原 `com.kairowan.roomflow` 未被覆盖、清空或卸载；操作前后其 lastUpdateTime 都为 2026-08-28 10:43:53。
Skill quick_validate、shell 语法、schema 对照与 git diff --check 通过。未新增 SDK 运行时依赖、修改公共核心 API、提交、推送或发布。

仍未验证：API 24 真机、其他厂商/系统、真实断电/ENOSPC、多进程故障、Release 真机压力及本轮性能基准、任意宿主全部历史版本/复杂字段/转换器。
本阶段未重跑独立 AAR 消费工程或触发远程 CI；没有把上一阶段的结果计入本阶段。迁移完整性承诺仍限于被验证的 schema、数据样本及故障条件。

## 实施与验收

- [x] 1. 构建基线与下载故障
  - 对齐 Kotlin/KSP、compileSdk；示例依赖本地模块；Room 版本可通过 Gradle 属性切换。
  - 诊断 Maven Central TLS 握手：对照直连/代理，保持 HTTPS 和证书校验，不写死个人代理。
  - 验收：Gradle 配置、库和示例构建；记录网络阻塞与源码错误的区别。
- [x] 2. 数据安全与协程事务（实现完成，回归结果见下方）
  - 禁止自动删库“修复”；用 Room withTransaction；busy 重试识别异常类型、保留取消语义。
  - 写队列：提交后完成结果、异常回滚、有限容量、关闭后拒绝提交并终结未完成任务。
  - 备份/恢复仅允许明确停止访问的关闭数据库，拒绝未处理的 WAL；原子替换，不覆盖唯一备份。
  - 验收：事务挂起/回滚、任务失败/重试/取消/关闭、备份拒绝不安全输入。
- [x] 3. 查询、分页与 SQL 边界（实现完成，回归结果见下方）
  - 原生写入经过 Room 事务触发失效；Flow 串行查询；分页以 offset 为 key。
  - 标识符转义、全表更新显式确认、Cursor Iterator 契约、取消传播。
  - 验收：原生更新触发 Flow、连续更新收敛最新值、首次 3 页后追加不重复、列名及迭代边界。
- [x] 4. 迁移、维护、加密、路由与监控（实现完成，回归结果见下方）
  - 解析真实 schema 的对象根和 columnName；保留建表/索引 SQL；不自动猜测危险迁移。
  - checkpoint 检查 busy，使用单调时钟与 WAL 文件估算避免轮询隐式 checkpoint。
  - rekey 校验 SQLCipher/输入并传播失败；日志与指标接入；未知用户路由不回落共享库。
  - 验收：真实 schema、NOT NULL 无默认值拒绝、checkpoint busy、错误传播、指标记录。
- [x] 5. 示例与文档
  - 移除破坏性迁移默认设置、主线程磁盘操作、无 Provider 的伪跨进程演示。
  - 修复 View 生命周期、重复订阅、分页刷新竞态；对齐 README 实际 API 和能力边界。
  - 验收：示例构建，调试页面和主要操作冒烟，文档代码与 API 一致。
- [x] 6. 项目 skill 与验证记录
  - 建立仓库内 skill、AGENTS.md 入口和 .editorconfig：风格、线程/事务/资源约束、测试命令、版本承诺。
  - 每类非平凡修复保留最小可运行回归检查，优先既有 JUnit/Android 测试，不引入 mock 框架。
  - 执行 Room 两个边界的构建/测试，按实际结果记录通过、未执行与已知限制。

## 2026-08-28 发布安全与实用扩展（本轮）

- [x] 1. 日志默认关闭；自定义日志默认不接收原始异常，敏感异常详情显式 opt-in，保留回归。
- [x] 2. 检查 API 24 可运行环境；补进程中断/迁移路径回归，无法实测的硬件/故障条件单列（本机未安装 API 24 镜像，本次仍不宣称实测）。
- [x] 3. 建立 JVM 签名基线与调试 AAR 独立消费检查，统一验证摘要。
- [x] 4. 新增队列排空关闭，保留原取消关闭；定义暂存任务、并发提交、等待取消契约。
- [x] 5. 增加逐行导出示例与可选查询诊断，不缓存全表、不自动建索引。
- [x] 6. 备份复制进度、空间估算与安全取消；同步 Skill/README，运行两 Room 边界回归。

不扩展持久化任务、离线同步、Room 3、KMP、SQLCipher；不提交或发布。

### 本轮验证与剩余门槛

| 检查 | Room 2.6.1 / KSP1 | Room 2.8.4 / KSP2 |
| --- | --- | --- |
| JVM 回归 | 10/10 | 10/10 |
| API 35 普通功能回归 | 32 通过，2 项 opt-in 跳过 | 32 通过，2 项 opt-in 跳过 |
| 备份发布前 / 事务提交前真实 force-stop 后恢复 | 两个故障点通过 | 两个故障点通过 |
| 两模块 Release AAR、示例 Debug/AndroidTest、Release/R8/vital lint | 通过 | 通过 |
| 独立消费核心/调试 AAR × GMM/纯 POM | 四种组合通过，SQLite 2.5.0 | 四种组合通过，SQLite 2.6.2 |
| JVM 签名基线 / 类型规则 / 模块边界 | 通过 | 通过 |

普通设备组返回 `OK (34 tests)`，其中 PerformanceBaselineTest 与 ProcessInterruptionTest 各一项默认 assumption 跳过，不能算作 34 项功能全部执行。
本轮未重跑 opt-in 性能基准或 Release APK 运行冒烟，不用上一阶段结果冒充本轮执行。
新增 JVM 检查是异常详情 opt-in、复制进度/发布前失败保全；新增设备功能检查是迁移历史 1 项、排空 2 项、导出/诊断 1 项、备份进度/取消 1 项。
进程终止通过 `scripts/check-process-interruption.sh` 单独执行：在主文件复制回调（发布前）和未提交事务内等待外部 force-stop，再重新打开确认原数据。
这不是断电、真实 ENOSPC、发布后中断或多进程写入协调测试；这些仍未通过验收。

制品检查发现并修复调试模块公开 Activity/Adapter 父类型缺失的编译依赖：AppCompat、RecyclerView 从 implementation 改为 api。
独立消费者只依赖待测制品，不手动补这些依赖；同时编译 Activity、Fragment、Adapter 类型关系。
JVM 基线覆盖两模块 public/protected 描述符、泛型签名、常量（保守包含 internal/生成的 public 类型）；验证了不匹配会失败。
它不覆盖 Kotlin metadata、inline 方法体、Android 资源或行为兼容，不代表已选定正式稳定版本。

构建沿用 JDK 17、--no-daemon、--max-workers=2、Kotlin in-process，以及 `/tmp/roomflow-verification.NFWBsR/verify.gradle` 和隔离项目缓存。
本轮本地产物位于该目录的 `iteration-repository`，消费者日志为 `consumer-<room>-<pomOnly>-<withDebug>.log`。
首次 2.8.4 离线验建因新增公开依赖路径缺少 RecyclerView AAR 缓存失败，随后保留 HTTPS/证书校验联网补齐并通过；没有修改个人网络配置。
设备仅使用本次启动的 API 35 emulator-5590。两个进程故障报告目录分别为：
`/var/folders/bk/fbtvw8qj6mj_xw24gvq7qw500000gn/T/roomflow-process.JGqmUP`（2.6.1）和
`/var/folders/bk/fbtvw8qj6mj_xw24gvq7qw500000gn/T/roomflow-process.5ebOvm`（2.8.4）。
Kotlin PSI 检查 82 个源文件与 9 项自检通过；Skill 校验、shell 语法、Workflow YAML 和 git diff --check 通过。

仍未完成：API 24 实跑（本机无该镜像）、真机 Release 长时间运行、真实磁盘满/断电、发布后故障、多进程协调、业务全部历史迁移与正式 API/签名/发布审核。
未推送或触发远程 CI，未操作实体手机，未修改 `.idea/misc.xml` 或 `local.properties` 的用户改动。

## 2026-08-28 后续优化（依次执行）

- [x] 1. 查询取消：原生 List/Flow/分页共享 CancellationSignal，Cursor 在取消作用域内关闭；验证长 SQL、映射失败、取消后继续写入。分页取消同时失效并释放观察者。
- [x] 2. 指标口径：区分顶层包装操作、尝试/重试、队列结果；同库包装上下文去重，失败/取消与提交竞争不冒充物理提交统计。外部 Room 事务跨 dispatcher 的识别限制见 README。
- [x] 3. 性能基准：可重复的 1 万/10 万数据查询、深分页、队列与 Flow 测量，记录设备/版本/样本与局限，见 docs/PERFORMANCE.md。
- [x] 4. 按实测优化：示例顺序翻页改为 id keyset；通用 OFFSET/COUNT 契约保留，不新增自动全局缓存。
- [x] 5. 备份故障：复制/sync/rename 失败及不完整文件集拒绝恢复，原库、备份与恢复点保全；不把模拟异常当真实断电测试。
- [x] 6. 发布准备：本地产物消费、API 变更说明/Release 检查、两版本回归及文档。全量 API baseline、API 24/远程 CI/正式签名发布仍是独立发布门槛，未冒充完成。

### 本阶段最终验证（2026-08-28）

| 检查 | Room 2.6.1 / KSP1 | Room 2.8.4 / KSP2 |
| --- | --- | --- |
| JVM 回归 | 8/8 | 8/8 |
| API 35 功能回归 | 27 通过，性能基准 1 项默认跳过 | 27 通过，性能基准 1 项默认跳过 |
| 显式性能基准 | 1/1，1 万/10 万行 | 1/1，1 万/10 万行 |
| 核心/调试 Release AAR，示例 Debug/AndroidTest APK | 通过 | 通过 |
| 示例 Release / R8 / vital lint | 通过 | 通过 |
| Release 测试签名副本启动、批量写入、首页分页 | 通过 | 通过 |
| 独立消费本地 AAR（GMM / 纯 POM） | 通过，SQLite 2.5.0 | 通过，SQLite 2.6.2 |
| Kotlin PSI / 模块边界 | 72 个文件、9 项自检通过 | 72 个文件、9 项自检通过 |

设备全组输出 `OK (28 tests)`，其中 1 项为 opt-in 基准的 assumption 跳过，不能算作 28 项功能全部执行。
新增功能回归为 QueryCancellationTest 2 项、TransactionMetricsTest 2 项、UserPagingTest 1 项、MigrationUpgradeTest 1 项；
缺清单恢复在原备份回归中扩展。JVM 新增 AtomicCopyTest 2 项，模拟部分复制异常、sync 和 rename 失败。
取消回归从 Main 发起长 SQL/分页，检查取消后连接可继续读写、映射失败关闭 Cursor、分页取消失效；清理在不可取消的后台上下文中完成。

最终两版本均重新运行：checkKotlinTypes、checkModuleBoundaries、:room-flow:testDebugUnitTest、核心/调试 assembleRelease、
:app:assembleRelease、:app:assembleDebug、:app:assembleDebugAndroidTest，以及 :room-flow:publishVerificationPublicationToVerificationRepository。
继续使用 JDK 17、--no-daemon、--max-workers=2、Kotlin in-process 与先前隔离 init script / project cache；本阶段缓存齐全，最终构建使用 --offline。
设备仅为本次启动的 API 35 emulator-5590，未操作实体手机；每次测试均安装对应版本的 APK。
Release 使用个人环境已有的 Android **测试签名**生成独立副本，已核对 APK 内 Room 版本；不修改项目正式签名配置。

本地产物脚本完整跑通；最后的清理补充后又分别发布到 `/tmp/roomflow-verification.NFWBsR/final-repository`，
独立消费工程对两种元数据各重新构建验证。未写 Maven Local，未连接远端发布仓库。
源码、测试和示例保持一文件一具名类型；未新增运行时依赖，新增 Gradle 发布能力为内置 maven-publish。
Skill quick_validate、Workflow YAML 解析、shell 语法及 git diff --check 均通过。

性能结果见 `docs/PERFORMANCE.md`：只据实优化示例顺序分页，未新增全局 Flow 缓存或自动业务索引。
`docs/RELEASE-CHECKLIST.md` 列明未完成的正式发布门槛：API 24 实跑、真机 Release 压力/内存、真实磁盘满/kill/断电、
全量 API 签名基线与生产签名/发布流程。Room 3、SQLiteDriver、KMP、SQLCipher 完整支持仍不在承诺内。
CI 已增加本地产物消费检查，但本次未推送、未触发远程 CI。

## 2026-08-27 可靠性增强（按复核优先级执行）

- [x] 1. 队列与关闭：拒绝同队列重入/当前事务线程入队，区分拒绝原因，限制批次，增加 closeAndJoin/stopAndJoin；路由可注销但不隐式关库。验收：重入跨 dispatcher、关闭退出、批次拒绝和路由回收。
- [x] 2. 恢复安全：备份身份/版本/Room identity_hash/摘要校验，恢复预检与覆盖前恢复点；schema 差异报告额外表和检查盲区。验收：错账号、错版本、被改写备份拒绝且原库不变。
- [x] 3. 分页与自动检查：参数化 COUNT、PagingConfig、删除后刷新回退；兼容/Release 构建 CI 与 Kotlin 类型规则检查。验收：刷新/前插/空库/过滤计数，规则自检。
- [x] 4. 指标与调试：按数据库实例隔离指标，队列积压/等待/失败/慢事务；调试 UI 独立可选模块。验收：指标隔离、失败计数、核心不依赖 UI、示例与 R8 构建。
- [x] 5. 最终回归：Room 2.6.1/2.8.4 构建及 API 35 模拟器测试，更新 README/Skill，未执行项见下方。

不扩展 Room 3、SQLCipher 密钥管理、持久化任务或跨库同步。现有未提交改动保留；本节结果不能用历史测试结果替代。

### 本阶段最终验证（2026-08-27）

| 检查 | Room 2.6.1 / KSP1 | Room 2.8.4 / KSP2 |
| --- | --- | --- |
| JVM 回归 | 6/6 | 6/6 |
| API 35 设备回归 | 21/21 | 21/21 |
| 核心/可选调试 Release AAR | 通过 | 通过 |
| 示例 Debug / AndroidTest APK | 通过 | 通过 |
| 示例 Release / R8 / vital lint 构建 | 通过 | 通过 |
| Kotlin PSI 类型规则 / 模块依赖边界 | 通过 | 通过 |

设备回归为 19 项数据库测试和 2 项 UI 测试，覆盖调试 Activity 启动、ViewHolder 复用、重入跨 dispatcher、关闭退出、指标隔离、
备份身份/版本/摘要拒绝及恢复点、额外表检测、删除后分页刷新/前插/无计数回退/空表/错误计数检查。
部分场景在原测试内扩展，而非每个断言单独新增测试。Kotlin 规则扫描 59 个手写 Kotlin 文件，9 个规则自检通过。

实际运行同下列任务，2.8.4 追加 `-ProomVersion=2.8.4`；输出继续放在此前隔离的 `/tmp/roomflow-verification.NFWBsR`，未修改个人 Gradle 初始化脚本：

```sh
./gradlew checkKotlinTypes checkModuleBoundaries :room-flow:testDebugUnitTest \
  :room-flow:assembleRelease :room-flow-debug:assembleRelease \
  :app:assembleRelease :app:assembleDebug :app:assembleDebugAndroidTest \
  --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process
```

2.6.1 的任务分批完成；2.8.4 全组任务一次通过。分别安装对应 APK 后直接执行 instrumentation，最终均为 `OK (21 tests)`。
仅使用本次启动的 emulator-5590，未使用实体手机。Release 合并 manifest 不包含调试 Activity，核心 AAR 不含 UI 布局。
`git diff --check`、shell 语法、Workflow YAML 解析和 Skill quick_validate 均通过。

本阶段发现并修复的验证问题：Android 对 WAL 库的只读校验可能留下 shm，导致第二次预检失败；现改为只打开临时副本，原文件不受校验副作用影响。
模块拆分后显式补入调试模块使用的 lifecycle-runtime-ktx（沿用已有 2.6.2）；核心不再依赖传递 UI 依赖。

### 仍需区分的边界

- GitHub Actions 已配置 Room 两边界 × API 24/35，YAML 已校验，但未推送或触发远程 CI；API 24 仍未本机实测。
- Release 验建/R8/vital lint 通过，不等于已完成 Release APK 的真机运行、签名发布或所有 API 的混淆覆盖。
- 队列仍为内存队列；单项字节数、独立 Scope/多个队列依赖环、不合作的阻塞 SQL/回调无法由这些接口强制解决。
- 备份仅支持可信普通 Room 离线文件；清单摘要不是签名，identity_hash 不是实际 schema 完整校验。调用方必须停止所有进程访问，并提供可信目标身份；缺清单旧备份需人工迁移。
- 原子文件替换不提供断电后的完整持久性保证；本轮未做磁盘满、断电及真实多进程/SQLCipher 测试。
- 分页保留 COUNT + 页面事务快照与 OFFSET；深分页性能未做压力基准，不冒充无锁读取或 keyset 分页。
- 指标仅覆盖已接入的包装操作；busy/checkpoint/SQL 类型仍是进程级，嵌套包装事务不等于不同底层物理提交。
- BackupIdentity 必填、调试模块独立及分页/队列签名变动需要调用方按 README 迁移并重新编译。

## 后续独立阶段：Room 3（不在上述修复范围）

迁移至 androidx.room3，改用 driver/connection 事务与 InvalidationTracker.createFlow；决定是否发布独立主版本。
执行相同数据安全回归，再决定是否删除 Room 2 API。此阶段未实施，不计入本轮兼容承诺。

## 本轮追加：按项目 Skill 拆分类型

- [x] 审计手写源码及全部引用：11 个文件存在类型混放、嵌套或文件名不一致。
- [x] 拆分 WriteQueue/RetryPolicy/BusyRetry、Trace/Logger、路由类型、迁移数据类型、Cursor 包装类及 Adapter/ViewHolder/控件模型；对齐 WalCheckpointScheduler 文件名。
- [x] 更新示例、测试、公开类型限定名迁移说明；保留匿名回调、companion object 和原有顶层扩展函数入口。
- [x] 复核“一文件一类型”、无具名类/接口嵌套；重新执行 Room 两个版本的构建及既有回归。

本轮仅调整组织和相关风格，不更换依赖、不改变 SQL/事务/迁移语义。数据模型例外不适用于 MigrationAssistant 或 Adapter 这类业务实现。
2026-08-27 本轮重新验证结果：

| 检查 | 结果 |
| --- | --- |
| 手写源码类型声明及文件名 | 44 个具名类型分别位于 44 个同名文件，无具名类/接口嵌套 |
| 旧嵌套限定名在源码中的引用 | 0；README 仅保留迁移对照 |
| Room 2.6.1 / KSP1 | JVM 5/5，库及两个 APK 构建通过，API 35 设备测试 15/15 |
| Room 2.8.4 / KSP2 | JVM 5/5，库及两个 APK 构建通过，API 35 设备测试 15/15 |
| git diff --check | 通过 |

构建沿用下方同一组 Gradle 任务和临时输出目录；两次分别安装对应版本 APK 并运行 instrumentation，均返回 `OK (15 tests)`。
设备测试保留原有 14 项数据库回归，新增 `AdapterBindingTest.extractedViewHoldersBindAndClearRecycledState`，
验证四个拆分后的 ViewHolder、文本绑定、按钮点击，以及右侧按钮复用时隐藏/解绑/重新启用。不操作示例数据库。
测试只使用本次启动的 API 35 模拟器，完成后关闭；未使用实体手机。

公开嵌套类型拆分是源码/二进制兼容性变化，调用方需按 README 对照更新 import 并重新编译。
本轮没有扩展 Room/Android 支持范围；下方 API 24、Room 3、SQLCipher 等未覆盖项继续有效。

## 此前安全修复验证记录

验收日期：2026-08-27。JDK 17 / Gradle 8.11.1 / AGP 8.9.2 / Kotlin 2.0.21 / KSP 2.0.21-1.0.28。
设备为本次独立创建的 Android API 35 arm64 模拟器；未操作实体手机。

| Room | 示例处理器 | JVM 回归 | 库/示例/测试 APK 构建 | 设备回归 |
| --- | --- | --- | --- | --- |
| 2.6.1（项目最低维护基线，不是 Room 历史最早版本） | KSP1 | 5/5 | 通过 | 14/14 |
| 2.8.4（Room 2 最新稳定版） | KSP2 | 5/5 | 通过 | 14/14 |

构建分别执行以下命令，第二次追加 `-ProomVersion=2.8.4`：

```sh
./gradlew :room-flow:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest \
    --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process
```

本机还以临时 init script 与 `--project-cache-dir` 把验证产物放在 `/tmp/roomflow-verification.NFWBsR`，
用于避开个人 HDD 构建目录和内存压力；未更改用户级 Gradle 初始化脚本。
每个版本分别安装对应的两个 APK 后执行：

```sh
adb -s emulator-5590 shell am instrument -w com.kairowan.roomflow.test/androidx.test.runner.AndroidJUnitRunner
```

两个版本最终均返回 `OK (14 tests)`，覆盖事务回滚、busy 重试、队列失败/容量/关闭/取消、原生 SQL 失效、
Flow 串行查询、分页首次加载/追加/计数、Cursor 迭代、真实 schema、非破坏性打开、离线备份恢复、
损坏文件保全、checkpoint busy/调度、加密和路由错误输入。
示例在 Room 2.8.4 上完成启动、批量写入、Partial Update、打开调试面板冒烟；显示事务指标和脱敏 SQL 类型。

### 排障记录

- Java 17 对 Maven Central 的直连/本机代理各 3 次请求均返回 200；历史故障是间歇性握手中断，不能断言服务端不支持 TLS 1.2/1.3。
- 项目配置 Maven Central 绕过显式 HTTP 代理，对齐 KSP 2.0.21-1.0.28；不修改全局代理、不降低 TLS 校验。
- 一次构建遇到 daemon 消失；随后使用 `--no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process` 继续验证。
- skill 通过 `uv run --with pyyaml python .../skill-creator/scripts/quick_validate.py .agents/skills/room-flow-development`；校验依赖仅在工具隔离环境中，不是项目依赖。
- 首次 API 35 / Room 2.6.1 设备回归为 12/13：保留数据测试发现 SQLite Framework 2.4.0 的 onOpen 异常会误删库。已添加 >=2.5.0 依赖约束；回归不得省略。上游修复：[b/348458416](https://android.googlesource.com/platform/frameworks/support/+/71ff1935701be21d57acdc0a84a996bfa4d31c99)。
- 首次 Room 2.8.4 构建在 KSP1 schema 导出中遇到 `GeneratedSerializer.typeParametersSerializers` AbstractMethodError；Room 2.6.1/KSP2 又存在 `unexpected jvm signature V`。示例按 roomVersion 自动为 2.6 选择 KSP1、2.7+ 选择 KSP2。
- 损坏文件验证使用不删文件的错误处理器，新增损坏备份保全回归。API 35 拒绝 app 数据目录 hard link，因此使用进程内串行校验/rename，多进程访问仍要求调用方统一协调。
- `:app:dependencyInsight --dependency sqlite-framework --configuration debugRuntimeClasspath` 确认基线实际使用 SQLite Framework 2.5.0。
- `git diff --check`、`bash -n scripts/check-room-compatibility.sh` 通过。兼容脚本用于后续复验；本次设备测试直接调用 instrumentation，未运行 Gradle connected 任务。

### 未覆盖与明确限制

- API 24 仅为配置的最低 Android 版本，本轮未在 API 24 设备上运行；中间 Room 版本未逐个实测。
- Room 3 / SQLiteDriver / KMP 不支持；没有验证真实 SQLCipher 密钥轮换、独立进程 ContentProvider 通知、release 混淆及所有 UI 生命周期组合。
- schema 比较不是完整迁移验证；真实应用升级仍需自己的 Migration 测试。离线备份要求调用方统一停写、关库并协调所有进程，不能当在线快照使用。
- 默认构建使用 Room 2.6.1；2.8.4 已通过相同回归，不意味着所有扩展工厂/数据库结构均兼容。
- 官方版本依据：[Room 2 发布说明](https://developer.android.com/jetpack/androidx/releases/room)、[Room 3 发布说明](https://developer.android.com/jetpack/androidx/releases/room3)。
