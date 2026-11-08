# CRM M3 7f-1：合同台账投影 + 回款计划与催收

## Context

五服务分步计划 Step 7 的 CRM M3 拆为 7f-1 / 7f-2 / 7f-3。本批只做 **7f-1：合同台账投影 + 回款计划/分期/承诺/催收优先级**，不涉及商务订单下发 ERP（7f-2）、发票与应收对账（7f-3）。

代码对账结论：

- `whale-crm` 已有 M0–M2（customer / sales / commerce 上下文），报价垂直切片可直接复用四层与仓储模式。
- `whale-contract` 的 `ct_contract` 已有 `biz_type` / `biz_id` 列与索引 `idx_biz`，但只有 `/contract/page`、`/contract/{id}`，**没有按业务查询的内网端点**；`ContractQueryRequest` 也没有 biz 过滤。
- CRM `application.yml` 已有 `whale.crm.contract-reference-enabled=false`，尚无合同客户端。
- CRM 权限 1800–1859 已用，菜单 413–426 已用；1860–1874 / 427–431 未占用，本批使用 1860–1865 / 427–428。
- 红线不动：`.trae/documents/**`（本计划文件不提交）、`whale-mall/**`、`backend/whale-services/pom.xml`、`whale-gateway/application.yml`、`deploy/monitoring/prometheus.yml`、`infrastructure/docker/mysql/init/01-init-databases.sql`、`whale-map/**`。

边界：合同正文/模板/签署/归档归 `whale-contract`；CRM 只存投影（contractId/contractNo/amount/status/effectiveDate/expireDate + 续约标记）；ERP 是实收/税额/记账唯一所有者，本批 CRM 不写实收、不做发票。

## 实施步骤

### 1. whale-contract：按业务查询的内网只读契约（仅追加）

1. `backend/whale-apps/whale-contract/.../dto/ContractQueryRequest.java`
   - 追加 `bizType`、`bizId` 两个可选字段。
2. `backend/whale-apps/whale-contract/.../service/ContractService.java` + `impl/ContractServiceImpl.java`
   - `page()` 增加 `eq(bizType)` / `eq(bizId)` 过滤。
   - 新增 `List<ContractVO> listByBiz(String bizType, String bizId)`，只查主表、按 `createTime desc`，不加载相对方/版本/流程。
3. 新增 `controller/ContractInternalController.java`
   - `GET /contract/internal/by-biz?bizType=...&bizId=...`，返回 `Result<List<Map<String,Object>>>` 精简投影：
     `id / code / title / category / amount / currency / status / effectiveDate / expireDate / bizType / bizId / ownerId / deptId / updateTime`。
   - 参照 `ContractInternalSealController`：不加权限注解，依赖网关 `internal-deny` 拦截外部访问。
   - `bizType` 空或 `bizId` 空返回 400 参数错误，不返回全表。

### 2. CRM 数据迁移 `V7__commerce.sql`

仅 `CREATE TABLE IF NOT EXISTS`，无 DROP/TRUNCATE/DELETE/种子数据：

| 表 | 关键字段 | 约束 |
|---|---|---|
| `crm_contract` | contract_id、contract_no、customer_id、title、category、amount、currency、status、effective_date、expire_date、biz_type、biz_id、renewal_status(PENDING/RENEWED/NOT_RENEWED)、renewal_owner_id、renewal_remark、renewal_at、last_sync_at、owner_id、dept_id | `UNIQUE(tenant_id, contract_id)`；索引 `(tenant_id, customer_id)`、`(tenant_id, expire_date, status)` |
| `crm_collection_plan` | contract_id、customer_id、installment_no、planned_amount、planned_date、status(OPEN/CANCELLED)、priority_override、promise_date、promise_amount、owner_id、dept_id、remark、lock_version | `UNIQUE(tenant_id, contract_id, installment_no)`；索引 `(tenant_id, planned_date, status)`、`(tenant_id, customer_id)` |
| `crm_collection_action` | plan_id、action_type(CALL/VISIT/EMAIL/REMINDER/PROMISE)、content、promise_date、promise_amount、operator_id、created_at | 追加式；索引 `(tenant_id, plan_id)` |

优先级规则（应用层计算，落视图）：逾期 >30 天 HIGH、>7 天 MEDIUM、其余 LOW；`priority_override` 非空时覆盖。

### 3. CRM 后端四层

