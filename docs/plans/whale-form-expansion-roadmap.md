# whale-form 动态表单能力扩展路线图（24 项分批）

## 一、背景与结论

外部第二轮评审从「产品能力 / 架构演进 / 运维可观测 / 研发效率 / 安全加固」五个角度提出 24 项扩展建议，
总体判断成立：当前模块「能跑、安全底座扎实、契约清晰」，缺的是「产品纵深」与「运维闭环」。

评审中存在若干与代码现状不符的表述（第 16、17 项），本文件第 2 节逐条核对，避免把已完成项再排期。

### 已确认的决策

| 决策项 | 结论 |
| --- | --- |
| 表单数据审批能力（第 1 项） | **由外部工作流（whale-workflow）承载**。本模块只增加 `bizStatus` + `processInstanceId` + 「审批中只读」判定，状态推进由工作流回调驱动，不在本模块建本地状态机 |
| 推进方式 | 全量 24 项分批推进，本轮只落地「批次 1」（高优先级四项），其余按批次排期 |

## 二、评审事实核对（开工前）

| 评审项 | 代码现状 | 结论 |
| --- | --- | --- |
| 第 12 项 指标缺失 | `pom.xml` 已引 `micrometer-registry-prometheus`，`application.yml` 暴露 `prometheus`；无任何自定义业务指标 | 成立，仅缺业务指标 |
| 第 16 项 ArchUnit 未覆盖 | `src/test/java/com/bluewhale/form/architecture/FormArchitectureTest.java` 已存在 3 条规则（controller↛mapper/po、mapper↛controller/service、po↛外层）；缺 service↛controller、support↛service/controller 反向依赖规则 | 部分成立（规则已存在，需补强） |
| 第 17 项 关键路径单测缺失 | 已有 `FormTableDdlGeneratorTest`（16 条）、`FormDataExportSupportTest`（CSV 公式注入已覆盖）、`FormSchemaParserTest`（保留列/重复 key/嵌套子表已覆盖）、`FormMetadataCompilerTest`（重复引用/布局字段已覆盖）、`JdbcFormDataEngineFilterTest`（7 条）；缺口是：`escapeComment` 的 `'; DROP TABLE` 输入与 200 字符截断、`in`/`between` 参数顺序与上限 | 部分成立（仅上述 3 类缺口） |
| snapshotSeq 半接线 | `FormDefinitionServiceImpl#nextSnapshotSeq`（约 899-918 行）已接线，单测断言 `snapshotSeq=1` | 闭环，无需改动 |
| truncated 半接线 | `FormDataServiceImpl#export`（297-326 行）已产出 `truncated`，`FormDataController#export` 已按需写 `X-Export-Truncated` | 闭环，无需改动 |
| 第 10 项 缓存层 | 模块无 Caffeine / Redis 依赖，`requireDefinition*`、`requireRegistry` 每请求查库 | 成立 |
| 第 24 项 JSON 深度 | 本模块 Jackson 2 未配置 `StreamReadConstraints`；auth 模块已有同类写法可参考 | 成立 |

## 三、分批排期

### 批次 1（本轮落地：高优先级四项）

| 编号 | 事项 | 交付物 |
| --- | --- | --- |
| 2 | 表单数据变更历史 | `form_data_history` 表 + `GET /form/v1/data/{id}/history` + 提交/修改/删除留痕 |
| 9 | 动态 DDL 发布预检 | 列类型「收窄」识别 + `force` 二次确认，收窄未确认即拒绝发布 |
| 13 | 主表/物理表对账 | `form_reconcile_diff` 表 + 每日对账 Job + 差异分页 + 人工修复接口 |
| 17 | 关键路径单测补强 | `escapeComment` 恶意输入/截断、`in`/`between` 参数顺序与上限 |

### 批次 2（产品纵深，已落地）

1. 审批接线（外部工作流）：`form_data.biz_status`（1 草稿 / 2 审批中 / 3 已通过 / 4 已驳回）、
   `process_instance_id`；审批中 `update` / `delete` 直接拒绝；`POST /form/v1/data/{id}/approval` 发起、
   内网 `/form/v1/internal/data/approval-callback` 回调推进状态（幂等），状态流转写 `form_data_history`（`APPROVAL`）。
   存量与未接入审批的数据默认 3（已通过），升级后行为不变。
