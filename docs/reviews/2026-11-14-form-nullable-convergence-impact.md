# 表单存量物理列 NOT NULL 强制收敛 · 在线 DDL 影响评估

> 日期：2026-11-14 ｜ 模块：`whale-services/whale-form` ｜ 数据库：MySQL 8（项目基线）
> 触发：上一轮已让 `nullable=0` 的列在**新建/类型变更**时生成 `NOT NULL`，需评估是否对**存量物理表**强制收敛可空性。

## 一、结论（TL;DR）

1. **不建议在表单发布链路内强制收敛存量表**。发布接口是同步 HTTP，当前没有语句超时与异步任务，
   存量表一旦达到 GB 级，单次重建就可能超过网关超时，且失败后重试成本高。
2. **现状无需回滚**：上一轮改动只影响**新建物理表**和**发生类型变更的列**；存量表不会被自动 ALTER。
   服务层必填校验仍是数据完整性的第一道防线，物理列暂时可空不会造成业务数据错误。
3. **若合规要求物理层必须 `NOT NULL`**，推荐一次性、离线、可恢复的批处理：
   - 先盘点与 NULL 预检；
   - **每张表一条 ALTER、一次性改多列**，显式 `ALGORITHM=INPLACE, LOCK=NONE`；
   - 只处理**最新版本**且**有实际数据**的租户表；
   - 低峰限流执行，异步任务 + 审计留痕，失败可断点续跑。
4. **禁止**按列逐条 ALTER：MySQL 每次 `MODIFY COLUMN ... NOT NULL` 都会**重建整表**，
   N 个必填列就是 N 次全表重建，成本与风险成倍放大。

## 二、评估基线（代码与数据事实）

| 事实 | 位置 | 影响 |
|---|---|---|
| `columnDef` 对 `nullable=0` 输出 `NOT NULL` | `FormTableDdlGenerator#columnDef` | 只在新表/列类型变更时生效 |
| 发布只比较 `COLUMN_TYPE`，不比较 `IS_NULLABLE` | `FormPublishServiceImpl#applyAlter` + `JdbcFormDataEngine#describeColumns` | 存量表当前不会被收敛 |
| 每个列变更执行一条独立 ALTER | `FormPublishServiceImpl#applyAlter` | 若扩展为收敛，会演变为「每列一次重建」 |
| 发布为同步 HTTP，DDL 无 queryTimeout | `FormPublishServiceImpl#executeDdl` → `JdbcFormDataEngine#executeDdl` | 长 DDL 占住请求线程/连接，网关易 504，DDL 仍可能继续执行 |
| 物理表按「定义 × 版本 × 租户」拆分 | `form_table_registry.physical_table` | 待处理表数量 = 定义数 × 版本数 × 租户数，历史版本也占成本 |
| 本机 `bluewhale_form` 动态表数量为 0 | `D:\mysql8\data\bluewhale_form`，登记表约 192KB | 本地无法实测；必须用生产/准生产盘点数据评估 |

## 三、MySQL 8 在线 DDL 机制

`ALTER TABLE ... MODIFY COLUMN ... NOT NULL` 在 MySQL 8 中的行为：

- **算法**：支持 `ALGORITHM=INPLACE`，但**必须重建表**（不是 INSTANT，也不是只改元数据）。
- **并发 DML**：支持 `LOCK=NONE`，执行期间允许读写；但仍是全表/全索引复制，I/O 与 CPU 成本真实存在。
- **前置条件**：需要严格 SQL 模式（`STRICT_ALL_TABLES` 或 `STRICT_TRANS_TABLES`）；
  严格模式下**存量 NULL 会使 ALTER 失败**，因此必须先预检/回填。
- **元数据锁（MDL）**：ALTER 开始需拿到 MDL；长事务会阻塞它，而排队中的 ALTER 又会阻塞后续查询，
  形成「雪崩式」等待。建议设置短 `lock_wait_timeout` 并快速失败重试。
- **空间与日志**：重建期间需要约 1 倍表大小的额外磁盘；并发写入进入 online alter log
  （默认上限 `innodb_online_alter_log_max_size=128MB`），写入量大时可能溢出并回滚。
- **复制与缓存**：主库重建会带来 redo/binlog 压力，从库执行 DDL 时独立重建，可能产生复制延迟；
  大量页读写会挤占 buffer pool，导致其他表短时命中率下降。
- **回滚**：`NOT NULL → NULL` 同样是一次重建；失败回退不是零成本。

## 四、影响量化模型

单表耗时近似：

