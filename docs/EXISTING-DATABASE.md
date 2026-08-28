# 已有 Room 数据接入与 SQL 规范

room-flow 增强现有 Room，不接管宿主的 schema。接入不等于迁移，事务原子性也不等于业务数据完整。

## 接入清单

1. 保留已有 Room 实例、数据库文件名/路径、@Database.version、Migration/AutoMigration、TypeConverter、Callback、OpenHelperFactory 和执行器配置。仅添加 SDK 不需要改版本或复制数据库。
2. 检查依赖树中的 Room runtime/ktx/compiler 和 SQLite 实际版本；SDK 的 api 依赖会参与解析。当前仅支持 README 中的 Room 2/OpenHelper 模式、minSdk 24，不覆盖 setDriver、Room 3、KMP 或完整 SQLCipher 流程。
3. 禁止重要数据库配置 fallbackToDestructiveMigration，打开失败不能自动删库。SelfHealingRoom 不删库，但也不能取消宿主自己配置的破坏性策略。
4. 队列只覆盖显式提交的任务，指标只覆盖包装接口；原 DAO、其他连接或进程不会自动纳入队列或指标。
5. 默认不要调用 tunePragmas；显式改为 synchronous=NORMAL 有断电时丢失最近提交的风险。

## 迁移与完整性边界

结构修改通过 Room 注册的 Migration/AutoMigration 完成。MigrationAssistant 只给有限的建表/加列建议；不自动处理任意重命名、删列、数据转换或复杂约束。不要修改 room_master_table 或 user_version 来绕过验证。

Room 的 Migration 在事务内执行，异常应原样传播；误写但成功提交的 SQL 不会被事务自动纠正。
迁移测试至少比较主键集合、逐字段值、NULL/默认值、时间/整数精度、外键关系、索引和自动生成主键；业务做了转换时比较明确的目标值，而不是机械要求字段完全不变。
每个仍支持的历史版本都需要升级到当前版本，同时测试完整链和已注册的跳跃路径。

PRAGMA integrity_check 与 foreign_key_check 只是补充检查，不能替代业务断言。导出的 schema 纳入版本管理；真实宿主还有触发器、视图、BLOB、转换器、金额等字段时，应在宿主自己的历史 fixture 中覆盖。

备份要求所有相关进程停写并关闭连接；databaseClosed=true 是调用方声明，不是自动锁。
备份 hash/identity 不是业务完整证明或真实性签名。恢复旧备份会失去备份后写入；必须先保留当前恢复点，并使用匹配旧 schema 的配置重开，再执行升级。未验证真实断电/磁盘满不应承诺绝对不丢数据。

## SQL 组织

- 静态 SQL 放在独立的 XxxDao 接口，以 @Query 编译期检查并绑定参数；新版示例的 MAX(id) 查询返回 Long?，空库不是 id=0。
- 动态 SQL 放在业务独立的 XxxQueries 类/object，返回 SupportSQLiteQuery，不持有数据库，不负责执行或重试。SDK 核心不包含宿主的表名/业务查询。
- 示例 UserQueries.page 提供 id keyset 分页和可选名字包含过滤，1..500 条；NULL 不过滤，空串匹配全部，%、_、反斜杠按字面量处理。大小写沿用 SQLite LIKE，不承诺 Unicode 语言学匹配。
- 明确投影列、稳定排序；值绑定，动态标识符白名单；不能把用户文本拼进 WHERE。大集合、空 IN、NULL、通配符必须有明确语义及测试。
- 示例导出使用 UserQueries.exportAll + rawQueryEach；有意全表但逐行消费，不能拿来一次加载整个 UI 列表。
- 更新复用 UpdateBuilder；父行写入用 @Upsert/UPDATE，不拿 REPLACE 冒充无副作用更新。@Upsert 返回插入 rowId 或更新时 -1。
- Migration 的完整 SQL 固定在对应历史版本，不引用演进中的业务查询工厂/公共 SQL 常量。

调用示例（映射结果只是 id 列表，不构造多余模型）：

```kotlin
val ids = db.rawQueryList(UserQueries.page(beforeId = null, size = 50, nameContains = "用户%")) {
    it.getLong(0)
}
```

固定 SQL 继续用 @Query。另有用户明确要求的 [类型安全实体 CRUD](TYPED-CRUD.md)：
可选 @RoomFlowEntity + 构建期处理器生成实体字段映射，并提供泛型约束及执行前检查；不复刻 Room 的 DAO/数据库生成器。

## 隔离真机验证要求

按所有者要求，设备测试目录与覆盖接入脚本已删除；历史报告和旧版数据库定义保留，当前仓库不能直接重跑原流程。正式接入时在宿主或独立验证环境完成以下检查：

1. 获得设备所有者授权，明确设备与 Android user，使用独立验证包，不覆盖、卸载或清空原业务应用。
2. 安装不依赖 SDK 的旧版 APK，写入代表性历史数据；保持 applicationId/签名覆盖安装接入 SDK 的 APK。
3. 要求旧数据库文件已存在，不能在验收端重建缺失文件掩盖丢数据；比较同 schema 接入前后的字段值、schema/identity/version 及 DAO/SDK 共用行为。
4. 对每个支持的历史版本验证迁移、缺失路径、DDL/DML 失败回滚、备份恢复；不能用示例合成数据替代宿主真实迁移路径。
5. 保留 APK、包名、日志和数据断言结果；故障注入仅在专用环境执行，不能用构建成功代替运行时验证。

历史结果与尚未关闭的发布门槛见 PLAN.md；未经新授权不恢复本仓库测试目录，也不操作真机。

参考：[Room Migration](https://developer.android.com/reference/androidx/room/migration/Migration)、[迁移与测试](https://developer.android.com/training/data-storage/room/migrating-db-versions)、[Query](https://developer.android.com/reference/androidx/room/Query)、[Upsert](https://developer.android.com/reference/androidx/room/Upsert)。项目仍使用 androidx.room 2，不能直接复制官方其他版本的 androidx.room3/driver 示例。
