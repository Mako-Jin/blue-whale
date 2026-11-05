# Wave 5.80 M2-3：PDM 变更单（ECO）任务清单

> 对应规格：[spec.md](./spec.md)
> 状态：**已完成（2026-11-05）**
> 约定：后端先行，权限与前端随后；验证通过后统一提交与推送。

---

## 阶段一：后端

- [x] **T1.1 迁移 `V8__change_order.sql`**
  - `pdm_chg_order`：唯一键 `uk_chg_order_no`，索引 `idx_chg_order_status`/`idx_chg_order_ecr`。
  - `pdm_chg_task`：索引 `idx_chg_task_eco`/`idx_chg_task_sort`。
- [x] **T1.2 实体/Mapper/状态常量**
  - `PdmChgOrder`、`PdmChgTask` 继承 `BaseEntity`。
  - `PdmChgOrderStatus`、`PdmChgTaskStatus` 常量化。
- [x] **T1.3 DTO/VO**
  - 查询、保存、评估、任务保存请求；ECO 视图内嵌任务视图。
- [x] **T1.4 服务与控制器（17 端点）**
  - 状态机与业务校验统一在服务层；写操作 `@AuditLog`。
- [x] **T1.5 单元测试 `PdmChgOrderServiceImplTest`**
  - 用例覆盖规格 §五 1–9。

## 阶段二：权限与菜单

- [x] **T2.1 `migration-5.80-pdm-eco.sql`**
  - 权限 956–962；菜单 191；角色 21105 追加授权。
  - 授权：`tenant_admin/pdm_manager/pdm_change_manager` 全量；`pdm_member` 仅 956。
  - 幂等：全部 `NOT EXISTS`；无删除语句。

## 阶段三：前端

- [x] **T3.1 `src/api/changeOrder.ts`**：类型与 17 个函数。
- [x] **T3.2 `src/pages/change/order.tsx`**：列表 + 状态筛选 + 新建（选已通过 ECR）+ 评估 + 任务维护 + 提交/审批/执行/完成/取消/关闭/删除。
- [x] **T3.3 `App.tsx`**：菜单 `/change/order` 与路由。
- [x] **T3.4 前端验证**：`lint`、`build`。

## 阶段四：文档、提交与推送

- [x] **T4.1 计划 §八状态更新**（M2-3 完成、M2-9/M2-10 部分完成）。
- [x] **T4.2 检查报告 `docs/reviews/2026-11-05-wave5.80-pdm-m2-eco.md`**。
- [x] **T4.3 devlog / service-catalog / requirements register 同步**。
- [x] **T4.4 后端测试全绿**：`mvn -pl backend/whale-apps/whale-pdm test`。
- [x] **T4.5 提交并推送**：仅本次 PDM、权限脚本与文档。

---

## 完成定义（DoD）

1. ECO 十七端点可用，状态机非法流转全部拒绝。
2. 未审批执行返回含 `CHANGE_NOT_APPROVED` 的业务错误。
3. 权限 956–962 / 菜单 191 幂等迁移可重复执行。
4. 前端 ECO 页可查询、新建、评估、维护任务、提交、审批、执行、完成、取消、关闭。
5. 后端测试与前端 lint/build 全绿，仅本次范围进入提交并推送至 `origin`。
