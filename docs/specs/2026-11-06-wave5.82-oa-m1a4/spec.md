# Wave 5.82 OA M1A-4 规格：审批通知、自动催办、状态对账与请假附件校验

> 子计划：Wave 5.82 OA 全景 §6.2 M1A-4
> 上游：M1A-1 审批中心（2026-11-06 已完成）
> 日期：2026-11-06

## 一、目标

1. 审批关键节点产生站内通知：待办、结果、抄送、催办。
2. 运行中且超时未处理的审批自动提醒当前审批人，按天去重。
3. 本地 `RUNNING` 实例与 Workflow 状态定期对账，终态回写并触发业务结果同步。
4. 请假附件强校验：要求附件的假期类型必须 1–5 个附件，编辑时可保留/替换。

## 二、非目标

- 不做邮件/短信渠道编排（沿用消息服务模板渠道配置）。
- 不新增 OA 附件元数据表（复用 `oa_file_ref`）。
- 不实现 OA 文件下载代理（沿用平台文件服务）。

## 三、关键决策

| 决策 | 说明 |
|---|---|
| 通知 best-effort | 投递异常只记 warn，不阻断审批动作与事务提交 |
| 消息幂等 | 消息服务按 `(tenantId, idempotencyKey)` 去重；TODO 键含任务与接收人，RESULT 键含实例与状态，URGE 键含秒级时间，AUTO_URGE 键含日期 |
| 账号解析 | 纯数字按用户ID；其余调用 Auth `/user/internal/directory` 且仅接受 `username` 精确匹配，解析失败跳过并 warn |
| 对账范围 | 仅回写 Workflow 1/2/3 终态；每批 ≤100 个实例（内网接口上限） |
| 自动催办 | 只发站内通知，不重复调用 Workflow 催办接口；开关/阈值/批量可配 |
| 附件元数据 | `oa_file_ref`（`biz_type=LEAVE`、`biz_id=leaveId`）；上传走平台 `/file/upload` |
| 附件编辑 | `fileIds` 为新增文件，`keepAttachmentIds` 为保留引用；不传 `keepAttachmentIds` 表示全部保留 |

## 四、接口与配置

### 通知事件

- `ApprovalWorkflowAttachedEvent`：`attachWorkflow` 首次绑定成功后发布 → 待办 + 抄送通知。
- `ApprovalResultSyncedEvent`（既有）：终态落库后发布 → 结果通知。
- `ApprovalUrgedEvent`：催办接口成功后发布 → 催办通知。

### 定时任务

| 任务 | 默认 cron | 开关 | 其他配置 |
|---|---|---|---|
| 自动催办 | `0 0 */2 * * *` | `whale.oa.approval.auto-urge.enabled=true` | `threshold-hours=24`、`batch-size=100` |
| 状态对账 | `0 */30 * * * *` | `whale.oa.approval.reconcile.enabled=true` | `batch-size=200` |

### 请假命令

```json
{
  "leaveType": "SICK",
  "fileIds": [11],
  "keepAttachmentIds": [1]
}
```

## 五、验收

1. 绑定成功后当前待办人收到一次待办通知，重复绑定不重复发送。
2. 审批终态后发起人收到结果通知；同一状态重复同步不重复发送。
3. 催办指定人只通知该人；未指定时通知全部当前待办人。
4. 自动催办每日最多一次，发起人不被催办。
5. 对账 Job 仅回写 1/2/3，单条失败不影响后续记录。
6. `SICK` 等要求附件类型零附件、或附件超过 5 个时返回 `LEAVE_INVALID`。
7. 普通员工无需 Workflow 权限，仅需 OA 权限 + 文件上传权限。
