# 网关访问名单（白名单 / 黑名单）入库与动态加载

| 项 | 内容 |
|---|---|
| 状态 | 已落地（2026-11-12，Wave 6.64 白名单 / Wave 6.65 黑名单 / Wave 6.67 管理端页面） |
| 关联需求 | REQ-ARCH-003（服务自持一级前缀）、REQ-FUNC-006（内网接口收敛） |
| 落点 | `whale-service-auth`（数据 + 只读内部接口）、`whale-gateway`（拉取与匹配） |
| 台账 | [whale-auth-backlog AUTH-40/AUTH-41](../plans/whale-auth-backlog.md)、[whale-gateway-backlog GW-08/GW-09](../plans/whale-gateway-backlog.md) |

## 1. 背景与目标

JWT 白名单原先硬编码在网关 `application.yml` 的 `whale.gateway.white-list`（15 条）：
新增免鉴权路径需要改配置并重启网关，且与权限中心数据割裂；同时缺少「紧急封禁路径」的手段。

本方案把**白名单 / 黑名单**作为权限中心控制平面的数据资产落到 auth 库：

- 权威数据：`sys_gateway_whitelist`、`sys_gateway_blacklist`；
- 下发通道：只读内部接口 `GET /auth/v1/internal/gateway/whitelist`、`.../blacklist`；
- 消费方：网关每 60s 拉取，进程内两份快照 + 本地 `white-list` 兜底；
- **优先级：黑名单 > 白名单**——命中黑名单即 403，不再判断白名单与令牌。

> 「动态路由」在本方案中解释为**名单动态下发/生效**；现有 `/auth/v1/**` 路由已覆盖内部接口，
> 路由定义本身仍由 Nacos `whale-gateway-routes.yaml` 管理（见 GW-03），不在本方案范围。

## 2. 数据模型

`sys_gateway_whitelist` 与 `sys_gateway_blacklist` 结构一致（黑名单表无种子）：

```sql
CREATE TABLE sys_gateway_xxxlist (
    id           BIGINT       PRIMARY KEY,
    path_pattern VARCHAR(255) NOT NULL,            -- Ant 通配，如 /music/stream/**
    http_method  VARCHAR(16)  NOT NULL DEFAULT '*',-- * 表示不限方法
    scope        VARCHAR(16)  NOT NULL DEFAULT 'PLATFORM',
    source       VARCHAR(16)  NOT NULL DEFAULT 'MIGRATION' / 'MANUAL',
    remark       VARCHAR(255),
    status       TINYINT      NOT NULL DEFAULT 1,  -- 1 启用 / 0 停用（解除）
    sort_no      INT          NOT NULL DEFAULT 0,
    create_time / update_time / deleted,
    UNIQUE KEY uk_gateway_xxxlist (path_pattern, http_method, scope),
    KEY idx_gateway_xxxlist_status (status, sort_no)
);
```

- 迁移：`V13__gateway_whitelist.sql`（15 条与旧 yml 逐字一致的种子）、`V14__gateway_blacklist.sql`；
  均按 `(path_pattern, http_method, scope)` 判重，幂等；`V15__gateway_access_menu.sql` 补管理端菜单。
- 权限码：白名单 `sys:gateway:whitelist:*`（2360–2363）、黑名单 `sys:gateway:blacklist:*`（2364–2367）；
  V15 起仅平台租户建菜单，权限码仍不授予业务租户管理员。
- `status=0` 表示停用/解除封禁，保留记录便于审计；`scope` 当前仅 `PLATFORM` 参与下发。

## 3. 接口契约

```
GET /auth/v1/internal/gateway/whitelist
GET /auth/v1/internal/gateway/blacklist
Header: X-Internal-Token: ${WHALE_INTERNAL_TOKEN}

200 {"code":0,"message":"ok","data":[
      {"pathPattern":"/auth/v1/login","httpMethod":"*"},
      {"pathPattern":"/music/stream/**","httpMethod":"GET"}
    ]}
```

- 查询条件：`status=1 AND scope='PLATFORM' ORDER BY sort_no, id`；
- 两个接口彼此独立（两个概念、两张表），网关分别拉取、分别降级，互不影响；
- 服务侧：`SecurityConfig` permitAll 放行 `/auth/v1/internal/**`，令牌由 `InternalTokenFilter`
  （`/**/internal/**`）校验；非 dev 未配置 `WHALE_INTERNAL_TOKEN` 时 auth 启动即失败；
