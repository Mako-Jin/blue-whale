# docs/api — 前后端契约

本目录存放**跨端唯一真相源**：OpenAPI 3.1 契约文件。

```
docs/api/openapi.json
```

它由 `scripts/export-openapi.sh` 从**运行中的应用**导出，不是手写的。原因与取舍见 `docs/decisions/0006-接口契约的生成与消费.md`。

---

## 一、当前接口清单

契约版本 `0.1.0`，标题「BlueWhale 服务端 API」，共 **25 个接口**。

| 域       | 模块               | 接口数 | 说明                                              |
| -------- | ------------------ | ------ | ------------------------------------------------- |
| 日志     | `sts-server-log`   | 5      | 客户端日志上报 / 查询 / 聚合                      |
| 行为事件 | `sts-server-track` | 4      | 客户端行为事件上报 / 查询 / 聚合                  |
| 权限     | `sts-server-auth`  | 15     | 认证 4 + 租户 5 + 用户 6（详见下表）              |
| 系统     | `sts-app-server`   | 1      | 健康检查                                          |

### 日志域（`sts-server-log`）

| 方法   | 路径                               | 说明                                                |
| ------ | ---------------------------------- | --------------------------------------------------- |
| `POST` | `/api/logs/client`                 | 批量上报客户端日志，单批最多 500 条                 |
| `GET`  | `/api/logs/client`                 | 分页查询，支持级别 / 命名空间 / 关键词 / 时间窗过滤 |
| `GET`  | `/api/logs/client/{id}`            | 查询单条日志详情                                    |
| `GET`  | `/api/logs/client/trace/{traceId}` | 按链路标识取整条链路，按时间升序                    |
| `GET`  | `/api/logs/client/stats`           | 按级别聚合统计                                      |

### 行为事件域（`sts-server-track`）

| 方法   | 路径                       | 说明                                                                            |
| ------ | -------------------------- | ------------------------------------------------------------------------------- |
| `POST` | `/api/events/client`       | 批量上报行为事件，单批最多 500 条；逐条校验事件名，返回 `accepted` / `rejected` |
| `GET`  | `/api/events/client`       | 分页查询，支持事件名 / 用户 / 设备 / 会话 / 应用 / 时间窗过滤                   |
| `GET`  | `/api/events/client/{id}`  | 查询单条事件详情                                                                |
| `GET`  | `/api/events/client/stats` | 按**事件名**分组聚合                                                            |

> **聚合接口刻意不接受 `eventName` 过滤参数。** 它本身就是"按事件名分组"，再传一个 `eventName` 去过滤是自相矛盾的；要看单个事件名的量，用分页查询的 `total`。

> **排序与时间窗一律用服务端接收时间**（`serverTime`）。客户端上报的 `clientTime` 也存下来，但只用于展示与归因参考 —— 用户会改系统时间。

> **一个易踩的改名**：前端 `EventEnvelope` 里的字段叫 `clientTime`，契约里叫 `timestamp`（与日志域的 `ClientLogItem` 对齐）。改名发生在接线层（`platform-event-console/src/events.ts` 的 `toReportItem`）。**字段名写错不会有任何报错** —— 请求照样 200、事件照样落库，只有客户端时间这一列静默为空。前后端字段名不一致是这类"静默丢字段"的高发区。

### 系统域（`sts-app-server`）

| 方法  | 路径          | 说明                           |
| ----- | ------------- | ------------------------------ |
| `GET` | `/api/health` | 服务健康检查，含数据库探活结果 |

### 权限域（`sts-server-auth`）

**认证**（`/api/auth/*`）—— 唯一的公开入口是 `login` / `refresh` / `logout`；其余需带 `Authorization: Bearer <access>`。

| 方法   | 路径                       | 所需权限   | 说明                                                                     |
| ------ | -------------------------- | ---------- | ------------------------------------------------------------------------ |
| `POST` | `/api/auth/login`          | 公开       | 登录，签发 access + refresh。**刻意不区分"账号不存在"与"口令错误"**       |
| `POST` | `/api/auth/refresh`        | 公开       | 刷新令牌。**轮换 + 重放检测**：已作废的 jti 再次使用 → 撤销该用户全部刷新令牌 |
| `POST` | `/api/auth/logout`         | 公开       | 登出（幂等）。不传令牌则撤销该用户全部刷新令牌                            |
| `GET`  | `/api/auth/me`             | 仅需登录   | 当前登录者信息，**含其全部权限标识**（前端据此做菜单/按钮级控制）         |

**租户**

| 方法     | 路径                     | 所需权限          |
| -------- | ------------------------ | ----------------- |
| `GET`    | `/api/auth/tenants`      | `auth:tenant:read`   |
| `POST`   | `/api/auth/tenants`      | `auth:tenant:create` |
| `GET`    | `/api/auth/tenants/{id}` | `auth:tenant:read`   |
| `PUT`    | `/api/auth/tenants/{id}` | `auth:tenant:update` |
| `DELETE` | `/api/auth/tenants/{id}` | `auth:tenant:delete` |

