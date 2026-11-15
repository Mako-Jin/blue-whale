# whale-file 能力扩展（批次 11：文件版本第一批）

## 一、范围与决策

批次 1~10 已完成存储路由与回收站、重命名/移动、指标与健康检查、孤儿对象对账、分享链接、
批量删除与打包下载、内容魔数校验、租户/桶级配额、生命周期事件、CDN 预签名地址、上传策略热更新。
本批次落地路线图阶段 3 的「文件版本」第一批：数据模型 + 新版本上传 + 版本列表，并把版本占用纳入配额。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 版本数据模型 | ✅ | `file_info` 增加 `parent_id`、`version`，根版本 `parent_id = NULL`、`version = 1` |
| 新版本上传 | ✅ | 新物理对象 + 新元数据行，版本号取组内最大值 + 1，桶沿用根文件 |
| 版本列表 | ✅ | `GET /file/v1/{id}/versions`，按版本号倒序返回根版本与全部新版本 |
| 配额纳入 | ✅ | 每个版本是独立物理对象，按既有个人/租户/桶配额口径计量 |
| 文件列表过滤 | ✅ | `page` 只返回根文件（`parent_id IS NULL`），避免版本行污染文件列表 |
| 版本回滚 | ❌ | 批次 13 |
| 删除/恢复级联与回收站版本展示 | ❌ | 批次 12 |
| 版本数上限、版本清理 | ❌ | 见待做项 |
| 秒传参与版本 | ❌ | 秒传仍只登记根文件，不产生版本 |
| 版本列表分页 | ❌ | 版本量级较小，本批返回完整列表 |

**边界**：本批只做「追加版本」，不改变既有上传、删除、恢复与变更流语义。删除根文件时版本行
仍保留（当前行为），级联清理统一放到批次 12 处理；变更流仍会输出版本行，供知识库增量采集。

## 二、设计

### 2.1 数据模型

`V7__file_version.sql`（幂等：先查 `information_schema`，列/索引已存在时跳过）：

```sql
ALTER TABLE file_info
    ADD COLUMN parent_id BIGINT DEFAULT NULL COMMENT '版本组根文件ID；根版本为 NULL' AFTER uploader_id,
    ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '版本号：根版本为 1，新版本依次递增' AFTER parent_id;
ALTER TABLE file_info ADD INDEX idx_parent_version (parent_id, version);
```

`db/init.sql` 同步列与索引，保证空库初始化与 Flyway 基线一致。

### 2.2 聚合

`FileAssetAggregate` 增加 `parentId`、`version`：

- `register` / `reuse`：`parentId = null`、`version = 1`；
- `registerVersion(root, version, objectKey, ...)`：`parentId = root.id`、`version` 由应用层给出，
  桶与存储类型沿用根文件；根文件必须已落库；
- `assertVersionUploadable()`：仅 `NORMAL` 状态可追加版本（禁用与归档锁定均拒绝）；
- `reconstitute` 增加 `parentId`、`version` 两参，持久化还原时保留版本归属。

不变量：`parentId` 非空时 `version >= 2`；`version < 1` 视为数据异常（`SYSTEM_ERROR`）。

### 2.3 仓储

`FileAssetRepository` 新增：

| 方法 | 语义 |
| --- | --- |
| `findLatestVersion(rootId)` | 组内版本号最大的一行（`id = rootId OR parent_id = rootId`，含逻辑删除行，保证版本号单调） |
| `findVersions(rootId)` | 组内全部未删除版本（`version DESC, id DESC`），包含根版本 |

`page()` 追加 `parent_id IS NULL`，文件列表仅展示根文件；变更流与变更详情不过滤版本行。

### 2.4 应用层

`FileInfoService` 新增：

- `uploadVersion(id, UploadContent, requesterId, canManage, UploadLimits)`：
  解析根文件（传入版本行时回溯到根）→ 可见性校验 → 状态校验 → 魔数校验 → 配额预检 →
  计算下一版本号 → 写物理对象 → 配额内登记元数据（失败补偿删除对象）→ 返回版本结果；
- `versions(id, requesterId, canManage)`：解析根文件后返回版本组列表。

上传者记为当前请求者；版本对象键沿用 `tenants/{tenantId}/yyyy/MM/dd/<uuid>.<ext>` 规则。

### 2.5 接口