- `common/CrmPermissions.java`：新增 `CONTRACT_LIST`(1860)、`CONTRACT_SYNC`(1861)、`CONTRACT_RENEWAL`(1862)、`COLLECTION_LIST`(1863)、`COLLECTION_MANAGE`(1864)、`COLLECTION_ACTION`(1865)。
- `common/CrmErrorCode.java`：新增 41043 CONTRACT_NOT_FOUND、41044 CONTRACT_SYNC_FAILED、41045 CONTRACT_AMOUNT_MISMATCH、41046 COLLECTION_NOT_FOUND、41047 COLLECTION_PLAN_INVALID、41048 COLLECTION_STATE_CONFLICT 及提示语。
- `config/CrmContractProperties.java`：`enabled`(默认 false)、`baseUrl`(`WHALE_CONTRACT_URL`，默认 `http://127.0.0.1:9006`)、`connectTimeoutMillis`、`readTimeoutMillis`、`expiringDays`(默认 30)、`syncCron`；`application.yml` 追加 `whale.crm.contract.*`。
- `commerce/domain/model`：`ContractLedger`、`ContractQuery`、`CollectionPlan`、`CollectionAction`、`CollectionPriority`；`domain/repository`：`ContractLedgerRepository`、`CollectionPlanRepository`、`CollectionActionRepository`；`domain/port`：`ContractSyncPort`。
- `commerce/infrastructure/persistence`：3 个 Entity + Mapper + RepositoryImpl，沿用 `QuoteRepositoryImpl` 的 MyBatis-Plus 模式。
- `commerce/infrastructure/client/ContractInternalClient.java`：实现 `ContractSyncPort`，使用 `InternalTokenSupport.builder()`（参照 `WorkflowQuoteApprovalClient`）调用 `/contract/internal/by-biz`，带租户头；解析 `{code,data}`，非 0 抛 `CONTRACT_SYNC_FAILED`。
- `commerce/application`：
  - `service/ContractAppService` / `impl`：分页、详情、到期列表、按客户同步（upsert 投影，保留本地续约字段）、续约标记更新。
  - `service/CollectionAppService` / `impl`：分页、详情、按合同整批替换分期（校验合计 = 合同金额，差额 >0.01 抛 `CONTRACT_AMOUNT_MISMATCH`）、承诺还款、追加催收记录、动作列表。
  - `command`、`view` 按现有命名规则；续约/催收写 `@AuditLog`。
- `commerce/interfaces/controller`：
  - `CrmContractController` `/crm/contract`：`GET /page`、`GET /{id}`、`GET /expiring`、`POST /sync?customerId=`、`PUT /{id}/renewal`。
  - `CrmCollectionController` `/crm/collection`：`GET /page`、`GET /{id}`、`PUT /plan`（contractId+分期列表）、`POST /{id}/promise`、`POST /{id}/action`、`GET /{id}/actions`。
- `commerce/interfaces/scheduler/CrmContractSyncJob.java`：按 `whale.crm.contract.enabled` + 到期客户列表批量同步，`@Scheduled(cron=...)`，默认关闭；失败只记录日志不中断。

### 4. 权限/菜单脚本（Auth 库）

新增 `backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/migration-5.83-crm-m3-commerce.sql`：

- 权限 1860–1865（`sys:crm:contract:*` / `sys:crm:collection:*`），全部 `INSERT ... WHERE NOT EXISTS`。
- 菜单 427「合同台账」`/crm/contract`（permission `sys:crm:contract:list`）、428「回款计划」`/crm/collection`（`sys:crm:collection:list`），父菜单 413。
- 角色授权：`tenant_admin / crm_admin / crm_sales_manager / crm_sales` 全量 1860–1865；`crm_analyst / crm_service / crm_marketing` 仅 1860/1863。

### 5. 前端 `frontend/platform-apps/whale-crm`

- `src/api/commerce.ts`：合同/回款类型与 11 个函数，复用 `api/client.ts`。
- `src/utils/dict.ts`：追加合同状态、续约状态、回款状态、优先级标签与颜色。
- `src/pages/contract/index.tsx`：台账筛选/分页、详情抽屉、按客户同步、续约标记、30 天到期提醒 Tab。
- `src/pages/collection/index.tsx`：回款计划分页/逾期高亮、按合同编辑分期（合计实时校验）、承诺还款、催收记录抽屉。
- `src/App.tsx`：菜单 + 路由注册（`/contract`、`/collection`），selectedKey 归并。

### 6. 测试

- CRM 迁移测试：追加 `V7` 表集合断言（3 表）、无 seed、唯一键断言。
- `ContractAppServiceImplTest`：同步 upsert、续约字段保留、合同不存在错误。
- `CollectionAppServiceImplTest`：分期合计等于合同金额、超差拒绝、承诺还款写动作、优先级派生。
- Contract 模块：为 `listByBiz` 增加最小服务测试（mock mapper），覆盖 biz 过滤与空参。
- 现有 234 项测试不得回归。

### 7. 文档回填（仅 4 处）

1. `docs/plans/2026-11-04-wave5.83-crm-panorama.md`：状态行与 M3 章节标注 7f-1 已完成及表/权限/端点。
2. `docs/plans/2026-11-06-five-services-backlog-stepwise.md`：Step 7 行追加 7f-1。
3. `docs/service-catalog.md`：C-11 行与主表行补 V7、表数、端点、权限段。
4. `docs/devlog/2026-11-07.md`：新增 7f-1 小节。

## 验证

1. 后端 CRM：`mvn -o -pl whale-apps/whale-crm test "-Dcheckstyle.skip=true"`（cwd `backend`）。
2. 后端 Contract：`mvn -o -pl whale-apps/whale-contract test "-Dcheckstyle.skip=true"`。
3. 前端：`pnpm --filter whale-crm lint` 与 `pnpm --filter whale-crm build`（cwd `frontend`）。
4. 文档：`powershell -File scripts/check-doc-consistency.ps1` → PASS。
5. 迁移：空库 Flyway 启动建 3 表；脚本可重复执行；无 DROP/TRUNCATE/DELETE。
6. 精确暂存（排除 9 个 `.trae/documents/*.md` 红线）→ 提交 → 推送 `origin/main` → 输出四段式报告。
