# whale-file 能力扩展（批次 30：定时清理任务运行审计补齐）

## 一、范围与决策

批次 28 已为回收站到期清理（`file.recycle-cleanup`）接入运行审计，同类的存量版本清理
（`file.version-cleanup`）与孤儿对象对账（`file.orphan-reconcile`）仍只留业务日志。本批次按同一
口径补齐，让三个会物理删除数据的定时任务都有可追溯的执行记录。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 存量版本清理 | ✅ | `VersionCleanupTask#cleanup` 运行审计 |
| 孤儿对象对账 | ✅ | `OrphanReconcileTask#reconcile` 运行审计 |
| 审计通道 | ✅ 复用 | `@AuditLog` + 既有 `audit_log`，无表/迁移/配置变更 |
| 回收站清理 | ✅ 不变 | 批次 28 已接入 |
| 权限码 / 接口 / 业务逻辑 | ✅ 不变 | 仅注解与测试 |

**口径**：与批次 28 一致——`recordParams=false`（任务无请求参数）、`recordResult=true`
（执行摘要写入 `result_body`）；无操作者，租户按切面缺省记为 0；单条明细仍留在业务日志。

## 二、设计

| 任务 | 操作描述 | 类型 | 对象类型 | 摘要 |
| --- | --- | --- | --- | --- |
| `VersionCleanupTask#cleanup` | 存量历史版本清理 | `DELETE` | `file_info` | `pruned=n` / `skipped: ...` |
| `OrphanReconcileTask#reconcile` | 孤儿对象对账 | `DELETE` | `storage_object` | `orphanObjects=n, danglingMetadata=n, deleted=n` / `skipped: ...` |

- 孤儿对账默认只统计、不删除，但开启 `delete-orphan-objects` 后会物理删除对象，属不可逆操作，
  故按 `DELETE` 审计；`deleted=0` 与 `deleted=n` 的区分由摘要体现；
- 两个任务均为 `@ScheduledJob`，注册的是容器 Bean（AOP 代理），进程内调度与调度中心远程触发
  都会经过审计切面；
- 开关关闭时任务提前返回跳过摘要，审计同样留痕，便于运维区分「未开启」与「无数据」。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 版本清理 | 反射断言 `cleanup` 标注 `@AuditLog`，`type=DELETE`、`targetType=file_info`、不记参数、记结果 |
| 孤儿对账 | 反射断言 `reconcile` 标注 `@AuditLog`，`type=DELETE`、`targetType=storage_object`、不记参数、记结果 |
| 回归 | 两个任务既有行为用例与 file 模块全量单测通过 |

## 四、完成情况

- `VersionCleanupTask#cleanup` 标注 `@AuditLog(operation="存量历史版本清理", type=DELETE,
  targetType="file_info", recordParams=false, recordResult=true)`；
- `OrphanReconcileTask#reconcile` 标注 `@AuditLog(operation="孤儿对象对账", type=DELETE,
  targetType="storage_object", recordParams=false, recordResult=true)`；
- 两个任务均为 `@ScheduledJob`，容器 Bean（AOP 代理）由调度注册器注册，两种调度模式都经过切面；
- 无新增表、迁移、配置项、权限码，业务逻辑与摘要格式不变。

## 五、验证结果

- `mvn -o test`（工作目录 `whale-backend/whale-services/whale-service-file`）→
  13 个模块 BUILD SUCCESS，385 项单测零失败，checkstyle 0 violation；
- 单测分布：domain 44、infrastructure 65、application 162、plugins-local 12、
  plugins-minio 10、architecture 4、web 88；
- 本轮新增 2 项：`VersionCleanupTaskTest#shouldAnnotateCleanupWithAuditLog`、
  `OrphanReconcileTaskTest#shouldAnnotateReconcileWithAuditLog`；
- 两个任务既有的开关跳过、计数摘要、异常上抛用例全部通过。

## 六、已修正问题项

- 三个会物理删除数据的定时任务此前审计覆盖不一致（仅回收站清理有运行审计）；本批次补齐后
  口径统一为「运行级摘要 + 明细业务日志」。
- 孤儿对账默认只统计，但删除开关开启后不可逆，故按 `DELETE` 而非 `QUERY` 审计，
  避免运维按查询类审计的保留策略低估其风险。

## 七、待做项

- 审计落库联调：确认 `whale.audit.forward` 在目标环境的取值与 `audit_log` 实际写入；
- 既有候选：outbox 可靠领域事件、分块加密与密钥轮换、异步打包下载。

