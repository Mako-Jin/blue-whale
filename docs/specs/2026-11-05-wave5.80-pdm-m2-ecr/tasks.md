# Wave 5.80 M2-2：PDM 变更申请（ECR）任务清单

> 对应规格：[spec.md](./spec.md)
> 状态：**已完成**
> 约定：后端先行，权限与前端随后；验证通过后统一提交与推送。

---

## 阶段一：后端

- [x] **T1.1 迁移 `V7__change_request.sql`**
- [x] **T1.2 实体/Mapper/状态常量/影响对象类型**
- [x] **T1.3 服务与控制器（9 端点）**
- [x] **T1.4 单元测试 `PdmChgRequestServiceImplTest`**
  - 表元数据初始化 `PdmChgRequest`。
  - 用例：新建草稿、提交缺影响对象、非草稿不可修改、审批状态流转、驳回意见必填、关闭口径、非法影响对象、分页上限。

## 阶段二：权限与菜单

- [x] **T2.1 `migration-5.80-pdm-change.sql`**
  - 权限 950–955；角色 21105 `pdm_change_manager` + 业务租户复制；菜单 190。
  - 授权：`tenant_admin/pdm_manager/pdm_change_manager` 全量；`pdm_member` 仅 950。
  - 幂等：全部 `NOT EXISTS`；无删除语句。

## 阶段三：前端

- [x] **T3.1 `src/api/change.ts`**：类型与 9 个函数。
- [x] **T3.2 `src/pages/change/index.tsx`**：列表 + 状态筛选 + 新建/编辑抽屉 + 提交/审批/关闭。
- [x] **T3.3 `App.tsx`**：菜单 `/change` 与路由。
- [x] **T3.4 前端验证**：`lint`、`tsc -b`、`vite build`。

## 阶段四：文档、提交与推送

- [x] **T4.1 计划 §八状态更新**（M2-2 完成、M2-9/M2-10 部分完成）。
- [x] **T4.2 检查报告 `docs/reviews/2026-11-05-wave5.80-pdm-m2.md`**。
- [x] **T4.3 devlog / service-catalog / requirements register 同步**。
- [x] **T4.4 后端测试全绿**：`mvn -pl backend/whale-apps/whale-pdm test`（110 tests / 0 failures）。
- [x] **T4.5 提交并推送**：`a42aadfd feat(pdm): Wave 5.80 M2-2 change request and approval flow`，已推送 `origin/main`。

---

## 完成定义（DoD）

1. ECR 九端点可用，状态机非法流转全部拒绝。
2. 权限 950–955 / 菜单 190 / 角色 21105 幂等迁移可重复执行。
3. 前端变更页可查询、新建、编辑、提交、审批、关闭。
4. 后端测试与前端 lint/build 全绿。
5. 仅 PDM、权限脚本与本次文档进入提交并推送至 `origin`。
