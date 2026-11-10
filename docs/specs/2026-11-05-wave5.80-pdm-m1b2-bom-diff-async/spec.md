# Wave 5.80 M1B-2：BOM 大规模异步对比 规格说明

> 状态：**待评审**（Spec Mode）
> 日期：2026-11-05
> 上游计划：[Wave 5.80 PDM 全景实施计划](../../plans/2026-11-04-wave5.80-pdm-panorama.md) §7.1 M1B-2
> 上游报告：[Wave 5.80 PDM M1B 交付报告](../../reviews/2026-11-05-wave5.80-pdm-m1b.md)
> 既有实现：BOM 派生/同步对比/Excel 导出（提交 `9c328079`）
> 边界：对比只读，不写 BOM 结构；不改审批/ECR 流程；不引入消息队列与独立服务。

---

## 一、目标与范围

### 1.1 目标

1. 为超过 2000 行的 BOM 提供异步对比，解除在线对比 2000 行上限带来的功能空白。
2. 同步对比 `GET /pdm/bom/diff` 行为保持不变（仍保留 2000 行上限），避免存量前端与调用方回归。
3. 新增对比任务持久化表与查询接口，任务状态可轮询、失败原因可回显。
4. 前端在同步对比提示行数超限时自动降级为异步任务并轮询结果，成功后复用既有对比表格展示。

### 1.2 不做（本阶段）

- 任务取消、任务重试、任务列表页、任务结果分页导出。
- 跨租户/跨用户的任务隔离策略（本阶段仅按 `sys:pdm:bom:list` 权限校验）。
- MQ、分布式任务调度、Redis 任务状态（仍为单库单服务内存线程池执行）。
- 真实 MySQL 环境下的任务状态并发压测。

---

## 二、关键决策（Decision-Complete）

| 编号 | 决策 | 结论 |
|---|---|---|
| D1 | 迁移版本号 | 新增 `V6__bom_diff_task.sql`；V5 已被派生视图迁移占用，严格递增；脚本无 `DROP/TRUNCATE/DELETE`，仅 `CREATE TABLE IF NOT EXISTS`。 |
| D2 | 任务落库 | 新表 `pdm_bom_diff_task`；状态 `PENDING/RUNNING/SUCCESS/FAILED`，结果 `result_json`（LONGTEXT），失败原因 `error_message`（VARCHAR(500)）。 |
| D3 | 实体与 Mapper | `PdmBomDiffTask` 继承 `BaseEntity`（`id/createTime/updateTime/deleted` 自动），`PdmBomDiffTaskMapper extends BaseMapper`，`@Mapper` 注解。 |
| D4 | 状态常量 | 新增 `PdmBomDiffTaskStatus`（`PENDING/RUNNING/SUCCESS/FAILED`），与 `PdmBomStatus` 同包同风格，禁止裸字符串散落。 |
| D5 | 执行线程池 | 新增 `PdmBomDiffConfig`，Bean 名 `pdmDiffExecutor`（core 2 / max 4 / queue 100 / 前缀 `pdm-bom-diff-`），挂 `TenantContextTaskDecorator`；`@EnableAsync` 不启用，服务通过构造器注入 `Executor`（字段名与 Bean 名一致，按名称匹配）。 |
| D6 | 同步/异步边界 | `PdmBomServiceImpl#diff` 仍先校验双 BOM 行数 ≤ 2000 后调用 `computeDiff`；异步任务直接调用 `computeDiff`，不做行数校验。 |
| D7 | 结果序列化 | 使用静态 `ObjectMapper`（`PdmBomDiffVO` 仅含字符串/数字，无时间类型），写入 `result_json`；写入或读取失败按失败任务处理。 |
| D8 | 创建接口 | `POST /pdm/bom/diff/tasks?baseId=&targetId=`；`@RequirePermission("sys:pdm:bom:list")`；不写审计日志（轮询接口若审计会放大日志量）。 |
| D9 | 查询接口 | `GET /pdm/bom/diff/tasks/{id}`；`@RequirePermission("sys:pdm:bom:list")`；`SUCCESS` 时返回解析后的 `diff`，其余状态 `diff` 为空。 |
| D10 | 创建前置校验 | 两个 BOM 必须存在、必须同一 `productId`；复用 `requireBom` 与既有错误文案，不做行数校验。 |
| D11 | 提交失败处理 | 线程池拒绝/提交异常时任务置 `FAILED` 并写入截断后的错误信息，接口仍返回任务视图，避免请求 500。 |
| D12 | 执行失败处理 | 任务执行异常置 `FAILED`，`error_message = "对比失败: " + 异常信息`，截断 500 字符；不抛出到线程池外。 |
| D13 | 创建人 | 控制器通过 `PdmBomRequestContext.requireUserId(request)` 取用户，落 `created_by`；缺失身份沿用网关 401 语义。 |
| D14 | 任务不存在 | `getDiffTask` 查不到任务时抛 `BizException(ErrorCode.PARAM_ERROR, "对比任务不存在")`；结果 JSON 解析失败抛 `BizException(ErrorCode.PARAM_ERROR, "对比结果不可用")`。 |
| D15 | 前端降级触发 | 仅在同步对比异常信息包含 `2000` 或 `超过` 时创建异步任务；其他错误原样提示，不静默改道。 |
| D16 | 轮询策略 | 间隔 2 秒、最多 30 次（约 60 秒）；`SUCCESS` 立即渲染并提示完成，`FAILED` 提示 `errorMessage`，超时提示“对比超时，请稍后重试”；轮询期间沿用 `diffLoading` 禁用按钮。 |
| D17 | 无破坏性变更 | 表、接口、前端入口均为新增；不修改既有请求/响应字段，不调整权限号段。 |

