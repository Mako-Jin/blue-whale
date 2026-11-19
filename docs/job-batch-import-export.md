# whale-job 运维效率增强：任务批量操作与导入导出

> 对应扩展路线图第 7 项。目标是让管理端在跨环境搬运任务定义、批量治理历史任务时
> 不必逐个点，同时保证「部分失败不影响其余条目」。
> 不改调度语义、不改表结构，只新增 4 个接口。

## 1. 现状与缺口

| 场景 | 现状 | 缺口 |
|---|---|---|
| 批量启停 | `PUT /job/info/{id}/status` 单条 | 迁移/演练时要逐条点，且中途失败无法定位是哪条 |
| 批量删除 | `DELETE /job/info/{id}` 单条 | 清理测试数据逐条删，效率低 |
| 环境搬运 | 无 | 测试环境调好的任务与依赖要手工在预发/生产重建 |
| 数据体检 | `GET /job/graph` 只读 | 拿到图但没有可回灌的载体 |

## 2. 第 7 项：接口设计

### 2.1 批量启停

`POST /job/info/batch-status`（权限 `sys:job:edit`）

```json
{"jobIds": [1, 2, 3], "status": 0}
```

响应（HTTP 200，逐条结果在体内）：

```json
{"successCount": 2, "failures": [{"id": 3, "reason": "资源不存在"}]}
```

### 2.2 批量删除

`POST /job/info/batch-delete`（权限 `sys:job:delete`）

```json
{"jobIds": [1, 2, 3]}
```

响应结构同上。删除为逻辑删除，与单条 `DELETE` 语义完全一致（先取消调度再删）。

### 2.3 语义约定（批量）

- **逐条复用单条接口的服务方法**（`changeStatus` / `delete`），不另写批量 SQL：
  单条路径上的存在性校验、启停清零 `failCount`、事务提交后重新注册/取消调度全部保留；
- **不整批回滚**：一条不存在或一条调度失败不影响其余条目。若整体加一个事务，
  一条失败会让整批回滚，与「批量治理」的使用预期相反；
- 入参 `jobIds` 空数组、含空值、重复 ID 均视为参数错误（空数组）或自动去重（重复绝不报错）；
- `jobIds` 上限 200 条，避免单次请求长时间占用 Web 线程；
- 失败原因取 `BizException.message`（如「资源不存在」），非业务异常取异常消息。

### 2.4 导出

`GET /job/info/export?jobGroup=&jobIds=1,2,3`（权限 `sys:job:list`）

- `jobGroup` 与 `jobIds` 均可选，同时传入时取交集（只导出该分组内且 ID 命中的任务）；
- `jobIds` 支持逗号分隔或重复参数两种写法，用于管理端「仅导出选中任务」。

```json
{
  "version": "1.0",
  "exportedAt": "2026-11-19T10:20:30",
  "jobs": [
    {"jobName": "CRM 商务订单下发 ERP", "jobGroup": "CRM",
     "executorApp": "whale-crm", "jobCode": "crm.order-push",
     "cronExpression": "0 0/10 * * * ?", "handlerBean": "httpJobHandler",
     "handlerParam": "{\"url\":\"/internal/job/order-push\"}",
     "misfirePolicy": "SMART", "maxRetry": 2, "retryIntervalSeconds": 60,
     "shardTotal": 1, "timeoutSeconds": 300, "failThreshold": 5,
     "status": 1, "remark": ""}
  ],
  "dependencies": [
    {"from": {"executorApp": "whale-crm", "jobCode": "crm.order-pull", "jobName": "CRM 订单拉取"},
     "to":   {"executorApp": "whale-crm", "jobCode": "crm.order-push", "jobName": "CRM 商务订单下发 ERP"}}
  ]
}
```

- **只导出可移植字段**：不含 `id` / `tenantId` / `failCount` / `createTime` / `updateTime`。
  `id` 与租户是环境相关的；`failCount` 是运行态计数，搬运过来会让熔断带着别处的失败历史。
- **依赖边用业务标识而非 ID**：`{executorApp, jobCode, jobName}` 作为任务引用，
  跨环境 ID 必然不同，用 ID 导出等于不可用。`jobName` 同时给出，便于人工核对与 `jobCode` 缺失时兜底匹配。
- 指定 `jobGroup` 只导出该分组；依赖边只保留两端都在导出集合内的（与图接口口径一致）。
- 任务按 ID 升序、依赖边按 (上游ID, 下游ID) 排序，输出稳定便于 diff。

### 2.5 文件下载

`GET /job/info/export/download?jobGroup=&jobIds=`（权限 `sys:job:list`）

