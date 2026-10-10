# 0010. 达梦 DM8 支持与 Flyway 的边界

- **状态**：已采纳
- **日期**：2026-10-10
- **决策者**：舰长

## 背景

舰长提出：「现阶段最起码需要支持一种国产库，支持一种主流库。」
主流库已经支持（MySQL，见 ADR-0009），本轮补的是国产库。

候选与筛选依据是两个正交维度 —— **方言距离**（越远越能检验换库能力）与
**本机可验证性**（开发机没有 Docker，`wsl` 被安全策略拦住）：

| 候选 | 内核 / 协议 | Flyway 官方模块 | 本机可起实例 | 结论 |
|------|-------------|-----------------|--------------|------|
| **达梦 DM8** | 自研，Oracle 兼容 | 无 | 有 Windows 版 | **选定** |
| 人大金仓 KingbaseES | PostgreSQL | 无 | 有 Windows 版 | 备选 |
| openGauss | PostgreSQL | 无 | 仅 Linux 版 | 排除 |
| OceanBase | MySQL 兼容 | 有 | 仅 Linux 版 | 排除 |
| TiDB | MySQL 协议 | 有 | 仅 Linux 版 | 排除 |

选定**达梦**的三个理由：

1. **信创场景里它是事实上的代表**，出现频率最高，先支持它覆盖面最广
2. **方言最远**（Oracle 血统）—— 先啃最硬的，其余国产库更容易兜住
3. **有 Windows 版**，本机具备验证的前提条件

OceanBase / TiDB 虽然 Flyway 支持最好、改动最小，但**它们本质是 MySQL 协议**，
选它们等于没有验证"换库能力"，只是换了个连接串。

同时接受两个既成事实：

- **Flyway 官方没有达梦模块** —— 这是本篇要处理的核心问题
- **本轮不做真实实例验证** —— 开发机没有达梦实例（也没有 Docker，`wsl` 不可用），
  舰长明确选择「先交付改动，并明确标注哪些未验证」

## 决策

### 1. 达梦 DDL 的方言差异

三套脚本按厂商分目录，列名**必须完全一致**（列名分叉意味着 Java 侧映射也要分叉）：

```
sts-server-log/src/main/resources/db/migration/
├── h2/V1__create_bw_client_log.sql
├── mysql/V1__create_bw_client_log.sql
└── dameng/V1__create_bw_client_log.sql      ← 新增
```

| 项目 | MySQL 8 | 达梦 DM8 |
|------|---------|----------|
| 自增主键 | `BIGINT NOT NULL AUTO_INCREMENT` | `BIGINT IDENTITY(1,1) NOT NULL` |
| 变长字符串 | `VARCHAR(64)` 按字符 | `VARCHAR(64 CHAR)` —— **默认按字节**，必须显式声明 |
| 长文本 | `TEXT` | `TEXT`（等价 CLOB） |
| 列注释 | `COMMENT '...'`（inline） | `COMMENT ON COLUMN ... IS '...'`（独立语句） |
| 表选项 | `ENGINE` / `CHARSET` / `COLLATE` | 不支持，写了直接语法错误 |
| 标识符大小写 | 受 `lower_case_table_names` 影响 | 不加引号一律**转大写**（与 Oracle 一致） |

**`CHAR` 那一条不是风格问题**：Java 侧 `ClientLogService` 的长度校验按**字符**算，
达梦 `VARCHAR(n)` 默认按**字节**算。UTF-8 下 255 字符的中文是 765 字节，
塞进 `VARCHAR(255)` 会插入失败，而报错只提"数据超长"，
不会告诉你差在字符与字节的换算上。

### 2. MyBatis-Plus 原生支持达梦，代码零改动

这是本轮最大的意外收获。`mybatis-plus-annotation` 3.5.17 的 `DbType` 枚举里
**内置了 `DM`**，`mybatis-plus-extension` 的 `JdbcUtils` 用正则 `:dm\d*:` 匹配 URL：

```java
} else if (regexFind(":dm\\d*:", url)) {
    return DbType.DM;
}
```

也就是说，只要 `application-dameng.yml` 的 URL 写对，`DbAutoConfiguration` 里
那个**刻意不指定 `DbType`** 的分页拦截器就会自动选择达梦方言。
分页、防全表更新、乐观锁全部无需改动。

