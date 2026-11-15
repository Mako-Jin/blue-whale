# whale-form 多租户物理表策略评估（第 8 项）

> 结论先行：**推荐「单表 + tenant_id 列」**。当前实现实际是「物理表按租户分表 + 登记表不区分租户」的混合态，
> 在多租户同时发布同一表单时会互相覆盖登记，必须在租户数破百前收敛。
> 本文只做评估与迁移设计，不改代码（按路线图约定）。

## 一、现状（代码事实）

| 维度 | 现状 | 代码位置 |
| --- | --- | --- |
| 物理表命名 | 默认租户 `form_data_{code}_v{version}`；非默认租户 `form_data_t{tenantId}_{code}_v{version}` | `FormTableDdlGenerator#tableName(Long, String, Integer)` |
| 表名来源 | 发布时按当前请求租户（`TenantContext`）计算，并写入 `form_table_registry.physical_table` | `FormPublishServiceImpl#publishLegacy` / `#ensurePublished` |
| 读写路由 | 按记录上的 `table_code + table_version` 查登记，取登记的 `physical_table` 直接读写 | `FormDataServiceImpl#requireRegistry` |
| 数据隔离 | `form_data` 与每张物理表均带 `tenant_id`，读写 SQL 全部带租户条件 | `FormDataServiceImpl`、`JdbcFormDataEngine` |
| 登记唯一键 | `uk_tenant_table_code_version (tenant_id, table_code, table_version)`、`uk_tenant_definition_code_version (...)` | `V3__unique_constraints.sql` |
| 登记实体 | `FormTableRegistryPo` **没有** `tenantId` 字段，落库依赖列默认值 1 | `FormTableRegistryPo`、`V1__baseline.sql` |
| 登记查询 | `findRegistry(tableCode, version)` / `requireRegistry(code, version)` **不带租户条件** | `FormPublishServiceImpl`、`FormDataServiceImpl` |

### 由此产生的两个直接问题

1. **登记表实际是「全局单份」**：`tenant_id` 恒为默认值 1，唯一键 `(tenant_id, table_code, table_version)`
   等价于 `(table_code, table_version)`。
2. **多租户发布互相覆盖**：租户 2 发布同一 `(code, version)` 时，`findRegistry` 会命中租户 1 的登记行并直接
   `updateById`，把 `physical_table` 改成 `form_data_t2_...`；此后租户 1 的读写也会被路由到租户 2 的物理表
   （隔离靠 SQL 里的 `tenant_id` 条件兜住数据，但表归属已经错了，且租户 1 自己的物理表变成孤儿）。
   只要出现「同一表单被两个租户分别发布」，就会踩到。

> 当前生产若只有默认租户 1，此问题不会暴露；一旦开启第二个租户，属**必须先修**的正确性问题。

## 二、两种目标形态对比

| 对比项 | 方案 A：单表 + `tenant_id` 列（推荐） | 方案 B：按租户分表（每租户一套） |
| --- | --- | --- |
| 物理表命名 | `form_data_{code}_v{version}`（去掉租户段） | 维持 `form_data_t{tenantId}_{code}_v{version}` |
| 表数量 | 表单数 × 版本数 | 租户数 × 表单数 × 版本数 |
| MySQL 表数量压力 | 低（百级） | 高（租户破百即万级，受 `open_files_limit` / `innodb_file_per_table` 制约） |
| 数据隔离 | 应用层 `tenant_id` 条件（现状已全部具备） | 物理隔离，天然更强 |
| 索引设计 | 需复合索引 `(tenant_id, biz_key)`、`(tenant_id, status)`，动态列索引前端补 `tenant_id` | 单租户内索引即可，`biz_key` 单列可接受 |
| 租户删除/归档 | 逻辑删 + 归档，单表按 `tenant_id` 清理 | 可整表归档/删除，运维直观 |
| 大租户性能 | 共享表，热点集中；可后续按 `tenant_id` 分区 | 天然分散 |
| DDL 成本 | 发布一次改一张表，全租户生效 | 发布要遍历所有租户逐一 ALTER，耗时 × 租户数 |
| 迁移成本 | 中：合并存量分表数据到单表 | 低：维持现状，补租户维度到登记表 |
| 跨租户统计 | 直接聚合 | 需跨表 UNION |

### 为什么推荐 A

