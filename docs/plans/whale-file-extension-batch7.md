# whale-file 能力扩展（批次 7：租户级与桶级配额）

## 一、范围与决策

批次 1~6 已完成存储类型路由、回收站生命周期、重命名/移动、指标与健康检查、孤儿对象对账、
分享链接、批量删除与打包下载、上传内容魔数校验。本批次落地路线图阶段 2 的最后一项：
**租户/桶级配额**。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 个人配额 | ✅ 既有 | `whale.file.quota-bytes`，按 `uploader_id` 统计 |
| 租户级配额 | ✅ | `whale.file.quota.tenant-bytes`，按租户内正常文件总字节统计 |
| 桶级配额 | ✅ | `whale.file.quota.bucket-bytes`，按租户内单个桶的字节数统计 |
| 配额原子性 | ✅ | 沿用 MySQL 命名锁，多维锁按固定顺序获取，避免并发穿透与死锁 |
| 恢复占用配额 | ✅ | 恢复与上传共用同一套多维校验 |
| 配额用量查询接口 | ❌ | `/file/v1/quota` 仍只返回个人用量，多维用量查询列入待做 |
| 按租户差异化配额 | ❌ | 只提供全局默认值，租户级覆盖表/管理后台列入待做 |

**计量口径**：只统计未逻辑删除且状态为正常的文件（与个人配额一致）；桶配额在**租户内**计量
（与租户隔离口径一致，不跨租户合并）；租户配额只在存在租户上下文时生效，内网无租户态调用
（启动期、后台任务）不做租户维度限制，避免全局统计误伤。

## 二、设计

### 2.1 领域

- 新增 `StorageQuota` 值对象：`userBytes` / `tenantBytes` / `bucketBytes`，`<=0` 表示该维度不限；
  `hasAnyLimit()` 供仓储快速判断是否需要进入临界区。
- 仓储端口 `saveWithQuotaCheck` / `restoreWithQuotaCheck` 的 `long quotaBytes` 参数改为
  `StorageQuota quota`，把三个维度的上限一次传入；端口不再增加重载，避免调用方误用旧签名。

### 2.2 基础设施

- 用量统计：
  - `sumNormalSizeByUploader(uploaderId)`（既有）；
  - `sumNormalSizeByTenant()`：不附加用户条件，租户条件由 `TenantLineInnerInterceptor` 追加；
  - `sumNormalSizeByBucket(bucket)`：附加 `bucket = ?`，同样由拦截器限定当前租户。
  - 三者共享 `COALESCE(SUM(file_size), 0)` 查询与数值解析逻辑。
- 原子校验：`saveWithQuotaCheck` / `restoreWithQuotaCheck` 在 `REQUIRES_NEW` 事务内，
  按 **个人 → 租户 → 桶** 固定顺序逐层获取命名锁（只有配置了上限的维度才取锁），
  在最内层依次校验三个维度的已用量 + 本次写入量。固定顺序保证多请求不会形成锁等待环。
- 锁名：个人沿用 `whale:file:quota:{uploaderId}`（滚动升级期间新旧实例仍互斥），
  新增 `whale:file:quota:tenant:{tenantId}` 与 `whale:file:quota:bucket:{bucket}`。
- 锁释放沿用「事务完成后统一释放」策略，`file.quota.lock` 计时指标覆盖每一次取锁。
- 索引：新增 `V6__file_quota_index.sql`，为 `file_info(tenant_id, bucket)` 建索引，
  支撑桶维度聚合；个人/租户维度继续使用既有 `idx_uploader_id` / `idx_tenant_id`。

### 2.3 应用层

- 配置：`FileProperties.Quota`（前缀 `whale.file.quota`）新增 `tenant-bytes`、`bucket-bytes`，
  默认均为 0（不限），保持升级前后行为不变。
- 预检：`validateQuota(incomingSize, uploaderId, bucket, userQuota)` 依次校验已配置的维度，
  与仓储临界区使用同一计量口径，提前失败避免无谓的存储写入与摘要计算。
- 桶解析：新增 `resolveBucket(bucket)`，预检与落库使用同一个生效桶名（请求未指定时取默认桶）。
- 写入：`persistAsset` 把 `UploadLimits#quotaBytes` 与配置中的租户/桶上限组装为 `StorageQuota`。
- 恢复：`restore` 同样组装 `StorageQuota`，仓储从待恢复行读取 `uploaderId` / `bucket` / `fileSize`
  后在同一临界区内校验。
- 秒传：`rapidUpload` 无新内容但会新增元数据行，仍按三个维度校验并原子登记。