顺带发现枚举里还有一长串国产库：`KINGBASE_ES`、`OPENGAUSS`、`GAUSS`、`GAUSS_DB`、
`OCEAN_BASE`、`XU_GU`（虚谷）、`SINODB`（星瑞格）、`OSCAR`（神通）、`UXDB`、
`VASTBASE`、`GOLDENDB`、`YASDB`（崖山）、`GBASE` 系列……
**将来再换库时先查这张枚举表**，能省掉大量判断。

新增 `DatasourceDialectTest` 把三个 profile 的 URL → `DbType` 映射钉在测试上。
理由：识别失败的表现是**静默退化** —— 分页拦截器不报错、只是不再生成分页 SQL，
`Page` 查询变成全量查询，要等表长到几十万行才表现为"越来越慢"，且没有明显触发条件。

### 3. Flyway 在达梦下关闭，表结构走交付脚本

要在达梦上用 Flyway，只有两条路，**当前两条都不该走**：

| 方案 | 代价 | 判定 |
|------|------|------|
| 引入社区 `db-migration` 扩展 | 为一个库引入个人维护的外部依赖，版本需与之绑定 | 否 |
| 自研 `DatabaseType` | `DatabaseType` 位于 `org.flywaydb.core.internal.*`，`BaseDatabaseType` 尚有 8 个抽象方法，且 `createDatabase` / `createParser` 需配套 `Database`、`Schema`、`Parser` 子类，合计约 300–400 行；全部绑定 Flyway 内部 API，**Flyway 每次升级都可能改签名** | 否 |

在**没有实例可验证**的前提下，这两条路都是纯负债 —— 写完既无法证明它是对的，
也无法在 Flyway 升级时快速发现它坏了。

因此达梦 profile 下 `spring.flyway.enabled=false`，DDL 作为**交付脚本**：

```bash
BW_DB_PASSWORD=xxx bash scripts/init-dameng.sh          # 需要 disql 在 PATH 中
# 或由 DBA 直接执行 db/migration/dameng/V1__create_bw_client_log.sql
```

这也与信创交付的常规做法一致：DDL 作为交付物提交、由 DBA 审核后执行，
应用不擅自变更库结构。

**文件仍放在 `db/migration/dameng/` 下、编号仍用 `V1__`** —— 不是为了 Flyway，
是为了三套脚本能按同一条时间线对照。将来若真要在达梦上恢复自动迁移，
文件无需改名挪位，直接就能被接管。

### 4. 保留字核对：本表在达梦上零冲突

达梦的关键字/保留字可从 `V$RESERVED_WORDS` 视图查（`RESERVED='Y'` 的才不能直接作标识符）。
逐项核对结果：

| 对象 | 结果 |
|------|------|
| 表名 `bw_client_log`、4 个索引名、约束名 `pk_bw_client_log` | 均不在达梦关键字表中 |
| 14 个列名 | 唯一出现在关键字表里的是 `LEVEL` —— 但它是**关键字而非保留字**，可合法作列名 |
| `RELEASE` | **达梦根本不认它是关键字** |

最后一行是对 ADR-0009 的呼应，也是这件事最有意思的地方：
`RELEASE` 是 **MySQL 8 的保留字**、达梦完全不管；`LEVEL` 是**达梦的关键字**、
MySQL 不管。**保留字风险是按库各算的 —— 在一个库上跑通不能证明在另一个库上安全。**
`app_release` 这个列名在达梦上本来就合法，保持它纯粹是为了三套脚本同构。

## 验证状态

### 已在本机验证

以下各项**不需要达梦实例**即可验证，均已跑通：

| 已验证项 | 证据 |
|----------|------|
| 三份 profile 的 URL 都能被 MyBatis-Plus 识别出方言 | `DatasourceDialectTest`（26 个用例中的 2 个） |
| `dameng` profile 能被正确激活 | 启动日志 `The following 1 profile is active: "dameng"` |
| 达梦驱动在 classpath 上、DataSource 可创建 | 应用启动成功（3.2s），Tomcat 正常监听 |
| 达梦 profile 下 Flyway **确实被跳过** | 启动过程无任何 Flyway 输出与迁移执行 |
| 健康检查能识别数据库不可达 | `{"status":"DOWN","dependencies":{"db":"DOWN"},"profile":"dameng"}` |
| 连库失败时不外泄内部细节 | 业务接口返回 `code:50000` / `"系统内部错误"`，无驱动堆栈 |

