# 网关路由来源治理（Nacos / 本地 YAML / DB 覆盖）

| 项 | 内容 |
|---|---|
| 状态 | Phase 0/1 已落地（2026-11-13）；Phase 2（DB 覆盖层）待需求触发，未启动 |
| 关联需求 | REQ-ARCH-003（服务自持一级前缀）、REQ-CONFIG（路由配置治理） |
| 落点 | `whale-gateway`（路由解析/校验/治理端点）、`whale-service-auth`（Phase 2 覆盖数据） |
| 台账 | [whale-gateway-backlog GW-10](../plans/whale-gateway-backlog.md) |

## 1. 现状与问题

| 事实 | 说明 |
|---|---|
| 本地静态路由 | `application.yml` 的 `spring.cloud.gateway.routes` 共 38 条，**始终生效** |
| Nacos 动态路由 | `whale-gateway-routes.yaml` 由 `NacosRouteDefinitionRepository` 读取，**增量叠加**；读取失败/为空返回空列表 |
| 兜底 | 没有独立 classpath fallback 文件，本地静态路由就是实际兜底 |
| order | 转换器此前**未解析** `order`，重叠路径只能按注册顺序命中，结果不可预期 |
| id 唯一性 | 此前**未校验**，同一批配置重复 id 会在运行时按 id 建映射时冲突 |
| 治理端点 | 已有 current/previous 双快照、整体拒绝、回滚固定标记，缺 order 与条目数 |
| 网关数据依赖 | 只有 Nacos + Redis，**无 JDBC/Flyway**（与访问名单既定范式一致） |

结论：Nacos 目前不是权威源；"DB 存路由"不应在网关里直接接数据库。

## 2. 决策

| 编号 | 决策 | 理由 |
|---|---|---|
| D1 | 路由定义（id/uri/predicates/filters/order）只来自 Nacos / 本地 YAML，**DB 不存定义** | 避免权威源分裂；WebFlux 请求链路不碰 JDBC |
| D2 | DB 只存**业务属性覆盖**（enabled/生效窗口），且表落 **auth**，网关走内部只读接口 + 60s 快照 | 复用访问名单的控制平面范式，消除多实例一致性与部署形态问题 |
| D3 | 覆盖层 v1 只做 `enabled + effective_from/to`；`weight/match_tags` 延后 | 单路由一个 weight 无法直接生效（需 Weight 分组或自定义 LB），tags 无消费方 |
| D4 | 不新建重复的 fallback 文件；迁移完成前，本地静态路由继续承担兜底 | 两处同内容必漂移 |
| D5 | Nacos 配置必须解析 order、拒绝重复 id | 重叠路径的可预期命中是路由正确性的前提 |

权威矩阵（目标形态）：

| 数据 | 权威写入源 | 网关读取 | 生效方式 |
|---|---|---|---|
| 路由定义 | Nacos | `NacosRouteDefinitionRepository` | Nacos 推送 → `RefreshRoutesEvent`，≤ 秒级 |
| 冷备定义 | 本地 YAML（application.yml 或未来 fallback） | 同一仓库兜底 | 启动即生效，Nacos 不可用时保持 |
| 业务属性 | auth DB（Phase 2） | 内部接口快照（60s） | 下一周期生效，不重启 |

## 3. 已落地（Phase 1）

- **order 解析**：`order` 支持整数（含负数），缺省按 0；非整数视为条目级错误——WARN 日志并跳过该条，避免静默按 0 排序。
- **重复 id 整体拒绝**：新增 `RouteDefinitionValidator.validateAll()`，先逐条校验再校验 id 唯一；任一错误 → 整份 Nacos 配置不生效，保留上一版并累加 `gateway.routes.rejected{dataId}`。
- **签名包含 order**：`sameAsCurrent` 的内容签名加入 `order`，仅调整优先级也会触发快照替换。
- **治理端点**：路由摘要新增 `order`，顶层新增 `currentCount` / `previousCount`，保留 `rejectedCount`。
- **测试**：`NacosRouteDefinitionRepositoryTest` 20/20、`RouteDefinitionValidatorTest` 12/12（含 order 解析、非法 order 跳过、重复 id 拒绝、仅 order 变化刷新）。