4. 批量导入：`GET /form/v1/data/import/template` 下载模板、`POST /form/v1/data/import` 导入 CSV
   （首行表头匹配字段显示名或编码，未知列忽略）；逐行复用提交校验与双写；
   `partial=true`（默认）逐行独立事务、失败行回显 `{rowNo, errors}`，`partial=false` 整批回滚；
   单次上限 1000 行。**边界**：XLSX 导入需引入 POI，本期只做 CSV（与导出 CSV 闭环），
   附件/子表字段不支持导入并在表头匹配时直接拒绝。
12. 业务指标（`FormMetrics`）：`form_publish_total{result}`、`form_publish_duration_seconds`、
   `form_publish_failure_total`、`form_ddl_audit_failure_total`、`form_export_rows`、
   `form_filter_query_duration`、`form_data_dual_write_mismatch_total`（对账差异数）。
15. 发布失败告警：失败计数 + `[ALERT]` error 日志（含表名/定义ID/异常），供 Prometheus 告警规则或日志采集触发。
5. 表单模板/分类/标签：`form_definition.category` + `tags`（JSON 数组文本），列表按 `category` 过滤；
   `tags` 非法 JSON 数组直接拒绝。

### 批次 3（性能与架构，待排期）

10. 缓存层：Caffeine（TTL 5min + 写时主动失效）优先，多实例共享需求明确后再上 Redis。
11. 事件解耦：`submit/update/delete` 后发领域事件，archive/统计改订阅，弱化 `FormInternalController` 同步耦合点。
8. 多租户物理表策略：输出「单表 + tenant_id 列」与「按租户分表」的对比评估（表数量上限、复合索引、迁移成本），
   **租户数破百前定稿**。
7. 冷热分离：`form_table_registry` 增 `archive_status/archive_time`，旧版本物理表导出归档。
19. Schema drift 检测：启动/巡检比对 `information_schema` 与实体字段，防止改实体忘改脚本。

### 批次 4（安全与权限深化，待排期）

6. 字段级权限：`visibleRoles/editableRoles`，涉及 VO 裁剪、导出裁剪、校验跳过，单独立项。
22. 导出脱敏：`sensitive` 标记 + 导出时脱敏。
23. 多版本检索 DoS：限制参与检索的版本数（如最近 3 个）或并行 + 超时。
24. JSON 深度攻击：配置 `StreamReadConstraints` 限制嵌套深度/字符串长度。
21. `X-Username` 可信边界：纳入「仅网关可信」，服务直连不采信。
14. DDL 权限收敛：部署文档明确应用账号只允许操作 `form_data_*` 前缀表。
3. 附件引用表 + 子表检索边界：`form_attachment_ref` 支持反查；子表条件检索明确不支持并写入产品边界。
16. ArchUnit 补强：service↛controller、support↛service/controller。
18. 契约测试：`FormDataVO` / `FormDefinitionVO` 的 JSON 快照测试。
20. 文档注释同步：注释引用枚举常量，避免手写状态列表。

## 四、批次 1 详细设计

### 4.1 表单数据变更历史（第 2 项）

**表结构** `form_data_history`（只追加，不更新不删除）：

| 列 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 雪花主键 |
| tenant_id | BIGINT | 租户 |
| data_id | BIGINT | 对应 `form_data.id` |
| definition_code | VARCHAR(64) | 表单编码（冗余，便于按表单查询） |
| change_type | VARCHAR(16) | `CREATE` / `UPDATE` / `DELETE` |
| before_json | MEDIUMTEXT | 变更前数据（`CREATE` 为 NULL） |
| after_json | MEDIUMTEXT | 变更后数据（`DELETE` 为 NULL） |
| changed_keys | VARCHAR(1024) | 变更字段编码，逗号分隔（`CREATE`/`DELETE` 为空） |
| operator | VARCHAR(64) | 操作人（`X-Username`） |
| change_time | DATETIME | 变更时间 |
| create_time | DATETIME | 落库时间 |

索引：`idx_tenant_data (tenant_id, data_id, change_time)`、`idx_definition_code (definition_code)`。

**口径**：
- 留痕与业务写入**同一事务**（不是「审计失败仅告警」的旁路审计）：合规要求「有数据变更必有历史」，
  历史写入失败必须回滚数据变更。
- `update` 的 `changedKeys` 由「变更前 JSON ∪ 变更后 JSON」逐字段比较得出，只记真正变化的字段。
- `delete` 记录删除前快照，便于追溯。

**接口**：`GET /form/v1/data/{id}/history`，权限 `sys:form:data:list`，按 `change_time` 倒序，最多 200 条。

### 4.2 动态 DDL 发布预检（第 9 项）

新增 `FormColumnNarrowingDetector`（`support` 包，纯静态、无状态）识别「收窄」：

