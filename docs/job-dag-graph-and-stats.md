# whale-job 运维效率增强：DAG 拓扑图接口与执行统计

> 对应扩展路线图第 5、6 项。两项都是只读接口，不改调度语义、不加表结构，
> 目标是让管理端能「看见」依赖拓扑与执行质量，而不是只能逐条翻日志。

## 1. 现状与缺口

| 项 | 现状 | 缺口 |
|---|---|---|
| 依赖拓扑 | 只有 `GET /job/v1/info/{id}/dependencies` 单点上下游 | 无法一次拿到全量节点与边，前端画不了图 |
| 环检测 | `saveDependencies` 只做「本次新增边」的增量环检测 | 全量编辑 / 数据修复后缺少整图校验入口 |
| 执行统计 | `GET /job/v1/log/page` 只有明细分页 | 没有按天成功率、耗时 P95、失败 TopN |

## 2. 第 5 项：DAG 拓扑图

### 2.1 全量图接口

`GET /job/v1/graph?jobGroup=`（权限 `sys:job:list`）

```json
{
  "nodes": [
    {"id": 1, "jobName": "执行日志清理", "jobGroup": "SYSTEM", "status": 1,
     "executorApp": "whale-file", "jobCode": "file.share-cleanup",
     "cronExpression": "0 0 3 * * ?", "shardTotal": 1,
     "timeoutSeconds": 0, "failThreshold": 0, "failCount": 0}
  ],
  "edges": [{"from": 1, "to": 2}]
}
```

- 边的方向固定为 **上游 → 下游**（`from=upstreamJobId`，`to=jobId`），与依赖表语义一致。
- `jobGroup` 为空返回全量；指定分组时节点按分组过滤，**只有两端都在节点集合内才保留边**。
- 节点使用精简的 `JobGraphNodeVO` 而非 `JobInfoVO`：图渲染不需要 `handlerParam` 等大字段，
  也不需要逐条解析 cron 计算下次触发时间（避免 N 次 CronExpression 解析）。
- 节点顺序按 ID 升序、边按 (from,to) 升序，保证接口输出稳定，便于前端 diff 与快照测试。

### 2.2 整图环检测

`GET /job/v1/graph/validate`（权限 `sys:job:list`）

```json
{"acyclic": false, "cycleJobIds": [2, 3, 2]}
```

- 对全量节点做三色 DFS（未访问 / 访问中 / 已完成），发现回边即截取当前栈上的环并返回；
- `cycleJobIds` 首尾为同一节点，空数组表示无环；
- 只读接口，供前端「保存前校验」「数据体检」使用；`saveDependencies` 仍保留原有增量检测，
  两者互补，不改变保存行为。

## 3. 第 6 项：执行统计

`GET /job/v1/log/stats?jobId=&jobGroup=&from=&to=`（权限 `sys:job:log:list`）

```json
{
  "from": "2026-11-11T00:00:00",
  "to": "2026-11-18T23:59:59",
  "sampled": false,
  "totalCount": 128,
  "daily": [
    {"date": "2026-11-17", "successCount": 18, "failureCount": 2,
     "avgDurationMillis": 320, "p95DurationMillis": 1200}
  ],
  "failureTop": [{"jobId": 21, "jobName": "CRM 商务订单下发 ERP", "failureCount": 9}],
  "durationTop": [{"jobId": 31, "jobName": "File 到期分享清理", "avgDurationMillis": 5300}],
  "errorTypeTop": [
    {"errorType": "BizException", "count": 7, "sampleMessage": "BizException: 参数错误"},
    {"errorType": "HttpServerErrorException$BadGateway", "count": 2, "sampleMessage": "HttpServerErrorException$BadGateway: 502"}
  ]
}
```

### 3.1 口径

- 只统计已结束的执行（`status=1/-1`），执行中（`status=0`）不计入，避免把未完成任务的耗时刻意拉高；
- 时间维度按 `start_time` 的**服务端本地日期**分桶（与 `job_log` 落库时区一致）；
- 耗时 = `end_time - start_time`（毫秒）；日 P95 用「升序第 `ceil(n×0.95)` 个」的离散口径；
- `failureTop` 按失败次数降序取前 10；`durationTop` 按平均耗时降序取前 10；
- `errorTypeTop` 按异常类型聚合失败次数取前 10：类型取自 `error_msg` 首段（调度侧写入格式
  `异常简单类名: 消息`），不符合类名形态的记录归入 `OTHER`，避免把整段错误信息当成类型导致基数爆炸；
  聚合只回捞失败行（上限 2000 条、按开始时间倒序），示例信息截断 200 字；
- 指定 `jobId` 时 TopN 无意义，三项返回空数组；
- `jobGroup` 先按 `job_info` 解析出任务 ID 集合再过滤 `job_log`（`job_log` 不冗余分组）；
- 默认窗口最近 7 天，最大跨度 90 天，避免误传超大区间拖库；
- 单次最多取 `100000` 条明细做内存聚合，超出置 `sampled=true`（前端提示「样本截断」）。
  数据量继续增长时改由 Prometheus 指标或按天汇总表承接，本接口口径保持不变。

### 3.2 为什么不直接 SQL GROUP BY

P95 在 MySQL 8 没有原生百分位函数，用窗口函数或 `GROUP_CONCAT` 都会让 SQL 与租户插件、
逻辑删除的组合变得脆弱；当前任务量级下取明细在应用内一次算完（计数/均值/P95/TopN 共用一趟扫描）
更简单也可测。`sampled` 给了数据量增长后的降级说明。

## 4. 测试要点

- 图：分组过滤只保留组内节点与两端均在组内的边；无分组返回全量；边方向为上游→下游。
- 环检测：无环返回空；构造 A→B→C→A 返回闭环路径；孤立节点不影响结果。
- 统计：跨天分桶正确；成功/失败计数正确；P95 与平均值按离散口径计算；
  `jobId` 过滤时 TopN 为空；`jobGroup` 无匹配任务时返回空结构；起止时间非法抛参数错误。

## 5. 兼容与回滚

- 纯新增只读接口，无 DDL、无配置项、无调度行为变更；回滚直接回退代码即可。
- 前端接入前建议先在管理端用 `GET /job/v1/graph` 与 `/job/v1/log/stats` 验证数据；
  DAG 拖拽连线仍调用既有 `POST /job/v1/info/{id}/dependencies`（全量替换语义），本次不新增写接口。
