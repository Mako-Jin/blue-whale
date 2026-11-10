# Wave 5.80 M2-2：PDM 变更申请（ECR）规格说明

> 状态：**待评审 → 执行中**
> 日期：2026-11-05
> 上游计划：[Wave 5.80 PDM 全景实施计划](../../plans/2026-11-04-wave5.80-pdm-panorama.md) §八 M2-2
> 前置：[M2-1 版本值对象](../../plans/2026-11-04-wave5.80-pdm-panorama.md)（提交 `70520848`）
> 边界：PDM 只保存变更业务事实；审批引擎在 M2-5 对接 OA/Workflow，本阶段保留人工流转。

---

## 一、目标与范围

### 1.1 目标

1. 建立 ECR（变更申请）主档：变更原因、变更范围、影响对象（文档/BOM/零件）、发起人。
2. 固化状态机 `DRAFT → SUBMITTED → APPROVED / REJECTED → CLOSED`，非法流转一律拒绝。
3. 影响对象为空不可提交；非草稿不可编辑/删除/提交；驳回必须填写审批意见。
4. 变更权限号段 950–956、菜单 190、角色 `pdm_change_manager`（21105）落地。
5. 前端提供变更申请列表、状态筛选、新建/编辑、提交、审批与关闭。

### 1.2 不做（本阶段）

- ECO 影响评估与执行、ECN 生效日期（M2-3/M2-4）。
- OA 审批发起与内网回调（M2-5）；本阶段通过 `sys:pdm:change:approve` 权限人工审批。
- 变更追溯时间线（M2-6）、到期生效任务（M2-7）、通知（M2-8）。
- 影响对象的联动校验（对象是否存在由后续 ECO 执行阶段校验）。

---

## 二、关键决策

| 编号 | 决策 | 结论 |
|---|---|---|
| D1 | 迁移版本 | 新增 `V7__change_request.sql`；V6 已被对比任务占用，严格递增；仅 `CREATE TABLE IF NOT EXISTS`。 |
| D2 | 影响对象存储 | `impact_json` LONGTEXT，元素为 `{objectType,objectId,objectCode,objectName}`；类型限 `DOCUMENT/BOM/PART`，至少提供 `objectId` 或 `objectCode`。 |
| D3 | 编号规则 | `CR-yyyyMMddHHmmss-四位随机数`；依赖 `uk_chg_request_no(tenant_id, change_no)` 兜底唯一。 |
| D4 | 状态常量 | `PdmChgRequestStatus` 常量类，禁止裸字符串；`PdmChgObjectType` 负责类型校验。 |
| D5 | 审批口径 | 提交/通过/驳回/关闭均为人工流转，写 `approve_by/approve_time/approve_comment/close_time`。 |
| D6 | 权限码 | 950 list、951 add、952 edit（含关闭）、953 delete、954 submit、955 approve；956–969 预留 M2-3..M2-8。 |
| D7 | 菜单 | 菜单 190 `/pdm/change`，组件 `pdm/change/index`，权限码 `sys:pdm:change:list`；191–194 预留给 ECO/ECN。 |
| D8 | 角色 | 平台模板 `21105 pdm_change_manager`；业务租户按 5.65 口径复制。 |
| D9 | 授权口径 | `tenant_admin/pdm_manager/pdm_change_manager` 全量 950–955；`pdm_member` 仅 950（查询）。 |
| D10 | 审计 | 新建/修改/删除/提交/审批/关闭均 `@AuditLog`；查询不审计。 |
| D11 | 租户隔离 | 实体继承 `BaseEntity`，`tenant_id` 由租户拦截器写入；按 ID 查询跨租户返回业务错误。 |

---

## 三、接口清单

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/pdm/change/request/page` | `sys:pdm:change:list` | 按编号/状态/发起人分页 |
| GET | `/pdm/change/request/{id}` | `sys:pdm:change:list` | 详情 |
| POST | `/pdm/change/request` | `sys:pdm:change:add` | 新建草稿 |
| PUT | `/pdm/change/request/{id}` | `sys:pdm:change:edit` | 修改草稿 |
| DELETE | `/pdm/change/request/{id}` | `sys:pdm:change:delete` | 删除草稿 |
| POST | `/pdm/change/request/{id}/submit` | `sys:pdm:change:submit` | 提交（影响对象非空） |
| POST | `/pdm/change/request/{id}/approve` | `sys:pdm:change:approve` | 审批通过 |
| POST | `/pdm/change/request/{id}/reject` | `sys:pdm:change:approve` | 驳回（意见必填） |
| POST | `/pdm/change/request/{id}/close` | `sys:pdm:change:edit` | 关闭（仅通过/驳回） |

分页每页上限 200。

---

## 四、数据库变更

`V7__change_request.sql`（`bluewhale_pdm`）：

- `pdm_chg_request`：`id/tenant_id/change_no/change_reason/change_scope/impact_json/status/initiator_id/submit_time/approve_by/approve_time/approve_comment/close_time/create_time/update_time/deleted`。
- 唯一键 `uk_chg_request_no (tenant_id, change_no)`。
- 查询索引 `idx_chg_request_status (tenant_id, status, create_time)`、`idx_chg_request_initiator (tenant_id, initiator_id, create_time)`。
- 无物理外键、无 `DROP/TRUNCATE/DELETE`。

---

## 五、测试与验收

1. 状态机：草稿可改/删/提交；已提交可审批；已通过/已驳回可关闭；跨状态操作抛 `BizException`。
2. 提交校验：影响对象为空返回“影响对象不能为空”。
3. 驳回校验：审批意见为空返回“驳回必须填写审批意见”。
4. 影响对象：非法类型、缺少 ID 与编码均拒绝；合法对象序列化后可反序列化回显。
5. 分页：`size > 200` 时按 200 截断。
6. 权限迁移脚本可重复执行；权限 950–955 连续、菜单 190、角色 21105 存在。
7. 后端 `mvn -pl whale-backend/whale-apps/whale-pdm test` 全绿；前端 `lint`、`tsc -b`、`vite build` 通过。

---

## 六、提交范围

```
whale-backend/whale-apps/whale-pdm/src/main/java/com/bluewhale/pdm/change/**
whale-backend/whale-apps/whale-pdm/src/main/resources/db/migration/V7__change_request.sql
whale-backend/whale-apps/whale-pdm/src/test/java/com/bluewhale/pdm/change/**
whale-backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/migration-5.80-pdm-change.sql
whale-frontend/platform-apps/whale-pdm/src/api/change.ts
whale-frontend/platform-apps/whale-pdm/src/pages/change/**
whale-frontend/platform-apps/whale-pdm/src/App.tsx
docs/plans/2026-11-04-wave5.80-pdm-panorama.md
docs/service-catalog.md
docs/requirements/register.md
docs/devlog/2026-11-05.md
docs/reviews/2026-11-05-wave5.80-pdm-m2.md
docs/specs/2026-11-05-wave5.80-pdm-m2-ecr/**
```

禁止 `git add -A`，避免带入并行 workflow 改动。
