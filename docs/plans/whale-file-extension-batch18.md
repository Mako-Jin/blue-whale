# whale-file 能力扩展（批次 18：版本保留策略与旧版本清理）

## 一、范围与决策

版本能力已支持新版本上传、回滚、级联删除恢复，但历史版本只增不减：长期高频更新的文件会持续
占用存储与配额。本批次增加「每文件版本数上限」并在新版本产生后自动清理最旧版本。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 版本数上限配置 | ✅ | `whale.file.version.max-versions`，默认 20，含根版本 |
| 新版本后自动清理 | ✅ | 上传新版本、回滚成功后清理超出上限的最旧版本 |
| 物理清理 | ✅ | 先删对象再物理删除版本行，释放存储与配额 |
| 根版本保护 | ✅ | 根版本永不清理，上限按「根版本 + 最近 N-1 个版本」保留 |
| 清理失败隔离 | ✅ | 单个版本清理失败只告警，不影响本次上传/回滚 |
| 定时清理存量 | ❌ | 仅在新版本产生时触发；见待做项 |
| 清理审计/通知 | ❌ | 见待做项 |
| 数据库变更 | ❌ | 无 |

**语义**：`max-versions = 2` 表示保留根版本与最近 1 个版本；`max-versions <= 0` 表示不限制。
上限配置为 `1` 时等价于不保留历史版本（新版本上传后立即清理）。

## 二、设计

- `FileProperties` 新增 `whale.file.version.max-versions`；
- `FileAssetRepository` 新增 `purgeVersionById(id)`：仅删除 `parent_id IS NOT NULL` 的行，
  防止误删根文件；Mapper 对应 `DELETE ... WHERE id = ? AND parent_id IS NOT NULL`；
- `FileInfoServiceImpl#pruneVersions(root, requesterId)`：按版本号倒序取组内未删除版本，
  过滤掉根版本后保留最近 `max-versions - 1` 个，其余逐个：
  1. 删除物理对象（按版本自身落盘类型）；
  2. 物理删除版本行；
  3. 发布 `DELETED` 事件（下游知识库同步移除）。
- `uploadVersion` 与 `rollback` 成功后调用清理；清理异常整体捕获，不改变本次上传结果；
  单个版本失败保留其行，待下次新版本产生时重试（物理对象已删则删除幂等）。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 仓储 | `purgeVersionById` 委派与命中/未命中 |
| 应用层 | 超出上限时清理最旧版本并删除对象；未超限不清理；`max-versions <= 0` 禁用清理；清理失败不影响上传结果 |
| 回归 | 既有上传、版本列表、回滚、删除恢复用例保持通过 |

## 四、完成情况

- `FileProperties` 新增 `whale.file.version.max-versions`（默认 20，含根版本，`<=0` 不限）；
- `FileAssetRepository` 新增 `purgeVersionById`，Mapper 带 `parent_id IS NOT NULL` 条件，
  防止误删根版本；
- `FileInfoServiceImpl#pruneVersions`：按版本号倒序保留根版本与最近 `max-versions - 1` 个版本，
  逐个先删物理对象、再物理删除版本行、最后发布 `DELETED` 事件；
- `uploadVersion` 与 `rollback` 成功后调用清理；整体异常捕获，不影响本次写入；单个版本失败
  只告警并保留行待下次重试；
- 测试：超限清理、上限为 0 不清理、清理失败隔离、仓储物理删除共 4 项。

## 五、验证结果

`mvn -o test`（`whale-backend/whale-services/whale-service-file`）→ 13 个模块 BUILD SUCCESS，
355 项单测零失败（批次 17 为 351，本轮新增 4 项），全部模块 checkstyle 0 violation。

## 六、已修正问题项

- 无。本轮实现与单测一次通过。

## 七、待做项

- 存量文件的定时版本清理任务（当前仅在新版本产生时触发）。
- 清理审计与用户通知、按文件类型/租户差异化保留策略。
- 并发上传时的清理竞争：同一组可能同时清理不同版本，需唯一约束或根级锁兜底。
- 版本回收站与单版本找回（见批次 12 待做项）。