### 2.4 配置

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `whale.file.quota-bytes` | 1073741824 | 个人配额（既有），`<=0` 不限 |
| `whale.file.quota.tenant-bytes` | 0 | 租户配额，`<=0` 不限 |
| `whale.file.quota.bucket-bytes` | 0 | 单桶配额（租户内），`<=0` 不限 |

环境变量：`WHALE_FILE_QUOTA_TENANT_BYTES`、`WHALE_FILE_QUOTA_BUCKET_BYTES`。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 领域 | `StorageQuota#hasAnyLimit` 各组合 |
| 仓储 | 仅个人维度时锁名与行为不变；租户维度超限拒绝；桶维度超限拒绝；三维度按固定顺序取锁；三个统计方法的数值/字符串/空结果兼容 |
| 应用 | 租户配额预检拒绝、桶配额预检拒绝、恢复传入 `StorageQuota`、默认配置不触发多维查询 |
| 迁移 | V6 命名合规、无破坏性语句（沿用既有迁移校验） |

## 四、完成情况

- 领域：新增 `StorageQuota` 值对象（个人/租户/桶，`<=0` 不限，`hasAnyLimit` 快速短路）；
  仓储端口 `saveWithQuotaCheck` / `restoreWithQuotaCheck` 的 `long quotaBytes` 改为
  `StorageQuota`，并新增 `sumNormalSizeByTenant` / `sumNormalSizeByBucket` 两个统计方法。
- 基础设施：三个维度共享 `COALESCE(SUM(file_size), 0)` 查询与数值解析；配额临界区按
  **个人 → 租户 → 桶** 固定顺序逐层获取命名锁（只对配置了上限且上下文具备的维度取锁），
  在最内层依次校验；锁名沿用 `whale:file:quota:{uploaderId}`，新增
  `whale:file:quota:tenant:{tenantId}` 与 `whale:file:quota:bucket:{bucket}`。
- 迁移：新增 `V6__file_quota_index.sql`（`information_schema` 判重幂等），为
  `file_info(tenant_id, bucket)` 建索引；`db/init.sql` 同步该索引。
- 配置：`FileProperties.Quota`（`tenant-bytes` / `bucket-bytes`，默认 0 不限）与
  `application.yml` 的 `WHALE_FILE_QUOTA_TENANT_BYTES` / `WHALE_FILE_QUOTA_BUCKET_BYTES`。
- 应用：`validateQuota` 扩展为三维预检；新增 `quotaOf` 组装三维配额、`resolveBucket` 统一
  桶名解析；`upload`/`completeChunkUpload`/`rapidUpload` 预检与 `persistAsset` 落库使用同一口径；
  `restore` 同样传入三维配额，仓储从待恢复行读取上传者、桶与大小。
- 测试：领域 `StorageQuotaTest` 3 项；仓储新增 4 项（租户超限、桶超限、三维固定顺序取锁、
  租户/桶聚合）；应用新增 4 项（租户超限、桶超限、三维组装、恢复传入租户与桶维度）。

## 五、验证结果

`mvn -o test`（`whale-backend/whale-services/whale-service-file`）→ 13 个模块 BUILD SUCCESS，
277 项单测零失败（批次 6 为 266，本轮新增 11 项），全部模块 checkstyle 0 violation。

## 六、已修正问题项

- 秒传预检首轮漏改：只更新了 `upload` / `completeChunkUpload` 的调用，`rapidUpload` 仍用旧签名
  导致编译失败 → 补上三维预检，并改用源文件所在桶（`RapidUploadCommand` 无桶参数，按默认桶
  统计会让非默认桶的秒传被错误计量）。
- 设计期规避的隐患（非上线后发现）：
  - 三个维度各自加锁时顺序不固定会形成锁等待环 → 统一按「个人 → 租户 → 桶」嵌套获取，
    所有请求顺序一致，不会互相持有对方需要的锁；
  - 无租户上下文时租户拦截器不追加条件，租户/桶聚合会变成全局统计 → 这两个维度只在存在
    租户上下文时校验与取锁，内网无租户态调用不受限制；
  - `StorageQuota` 全为 0 或负数时仍进入临界区会产生无谓加锁 → 以 `hasAnyLimit` 短路为普通保存。

## 七、待做项

- 多维配额用量查询接口（个人/租户/桶）与租户管理员视图。
- 按租户差异化配额（配额表 + 管理接口），替代全局默认值。
- 配额告警（阈值 80%/95%）与用量趋势指标。
- 路线图其余项：文件版本、存储迁移、CDN、领域事件、多段 Range、加密、策略热更新。