`FileVersionApi`（`/file/v{version}/{id}/versions`）：

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/file/v1/{id}/versions` | `sys:file:list` | 返回版本组（含根版本），按版本号倒序 |
| POST | `/file/v1/{id}/versions` | `sys:file:upload` | 上传新版本（multipart `file`），返回新版本元数据 |

`FileVersionResponse`：`id`、`parentId`、`version`、`fileName`、`originalName`、`bucket`、
`objectKey`、`fileSize`、`contentType`、`md5`、`uploaderId`、`createTime`。

POST 的普通上传通道与既有 `/file/v1/upload` 一致：先按生效上限预检声明大小，再全量读入。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 聚合 | `register` 根版本默认值；`registerVersion` 继承桶、记录根ID与版本号；根未落库/版本号非法/名称空/大小非正/根非正常状态被拒绝；`reconstitute` 保留版本归属 |
| 仓储 | `findLatestVersion` / `findVersions` 映射；`page` 追加 `parent_id IS NULL` |
| 应用层 | 新版本上传写入根文件桶、版本号为最新 + 1；传入版本行回溯根文件；非正常状态拒绝；配额不足拒绝且不写存储；版本列表可见性 |
| 控制器 | GET 返回版本列表；POST multipart 返回新版本；权限注解为 `sys:file:list` / `sys:file:upload` |
| 迁移 | V7 版本命名与幂等脚本结构（沿用既有迁移基线测试） |

## 四、完成情况

- 迁移：新增 `V7__file_version.sql`（`parent_id`、`version` 列与 `idx_parent_version` 索引，
  均按 `information_schema` 判重幂等），`db/init.sql` 同步列与索引；存量行默认根版本。
- 聚合：`FileAssetAggregate` 增加 `parentId`、`version`；`register`/`reuse` 固定根版本；
  新增 `registerVersion(root, version, ...)` 与 `assertVersionUploadable()`；校验逻辑抽取为
  `validateStorageLocation` / `validateVersion`；`reconstitute` 增加带版本归属的重载。
- 持久化：`FileInfoPo` 增加 `parentId`、`version`，装配器双向映射；`FileInfoMapper` 全部自定义
  SQL 列清单补齐两列，新增 `selectLatestVersion`（含已删除行）与 `selectVersions`（仅未删除）。
- 仓储：`FileAssetRepository` 新增 `findLatestVersion`、`findVersions`；`page()` 追加
  `parent_id IS NULL`，文件列表只展示根文件。
- 应用层：`FileInfoService#uploadVersion` 回溯根文件、校验状态/魔数/配额后写入根文件桶，
  版本号取组内最大值 + 1，落库失败补偿删除对象；`versions` 返回版本组列表。
- 接口：新增 `FileVersionApi`、`FileVersionResponse`、`FileVersionController`；
  `GET /file/v1/{id}/versions`（`sys:file:list`）与 `POST /file/v1/{id}/versions`
  （`sys:file:upload`，multipart `file`）。
- 测试：聚合 4 项、仓储 2 项、应用层 5 项、控制器 3 项。

## 五、验证结果

`mvn -o test`（`whale-backend/whale-services/whale-service-file`）→ 13 个模块 BUILD SUCCESS，
316 项单测零失败（批次 10 为 302，本轮新增 14 项），全部模块 checkstyle 0 violation。

## 六、已修正问题项

- 构造器加入版本校验后圈复杂度达到 18，触发 checkstyle `CyclomaticComplexity`（上限 15）→
  将存储定位与版本归属校验拆为私有静态方法，构造器恢复简单分支。
- 仓储单测断言 `page` 的 `parent_id IS NULL` 时直接调用 `getSqlSegment()`，无 Spring 容器时
  MyBatis-Plus 缺少实体元数据而报 `can not find lambda cache` → 断言前用
  `TableInfoHelper.initTableInfo` 手动初始化 `FileInfoPo` 元数据。
- 版本号取「组内最大值 + 1」而不是「根版本 + 1、再按未删除版本计数」：已删除版本占用的序号
  不复用，避免版本列表出现重复版本号；`selectLatestVersion` 因此不过滤逻辑删除行。

## 七、待做项

- 批次 12：删除/恢复/物理清理的版本级联，回收站与版本列表的口径统一。
- 批次 13：版本回滚（回滚为复制或指针切换，需明确物理对象保留策略）。
- 版本列表标记「最新版本」、版本数上限与旧版本清理（释放配额与物理对象）。
- 并发上传同一文件的两个版本可能得到相同版本号，需要唯一约束或按根文件加锁。
- 秒传不参与版本；知识库变更流对版本行的采集口径需在下游确认。