---

## 三、数据库变更

### 3.1 `V6__bom_diff_task.sql`（bluewhale_pdm）

```sql
CREATE TABLE IF NOT EXISTS pdm_bom_diff_task (
    id            BIGINT       NOT NULL COMMENT '主键',
    tenant_id     BIGINT       NOT NULL COMMENT '租户ID',
    base_bom_id   BIGINT       NOT NULL COMMENT '基线 BOM ID',
    target_bom_id BIGINT       NOT NULL COMMENT '目标 BOM ID',
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/SUCCESS/FAILED',
    result_json   LONGTEXT     DEFAULT NULL COMMENT '对比结果 JSON（PdmBomDiffVO）',
    error_message VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
    created_by    BIGINT       DEFAULT NULL COMMENT '创建人',
    create_time   DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_time   DATETIME     DEFAULT NULL COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_bom_diff_task_created (tenant_id, created_by, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM BOM 异步对比任务';
```

- 幂等：`CREATE TABLE IF NOT EXISTS`；不新增索引重建语句。
- `tenant_id` 由既有租户拦截器写入，实体不重复声明该字段。
- 校验建议：`SHOW CREATE TABLE pdm_bom_diff_task;`

---

## 四、后端变更

### 4.1 新增文件

| 文件 | 内容 |
|---|---|
| `whale-backend/whale-apps/whale-pdm/src/main/resources/db/migration/V6__bom_diff_task.sql` | 上文 DDL |
| `.../part/entity/PdmBomDiffTask.java` | 字段：`baseBomId/targetBomId/status/resultJson/errorMessage/createdBy`；`@TableName("pdm_bom_diff_task")` |
| `.../part/mapper/PdmBomDiffTaskMapper.java` | `@Mapper interface ... extends BaseMapper<PdmBomDiffTask>` |
| `.../part/support/PdmBomDiffTaskStatus.java` | 四个状态常量 + 私有构造 |
| `.../part/vo/PdmBomDiffTaskVO.java` | `id/baseBomId/targetBomId/status/errorMessage/diff/createTime` |
| `.../config/PdmBomDiffConfig.java` | `DIFF_EXECUTOR = "pdmDiffExecutor"` + `ThreadPoolTaskExecutor` Bean |

### 4.2 修改 `PdmBomService.java`

新增：

```java
PdmBomDiffTaskVO createDiffTask(Long baseId, Long targetId, Long userId);
PdmBomDiffTaskVO getDiffTask(Long taskId);
```

