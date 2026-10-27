# P1 #14 检查报告：白名单路径未剥离伪造身份与权限头

- 日期：2026-10-27
- 范围：`whale-gateway` JWT 白名单请求头净化、权限码头注入
- 关联：P1 #13 actuator 鉴权、Wave 5.8 AUTH-06 权限码头、REQ-SEC-017

## 1. 问题

`JwtAuthGlobalFilter` 命中白名单时只清除 `X-Tenant-Id`，仍原样转发客户端携带的 `X-User-Id` / `X-Username` / `X-User-Roles`；`PermissionHeaderGlobalFilter` 在「开关关闭」与「缺 `X-User-Id`（即白名单/未认证）」两个分支直接 `chain.filter(exchange)`，不清理客户端自带的 `X-Permission-Codes`。

白名单包含可被匿名访问、且下游会信任网关身份头的路径：

- `/audit/log/frontend/batch`、`/audit/log/behavior/batch`：`LogIngestController` 的 `resolveUserId(httpRequest, request.getUserId())` **优先读取 `X-User-Id`** 作为日志归属；
- `/auth/login`、`/auth/refresh`、`/im/ws`、`/contract/sign/callback/**`：同为免 JWT 路径，任何后续新增的「信任网关头」逻辑都会落在同一信任边界内。

因此匿名调用者可以：

1. 伪造 `X-User-Id` 把前端日志/行为埋点归属到任意用户（审计溯源污染，可栽赃或掩盖真实操作）；
2. 伪造 `X-Permission-Codes` 让下游 `RequirePermissionInterceptor` 误信客户端声明的权限（当前埋点接口未标注权限，但该头在后续白名单接口上会成为直通后门）；
3. 伪造 `X-User-Roles` / `X-Username`，影响任何按网关头渲染或鉴权的下游逻辑。

P1 #13 修复的是 actuator 的匿名写入口，本条是同一信任边界上「白名单请求头未净化」的剩余缺口。

## 2. 改动

| 文件 | 说明 |
|---|---|
| `gateway/.../filter/JwtAuthGlobalFilter.java` | 白名单分支 `withoutTenantHeader` 改为 `withoutIdentityHeaders`：一次性移除 `X-User-Id`、`X-Username`、`X-User-Roles`、`X-Tenant-Id`；方法注释说明白名单不携带可信身份 |
| `gateway/.../filter/PermissionHeaderGlobalFilter.java` | 「注入开关关闭」与「缺 `X-User-Id`」两个不注入分支统一走 `withoutCodesHeader(exchange)`，先移除客户端伪造的 `X-Permission-Codes` 再放行；正常注入路径仍保持「先移除同名头再写入解析结果」 |
| `JwtAuthGlobalFilterTest.java` | 白名单用例扩展为同时伪造 4 个身份/租户头，断言转发请求全部为空 |
| `PermissionHeaderGlobalFilterTest.java` | 「开关关闭」「缺 X-User-Id」两用例改为携带伪造 `X-Permission-Codes`，断言转发请求头已被移除 |

修复点位于网关而非下游，保证「网关头只由网关写入」这一信任约定的唯一入口。

## 3. 行为矩阵

| 请求类型 | 客户端携带头 | 转发前处理 | 下游可见 |
|---|---|---|---|
| 白名单（登录/刷新/埋点/回调） | `X-User-Id`/`X-Username`/`X-User-Roles`/`X-Tenant-Id` | 全部移除 | 无身份头，按匿名处理 |
| 白名单 | `X-Permission-Codes` | 移除 | 无权限码头，下游 fail-closed |
| 已认证受保护请求 | 伪造身份头 | JWT 校验后覆盖为令牌值 | 令牌身份 |
| 已认证受保护请求 | 伪造 `X-Permission-Codes` | 注入前移除并写入解析结果 | 网关解析的权限码 |
| 已认证 + 权限码头注入关闭 | 伪造 `X-Permission-Codes` | 移除后放行 | 无权限码头 |
| actuator health/prometheus | 任意 | 不经路由链，不涉及 | — |

## 4. 验证

```powershell
mvn -B -pl backend/whale-services/whale-gateway -am ^
  "-Dtest=!Sm4CryptoTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- gateway `112` 用例，`0` 失败、`0` 错误，BUILD SUCCESS；
- Checkstyle `0` 违规（parent / whale-security / gateway）；
- `JwtAuthGlobalFilterTest`：白名单用例断言 `X-User-Id=888`、`X-Username=forged`、`X-User-Roles=admin`、`X-Tenant-Id=999` 全部不会到达下游；
- `PermissionHeaderGlobalFilterTest`：开关关闭/缺用户头时，`X-Permission-Codes=forged-by-client` 被移除；正常注入用例仍验证「移除伪造头 + 写入解析值」。

## 5. 边界

- **白名单本身仍是匿名入口**：本次只保证「不携带伪造身份」，不改变埋点上报匿名可用的设计（限流与批量上限由审计服务负责）；
- **审计归属回退**：`LogIngestController` 在无网关身份头时回退请求体 `userId`，属既有业务契约，不在本次范围；
- **直连下游端口**：与既往结论一致，服务端口仅内网可达，生产只暴露网关；直连时身份头不受本过滤器保护；
- **历史日志**：修复前已写入的埋点归属无法追溯纠正，仅能保证后续请求不再被伪造头污染；
- **actuator 路径**：不经过网关路由链，`X-Permission-Codes`/身份头不会到达 actuator，无需额外处理。