- **代码已为 A 打底**：所有物理表读写 SQL 都已带 `tenant_id` 条件，`FormDataEngine` 的列白名单与参数绑定不区分租户；
  改成 A 主要是「表名去掉租户段 + 登记表租户维度收敛」，不触及数据访问层语义。
- **发布成本**：动态 DDL 是本模块最大风险面（见批次 1 发布预检）。方案 B 下每加一个租户，发布耗时就乘租户数，
  且任一张表失败就产生租户间结构不一致；A 只有一份结构，发布一次的收益覆盖全部租户。
- **表数量**：B 的表数量是 A 的租户倍数，租户破百后接近 MySQL 单库表数量与文件句柄上限，属不可逆的运维债。
- **B 的唯一优势是物理隔离**，但本模块的租户隔离本来就是应用层 `tenant_id`（同一库、同一连接池），
  分表并不能带来真正的安全边界提升；真正的物理隔离应通过「独立库/独立实例」实现，不属于本表设计范畴。

## 三、方案 A 落地设计（待排期实施）

### 3.1 结构变更

1. `form_table_registry` 唯一键从 `(tenant_id, table_code, table_version)` 收敛为
   `uk_table_code_version (table_code, table_version)`；`tenant_id` 列保留但语义改为「首个发布租户」，仅供审计。
2. `FormTableDdlGenerator#tableName` 去掉租户段（保留 `tenantId` 入参以兼容调用方签名，内部忽略），
   物理表名统一 `form_data_{code}_v{version}`。
3. 物理表公共列已含 `tenant_id`，无需新增列。

### 3.2 索引

每张物理表已有公共索引（见 `FormTableDdlGenerator#baseIndexDefs`），需确认/补齐：

- `idx_tenant_biz (tenant_id, biz_key)`
- `idx_tenant_status (tenant_id, status)`
- 字段级自动索引 `idx_f_{column}` 仅单列；建议后续改为 `(tenant_id, column)`，否则跨租户检索选择性不足。
  该改动会影响 `syncIndexes` 的期望索引集合，需与发布流程一起改。

### 3.3 存量数据迁移（一次性）

1. 发布窗口内停写（或按租户灰度）：对每个存在非默认租户物理表的 `(code, version)`：
   - 建单表 `form_data_{code}_v{version}`（若已存在则跳过）；
   - `INSERT INTO ... SELECT ... FROM form_data_t{n}_{code}_v{version}`（保留原 `tenant_id`）；
   - 比对行数后删除分表（脚本外执行，Flyway 不做 DROP）。
2. 更新 `form_table_registry.physical_table` 为单表名，并清理重复登记（保留版本号最大/创建时间最早一条）。
3. 迁移脚本只做结构变更，数据搬迁走运维脚本（参考批次 1 对账与修复接口的「人工确认」模式）。

### 3.4 兼容与回滚

- 迁移前对 `form_table_registry` 与各分表做逻辑备份（导出到对象存储）；
- 回滚策略：保留分表只读副本一个版本周期，确认无回归后再物理删除；
- 应用层回滚只需把 `tableName` 恢复带租户段，并回滚 registry 唯一键（需先清理重复行）。

## 四、决策口径与时间点

- **决策**：采用方案 A（单表 + `tenant_id`）。
- **时间点**：租户数破百前完成；若近期将开启第二个租户，则提级到「批次 4 之前」优先修复
  （当前混合态在多租户发布时会覆盖登记，属正确性问题，不是纯性能问题）。
- **最小止血（若不立刻做完整迁移）**：先给 `form_table_registry` 补 `tenant_id` 并让
  `findRegistry` / `requireRegistry` 带租户条件、唯一键改为 `(tenant_id, table_code, table_version)`，
  使现状「分表」语义自洽；A 的完整迁移可随后进行。该止血改动面小（实体加字段 + 两处查询条件 + 一个迁移脚本），
  建议与批次 4 一起排期。

## 五、验收标准（实施时）

- 两个租户分别发布同一 `(code, version)` 后，各自读写仍命中自己的数据，登记只有一条且 `physical_table` 不含租户段；
- 单表数据量 100 万行、10 个租户时，`tenant_id + biz_key` 检索走索引；
- 发布一次 DDL 的耗时与租户数无关；
- 归档（`archive_status`）以「表编码 + 版本」为粒度，语义与单表一致。