```
T ≈ (DATA_LENGTH + INDEX_LENGTH) / 有效重建吞吐 + MDL 等待
有效重建吞吐受磁盘 IOPS、行宽、索引数量、并发写入、复制压力影响
```

按不同有效吞吐的参考估算（仅用于排期，必须用生产盘点校准）：

| 单表数据+索引 | 20 MB/s | 50 MB/s | 100 MB/s |
|---|---|---|---|
| 100 MB | ≈5s | ≈2s | ≈1s |
| 1 GB | ≈51s | ≈20s | ≈10s |
| 10 GB | ≈8.5min | ≈3.4min | ≈1.7min |
| 100 GB | ≈85min | ≈34min | ≈17min |

生产环境在业务高峰通常应按上述结果的 **3～10 倍**预留时间，并叠加索引重建与 MDL 等待。

规模场景（示例，不代表当前生产实际）：

| 场景 | 总量 | 串行重建估算（50MB/s） | 风险判断 |
|---|---|---|---|
| 50 张表 × 100MB | 5 GB | ≈2min 纯复制；含抖动约 5～15min | 低，可在低峰窗口一次完成 |
| 200 张表 × 500MB | 100 GB | ≈34min 纯复制；实际数小时 | 中高，需分批与限流 |
| 单表 20GB | 20 GB | ≈7min；高写入时更久 | 高，建议影子表/专门工具 |
| 单表 100GB+ | 100 GB+ | ≥34min/表 | 极高，不建议物理收敛 |

**分级建议**：

- `≤100MB` 且 `≤50万行`：低风险，可批量低峰执行。
- `100MB～1GB`：中风险，单批 5～10 张，监控 MDL/复制延迟。
- `1GB～10GB`：高风险，单独窗口、限流、准备回滚快照。
- `>10GB`：不建议直接 `ALTER`；仅在合规强要求下使用影子表方案。

## 五、风险清单

| 级别 | 风险 | 触发条件 | 缓解 |
|---|---|---|---|
| P0 | 存量 NULL 导致 ALTER 失败 | 严格模式 + 历史空值 | 先盘点并回填/整改，再执行 |
| P0 | MDL 排队阻塞读写 | 有长事务/长查询 | 短 `lock_wait_timeout`、低峰、先 kill 长事务 |
| P0 | 同步请求超时、连接池耗尽 | 大表重建超过网关/HTTP 超时 | 改为异步任务，或完全移出发布链路 |
| P0 | 每列一次全表重建 | 逐列生成 ALTER | **单表单条 ALTER 合并所有列** |
| P1 | 磁盘不足/online log 溢出 | 表大、写入高 | 预留 ≥1 倍最大表空间；临时提高 log 上限；限流 |
| P1 | 从库延迟、缓存抖动 | 重建期间大量 IO | 监控复制延迟，避开高峰 |
| P1 | 历史版本表被无谓重建 | 版本化表全部纳入 | 只处理最新 `table_version` |
| P1 | 多租户重复放大 | 每租户独立物理表 | 按租户数据量排序，空表跳过 |
| P2 | 失败后状态不一致 | 中途失败/重试 | 幂等、断点续跑、DDL 审计留痕 |
| P2 | 回滚再次重建 | 误操作/需求反转 | 变更前快照，回滚也按批处理 |

## 六、方案对比

| 方案 | 做法 | 优点 | 缺点 | 结论 |
|---|---|---|---|---|
| A 保持现状（推荐默认） | 只对新表/类型变更输出 NOT NULL | 零存量 DDL 风险 | 存量物理列仍可空 | **默认采用** |
| B 一次性离线批处理 | 盘点 → NULL 预检 → 单表批量 ALTER → 校验 | 风险可控、可恢复 | 需要窗口与运维投入 | **合规要求时采用** |
| C 发布链路内强制收敛 | publish 时检测 NULLABLE 差异并 ALTER | 自动化 | 同步超时、逐列重建、阻塞发布 | **不建议** |
| D 影子表/在线改表工具 | 新建表复制数据后 rename | 对业务影响最小 | 无现成工具/Docker，运维复杂 | 仅超大表兜底 |

**推荐**：默认 A；若必须物理 `NOT NULL`，选 B，且只覆盖最新版本与有数据的租户表。

## 七、若执行方案 B：落地步骤

### 1）盘点规模

