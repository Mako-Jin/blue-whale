# whale-file 能力扩展（批次 23：版本回收站与单版本找回）

## 一、范围与决策

批次 22 支持逻辑删除单个历史版本，但这些行在文件回收站中不可见（回收站只列根文件），
也没有找回入口。本批次补齐「删除 → 查看 → 找回」闭环。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 已删除版本列表 | ✅ | `GET /file/v1/{id}/versions/deleted`，仅列版本行 |
| 单版本找回 | ✅ | `POST /file/v1/{id}/versions/{targetVersion}/restore`，配额内恢复 |
| 根版本 | ❌ | 根文件删除/恢复走原有回收站接口，不在本接口范围 |
| 配额 | ✅ | 恢复前按该版本字节数校验个人/租户/桶配额 |
| 权限 | ✅ | 列表 `sys:file:list`，找回 `sys:file:delete`（与文件恢复一致） |
| 删除时间展示 | ❌ | 聚合与响应暂不含 `delete_time`；见待做项 |
| 数据库变更 | ❌ | 复用 `deleted`、`delete_time` |

**前置条件**：文件本身必须未删除（`resolveVersionRoot` 只查未删除根文件）；整组在回收站时
需先恢复文件，再找回版本。

## 二、设计

- 列表：`resolveVersionRoot` → `findDeletedVersions(rootId)`（已有）→ 过滤 `parentId != null`
  → 映射 `FileVersionResult`；
- 找回：同样定位已删除版本，调用既有 `restoreWithQuotaCheck(versionId, quota)`；
  该方法按 `id = ? OR parent_id = ?` 统计待恢复字节，传版本ID时只计该版本；
- 成功后发布 `RESTORED` 事件并记录恢复指标；配额不足抛出 `PARAM_ERROR`；
- 幂等：目标已恢复时 `restoreWithQuotaCheck` 返回 `false`，按成功处理（与文件恢复一致）。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 应用层 | 已删除版本列表过滤根版本；找回成功调用配额恢复并发布事件；版本不存在返回未找到 |
| Web | GET 已删除列表 200；POST 找回 200；权限注解分别为 `sys:file:list` / `sys:file:delete` |
| 回归 | 版本删除、级联恢复、保留清理用例保持通过 |

## 四、完成情况

- 应用层新增 `deletedVersions` 与 `restoreVersion`：复用 `findDeletedVersions` 与
  `restoreWithQuotaCheck`，找回后发布 `RESTORED` 事件并记录恢复指标；
- 接口新增 `GET /file/v1/{id}/versions/deleted`（`sys:file:list`）与
  `POST /file/v1/{id}/versions/{targetVersion}/restore`（`sys:file:delete`）；
- 列表与找回均过滤根版本；文件整组在回收站时需先恢复文件；
- 测试：已删除列表、找回成功、目标不存在、Web 两个接口与权限注解共 5 项。

## 五、验证结果

`mvn -o test`（`whale-backend/whale-services/whale-service-file`）→ 13 个模块 BUILD SUCCESS，
373 项单测零失败（批次 22 为 368，本轮新增 5 项），全部模块 checkstyle 0 violation。

## 六、已修正问题项

- 控制器漏引 `FileVersionResult`、误用不存在的 `toResponse` 与 `effectiveLimits` 方法名 →
  改为既有 `fileAssembler.toVersionResponse` 与 `uploadLimits`。

## 七、待做项

- 响应增加删除时间（需扩展聚合或专用回收站视图对象）。
- 整组回收站与单版本找回的组合操作（恢复文件时选择性恢复版本）。
- 找回审计与用户通知。
