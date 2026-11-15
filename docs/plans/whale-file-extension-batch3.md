# whale-file 能力扩展（批次 3：孤儿对象对账）

## 一、范围与决策

批次 1、2 已完成存储类型路由、回收站生命周期、重命名/移动、指标与健康检查。
本批次补齐路线图「阶段 1 剩余」的最后一项：存储对象与元数据的对账。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 存储侧对象列举 | ✅ | local 遍历桶目录，minio `listBuckets` + `listObjects` |
| 孤儿对象识别 | ✅ | 存储有、元数据无（含回收站行视为仍被引用） |
| 悬空元数据识别 | ✅ | 元数据有、存储无；**只报告，不自动删元数据** |
| 孤儿对象清理 | ✅ | 默认关闭，需显式 `delete-orphan-objects=true` |
| 定时任务 | ✅ | 默认关闭（`enabled=false`），避免未经评估的全量扫描 |
| 清理任务分布式锁 | ❌ | 继续依赖「对象删除幂等 + 元数据条件删除」兜底，单独排期 |

**安全原则**：对账默认只统计与打样日志，不删除任何东西；即使开启删除，也只删「存储有、元数据无」的孤儿对象，
绝不自动删除悬空元数据（对象可能因跨桶迁移、备份恢复、最终一致性而暂时缺失）。

## 二、设计

### 2.1 对象列举能力

- 领域新增 `StorageObject(bucket, objectKey, size)`。
- `StoragePlugin#listObjects()`：返回该介质下全部桶的对象；默认实现抛 `PARAM_ERROR`（未实现显式失败）。
  - local：遍历根目录下的桶目录，递归文件，对象键统一用 `/` 分隔；跳过多点前缀目录（如 `.chunk-upload`）。
  - minio：`listBuckets` 后用 `listObjects(recursive=true)` 遍历；任一桶失败则整体失败，由任务记录告警。
- `FileStorage#listObjects(storageType)` 按落盘介质路由（空值回落活动插件），与批次 1/2 的读取、删除路由一致。

### 2.2 对账任务

- 新增 `FileObjectRef(storageType, bucket, objectKey)` 与仓储 `findAllObjectRefs()`：
  查询全部元数据行（**包含逻辑删除行**，其对象在到期清理前仍被引用）。
- 应用层 `OrphanReconcileTask`：
  1. 按已装配插件逐个列举对象，得到 `storageType|bucket|objectKey` 集合；
  2. 读取 DB 引用集合，`storageType` 为空的历史行按活动插件归属；
  3. 差集：孤儿对象 = 存储 - DB；悬空元数据 = DB - 存储；
  4. 报告数量与最多 N 条样例行；开启删除时逐个删除孤儿对象；
  5. 任一介质列举失败即整轮放弃并计失败指标（不删除任何内容）；单个孤儿对象删除失败仅告警并跳过。
- 配置（`whale.file.orphan`）：`enabled` 默认 false、`delete-orphan-objects` 默认 false、
  `report-samples` 默认 20、`cron` 默认 `0 0 4 * * ?`（`@Scheduled` 占位符）。
- 指标：`file.orphan.objects`、`file.orphan.metadata`（Gauge，最近一轮结果）、
  `file.orphan.reconcile{result}`（Counter）。

### 2.3 为什么孤儿对象只按 storageType 比对

同一 `objectKey` 可能在不同介质/桶同时存在（移动的中断补偿会留下目标桶副本）。
键统一带上 `storageType`，避免把「合法副本」误判为孤儿；删除孤儿时也按该类型路由，
不会误删其他介质中的同名对象。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| local 列举 | 多桶、嵌套对象键、跳过多点目录 |
| 差分 | 孤儿对象、悬空元数据、空值 storageType 回落活动插件 |
| 任务 | 默认关闭不扫描；只报告不删除；显式开启后删除孤儿且不碰悬空元数据 |
| 指标 | 最近一轮数量写入 Gauge，失败计数 |

## 四、完成情况

- 领域：`StorageObject`、`FileObjectRef`；`StoragePlugin#listObjects()` 默认显式失败；
  local 按 `${root}/${bucket}/${objectKey}` 递归列举并跳过多点前缀保留目录；minio 遍历全部桶。
- 应用：`OrphanReconcileTask`（`@Scheduled` 默认关闭）、`FileMetrics` 增加
  `file.orphan.objects`、`file.orphan.metadata`、`file.orphan.reconcile{result}`。
- 基础设施：`FileStorage#listObjects` 按介质路由、`StoragePluginRegistry` 委派、
  `FileAssetRepository#findAllObjectRefs` + `FileInfoMapper#selectObjectRefs`（含逻辑删除行）。
- 配置：`FileProperties.Orphan`（`enabled` / `delete-orphan-objects` / `report-samples`）
  与 `application.yml` 的 `whale.file.orphan.*`（含 `WHALE_FILE_ORPHAN_*` 环境变量）。
- 测试：local 列举 1 项、minio 列举 1 项、对账任务 5 项（默认关闭、只报告、显式删除、
  列举失败放弃、空 storageType 回落活动插件）。

## 五、验证结果

`mvn -o test`（`whale-backend/whale-services/whale-service-file`）→ 13 个模块 BUILD SUCCESS，
208 项单测零失败（批次 2 为 201，本轮新增 7 项），全部模块 checkstyle 0 violation。

## 六、已修正问题项

- 首版对账任务在任一介质列举异常时仍继续比对，会把「列举失败」当成「该介质无对象」，
  导致大量合法元数据被误判为悬空；改为整轮放弃并计失败指标。
- 「开启删除」单测的 `task()` 工厂方法把 `delete-orphan-objects` 重置为 false，
  导致该用例实际走只报告分支；已改为由用例自行设置开关。
- 对账键首版只用 `bucket + objectKey`，跨介质同名对象会被互相抵消；改为
  `storageType|bucket|objectKey`，删除时也按该类型路由。

## 七、待做项

- 对账任务分布式锁与限流（超大对象量时按桶分批）。
- 悬空元数据的人工处置流程（导出报告 → 复核 → 手动恢复或清理）。
- 孤儿对象清理接入审批/审计。
- 批次 1 路线图中的版本、分享链接、批量操作、病毒扫描等。
