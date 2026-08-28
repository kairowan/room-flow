# 发布前验收

当前新增 `0.2.0-rc.1` 预发布流程与 JitPack 依赖配置；不签生产 APK、不发布应用商店，不将预发布标记为稳定商用完成。制品发布流程见 [PUBLISHING](PUBLISHING.md)。

2026-08-28 按所有者要求删除单元/设备测试目录与对应脚本。下表中的设备通过记录是删除前的历史证据；当前仓库仅保留构建类检查，不能自动重跑这些运行时用例。正式发布仍须在宿主或独立验证环境完成对应验收，不因删除测试而降低门槛。

## 商用发布门槛（按顺序验收）

以下是发布条件，不是已全部完成的声明。每次候选发布将对应提交、制品摘要、环境、原始报告和未通过项记录到 PLAN；报告必须对应准备发布的同一份代码。

| 顺序 | 门槛 | 最小验收要求 | 当前状态 |
| --- | --- | --- | --- |
| P0-1 | 数据安全与支持范围 | 明确仅 Room 2/OpenHelper；默认不删库；旧 APK 覆盖接入、迁移失败回滚、备份恢复数据断言通过 | 两系统合成数据/冷 journal 回归通过；两 APK 本轮未重跑，宿主历史路径仍须补验 |
| P0-2 | 发布运行矩阵 | Room 两边界 × API 24/35；测试签名的 R8 Release 真正安装后验证 CRUD、DTO、统计、Flow、取消；不只 assemble | 两边界 × 两系统 Debug 与联合 R8 回归通过，Runner 已修复；不替代正式宿主验证 |
| P0-3 | 真实生命周期与故障 | 后台冻结/恢复、进程重建、并发读写；专用设备验证低空间/发布中断。明确已接收但未提交的内存队列任务不承诺持久化 | API 24 两 Room 备份/事务进程中断通过；后台长时/真实磁盘故障未完成 |
| P0-4 | 稳定 API 与依赖 | 确定首个稳定基准，审阅 Kotlin 源码/metadata/inline 兼容性、POM/GMM；审核运行时/构建依赖漏洞和许可证 | 构建工具链命中 OSV 公告待评估；完整 API/许可证审核未完成 |
| P0-5 | 所有者发布决策 | 确定版本、坐标、LICENSE/NOTICE、支持渠道与维护范围；保护签名和发布凭据 | 按用户要求配置预发布 tag/坐标；LICENSE/NOTICE、支持承诺和生产签名仍待确认 |
| P1 | 灰度与回滚 | 在受控宿主小范围接入，观察错误率/耗时/内存；可停用 SDK 功能且不删库，升级后的 schema 不盲目降级旧 APK | 尚未执行 |

P0 未关闭前不标记“正式商用可用”。没有能力覆盖的特性可以明确排除出支持范围，但核心数据安全问题不能用免责声明替代修复。
实际磁盘满/断电故障只在专用环境执行；不得为完成验收影响个人手机或原有应用数据。
数据库结构升级后的回滚必须有匹配 schema 的方案和离线恢复点，不能把应用版本回退等同于数据库可安全降级。

## 可重复检查

本仓库已移除 `releaseVerification` 专用入口、测试签名和 Runner；普通 Release 保持 R8 且禁止包含测试依赖。以下构建命令可运行，设备行为要求需在外部验证环境安排，不能用空测试任务代替。

1. `bash scripts/check-room-compatibility.sh --no-daemon --max-workers=2`：Room 两边界、AAR/APK/R8、类型/API 与模块边界；不执行单元或设备测试。
2. `bash scripts/check-artifact-consumer.sh --no-daemon --max-workers=2`：本地 Maven 目录中生成核心/调试 AAR、sources、编译器 JAR、POM、module。
   独立 `verification/consumer` 工程编译公开入口，分别消费核心/调试模块的 GMM 和纯 POM，同时使用发布的编译器生成实体字段并编译 typed CRUD；检查 Room 版本、SQLite Framework ≥2.5、核心无 UI/KSP/反射依赖、调试模块能正确传递 UI 依赖。
   日志给出临时目录；不写 ~/.m2、不配置远端仓库。可指定 ROOM_VERSION 单独检查。
   发布阶段使用独立 build 目录和 project-cache，不与 IDE 或其他验证共享类文件输出。