### 4.3 修改 `PdmBomServiceImpl.java`

1. 常量：`MAX_DIFF_LINES` 注释改为“在线对比上限，超过走异步任务”；新增 `MAX_ERROR_LENGTH = 500`、静态 `ObjectMapper`。
2. 字段：新增 `PdmBomDiffTaskMapper bomDiffTaskMapper`、`Executor pdmDiffExecutor`（Lombok 构造器注入，字段名即 Bean 名）。
3. `diff(baseId, targetId)`：保留存在性/同产品/双 BOM 行数校验，改为 `return computeDiff(base, target);`。
4. 抽取 `computeDiff(PdmBom base, PdmBom target)`：原 415–474 行的比对逻辑整体迁入，不含行数校验。
5. `createDiffTask`：校验 → 插入 `PENDING` 任务（含 `createdBy`）→ `pdmDiffExecutor.execute(() -> executeDiffTask(id))` → 提交异常时置 `FAILED` → `return getDiffTask(id)`。
6. `executeDiffTask`：置 `RUNNING` → `computeDiff` → 序列化写 `result_json`、置 `SUCCESS` → 异常置 `FAILED` 并截断错误 → `updateById`。
7. `getDiffTask`：查任务 → 组装 VO → `SUCCESS` 时反序列化 `diff`（解析异常按 D14 处理）。
8. 新增 `truncate(String, int)` 私有工具，仅在错误信息写入处使用。

### 4.4 修改 `PdmBomController.java`

```java
@PostMapping("/diff/tasks")
public Result<PdmBomDiffTaskVO> createDiffTask(@RequestParam Long baseId,
                                               @RequestParam Long targetId,
                                               HttpServletRequest request);

@GetMapping("/diff/tasks/{id}")
public Result<PdmBomDiffTaskVO> getDiffTask(@PathVariable Long id);
```

- 两者 `@RequirePermission("sys:pdm:bom:list")`，无 `@AuditLog`。
- 路径与既有 `GET /diff`、`GET /{id}` 不冲突（Spring 按最长匹配解析）。

---

## 五、前端变更

### 5.1 `src/api/part.ts`

新增类型与函数：

```ts
export type BomDiffTaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';

export interface BomDiffTask {
  id: string;
  baseBomId: string;
  targetBomId: string;
  status: BomDiffTaskStatus;
  errorMessage?: string;
  diff?: BomDiff;
  createTime?: string;
}

export function createBomDiffTask(baseId: string, targetId: string): Promise<BomDiffTask>;
export function getBomDiffTask(id: string): Promise<BomDiffTask>;
```

- `createBomDiffTask` 用 `http.post` 拼接查询串（`encodeURIComponent`），因现有 `http.post` 不支持 query 参数对象。

### 5.2 `src/pages/bom/BomEditor.tsx`

1. 导入 `createBomDiffTask`、`getBomDiffTask` 及 `BomDiffTask` 类型。
2. 新增常量 `DIFF_POLL_INTERVAL = 2000`、`DIFF_POLL_MAX_ATTEMPTS = 30`。
3. `handleDiff`：
   - 先尝试 `diffBoms`；
   - 异常信息包含 `2000` 或 `超过` → `message.info('BOM 行数较多，已转异步对比，请稍候')` → `createBomDiffTask` → 轮询；
   - 轮询 `SUCCESS` → `setDiffResult(task.diff)` + 成功提示；`FAILED` → `message.error(errorMessage)`；超时 → 抛出“对比超时，请稍后重试”；
   - 其他异常保持原样 `message.error`；
   - `finally` 恢复 `diffLoading`。
4. 不改动既有对比结果表格、导出、派生逻辑。

---

## 六、测试与验收

### 6.1 后端单元测试（`PdmBomServiceImplTest`）

