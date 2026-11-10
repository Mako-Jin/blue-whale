# Wave 5.83 · M2 增量：报价单与审批回调 任务清单

> 规格：[spec.md](./spec.md)。按顺序执行，每项完成后立即更新状态。
> 路径约定：`CRM = whale-backend/whale-apps/whale-crm`，`AUTH = whale-backend/whale-services/whale-service-auth/whale-service-auth-starter`。
> 本清单需规格批准后开始执行。

## T1 数据库迁移
- [x] 新增 `CRM/src/main/resources/db/migration/V5__quote.sql`
  - `CREATE TABLE IF NOT EXISTS crm_quote`（`uk_crm_quote_no_version`）
  - `CREATE TABLE IF NOT EXISTS crm_quote_line`（`idx_crm_quote_line_quote`）
  - `CREATE TABLE IF NOT EXISTS crm_quote_approval`（`uk_crm_quote_approval_instance`）
  - 无 DROP / TRUNCATE / DELETE，无业务种子数据

**验证**：`CrmFlywayMigrationScriptTest` 三表集合断言；SQL 文本不含 `INSERT INTO CRM_`。

## T2 领域模型与仓储端口
- [x] `CRM/src/main/java/com/bluewhale/crm/commerce/domain/model/QuoteStatus.java`
- [x] `.../domain/model/QuoteApprovalStatus.java`
- [x] `.../domain/model/QuoteDiscountLevel.java`（≤5 / ≤15 / >15 三档）
- [x] `.../domain/model/Quote.java`
  - `updateAmounts(...)`、`locked()`、`submit(...)`、`applyApproval(...)`、`revise(...)`
  - 状态校验抛 `QUOTE_STATE_CONFLICT`
- [x] `.../domain/model/QuoteLine.java`（`calculateAmount()` 2 位舍入）
- [x] `.../domain/model/QuoteApproval.java`
- [x] `.../domain/model/QuoteQuery.java`（继承 `PageQuery`）
- [x] `.../domain/repository/QuoteRepository.java`
- [x] `.../domain/repository/QuoteApprovalRepository.java`
- [x] `.../domain/port/QuoteApprovalPort.java`

**验证**：领域层不引入 Spring / MyBatis（ArchUnit 规则）。

## T3 持久化实现
- [x] `.../infrastructure/persistence/entity/QuoteEntity.java`（`lockVersion` 加 `@Version`）
- [x] `.../entity/QuoteLineEntity.java`
- [x] `.../entity/QuoteApprovalEntity.java`
- [x] `.../mapper/QuoteMapper.java`、`QuoteLineMapper.java`、`QuoteApprovalMapper.java`
- [x] `.../repository/QuoteRepositoryImpl.java`
  - 分页 / 详情 / 行查询 / 头行插入 / 更新 / 新版本插入 / `maxSequence(prefix)`
- [x] `.../repository/QuoteApprovalRepositoryImpl.java`
  - `findPendingByInstanceId`、`markDecided`（条件更新，幂等）

**验证**：字段与 V5 列一一对应；`version` 为业务版本、`lock_version` 为并发锁。

## T4 workflow 客户端与配置
- [x] `CRM/src/main/java/com/bluewhale/crm/config/CrmQuoteApprovalProperties.java`
- [x] `WhaleCrmApplication` 注册配置类
- [x] `CRM/src/main/resources/application.yml` 增加 `whale.crm.quote-approval`
- [x] `.../commerce/infrastructure/client/WorkflowQuoteApprovalClient.java`
  - `POST /workflow/instance`，`InternalTokenSupport.builder()`
  - 透传 `X-User-Id / X-Username / X-Permission-Codes / X-User-Roles / X-Tenant-Id`
  - 校验统一响应 `code=0`，取 `data.id`

**验证**：客户端单测缺失时由 T7 的发起失败用例覆盖；配置默认值与契约一致。

## T5 应用服务与权限常量
- [x] `CrmPermissions` 增加 `QUOTE_LIST / QUOTE_CREATE / QUOTE_UPDATE / QUOTE_SUBMIT / QUOTE_REVISE`
- [x] `CrmErrorCode` 增加 41031–41036 六个 `QUOTE_*`
- [x] `.../commerce/application/command/QuoteSaveCommand.java`
- [x] `.../command/QuoteLineCommand.java`
- [x] `.../command/QuoteApprovalCallbackCommand.java`
- [x] `.../application/view/QuoteView.java`、`QuoteLineView.java`
- [x] `.../application/service/QuoteAppService.java`
- [x] `.../application/service/impl/QuoteAppServiceImpl.java`
  - 门禁、数据范围、商机可见性校验
  - 编号 `QUOTE-{yyyy}-{6位}`、金额 / 折扣率计算
  - `submit` 先发起 workflow 再落库，失败回滚保持 DRAFT
  - `revise` 追加版本、`root/parent` 指向首版
  - 回调按 `(instanceId, PENDING)` 幂等

**验证**：`QuoteAppServiceImplTest` 全部用例通过。

## T6 接口层
- [x] `.../commerce/interfaces/controller/QuoteController.java`
  - `GET /crm/quote/page`、`GET /crm/quote/{id}`
  - `POST /crm/quote`、`PUT /crm/quote/{id}`
  - `POST /crm/quote/{id}/submit`、`POST /crm/quote/{id}/revise`
  - 写接口 `@RequirePermission` + `@AuditLog`
- [x] `.../controller/QuoteInternalController.java`
  - `POST /crm/internal/quote/approval-callback`（无权限注解）

**验证**：路径与权限脚本 1850–1854 的 `path/method` 完全匹配。

## T7 权限脚本
- [x] 新增 `AUTH/src/main/resources/db/migration-5.85-crm-quote.sql`
  - 1850–1854 五条权限
  - `crm_admin / tenant_admin / crm_sales_manager / crm_sales` 全量
  - `crm_marketing / crm_service / crm_analyst` 仅 1850
  - 全部 `INSERT ... SELECT ... WHERE NOT EXISTS`

**验证**：脚本可重复执行；无 1855 权限记录。

## T8 测试
- [x] 新增 `CRM/src/test/java/com/bluewhale/crm/commerce/application/service/impl/QuoteAppServiceImplTest.java`
  - 门禁 / 参数 / 编号 / 金额 / 状态机 / 折扣阈值 / 发起失败回滚 / 回调幂等 / 审批后锁定 / 新版本 / 数据范围
- [x] 扩展 `CrmFlywayMigrationScriptTest` 增加 V5 用例

**验证**：
```powershell
.\mvnw.cmd -pl :whale-crm validate
.\mvnw.cmd -pl :whale-crm test
```
0 失败、Checkstyle 0 违反。

## T9 文档与提交
- [x] `docs/design/crm-service.md`：M2 状态、§3.3 报价接口、§3.4 偏差、§4 表、§5 API、权限号段
- [x] `docs/plans/2026-11-04-wave5.83-crm-panorama.md` §13.2
- [x] `docs/design/crm-module-panorama.md` §3.2 / §6.2 / §12
- [x] `docs/requirements/register.md` REQ-FUNC-082
- [x] `docs/devlog/2026-11-04.md` 追加报价小节
- [x] 新增 `docs/reviews/2026-11-04-wave5.83-crm-quote.md`
- [x] 运行 `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-doc-consistency.ps1`
- [x] 逐文件暂存 CRM / AUTH / 本波文档；`git diff --cached` 核对后提交
  - `feat(crm): Wave 5.83 M2 报价单与审批回调落地`

**验证**：`DOC-CONSISTENCY-PASS`；提交仅含本波文件，不夹带其他波次改动。