```sql
SELECT r.tenant_id, r.physical_table,
       COALESCE(r.table_code, r.definition_code) AS code,
       COALESCE(r.table_version, r.definition_version) AS ver,
       r.status, t.TABLE_ROWS,
       ROUND((t.DATA_LENGTH + t.INDEX_LENGTH) / 1024 / 1024, 2) AS size_mb
FROM form_table_registry r
LEFT JOIN information_schema.TABLES t
  ON t.TABLE_SCHEMA = DATABASE() AND t.TABLE_NAME = r.physical_table
WHERE r.deleted = 0
ORDER BY size_mb DESC;
```

同时检查未登记的孤儿物理表：

```sql
SELECT TABLE_NAME, TABLE_ROWS,
       ROUND((DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024, 2) AS size_mb
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME LIKE 'form_data_%'
  AND TABLE_NAME NOT IN (SELECT physical_table FROM form_table_registry WHERE deleted = 0);
```

### 2）找出「必填但物理可空」的列

```sql
SELECT r.tenant_id, r.physical_table, m.column_code, m.required, m.nullable,
       c.IS_NULLABLE, c.COLUMN_TYPE
FROM form_table_registry r
JOIN form_column_meta m
  ON m.tenant_id = r.tenant_id
 AND m.table_definition_id = r.table_definition_id
 AND m.table_version = r.table_version
 AND m.deleted = 0
JOIN information_schema.COLUMNS c
  ON c.TABLE_SCHEMA = DATABASE()
 AND c.TABLE_NAME = r.physical_table
 AND c.COLUMN_NAME = m.column_code
WHERE r.deleted = 0
  AND r.table_definition_id IS NOT NULL
  AND m.required = 1
  AND c.IS_NULLABLE = 'YES';
```

### 3）NULL 预检

必须先确认待收敛列没有 NULL。按上一步结果生成每列计数（示例模板）：

```sql
SELECT COUNT(*) AS null_cnt FROM `form_data_x_v1` WHERE `col_a` IS NULL;
```

有 NULL 时只能二选一：业务确认后回填默认值，或排除该列并输出整改清单；**禁止盲目填 0/空串**。

### 4）单表一条 ALTER（关键）

```sql
ALTER TABLE `form_data_x_v1`
  MODIFY COLUMN `col_a` VARCHAR(255) NOT NULL COMMENT '字段A',
  MODIFY COLUMN `col_b` DECIMAL(18,4) NOT NULL COMMENT '字段B',
  ALGORITHM=INPLACE, LOCK=NONE;
```

- 显式指定算法与锁，避免 MySQL 静默回落到阻塞 DML 的 COPY。
- 执行前 `SET SESSION lock_wait_timeout = 10;`，避免 MDL 排队拖垮业务。
- JDBC 侧设置语句超时（如 `queryTimeout`/`socketTimeout`），确保请求可快速失败并重试。
- 每张表执行前后校验 `IS_NULLABLE`，并写入 `form_ddl_audit`。

### 5）执行策略

- 只处理最新版本（`table_version = 该定义最大版本`）且 `TABLE_ROWS > 0` 的表。
- 按 `size_mb` 从小到大执行，单批 5～10 张，批间留出观察窗口。
- 监控：`information_schema.innodb_trx`、`performance_schema.metadata_locks`、复制延迟、磁盘余量。
- 失败即停，修因后续跑；反向 ALTER 也会重建，回滚同样按批处理。

## 八、需求项 / 完成项 / 待做项 / 已修正问题项

- **需求项**：评估强制收敛存量物理列可空性的在线 DDL 影响，并给出是否执行与如何执行的结论。
- **完成项**：核实发布/DDL 代码现状、MySQL 8 在线 DDL 行为、版本化与多租户放大效应；
  形成风险清单、量化模型、方案对比与可执行 SQL。
- **待做项**：
  1. 在生产/准生产执行第七节盘点 SQL，回填真实表数量、行数、容量；
  2. 由业务确认是否需要物理 `NOT NULL`（若无合规要求，建议保持方案 A）；
  3. 若确认执行，按方案 B 建异步任务、限流与审计，禁止进入同步发布链路。
- **已修正问题项**：本轮未变更代码。确认上一轮 `NOT NULL` 改动**不会自动影响存量表**，
  仅作用于新建表与类型变更列；存量风险仍为「物理可空、服务必填」的过渡状态。

## 参考来源

- [MySQL 8.0 Reference Manual · Online DDL Operations](https://dev.mysql.com/doc/refman/8.0/en/innodb-online-ddl-operations.html)
- [MySQL 8.0 Reference Manual · ALTER TABLE Statement](https://dev.mysql.com/doc/refman/8.0/en/alter-table.html)
- [MySQL 8.0 Reference Manual · InnoDB and Online DDL](https://dev.mysql.com/doc/refman/8.0/en/innodb-online-ddl.html)
