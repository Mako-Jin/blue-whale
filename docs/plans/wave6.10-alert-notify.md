# Wave 6.10：告警外发（把「页面可见」升级为「主动触达」）

> 状态：**M0 已落地（2026-11-10）** · 上游：6.06 落库 → 6.07 规则/确认 → 6.08 无头消费/总览 → 6.09 行级角标
> 缺口：到现在为止，告警只对「正在看页面的人」可见。夜里没人开页面时，
> 告警虽然落库了，但没有任何人会被通知到——这是告警体系最后的断点。

## 一、渠道选型

| 渠道 | 结论 |
|---|---|
| 站内信 / 邮件 / 短信 / IM | **不重造**：`whale-message` 已有 `POST /message/internal/send`（`INAPP/EMAIL/SMS/IM` 四通道、幂等键、回执回调），M1 直接复用 |
| Webhook | 平台无现成能力，M0 自建（运维配置的外部地址，最通用的对接方式） |
| 前端 | M2 做「通知规则 + 投递状态」入口 |

## 二、里程碑

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M0 | webhook 通道：级别门槛 + 冷却去重 + 虚拟线程异步投递 + 配置开关 | ✅ 2026-11-10 |
| M1 | 站内信通道：复用 `whale-message` 内网发送，投递结果落 `twin_alert_dispatch` | 待开始 |
| M2 | 前端：场景级通知设置（通道/接收人/最低级别）+ 投递记录查看 | 待开始 |

## 三、M0 设计要点

### 1. 触发点

`TwinBindingRuntimeService.publishAlerts` 是告警的唯一出生地（前台 tick 与无头通道都走这里）：
SSE 帧 → 落库 → **外发**。顺序保证「先落库、再外发」，外发失败不影响落库与页面可见性。

### 2. 过滤（`TwinAlertNotifyFilter`）

- **级别门槛**：`min-level` 默认 `warn`（`critical` 可收窄）；`normal`（恢复）M0 不外发；
- **冷却去重**：键为 `sceneId:stateKey:level`，默认 300s 内同键只发一次——
  规则引擎的「持续确认 + 回差」已滤掉毛刺，但 warn→normal→warn 的慢抖动仍会周期性触发，
  冷却窗口保证通知不变成刷屏；
- 内存表上限 4096，超限时按「已过冷却期」清理，避免长跑无界。

### 3. 投递（`TwinAlertWebhookSender`）

- URL 只接受 `http/https`、必须有 host、不得带 userInfo（防把凭据写进 URL）；
  非法地址记 WARN 跳过，**不影响其它地址**；
- 每个地址独立 POST，超时 `timeout-seconds`（默认 5s，连接/读取共用）；
- 可选 `Authorization: Bearer <webhook-token>`，并附带 `X-Twin-Alert-Level` 便于接收端分流；
- **不走 `InternalTokenSupport.restClient()`**：内网令牌不能泄漏给第三方地址，用独立 RestClient。

### 4. 并发与失败策略

- 虚拟线程执行器（对齐 `TwinInsightService` 既有做法）异步投递，tick 不被外部 HTTP 拖慢；
- 失败**不重试**：告警本身有级别变化语义，下次级别变化会再发；无脑重试只会在对方故障时堆请求；
- `@PreDestroy` 关闭执行器；提交被拒（关停中）只记 DEBUG。

### 5. 配置（`whale.twin.alert-notify.*`）

```yaml
whale.twin.alert-notify:
  enabled: false            # 默认关：没有 webhook 配置时不应产生任何出站请求
  min-level: warn
  timeout-seconds: 5
  webhook-urls: []          # 运维配置，不是用户输入
  webhook-token: ""
  cooldown-seconds: 300
```

## 四、验收

- `TwinAlertNotifyFilterTest`：级别门槛 / 冷却窗口 / 不同状态键互不影响 / cooldown=0 不抑制 / 恢复不外发
- `TwinAlertWebhookSenderTest`：成功投递带 Bearer 与级别头 / 非 2xx 返回 false / 非法 URL 不发请求
- `TwinAlertNotifyServiceTest`：开关关闭或未配 URL 时零调用 / 异步投递载荷字段完整 / 冷却期内二次告警被抑制
- 既有 114 例保持绿（默认 `enabled=false`，前台路径行为不变）

## 五、M0 落地清单（2026-11-10）

- `TwinProperties.AlertNotify`：enabled / minLevel / timeoutSeconds / webhookUrls / webhookToken /
  cooldownSeconds，环境变量口子 `WHALE_TWIN_NOTIFY_*`
- `support/TwinAlertNotifyFilter`：级别权重（warn=1 / critical=2）+ 冷却表，`synchronized` 保证
  多线程下冷却判定原子；表超 4096 条时按冷却期清理
- `support/TwinAlertWebhookSender`：独立 RestClient（`SimpleClientHttpRequestFactory` 超时），
  URL 形态校验，逐地址 POST，失败记 WARN 返回 false
- `service/TwinAlertNotifyService`：过滤 → 组包 → 虚拟线程异步投递；`Executor` 可注入（测试用同步执行器）
- `TwinBindingRuntimeService`：落库后调用 `notify`（前台与无头通道同一路径）；
  无头告警同样外发，日志口径不变
- 测试：新增 15 例（filter 7 / sender 3 / notify 服务 5），运行时测试补「落库后调用外发」断言；
  whale-twin **129/129 全绿**
- 两个类都有测试用构造器，生产构造器显式标注 `@Autowired`，避免 Spring 因多构造器找不到无参构造

## 六、后续

- M1 站内信：`POST /message/internal/send`（`channels=[INAPP]`，`idempotencyKey=twin-alert-{alertId}`），
  投递结果与失败原因落 `twin_alert_dispatch`，供 M2 展示；
- 接收人来源：场景 `sceneConfig.alertNotify`（复用既有 JSON 配置，不新增迁移），
  默认回退到「绑定的创建人」；
- 恢复通知（`sendRecovery`）与每日静默时段，等 M2 有设置界面后再开。
