# Wave 5.80 M1B-2：BOM 大规模异步对比 任务清单

> 对应规格：[spec.md](./spec.md)
> 状态：**待评审 → 待执行**
> 约定：每个任务独立可验证；后端先行，前端随后；验证通过后统一提交与推送。

---

## 阶段一：数据库与基础设施

- [x] **T1.1 `V6__bom_diff_task.sql`**
  - 新建 `pdm_bom_diff_task`（`id/tenant_id/base_bom_id/target_bom_id/status/result_json/error_message/created_by/create_time/update_time/deleted`）。
  - `PRIMARY KEY (id)` + `KEY idx_bom_diff_task_created (tenant_id, created_by, create_time)`。
  - 仅 `CREATE TABLE IF NOT EXISTS`，无破坏性语句。
- [x] **T1.2 实体/Mapper/状态常量**
  - `PdmBomDiffTask extends BaseEntity`，`@TableName("pdm_bom_diff_task")`。
  - `PdmBomDiffTaskMapper extends BaseMapper`，`@Mapper`。
  - `PdmBomDiffTaskStatus`：`PENDING/RUNNING/SUCCESS/FAILED`。
- [x] **T1.3 线程池配置**
  - `PdmBomDiffConfig`：Bean `pdmDiffExecutor`，core 2 / max 4 / queue 100，`TenantContextTaskDecorator`。
  - 不启用全局 `@EnableAsync`。

## 阶段二：后端服务与接口

- [x] **T2.1 结果 VO**
  - `PdmBomDiffTaskVO`：`id/baseBomId/targetBomId/status/errorMessage/diff/createTime`。
- [x] **T2.2 服务接口**
  - `PdmBomService` 增加 `createDiffTask(baseId, targetId, userId)`、`getDiffTask(taskId)`。
- [x] **T2.3 同步对比重构**
  - 从 `diff` 抽取 `computeDiff(base, target)`；`diff` 保留双 BOM 2000 行校验与原文案。
- [x] **T2.4 异步任务执行**
  - `createDiffTask`：同产品校验 → 插入 `PENDING`（含 `createdBy`）→ 提交线程池 → 提交失败置 `FAILED`。
  - `executeDiffTask`：`RUNNING` → `computeDiff` → JSON 写 `result_json` + `SUCCESS`；异常 `FAILED` + 截断 500 字符。
  - `getDiffTask`：不存在抛 400；`SUCCESS` 反序列化 `diff`，解析失败抛“对比结果不可用”。
- [x] **T2.5 控制器**
  - `POST /pdm/bom/diff/tasks`（`baseId/targetId` 查询参数，取用户身份）。
  - `GET /pdm/bom/diff/tasks/{id}`。
  - 均 `@RequirePermission("sys:pdm:bom:list")`，不写审计。
- [x] **T2.6 后端测试**
  - 表元数据初始化增加 `PdmBomDiffTask`。
  - 新增 `@Mock PdmBomDiffTaskMapper`、`@Mock Executor`。
  - 用例：异步成功入库、失败回显、任务不存在；同步超限用例保持通过。
  - 执行 `mvn -pl backend/whale-apps/whale-pdm test`。

## 阶段三：前端降级与轮询

- [x] **T3.1 API 层**
  - `part.ts` 增加 `BomDiffTaskStatus`、`BomDiffTask`、`createBomDiffTask`、`getBomDiffTask`。
- [x] **T3.2 对比交互**
  - `BomEditor.tsx` 的 `handleDiff` 在超限错误时创建异步任务并轮询（2 秒 × 30 次）。
  - `SUCCESS` 复用对比表格；`FAILED` 提示原因；超时提示重试。
  - `diffLoading` 覆盖完整轮询周期。
- [x] **T3.3 前端验证**
  - `pnpm --filter whale-pdm lint`
  - `pnpm --filter whale-pdm build`

## 阶段四：文档、提交与推送

- [x] **T4.1 计划与报告**
  - 更新 `docs/plans/2026-11-04-wave5.80-pdm-panorama.md` §7.1：M1B-2 完成，移除异步遗留。
  - 更新 `docs/reviews/2026-11-05-wave5.80-pdm-m1b.md`：追加 M1B-2 实现与验证结论。
- [x] **T4.2 提交**
  - `git commit --only -- <spec.md §八 列出的路径>`；禁止 `git add -A`。
  - 提交信息：`feat(pdm): BOM 大规模异步对比任务`。
- [x] **T4.3 推送**
  - 检查 `git remote -v` 与当前分支。
  - `git push origin HEAD`；回复中说明可能同时推送本地已有的并行提交。

---

## 完成定义（DoD）

1. 同步对比行为与文案不变，异步任务可创建、可轮询、可失败回显。
2. 后端测试全绿，前端 lint/build 全绿。
3. 仅 PDM 与本次 docs 进入提交，工作区无额外遗留。
4. 分支已推送至 `origin`。