1. `@BeforeAll` 增加 `PdmBomDiffTask` 表元数据初始化。
2. 新增 `@Mock PdmBomDiffTaskMapper`、`@Mock Executor pdmDiffExecutor`。
3. 用例：
   - `createDiffTaskShouldRunAndStoreSuccess`：线程池 mock 同步执行 Runnable；行数 2001 仍成功；断言任务 `SUCCESS`、`resultJson` 非空、VO `diff.added/changed` 正确。
   - `getDiffTaskShouldReturnFailure`：`FAILED` 任务回显 `errorMessage`，`diff` 为空。
   - `getDiffTaskShouldRejectMissing`：不存在抛 `BizException(PARAM_ERROR)`。
4. 回归：既有 `diffShouldRejectOversizedBom` 等 90 个用例保持通过。

### 6.2 前端

- `pnpm --filter whale-pdm lint`
- `pnpm --filter whale-pdm build`

### 6.3 验收标准

1. BOM 行数 > 2000 时同步接口仍返回 400（文案含“2000”）。
2. 创建异步任务返回 `PENDING/RUNNING/SUCCESS` 之一；轮询至 `SUCCESS` 后 `diff` 结构与同步结果一致（同一 `computeDiff`）。
3. 任务执行失败可在 `errorMessage` 查看且接口不 500。
4. 前端在超限场景自动降级并渲染结果；非超限错误不改道。
5. 后端测试与前端 lint/build 全绿。

---

## 七、风险与兼容

| 风险 | 处理 |
|---|---|
| 大量并发创建任务占用线程池 | 队列 100 + 拒绝时任务置 `FAILED`，不阻塞请求线程 |
| 结果 JSON 过大（十万行级） | 本阶段接受 LONGTEXT；后续可改结果分页/压缩 |
| 任务表无限增长 | 本阶段不做清理，后续运维按 `create_time` 归档 |
| 既有同步对比回归 | `diff` 仅抽取方法，上限与错误文案保持原样 |
| 前端轮询对网关压力 | 2 秒 × 30 次上限，完成后立即停止 |

---

## 八、提交范围（仅 PDM + 本次 docs）

```
whale-backend/whale-apps/whale-pdm/src/main/resources/db/migration/V6__bom_diff_task.sql
whale-backend/whale-apps/whale-pdm/src/main/java/com/bluewhale/pdm/config/PdmBomDiffConfig.java
whale-backend/whale-apps/whale-pdm/src/main/java/com/bluewhale/pdm/part/entity/PdmBomDiffTask.java
whale-backend/whale-apps/whale-pdm/src/main/java/com/bluewhale/pdm/part/mapper/PdmBomDiffTaskMapper.java
whale-backend/whale-apps/whale-pdm/src/main/java/com/bluewhale/pdm/part/support/PdmBomDiffTaskStatus.java
whale-backend/whale-apps/whale-pdm/src/main/java/com/bluewhale/pdm/part/vo/PdmBomDiffTaskVO.java
whale-backend/whale-apps/whale-pdm/src/main/java/com/bluewhale/pdm/part/service/PdmBomService.java
whale-backend/whale-apps/whale-pdm/src/main/java/com/bluewhale/pdm/part/service/impl/PdmBomServiceImpl.java
whale-backend/whale-apps/whale-pdm/src/main/java/com/bluewhale/pdm/part/controller/PdmBomController.java
whale-backend/whale-apps/whale-pdm/src/test/java/com/bluewhale/pdm/part/service/impl/PdmBomServiceImplTest.java
whale-frontend/platform-apps/whale-pdm/src/api/part.ts
whale-frontend/platform-apps/whale-pdm/src/pages/bom/BomEditor.tsx
docs/plans/2026-11-04-wave5.80-pdm-panorama.md
docs/reviews/2026-11-05-wave5.80-pdm-m1b.md
docs/specs/2026-11-05-wave5.80-pdm-m1b2-bom-diff-async/spec.md
docs/specs/2026-11-05-wave5.80-pdm-m1b2-bom-diff-async/tasks.md
```

- 使用 `git commit --only -- <paths>` 提交，禁止 `git add -A`，避免带入并行 ERP/MES 改动。
- 提交后 `git push origin HEAD`；推送可能携带本地已有并行提交（当前分支领先 33 个提交）。

---

## 九、审批

本规格需用户确认后进入执行；确认即按 `tasks.md` 顺序实施、验证、提交与推送。
