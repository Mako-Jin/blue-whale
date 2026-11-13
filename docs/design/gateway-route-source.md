# 网关路由来源治理（Nacos / 本地 YAML / DB 覆盖）

| 项 | 内容 |
|---|---|
| 状态 | Phase 0/1/2 已落地（2026-11-13）；Phase 2 运行时验收（停用 ≤60s 生效取证）待真机重启后执行 |
| 关联需求 | REQ-ARCH-003（服务自持一级前缀）、REQ-CONFIG（路由配置治理） |
| 落点 | `whale-gateway`（路由解析/校验/治理端点）、`whale-service-auth`（Phase 2 覆盖数据） |
| 台账 | [whale-gateway-backlog GW-10](../plans/whale-gateway-backlog.md) |

## 1. 现状与问题

| 事实 | 说明 |
|---|---|
| 本地兜底路由 | 38 条基础路由已从 `application.yml` 迁至 `classpath:gateway-routes-fallback.yaml`，与 Nacos 使用同一 `whale.gateway.dynamic-routes` 结构 |
| Nacos 动态路由 | `whale-gateway-routes.yaml` 由 `NacosRouteDefinitionRepository` 读取，与兜底**按 id 合并**；同 id 由 Nacos 覆盖 |
| 兜底语义 | Nacos 不可用/为空/非法时使用兜底；已有生效快照时保持快照不降级 |
| order | 转换器此前**未解析** `order`，重叠路径只能按注册顺序命中，结果不可预期 |
| id 唯一性 | 此前**未校验**，同一批配置重复 id 会在运行时按 id 建映射时冲突 |
| 治理端点 | 已有 current/previous 双快照、整体拒绝、回滚固定标记，缺 order 与条目数 |
| 网关数据依赖 | 只有 Nacos + Redis，**无 JDBC/Flyway**（与访问名单既定范式一致） |

结论：Nacos 目前不是唯一权威源（兜底文件保证基础路由始终可用）；"DB 存路由"不应在网关里直接接数据库。

## 2. 决策

| 编号 | 决策 | 理由 |
|---|---|---|
| D1 | 路由定义（id/uri/predicates/filters/order）只来自 Nacos / 本地 YAML，**DB 不存定义** | 避免权威源分裂；WebFlux 请求链路不碰 JDBC |
| D2 | DB 只存**业务属性覆盖**（enabled/生效窗口），且表落 **auth**，网关走内部只读接口 + 60s 快照 | 复用访问名单的控制平面范式，消除多实例一致性与部署形态问题 |
| D3 | 覆盖层 v1 只做 `enabled + effective_from/to`；`weight/match_tags` 延后 | 单路由一个 weight 无法直接生效（需 Weight 分组或自定义 LB），tags 无消费方 |
| D4 | 兜底文件复用 Nacos 的 `whale.gateway.dynamic-routes` 自定义结构，按 id 合并、Nacos 优先；不使用 Spring `routes` 结构 | 复用 `convert()` 校验链路；避免 Binder 把 `Path=/x/**` 绑定成 `_genkey_0`、与校验器只认 `patterns` 冲突；合并消除同 id 双源歧义 |
| D5 | Nacos 配置必须解析 order、拒绝重复 id | 重叠路径的可预期命中是路由正确性的前提 |

权威矩阵（目标形态）：

| 数据 | 权威写入源 | 网关读取 | 生效方式 |
|---|---|---|---|
| 路由定义 | Nacos | `NacosRouteDefinitionRepository` | Nacos 推送 → `RefreshRoutesEvent`，≤ 秒级；同 id 覆盖兜底 |
| 冷备定义 | `classpath:gateway-routes-fallback.yaml` | 同一仓库兜底 | 启动即生效，Nacos 不可用/为空/非法时仍然可用 |
| 业务属性 | auth DB（Phase 2） | 内部接口快照（60s） | 下一周期生效，不重启 |

## 3. 已落地（Phase 1）

