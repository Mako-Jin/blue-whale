# Wave 5.80 M2-4..M2-8：PDM 变更通知（ECN）、追溯、审批回调与生效任务规格说明

> 状态：**已完成（2026-11-05）**
> 日期：2026-11-05
> 上游计划：[Wave 5.80 PDM 全景实施计划](../../plans/2026-11-04-wave5.80-pdm-panorama.md) §八 M2-4..M2-10
> 前置：[M2-3 变更单 ECO](../../specs/2026-11-05-wave5.80-pdm-m2-eco/spec.md)（提交 `7b4e2939`）
> 边界：审批引擎在 OA/Workflow；消息投递在 `whale-message`；PDM 只保存业务事实、追溯与同步状态。

---

## 一、目标与范围

### 1.1 目标

1. **ECN**：由已审批通过的 ECO 生成，登记生效日期、通知对象、通知渠道与内容；生效时把同对象更低版本置 `OBSOLETE`。
2. **OA 集成**：ECR/ECO 提交即发起审批，`approvalInstanceId`/`approvalStatus`/`approvalSyncTime`/`approvalSource` 落库；内网回调幂等；工作流不可用时降级人工审批。
3. **变更追溯**：`pdm_chg_trace` 只追加，覆盖 ECR/ECO/ECN 全部状态流转、版本变化与旧版本作废；按对象查时间线。
4. **到期生效任务**：每日扫描到期 ECN，数据库行锁保证集群单实例执行，重复执行幂等，失败告警。
5. **通知**：ECN 已通知/已生效经 `whale-message` 投递，成功与失败均落 `pdm_chg_notify_log`，失败可重试。
6. **权限/菜单**：963–969、菜单 192–194；前端增变更通知与追溯两页。

### 1.2 不做（本阶段）

- 审批定义管理与会签/加签（归 `whale-workflow` / OA）。
- 变更单实体表的物理删除与历史重算。
- 文档/BOM 新版本的正文发布（由既有文档/BOM 发布流程完成，本阶段只登记结果版本号与作废旧版本）。
- 集群级分布式锁组件（用数据库行锁替代 Redisson，不新增依赖）。

---

## 二、关键决策

| 编号 | 决策 | 结论 |
|---|---|---|
| D1 | 迁移版本 | 新增 `V9__change_notice.sql`；V7/V8 已被 ECR/ECO 占用，严格递增；仅 `CREATE TABLE IF NOT EXISTS`。 |
| D2 | 表拆分 | `pdm_chg_notice`（ECN 主档）、`pdm_chg_trace`（追溯只追加）、`pdm_chg_notify_log`（投递日志）、`pdm_chg_job_lock`（任务锁）。 |
| D3 | 审批字段扩展 | 幂等 ALTER `pdm_chg_request` / `pdm_chg_order` 增 `approval_instance_id`、`approval_status`、`approval_sync_time`、`approval_source`（逐列 `information_schema` 检查）。 |
| D4 | ECN 来源 | 仅 `APPROVED/EXECUTING/COMPLETED/CLOSED` 的 ECO 可建通知；创建后不可更换来源变更单。 |
| D5 | 生效日期 | `effectiveFrom` 不得早于 ECO 审批通过日；未到生效日调用 `effective` 被拒。 |
| D6 | ECN 状态机 | `DRAFT → NOTIFIED → EFFECTIVE → CLOSED`，旁支 `CANCELLED`；`notify` 仅草稿、`close` 仅已生效、`cancel` 仅草稿。 |
| D7 | 生效幂等 | 已 `EFFECTIVE` 再次调用直接返回详情，不重复作废旧版本、不重复发消息。 |
| D8 | 旧版本作废 | 按 ECO 已完成任务的目标对象与结果版本号，把同文档/BOM **版本序号更低**的 `RELEASED` 记录置 `OBSOLETE`；目标版本不存在或非已发布时 `log.warn` 跳过。 |
| D9 | 通知失败不回滚 | 消息投递包在 try/catch 内，异常只落 `pdm_chg_notify_log(FAILED)` 与 `log.error`，ECN 状态与旧版本作废照常提交。 |
| D10 | 重复回调 | 以 `approvalStatus` 相等判定直接返回；未知实例号静默忽略，不抛错。 |
| D11 | 审批降级 | 未配置流程定义（`pdm.change.definition-id <= 0`）或工作流不可用时，`approval_source=MANUAL`，人工审批仍由权限码保护。 |
| D12 | 单实例锁 | `pdm_chg_job_lock`：`INSERT IGNORE` 建行 + 条件 `UPDATE ... WHERE locked_until < now` 抢占，未抢到直接跳过本轮。 |
| D13 | 追溯只追加 | `PdmChgTraceRecorder` 只 `insert`，不提供更新/删除入口；对象级追溯展开影响对象 JSON。 |
| D14 | 权限码 | 963 list、964 add、965 edit（含关闭/取消/删除）、966 notify（含重试）、967 effective、968 trace list、969 notify log。 |
| D15 | 菜单 | 192 变更通知、193 变更追溯、194 通知日志。 |
| D16 | 角色 | 复用 21105 `pdm_change_manager`；`tenant_admin/pdm_manager/pdm_change_manager` 获 963–969，`pdm_member` 仅 963/968。 |
| D17 | 内网端点 | 回调 `POST /pdm/internal/change/approval-callback` 无权限码；网关 `/pdm/internal/**` 已列入 internal-deny，对外 403。 |

