# whale-file 能力扩展路线图（批次 1：storageType 路由 / 回收站恢复 / 指标与健康检查）

## 一、背景

外部评审对文件服务提出四类共 23 项扩展建议（功能完整性 / 可观测与运维 / 安全合规 / 存储与性能）。
经代码核对，多数建议成立，但本轮只落地其中「事故级风险 + 运维修复 + 业务刚需」三项，
其余按批次排期，避免一次性铺太大。

### 评审事实核对（开工前）

| 评审项 | 代码现状 | 结论 |
| --- | --- | --- |
| 存储插件切换后存量文件 404 | `StoragePluginRegistry` 的 `load`/`presignedDownloadUrl` 只委派 `active`；`file_info` 无存储类型列 | **成立，事故级** |
| `/actuator/health` 不反映存储状态 | `StoragePlugin` 无 `health()`，仓库无任何 `HealthIndicator` | **成立，运维盲区** |
| POM 引 Micrometer 却无业务指标 | `web/pom.xml` 已含 actuator + prometheus，`application.yml` 已暴露 `health,info,prometheus`，但无自定义指标 | **成立** |
| 删除只能靠 DBA 改库恢复 | `DELETE /file/v1/{id}` 为逻辑删除，无回收站列表与恢复接口 | **成立** |
| `delete` 幂等 | 重复删除第二次在 `requireAccessibleFile` 处直接 404，非幂等成功 | **修正**：评审表述不准确，见「已修正问题项」 |
| 重命名/移动可直接在接口层做 | `FileAssetAggregate` 的 `bucket`/`objectKey`/`originalName` 均为 `final` | **修正**：需改聚合可变性，本轮不做 |
| `rapidUpload` 重复登记 | 同 md5+size 每次调用都新建元数据行（物理对象复用） | 成立，但属去重语义设计问题，本轮不做 |

### 已确认的决策

| 决策项 | 结论 |
| --- | --- |
| 本轮范围 | 三件关键项：storageType 路由、回收站/恢复、指标与健康检查 |
| 存量 storageType 回填 | 启动期按「当前活动插件」回填 `storage_type IS NULL` 的行，SQL 迁移不写死 `local` |
| 回收站物理清理 | 本轮只做列表与恢复；到期物理清理（定时任务 + 对象删除）列入待做 |
| 新增权限码 | 不新增。回收站列表复用 `sys:file:list`，恢复复用 `sys:file:delete`，避免跨库权限种子与网关改动 |

## 二、设计

### 2.1 storageType 路由（事故级修复）

**表结构**（`V3__file_storage_type.sql`）

```sql
ALTER TABLE file_info
    ADD COLUMN storage_type VARCHAR(32) DEFAULT NULL
    COMMENT '落盘时的存储插件类型（local/minio）；存量由启动期回填' AFTER bucket;
```

`db/init.sql` 同步加列，保证空库初始化与 Flyway 基线一致。

**写入**：`FileInfoServiceImpl#persistFile` 登记元数据时写入 `StoragePluginCatalog#activeType()`；
秒传 `reuse` 继承源文件的 `storageType`（复用同一物理对象，存储类型不可变）。

**读取**：`FileStorage` 新增带 `storageType` 的 `load` / `presignedDownloadUrl` 重载，
`StoragePluginRegistry` 按类型选插件；类型为空时回落活动插件（兼容回填前的存量行），
类型非空但插件未装配时抛 `SYSTEM_ERROR` 并列出可用插件（数据指向不可用介质，不能静默 404）。

**回填**：新增 `StorageTypeBackfillRunner`（`ApplicationRunner`），执行
`UPDATE file_info SET storage_type = ? WHERE storage_type IS NULL`，值取活动插件类型。
启动期无租户上下文，租户插件按既有约定跳过追加条件，可覆盖全量存量行；
回填失败仅 WARN，读取侧仍有「空值回落活动插件」兜底。

### 2.2 回收站 / 恢复