- 参数与导出接口一致，返回 `application/json` 附件，`Content-Disposition: attachment; filename="job-export-yyyyMMdd-HHmmss.json"`；
- 内容即导出接口的 `data` 部分（任务 + 依赖），可直接另存后作为导入文件；
- 下载接口直接返回文件流，不再套 `Result` 包装；错误仍走统一异常处理返回 JSON。

### 2.6 导入

`POST /job/info/import`（权限 `sys:job:add`）

```json
{
  "conflictStrategy": "SKIP",
  "jobs": [ ... 与导出的 jobs 元素同构 ... ],
  "dependencies": [ ... 与导出的 dependencies 同构 ... ]
}
```

响应：

```json
{"createdCount": 2, "updatedCount": 1, "skippedCount": 0, "dependencyCount": 1,
 "failures": [{"target": "crm.order-push", "reason": "执行器 Bean 不存在"}]}
```

**匹配键（决定「已存在」与否）**

| 条件 | 匹配键 |
|---|---|
| `jobCode` 非空 | `executorApp + jobCode`（`executorApp` 为空时视为空串） |
| `jobCode` 为空 | `jobName` |

同一个文件里出现重复匹配键，按出现顺序处理：SKIP 下第二条被跳过，OVERWRITE 下后一条覆盖前一条。

**冲突策略**

- `SKIP`（默认）：已存在则跳过，计入 `skippedCount`，不修改任何字段；
- `OVERWRITE`：已存在则按导入内容更新（复用 `update`，未提供的字段保留原值），计入 `updatedCount`；
- 其他取值返回 400 参数错误，避免拼写错误被静默当成 SKIP。

**失败聚合**

- 每条任务独立处理，一条失败记录 `{target, reason}` 后继续下一条，**不整批回滚**；
- `target` 优先取 `executorApp:jobCode`，否则取 `jobName`；
- 任务创建/更新走既有 `create` / `update` 校验链：cron 合法性、执行器 Bean 是否存在、
  字段长度与取值范围全部与手工新增一致，不存在「导入通道绕过校验」的入口。

**依赖还原**

1. 任务全部落库后再处理依赖，保证同文件内新建的任务可作为端点被解析；
2. 按下游聚合：同一 `to` 的多条边合并为一次「全量替换上游」调用，避免逐条保存互相覆盖；
3. 端点解析失败（找不到对应任务）记为一条失败，`target` 为下游标识，`reason` 指明哪一端无法解析；
4. 保存复用 `JobDependencyService.saveDependencies`：自依赖、环检测、保存后重新调度全部沿用；
   某条下游的依赖保存失败（如成环）只影响该下游，其余照常。

**不整体加事务**：导入的定位是「尽可能多地搬过去并告诉你哪些没成功」，
整体事务会让一条坏数据把整份文件全部回滚，反而放大排障成本。

## 3. 边界与取舍

| 问题 | 选择 | 理由 |
|---|---|---|
| 导入是否覆盖 `status` | 是，按文件值导入 | 环境搬运要连「默认是否启用」一起搬；不想启用就把导出文件的 status 改 0 |
| 是否导入 `failCount` | 否 | 运行态计数跨环境无意义，导入任务 `failCount` 一律从 0 起 |
| 是否校验依赖完整 | 只校验端点可解析 | 依赖边允许指向本次未导入的既有任务，按匹配键解析即可 |
| 权限 | 批量启停 `sys:job:edit`、批量删除 `sys:job:delete`、导入 `sys:job:add`、导出 `sys:job:list` | 与单条接口完全一致，不引入新的权限码 |
| 审计 | 批量启停/删除/导入标 `@AuditLog` | 批量操作影响面更大，必须留痕 |

## 4. 测试要点

- 批量启停：2 成功 1 不存在 → `successCount=2`，失败明细含该 ID 与原因；重复 ID 只执行一次。
- 批量删除：不存在的 ID 不影响其余任务被删除。
- 导出：分组过滤只含组内任务与两端都在组内的边；导出字段不含 `id` / `failCount`；边方向为上游 → 下游。
- 导入 SKIP：已存在任务被跳过且不调用 update；新建任务计数正确。
- 导入 OVERWRITE：已存在任务调用 update 且状态按文件值写入。
- 导入依赖：端点缺失记失败明细；同一下游多条上游合并为一次保存；下游依赖保存失败不影响其他下游。
- 导入非法策略：抛参数错误且不落库。

## 5. 兼容与回滚

- 纯新增接口与 DTO/VO，无 DDL、无配置项、无调度行为变更；回滚直接回退代码即可。
- 导入文件版本号当前仅 `1.0`；后续字段扩展时保持向后兼容（新增字段可选、旧字段语义不变），
  读到不认识的 `version` 由调用方决定，本接口不额外拦截。
