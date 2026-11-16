# whale-file 能力扩展（批次 19：存量历史版本定时清理）

## 一、范围与决策

批次 18 的版本上限只在新版本产生或回滚时触发，上线前已存在的超限历史版本不会被处理。本批次
增加定时任务，按游标扫描全部文件并清理存量超限版本。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 存量超限扫描 | ✅ | 按根文件ID游标分批，避免一次性加载 |
| 定时清理 | ✅ | `@Scheduled`，默认每天 04:30，可配置 cron |
| 开关与批次 | ✅ | `cleanup-enabled` 默认关闭；`cleanup-batch-size` 默认 100 |
| 根版本保护 | ✅ | 复用批次 18 的清理逻辑与 `parent_id IS NOT NULL` 条件 |
| 领域事件 | ❌ | 定时任务无租户上下文，不发布事件，避免产生无效租户事件 |
| 跨租户 | ✅ | 无租户上下文时拦截器不追加条件，可全局清理 |
| 数据库变更 | ❌ | 无 |

**默认关闭**：避免升级后自动删除存量历史版本；需要显式开启并确认 `max-versions` 取值。

## 二、设计

```
VersionCleanupTask#cleanup (cron)
  → FileInfoService#pruneExcessVersions
      → repository.findVersionRootIdsWithExcess(allowed=max-1, afterId, batchSize)
      → 逐个 findById(root) + pruneVersions(root, null, publishEvents=false)
```

- 扫描 SQL：按 `parent_id` 分组统计未删除版本行，`HAVING COUNT(*) > max-versions - 1`，
  按 `parent_id` 升序 + `LIMIT` 游标推进；
- 清理复用批次 18：保留根版本与最近 `max-versions - 1` 个版本，先删对象、再物理删行；
- 单文件清理失败只告警，不中断其他文件；批次取满时继续下一批，取不满即结束；
- 根文件已逻辑删除时跳过该组（交由回收站级联处理），游标仍推进，避免死循环。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 仓储 | 游标查询委派、批次大小规整 |
| 应用层 | 跨文件扫描清理并返回清理数、不发布事件；上限非正直接返回 0 |
| 任务 | 开关关闭不调用；上限非正不调用；开启时调用清理 |
| 回归 | 批次 18 的上传/回滚后清理、`max-versions <= 0`、清理失败隔离用例保持通过 |

## 四、完成情况

- `FileProperties.Version` 新增 `cleanup-enabled`（默认关闭）、`cleanup-batch-size`（默认 100）；
- 仓储新增 `findVersionRootIdsWithExcess`：按 `parent_id` 分组、`HAVING COUNT(*) > max-1`、
  根文件ID升序游标分批；
- `FileInfoService#pruneExcessVersions`：循环扫描超限根文件，复用清理逻辑（不发布事件），
  返回实际物理删除的版本行数；
- `VersionCleanupTask`：`@Scheduled(cron = "${whale.file.version.cleanup-cron:0 30 4 * * ?}")`，
  开关关闭或上限非正时直接返回；
- 根文件已删除时跳过该组，游标继续推进；
- 测试：游标查询、跨文件清理不发布事件、上限非正、任务开关共 6 项。

## 五、验证结果

`mvn -o test`（`whale-backend/whale-services/whale-service-file`）→ 13 个模块 BUILD SUCCESS，
361 项单测零失败（批次 18 为 355，本轮新增 6 项），全部模块 checkstyle 0 violation。

## 六、已修正问题项

- 无。本轮实现与单测一次通过。

## 七、待做项

- 清理审计与用户可见的版本保留说明。
- 定时任务分布式互斥（多实例同时执行时依赖物理删除幂等，仍可能重复扫描）。
- 按租户/文件类型差异化保留策略。
- 被清理版本的回收站与单版本找回。