- **order 解析**：`order` 支持整数（含负数），缺省按 0；非整数视为条目级错误——WARN 日志并跳过该条，避免静默按 0 排序。
- **重复 id 整体拒绝**：新增 `RouteDefinitionValidator.validateAll()`，先逐条校验再校验 id 唯一；任一错误 → 整份 Nacos 配置不生效，保留上一版并累加 `gateway.routes.rejected{dataId}`。
- **兜底路由落盘**：38 条基础路由从 `application.yml` 迁至 `classpath:gateway-routes-fallback.yaml`，由仓库用同一 `convert()` 解析并校验；解析结果进程内缓存。
- **按 id 合并**：兜底在前、Nacos 动态新增在后，同 id 由 Nacos 覆盖；合并结果再做一次 `validateAll()`，防止兜底与动态路由拼出重复 id。
- **降级语义**：Nacos 读取异常/为空/校验失败时，已有快照保持不变，首次加载使用兜底。
- **签名包含 order**：`sameAsCurrent` 的内容签名加入 `order`，仅调整优先级也会触发快照替换。
- **治理端点**：路由摘要新增 `order`，顶层新增 `currentCount` / `previousCount`，保留 `rejectedCount`。
- **测试**：网关全量 163/163 通过；`NacosRouteDefinitionRepositoryTest` 23/23（含 order 解析、非法 order 跳过、重复 id 拒绝、仅 order 变化刷新、兜底加载、Nacos 同 id 覆盖兜底、生产兜底文件解析校验）。

## 4. Nacos 权威化迁移 runbook（待运维执行）

> 目标：Nacos 成为路由定义的唯一写入源，本地文件仅作冷备。**因已按 id 合并，可分批发布，无需与代码发布严格同刻。**

1. 将 `classpath:gateway-routes-fallback.yaml` 的 38 条路由按需补齐 `order`（建议按路径具体度从 -100 到 0 递增），分批发布到 Nacos `whale-gateway-routes.yaml`。
2. 每批发布后核对：治理端点 `currentCount` 与预期一致、`rejectedCount` 不增长、`previousCount` 正常滚动。
3. 全部迁完后，本地兜底文件继续保留（冷备），不再维护增量业务路由；新增/调整路由只改 Nacos。
4. 冒烟：登录、刷新、登出、各服务一级路径、内网 403、限流/熔断降级。
5. 回滚：治理端点 `POST /actuator/routeGovernance` 固定上一版路由；或移除 Nacos 中对应路由，自动回落到兜底。

## 5. DB 业务覆盖层（Phase 2，已落地 2026-11-13）

实现与设计存档一致：

- 表 `sys_gateway_route_override`（auth 库，`V27__gateway_route_override.sql`，权限点号段 2364–2367）：
  `route_id`（PK）、`enabled`、`effective_from`、`effective_to`、`version`、`remark`、审计字段。
- auth 暴露只读内部接口 `GET /auth/v1/internal/gateway/route-overrides`（`X-Internal-Token`），
  返回**全部条目**（含停用与窗口外条目），生效判定由网关按当前时间执行——时间窗口随时间推移
  自动进入/退出生效态，无需重新下发；写操作走管理端接口 + 权限码 `sys:gateway:route-override:*` + 审计
  （停用等于全站 404，需二次确认）。
- 网关 `RouteOverrideProvider`（60s `@Scheduled` 拉取、失败保留旧快照、防重入、内容等价静默跳过、
  快照变化发布 `RefreshRoutesEvent` 立即重载）；**不引入 JDBC**，配置 `whale.gateway.route-override.*`。
- 合并位置：`NacosRouteDefinitionRepository.loadRouteDefinitions()` 末尾 `applyOverrides()`——基础候选
  （兜底 ∪ Nacos 合并）先经覆盖过滤再入快照，只过滤 `enabled=false` / 不在窗口内的路由，不改写
  predicates/filters；孤儿 `route_id` 忽略并计 `gateway.routes.override{result="orphan"}`（按集合变化计一次，
  不随刷新重复累加），不阻断路由刷新。

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

Phase 2（代码已落地，运行时取证待真机重启后执行）：

- [ ] 后台停用一条路由 ≤60s 生效，恢复同样 ≤60s（单测已覆盖过滤语义与事件发布，待真机取证）。
- [x] auth 不可用不影响既有路由；覆盖快照失败保留（`RouteOverrideProviderTest`）。
- [x] 孤儿 `route_id` 可观测且不阻断刷新（`NacosRouteDefinitionRepositoryTest`，指标按集合变化计一次）。
- [x] Nacos 回滚不撤销 DB 覆盖（覆盖过滤在 `replaceSnapshot` 之外的最终投影，两者语义独立）。

## 8. 变更记录

| 日期 | 变更 |
|---|---|
| 2026-11-13 | 初版：现状核对、权威矩阵与 D1~D5 决策；Phase 1 落地 order 解析、重复 id 拒绝、签名纳入 order、治理端点补字段；Phase 2 设计存档 |
| 2026-11-13 | **Phase 2 落地**：`V27` 建表 `sys_gateway_route_override` + 权限点 2364–2367；auth 聚合/仓储/应用服务/管理端+内部接口；网关 `RouteOverrideProperties/Rule/Client/Provider` + 仓库 `applyOverrides` 合并过滤与孤儿指标。网关单测 28+7 绿、auth starter 全量绿（含迁移守卫） |
