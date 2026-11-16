# whale-file 能力扩展（批次 28：清理 / 回滚 / 找回审计）

## 一、范围与决策

回收站物理清理、版本回滚/删除/找回等操作会改变文件可用性与物理对象，但当前只留日志，
没有可追溯的审计记录：出事后无法回答「谁在什么时候把哪个文件回滚到了哪个版本」。
本批次把这些高危动作接入既有审计通道，不新增表、不新增权限码。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 文件逻辑删除 | ✅ | `DELETE /file/v1/{id}`（移入回收站） |
| 回收站找回 | ✅ | `POST /file/v1/{id}/restore` |
| 批量删除 | ✅ | `POST /file/v1/batch/delete` |
| 版本回滚 | ✅ | `POST /file/v1/{id}/versions/{targetVersion}/rollback` |
| 版本删除 | ✅ | `DELETE /file/v1/{id}/versions/{targetVersion}` |
| 版本找回 | ✅ | `POST /file/v1/{id}/versions/{targetVersion}/restore` |
| 回收站到期清理 | ✅ | `file.recycle-cleanup` 每次执行留一条运行审计 |
| 版本超限清理 / 孤儿对账 | ❌ 后续 | 同类能力，按需在后续批次追加注解 |
| 数据库（file 库） | ✅ 无 | 复用 `V2__audit_log.sql` 已建的 `audit_log` |
| 权限码 | ✅ 无新增 | 不改变既有 `sys:file:*` 口径 |

**决策依据**：`whale-common` starter 已提供 `@AuditLog` 注解 + 切面（异步落库、
事务 afterCommit、敏感字段脱敏、MQ/HTTP/本地三级降级），file 服务库也已由 V2 迁移建好
`audit_log`，因此只需在既有入口标注注解，不重复建设审计落库能力。

## 二、设计

### 2.1 审计通道

- 统一使用 `com.bluewhale.common.audit.annotation.AuditLog`，`module` 缺省取
  `spring.application.name`（`whale-file`），无需显式声明；
- 操作对象类型统一 `target_type=file_info`，`target_id` 取路径上的文件 ID（`#id`）；
- 用户、租户、链路、IP、耗时由切面从网关注入头与请求上下文采集；
- 参数默认记录（`recordParams=true`），`targetVersion` 等路径参数随方法参数进入
  `request_params`，可回答「回滚到哪个版本」；
- 写审计失败只告警，不影响业务主流程；存在事务时在 afterCommit 落库，避免审计指向未提交数据。

### 2.2 标注清单

| 入口 | 操作描述 | 类型 |
| --- | --- | --- |
| `FileController#delete` | 删除文件（移入回收站） | `DELETE` |
| `FileController#restore` | 回收站找回文件 | `UPDATE` |
| `FileBatchController#batchDelete` | 批量删除文件 | `DELETE` |
| `FileVersionController#rollback` | 回滚文件版本 | `UPDATE` |
| `FileVersionController#delete` | 删除文件版本 | `DELETE` |
| `FileVersionController#restore` | 找回文件版本 | `UPDATE` |
| `RecyclePurgeTask#purgeExpired` | 回收站到期物理清理 | `DELETE` |

- 批量删除的 `target_id` 取 `#request.ids`（超长由切面按 64 字符截断），完整 ID 列表在
  `request_params` 中；
- 清理任务无 HTTP 请求与操作者：`recordParams=false`、`recordResult=true`，把执行摘要
  （`purged=n` / `skipped: ...`）写入 `result_body`，租户按切面缺省记为 0（未知），
  单条文件级的清理明细仍保留在业务日志中。

### 2.3 与调度架构的关系

`RecyclePurgeTask` 标注 `@AuditLog` 后成为 AOP 代理对象；`ScheduledJobRegistrar`
注册的是容器中的 Bean 实例（代理），因此进程内调度与调度中心远程触发都会经过切面，
两种模式行为一致。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 控制器 | 反射断言三个控制器的高危方法均标注 `@AuditLog`，`type` / `targetType` / `targetId` 符合约定 |
| 清理任务 | 反射断言 `purgeExpired` 标注 `@AuditLog`，且 `recordParams=false`、`recordResult=true` |
| 回归 | 现有 standalone MockMvc 用例不受注解影响；file 模块全量单测通过 |

## 四、完成情况

- 审计通道：复用 `whale-common` starter 的 `@AuditLog` + `AuditLogAspect`（异步落库、
  afterCommit、脱敏、MQ/HTTP/本地降级），未新增表、迁移、配置项与权限码；
  file 库 `audit_log` 已由 `V2__audit_log.sql` 创建，本次无需迁移；
- Web 入口标注：
  - `FileController#delete`（删除文件（移入回收站），`DELETE`，targetId `#id`）；
  - `FileController#restore`（回收站找回文件，`UPDATE`，targetId `#id`）；
  - `FileBatchController#batchDelete`（批量删除文件，`DELETE`，targetId `#request.ids`）；
  - `FileVersionController#rollback`（回滚文件版本，`UPDATE`，targetId `#id`）；
  - `FileVersionController#delete`（删除文件版本，`DELETE`，targetId `#id`）；
  - `FileVersionController#restore`（找回文件版本，`UPDATE`，targetId `#id`）；
- 清理任务：`RecyclePurgeTask#purgeExpired` 标注 `@AuditLog(recordParams=false, recordResult=true)`，
  每次执行留一条运行审计，执行摘要写入 `result_body`；`ScheduledJobRegistrar` 注册的是容器 Bean
  （代理），进程内调度与调度中心远程触发都会经过切面；
- 公共口径：`target_type=file_info`，用户名/租户/链路/IP/耗时由切面从网关注入头与请求上下文采集，
  `targetVersion` 随方法参数写入 `request_params`；清理任务无操作者，租户按切面缺省记为 0。

## 五、验证结果

- `mvn -o test`（工作目录 `whale-backend/whale-services/whale-service-file`）→
  13 个模块 BUILD SUCCESS，381 项单测零失败，checkstyle 0 violation；
- 单测分布：domain 44、infrastructure 65、application 158、plugins-local 12、
  plugins-minio 10、architecture 4、web 88；
- 本轮新增 4 项：`RecyclePurgeTaskTest#shouldAnnotatePurgeWithAuditLog`（1）、
  `FileAuditLogAnnotationTest`（3，覆盖三个控制器的 6 个高危入口）；
- 既有 standalone MockMvc 用例全部通过，注解不影响请求映射与业务行为。

## 六、已修正问题项

- 设计初稿曾考虑为清理任务逐文件写审计。评估后改为「每次运行一条摘要 + 单文件明细留业务日志」：
  `audit_log` 在 file 库没有本地清理策略，逐条写入会按删除量同阶增长，且清理任务无操作者与请求
  上下文，逐条记录的追溯价值有限；运行级摘要已能回答「何时清理了多少条」。
- 批次 26 遗留的「业务级清理/回滚审计缺失」在本批次关闭。

## 七、待做项

- 版本超限清理（`file.version-cleanup`）与孤儿对账（`file.orphan-reconcile`）的运行审计；
- 审计落库联调：确认 `whale.audit.forward` 开关在目标环境的取值与 `audit_log` 实际写入；
- 既有候选：版本组并发唯一约束（需迁移）、outbox 可靠领域事件、分块加密与密钥轮换、异步打包下载。

