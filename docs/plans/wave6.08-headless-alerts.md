# Wave 6.08：无头告警消费（无人订阅也持续评估与落库）

> 状态：**M0 已落地（2026-11-10）** · 上游：Wave 6.07 已交付（规则引擎 / ACK / 前端规则编辑）
> 核心动机：6.06 起告警评估寄生在「场景有活跃 SSE 订阅者」的轮询路径上——
> 没人打开 3D 查看器时不轮询、不评估、不落库，等于**告警依赖人盯着看**。
> 本波把「评估/落库」与「订阅」解耦：无头轮询全场景，SSE 只作为实时可视化的加速带。

## 一、既有契约回顾

- `TwinBindingRuntimeService.tick()` 只遍历 `streamService.activeScenes()`；
- 场景内按 `pollIntervalMs` 节流（`nextDueAt`），差分后推 `binding` 帧、评估后推
  `alert` 帧并落 `twin_alert`；
- `TwinAlertTracker` 按场景维护级别状态（持续确认窗口 + 回差），
  场景不再被刷新即状态自然丢失；
- 告警查询接口全部**按场景**（`/twin/scenes/{id}/alerts`）。

## 二、里程碑

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M0 | 无头轮询：`tick` 追加「无订阅者场景」评估通道（独立节流 + 场景数上限 + 状态保留集清理） | ✅ 2026-11-10 |
| M1 | 跨场景告警总览：未恢复列表 / 级别统计接口（摆脱"必须先选场景"） | 待开始 |
| M2 | 前端：场景列表页全局告警角标 + 总览抽屉（复用 6.06 抽屉样式） | 待开始 |

## 三、M0 设计要点

### 1. 调度模型

```
tick(每 tick-ms)
├─ 前台通道：activeScenes() → refreshScene(sceneId, headless=false)
└─ 无头通道：每 headless-interval-ms 最多一次
   ├─ 取「有启用绑定」的场景（去重，按 ID 升序，上限 max-headless-scenes）
   ├─ 跳过本 tick 已在前台通道刷过的场景（避免同 tick 双拉数据源）
   └─ refreshScene(sceneId, headless=true)
```

- **节流**：无头通道自带 `headless-interval-ms`（默认 15000），
  与 tick 解耦——tick 仍 5s，无头评估 15s 一轮；
- **绑定级节流不变**：仍由 `nextDueAt` 按 `pollIntervalMs` 生效，
  无头通道不会让数据源被更频繁调用；
- **场景数上限**：`max-headless-scenes`（默认 20）防大租户拖垮，
  截断时 DEBUG 日志（每轮 warn 会刷屏）。

### 2. 状态保留集

无头通道跑完后按「前台场景 ∪ 本轮无头场景」清理
`lastSnapshots`（差分指纹）与 `alertTrackers`（级别状态），
避免长跑场景数增长导致内存无界；已被移出保留集的场景下次被订阅时
首帧全量推送（既有语义）。

### 3. 告警可见性

- 无订阅者时 `streamService.publish` 天然空转，`twin_alert` 照常落库；
- 无头通道产生的告警额外打 INFO 日志（`无头告警`），
  保证纯日志链路也能追到「没人看时发生过什么」；
- 配置开关 `enabled=false` 时无头通道整体关闭，行为回退到 6.07。

## 四、验收

- 运行时单测：无订阅场景也评估落库 / 有订阅场景不被无头通道重复拉取 /
  无头通道受 `headless-interval-ms` 节流 / `headless-enabled=false` 时无副作用；
- `TwinBindingService.listScenesWithEnabledBindings`：只取启用绑定、
  只取两种源类型、场景去重与上限；
- 既有 100 例全部保持绿（默认配置下前台路径行为不变）。

## 五、M0 落地清单（2026-11-10）

- `TwinProperties.Runtime` 新增 `headlessEnabled`（默认 true）/ `headlessIntervalMs`
  （默认 15000）/ `maxHeadlessScenes`（默认 20），并在 `application.yml` 给出
  `WHALE_TWIN_HEADLESS_*` 环境变量口子
- `TwinBindingService.listScenesWithEnabledBindings(limit)`：只取 `enabled=1` 的
  两种可轮询源类型绑定 → 实体反查场景 → 去重升序 → 上限截断；
  实体 IN 查询规模超 5000 时跳过本轮（WARN 日志），宁可漏一轮也不打爆数据库
- `TwinBindingRuntimeService`：`tick()` 拆成前台通道 + `tickHeadless(active)`；
  无头通道按间隔自节流、跳过本 tick 已有订阅者的场景（避免双拉数据源），
  跑完按「前台场景 ∪ 本轮无头场景」清理 `lastSnapshots` / `alertTrackers`
- 无头通道产生的告警额外打 INFO 日志（`无头告警：sceneId=... level=...`），
  保证纯日志链路可追溯；落库与 SSE 帧路径完全复用既有代码
- 测试：运行时 4 例（无订阅也评估落库 / 有订阅不重复拉 / 间隔节流 / 开关关闭退回）
  + 绑定服务 2 例（去重与上限 / 无绑定不查实体）；whale-twin **106/106 全绿**
- 已知边界：`nextDueAt`（绑定级到期表）仍按 bindingId 累积，删除绑定的残留项
  由 `bindingId` 复用或进程重启消化，暂不做主动清理（量级小、无功能影响）
