# whale-file 能力扩展（批次 22：单版本删除）

## 一、范围与决策

版本能力已覆盖上传、列表、回滚、保留上限与定时清理，但缺少「删除指定历史版本」入口：
用户无法主动清掉误传或不再需要的历史版本，只能等待保留策略清理。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 删除指定历史版本 | ✅ | 逻辑删除，释放配额，进入回收站到期物理清理 |
| 根版本保护 | ✅ | 根版本不可单独删除，需删除整个文件 |
| 最新版本保护 | ✅ | 最新版本对应当前内容，不可删除；需先回滚或上传新版本 |
| 权限 | ✅ | 复用 `sys:file:delete`，不新增权限码 |
| 归属校验 | ✅ | 与版本列表/回滚一致：上传者或 `sys:file:manage` |
| 物理删除 | ❌ | 逻辑删除后由回收站到期任务统一清理 |
| 数据库变更 | ❌ | 复用 `deleted`、`delete_time` |

**接口**：`DELETE /file/v1/{id}/versions/{targetVersion}`，与回滚路径保持同一「版本号」维度。

## 二、设计

- 仓储新增 `deleteVersionById(id)`：`UPDATE file_info SET deleted = 1, delete_time = NOW(),
  update_time = NOW() WHERE id = ? AND parent_id IS NOT NULL AND deleted = 0`；
- 应用层 `deleteVersion(id, targetVersion, requesterId, canManage)`：
  1. `resolveVersionRoot` 回溯根文件并做归属/权限校验；
  2. 在未删除版本中按版本号定位目标，不存在 → `NOT_FOUND`；
  3. 目标为根版本 → `PARAM_ERROR`（提示删除整个文件）；
  4. 目标为最新版本 → `PARAM_ERROR`（提示先回滚或上传新版本）；
  5. 逻辑删除并发布 `DELETED` 事件。
- 并发：最新版本判断与删除非原子，极端并发下可能删除刚成为最新的版本；本轮接受，
  记录为待做项（可用条件删除兜底）。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 应用层 | 删除历史版本成功并发布事件；根版本拒绝；最新版本拒绝；版本不存在返回未找到；无权限/非本人拒绝 |
| 仓储 | 逻辑删除版本行命中/未命中 |
| Web | MockMvc 删除成功返回 200，权限注解为 `sys:file:delete` |
| 回归 | 版本上传/列表/回滚/保留清理用例保持通过 |

## 四、完成情况

- 仓储新增 `deleteVersionById`，Mapper 带 `parent_id IS NOT NULL AND deleted = 0` 条件；
- 应用层 `deleteVersion`：回溯根文件 → 定位目标 → 拒绝根版本/最新版本 → 逻辑删除并发布
  `DELETED` 事件；
- 接口新增 `DELETE /file/v1/{id}/versions/{targetVersion}`，权限复用 `sys:file:delete`；
- 测试：删除成功/根版本/最新版本/不存在、仓储逻辑删除、Web 删除与权限注解共 4 项。

## 五、验证结果

`mvn -o test`（`whale-backend/whale-services/whale-service-file`）→ 13 个模块 BUILD SUCCESS，
368 项单测零失败（批次 21 为 364，本轮新增 4 项），全部模块 checkstyle 0 violation。

## 六、已修正问题项

- 无。本轮实现与单测一次通过。

## 七、待做项

- 删除最新版本的产品语义与「回滚即切换当前内容」的统一设计。
- 并发删除与上传的原子性（条件删除 / 根级锁）。
- 版本级回收站视图与单版本找回。
- 删除审计与通知。
