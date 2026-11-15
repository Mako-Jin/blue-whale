# whale-file 能力扩展（批次 12：版本删除/恢复级联与回收站口径）

## 一、范围与决策

批次 11 已落地文件版本模型、新版本上传与版本列表。本批次补齐版本的删除、恢复与物理清理级联，
并统一回收站口径，避免根文件删除后版本行仍可下载、恢复后版本缺失。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 根文件逻辑删除级联版本 | ✅ | 删除根文件时同批逻辑删除其全部未删除版本 |
| 根文件恢复级联版本 | ✅ | 恢复根文件时恢复组内已删除版本，配额按组内待恢复总字节校验 |
| 版本行单独删除/恢复 | ✅ | 仅影响该版本；版本行删除不进回收站列表 |
| 到期物理清理 | ✅ | 复用 `RecyclePurgeTask`（级联删除后版本行本身也是 `deleted=1` 行） |
| 回收站列表口径 | ✅ | 仅展示版本组根文件（`parent_id IS NULL`） |
| 版本级回收站展示/单独找回 | ❌ | 见待做项；当前通过恢复根文件找回 |
| 版本数上限与旧版本清理 | ❌ | 见待做项 |
| 数据库变更 | ❌ | 复用批次 11 的 `parent_id` / `version` / `delete_time` |

**语义**：

- 删除根文件：级联逻辑删除组内全部版本；组内任一文件处于归档锁定时整体拒绝删除。
- 删除版本行：仅逻辑删除该版本，不影响根文件与其他版本。
- 恢复根文件：恢复根文件与组内全部已删除版本，配额按组内待恢复字节合计校验。
- 恢复版本行：根文件已删除时按整组恢复；根文件正常时仅恢复该版本。
- 回收站列表只展示根文件；版本行不单独出现在回收站。

## 二、设计

### 2.1 仓储

`FileAssetRepository` 新增：

| 方法 | 语义 |
| --- | --- |
| `findDeletedVersions(rootId)` | 组内已逻辑删除的版本行（不含根文件），供恢复后发布事件 |

行为调整：

- `deleteById(id)`：先逻辑删除目标行，再按 `parent_id = id` 级联逻辑删除其版本；
  同一方法既覆盖根文件也覆盖版本行（版本行没有子行）。
- `restoreWithQuotaCheck(id, quota)`：待恢复字节取组内 `deleted = 1` 的合计
  （`id = rootId OR parent_id = rootId`），临界区内恢复根文件与全部已删除版本。
  传入版本行时按该行自身为组根，只统计并恢复自身。

`FileInfoMapper` 新增 `markDeletedByRootId`、`restoreVersionsByRootId`、
`selectDeletedVersions`、`sumDeletedSizeOfGroup`；回收站分页与计数追加 `parent_id IS NULL`。

### 2.2 应用层

- `delete` / `batchDelete`：删除前校验目标与组内版本均可删除（归档锁定拒绝），
  再调用仓储级联删除；对根文件与级联版本分别发布 `DELETED` 事件。
- `restore`：先读取目标（含已删除行）并解析根文件，可见性以根文件为准；
  根文件已删除时按整组恢复并发布根文件与已删除版本的 `RESTORED` 事件；
  根文件正常且目标是版本行时仅恢复该版本。
- 幂等口径不变：目标未删除时按成功返回，不查配额、不改数据。

### 2.3 物理清理

级联删除时版本行与根文件使用相同口径写入 `delete_time`，`RecyclePurgeTask` 的到期查询
（`deleted = 1 AND delete_time < cutoff`）天然包含版本行，逐条删除对象与元数据即可，
无需新增清理入口。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 仓储 | `deleteById` 级联调用；`restoreWithQuotaCheck` 按组内待恢复字节校验并恢复版本；`findDeletedVersions` 映射 |
| 应用层 | 删除根文件级联并发布每个文件事件；组内版本归档锁定整体拒绝；删除版本行不影响根文件；恢复根文件级联并校验合计配额；根文件正常时恢复单个版本；恢复幂等 |
| 控制器 | 接口契约不变，既有回收站/删除用例回归通过 |

## 四、完成情况

- 仓储：`deleteById` 追加 `markDeletedByRootId` 级联删除版本；`restoreWithQuotaCheck`
  按组内待恢复字节合计校验并调用 `restoreVersionsByRootId` 恢复版本；新增 `findDeletedVersions`。
- Mapper：新增 `selectDeletedVersions`、`sumDeletedSizeOfGroup`、`markDeletedByRootId`、
  `restoreVersionsByRootId`；回收站分页与计数追加 `parent_id IS NULL`。
- 应用层：`delete`/`batchDelete` 统一走 `deleteWithVersions`，根文件删除前校验组内版本可删除，
  删除后对根文件与级联版本逐个发布 `DELETED` 事件；`restore` 以根文件为可见性口径，
  根文件已删除时整组恢复并发布根文件与已删除版本的 `RESTORED` 事件，根文件正常时仅恢复版本行。
- 物理清理：级联删除的版本行同样写入 `delete_time`，复用 `RecyclePurgeTask` 到期清理。
- 测试：仓储删除级联、恢复版本、已删除版本映射；应用层根文件级联删除/锁定拒绝、
  版本行单删、整组恢复、根文件正常时单版本恢复。

## 五、验证结果

`mvn -o test`（`whale-backend/whale-services/whale-service-file`）→ 13 个模块 BUILD SUCCESS，
321 项单测零失败（批次 11 为 316，本轮新增 5 项），全部模块 checkstyle 0 violation。

## 六、已修正问题项

- 恢复配额原按目标行自身大小计入，根文件恢复会低估组内版本占用 → 改为
  `sumDeletedSizeOfGroup` 统计组内待恢复合计，与恢复动作一致。
- 恢复事件的版本行必须在恢复前读取，否则 `deleted = 1` 条件失效 → `findDeletedVersions`
  在调用仓储恢复前完成查询。
- 删除版本行若复用根文件逻辑会误查版本组 → 以 `parentId` 判定：版本行只删除自身并只发布一次事件。

## 七、待做项

- 回收站展示版本：如需单独找回某个历史版本，需要版本级回收站视图与恢复入口。
- 恢复根文件会一并恢复此前单独删除的版本（当前口径），如需保留「删除意图」需记录级联来源。
- 版本数上限、旧版本清理与配额释放策略。
- 并发删除/恢复同一版本组的临界区仍需唯一约束或按根文件加锁兜底。