- 网关侧：外部访问 `/auth/v1/internal/**` 由 `InternalApiDenyGlobalFilter` 统一 403；
- 不引入 Redis/Caffeine 缓存：每实例每分钟两次查询，直接读库。

### 3.1 管理端维护接口（权限中心）

```
GET    /auth/v1/gateway/whitelist?status={0|1}   sys:gateway:whitelist:list
POST   /auth/v1/gateway/whitelist                sys:gateway:whitelist:add
PUT    /auth/v1/gateway/whitelist/{id}           sys:gateway:whitelist:edit
DELETE /auth/v1/gateway/whitelist/{id}           sys:gateway:whitelist:delete
（黑名单同构：/auth/v1/gateway/blacklist + sys:gateway:blacklist:*）
```

- 请求体：`{pathPattern, httpMethod, status, sortNo, remark}`；`pathPattern` 必填且以 `/` 开头；
  `httpMethod` 为空表示不限，统一转大写后存储；`status` 仅 0/1；
- 同一 `(pathPattern, httpMethod, scope)` 唯一，重复新增或改名冲突返回参数错误码 400；
- 删除为**物理删除**（释放唯一键，便于重新录入），仅临时停用/解封请把 `status` 置 0；
- 写操作记审计（`targetType=sys_gateway_whitelist | sys_gateway_blacklist`），网关下一周期自动生效。

### 3.2 管理端页面

- 入口：系统管理 →「网关访问名单」（`/system/gateway-access`，组件键 `gateway/access`，V15 菜单种子）；
  **仅平台租户**（`tenant_id=1`）建菜单，权限码 `sys:gateway:whitelist:list` 控制菜单与 Tab 可见性。
- 页面为白/黑双 Tab：路径、方法、状态开关、排序、来源、备注、更新时间；支持新增 / 编辑 / 启停 / 删除，
  删除有二次确认并提示「临时停用请用状态开关」；黑名单 Tab 顶部常驻误封风险提示。
- 前端权限码一一对应后端：Tab 与查询用 `sys:gateway:{whitelist|blacklist}:list`，
  按钮分别用 `:add` / `:edit` / `:delete`；状态开关复用编辑权限。

## 4. 网关动态加载与匹配

```yaml
whale:
  gateway:
    access-list:
      enabled: ${WHALE_GATEWAY_ACCESS_LIST_ENABLED:true}
      whitelist-uri: lb://whale-service-auth/auth/v1/internal/gateway/whitelist
      blacklist-uri: lb://whale-service-auth/auth/v1/internal/gateway/blacklist
      refresh-interval-ms: 60000
      initial-delay-ms: 5000
      timeout-millis: 800
      internal-token: ${WHALE_INTERNAL_TOKEN:}
      internal-token-header: X-Internal-Token
```

组件（`com.bluewhale.gateway.accesslist`）：

| 类 | 职责 |
|---|---|
| `GatewayAccessListProperties` | 配置绑定（与 `white-list` 分离，避免 List 绑定歧义） |
| `AuthGatewayAccessListClient` | 负载均衡 WebClient 拉取；内部令牌头 + 800ms 超时；`code!=0`/解析失败抛错 |
| `GatewayAccessListProvider` | 白/黑两份 `AtomicReference` 快照 + 各自防重入；`@Scheduled` 首拉/周期刷新 |
| `JwtAuthGlobalFilter` | **先黑名单后白名单**：黑名单命中 → 403；白名单 = 本地 `white-list` 优先 ∪ 远程快照 |

单次请求判定顺序：

```
路径命中黑名单? ──是──> 403 {"code":403,"message":"禁止访问"}
        │否
路径命中白名单（本地 ∪ 远程）? ──是──> 清除伪造身份头 → 转发
        │否
校验 JWT / 黑名单令牌 / 租户 / 撤销 / 强制改密 → 转发或 401/403
```

刷新与降级矩阵（白名单、黑名单各自独立）：

| 场景 | 行为 |
|---|---|
| 拉取成功且非空 | 整体替换该名单快照 |
| 拉取成功但为空 | 清空该名单快照（白名单 → 仅本地配置；黑名单 → 无封禁） |
| HTTP 失败 / 超时 / `code!=0` / 解析失败 | **保留上一份快照**，WARN 日志，避免抖动期名单丢失 |
| 同一名单上一次拉取未结束 | 跳过本次，避免并发覆盖 |
| `enabled=false` | 不拉取；白名单仅本地，黑名单不生效 |

