# 面向实体的类型安全 CRUD

这不是手写 UserQueries 的扩展：`@RoomFlowEntity` 让可选 KSP 处理器根据现有 Room 实体生成 `UserTable`。
调用时选择生成的字段，不传字段字符串；查询结果自动映射为实体。Room 仍负责建库、schema 验证、DAO 和迁移。

## 接入

宿主保留已有 Room/KSP 插件和匹配版本的 room-compiler，另外添加构建期处理器：

```kotlin
dependencies {
    implementation(project(":room-flow"))
    ksp(project(":room-flow-compiler"))
}
```

上面使用仓库本地模块；外部项目的 JitPack 预发布坐标见 [README 接入](../README.md#setup)。核心 AAR 与编译器 JAR 必须使用同一 tag，编译器只通过 ksp 引入；本地制品验收也会用独立工程消费。
处理器不会进入 APK，不需要 kotlin-reflect。KSP1/KSP2 模式沿用项目的 Room 2.6.1/2.8.4 配置。

在**现有实体**上增加 `RoomFlowEntity` 注解即可，不必为了接入修改表结构或数据库版本。例如项目的 User：

```kotlin
@RoomFlowEntity
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val age: Int?,
    val sex: String,
    val lastActive: Long = System.currentTimeMillis()
)
```

构建后在 `app/build/generated/ksp/<variant>/kotlin/.../UserTable.kt` 生成同包、独立文件的 object（启用隔离构建时位于该次输出目录）。
不要手工创建或编辑同名 UserTable。它包含 `EntityColumn<User, Long>` 的 id、`EntityColumn<User, String>` 的 name 等引用及读取映射。
`@ColumnInfo(name = "display_name") val name: String` 仍用 `Table.name` 调用，实际 SQL 使用 display_name。
SQL 条件和值在运行时组合，**不是把每一种查询 SQL 写入 build 文件**。

以下扩展函数在 `com.kairowan.room_flow.typed`：query、select、insert、update、delete、contains。每个具名类型独立文件。

## 查询：分页可选

```kotlin
// 无分页、无需手写 Cursor mapper；返回 List<User>。
val users = db.select(UserTable)
    .where(UserTable.age.greaterThan(18))
    .orderBy(UserTable.id.desc())
    .list()

// 可选分页：页码从 1 开始，单页 1..500 条。
val firstPage = db.select(UserTable)
    .where(UserTable.name.contains("张%")) // % 当作字面字符，不是通配符。
    .orderBy(UserTable.lastActive.desc())
    .page(1, 20)
    .list()

// 单列投影，返回 List<String>，不构造字段缺失的 User。
val names = db.select(UserTable).values(UserTable.name)

// 大数据逐行处理：不创建全量 List，消费失败不会自动重试。
db.select(UserTable).each { user -> consume(user) }
```

无 page() 就无 LIMIT；默认按主键排序，自定义排序后补齐主键保证同值排序稳定。
page 是 OFFSET 分页，不承诺并发插入/删除时跨页快照一致。顺序翻页可用下文的 seekAfter；UserPaging.kt 的单 id 示例仍保留。
EntitySelect 是单使用方的可变构造器，不跨线程共享/并发修改；新 QuerySpec 是可复用的不可变定义，见下节。两者 build() 返回绑定参数的 SupportSQLiteQuery，供检查或已有原生查询接口使用；外部直接执行 build() 的结果会绕过 typed 执行入口的 schema 预检。

支持 eq、notEq、greaterThan、lessThan、greaterThanOrEqual、lessThanOrEqual、between、isNull、isNotNull、isIn/notIn、字符串 contains/startsWith/endsWith、and/or 分组及 !condition。
重复 where 自动 AND，不覆盖前面的条件；eq(null) 是 IS NULL，空 IN 不匹配任何行，含 null 的 IN 显式包含 IS NULL。
contains 对 %、_、反斜杠转义，大小写沿用 SQLite LIKE。单次 SQL 保守限制 900 个绑定参数（包含 LIMIT/OFFSET），不自动分批改变语义。
between 包含两个端点，反向范围不匹配；字符串前缀/后缀也按字面量转义。
notIn 是 isIn 的集合补集：空列表匹配全部，列表不含 null 时也包含 NULL 行，含 null 时排除 NULL 行。
它不是直接拼接 SQL NOT IN；!condition 保留 SQLite 三值逻辑，例如 !column.eq("a") 不匹配 NULL。

## 查询复用与用户二次封装

`EntityTable.query()` 创建 `QuerySpec<E>`：不持有数据库/Context/Cursor，也不会访问磁盘。
where/orderBy/page/unpaged 返回新定义，原定义不变；条件值在构造时绑定快照，ByteArray 不与调用方共享。
查询定义可跨调用复用，也可交给不同但 schema 匹配的 Room 实例执行；它不是某一时刻数据库内容的快照。

```kotlin
// 用户维护的 UserQueries.kt，不修改生成的 UserTable。
object UserQueries {
    fun adults(): QuerySpec<User> = UserTable.query()
        .where(UserTable.age.greaterThanOrEqual(18))
        .orderBy(UserTable.id.desc())
}

// 用户可以用普通扩展函数增加业务规则，无需继承 SDK 类。
fun QuerySpec<User>.named(keyword: String): QuerySpec<User> =
    where(UserTable.name.contains(keyword))

val base = UserQueries.adults()
val filtered = base.named("张")
val firstPage = filtered.page(1, 20)
val secondPage = filtered.page(2, 20) // 不改变 firstPage、filtered 或 base。
val users = db.select(firstPage).list()
```

可选条件使用 `spec.whereIfNotNull(keyword) { UserTable.name.contains(it) }`。
null 不执行回调且不添加条件；空串/空集合不是 null，仍按传入条件处理。回调立即执行，不在数据库重试时重新调用。
orderBy 替换此派生定义的排序，where 始终 AND，page 替换此派生定义的分页；unpaged 只移除分页，不移除过滤条件。

项目中的 UserQueries.matching/adults 是实际可编译示例；旧 page/exportAll 返回原生 SQL 的示例保留，不能混淆两者的字段检查能力。
独立 UserRepository 示例借用 AppDatabase，不创建/关闭数据库；将校验和执行集中到业务方法，页面可以只调用：

```kotlin
val repository = UserRepository(db)
val page = repository.adultPage(1, 20, nameContains = "张")
val total = repository.adultCount(nameContains = "张")
val affected = repository.rename(userId, "李四")
```

没有强制 BaseRepository 或 Repository 接口；需要可替换实现时由宿主定义独立接口文件。
权限/租户隔离仍由业务保证，QuerySpec 不是权限令牌。强制范围的业务方法返回结果，不暴露可任意派生的查询对象当安全保证。
查询定义可能保留个人数据参数，不放入无生命周期的全局缓存、不输出绑定值。

### 结果入口的精确语义

| 入口 | 含义 |
| --- | --- |
| list() / values(column) / each(action) | 遵循定义的条件、排序、分页 |
| firstOrNull() | 当前窗口的第一条；保留排序和页偏移，只取一条；无结果为 null |
| exists() | 当前窗口是否存在记录；SQL 只取常量一行，不加载实体 |
| count() | 当前窗口行数，返回 Long；有分页时最多为页大小，越界为 0 |
| totalCount() | 保留过滤条件，明确忽略分页/排序；返回全部匹配行数 Long |

例如匹配 45 条，page(3, 20).count() 为 5，totalCount() 为 45；page(4, 20).exists() 为 false。
count 在 SQLite 内统计，不通过 list().size；firstOrNull/exists 的 LIMIT/OFFSET 也计入 900 参数上限。
单独调用 list 和 totalCount 是两次查询；并发写入时可能不是同一快照，需要一致性时使用下文 pageResult 或由宿主使用 Room withTransaction 包住。

## 多列 DTO 与一致性分页

DTO 独立放在 UserSummary.kt；无需注解或继承 SDK 类型：

```kotlin
data class UserSummary(val id: Long, val name: String)
```

```kotlin
val summary = projection(UserTable.id, UserTable.name, ::UserSummary)
val query = db.select(UserQueries.adults()).project(summary)
val rows: List<UserSummary> = query.list() // SELECT 只读取 id/name。
val page: PageResult<UserSummary> = query.pageResult(1, 20)
// page.items、page.total、page.page、page.pageSize、page.hasNext
```

字段实体类型及 DTO 构造函数参数类型在编译期匹配；unchecked cast 混入其他表在运行时拒绝。
`column.project().map { ... }` 可变换单列；`summary.zip(UserTable.age.project()) { summary, age -> ... }` 可继续组合多列。
映射回调只接收字段值，不暴露 Cursor；必须快速、无外部副作用，不进行数据库写入或阻塞工作。
mapper 异常原样传播，投影执行不自动重试；宿主也不应把带副作用 mapper 放入重试事务。
project 捕获调用时定义，ProjectedSelect 不可变，提供 build/list/firstOrNull/pageResult/observe。
重复选取同一字段按列位置读取；不构造缺字段的实体，不开放任意 SQL 表达式或反射 mapper。

实体查询也可直接 `db.select(UserQueries.adults()).pageResult(1, 20)`。
pageResult 覆盖原分页，在一次 Room withTransaction 中读取当前页和全部匹配行数，页码从 1 开始、大小 1..500，越界返回空 items 和实际 total。
同快照只覆盖这次调用，不覆盖下一次翻页；读事务可能延迟其他写入，COUNT 大表成本也不会消失。
这里不增加自动事务重试，不先加载全表再计算 total。示例 Repository 提供 adultSummaryPage。

## 统计与分组

```kotlin
val query = db.select(UserQueries.adults())
val ageSum: Long? = query.aggregate(UserTable.age.sumLong())
val averageAge: Double? = query.aggregate(UserTable.age.average())
val names: Long = query.aggregate(UserTable.name.countDistinct())
val bySex = query.groupBy(UserTable.sex, UserTable.age.average())
val updates: Flow<Double?> = query.observeAggregate(UserTable.age.average())
```

- minimum/maximum 保留字段值类型，但空集或全 NULL 返回 null；countDistinct 不统计 NULL，空集返回 0。
- sumLong 只允许 INTEGER 存储列，结果 Long?；整数溢出明确失败，不转 Double 丢精度。REAL 字段调用时拒绝。
- sumDouble/average 返回近似 Double?；空集/全 NULL 为 null，非有限数结果拒绝。不要以浮点统计承诺金额精度。
- aggregate 统计当前查询窗口；先 page 再 aggregate 就只统计该页，全部匹配统计使用未分页定义。
- groupBy 当前只支持一个分组字段、一个统计，按分组键 ASC 输出 AggregateGroup(key, value)。分页限定的是输入行，不是分组后的结果；NULL 独立成组。
- observeAggregate 与 observe 都是冷流、按表失效串行重查；不承诺逐次写入事件，不在事务内 collect。

JOIN、多字段分组、HAVING、SQL 表达式插件仍使用 Room @Query，不为这些场景暴露不受约束的 SQL 字符串入口。

## 多字段游标分页

```kotlin
val base = UserTable.query().orderBy(UserTable.age.desc(), UserTable.name.asc())
val first = db.select(base.seekAfter(null, 20)).list()
val next = if (first.isEmpty()) emptyList() else
    db.select(base.seekAfter(first.last(), 20)).list()
```

seekAfter 按当前排序和补齐的完整主键做字典序比较，支持 ASC/DESC 混排、复合主键及 SQLite NULL 排序（ASC 在前，DESC 在后）。
每页从同一个 base 派生，cursor 传上一页最后一个实体；替换 OFFSET、页大小 1..500，绑定值立即快照。
不要把空页 lastOrNull() 当作下一页游标继续查询，因为 null 表示第一页。
游标不是授权令牌或数据库快照；并发修改排序字段仍可能导致跨页遗漏/重复，条件/排序也必须保持一致。
本接口不负责游标持久化/签名或跨版本序列化；复杂排序列越多，条件与参数越多，仍受 900 参数上限限制。大表须针对实际排序建立合适索引并测量性能。

### 观察查询与旧接口兼容

```kotlin
val query = db.select(UserQueries.adults().page(1, 20))
val definition = query.toSpec() // 不持有数据库的不可变快照。
val updates: Flow<List<User>> = query.observe()
```

observe 在调用时捕获当前查询定义，Flow 为冷流：每次订阅先查询一次，随后按 Room 表失效串行重查。
之后修改 query 不改变 definition 或 updates；没有全局 Scope，不自动 shareIn/stateIn。
订阅取消复用原有观察者移除和原生查询取消链路，异常向下游传播；不在事务内等待该 Flow。
它观察的是当前页结果，不是每一条写入的事件流；失效事件允许合并，也可能重复发射相等结果。
需要去重/共享由宿主在业务生命周期中组合 Flow 操作；大列表必须显式分页，observe 本身不限制结果量。

旧 `db.select(UserTable).where(...).page(...)` 继续有效，仍会修改同一个 EntitySelect，保留忽略返回值的调用行为。
需要复用的是 QuerySpec/toSpec，不是跨线程共享 EntitySelect。写入入口、全表保护和 Room 事务语义均保持不变。

## 新增、更新、删除

```kotlin
val id = db.insert(UserTable, User(name = "张三", age = 20, sex = "男"))

// 按实体完整主键更新所有持久化非主键字段；不会 REPLACE 父记录。
val updated = db.update(UserTable, user.copy(name = "李四"))

// 部分更新，字段和值类型必须匹配。
val changed = db.update(UserTable)
    .set(UserTable.name, "李四")
    .set(UserTable.age, null)
    .where(UserTable.id.eq(id))
    .execute()

val deleted = db.delete(UserTable).where(UserTable.id.eq(id)).execute()
// 或按实体完整主键删除：db.delete(UserTable, user)

// 全表操作必须显式表达意图。
db.update(UserTable).set(UserTable.age, null).allRows().execute()
```

insert 默认冲突报错/回滚（ABORT），不 IGNORE/REPLACE；返回 SQLite rowId，复合主键不能把 rowId 当业务主键。
autoGenerate 的 Int/Long 主键为 0 时省略该列，由 SQLite 生成；不会修改传入的不可变实体。更新/删除未分配的自增主键会拒绝。
实体更新/删除按完整复合主键匹配，返回受影响行数，不存在返回 0；部分更新禁止修改主键，重复 set 同一列报错。
缺少 where 的部分更新/删除必须显式 allRows()；不能用字符串注入绕过字段/条件保护。业务本身过宽的有效条件仍须调用方负责。

业务有行数预期时，使用可选的执行上限：

```kotlin
db.update(UserTable).set(UserTable.age, 20)
    .where(UserTable.id.eq(userId)).execute(maxAffectedRows = 1)
db.delete(UserTable).where(UserTable.id.notIn(retainedIds))
    .execute(maxAffectedRows = 10)
```

`notIn(emptyList())` 仍匹配全部；无参 `execute()` 保持原有行为，不自动防止过宽条件。
上限为非负 Int，0 表示不允许影响任何行；超过时在语句所在事务内抛 IllegalStateException 并回滚，
包括该事务的外键级联/触发器数据库修改。嵌套 Room 事务也会使外层事务不能提交，不应捕获异常后继续业务。
它不是 SQL LIMIT，也不保证只尝试修改前 N 行；仅统计直接影响行数，不限制级联/触发器行数或执行耗时。
`build()` 返回的是 SQL 查询，直接用 raw/其他执行器执行不携带此保护。它不能替代业务权限、唯一约束或外部副作用控制。
写入复用 SDK 的 Room 事务和 busy/locked 重试，参与表失效通知；多步原子业务仍用 Room withTransaction 包住。
entity 字段全部按值写入，不把 Kotlin null 偷换成数据库默认值，不提供自动 upsert；需要 upsert 继续使用 Room @Upsert DAO。

## 哪些错误能被发现

```kotlin
// 编译失败：String 字段赋 Int。
db.update(UserTable).set(UserTable.name, 123)

// 编译失败：其他实体的条件不能用于 User 查询。
db.select(UserTable).where(OtherTable.id.eq(1L))

// 编译失败：不存在或被 @Ignore 忽略的字段不生成引用。
UserTable.nonexistent
```

运行时还会检查字段/条件属于同一张生成表，防御强制类型转换混入其他表。
typed 执行入口先检查打开文件中对应对象的 sqlite_master 类型为 table，并核对列名、SQLite 声明类型、nullability 和主键位置，不匹配就失败，不删库/改库。
这不是完整 schema 或业务完整性验证；索引、默认值、外键和迁移仍交给 Room 及业务回归。
当前每次执行有两次元数据预检查询，无全局缓存；不要把它当作零开销或性能提升承诺。

## 第一版边界

- 支持公开顶层、无继承/泛型的 Kotlin data class；持久化属性全部在公开主构造函数中。
- 支持 String、Boolean、Byte、Short、Int、Long、Float、Double、ByteArray 及可空字段；主键非空。浮点 NaN/Infinity 写入/条件拒绝。
- 支持 @ColumnInfo 列名、单主键、Entity.primaryKeys 复合主键；自增仅 Int/Long。
- @Ignore 属性放在类体并自行初始化，不放主构造参数；Room 2.6 的构造匹配不能按“有 Kotlin 默认值”假定它可用。
- 不支持 Embedded、Relation、TypeConverters、FTS、Entity.ignoredColumns、继承、泛型实体或自定义存储 affinity；不支持的源实体编译失败。
- 数据库级转换器在同次源代码处理中也拒绝；处理器看不到其他模块/运行时才接入的转换器配置，宿主仍须遵守“只使用原生标量映射”的契约。
- 支持标量字段 DTO 投影、单列统计和单键/单指标分组；不支持 JOIN/HAVING、多键分组、任意 SQL 表达式、自动 schema 迁移、自动批量分块或通用 upsert。复杂映射/SQL 继续用 Room DAO，不猜测转换逻辑。
- 不改变现有 Room 2 OpenHelper、minSdk 24、非 Room 3/Driver/KMP 的范围。不得将生成器用于旧版迁移 SQL。

## 验证入口

`bash scripts/check-typed-compilation.sh --offline` 验证两 KSP 模式对跨实体、错误值、忽略字段、嵌套、自定义类型、泛型的编译拒绝；没有缓存时去掉 --offline。
编译失败检查同时覆盖 QuerySpec 的跨实体条件、可选条件错误值类型、跨实体投影、错误 DTO 参数、字符串数值统计和跨实体游标；独立消费工程从发布制品编译这些扩展入口。
按所有者要求，真实 SQLite 的 CRUD、QuerySpec、投影/统计/游标等设备测试源码及运行脚本已删除，历史执行结果保留在 PLAN.md。
编译检查不验证事务、数据完整性、Flow、取消或 SQLite 运行行为；正式接入仍须在宿主或独立设备环境验收，不覆盖或清空原应用。
