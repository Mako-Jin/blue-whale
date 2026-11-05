# Wave 5.80 M2-3：PDM 变更单（ECO）影响评估与变更任务规格说明

> 状态：**已完成（2026-11-05）**
> 日期：2026-11-05
> 上游计划：[Wave 5.80 PDM 全景实施计划](../../plans/2026-11-04-wave5.80-pdm-panorama.md) §八 M2-3
> 前置：[M2-2 变更申请 ECR](../../specs/2026-11-05-wave5.80-pdm-m2-ecr/spec.md)（提交 `a42aadfd`）
> 边界：PDM 只保存 ECO 业务事实与执行任务；审批引擎在 M2-5 对接 OA/Workflow，本阶段保留人工流转。

---

## 一、目标与范围

### 1.1 目标

1. 建立 ECO（变更单）主档：关联已通过 ECR、标题、影响评估、执行计划、影响对象快照。
2. 固化状态机 `DRAFT → ASSESSING → APPROVING → APPROVED → EXECUTING → COMPLETED → CLOSED`，旁支 `REJECTED / CANCELLED`，非法流转一律拒绝。
3. 变更任务：任务名称、类型、目标对象、执行说明、负责人、排序；执行阶段登记结果版本号。
4. 审批通过前不可执行：非 `APPROVED` 执行返回含 `CHANGE_NOT_APPROVED` 的业务错误；执行只登记新版本号，不覆盖已发布版本。
5. 权限 956–962、菜单 191 落地；前端提供 ECO 列表、评估、任务维护、提交/审批/执行/完成/关闭。

### 1.2 不做（本阶段）

- ECN 生效日期、通知对象与旧版本 `OBSOLETE`（M2-4）。
- OA 审批发起与内网回调（M2-5）；本阶段通过 `sys:pdm:change:approve` 权限人工审批。
- 变更追溯时间线（M2-6）、到期生效任务（M2-7）、消息通知（M2-8）。
- 自动改写文档/BOM/零件的已发布版本；执行仅登记结果版本号，实际发布由既有文档/BOM 发布流程完成。

---

## 二、关键决策

| 编号 | 决策 | 结论 |
|---|---|---|
| D1 | 迁移版本 | 新增 `V8__change_order.sql`；V7 已被 ECR 占用，严格递增；仅 `CREATE TABLE IF NOT EXISTS`。 |
| D2 | ECR 来源 | 仅 `APPROVED` ECR 可创建 ECO；创建时原样复制 ECR 的 `impact_json`，不解析重序列化。 |
| D3 | 编号规则 | `EO-yyyyMMddHHmmss-四位随机数`；依赖 `uk_chg_order_no(tenant_id, eco_no)` 兜底唯一。 |
| D4 | 状态常量 | `PdmChgOrderStatus` / `PdmChgTaskStatus` 常量类，禁止裸字符串。 |
| D5 | 评估口径 | `assess` 仅 `DRAFT → ASSESSING`，要求影响评估非空；重复评估拒绝。 |
| D6 | 提交口径 | 仅 `ASSESSING → APPROVING`；提交前要求影响评估非空且至少 1 个任务。 |
| D7 | 执行口径 | 仅 `APPROVED → EXECUTING`；其他状态抛“变更单未审批通过，不可执行（CHANGE_NOT_APPROVED）”。 |
| D8 | 任务完成 | 仅 `EXECUTING` 且任务为 `PENDING`；`resultVersionNo` 经 `PdmVersionNo.parse` 校验并归一化；重复完成拒绝。 |
| D9 | ECO 完成 | 仅 `EXECUTING` 且全部任务 `COMPLETED`；写 `complete_time`；重复完成拒绝。 |
| D10 | 任务编辑 | 仅 `DRAFT/ASSESSING` 可增删改任务；任务完成需校验属于当前 ECO。 |
| D11 | 删除 | 仅 `DRAFT` 可删除 ECO，并级联逻辑删除任务。 |
| D12 | 取消/关闭 | `cancel` 允许 `DRAFT/ASSESSING/APPROVING/APPROVED`；`close` 允许 `COMPLETED/REJECTED/CANCELLED`。 |
| D13 | 权限码 | 956 list、957 add、958 edit（评估/取消/关闭/任务增删改）、959 delete、960 submit、961 approve、962 execute（执行/任务完成/ECO 完成）；963–969 预留 M2-4..M2-8。 |
| D14 | 菜单 | 菜单 191 `/pdm/change/order`，组件 `pdm/change/order`，权限码 `sys:pdm:change:order:list`；192–194 预留给 ECN/追溯/通知。 |
| D15 | 角色 | 复用 `21105 pdm_change_manager`；`tenant_admin/pdm_manager/pdm_change_manager` 追加 956–962，`pdm_member` 仅 956。 |
| D16 | 审计 | 新建/修改/删除/评估/提交/审批/驳回/执行/任务增删改/任务完成/完成/取消/关闭均 `@AuditLog`；查询不审计。 |
| D17 | 租户隔离 | 实体继承 `BaseEntity`；`tenant_id` 由租户拦截器写入；按 ID 查询跨租户不可见。 |
| D18 | 计划字段 | `execute_plan` 为 ECO 级总体执行计划；`pdm_chg_task.plan_desc` 为单任务执行说明。 |

---

## 三、接口清单

