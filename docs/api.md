# API 文档规范

## 统一入口与路径

- **唯一对外入口是网关 `:8080`**，前端一律 `http://<host>:8080/<一级前缀>/...`，禁止直连业务服务端口。
- **服务自持对外一级前缀，网关只转发、不剥离**（REQ-ARCH-003，禁止 `StripPrefix`）：如 `/auth/login`、`/pay/**`、`/doc/**`；完整路由表见 [service-catalog.md](service-catalog.md) §二。
- 服务直连端口仅用于内网（运维 smoke / 服务间调用），生产必须网络收口（[network-boundary.md](design/network-boundary.md)）。

## 响应体（`com.bluewhale.common.core.result.Result`）

```json
{ "code": 0, "message": "success", "data": { } }
```

- `code=0` 成功；非 0 为错误码（`ErrorCode` 枚举 + 业务码段），HTTP 状态与业务码可不一致（如限流 429 / 熔断 503 / 未认证 401 仍返回统一结构）。
- 分页等复杂结构放 `data` 内，不新增外层字段。

## 鉴权与头

| 头 | 说明 |
|---|---|
| `Authorization: Bearer <access>` | 登录获取；网关校验 JWT（白名单见 service-catalog §二） |
| `X-User-Id` / `X-Permission-Codes` | 网关注入的身份与权限码；服务侧拦截器（`RequirePermissionInterceptor`）消费；外部伪造由网关剥离 |
| `X-Internal-Token` | 服务间内网调用令牌；网关入口强制剥离外部同名头，服务侧过滤器校验 `/**/internal/**` |
| `X-Trace-Id` | 链路追踪，网关生成透传 |

## 内网接口约定

- 服务间同步调用：`POST/GET /**/internal/**`，经 Nacos 服务发现直连（不经网关），必须携带 `X-Internal-Token`；出站统一用 `InternalTokenSupport.interceptor()`。
- 典型契约：`/file/v1/internal/upload`、`/message/internal/send`、`/archive/internal/items`、`/user/v1/internal/exists`（auth 自 Wave 6.33 起内网端点也带版本段，[service-catalog.md](service-catalog.md) §六）。

## 接口文档（springdoc）

各服务直连端口暴露（网关不放行）：`/v3/api-docs` 与 `/swagger-ui.html`；生产环境随端口一并收口。
