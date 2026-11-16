# whale-file 能力扩展（批次 32：到期分享链接清理）

## 一、范围与决策

分享链接有有效期与下载次数上限，但失效后记录永久留在 `file_share`：过期分享按月累积，
既拖慢分页查询，也让「已失效但仍可查」的链接在列表中干扰运营。本批次新增定时清理任务，
物理删除到期超过保留期的分享记录，沿用三个既有清理任务的调度与审计口径。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 清理任务 | ✅ | `file.share-cleanup`，物理删除过期分享 |
| 保留期 | ✅ | 到期后再保留 N 天（默认 7），便于短期追溯；`<=0` 到期即删 |
| 批量删除 | ✅ | 单批 200 条循环至不足一批，避免长事务 |
| 审计 | ✅ | `DELETE` / `file_share` / 运行级摘要 |
| whale-job 种子 | ✅ | `V15__seed_file_share_cleanup.sql`，`init.sql` 同步 |
| 表结构 / 权限码 | ✅ 不变 | 无迁移、无新接口 |

**不做**：撤销分享的清理。撤销只把状态置为已撤销、`expire_time` 仍是未来时间，
提前删除会丢失「谁在何时撤销」的痕迹，等其自然到期后按同一规则清理即可。

## 二、设计

- 仓储端口新增 `purgeExpiredBefore(threshold, batchSize)`，Mapper 使用
  `DELETE FROM file_share WHERE expire_time IS NOT NULL AND expire_time < #{threshold} LIMIT #{limit}`
  ——批次删除防止一次删除过多行造成长事务与主从延迟；
- 任务逻辑（`ShareCleanupTask#purgeExpired`）：
  1. 开关关闭返回 `skipped: purge disabled`；
  2. `threshold = now - retentionDays`（`<=0` 表示不额外保留）；
  3. 循环删除直到单批返回数小于批大小，累计条数；
  4. 返回 `purged=n`；
- 异常上抛给调度中心记录失败并重试（与另三个清理任务一致）；
- 审计：`@AuditLog(operation="到期分享链接清理", type=DELETE, targetType="file_share",
  recordParams=false, recordResult=true)`；
- 配置项：`whale.file.share.purge-enabled`（默认开启）、
  `whale.file.share.retention-days`（默认 7）、`whale.file.share.purge-batch-size`（默认 200），
  cron 由 `@ScheduledJob` 从 `whale.file.share.purge-cron` 绑定。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 任务 | 开关关闭返回跳过摘要；到期即删语义的阈值传入；多批累计；异常上抛 |
| 任务 | 反射断言 `@AuditLog(DELETE, file_share)` 与不记参数、记结果 |
| 仓储 | `purgeExpiredBefore` 透传阈值与批大小并返回影响行数 |
| 调度种子 | whale-job 迁移脚本命名/递增校验通过 |
| 回归 | file 与 whale-job 模块单测通过 |

## 四、完成情况

- 仓储端口 `FileShareRepository#purgeExpiredBefore(threshold, batchSize)`，Mapper 使用
  `DELETE ... WHERE expire_time IS NOT NULL AND expire_time < #{threshold} LIMIT #{limit}` 按批物理删除；
- `ShareCleanupTask#purgeExpired`：开关关闭返回 `skipped: purge disabled`；`retentionDays`
  换算为阈值（`<=0` 到期即删）；循环删除至单批不足一批，返回 `purged=n`；异常上抛；
- 配置：`whale.file.share.purge-enabled`（默认 true）、`retention-days`（默认 7）、
  `purge-batch-size`（默认 200，`@Min(1)`）；cron 由 `whale.file.share.purge-cron` 绑定；
- 审计：`@AuditLog(operation="到期分享链接清理", type=DELETE, targetType="file_share",
  recordParams=false, recordResult=true)`；
- whale-job：新增 `V15__seed_file_share_cleanup.sql`（按 `job_code` 幂等），`init.sql` 同步；
- 表结构、权限码、接口无变化。

## 五、验证结果

- `mvn -o test`（`whale-service-file`）→ 13 个模块 BUILD SUCCESS，396 项单测零失败，
  checkstyle 0 violation；分布：domain 44、infrastructure 66、application 170、plugins-local 12、
  plugins-minio 10、architecture 4、web 90；
- 本轮新增 6 项：`ShareCleanupTaskTest` 5（开关跳过、分批累计、保留期阈值、异常上抛、审计注解）、
  `FileShareRepositoryImplTest#shouldPurgeExpiredShares` 1；
- `mvn -o test`（`whale-job`）→ BUILD SUCCESS，102 项单测零失败，checkstyle 0 violation，
  V15 通过迁移脚本命名/递增校验；
- 首次运行在 web 模块遇到 JVM 原生内存不足（`hs_err_pid19320.log`，机器内存压力，非用例失败），
  重跑通过；崩溃文件已删除。

## 六、已修正问题项

- Mapper 注解 SQL 初稿把 `<` 写成 XML 转义 `&lt;`：注解 SQL 不做 XML 解析，会原样下发导致语法错误，
  改为字面 `<`；
- 未实现「一次删除全部到期分享」：改为 `LIMIT` 分批，避免长事务与主从延迟；
- 撤销分享不提前删除：其 `expire_time` 仍在未来，保留至自然到期后按同一规则清理，以免丢失撤销痕迹。

## 七、待做项

- 仅剩联调项：审计落库（`whale.audit.forward` 与 `audit_log` 写入）、打包下载网关超时与审计目标
  ID 截断、whale-file 多实例调度（执行器注册/远程触发/日志回捞）、V8 迁移前存量查重；
- 可选后续：上传策略热更新、多段 Range、rapidUpload 同用户同文件去重语义。

