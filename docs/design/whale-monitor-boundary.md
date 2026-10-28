# whale-monitor 能力边界（F-06 结案）

> 结论日期：2026-10-28
> 状态：**已结案（能力边界确认，无需继续扩展）**
> 关联待办：`docs/plans/pending-issues-2026-10-27.md` F-06

## 1. 结论

`whale-monitor`（9102，`/monitor/**`）定位为**服务实例只读聚合面**，不是指标采集器、时序库或告警引擎。

- **负责**：经 Nacos 查询服务名、实例列表、实例健康状态，供运维端做“当前有哪些服务、哪些实例在线”的即时视图。
- **不负责**：指标抓取（Prometheus）、指标存储（时序库）、规则评估与告警路由（Alertmanager + `whale-alert`）、日志采集（Loki/ELK）。
- **不落库**：不持有业务库，不写监控历史；所有历史与趋势查询走外部 Prometheus。
- **不新增采集 Agent**：不在服务内起定时抓取线程，避免与 Prometheus 重复采集和双份口径。

## 2. 对外能力

| 能力 | 路径 | 数据来源 | 说明 |
|---|---|---|---|
| 服务列表 | `GET /monitor/service/list` | Nacos Discovery | 服务名、实例数、健康实例数 |
| 实例列表 | `GET /monitor/service/{serviceName}/instances` | Nacos Discovery | 实例 IP/端口/健康状态/元数据 |
| 健康探针 | `GET /actuator/health` | 本服务 | 供 Prometheus `up` 探活 |

> 路径以现有 `MonitorController` 为准；本节只固定**能力口径**，不新增接口承诺。

## 3. 与周边组件的关系

| 组件 | 关系 |
|---|---|
| Nacos | 只读消费其服务/实例 OpenAPI，不写注册数据 |
| Prometheus | 由 Prometheus 主动抓取各服务 `/actuator/prometheus`；`whale-monitor` 不向 Prometheus 推送，也不代理其查询 |
| `whale-alert` | 告警由 `whale-alert` 消费 Alertmanager Webhook 后落库/通知；`whale-monitor` 不参与告警判定 |
| `whale-audit` | 审计与监控分离：`whale-monitor` 不写审计日志 |
| 运维前端 | 只调用只读聚合接口；趋势图直接查 Prometheus HTTP API |

## 4. 验收口径

- 服务启动后 `/monitor/**` 可返回 Nacos 中已注册服务与实例；
- Nacos 中服务下线时，实例列表随之消失或标记不健康，不依赖本地缓存“伪装在线”；
- 不新增任何数据库表、定时采集任务或告警规则；
- Prometheus 配置中保留 `whale-monitor` 抓取目标（`/actuator/prometheus`），仅用于自身健康与 JVM 指标。

## 5. 后续触发条件

只有在同时满足以下条件时才重新立项扩展：

1. 需要“监控历史/趋势”且明确不引入 Prometheus 或现有时序库无法满足；
2. 需要跨服务拓扑、依赖关系图，且 Nacos + Prometheus 现有数据不足以表达；
3. 有明确的存储与保留周期预算（表结构、归档、容量）。

否则保持本边界，新增需求优先落到 Prometheus/Alertmanager 配置或 `whale-alert`。
