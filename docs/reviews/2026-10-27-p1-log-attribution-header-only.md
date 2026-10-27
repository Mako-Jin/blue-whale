# T2 检查报告：埋点归属只信任网关头

- 日期：2026-10-27
- 范围：`whale-audit` 前端日志/行为埋点批量上报
- 关联：P1 #14 边界「白名单匿名 + 请求体 userId 回退」、待办清单 T2

## 1. 问题

`LogIngestController.resolveUserId(HttpServletRequest, Long)` 在 `X-User-Id` 缺失或不可解析时回退请求体 `userId`。这两个上报接口在网关白名单中可匿名访问，P1 #14 虽已剥离客户端伪造的 `X-User-Id`，但浏览器仍可在请求体声明任意 `userId`，把埋点归属到他人（审计溯源污染）。

## 2. 改动

| 文件 | 说明 |
|---|---|
| `whale-audit/.../controller/LogIngestController.java` | `resolveUserId` 去掉 `fallbackUserId` 参数：只读取 `X-User-Id`；缺失/非法一律返回 `null`（匿名落库），请求体声明永不参与归属 |
| `whale-audit/.../dto/LogBatchRequest.java` | `userId` 字段注释明确为「兼容旧版 SDK 的忽略字段」，服务端以网关头为准 |
| `LogIngestControllerTest.java` | 「合法批量」改为断言 `saveBatch(request, null)` 且从不使用请求体 `userId`；HTTP 无鉴权用例同步断言匿名落库；头存在用例额外断言不会落匿名 |

## 3. 验证

- `mvn -o -pl whale-ops/whale-audit -am -Dtest=LogIngestControllerTest -Dsurefire.failIfNoSpecifiedTests=false test` → exit 0。
- `mvn -o -pl whale-ops/whale-audit test` → 模块全部单测 exit 0。
- 覆盖：请求体伪造 `userId` 被忽略；网关头存在时以头为准；无头匿名上报返回 200 且归属为 `null`。

## 4. 边界

- 旧版日志 SDK 仍会在请求体携带 `userId`，服务端忽略即可，无需同步升级；登录前产生的埋点将不再归属个人（符合「只信网关头」）。
- P1 #14 之前已落库的伪造归属无法追溯修正（见待办 S-05）。
- 匿名上报仍受网关白名单保护，接口本身不做鉴权；若后续要阻断匿名上报，需另行调整白名单与限流策略。
