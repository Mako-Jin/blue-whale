# Wave 6.06：孪生阈值告警联动（后端评估 / SSE 告警帧 / 前端告警中心）

> 状态：**M0 已落地（2026-11-10）** · 上游：Wave 6.05 已交付（流式解读 / 可视化拾取 / internal_api 源）
> 核心动机：`propertyMap.thresholds` 目前只驱动前端变色，后端对阈值越界零感知——
> 本波让 IoT→孪生→告警形成数据闭环，用户不盯着 3D 场景也能收到越界通知。

## 一、既有契约回顾

- `propertyMap` 值对象可带 `thresholds: [t1, t2]`，前端语义：
  **低于 t1 绿（normal）/ t1~t2 橙（warn）/ 高于 t2 红（critical）**；
- 运行时差分口径：绑定粒度 `online + values` 指纹变化才推 `binding` 帧；
- 事件流：`TwinStreamService` 单向 SSE（`/twin/stream/scenes/{id}/events`），
  帧类型 `state` / `binding` / `marker`。

## 二、里程碑

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M0 | 后端阈值评估器 + SSE 告警帧（EVENT_ALERT）：级别变化才推（升级与恢复） | ✅ 2026-11-10 |
| M1 | 告警持久化：`twin_alert` 表（Flyway）+ 触发落库 + 场景告警查询/未恢复列表接口 | 待开始 |
| M2 | 前端告警中心：查看器告警角标 + 告警抽屉（实时帧消费 + 历史接口） | 待开始 |

## 二点五、M0 落地清单（2026-11-10）

**whale-twin 后端**：

- `support/TwinThresholdEvaluator`（新增，纯函数）：propertyMap `thresholds`
  + 实时态取值 → 状态键级别（normal/warn/critical）；口径与前端配色一致
  （`[t1, t2]`：`>=t2` 红 / `>=t1` 橙 / 其余绿；单阈值为 warn 线）；
  仅数值参与比较（Number 或可解析数值字符串，负数合法），非数值/缺阈值跳过
- `dto/BindingAlertVO`（新增）：告警帧条目（bindingId/entityId/entityCode/
  stateKey/value/previous/level/thresholds/ts）
- `TwinStreamService`：新增 `EVENT_ALERT = "alert"`
- `TwinBindingRuntimeService.publishAlerts`：差分推送后对本 tick 拉到态的
  绑定逐键评估；级别表 `场景→绑定→状态键→级别`，级别不变不推；首评越界
  （previous 空）与升级/降级/恢复都推；**离线静默清级**（上下线由 binding
  帧表达，恢复在线后重新首评）；已删除绑定自动清理级别表；
  `currentStates` 快照路径不产生告警

**验证**：whale-twin 76/76 全绿（评估器 8 例 + 运行时告警 4 例新增）；
Checkstyle 0 违规（私有构造补 Javadoc 一处后通过）。

**M0 边界**：不落库（M1）；告警只在 refreshScene（有活跃订阅者）时评估，
无人订阅的场景不累积告警——持久化后 M1 需要考虑"无订阅者也不丢告警"的
口径（落库与订阅解耦）。

## 三、M0 设计要点

1. **`support/TwinThresholdEvaluator`**（新增，纯函数）：
   输入 propertyMap + values，输出 `Map<stateKey, level>`；
   仅数值参与比较（非数值/缺 t2 降级为 warn 上界缺失时的单阈值口径）；
   thresholds 缺失的键不产生级别。
2. **级别状态跟踪**：运行时维护 `场景 → 绑定 → (stateKey → level)`；
   级别未变不推；normal→warn/critical 为升级，warn/critical→normal 为恢复，
   均推告警帧；绑定删除/离线时清理并按恢复口径处理。
3. **SSE 帧**：新增 `EVENT_ALERT = "alert"`；payload
   `{sceneId, ts, items:[{bindingId, entityId, entityCode, stateKey, value, level, previous, thresholds, ts}]}`。
4. **解耦**：告警评估挂在 `refreshScene` 差分推送之后（独立于 binding 帧）；
   快照接口 `currentStates` 不产生告警。
5. **M0 边界**：不落库（M1 做持久化与历史查询）；单值阈值，不做多条件组合。

## 四、验收

- 阈值评估单测：三档判定 / 非数值跳过 / 单阈值 / thresholds 缺失；
- 运行时单测：首评全量、级别不变不推、升级推、恢复推、离线清级；
- whale-twin 全量测试绿；Checkstyle/ArchUnit 0 违规。
