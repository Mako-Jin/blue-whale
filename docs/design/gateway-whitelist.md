# 网关白名单入库与动态加载（gateway-whitelist）

| 项 | 内容 |
|---|---|
| 状态 | 已落地（2026-11-12，Wave 6.64） |
| 关联需求 | REQ-ARCH-003（服务自持一级前缀）、REQ-FUNC-006（内网接口收敛） |
| 落点 | `whale-service-auth`（数据 + 只读内部接口）、`whale-gateway`（拉取与匹配） |
| 台账 | [whale-auth-backlog AUTH-40](../plans/whale-auth-backlog.md)、[whale-gateway-backlog GW-08](../plans/whale-gateway-backlog.md) |

## 1. 背景与目标

JWT 白名单原先硬编码在网关 `application.yml` 的 `whale.gateway.white-list`（15 条）：
新增免鉴权路径需要改配置并重启网关，且与权限中心数据割裂。

本方案把白名单作为**权限中心控制平面的数据资产**落到 auth 库：

- 权威数据：`bluewhale_auth.sys_gateway_whitelist`；
- 下发通道：auth 只读内部接口 `GET /auth/v1/internal/gateway/whitelist`；
- 消费方：网关每 60s 拉取一次，进程内快照 + 本地 `white-list` 兜底。

> 「动态路由」在本方案中解释为**白名单动态下发/生效**；现有 `/auth/v1/**` 路由已覆盖内部接口，
> 路由定义本身仍由 Nacos `whale-gateway-routes.yaml` 管理（见 GW-03），不在本方案范围。

## 2. 数据模型

```sql
CREATE TABLE sys_gateway_whitelist (
    id           BIGINT       PRIMARY KEY,
    path_pattern VARCHAR(255) NOT NULL,            -- Ant 通配，如 /music/stream/**
    http_method  VARCHAR(16)  NOT NULL DEFAULT '*',-- * 表示不限方法
    scope        VARCHAR(16)  NOT NULL DEFAULT 'PLATFORM',
    source       VARCHAR(16)  NOT NULL DEFAULT 'MIGRATION',
    remark       VARCHAR(255),
    status       TINYINT      NOT NULL DEFAULT 1,  -- 1 启用 / 0 停用
    sort_no      INT          NOT NULL DEFAULT 0,
    create_time / update_time / deleted,
    UNIQUE KEY uk_gateway_whitelist (path_pattern, http_method, scope),
    KEY idx_gateway_whitelist_status (status, sort_no)
);
```

- 迁移：`whale-service-auth-starter/src/main/resources/db/migration/V13__gateway_whitelist.sql`（幂等，
  种子按 `(path_pattern, http_method, scope)` 判重，与迁移前的 15 条 yml 配置逐字一致）。
- 权限码：`sys:gateway:whitelist:list/add/edit/delete`（2360–2363，按 `perm_code` 判重），
  为后续管理端 CRUD 预留；本轮不建菜单、不授租户管理员。
- `scope` 当前仅 `PLATFORM` 参与下发，为按租户扩展预留；网关拉取不带租户上下文。

## 3. 接口契约

```
GET /auth/v1/internal/gateway/whitelist
Header: X-Internal-Token: ${WHALE_INTERNAL_TOKEN}

200 {"code":0,"message":"ok","data":[
      {"pathPattern":"/auth/v1/login","httpMethod":"*"},
      {"pathPattern":"/music/stream/**","httpMethod":"GET"}
    ]}
```

- 查询条件：`status=1 AND scope='PLATFORM' ORDER BY sort_no, id`；
- 服务侧：`SecurityConfig` permitAll 放行 `/auth/v1/internal/**`，令牌由 `InternalTokenFilter`
  （`/**/internal/**`）校验；非 dev 未配置 `WHALE_INTERNAL_TOKEN` 时 auth 启动即失败；
- 网关侧：外部访问 `/auth/v1/internal/**` 由 `InternalApiDenyGlobalFilter` 统一 403；
- 不引入 Redis/Caffeine 缓存：每实例每分钟一次查询，直接读库。

## 4. 网关动态加载与匹配

