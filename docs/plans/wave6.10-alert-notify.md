# Wave 6.10：告警外发（把「页面可见」升级为「主动触达」）

> 状态：**M0 + M1 + M2 已落地（2026-11-10）** · 上游：6.06 落库 → 6.07 规则/确认 → 6.08 无头消费/总览 → 6.09 行级角标
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
| M1 | 站内信通道：复用 `whale-message` 内网发送，投递结果落 `twin_alert_dispatch` | ✅ 2026-11-10 |
| M2 | 前端：场景级通知设置（通道/接收人/最低级别）+ 投递记录查看 | ✅ 2026-11-10 |

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

## 六、M1 落地清单（2026-11-10，站内信通道）

- **配置**（`TwinProperties.AlertNotify`）：新增 `inappEnabled`（默认 false）、
  `messageBaseUrl`（默认 `http://127.0.0.1:8094`）、`maxRecipients`（默认 20，上限 100）；
  yml 口子 `WHALE_TWIN_NOTIFY_INAPP_ENABLED / _MESSAGE_BASE_URL / _MAX_RECIPIENTS`
- **`support/TwinMessageClient`**：调 `POST /message/internal/send`，
  `channels=[INAPP]`、`idempotencyKey=twin-alert-{alertId}`（无 alertId 时退化为
  `twin-alert-{sceneId}-{stateKey}-{ts}`）；信封 `code=0` 才判成功，`data.messageId/status` 回填；
  任何异常折算为失败结果不抛出。**与 webhook 相反，此通道必须带内网令牌**——
  客户端在调用时按 `InternalTokenSupport` 当前配置创建，避免 Bean 早于自动配置初始化时拿到空令牌
- **`support/TwinAlertRecipientResolver`**：`sceneConfig.alertNotify.userIds` 优先，
  为空回退场景 `createdBy`；解析失败只记 WARN 并回退；去重 + 按 `maxRecipients` 截断
- **通知文案**：标题 `【孪生告警·{预警|严重}】{场景名} / {实体编码}.{状态键}`（120 字截断）；
  正文纯文本多行（场景 / 点位 / 级别+上一级别 / 当前值 / 阈值 / 时间），接收端无需渲染
- **`V5__alert_dispatch.sql` + `entity/TwinAlertDispatch` + `mapper` + `service/TwinAlertDispatchService`**：
  每次投递尝试一行（`WEBHOOK` 按地址、`INAPP` 按一次发送），
  状态 `SUCCESS / FAILED / SKIPPED`（无接收人记 SKIPPED 并写明原因）；
  target/error 按列宽截断，写入异常只 WARN——投递记录是旁路
- **编排**：`TwinAlertNotifyService.notify(sceneId, tenantId, alertId, alert)`；
  两通道共用一套级别门槛与冷却过滤（一次级别变化最多触发一轮），
  异步执行内先 webhook 逐地址、再站内信；`TwinAlertService.record` 改为返回告警ID供幂等键使用
- 测试：新增 17 例（notify 服务 +4 / dispatch 3 / resolver 6 / message client 4），
  whale-twin **146/146 全绿**，Checkstyle 通过

## 七、后续（原 M2 规划，已由第八节落地）

- ~~前端：场景级通知设置（通道开关 / 接收人 → `sceneConfig.alertNotify` / 最低级别）~~ ✅ 第八节；
- ~~投递记录查看：基于 `twin_alert_dispatch` 的场景维度列表接口（`status` 过滤 + 时间倒序）~~ ✅ 第八节；
- 恢复通知（`sendRecovery`）与每日静默时段仍未做，属 6.10 之后的独立小波次。

## 八、M2 落地清单（2026-11-10）

**设置存储口径**：复用 `twin_scene.scene_config` 的 `alertNotify` 节点
（`{webhookEnabled, inappEnabled, minLevel, userIds}`），不新增迁移；写回只改 `scene_config`，
**不 bump version、不受「已发布禁止修改」限制**——通知设置是运营配置，编辑锁只管几何/绑定。

**叠加规则（单向收紧）**：场景可以把某通道关掉、把门槛抬到 `critical`；
不能突破全局开关与全局门槛（全局没开时场景设什么都不发）。生效级别取全局与场景中更严者。

**后端**

| 端点 | 说明 |
|---|---|
| `GET /twin/scenes/{id}/alert-notify` | 原始配置 + 叠加全局后的 `effective*` 视图 |
| `PUT /twin/scenes/{id}/alert-notify` | 整体覆盖；字段传 `null` = 清除该项、回到跟随全局；`userIds` 去重后上限 20 |
| `GET /twin/scenes/{id}/alert-dispatches` | 投递记录，时间倒序，支持 `status`/`channel` 过滤，`limit` 默认 50 上限 200 |

- `TwinAlertNotifySettingsService`：JSON 合并（保留 sceneConfig 其它键）+ 门槛/通道叠加判定；
  `LambdaUpdateWrapper` 显式 `set(scene_config, null)` 以支持「清空」（`updateById` 会跳过 null）
- `TwinAlertNotifyService`：外发前先读场景设置，通道开关与门槛在冷却过滤**之前**判定
  （避免被拦掉的告警占用冷却位）
- `TwinAlertDispatchVO` + `TwinAlertDispatchService.list`：ID 字符串化、时间 ISO-8601

**前端**（`whale-frontend/platform-apps/whale-twin`）

- `api/twin.ts` 新增 `notifyApi`（getSettings / saveSettings / dispatches）与三个类型
- 场景管理页行操作新增「通知」：抽屉内两个通道开关（关闭语义 = `false`，开启 = 清除回到跟随全局）、
  最低级别单选（跟随全局 / 仅严重）、接收人输入（逗号分隔用户ID，留空回退创建人）；
  抽屉头部「投递记录」打开弹层，可按结果过滤、最多 100 条

**验证（2026-11-10 实机）**

- 单测：157/157 全绿（M2 新增 11 例：设置服务 7 + 投递列表 2 + 外发收紧 2），Checkstyle 通过
- 起服：`docker compose --profile aiot up -d twin` → healthy，Flyway V1–V5 成功
- 接口：建场景 → 读设置（全 null / `fallbackToCreator=true`）→ 写设置
  （`webhookEnabled=false`/`minLevel=critical`/`userIds=[7,8]`，`effectiveMinLevel` 变 critical）
  → 场景详情可见 `scene_config` 落库 → `PUT {}` 清空后 `scene_config=null`（不是 `{}`）→ `version` 仍为 1
  → `alert-dispatches?status=FAILED` 返回空数组

**同轮顺带修掉的启动阻塞（twin 首次实机起服暴露）**

1. `JAVA_OPTS` 写 `-XX:MaxMetaspaceSize:256m`（冒号）→ JVM 拒启、容器无限重启（与 bebff078 同一处笔误）。
2. `TwinInsightService` 注入 `com.fasterxml.jackson.databind.ObjectMapper`
   ——Spring Boot 4 不再提供该 Bean，容器启动即 `APPLICATION FAILED TO START`；
   改为 `tools.jackson.databind.ObjectMapper`（Boot 4 自动装配），异常改捕 `JacksonException`。

**未做**：恢复通知（`sendRecovery`）、每日静默时段、按用户选择器（当前是手填用户ID）。