---

## 三、接口清单

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/pdm/change/notice/page` | `sys:pdm:change:notice:list` | 分页查询 |
| GET | `/pdm/change/notice/{id}` | `sys:pdm:change:notice:list` | 详情 |
| POST | `/pdm/change/notice` | `sys:pdm:change:notice:add` | 创建 |
| PUT | `/pdm/change/notice/{id}` | `sys:pdm:change:notice:edit` | 修改（仅草稿） |
| DELETE | `/pdm/change/notice/{id}` | `sys:pdm:change:notice:edit` | 删除（仅草稿） |
| POST | `/pdm/change/notice/{id}/notify` | `sys:pdm:change:notice:notify` | 发送通知 |
| POST | `/pdm/change/notice/{id}/retry-notify` | `sys:pdm:change:notice:notify` | 重试失败投递 |
| POST | `/pdm/change/notice/{id}/effective` | `sys:pdm:change:notice:effective` | 生效并作废旧版本 |
| POST | `/pdm/change/notice/{id}/close` | `sys:pdm:change:notice:edit` | 关闭 |
| POST | `/pdm/change/notice/{id}/cancel` | `sys:pdm:change:notice:edit` | 取消（仅草稿） |
| GET | `/pdm/change/notice/{id}/notify-logs` | `sys:pdm:change:notice:log` | 投递日志分页 |
| GET | `/pdm/change/trace/page` | `sys:pdm:change:trace:list` | 追溯分页 |
| GET | `/pdm/change/trace/timeline` | `sys:pdm:change:trace:list` | 对象时间线 |
| POST | `/pdm/internal/change/approval-callback` | 无（内网） | OA 审批状态回调 |

---

## 四、数据模型

| 表 | 关键字段 | 约束 |
|---|---|---|
| `pdm_chg_notice` | `notice_no`、`eco_id`、`title`、`content`、`impact_json`、`notify_targets`、`notify_channels`、`effective_from`、`status`、`notified_time`、`effective_time`、`close_time` | `uk_chg_notice_no(tenant_id, notice_no)` |
| `pdm_chg_trace` | `biz_type`、`biz_id`、`biz_no`、`object_type`、`object_id`、`object_code`、`action`、`from_version`、`to_version`、`content`、`operator_id`、`trace_time` | 索引 `idx_trace_biz`、`idx_trace_object` |
| `pdm_chg_notify_log` | `notice_id`、`event_type`、`recipients`、`title`、`content`、`status`、`retry_count`、`last_error`、`idempotency_key` | `uk_notify_idem(tenant_id, idempotency_key)` |
| `pdm_chg_job_lock` | `lock_key`（主键 INPUT）、`owner`、`locked_until` | 主键 `lock_key` |

---

## 五、验收口径

1. 未审批通过的 ECO 不能创建 ECN；生效日期早于审批通过日被拒。
2. 未到生效日调用生效被拒；已生效重复调用返回同一结果，不重复作废。
3. 旧版本作废只作用于同对象、更低版本序号、状态为 `RELEASED` 的记录。
4. 通知投递失败不影响 ECN 状态与作废结果，日志可查、可重试。
5. 同一 `approvalInstanceId` 重复回调不重复流转；未知实例静默忽略。
6. 定时任务未抢到锁直接跳过；已生效 ECN 不重复处理；单条失败不影响同批其他记录。
7. 追溯记录只追加，按业务/对象/编码/时间可查。
8. `pdm_member` 无新增/编辑/生效/日志权限，越权返回 403。
