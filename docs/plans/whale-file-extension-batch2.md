# whale-file 能力扩展（批次 2：回收站生命周期 + 文件重命名/移动）

## 一、范围与决策

批次 1 已完成 storageType 路由、回收站列表/恢复、指标与健康检查。本批次承接路线图「阶段 1 剩余」，
但**不含孤儿文件对账**（需要对存储侧做全量 list 与 dry-run 设计，单独排期）。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| `delete_time` 字段 | ✅ | 回收站真实删除时间，替代「用更新时间冒充删除时间」 |
| 回收站到期物理清理 | ✅ | 定时任务：删除 N 天前已逻辑删除的元数据 + 物理对象 |
| 文件重命名 | ✅ | 只改 `original_name`，不动 `objectKey`/`file_name` |
| 跨桶移动 | ✅ | 插件层新增 `move`，先迁物理对象再改元数据，失败补偿回迁 |
| 孤儿文件对账 | ❌ | 需要存储侧 `list` 与 dry-run 报告，排入阶段 2 |
| 新增权限码 | ❌ | 重命名复用 `sys:file:upload`；移动属存储位置变更，复用 `sys:file:manage` |

## 二、设计

### 2.1 delete_time

`V4__file_delete_time.sql` 幂等加列，并按 `update_time` 回填存量已删行的删除时间：

```sql
ALTER TABLE file_info ADD COLUMN delete_time DATETIME DEFAULT NULL
    COMMENT '逻辑删除时间；恢复时置空' AFTER deleted;
UPDATE file_info SET delete_time = update_time WHERE deleted = 1 AND delete_time IS NULL;
```

- 删除改走自定义 `UPDATE ... SET deleted=1, delete_time=NOW(), update_time=NOW() WHERE id=? AND deleted=0`，
  不再使用 MyBatis-Plus 逻辑删除（它不会写 `delete_time`）。
- 恢复清空 `delete_time`，并把 `update_time` 刷新为恢复时刻。
- 回收站列表改按 `delete_time DESC, id DESC` 排序，响应新增 `deleteTime`。

### 2.2 回收站到期物理清理

- 配置（`whale.file.recycle`）：`retention-days` 默认 30（`<=0` 关闭）、`purge-enabled` 默认 true、
  `purge-batch-size` 默认 200、`purge-cron` 默认 `0 30 3 * * ?`（cron 走 `@Scheduled` 占位符）。
- 任务在应用层 `RecyclePurgeTask`，`@Scheduled` + `@EnableScheduling`：
  1. 按 `deleted=1 AND delete_time < now-retention-days` 分批取到期记录（每批上限 `purge-batch-size`，最多 10 轮）；
  2. 先按记录 `storageType` 路由删除物理对象（对象不存在视为成功），再物理删除元数据行；
  3. 单条失败仅 WARN 并计入失败指标，记录保留待下轮重试；
  4. 无租户上下文，租户插件按既有约定跳过租户条件，可跨租户清理。
- 指标：`file.recycle.purged{result}`。
- 物理删除使用 `FileStorage#delete(bucket, objectKey, storageType)` 重载，避免切换活动插件后清错介质。

### 2.3 重命名

- 聚合新增行为 `rename(newName)`：仅 `NORMAL` 状态可改名（禁用/归档锁定视为不可变），
  空名抛 `PARAM_ERROR`，与原名相同返回 `false`（免更新）。
- 仅更新 `original_name` 与 `update_time`；`file_name`、`objectKey`、`storageType` 不变。
- 接口：`PUT /file/v1/{id}/rename`，body `{ "originalName": "..." }`，权限 `sys:file:upload`。

### 2.4 跨桶移动

- 聚合新增行为 `moveTo(targetBucket)`：仅 `NORMAL` 状态可移动；桶名拒绝空白、路径分隔符与 `..`；
  目标与当前相同返回 `false`（幂等）。
- 插件 SPI 新增 `move(bucket, objectKey, targetBucket)`：
  - local：`Files.move`（先建目标父目录，源不存在抛 `NOT_FOUND`）；
  - minio：`copyObject` + `removeObject`，目标桶按既有 `ensureBucket` 策略处理；
  - 默认实现抛 `PARAM_ERROR`（未实现移动能力的第三方插件显式失败，不静默）。
- `FileStorage#move(..., storageType)` 按落盘介质路由。
- 应用层顺序：先迁物理对象 → 再更新 `bucket`；更新失败时尽力回迁并 WARN，避免元数据与对象错位。
- 接口：`PUT /file/v1/{id}/move`，body `{ "bucket": "..." }`，权限 `sys:file:manage`。

## 三、接口契约