```yaml
whale:
  gateway:
    whitelist-dynamic:
      enabled: ${WHALE_GATEWAY_WHITELIST_DYNAMIC_ENABLED:true}
      uri: lb://whale-service-auth/auth/v1/internal/gateway/whitelist
      refresh-interval-ms: 60000
      initial-delay-ms: 5000
      timeout-millis: 800
      internal-token: ${WHALE_INTERNAL_TOKEN:}
      internal-token-header: X-Internal-Token
```

组件（`com.bluewhale.gateway.whitelist`）：

| 类 | 职责 |
|---|---|
| `GatewayWhitelistDynamicProperties` | 配置绑定（与 `white-list` 分离，避免 List 绑定歧义） |
| `AuthGatewayWhitelistClient` | 负载均衡 WebClient 拉取；内部令牌头 + 800ms 超时；`code!=0`/解析失败抛错 |
| `GatewayWhitelistProvider` | `AtomicReference` 快照 + `AtomicBoolean` 防重入；`@Scheduled` 首拉/周期刷新；`matches(path, method)` |
| `JwtAuthGlobalFilter#isWhiteListed` | **本地配置优先短路**，未命中再查远程快照（并集语义） |

刷新与降级矩阵：

| 场景 | 行为 |
|---|---|
| 拉取成功且非空 | 整体替换快照 |
| 拉取成功但为空 | 清空快照 → 仅本地 `white-list` 生效（不放大放行范围） |
| HTTP 失败 / 超时 / `code!=0` / 解析失败 | **保留上一份快照**，WARN 日志，不影响已放行路径 |
| 上一次拉取未结束 | 跳过本次，避免并发覆盖 |
| `enabled=false` | 不拉取，仅本地配置 |

匹配语义：远程条目 `http_method='*'`（或空）不限方法，否则与实际请求方法忽略大小写比较；
本地 `white-list` 条目始终不限方法。命中后仍执行「清除外部伪造身份/租户头」逻辑，行为不变。

## 5. 部署与配置

- 服务端：`WHALE_INTERNAL_TOKEN`（auth 与网关取同一值；非 dev 必配）。
- 网关可选覆盖：`WHALE_GATEWAY_WHITELIST_DYNAMIC_ENABLED`、`..._URI`、`..._REFRESH_INTERVAL_MS`、
  `..._INITIAL_DELAY_MS`、`..._TIMEOUT_MILLIS`。
- 启动顺序无强依赖：网关首拉失败即用本地兜底，auth 恢复后下一周期自动接管。

## 6. 排障

1. 直连 auth 校验契约：
   `curl -H "X-Internal-Token: $TOKEN" http://127.0.0.1:8081/auth/v1/internal/gateway/whitelist` → `code=0`；
   无令牌 → 403。
2. 外部不可达：`curl http://127.0.0.1:8080/auth/v1/internal/gateway/whitelist` → 403（internal-deny）。
3. 网关日志：每次刷新输出「网关白名单已刷新: N 条」；失败输出
   「拉取网关白名单失败，继续使用现有快照与本地配置」，据此区分「auth 不可达」与「数据为空」。
4. DB 变更未生效：确认 `status=1`、`scope='PLATFORM'`、`deleted=0`，并等待 ≤60s（或重启网关立即首拉）。
5. 登录 401：先确认本地 `whale.gateway.white-list` 仍含 `/auth/v1/login`（兜底不能被清空）。

## 7. 后续

- 管理端 CRUD：`GET/POST/PUT/DELETE /auth/v1/gateway/whitelist` + `sys:gateway:whitelist:*` 鉴权，
  写操作后网关下一周期自动生效（无需推送）。
- 按租户下发（`scope` 扩展）需网关携带租户上下文并按租户合并，涉及匹配优先级设计。
- 如需观察面：可补 `gateway.whitelist.remote{result}` 计数与快照条数 Gauge。

## 8. 变更记录

| 日期 | 变更 |
|---|---|
| 2026-11-12 | 初版：V13 迁移（表 + 15 条种子 + 4 个权限码）、auth 只读内部接口、网关动态拉取与本地兜底、单测与文档 |
