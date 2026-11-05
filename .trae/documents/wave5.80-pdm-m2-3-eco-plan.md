# Wave 5.80 M2-3：PDM ECO 影响评估与变更任务 实施计划

## Context

PDM 全景计划 M0/M1/M2-1/M2-2 已完成（M2-2 已提交 `a42aadfd` / `ddf54b22`，工作区干净，110 个后端测试全绿）。M2-3 是变更闭环的关键增量：ECR 审批通过后需要生成 ECO，完成影响评估、变更任务与执行计划；**审批通过前不可执行**，执行只登记新版本号、不覆盖已发布版本。本切片同时补齐 ECO 前端页面与权限/菜单，使变更从「申请」走到「执行完成」。

约束：文档先行；新增代码过协议/代码漏洞、圈复杂度、魔法值检查；后端测试 + 前端 lint/build 通过；完成后显式 add 本次路径并提交推送 `origin/main`，禁止 `git add -A`。

## 决策口径（沿用既有约定）

| 项 | 结论 |
|---|---|
| 迁移 | 新增 `V8__change_order.sql`（V7 已用）；仅 `CREATE TABLE IF NOT EXISTS`，无 `DROP/TRUNCATE/DELETE`、无物理外键 |
| 表 | `pdm_chg_order`：id/tenant_id/eco_no/ecr_id/title/impact_assessment/impact_json/execute_plan/status/initiator_id/submit_time/approve_by/approve_time/approve_comment/execute_time/complete_time/close_time/create_time/update_time/deleted；`uk_chg_order_no(tenant_id,eco_no)`，`idx_chg_order_status(tenant_id,status,create_time)`、`idx_chg_order_ecr(tenant_id,ecr_id,create_time)` |
| 表 | `pdm_chg_task`：id/tenant_id/eco_id/task_name/task_type/target_type/target_id/target_code/plan_desc/owner_id/status/result_version_no/sort_no/complete_time/create_time/update_time/deleted；`idx_chg_task_eco(tenant_id,eco_id,status)`、`idx_chg_task_sort(tenant_id,eco_id,sort_no)`；不建 sort 唯一键 |
| 状态机 | `DRAFT → ASSESSING → APPROVING → APPROVED → EXECUTING → COMPLETED → CLOSED`；旁支 `REJECTED / CANCELLED`。`assess` 仅 DRAFT→ASSESSING；`submit` 仅 ASSESSING；`approve/reject` 仅 APPROVING；`execute` 仅 APPROVED；`complete` 仅 EXECUTING；`close` 仅 COMPLETED/REJECTED/CANCELLED；`cancel` 仅 DRAFT/ASSESSING/APPROVING/APPROVED |
| 任务状态 | `PENDING → COMPLETED`；任务增删改仅 DRAFT/ASSESSING；完成仅 EXECUTING 且任务为 PENDING |
| ECR 来源 | 仅 `APPROVED` ECR 可建 ECO；创建时**原样复制** `impact_json`（不 parse 重序列化）；一个 ECR 允许多个 ECO |
| 提交校验 | 影响评估非空 + 至少 1 个任务 |
| 执行校验 | 非 APPROVED 抛 `BizException(PARAM_ERROR, "变更单未审批通过，不可执行（CHANGE_NOT_APPROVED）")` |
| 任务完成 | 校验任务属于该 ECO；`resultVersionNo` 用 `PdmVersionNo.parse(...).value()` 归一化后写入；重复完成拒绝 |
| ECO 完成 | 全部任务 COMPLETED 才可完成；重复完成拒绝 |
| 删除 | 仅 DRAFT 可删；级联逻辑删除任务，避免孤儿 |
| 编号 | `EO-yyyyMMddHHmmss-四位随机`，唯一键兜底 |
| 权限 | 956 list（page/detail）、957 add、958 edit（update/assess/cancel/close/task 增删改）、959 delete、960 submit、961 approve（approve/reject）、962 execute（execute/task complete/eco complete） |
| 菜单 | 191 `/pdm/change/order`，组件 `pdm/change/order`，权限码 `sys:pdm:change:order:list`，visible=1（本切片同步交付页面） |
| 角色 | 不新增角色；`tenant_admin/pdm_manager/pdm_change_manager` 追加 956–962，`pdm_member` 仅 956 |
| 前端 | `src/api/changeOrder.ts` + `src/pages/change/order.tsx`；`App.tsx` 菜单 `/change/order` 与路由 |

## 实施步骤

### 1. 文档先行

- 新增 `docs/specs/2026-11-05-wave5.80-pdm-m2-eco/spec.md`（目标、不做、决策、接口清单、DDL、验收、提交范围）与 `tasks.md`（阶段一后端/二权限/三前端/四文档提交，带勾选项）。

### 2. 后端（`com.bluewhale.pdm.change`）

