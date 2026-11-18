# whale-job 告警增强：多通道配置化与恢复通知

> 对应扩展路线图第 8 项。目标是让失败告警能同时送到多个 IM 通道，
> 并在任务从连续失败中恢复时补一条「已恢复正常」信号。
> 不改表结构、不改调度语义，复用既有 `job_alert` 流水。

## 1. 现状与缺口

| 项 | 现状 | 缺口 |
|---|---|---|
| 告警通道 | 只有一个 `whale.job.alert.webhook-url` | 想同时发钉钉与飞书只能自己再转一层 |
| 通道协议 | 固定自定义 JSON | 钉钉/飞书机器人要求各自的 `msgtype` / `msg_type` 结构 |
| 恢复通知 | 只有失败、超时、自动暂停 | 恢复后没有任何「已恢复」信号，值班同学需要自行回查 |

## 2. 多通道配置化

### 2.1 配置

```yaml
whale:
  job:
    alert:
      enabled: true
      # 兼容旧配置：channels 未配置时等价于「单个 WEBHOOK 通道 + webhook-url」
      webhook-url: https://example.com/hooks/job-alert
      channels:
        - type: WEBHOOK      # 通用 JSON，字段与旧版一致
          url: https://example.com/hooks/job-alert
        - type: DINGTALK     # 钉钉自定义机器人
          url: https://oapi.dingtalk.com/robot/send?access_token=xxx
        - type: FEISHU       # 飞书自定义机器人
          url: https://open.feishu.cn/open-apis/bot/v2/hook/xxx
        - type: WEBHOOK
          url: https://backup.example.com/hooks/job-alert
          enabled: false     # 单通道开关
```

- `channels` 非空时**完全替代** `webhook-url`，不再回退，避免「以为切了通道实际还在发旧地址」；
- `channels` 为空时回退为「单个 `WEBHOOK` 通道 + `webhook-url`」，既有部署零改动；
- 通道地址为空的条目自动忽略，可按环境留空而不报错；
- 告警总开关 `enabled=false` 时所有通道都不外发，仅落 `job_alert` 流水（与旧行为一致）。

### 2.2 请求体

| 类型 | 请求体 | 说明 |
|---|---|---|
| `WEBHOOK` | `{jobId, jobName, level, message, runId, logId, retryCount, errorMsg, occurTime}` | 与旧版完全一致，已有接收方无需改动 |
| `DINGTALK` | `{"msgtype":"text","text":{"content":"[ERROR] 任务执行失败：..."}}` | 钉钉机器人 text 消息 |
| `FEISHU` | `{"msg_type":"text","content":{"text":"[ERROR] 任务执行失败：..."}}` | 飞书机器人 text 消息 |

文本内容包含级别、摘要、任务名、任务 ID、时间、`runId` 与失败原因，便于在 IM 里直接判断影响面。

### 2.3 投递语义

- 同一事件的多个通道**串行推送**：单通道失败只记录并继续下一个通道，不回滚已成功的通道；
- `job_alert` 只写一条记录，`channel` 字段为本次尝试的通道名拼接（如 `DINGTALK,FEISHU`，上限 32 字符内）；
- 投递状态：全部成功记 `1`，任一失败记 `-1`，摘要写明失败通道数与最后一条错误；
- 冷却资格仍按「租户 + 任务」共享：冷却期内整组通道都不外发，只落 `LOG` 通道流水。

## 3. 恢复通知

### 3.1 触发条件

调度引擎在任务成功执行后清零连续失败计数（`fail_count` 条件更新，只有 `> 0` 时才生效）：

- 更新影响行数 `> 0` → 说明本次是从「连续失败」恢复，发送一次恢复通知；
- 影响行数 `= 0` → 本来就没有失败计数，不发送。

因此「一次失败 → 一次恢复」天然只通知一次，不需要额外的去重状态。

### 3.2 通知内容与通道

- 级别 `INFO`，摘要前缀 `任务执行已恢复`，落 `job_alert` 流水；
- 通道选择与失败告警完全一致（`LOG` 或配置的全部外发通道）；
- **不受冷却限制**：失败告警刚发过、恢复紧随其后时，恢复通知仍然送出；
- `whale.job.alert.recovery-enabled=false` 可整体关闭恢复通知（默认开启）。

### 3.3 边界

| 场景 | 行为 |
|---|---|
| 任务因连续失败被自动暂停，人工启用后成功 | 启用时已清零 `fail_count`，不再补发恢复通知（人工操作本身就是确认信号） |
| 分片任务部分分片失败 | 仍按「一次执行」记失败，不发送恢复通知；恢复需整次执行成功 |
| 告警总开关关闭 | 只落 `job_alert` 流水，不外发 |
| 通道推送失败 | 只回写告警状态，不影响调度与后续执行 |

## 4. 测试要点

- 多通道：配置 2 个启用 + 1 个禁用通道 → 实际推送 2 次，`job_alert.channel` 为 `DINGTALK,FEISHU`；
- 单通道失败：任一通道异常时状态记 `-1`，其余通道仍被尝试；
- 兼容：`channels` 为空 + `webhook-url` 有值 → 按单个 `WEBHOOK` 推送；
- 恢复：失败告警后再恢复 → 推送 2 次（恢复不受冷却影响），第二条告警级别 `INFO`；
- 恢复开关：`recovery-enabled=false` 时不落库、不外发；
- 调度侧：清零影响行数 `> 0` 才调用 `notifyRecovered`，`= 0` 不调用。

## 5. 兼容与回滚

- 无 DDL、无新增表：`job_alert.channel` 从单值扩展为逗号拼接，长度仍在 `VARCHAR(32)` 内；
- 旧配置 `webhook-url` 继续有效，无需改动即可升级；回滚只需回退代码与 `channels` 配置；
- 若接收方对 `channel` 字段做严格枚举校验，需放行逗号拼接值或改读 `level` / `message`。