**用户**

| 方法     | 路径                              | 所需权限        | 说明                                     |
| -------- | --------------------------------- | --------------- | ---------------------------------------- |
| `GET`    | `/api/auth/users`                 | `auth:user:read`   | 分页查询，受租户隔离                     |
| `POST`   | `/api/auth/users`                 | `auth:user:create` | 新建，租户默认取当前登录者               |
| `GET`    | `/api/auth/users/{id}`            | `auth:user:read`   |                                          |
| `PUT`    | `/api/auth/users/{id}`            | `auth:user:update` |                                          |
| `DELETE` | `/api/auth/users/{id}`            | `auth:user:delete` | **不能删自己**                           |
| `POST`   | `/api/auth/users/{id}/password`   | `auth:user:update` | 重置口令，**置为需改密并解锁账号**       |

> **权限标识的写法与取值**是跨端约定，见 `sts-server-auth` 的 `AuthPermissions` 与 ADR-0016；`/api/auth/me` 返回的 `permissions` 与这里逐字一致。
>
> **组织 / 角色 / 用户组 / 资源的管理接口尚未落地**（实体与 Mapper 已就绪，见 ADR-0016 末尾的"未覆盖清单"）。

### 关键数据模型

| 模型                        | 说明                                                                                                                                         |
| --------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| `ClientLogItem`             | 单条上报日志（`level` / `namespace` / `timestamp` / `message` / `data` / `context` / `traceId` / `sessionId` / `route` / `release`）         |
| `ClientLogReportRequest`    | 批量上报请求体：`app` / `release` / `sessionId` / `logs[]`                                                                                   |
| `ClientLogReportResultVO`   | 上报结果：`accepted` / `rejected`                                                                                                            |
| `ClientLogVO`               | 服务端返回的日志记录（含服务端生成的 `id` / `serverTime` / `ip`）                                                                            |
| `ClientLogStatsVO`          | 聚合结果：`total` / `levels[]` / `startTime` / `endTime`                                                                                     |
| `PageResultClientLogVO`     | 分页结果：`records` / `total` / `page` / `size` / `pages`                                                                                    |
| `ClientEventItem`           | 单条上报事件（`eventId` / `eventName` / `userId` / `deviceId` / `sessionId` / `route` / `release` / `props` / `propsVersion` / `timestamp`） |
| `ClientEventReportRequest`  | 批量上报请求体：`app`（必填） / `release` / `sessionId` / `events[]`（必填）                                                                 |
| `ClientEventReportResultVO` | 上报结果：`accepted` / `rejected`                                                                                                            |
| `ClientEventVO`             | 服务端返回的事件记录（含服务端生成的 `id` / `serverTime` / `ip`）                                                                            |
| `ClientEventStatsVO`        | 聚合结果：`total` / `names[]`（`ClientEventNameCount`） / `startTime` / `endTime`                                                            |
| `PageResultClientEventVO`   | 分页结果，结构同日志域                                                                                                                       |
| `HealthVO`                  | 健康检查：`status` / `application` / `dependencies`                                                                                          |
| `LoginRequest`              | 登录入参：`tenantCode` / `username` / `password`（**三者均必填**）                                                                           |
| `LoginVO`                   | 登录结果：`accessToken` / `refreshToken` / `tokenType` / `expiresIn` / `userId` / `username` / `displayName` / `tenantId` / `tenantCode` / `roles[]` / `permissions[]` |
| `RefreshRequest`            | 刷新入参：`refreshToken`（必填）                                                                                                             |
| `MeVO`                      | 当前登录者：`userId` / `username` / `displayName` / `tenantId` / `tenantCode` / `roles[]` / `permissions[]`                                   |
| `TenantSaveRequest`         | 租户新建/更新入参：`tenantCode`\* / `tenantName`\* / `parentId` / `description` / `status` / `allowRegister` / `allowAdd`（**`tenantCode` 创建后不可改**） |
| `TenantVO`                  | 租户视图：`id` / `tenantCode` / `tenantName` / `parentId` / `tenantLevel` / `status` / `description` / `allowRegister` / `allowAdd` / `createTime` / `updateTime` / `hasChild` |
| `UserSaveRequest`           | 用户新建/更新入参：`tenantId` / `username`\* / `displayName` / `email` / `phone` / `password` / `status`                                     |
| `PasswordResetRequest`      | 重置口令入参：`password`\*（重置后置为"需改密"并解锁账号）                                                                                    |
| `UserVO`                    | 用户视图：`id` / `tenantId` / `username` / `displayName` / `email` / `phone` / `status` / `failedAttempts` / `lockedUntil` / `lastLoginAt` / `mfaEnabled` / `avatarUrl` / `createTime` / `updateTime` —— **不含任何口令字段，连哈希也不返回** |
| `PageResultTenantVO` / `PageResultUserVO` | 分页结果，结构同日志域（`records` / `total` / `page` / `size` / `pages`）                                                        |
| `RVoid`                     | 无数据的成功响应（`data` 为空），用于删除与重置口令这类"只关心成败"的接口                                                                    |
| `R*` 前缀                   | 统一响应包装 `R<T>`：`code` / `message` / `data` / `timestamp`                                                                               |