## 4. Nacos 权威化迁移 runbook（待运维执行）

> 目标：Nacos 成为路由定义的唯一权威源，本地仅保留冷备。**必须协调发布，不能只改一边。**

1. 从现网 `application.yml` 导出 38 条路由，补齐每条 `order`（建议按路径具体度从 -100 到 0 递增），发布到 Nacos `whale-gateway-routes.yaml`。
2. 在**不删除本地静态路由**的前提下核对：Nacos 配置整体校验通过、治理端点 `currentCount` 与预期一致、`rejectedCount` 不增长。
3. 确认无误后，在**同一次发布**中移除 `application.yml` 的 `spring.cloud.gateway.routes`（否则同 id 冲突）；若需保留冷备，将同样内容放入 `classpath:gateway-routes-fallback.yaml` 并由仓库在 Nacos 不可用时加载（后续增量项）。
4. 冒烟：登录、刷新、登出、各服务一级路径、内网 403、限流/熔断降级。
5. 回滚：治理端点 `POST /actuator/routeGovernance` 固定上一版路由；或恢复 application.yml 静态路由并重新发布。

## 5. DB 业务覆盖层（Phase 2，未启动）

触发条件（满足其一再启动）：运营后台需要不经 Nacos 发布流程开关路由；需要按时间窗口临时启停；需要与租户/权限联动。

设计要点：

- 表 `sys_gateway_route_override`（auth 库，Flyway 新版本）：
  `route_id`（PK）、`enabled`、`effective_from`、`effective_to`、`version`、`remark`、审计字段。
- auth 暴露只读内部接口 `GET /auth/v1/internal/gateway/route-overrides`（`X-Internal-Token`），返回 `{version, items[]}`；
  写操作走管理端接口 + 权限码 + 审计（停用等于全站 404，需二次确认）。
- 网关 `RouteOverrideProvider`：60s 拉取、失败保留旧快照、防重入、内容签名不变不重建；**不引入 JDBC**。
- 合并位置：`NacosRouteDefinitionRepository.loadRouteDefinitions()` 末尾，只过滤 `enabled=false` / 不在窗口内的路由；
  未知 `route_id` 忽略并计 `gateway.routes.override{result="orphan"}`，不改变 predicates/filters。
- 变更识别优先级：60s 拉取 + 版本/签名比对 ＞ 内部刷新端点（令牌 + 审计）＞ MQ ＞ CDC；后两者暂不引入。

## 6. 观测与排障

- `GET /actuator/routeGovernance`：`current/previous`（含 `order`）、`currentCount`、`previousCount`、`rejectedCount`。
- 日志：Nacos 读取失败 WARN（保留现有路由）；order 非法 WARN（跳过条目）；配置被拒 ERROR（列出全部错误）。
- 排障顺序：先看 `rejectedCount` 是否增长（整份被拒）→ 再看 `currentCount` 是否符合预期 → 最后核对 `order` 与路径具体度。
- 常见误配：同 id 同时存在于 Nacos 与静态配置 → 启动/刷新冲突；order 缺省导致宽路径抢先命中。

## 7. 验收

Phase 1（已完成）：

- [x] `order` 生效且仅改 order 也会刷新。
- [x] 重复 id 整份拒绝，保留上一版快照。
- [x] 治理端点可看到 order 与条目数。
- [x] 网关全量单测通过、checkstyle 0 违规。

Phase 2（启动后）：

- [ ] 后台停用一条路由 ≤60s 生效，恢复同样 ≤60s。
- [ ] auth 不可用不影响既有路由；覆盖快照失败保留。
- [ ] 孤儿 `route_id` 可观测且不阻断刷新。
- [ ] Nacos 回滚不撤销 DB 覆盖（两者语义独立）。

## 8. 变更记录

| 日期 | 变更 |
|---|---|
| 2026-11-13 | 初版：现状核对、权威矩阵与 D1~D5 决策；Phase 1 落地 order 解析、重复 id 拒绝、签名纳入 order、治理端点补字段；Phase 2 设计存档 |