- 迁移 `V8__change_order.sql`（上述两表与索引）。
- 实体：`entity/PdmChgOrder`、`entity/PdmChgTask`（继承 `BaseEntity`，不声明 tenantId）。
- Mapper：`PdmChgOrderMapper`、`PdmChgTaskMapper`（`BaseMapper`）。
- 状态常量：`support/PdmChgOrderStatus`、`support/PdmChgTaskStatus`（禁止裸字符串）。
- DTO：`PdmChgOrderQueryRequest`（ecoNo/status/ecrId/initiatorId，继承 `PageQuery`）、`PdmChgOrderSaveRequest`（ecrId/title/impactAssessment/executePlan）、`PdmChgOrderAssessRequest`（impactAssessment 必填 + executePlan）、`PdmChgTaskSaveRequest`（taskName/taskType/targetType/targetId/targetCode/planDesc/ownerId/sortNo；targetType 用 `PdmChgObjectType.requireValid`）。
- VO：`PdmChgOrderVO`（含 `tasks`）、`PdmChgTaskVO`。
- 服务：`service/PdmChgOrderService` + `service/impl/PdmChgOrderServiceImpl`，方法：page/detail/create/update/delete/assess/addTask/updateTask/deleteTask/submit/approve/reject/execute/completeTask/complete/cancel/close；分页上限 200；`requireOrder` 统一不存在报错；`toVo` 内嵌按 `sort_no,id` 排序的任务。
- 控制器 `controller/PdmChgOrderController`，基路径 `/pdm/change/order`，17 端点全部 `@RequirePermission`；写操作全部 `@AuditLog`（targetType `pdm_chg_order` / `pdm_chg_task`）；审批 DTO 加 `@Valid`。
- 复用：`PdmBomRequestContext.requireUserId` 取用户；`PdmVersionNo` 校验任务结果版本；`BizException(ErrorCode.PARAM_ERROR, ...)` 统一异常。

### 3. 单测（新增 `PdmChgOrderServiceImplTest`）

`@BeforeAll` 对两个实体执行 `TableInfoHelper.initTableInfo`；Mockito 重载 `any` 带类型。覆盖：非 APPROVED ECR 建单拒绝、APPROVED 建单快照 impact_json、assess 状态与必填、submit 缺评估/缺任务/成功、update/delete 状态限制与任务级联、任务增删改状态冻结、approve/reject（驳回意见必填）、execute 非 APPROVED 含 `CHANGE_NOT_APPROVED`、任务完成非 EXECUTING/非法版本/重复、任务不属于 ECO、ECO 完成未完成任务拒绝/全完成成功、cancel/close 来源状态、分页上限 200、detail 任务排序。

### 4. 权限与菜单

- 新增 `backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/migration-5.80-pdm-eco.sql`：权限 956–962、菜单 191、角色 21105 追加授权；全部 `NOT EXISTS` 幂等，无删除语句。不修改已执行的 `migration-5.80-pdm-change.sql`。

### 5. 前端

- `src/api/changeOrder.ts`：类型（`ChgOrderStatus`、`ChgTaskStatus`、`ChgOrder`、`ChgOrderTask`、入参）与 17 个函数。
- `src/pages/change/order.tsx`：ECO 列表 + 状态筛选；新建（下拉选择已通过 ECR，分页 size=200）；评估弹窗；任务增删改（仅 DRAFT/ASSESSING 可编辑）；提交/审批/驳回/执行/任务完成/ECO 完成/取消/关闭/删除按钮按状态与权限显示；详情抽屉展示任务与计划。
- `App.tsx`：菜单项 `/change/order`（`sys:pdm:change:order:list`）与 `<Route path="/change/order">`。

### 6. 文档同步、验证与提交

- 更新 `docs/plans/2026-11-04-wave5.80-pdm-panorama.md` §八：M2-3 完成、M2-9/M2-10 部分完成说明。
- 更新 `docs/requirements/register.md` REQ-FUNC-052 状态与落点（`pdm_chg_order`/`pdm_chg_task`/`migration-5.80-pdm-eco.sql`）。
- 更新 `docs/service-catalog.md` whale-pdm：表数 10→12，能力补 ECO。
- 新增 `docs/devlog/2026-11-05.md` 章节与 `docs/reviews/2026-11-05-wave5.80-pdm-m2-eco.md` 检查报告。
- 验证：`.\mvnw.cmd -pl :whale-pdm test`；`pnpm --filter whale-pdm lint`、`pnpm --filter whale-pdm build`。
- 提交：显式 add 上述后端/迁移/前端/文档路径后 commit，推送 `origin/main`。

## 关键文件

- 后端参考模式：`change/entity/PdmChgRequest.java`、`change/service/impl/PdmChgRequestServiceImpl.java`、`change/controller/PdmChgRequestController.java`、`change/support/PdmChgRequestStatus.java`
- 复用：`backend/whale-apps/whale-pdm/src/main/java/com/bluewhale/pdm/support/PdmVersionNo.java`、`part/support/PdmBomRequestContext.java`
- 迁移测试：`backend/whale-apps/whale-pdm/src/test/java/com/bluewhale/pdm/migration/PdmFlywayMigrationScriptTest.java`（V8 命名合法即可，无需改断言）
- 前端参考：`frontend/platform-apps/whale-pdm/src/api/change.ts`、`src/pages/change/index.tsx`、`src/App.tsx`

## 验证与完成定义

1. ECO 状态机非法流转全部拒绝；未审批执行消息含 `CHANGE_NOT_APPROVED`。
2. 任务完成需 EXECUTING + 合法版本号；ECO 完成要求全部任务完成。
3. 权限 956–962 / 菜单 191 幂等迁移可重复执行；越权 403。
4. 后端测试全绿（预计 110 + 14 左右），Checkstyle/ArchUnit 随构建通过；前端 lint/build 通过。
5. 只提交 PDM、权限脚本与本次文档，推送 `origin/main`。