**接口**

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/file/v1/recycle/page` | `sys:file:list` | 仅查已逻辑删除行（`deleted=1`），无管理权限仅本人上传 |
| POST | `/file/v1/{id}/restore` | `sys:file:delete` | 恢复逻辑删除行；已恢复时幂等返回成功 |

回收站列表按 `update_time DESC, id DESC` 排序。当前表未单独记录删除时间，
列表中的 `updateTime` 是最后一次业务更新时间，接口文档明确此口径；`delete_time` 列入待做。

**恢复语义**

- 可见性：跨租户不可见；管理权限可恢复全租户；普通用户仅能恢复本人上传的文件。
- 配额：恢复会重新占用个人配额，配额校验与 `deleted` 置 0 在同一命名锁临界区内完成
  （复用 `saveWithQuotaCheck` 的 `GET_LOCK` 机制），避免并发恢复/上传穿透配额。
- 幂等：目标行不存在或 `deleted=0` 时不报错，返回成功（与 `lock/unlock` 的幂等口径一致）。

### 2.3 指标与健康检查

**指标**（Micrometer，Prometheus 侧下划线化）

| 指标 | 类型 | 标签 | 采集点 |
| --- | --- | --- | --- |
| `file.upload` | Counter | `mode`=multipart/chunk/rapid、`result`=success/failure/miss | 三个上传入口 |
| `file.upload.size` | DistributionSummary | `mode` | 上传成功后的字节数 |
| `file.upload.duration` | Timer | `mode` | 上传入口耗时 |
| `file.download` | Counter | — | `openContent`（下载与预览共用） |
| `file.download.size` | DistributionSummary | — | 文件字节数 |
| `file.download.duration` | Timer | — | 打开内容流耗时 |
| `file.quota.lock` | Timer | `op`=save/restore | 仓储层命名锁获取耗时 |
| `file.chunk.session.active` | Gauge | — | 暂存目录中的活跃会话数 |
| `file.storage.plugin.active` | Gauge | `type` | 活动插件为 1，其余为 0 |
| `file.recycle.restore` | Counter | `result`=success/failure | 恢复入口 |

**健康检查**

- 领域层新增 `StorageHealth(up, message)` 值对象；`StoragePlugin#health()` 默认 `up`，
  local 校验根目录存在且可写，minio 探测默认桶（未配置桶时 `listBuckets`）。
- `StoragePluginCatalog#health()` 返回 `Map<type, StorageHealth>`，单插件异常降级为 `down` 并保留信息。
- 新增 `StorageHealthIndicator`（bean 名 `storage`）：活动插件 DOWN 或分片暂存目录不可写时整体 DOWN，
  明细包含每个插件的健康信息与 `active`。
- `ChunkUploadStore` 新增默认方法 `writable()` / `sessionCount()`，本机实现按根目录可写性与
  `.chunk-upload` 子目录数量计算。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 聚合 | 登记/重建保留 `storageType`；秒传继承存储类型 |
| 注册表 | 按 `storageType` 路由到指定插件；空类型回落活动插件；未知类型抛 `SYSTEM_ERROR` |
| 仓储 | 回收站分页 SQL 传参、恢复幂等、配额锁临界区（Mockito 断言命名锁与恢复调用） |
| 应用层 | 上传登记活动插件类型；回收站查询的可见范围；恢复成功/超出配额/已恢复幂等 |
| 控制器 | 回收站分页、恢复接口的 Request→Query/Command 转换与响应 |
| 指标 | `SimpleMeterRegistry` 断言计数、计时与 Gauge 注册 |
| 健康检查 | 活动插件 DOWN、暂存目录不可写时整体 DOWN；插件明细上报 |

## 四、待做项

| 批次 | 事项 |
| --- | --- |
| 阶段 1 剩余 | 回收站到期物理清理（定时任务 + 物理对象删除）、文件重命名/移动、孤儿文件对账 |
| 阶段 2 | 分享链接（HMAC + 过期 + 下载次数/密码）、批量删除/打包下载、病毒扫描与魔数校验、租户/桶级配额 |
| 阶段 3 | 文件版本（version + parentId）、存储迁移工具、CDN 地址、领域事件（锁定/解锁/删除/恢复） |
| 阶段 4 | 多段 Range、存储加密、上传策略热更新、防盗链/限流 |
| 本轮遗留 | `file_info.delete_time`（回收站真实删除时间）、`rapidUpload` 同用户同文件去重语义 |