> **业务失败同样返回 HTTP 200。** 契约的 `description` 里写明了这一点：结果只由 `code` 表达，调用方统一判断 `code === 0`。传输层失败（401/403/404/5xx）由框架按标准返回，不套统一包装。

---

## 二、契约如何更新（硬性流程）

**后端接口有任何增删改，都必须走完这三步，并与代码在同一次提交里完成。**

```bash
# 1. 构建后端（导出脚本需要可运行的 jar）
cd server && mvn -DskipTests install

# 2. 从运行中的应用导出契约（默认端口 18080，可自动等待启动）
cd .. && bash scripts/export-openapi.sh

# 3. 重新生成前端类型
cd web && pnpm --filter common-api gen:api
```

产物：

| 文件                                                | 是否入库 | 说明                   |
| --------------------------------------------------- | -------- | ---------------------- |
| `docs/api/openapi.json`                             | ✅ 入库  | 跨端唯一真相源         |
| `web/packages/common-api/src/generated/schema.d.ts` | ✅ 入库  | 前端类型，**禁止手改** |

生成物入库的目的有两个：前端构建不依赖后端可运行；契约变更在 code review 里显式出现为一个 diff。

---

## 三、为什么导出脚本用 18080 端口

8080 上很可能正跑着开发用的实例。端口冲突时脚本会**连上另一个服务并导出一份错误的契约，而且没有任何报错** —— 这是最难发现的一类错误。因此脚本默认使用 `OPENAPI_PORT`（默认 `18080`），并要求显式覆盖：

```bash
OPENAPI_PORT=18081 bash scripts/export-openapi.sh
```

---

## 四、契约里"任意值"字段的处理

日志的 `data` / `context` 与事件的 `props` 在后端声明为 `Object`，OpenAPI 的单层 schema 表达不了"任意 JSON"，生成器只能给出保守形态——而且两代生成链给出的形态**都不理想**：

| 生成链                                 | 对"任意值"的渲染        | 问题                                                                                            |
| -------------------------------------- | ----------------------- | ----------------------------------------------------------------------------------------------- |
| springdoc 2.x（OpenAPI 3.0）           | `Record<string, never>` | 语义是"不允许有任何字段的对象"，`{ orderId: '…' }` 编译期就被拒                                 |
| springdoc 3.x（OpenAPI 3.1，**当前**） | `unknown`               | 过宽：函数、Symbol 也能通过编译，而它们会让 `JSON.stringify` 抛错，一条脏数据就能让整批上报失败 |

因此这一缺口分两处收紧：**类型本体**（`JsonValue` / `JsonArray` / `JsonObject`）与 `sanitize` 函数放在 `web/packages/common-tools/src/json`，`common-api` 只做转出并按字段名替换（日志用 `WithJsonPayload` 换 `data` / `context`，事件用 `WithJsonProps` 换 `props`），其余字段继续完全由契约生成。详见 ADR-0006 第 4 条与 ADR-0008 第 5 条。

> 升到 OpenAPI 3.1 还有一个连带修复：分页结果 `records` 的元素与导出的 `ClientLogVO`
> 曾不是同一个类型（前者未经收紧）。这个裂缝在 3.0 时代被 `Record<string, never>` 的
> 可赋值性掩盖，3.1 后立刻变成编译错误——已用 `WithJsonRecords` 修复，并有类型断言钉住。
>
> **事件域踩了同一个坑**：分页结果的 `records` 元素、上报请求体的 `events` 数组元素
> 都未经收紧。**新增任何带"任意 JSON 字段"的域时，这三处（本体 / 分页元素 / 请求体数组元素）
> 必须一并收紧** —— 只改第一处会被类型系统放过。

---

## 五、待补充

- 组织 / 角色 / 用户组 / 资源的管理接口尚未落地（权限域的 12 张表、实体与 Mapper 已就绪，见 ADR-0016 末尾的"未覆盖清单"）
- 前端 `common-api` 尚未接线权限域（`src/auth.ts`）与 token 注入；`common-tools/http` 已预留 `onRequest` 钩子（见 `HttpConfig`）
- 契约尚未按域拆分为多文件；接口量变大后再考虑 `openapi/` 目录方案
