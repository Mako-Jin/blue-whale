# 0009. 多数据源共存（H2 / MySQL）与 Flyway 迁移机制

- **状态**：已采纳
- **日期**：2026-10-10
- **决策者**：舰长

## 背景

后端起步时选了 **H2 内存库**（`jdbc:h2:mem:bluewhale`），理由充分：零外部依赖，
clone 下来就能跑，CI 也不需要先准备一个数据库。

但它有一个迟早会撞上的问题：**数据活在 JVM 进程内存里**。

- 进程一停，数据全没 —— 演示页每次重启都是空的
- 同时起两个实例，各自看到的是两个互不相干的空库
- 无法验证"重启后数据还在不在"这类最基本的问题

舰长的开发机上装着 **MySQL 8.0.26**（`D:\tools\installer\mysql`），
里面已经有 `yc-sts-db-*`、`sts-db-*`、`parallel_world_server` 等 11 个库，
是另一个项目在用的实例。需求因此变成两个**同时成立**的目标：

1. 新人 / CI 仍然零配置可跑（不能再要求先装一个 MySQL）
2. 真实联调要能连上本机 MySQL，数据落在磁盘上

同时暴露出一个更根本的问题：**建表机制表达不了"改表"**。
`spring.sql.init` 每次启动无条件重放脚本，只能建表；线上要给已有表加一列，
它无从下手。ADR-0006 已经预告过"生产建表交给 Flyway"，这里把它落实。

## 决策

### 1. 数据源用 profile 双轨，而不是"改几行配置"

```
application.yml         公共配置 + profiles.active: h2（默认）
application-h2.yml      H2 内存库 + H2 控制台
application-mysql.yml   本机 MySQL
```

用哪个库是**一个启动参数**：

```bash
java -jar sts-app-server.jar                              # 默认 h2
BW_DB_PASSWORD=xxx java -jar sts-app-server.jar --spring.profiles.active=mysql
```

不选"改配置文件切换"的原因：那样"当前连的是哪个库"变成了一种需要人工维护、
且切错了看不出来的**文件状态**。profile 让它显式出现在启动命令行与健康检查里。

**凭据走环境变量**（`BW_DB_PASSWORD`），不写进仓库 —— 依据 ADR-0003：
密钥级配置归 Vault/KMS，最次也得是运行期注入。写进版本库的密码会永久留在
git 历史里，即便后来删掉那一行也能翻出来。

### 2. 建表统一交给 Flyway，按数据库厂商分目录

```
sts-server-log/src/main/resources/db/migration/
├── h2/V1__create_bw_client_log.sql       H2 方言
└── mysql/V1__create_bw_client_log.sql    MySQL 方言
```

- 迁移脚本由**领域模块自带**（谁拥有表结构谁写脚本），
  具体目录在各自 profile 里指定 `spring.flyway.locations`
- 各写各的**原生方言**，不追求一份通用 DDL：硬凑通用版只能取交集，
  MySQL 的列注释、存储引擎、字符集排序规则就全都写不了

关于文件内容**不写** `IF NOT EXISTS`：Flyway 用 `flyway_schema_history` 保证
"只执行一次"，再加 `IF NOT EXISTS` 反而会把"表已存在但结构不对"这种真问题静默吞掉。

### 3. 列名不得撞 MySQL 保留字 —— 并且属性名要跟着一起改

这条来自一个真实事故，值得完整记录。

`release`（发布版本号）是**自然的领域命名**，但 `RELEASE` 是 MySQL 8 的保留字
（`RELEASE SAVEPOINT`），而 **H2 不认它**。于是同一个列名：

| 库 | 结果 |
|----|------|
| H2 | 一切正常，所有测试全绿 |
| MySQL | `ERROR 1064` 建表失败 |

**这是最难查的一类差异：只在其中一个库上暴露。** 修复过程还踩了第二层：

1. 把列名改成 `app_release`，Java 属性仍叫 `release`，加 `@TableField("app_release")`
   → 编译通过、H2 通过，MySQL 依然 1064
2. 原因是 **MyBatis-Plus 发现"列名与属性名不一致"，自动补了别名**
   `app_release AS release` —— 别名把保留字原样带了回来
3. 对照同一句 SQL 里的 `trace_id`、`data_json` 都没有别名，
   因为它们的下划线形式与属性名天然对应，MP 无需补

**最终做法**：让属性名与列名走同一套推导 —— 属性叫 `appRelease`，
由全局 `map-underscore-to-camel-case` 推导出 `app_release`，与
`traceId → trace_id` 完全同构，不产生别名。因此**刻意不加 `@TableField`**。

对外字段名不受影响：VO 与契约里的 `release` 保持不变（JSON 没有保留字概念），
映射在 `ClientLogService#toVO` 里显式完成。

### 4. `flyway-mysql` 必须显式引入

Flyway 10 起把各数据库支持拆成了独立模块，`flyway-core` 只内置
H2 / SQLite（可以在 core 的 jar 里看到 `internal/database/h2/`，
却没有任何 mysql 包）。少这一条的症状是 **H2 profile 一切正常，
mysql profile 启动时抛 "Unsupported Database: MySQL 8.0"**，编译期毫无征兆。

与 MyBatis-Plus 拆出 `mybatis-plus-jsqlparser` 是同一类陷阱：
starter 不再传递引入全部数据库支持，**谁用谁声明**。

## 后果

**正面**

- 两种场景同时满足：零配置可跑（默认 h2）+ 数据持久化（mysql profile）
- 数据真的落在磁盘上：重启、换端口、多实例并存，看到的都是同一个库
- 表结构变更有了演进机制：追加 `V2__xxx.sql` 即可，不必重建库
- 契约**完全没变** —— 数据库是内部实现，本次升级对前端与 OpenAPI 零影响

**负面 / 代价**

- MySQL 侧多了一次前置准备：库要先存在（Flyway 只建表、不建库）
- 迁移脚本要按厂商维护两份：新增字段时得改两个文件，
  忘一个的症状只在那一个库上出现 —— 但这正是"分目录"换来的可控性
- 迁移脚本一旦在真实环境执行过就**不可修改**（校验和不匹配会拒绝启动）。
  本地开发可重建库绕过；已有数据的环境必须追加新版本
- `mybatis-plus.global-config` 与 `map-underscore-to-camel-case` 成了隐式契约：
  关掉它，所有下划线列名都会失配

**后续约束**

- 新增会存到库里的字段前，**先查一遍 MySQL 保留字**（`rank`、`groups`、
  `system`、`rank`、`window` 等都是常见的踩坑词）
- 新增领域模块时，若它自建表，迁移脚本放自己的
  `src/main/resources/db/migration/<vendor>/` 下；应用层无需改配置
- 迁移脚本一旦提交就视为历史，只增不改
- 生产环境的密码必须由外部注入，`application-mysql.yml` 里不得出现明文
- 见 `server/README.md` 的「数据源与迁移」章节与 AGENTS.md 的环境坑表