> **一个值得记住的运行行为**：上表是在**没有任何达梦实例**的机器上验证的，
> 而应用依然"启动成功"了。原因是 DataSource **懒初始化**，
> 且 Flyway 关闭后启动阶段没有任何组件会去连库 —— 物理连接要等第一个请求才建立。
> 这与 mysql profile 不同（那边 Flyway 会在启动时连库，连不上就直接启动失败）。
> 好在本项目的健康检查**会真探活数据库**（上面那行 `db:DOWN` 就是证据），
> 因此"启动成功但连不上库"不会被误判为正常状态。

### 尚未验证

⚠️ 以下每一条都**尚未在真实达梦实例上执行过**。
首次接入真实实例时请按此清单逐条确认，不要假定它是通的。

| # | 待确认项 | 依据 / 退路 |
|---|----------|-------------|
| 1 | `VARCHAR(n CHAR)` 语法是否被目标版本接受 | 退路：长度 ×3（UTF-8 单字符最大 3 字节），或把实例参数 `LENGTH_IN_CHAR` 设为 1 |
| 2 | 降序索引 `(server_time DESC)` 语法 | 退路：删掉 `DESC` —— 这些列都在等值列之后或是单列，B 树可反向扫描，索引可用性不变 |
| 3 | `IDENTITY(1,1)` 与 MyBatis-Plus `IdType.AUTO` 的配合（驱动 `getGeneratedKeys()` 是否回填主键） | 当前 `insertBatch` 是自定义 SQL、不走主键回填，风险主要落在将来新增单条 `insert` 时 |
| 4 | 多值批量插入 `INSERT INTO t (..) VALUES (..),(..)` 是否被接受 | 达梦语法上支持；失败则需改为 `INSERT ALL` 或循环单条 |
| 5 | `TEXT` 用于 `message` / `data_json` / `context_json` | 达梦 `TEXT` 等价 `CLOB`，理论上可用 |
| 6 | 达梦返回**大写列名**与 MyBatis 下划线转驼峰的配合 | 理论可行（MyBatis 的属性匹配用大写 key），但未实测 |
| 7 | 库初始化时的字符集是否为 UTF-8（emoji 能否落库） | `dminit` 的 `CHARSET` 参数决定；GB18030 下 emoji 会失败 |
| 8 | 驱动版本 8.1.3.140 与目标实例版本是否匹配 | 驱动版本不宜低于数据库版本，以达梦官方配套驱动为准 |
| 9 | 达梦关键字随版本新增（8.1.3 → 8.1.4 新增 78 个） | 加列前重跑 `SELECT KEYWORD FROM V$RESERVED_WORDS WHERE RESERVED='Y'` |
| 10 | `JdbcUtils` 识别为 `DbType.DM` 后，达梦方言生成的**分页 SQL 本身**能否执行 | 识别已由 `DatasourceDialectTest` 覆盖；SQL 正确性必须连真库 |

## 后果

**正面**

- 「支持一种国产库」达成，且**代码零改动**（驱动 + profile + 交付脚本即全部）
- MyBatis-Plus 内置达梦方言，分页 / 防全表更新 / 乐观锁自动生效
- 三套 profile 并存，`--spring.profiles.active=` 一个参数切换，互不干扰
- **契约完全未变** —— 数据库是内部实现，对前端与 OpenAPI 零影响
- 达梦的方言差异（Oracle 血统）现在有据可查，为将来支持金仓 / openGauss 打了底

**负面 / 代价**

- **达梦下没有自动迁移**：表结构变更需人工执行脚本，与 H2 / MySQL 的 Flyway 体验不一致
- 达梦**未经真实实例验证**：配置与启动链路已在本机验证（见"验证状态"），
  但 DDL 与 SQL 的真实执行尚未跑过，上面 10 条待确认项意味着首次接入必然有一段调试
- 达梦驱动版本需自己收口（Boot 的 BOM 不纳管 `com.dameng`），是 `sts-parent` 里的又一处手工版本
- `scripts/init-dameng.sh` 依赖达梦客户端的 `disql`，且本仓不带该客户端

**后续约束**

- 新增落库字段前，**两个库的保留字都要查**（MySQL 与达梦各查一遍，结论不可互推）
- 若将来确需在达梦上恢复自动迁移，优先评估「引入成熟扩展」而不是自研 `DatabaseType`
  —— 自研的代价不在于写，而在于它绑定 Flyway 内部 API，升级即可能破损
- 达梦的 `V1__` 脚本一旦在真实环境执行过，与 H2 / MySQL 遵循同一条规则：**只增不改**
- 见 `server/README.md` 的数据源章节与 `AGENTS.md` 的环境坑表