| 当前类型 | 目标类型 | 判定 |
| --- | --- | --- |
| `varchar(m)` | `varchar(n)` 且 n < m | 收窄（截断风险） |
| `text` | `varchar(n)` | 收窄 |
| `decimal(p,s)` | `decimal(p',s')` 且 p' < p 或 s' < s | 收窄 |
| `datetime` | `date` | 收窄 |
| 其他 | 其他 | 不判定（交由数据库报错） |

**行为**：`ensurePublished` 在执行任何 DDL **之前**做整体预检；存在收窄且请求未带 `force=true` 时，
一次性列出全部收窄列并抛 `PARAM_ERROR`（避免执行一半再失败）；`force=true` 时放行并 `warn` 记录。

**接口**：`FormPublishService#publish(Long, boolean force)`、`#publishTableDefinition(Long, boolean force)`；
控制器增加 `@RequestParam(defaultValue = "false") boolean force`。原单参方法保留为 `force=false` 的重载。

### 4.3 主表/物理表对账（第 13 项）

**表结构** `form_reconcile_diff`：

| 列 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 雪花主键 |
| tenant_id | BIGINT | 租户 |
| batch_no | VARCHAR(32) | 对账批次（`yyyyMMddHHmmss`） |
| physical_table | VARCHAR(64) | 物理表名 |
| data_id | BIGINT | 记录 ID |
| diff_type | TINYINT | 1 主表有物理表无；2 物理表有主表无 |
| status | TINYINT | 0 待处理 / 1 已修复 / 2 已忽略 |
| detail | VARCHAR(512) | 差异说明 |
| create_time / update_time | DATETIME | 审计列 |

唯一键 `uk_batch_table_data (batch_no, physical_table, data_id)`，避免同批次重复登记。

**Job**：`form.data.reconcile`，cron 默认 `0 40 3 * * ?`，`whale.form.reconcile-enabled` 默认 `true`，
按租户遍历（`form_data` 活跃租户），单租户失败仅告警不中断。

**修复口径**：
- `diff_type=1`（主表有物理表无）：按记录 `tableVersion` 路由 registry，取 schema 字段，用 `dataJson` 重新写物理表。
- `diff_type=2`（物理表有主表无）：物理表逻辑删除该行（保留 `deleted=1`，不物理删除）。
- 修复成功置 `status=1`；记录已被处理过则幂等返回。

**接口**（权限码 `sys:form:data:reconcile`，auth 迁移 V32 幂等登记）：
- `POST /form/v1/data/reconcile/run`：手动触发一次对账
- `GET /form/v1/data/reconcile/diffs`：差异分页（`status` / `batchNo` 可选）
- `POST /form/v1/data/reconcile/diffs/{id}/repair`：修复单条差异

**已知边界**：每张物理表单次对账最多扫描 20000 条 ID；超出时**跳过**该表「主表有物理表无」方向
（未扫描到的记录会被误判为缺失），仅保留「物理表有主表无」比对，并在摘要中标记 `truncated`；
单批次最多登记 5000 条差异。全量比对后续可改为分批游标。

### 4.4 关键路径单测补强（第 17 项）

- `FormTableDdlGeneratorTest`：`'; DROP TABLE x; --` 输入、以 `\` 结尾、超过 200 字符截断、换行归一（参数化）。
- `JdbcFormDataEngineFilterTest`：`in` 参数顺序、`in` 值数量 1..50 边界、`between` 参数顺序与「恰好 2 个值」。

## 五、验收与上线注意

- 构建：`mvn -o -pl whale-services/whale-service-form test` → 208 项测试全部通过（批次 1 新增 37 项、
  批次 2 再新增 29 项），`checkstyle:check` 通过。
- 迁移：form 库新增 `V8`（变更历史）、`V9`（对账差异）、`V10`（审批状态）、`V11`（分类/标签）；
  auth 库新增 `V32`（对账权限码 2421）、`V33`（导入权限码 2422），均不自动授权任何角色。
- 兼容：`publish` 不传 `force` 与升级前行为一致；`biz_status` 默认 3，未接入审批的数据可正常编辑。
- 已知修正：迁移脚本单测原先按文件名字典序断言版本递增，导致两位版本号（V10/V11）误报；
  已改为按版本号数值升序断言（与 Flyway 排序一致）。
- 风险：对账 Job 默认开启，首次上线建议先手动触发一次
  `POST /form/v1/data/reconcile/run` 观察差异量与耗时，再交给定时任务。
