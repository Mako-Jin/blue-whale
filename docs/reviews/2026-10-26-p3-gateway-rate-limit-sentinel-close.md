# P3 收口：Sentinel 结案与网关限流实机生效（检查报告）

> 日期：2026-10-26　对象：`whale-gateway`（8080）　关联：`docs/service-catalog.md` 偏差 **C-07**、`whale-gateway-backlog.md` **GW-01**、AUTH-30

## 一、结论

1. **Sentinel 不引入，GW-01 结案**（沿用 [2026-10-21 评估](2026-10-21-wave5.35-sentinel-evaluation.md) 的结论与重开条件），C-07 的 Sentinel 残留项正式关闭。
2. **限流能力实机已生效**：修复网关进程未加载当前 Redis 口令导致的限流降级（原先每请求 `Unable to connect to Redis` → 计数失败即放行，限流形同虚设）。

## 二、变更（仅运维动作 + 文档，无代码改动）

| 项 | 动作 |
|---|---|
| 网关进程 | 以用户级环境变量（`MYSQL_PASSWORD` / `SPRING_DATA_REDIS_PASSWORD` / `SPRING_CLOUD_NACOS_USERNAME` / `SPRING_CLOUD_NACOS_PASSWORD`）重新注入后重启，`SERVER_PORT=8080` |
| 文档 | `docs/service-catalog.md` C-07 由「部分已解」改为「已解」，补实机 429 证据与重开条件 |
| 结论复用 | `docs/architecture.md` §限流（已记「Sentinel 未引入」）无需修改 |

## 三、验证

### 3.1 修复前（15:40 启动的网关进程）

- 网关日志持续出现 `RateLimitGlobalFilter：网关限流计数失败（Redis 不可用），按可用性优先降级放行 ... reason=Unable to connect to Redis`。
- 并发 40 次 `POST /audit/log/behavior/batch`（特化规则 30/s）全部 503（熔断），**无一个 429**，说明限流未生效。
- 并发 400 次 `GET /contract/page`（全局 200/s）全部 401，无 429，进一步印证计数未落 Redis。

### 3.2 修复后（16:14 重启，注入当前口令）

- `GET /actuator/health` = `UP`；启动 8.2s，无 `限流计数失败` 告警（日志计数 **0**）。
- 特化规则验证：并发 60 次 `POST /audit/log/behavior/batch` → **429 × 30 + 503 × 30**（30 为规则阈值，超阈值短路不再进入下游；503 为 `whale-audit` 熔断态）。
- 全局规则验证：并发 400 次 `GET /contract/page`（1.9s 内）→ **401 × 382 + 429 × 18**。
- 回归：经网关 `/auth/login` `code=0`；`/auth/me` `code=0`、`perms=172`（权限缓存链路正常）；`/contract/page`（带令牌）`code=0`。

## 四、风险与遗留

- **凭据注入是限流前提**：`whale-gateway` 的 `application.yml` 未内联 Redis 口令（沿用其他服务的「仅环境变量」约定），进程缺失 `SPRING_DATA_REDIS_PASSWORD` 时会静默降级为「限流放行」。运维重启务必使用 `scripts/dev/whale-start-all.ps1`（该脚本会校验四个凭据变量存在）。
- **降级语义**由设计决定（可用性优先），不建议改为「Redis 异常即拒绝」，否则 Redis 抖动会全站 429。
- Sentinel 若未来重开，须先满足评估报告 §四 的触发条件（制品库可用、Dashboard 可运行、出现热点参数/集群流控诉求），且与 resilience4j 熔断二选一避免双层熔断。