### 3.1 ECO 主档

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/pdm/change/order/page` | `sys:pdm:change:order:list` | 按编号/状态/ECR/发起人分页 |
| GET | `/pdm/change/order/{id}` | `sys:pdm:change:order:list` | 详情（内嵌任务） |
| POST | `/pdm/change/order` | `sys:pdm:change:order:add` | 从已通过 ECR 新建草稿 |
| PUT | `/pdm/change/order/{id}` | `sys:pdm:change:order:edit` | 修改草稿/评估中 |
| DELETE | `/pdm/change/order/{id}` | `sys:pdm:change:order:delete` | 删除草稿（级联任务） |
| POST | `/pdm/change/order/{id}/assess` | `sys:pdm:change:order:edit` | 影响评估（DRAFT→ASSESSING） |
| POST | `/pdm/change/order/{id}/submit` | `sys:pdm:change:order:submit` | 提交审批（ASSESSING→APPROVING） |
| POST | `/pdm/change/order/{id}/approve` | `sys:pdm:change:order:approve` | 审批通过（APPROVING→APPROVED） |
| POST | `/pdm/change/order/{id}/reject` | `sys:pdm:change:order:approve` | 驳回（意见必填，APPROVING→REJECTED） |
| POST | `/pdm/change/order/{id}/execute` | `sys:pdm:change:order:execute` | 执行（仅 APPROVED→EXECUTING） |
| POST | `/pdm/change/order/{id}/complete` | `sys:pdm:change:order:execute` | ECO 完成（EXECUTING→COMPLETED） |
| POST | `/pdm/change/order/{id}/cancel` | `sys:pdm:change:order:edit` | 取消（DRAFT/ASSESSING/APPROVING/APPROVED） |
| POST | `/pdm/change/order/{id}/close` | `sys:pdm:change:order:edit` | 关闭（COMPLETED/REJECTED/CANCELLED） |

### 3.2 变更任务

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| POST | `/pdm/change/order/{id}/tasks` | `sys:pdm:change:order:edit` | 新增任务（DRAFT/ASSESSING） |
| PUT | `/pdm/change/order/{id}/tasks/{taskId}` | `sys:pdm:change:order:edit` | 修改任务（DRAFT/ASSESSING） |
| DELETE | `/pdm/change/order/{id}/tasks/{taskId}` | `sys:pdm:change:order:edit` | 删除任务（DRAFT/ASSESSING） |
| POST | `/pdm/change/order/{id}/tasks/{taskId}/complete` | `sys:pdm:change:order:execute` | 完成任务（EXECUTING，登记结果版本号） |

分页每页上限 200。

---

## 四、数据库变更

`V8__change_order.sql`（`bluewhale_pdm`）：

### 4.1 `pdm_chg_order`

`id/tenant_id/eco_no/ecr_id/title/impact_assessment/impact_json/execute_plan/status/initiator_id/submit_time/approve_by/approve_time/approve_comment/execute_time/complete_time/close_time/create_time/update_time/deleted`。

- 唯一键 `uk_chg_order_no (tenant_id, eco_no)`。
- 查询索引 `idx_chg_order_status (tenant_id, status, create_time)`、`idx_chg_order_ecr (tenant_id, ecr_id, create_time)`。

### 4.2 `pdm_chg_task`

`id/tenant_id/eco_id/task_name/task_type/target_type/target_id/target_code/plan_desc/owner_id/status/result_version_no/sort_no/complete_time/create_time/update_time/deleted`。

- 索引 `idx_chg_task_eco (tenant_id, eco_id, status)`、`idx_chg_task_sort (tenant_id, eco_id, sort_no)`。
- 不建 `sort_no` 唯一键，避免任务重排冲突。

无物理外键、无 `DROP/TRUNCATE/DELETE`。

---

## 五、测试与验收

1. 仅 `APPROVED` ECR 可建 ECO；创建快照 `impact_json` 且状态 `DRAFT`。
2. `assess` 仅 `DRAFT` 可调且影响评估必填；重复评估拒绝。
3. `submit` 仅 `ASSESSING`；缺影响评估或缺任务拒绝。
4. `approve/reject` 仅 `APPROVING`；驳回意见必填。
5. `execute` 非 `APPROVED` 抛消息含 `CHANGE_NOT_APPROVED`；成功写 `execute_time`。
6. 任务增删改仅 `DRAFT/ASSESSING`；任务完成仅 `EXECUTING`，非法版本号拒绝，重复完成拒绝。
7. 任务不属于当前 ECO 时拒绝；ECO 完成后全部任务 `COMPLETED` 才可完成。
8. `cancel`/`close` 来源状态矩阵正确；`delete` 仅草稿并级联任务。
9. 分页 `size > 200` 截断为 200；详情任务按 `sort_no,id` 排序。
10. 权限迁移脚本可重复执行；权限 956–962 连续、菜单 191、角色 21105 授权存在。
11. 后端 `mvn -pl backend/whale-apps/whale-pdm test` 全绿；前端 `lint`、`build` 通过。

---

## 六、提交范围

```
backend/whale-apps/whale-pdm/src/main/java/com/bluewhale/pdm/change/**
backend/whale-apps/whale-pdm/src/main/resources/db/migration/V8__change_order.sql
backend/whale-apps/whale-pdm/src/test/java/com/bluewhale/pdm/change/**
backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/migration-5.80-pdm-eco.sql
frontend/platform-apps/whale-pdm/src/api/changeOrder.ts
frontend/platform-apps/whale-pdm/src/pages/change/**
frontend/platform-apps/whale-pdm/src/App.tsx
docs/plans/2026-11-04-wave5.80-pdm-panorama.md
docs/service-catalog.md
docs/requirements/register.md
docs/devlog/2026-11-05.md
docs/reviews/2026-11-05-wave5.80-pdm-m2-eco.md
docs/specs/2026-11-05-wave5.80-pdm-m2-eco/**
```

禁止 `git add -A`，避免带入并行 workflow 改动。
