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

---

## 错误码

### 通用码（`ErrorCode` 枚举，全平台一致）

| code | 含义 | 典型来源 |
|---|---|---|
| `0` | 成功 | 所有服务 |
| `400` | 参数错误（校验失败 / 请求体不可解析 / 缺参 / 类型不匹配） | 服务侧 `GlobalExceptionHandler` |
| `401` | 未认证 / 令牌无效或已登出 | 网关鉴权过滤器 |
| `403` | 无权限 / 内网接口外部访问 | 网关、`@RequirePermission` |
| `404` | 资源或路由不存在 | 服务侧、网关路由不存在 |
| `405` | 路径存在但 HTTP 方法不支持 | 服务侧 |
| `409` | 重复提交（幂等 token 处理中） | `@Idempotent` |
| `429` | 请求过于频繁（限流）/ 锁抢占失败（快速失败） | 网关限流、分布式锁 |
| `500` | 系统异常 | 兜底处理器 |
| `502` / `503` / `504` | 下游不可用（保持原 HTTP 状态，文案「服务暂时不可用」） | 网关异常处理 / 熔断 503 |

### HTTP 状态与业务码

- **业务异常**（`BizException`）由全局处理器转成 `Result`，HTTP 通常为 `200`，调用方**必须判断 `code`**，不能只看 HTTP 状态。
- **网关层**（鉴权 401、内网屏蔽 403、限流 429、熔断 503、下游 502/503/504）返回**真实 HTTP 状态**，且 `code` 与 HTTP 状态一致。
- 前端 HTTP 客户端应统一按「HTTP 非 2xx → 抛错；2xx 但 `code≠0` → 业务错误」两条路径处理。

### 业务码段

服务私有业务码由各服务自定义（如 CRM 报价非法流转 `41023`、金额非法 `41028`），**统一使用 5 位段内编号**，
并在对应服务设计稿或 `ErrorCode` 子枚举中登记；新增业务码不得占用上表通用码。

---

## 分页、排序与过滤

### 请求

| 参数 | 说明 | 默认 |
|---|---|---|
| `current`（别名 `pageNum`） | 页码，从 **1** 开始；小于 1 归一为 1 | 1 |
| `size`（别名 `pageSize`） | 每页条数；范围 1–**200**，超限截断 | 10 |
| 业务过滤参数 | 各接口显式声明（如 `status` / `ownerId` / `keyword`） | 无 |

- 两套命名（后端 `current/size`、前端 `pageNum/pageSize`）在服务侧归一化解析，同时传入时以 `current/size` 优先。
- **不提供通用 `sort` 参数**：需要排序的接口在各自契约中显式声明；未声明排序时调用方不得依赖返回顺序。
- 深度分页由 `PageQuery.MAX_PAGE_SIZE=200` 硬限制，禁止调用方通过超大 `size` 拉全量。

### 响应（`PageResult<T>`）

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [],
    "total": 0,
    "size": 10,
    "current": 1
  }
}
```

> `records` 为当前页数据；`total` 为满足条件的总条数。分页字段不再包裹一层 `page`。

---

## 幂等

- 注解：`@Idempotent(biz = "...", tokenHeader = "X-Idempotent-Token", expireSeconds = 3600)`。
- 键结构：`{prefix}:{biz}:{token}`（默认 `whale` 前缀，符合 `whale:<域>:<用途>:<标识>` 约定）。
- 行为：
  1. token **缺失** → 不做幂等约束，直接放行；
  2. 首次请求 `trySet` 抢占成功 → 执行目标方法并缓存返回体 JSON（`expireSeconds`，默认 1 小时）；
  3. 相同 token 的重复请求 → 处理中返回 **409 `REPEAT_SUBMIT`**，已完成则**直接返回缓存结果**（不重复执行）；
  4. 目标方法抛异常 → 删除幂等键，允许调用方重试。
- 调用方约束：同一业务操作必须复用同一 token；**不同操作不得复用**，否则会拿到错误缓存。

---

## 多租户

| 头 | 说明 |
|---|---|
| `X-Tenant-Id` | 租户标识；缺省 `1`。由服务侧租户行插件自动追加/填充租户条件，业务代码不手写 `tenant_id` 过滤 |
| 外部伪造 | 网关剥离外部同名头后按登录态注入，业务不得信任客户端直传值 |

新增表必须带 `tenant_id`；跨租户查询（仅平台运维场景）需显式豁免并留审计。

---

## 版本策略

- 对外路径**不强制**版本段，一级前缀由服务自持（如 `/crm/**`）。
- 破坏性变更优先**新增路径**（如 `/crm/v2/customer`）或新端点，旧端点至少保留一个发布周期并标注 `Deprecated`。
- 内网契约已有版本段的（如 auth `/user/v1/internal/**`）保持版本段，不因统一风格而回退。
- 字段只增不删：删除字段前先确认无调用方，或并行提供新字段并保留旧字段至下个主版本。

---

## 限流与重试

- 网关按「规则标识 + 固定窗口 + 客户端」计数，超阈值返回 `HTTP 429` + `{"code":429,"message":"请求过于频繁，请稍后重试"}`，并透传 `X-Trace-Id`。
- **不返回 `Retry-After`**：调用方按指数退避重试，避免固定等待造成二次尖峰。
- Redis 异常时限流**降级放行**（可用性优先）；因此 429 不是唯一保护，服务侧仍需幂等与资源上限。
- 熔断打开时返回 `503` + 统一 `Result`；此类错误**不应立即重试**，应退避并在恢复后重放。
- 重试只适用于**幂等请求**（GET，或带幂等键的写请求）；无幂等键的写请求不得自动重试。

---

## OpenAPI 示例

```json
{
  "openapi": "3.0.1",
  "info": { "title": "whale-crm", "version": "0.1.0" },
  "paths": {
    "/crm/customer/page": {
      "get": {
        "summary": "客户分页",
        "parameters": [
          { "name": "current", "in": "query", "schema": { "type": "integer", "default": 1 } },
          { "name": "size", "in": "query", "schema": { "type": "integer", "default": 10, "maximum": 200 } },
          { "name": "keyword", "in": "query", "schema": { "type": "string" } }
        ],
        "responses": {
          "200": {
            "description": "统一返回体",
            "content": {
              "application/json": {
                "schema": { "$ref": "#/components/schemas/ResultPageCustomer" }
              }
            }
          }
        }
      }
    }
  }
}
```

> 以上为**契约示例**，字段与路径以各服务实际 `springdoc` 输出为准（`http://<host>:<port>/v3/api-docs`）。