匹配语义：远程条目 `http_method='*'`（或空）不限方法，否则与实际请求方法忽略大小写比较；
本地 `white-list` 条目始终不限方法。黑名单命中时记录指标
`gateway.auth.rejected{reason="path_blacklisted"}`。

## 5. 安全边界与取舍

- **内网**：两个接口均为 `/internal/**`，外部经网关一律 403，服务侧令牌校验，双层防护。
- **为什么放权限中心而不是网关 DB**：
  1. 网关当前无数据库依赖（仅 Redis），引入 DB 会新增部署形态与故障点；
  2. 名单属于身份/访问控制数据，与权限、审计、权限码同源，放 auth 库可复用现有管理端与审计；
  3. 网关只需「拉取 + 快照」，数据一致性由单一控制平面保证。
- **代价**：auth 不可用时黑名单只能沿用最后快照；极端场景（auth 长时间不可用 + 需紧急封禁）
  需恢复 auth 或滚动重启网关（本地 `white-list` 不可用于封禁）。若后续需要「不依赖 auth 的秒级熔断」，
  可增加 Nacos 热配置通道（GW-03 已有配置面）。
- **误封风险**：黑名单优先级最高，误配 `/auth/v1/login` 会锁死登录；写操作（后续管理端）建议二次确认 +
  审计，并限制权限码只授予平台管理员。

## 6. 部署与配置

- 服务端：`WHALE_INTERNAL_TOKEN`（auth 与网关取同一值；非 dev 必配）。
- 网关可选覆盖：`WHALE_GATEWAY_ACCESS_LIST_ENABLED`、`WHALE_GATEWAY_WHITELIST_URI`、
  `WHALE_GATEWAY_BLACKLIST_URI`、`..._REFRESH_INTERVAL_MS`、`..._INITIAL_DELAY_MS`、`..._TIMEOUT_MILLIS`。
- 启动顺序无强依赖：网关首拉失败即用本地兜底，auth 恢复后下一周期自动接管。
- 升级兼容：白名单接口契约未变；旧网关不拉黑名单，黑名单在网关升级后生效。

## 7. 排障

1. 直连 auth 校验契约：
   `curl -H "X-Internal-Token: $TOKEN" http://127.0.0.1:8081/auth/v1/internal/gateway/whitelist`（黑名单同理）→ `code=0`；
   无令牌 → 403。
2. 外部不可达：`curl http://127.0.0.1:8080/auth/v1/internal/gateway/blacklist` → 403（internal-deny）。
3. 网关日志：「网关白名单已刷新: N 条」「网关黑名单已刷新: N 条」；
   失败输出「拉取网关白名单/黑名单失败，继续使用现有快照与本地配置」，据此区分「auth 不可达」与「名单为空」。
4. DB 变更未生效：确认 `status=1`、`scope='PLATFORM'`、`deleted=0`，并等待 ≤60s（或重启网关立即首拉）。
5. 登录 401：先确认本地 `whale.gateway.white-list` 仍含 `/auth/v1/login`（兜底不能被清空）。
6. 登录/接口 403 且路径疑似被封禁：查 `sys_gateway_blacklist` 是否有命中条目（`status=1`）；
   网关日志「请求命中路径黑名单」会打印 `path/method`。

## 8. 后续

- 管理端页面与菜单已落地（见 §3.2）；后续按租户下发（`scope` 扩展）需网关携带租户上下文并按租户合并，
  涉及匹配优先级设计。
- 如需观察面：可补 `gateway.access_list.refresh{list,result}` 计数与快照条数 Gauge。
- 应急通道：Nacos 热配置黑名单（不依赖 auth 可用性），作为本方案的第 2 优先级来源。

## 9. 变更记录

| 日期 | 变更 |
|---|---|
| 2026-11-12 | 初版：V13 迁移（白名单表 + 15 条种子 + 4 个权限码）、auth 只读内部接口、网关动态拉取与本地兜底 |
| 2026-11-12 | 追加黑名单：V14（黑名单表 + 4 个权限码）、`/internal/gateway/blacklist`、网关黑名单优先匹配（403 + 指标）；网关组件合并为 `accesslist` 包，配置前缀改为 `whale.gateway.access-list` |
| 2026-11-12 | 追加管理端 CRUD：`/auth/v1/gateway/{whitelist\|blacklist}` 四类操作 + 权限码/审计，删除走物理删除以释放唯一键 |
| 2026-11-12 | 追加管理端页面与菜单（V15，平台租户）：白/黑双 Tab，新增/编辑/启停/删除，黑名单误封提示 |
