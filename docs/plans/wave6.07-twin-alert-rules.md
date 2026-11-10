# Wave 6.07：孪生告警规则升级（持续确认 / 回差防抖 / 告警确认）

> 状态：**M0 + M1 + M2 全部落地（2026-11-10）** · 上游：Wave 6.06 已交付（阈值告警 M0 评估帧 / M1 持久化 / M2 前端告警中心）
> 核心动机：当前阈值告警是"瞬时越界即告警"——工业现场传感器毛刺会造成
> 大量瞬时告警，临界值附近来回抖动会造成告警/恢复反复刷屏。本波引入
> 持续时长确认（dwell）与回差区间（hysteresis），并提供告警确认（ACK）。

## 一、既有契约回顾

- `propertyMap` 值对象可带 `thresholds: [t1, t2]`；`v >= t2` critical、
  `v >= t1` warn、其余 normal（与前端配色一致）；
- 运行时 `TwinBindingRuntimeService.publishAlerts`：级别变化才推 `alert` 帧并落库；
- 级别表 `场景 → 绑定 → 状态键 → 级别`，离线静默清级、已删绑定清理；
- 告警表 `twin_alert`（M1）已有 resolved / resolve_time。

## 二、里程碑

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M0 | 规则引擎：propertyMap 支持 `confirmSeconds`（持续确认）与 `recoveryDelta`（回差），状态跟踪器 `TwinAlertTracker` | ✅ 2026-11-10 |
| M1 | 告警确认（ACK）：`twin_alert` 加 ack 字段 + 确认接口 + 未确认过滤 | ✅ 2026-11-10 |
| M2 | 前端：绑定表单规则编辑（确认时长/回差）+ 告警中心确认操作与规则展示 | ✅ 2026-11-10 |

## 三、M0 设计要点

### 1. 规则声明位置

`propertyMap` 每个状态键的对象形态扩展两个可选字段：

```json
{
  "temperature": {
    "stateKey": "temp",
    "thresholds": [50, 80],
    "confirmSeconds": 30,
    "recoveryDelta": 2
  }
}
```

- `confirmSeconds`：**升格**（normal→warn/critical、warn→critical）需要候选
  级别持续该秒数才生效；缺省 0（立即生效，保持既有行为）；上限 3600 钳制；
- `recoveryDelta`：**降格**的回差区间——生效级别为 warn 时需跌破 `t1 - delta`、
  为 critical 时需跌破 `t2 - delta` 才降级；缺省 0（无回差）；负数按 0 处理；
- 降格（含恢复）**不做持续确认**：恢复延迟会延长告警挂起时间，与运维直觉相反。

### 2. 状态跟踪（`support/TwinAlertTracker`）

- 每个绑定每状态键维护：生效级别 + 待确认候选级别 + 候选起始时刻；
- 观测顺序：先按回差修正原始级别（防抖），再做升格确认（dwell）判定；
- 首评语义不变：首次观测建立基线（normal），若首次即越界且 confirm=0，
  会以 `previous=null` 发出告警；confirm>0 时同样需要持续满时长；
- 候选级别变化（warn→critical）重新计时；
- 绑定离线 / 删除：清空该绑定全部状态（与 M0 现有口径一致）。

### 3. 运行时接线

- `alertLevels`（嵌套 Map）替换为 `Map<sceneId, TwinAlertTracker>`；
- `publishAlerts` 改为由 tracker 返回级别变化列表（含首评/升降级/恢复），
  帧内容与落库路径不变。

## 四、验收

- 评估器单测：规则解析（缺省/钳制/负数）、规则随 Level 透出；
- 跟踪器单测：confirm=0 立即升格、confirm>0 需满时长、未满不下发、
  候选切换重新计时、回差内不降级、跌破回差不降级、恢复立即生效、
  首评越界 previous 为空、离线重置；
- 运行时既有告警用例全部保持绿（缺省规则下行为与 6.06 完全一致）。

## 五、M0 落地清单（2026-11-10）

- `TwinThresholdEvaluator`：新增 `Rule(confirmSeconds, recoveryDelta)` 记录，
  `Level` 透出规则；`confirmSeconds` 钳制 `[0, 3600]`、`recoveryDelta` 负数按 0；
  `toNumber` 提升为包内可见供跟踪器复用
- `support/TwinAlertTracker`（新增）：绑定→状态键维护 `生效级别 / 待确认候选 /
  候选起始时刻`；先回差修正（critical 退出线 `t2-delta`、warn 退出线 `t1-delta`），
  再升格确认（候选变化重新计时）；降格与恢复立即生效；`reset` 清绑定、
  `retain` 清已删绑定；`synchronized` 保证并发安全
- `TwinBindingRuntimeService`：`alertLevels` 嵌套 Map 替换为
  `Map<sceneId, TwinAlertTracker>`，`publishAlerts` 消费 `Transition`（首评/升降级/
  恢复语义与 6.06 一致），帧内容与落库路径不变
- 测试：跟踪器 10 例 + 评估器规则 3 例 + 运行时 2 例（确认窗口抑制、
  回差带内维持）；whale-twin **96/96 全绿**

## 六、M1 落地清单（2026-11-10）：告警确认（ACK）

- `V3__alert_ack.sql`（Flyway）：`twin_alert` 增加 `ack`（默认 0）/
  `ack_time` / `ack_by` 与索引 `idx_scene_ack`
- `TwinAlert` 实体与 `TwinAlertVO` 同步扩展（`acked` / `ackTime` / `ackBy`）
- `TwinAlertService.ack(sceneId, alertId, userId)`：先按场景+ID 定位行
  （防跨场景确认），不存在报「告警不存在」、已确认报「该告警已确认」，
  更新后返回最新视图；确认与恢复语义相互独立（ACK 不改 resolved）
- `TwinAlertService.list` 增加 `acked` 过滤参数（可空 = 全部）
- `TwinAlertController`：`POST /twin/scenes/{sceneId}/alerts/{alertId}/ack`
  （`sys:twin:scene` + `@AuditLog`，确认人取 `UserContext.requireUserId`）
- 测试：服务新增 4 例（确认成功 / 告警不存在 / 重复确认 / acked 映射与过滤）；
  whale-twin **100/100 全绿**

## 七、M2 落地清单（2026-11-10）：前端规则编辑与告警确认

**`api/twin.ts`**：

- `TwinAlertVO` 扩展 `acked` / `ackTime` / `ackBy`；`alertApi.ack`

**`ViewerPage.tsx`**：

- 绑定表单新增「告警确认时长（秒）」与「恢复回差」两个可选输入；
  提交时写回 propertyMap（应用到全部带阈值的条目，0 = 移除字段保持
  JSON 干净）；编辑时从首个带阈值条目读出预填
- 告警抽屉：未确认条目（非实时插入项）展示「确认」按钮，成功后刷新
  当前列表；已确认展示蓝色 Tag 与确认人/确认时间
- 验证：`pnpm --filter whale-twin build` 通过（tsc + vite）
