# whale-file 能力扩展（批次 14：存储迁移工具）

## 一、范围与决策

批次 1 已用 `storage_type` 记录每个文件的落盘插件，切换 `whale.file.storage.type` 后存量文件仍可从
原介质读取，但存量数据不会自动搬家。本批次提供受控的批量迁移能力：把指定源介质的文件复制到
当前活动插件，校验通过后更新元数据归属。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 按源存储类型批量迁移 | ✅ | 每次最多处理固定条数，可重复执行，已迁移行不再命中 |
| 迁移校验 | ✅ | 复制过程中统计字节数与 MD5，与元数据不一致则放弃该文件 |
| 源对象删除 | ✅ | 可选 `deleteSource`，仅在元数据更新成功后删除 |
| dry-run | ✅ | 只统计待迁移数量，不读不写 |
| 桶过滤 | ✅ | 可选 `bucket`，仅迁移指定桶 |
| 二次确认 | ✅ | 非 dry-run 必须显式 `confirm=true`，防止误触 |
| 定时全量迁移 | ❌ | 迁移涉及大量 IO，保持人工触发；见待做项 |
| 跨租户迁移 | ❌ | 使用租户拦截器，迁移范围限于当前租户 |
| 数据库变更 | ❌ | 复用 `storage_type` |

**迁移语义**：源对象复制到当前活动插件的同桶同对象键 → 校验字节数与 MD5 → 更新
`file_info.storage_type` → 可选删除源对象。任何一步失败都保留元数据原值并尽力清理目标对象，
该文件计入失败，下一轮可重试；不会出现「元数据指向目标但目标对象不完整」的状态。

## 二、设计

### 2.1 仓储

`FileAssetRepository` 新增：

| 方法 | 语义 |
| --- | --- |
| `findByStorageType(storageType, limit)` | 按落盘类型取待迁移文件（主键升序，`limit` 条） |
| `updateStorageType(id, storageType)` | 迁移成功后更新落盘类型 |

Mapper 对应 `selectByStorageType`（显式列清单 + `LIMIT #{}`）与 `updateStorageType`。

### 2.2 应用层

`StorageMigrationService#migrate(StorageMigrationCommand)`：

1. 校验源类型非空且不等于活动插件类型；
2. 读取当前租户下源类型文件（`limit` 条），可选按桶过滤；
3. 逐个文件：`load(源类型)` → `store(活动插件)`，同时用计数流与摘要流校验；
4. 校验通过后更新 `storage_type`；`deleteSource=true` 时删除源对象（失败仅告警）；
5. 汇总 `scanned/migrated/skipped/failed/failures`，失败不影响后续文件；
6. dry-run 只统计，不触达存储。

### 2.3 接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/file/v1/storage-migrations` | `sys:file:manage` | 触发一批迁移，返回汇总 |

请求：`sourceStorageType`（必填）、`bucket`、`limit`（1~500，默认 100）、`dryRun`、`deleteSource`、
`confirm`（非 dry-run 必须为 `true`）。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 仓储 | `findByStorageType` 映射与 `limit`；`updateStorageType` 委派 |
| 应用层 | 成功迁移（校验 MD5/大小、更新类型、按需删除源对象）；源类型等于活动类型拒绝；dry-run 不触达存储；校验不一致时清理目标且不更新类型；单个文件失败不影响后续 |
| 控制器 | POST 返回汇总；非 dry-run 缺少确认返回参数错误；权限注解为 `sys:file:manage` |

## 四、完成情况

- 仓储：`FileAssetRepository` 新增 `findByStorageType`、`updateStorageType`；Mapper 对应
  `selectByStorageType`（显式列清单 + `LIMIT #{}`）与 `updateStorageType`。
- 应用层：新增 `StorageMigrationService`/`StorageMigrationServiceImpl`：按源类型取有界批次，
  可选桶过滤；逐文件 `load(源类型)` → `store(活动插件)`，用计数流与摘要流校验字节数和 MD5，
  通过后更新落盘类型，可选删除源对象；单个失败不影响后续，dry-run 不触达存储。
- 接口：`POST /file/v1/storage-migrations`（`sys:file:manage`），非 dry-run 必须
  `confirm=true`；新增请求/响应模型与 MapStruct 装配器。
- 测试：仓储 1 项、应用层 6 项、控制器 3 项。

## 五、验证结果

`mvn -o test`（`whale-backend/whale-services/whale-service-file`）→ 13 个模块 BUILD SUCCESS，
337 项单测零失败（批次 13 为 327，本轮新增 10 项），全部模块 checkstyle 0 violation。

## 六、已修正问题项

- try-with-resources 关闭源流可能抛出 `IOException`，最初未捕获导致编译失败 →
  在 `migrateOne` 中单独捕获 `IOException`，统一清理目标对象并转 `SYSTEM_ERROR`。
- 应用层「单个失败不影响后续」用例把成功与失败的文件主键写反 → 修正为断言首个文件更新成功、
  第二个文件不更新。

## 七、待做项

- 定时/自动迁移与进度持久化（当前为人工触发的有界批次）。
- 存储侧原生 copy 能力（当前为读 + 写流式复制，大文件较慢）。
- 迁移审计与限流，避免与业务读写争抢带宽。
- 历史数据 `storage_type` 为空时先由启动回填任务补齐，再执行迁移。