3. Release APK 使用**测试签名副本**安装到专用模拟器，确认启动、写入、分页，以及调试页不会进入 Release。
4. 在宿主验证真实历史文件升级、默认值/索引/外键级联，以及迁移异常后版本、结构和旧数据回滚；示例历史记录不代表目标应用全部历史版本均安全。
5. 在独立环境验证备份部分写入异常、sync 失败、rename 失败、原件保全及临时文件清理，以及缺清单/错身份/摘要/恢复点。
   IOException 注入与手工构造中断状态不等于真实 ENOSPC、进程 kill、断电或多进程试验。
6. `./gradlew checkJvmApi`：JDK 17 javap 比较两个 AAR 的 JVM public/protected 描述符、泛型签名和常量；保守包含 internal/生成类。
   它不检查 Kotlin metadata、inline 方法体、Android 资源 API 或行为兼容。基线更新须审阅，不自动接受差异。
7. 在专用模拟器的独立包中验证备份复制中、事务提交前的进程中断，再重启检查原数据并保留报告；不得操作个人手机或无关包。
   这些检查不覆盖发布 rename 后的断电、真实磁盘满或多个写进程协调。
8. `bash scripts/check-typed-compilation.sh --no-daemon --max-workers=2`：两 Room/KSP 边界检查非法跨实体字段、错误赋值类型、忽略字段及不支持映射的编译拒绝；必须匹配预期诊断，不能把依赖下载失败当通过。
   在宿主真实 SQLite 中另验字段映射、可选分页、增删改查、全表操作保护、父子记录、事务回滚、Flow 失效和错库拒绝。复杂映射支持范围见 `docs/TYPED-CRUD.md`。
9. 在宿主验证 DTO 列映射、mapper 不重试、items/total 同快照、统计空集/溢出、Flow 及 NULL/混排/复合主键游标；保留的编译反例检查投影实体/DTO 参数/数值聚合/游标类型。
   查询定义不是权限或跨页快照；统计和分组遵循输入窗口。业务应针对实际大表和索引补充有界性能验收，不能由小样本正确性测试推断 SLA。

## API 审核

- 本轮新增 `rawQueryList` 和显式 CancellationSignal 的 rawQuery 重载；原同步入口保留。
- 后续新增 drainAndJoin、rawQueryEach、QueryObservation、备份 signal/progress 重载和空间估算；原备份签名保留。
- 新增可选 `typed` API 和 KSP 编译器；已有 DAO、UserQueries 和字符串 SQL 入口保留。KSP 只生成实体元数据，不替代 Room schema/迁移校验，不承诺支持全部 Room 实体结构。
- 后续新增不可变 QuerySpec、select(spec)、结果统计与 observe；旧 EntitySelect 可变行为保持。分支隔离、窗口统计、Flow 快照/取消、事务和业务封装的历史测试证据见 PLAN，保留的编译反例及制品消费包含新入口。
- 新增 Projection/ProjectedSelect、PageResult、Aggregate/AggregateGroup、条件和 seekAfter；采用可选组合入口，旧查询/写入签名保留。DTO mapper 不自动重试，pageResult 不额外套重试事务，统计整数溢出不得静默转浮点。
- 默认日志关闭且原始异常需 opt-in；调试模块将公开父类型依赖 AppCompat/RecyclerView 正确暴露为 api。
- MetricsSnapshot 新字段改变生成的构造器/copy 签名；事务指标口径变化见 README。要求消费者重新编译。
- Source 加载取消后失效，不能继续复用旧 Source。
- 之前的类拆分、BackupIdentity 必填、调试模块独立等仍需按 README 迁移。
- 消费工程只覆盖列出的公开入口；JVM 基线补充了全量可见签名比较，但**不是完整 Kotlin 二进制/源码兼容性证明**。正式稳定发布前仍须选择发布基准，审核 metadata/inline 与运行行为。

## 仍是正式发布门槛的未验证项

- API 24/35 的 Debug 和联合 R8 回归已通过；真实宿主 Release、性能、内存/生命周期长时间测试仍未完成完整验收。
- 构建工具链已知漏洞的适用性和修复/缓解审核；OSV 无命中不表示无漏洞，不把构建期依赖问题说成 APK 可被利用。
- 真实磁盘满、发布阶段中断/断电、多进程协调与目标应用全部历史迁移路径；已执行哪些进程中断点见 PLAN。
- 稳定版本与许可证/生产签名、稳定 API 审核、生产发布与回滚流程；预发布 tag/坐标不关闭这些门槛。
- Room 3、SQLiteDriver、KMP、SQLCipher 完整支持、持久化写队列均不在支持承诺内。

具体执行结果以 PLAN 的 2026-08-28 记录为准；远端 CI 状态查看 [GitHub Actions](https://github.com/kairowan/room-flow/actions)，不把已触发当作已通过。