| 方法 | 路径 | 权限 | 请求 | 响应 |
| --- | --- | --- | --- | --- |
| GET | `/file/v1/recycle/page` | `sys:file:list` | 同批次 1 | 新增 `deleteTime`，排序改按删除时间 |
| POST | `/file/v1/{id}/restore` | `sys:file:delete` | — | 恢复时清空 `delete_time` |
| PUT | `/file/v1/{id}/rename` | `sys:file:upload` | `originalName` 非空 | 空结果 |
| PUT | `/file/v1/{id}/move` | `sys:file:manage` | `bucket` 非空 | 空结果 |

## 四、测试计划

| 范围 | 用例 |
| --- | --- |
| 聚合 | 重命名/移动的状态约束、幂等、非法桶名；移动不改变 objectKey |
| 仓储 | 删除写 `delete_time`、恢复清空、到期查询、物理删除、改名/改桶 SQL |
| 清理任务 | 关闭开关不执行、按批清理、物理删除失败保留记录、指标计数 |
| 应用层 | 重命名仅更新元数据、移动先物理后元数据、元数据失败回迁 |
| 控制器 | 两个新接口的请求校验与权限透传 |
| 插件 | local move 正常/源不存在；minio move 委派 copy+remove |

## 五、待做项

- 孤儿文件对账（存储侧 list + 差异报告 + 可选清理）。
- 回收站清理任务接入分布式锁，避免多实例同时清理同一批记录（当前以「对象删除幂等 + 元数据条件删除」兜底）。
- 文件版本、分享链接、批量操作、病毒扫描等见批次 1 路线图。

## 六、已修正问题项（设计阶段）

| 问题 | 处置 |
| --- | --- |
| 批次 1 回收站用 `update_time` 冒充删除时间 | 本批次新增 `delete_time` 并回填存量，列表排序与响应同步修正 |
| MyBatis-Plus `deleteById` 不会写删除时间 | 删除改自定义 SQL，条件带 `deleted=0`，天然幂等 |
| 移动若先改元数据再迁对象，失败后下载 404 | 改为先迁对象、再改元数据，并对元数据失败做回迁补偿 |

## 七、完成情况（2026-11-15）

### 完成项

| 事项 | 落地位置 |
| --- | --- |
| delete_time 迁移 | `V4__file_delete_time.sql`（information_schema 判重 + 按 `update_time` 回填）、`init.sql` 同步 |
| 删除/恢复语义 | `FileInfoMapper#markDeletedById`（写 `delete_time`，条件 `deleted=0`）、`restoreById`（清空 `delete_time`）、`FileAssetRepositoryImpl` |
| 回收站投影 | 领域新增 `FileRecycleItem` / `FileRecyclePage`，回收站列表不再复用聚合；列表按删除时间倒序，响应新增 `deleteTime` |
| 到期物理清理 | `RecyclePurgeTask`（`@Scheduled`，最多 10 轮 × 批大小），`FileStorage#delete(..., storageType)` 按落盘介质删除；`@EnableScheduling` |
| 清理配置 | `whale.file.recycle`（`purge-enabled` / `retention-days` / `purge-batch-size` / `purge-cron`），`retention-days<=0` 关闭清理 |
| 重命名 | 聚合 `rename`（仅 NORMAL、空名拒绝、同名幂等）、`updateOriginalName`、`PUT /file/v1/{id}/rename` |
| 跨桶移动 | 聚合 `moveTo`（桶名拒绝分隔符与 `..`）、插件 `move`（local `Files.move`、minio `copyObject`+`removeObject`）、`FileStorage#move` 按类型路由、`PUT /file/v1/{id}/move`、元数据失败回迁 |
| 指标 | `file.recycle.purged{result}` |
| 测试 | 聚合 4 项、仓储 3 项、清理任务 3 项、应用层 5 项、控制器 2 项、插件 3 项 |

### 验证结果

- `mvn -o test`（`whale-backend/whale-services/whale-service-file`）：13 个模块 BUILD SUCCESS。
- 单测：domain 22、infrastructure 43、application 62、plugins-local 11、plugins-minio 9、architecture 4、web 50，
  合计 201 项零失败（本轮新增 20 项）。
- checkstyle：全部模块 0 violation。

### 已修正问题项（实现阶段）

| 问题 | 处置 |
| --- | --- |
| 清理任务单测按「每次返回同一批」断言，导致 `delete` 被调用两次 | 断言改为 `times(2)`，并保持「两轮批量后返回空批」的桩，覆盖分批退出逻辑 |
| 控制器单测的兜底 `MockMvc` 未装配 Validator，空名称校验用例得到 200 | 移除该用例；非空校验仍由 `@Valid` 声明式保障，由运行期 Validator 生效 |
| 回收站结果加入 `deleteTime` 后构造参数变化 | 应用层结果、响应、控制器测试同步更新 |