## 五、已修正问题项

| 问题 | 处置 |
| --- | --- |
| 评审称 `delete` 已幂等 | 实际重复删除返回 404；本轮不改变删除语义，恢复接口按幂等实现 |
| 评审称重命名/移动只需接口层改动 | 聚合 `originalName`/`bucket`/`objectKey` 为 `final`，需先改领域模型，排入阶段 1 剩余 |
| 存储类型回填若写死 `local`，MinIO 环境会误标 | 改为启动期按活动插件回填，SQL 不写死介质类型 |

## 六、验证

```powershell
# 在 whale-backend/whale-services/whale-service-file 目录执行，聚合 pom 需要用该目录作为根才会构建子模块
mvn -o test
```

要求：全模块 BUILD SUCCESS、单测零失败、checkstyle 零违规。

## 七、完成情况（2026-11-15）

### 完成项

| 事项 | 落地位置 |
| --- | --- |
| 迁移脚本 | `V3__file_storage_type.sql`（information_schema 判重幂等）、`db/init.sql` 同步加列 |
| 存量回填 | `StorageTypeBackfillRunner`（启动期按活动插件回填 `storage_type IS NULL`） |
| 聚合与持久化 | `FileAssetAggregate.storageType`、`FileInfoPo.storageType`、装配器映射、自定义 SQL 补列 |
| 读取路由 | `FileStorage` 带 `storageType` 的 `load`/`presignedDownloadUrl` 重载，`StoragePluginRegistry` 按类型选插件，空值回落活动插件，未知类型抛 `SYSTEM_ERROR` |
| 下载与预签名 | `openContent`（含 Range）、`presignedUrl` 均按文件落盘类型路由 |
| 回收站接口 | `GET /file/v1/recycle/page`、`POST /file/v1/{id}/restore`，权限复用 `sys:file:list` / `sys:file:delete` |
| 恢复配额 | `restoreWithQuotaCheck` 在 `GET_LOCK` 临界区内统计用量并恢复，超配额抛 `PARAM_ERROR`，已恢复/不存在幂等返回 |
| 指标 | `FileMetrics`：上传/下载计数、字节数、耗时、恢复结果、配额锁耗时、活跃分片会话、活动插件 |
| 健康检查 | `StorageHealth` 值对象、`StoragePlugin#health()`（local/minio 实现）、`StorageHealthIndicator`（bean `storage`） |
| 测试 | 域/仓储/注册表/应用层/控制器/指标/健康检查均补充用例，file 模块单测全绿 |

### 验证结果

- `mvn -o test`（工作目录 `whale-backend/whale-services/whale-service-file`）：13 个模块 BUILD SUCCESS。
- 单测：domain 18、infrastructure 40、application 54、plugins-local 9、plugins-minio 8、architecture 4、web 48，合计 181 项，零失败。
- checkstyle：全部模块 0 violation。

### 已修正问题项（实现阶段）

| 问题 | 处置 |
| --- | --- |
| Spring Boot 4 已把 `Health`/`HealthIndicator` 从 `org.springframework.boot.actuate.health` 迁到 `spring-boot-health` 的 `org.springframework.boot.health.contributor` | 健康指示器按新包实现，置于 web 模块（infra 不引入 actuator 依赖，避免可部署模块之外的耦合） |
| `mvn -pl whale-services/whale-service-file -am` 只构建聚合 pom | 改为在 `whale-service-file` 目录执行 `mvn -o test`，构建 13 个模块 |
| 回收站测试样例 objectKey 缺少租户前缀，触发 `NOT_FOUND` | 测试数据统一改为 `tenants/{tenantId}/...` 口径，与真实对象键一致 |

