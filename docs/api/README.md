# docs/api — 前后端契约

本目录存放**跨端唯一真相源**：OpenAPI 3.1 契约文件。

```
docs/api/openapi.json
```

它由 `scripts/export-openapi.sh` 从**运行中的应用**导出，不是手写的。原因与取舍见 `docs/decisions/0006-接口契约的生成与消费.md`。

---

## 一、当前接口清单

契约版本 `0.1.0`，标题「BlueWhale 服务端 API」，共 **42 个接口**。

| 域       | 模块                | 接口数 | 说明                                        |
| -------- | ------------------- | ------ | ------------------------------------------- |
| 日志     | `sts-server-log`    | 5      | 客户端日志上报 / 查询 / 聚合                |
| 行为事件 | `sts-server-track`  | 4      | 客户端行为事件上报 / 查询 / 聚合            |
| 权限     | `sts-server-auth`   | 23     | 认证 4 + 租户 5 + 用户 6 + 资源 6 + 角色 8（详见下表） |
| 审计     | `sts-server-audit`  | 3      | **只读**：分页 / 详情 / 按动作聚合          |
| 系统     | `sts-app-server`    | 1      | 健康检查                                    |

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

| 方法   | 路径                | 所需权限 | 说明                                                                                                                         |
| ------ | ------------------- | -------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `POST` | `/api/auth/login`   | 公开     | 登录，签发 access + refresh。**刻意不区分"账号不存在"与"口令错误"**                                                          |
| `POST` | `/api/auth/refresh` | 公开     | 刷新令牌。**轮换 + 重放检测**：已作废的 jti 再次使用 → 撤销该用户全部刷新令牌                                                |
| `POST` | `/api/auth/logout`  | 公开     | 登出（幂等）。不传令牌则撤销该用户全部刷新令牌                                                                               |
| `GET`  | `/api/auth/me`      | 仅需登录 | 当前登录者信息，**含其全部权限标识**。前端由基座单点取用后经全局状态下发，子应用**不得自行调用**（见 `docs/decisions/0018`） |

**租户**

| 方法     | 路径                     | 所需权限             |
| -------- | ------------------------ | -------------------- |
| `GET`    | `/api/auth/tenants`      | `auth:tenant:read`   |
| `POST`   | `/api/auth/tenants`      | `auth:tenant:create` |
| `GET`    | `/api/auth/tenants/{id}` | `auth:tenant:read`   |
| `PUT`    | `/api/auth/tenants/{id}` | `auth:tenant:update` |
| `DELETE` | `/api/auth/tenants/{id}` | `auth:tenant:delete` |

**用户**

| 方法     | 路径                            | 所需权限           | 说明                               |
| -------- | ------------------------------- | ------------------ | ---------------------------------- |
| `GET`    | `/api/auth/users`               | `auth:user:read`   | 分页查询，受租户隔离               |
| `POST`   | `/api/auth/users`               | `auth:user:create` | 新建，租户默认取当前登录者         |
| `GET`    | `/api/auth/users/{id}`          | `auth:user:read`   |                                    |
| `PUT`    | `/api/auth/users/{id}`          | `auth:user:update` |                                    |
| `DELETE` | `/api/auth/users/{id}`          | `auth:user:delete` | **不能删自己**                     |
| `POST`   | `/api/auth/users/{id}/password` | `auth:user:update` | 重置口令，**置为需改密并解锁账号** |

**资源目录**（权限资源：菜单 / 页面 / 按钮 / 接口 / 数据）

| 方法     | 路径                       | 所需权限               | 说明                                                                      |
| -------- | -------------------------- | ---------------------- | ------------------------------------------------------------------------- |
| `GET`    | `/api/auth/resources`      | `auth:resource:read`   | 分页平铺列表。按 `keyword` / `resourceType` / `parentCode` / `isEnabled` 过滤 |
| `GET`    | `/api/auth/resources/tree` | `auth:resource:read`   | 按 `parentCode` 组装的树，全量返回。**孤儿与成环节点按根返回，不丢弃**    |
| `GET`    | `/api/auth/resources/{id}` | `auth:resource:read`   |                                                                           |
| `POST`   | `/api/auth/resources`      | `auth:resource:create` | 标识须为「域:资源:动作」三段小写。**若该标识曾被删除，则恢复原记录而非新建** |
| `PUT`    | `/api/auth/resources/{id}` | `auth:resource:update` | **资源标识不可修改**；父资源不能是自身或自身的下级（`40913`）             |
| `DELETE` | `/api/auth/resources/{id}` | `auth:resource:delete` | 逻辑删除。有子资源（`40913`）或**已被角色引用**（`40915`）时拒绝          |

> 这张表里存的**就是权限码**（`auth:user:create`），不是资源标识（`auth:user`）。
> 两者形状相近、混用不报错 —— 见 `docs/decisions/0021`。
>
> **权限标识的写法与取值**是跨端约定，见 `sts-server-auth` 的 `AuthPermissions` 与 ADR-0016；`/api/auth/me` 返回的 `permissions` 与这里逐字一致。

**角色**（角色本身，以及**把资源授给角色**）

| 方法     | 路径                                        | 所需权限           | 说明                                                                                     |
| -------- | ------------------------------------------- | ------------------ | ---------------------------------------------------------------------------------------- |
| `GET`    | `/api/auth/roles`                           | `auth:role:read`   | 分页列表。按 `keyword`（编码 / 名称） / `status` / `category` 过滤，按当前租户限定         |
| `GET`    | `/api/auth/roles/{id}`                      | `auth:role:read`   |                                                                                          |
| `POST`   | `/api/auth/roles`                           | `auth:role:create` | **若编码曾被已删除的角色占用，则恢复原记录**（且复活出来的是空角色）                       |
| `PUT`    | `/api/auth/roles/{id}`                      | `auth:role:update` | **角色编码与所属租户不可修改**；系统内置角色不可停用、不可改分类                            |
| `DELETE` | `/api/auth/roles/{id}`                      | `auth:role:delete` | 逻辑删除。系统内置（`40917`）/ 有子角色（`40913`）/ **有成员（`40916`）** 时拒绝             |
| `GET`    | `/api/auth/roles/{id}/resources`            | `auth:role:read`   | 该角色已授予的资源，含 `effect` / `priority` / 生效区间 / **`effectiveNow`（派生）**        |
| `POST`   | `/api/auth/roles/{id}/resources`            | `auth:role:update` | 授予。按 (角色, 资源) **upsert**：重复调用改效果而不是堆行；`effect` 取 `ALLOW` / `DENY`     |
| `DELETE` | `/api/auth/roles/{id}/resources/{resourceId}` | `auth:role:update` | 撤销。**从未授予过返回 `40407`**，不与撤销成功混淆                                          |

> **授权挂在角色下**（`/roles/{id}/resources`）而不是单开平级资源：授权关系是角色的内部边，
> 没有脱离角色独立存在的意义，也没有独立生命周期（不存在"无角色的授权"）。
>
> **`DENY` 是"排除"的表达方式**，判定按资源聚合、**一条命中即拒绝**，
> 因此它在**多个角色之间**才成立（角色 A 允许 + 角色 B 拒绝 ⇒ 拒绝）。
> 这条语义是本切片才真正实现的 —— 见 `docs/decisions/0022`。
>
> **"给用户 / 用户组授角色"（角色成员关系）尚未落地**（表已就绪、判定侧已支持、无接口）；
> 组织与用户组的管理接口同样未落地（见 ADR-0016 末尾的"未覆盖清单"）。
> **资源目录**（ADR-0021）与**角色管理**（ADR-0022）已落地。

### 审计域（`sts-server-audit`）

**这一域只有读接口 —— 刻意的。** 审计记录由后端拦截器在写接口的 `afterCompletion` 阶段自动产生，
调用方**没有任何路径**往审计表里写东西（没有写接口、`AuditLogService` 没有写方法、
`common-api` 的 `AuditApi` 也没有写方法）。不可篡改不靠权限保护，靠**接口不存在**。
见 `docs/decisions/0019`。

| 方法  | 路径                        | 权限               | 说明                                                                     |
| ----- | --------------------------- | ------------------ | ------------------------------------------------------------------------ |
| `GET` | `/api/audit/logs`           | `auth:audit:read`  | 分页查询。按 `action` / `resource` / `targetId` / `operatorId` / `operatorName` / `result` 与时间窗过滤；默认窗口 7 天，按服务端时间 + id 倒序 |
| `GET` | `/api/audit/logs/{id}`      | `auth:audit:read`  | 单条详情                                                                 |
| `GET` | `/api/audit/logs/stats`     | `auth:audit:read`  | **按动作**分组聚合（`resource` / `operatorId` / 时间窗可过滤），每项含总数与失败数。**刻意不接受 `action` 过滤** —— 它本身就是按动作分组 |

> `POST /api/audit/logs` 返回 **`40500`（请求方法不被支持）**，不是 404 也不是 500：
> 让"用错了 HTTP 方法"这件事说清责任方，比让它落进兜底的"系统内部错误"更有用。
>
> **审计没有客户端时间戳**（日志与事件都有双时间戳）：它不由客户端产生，因此只有一个
> 来自服务端的 `serverTime`。**匿名请求不产生审计**（没有操作人可归责，且公网扫描会灌满表），
> 但**已认证而无权限的请求必须产生**（有明确操作人，且那正是最该留痕的一类）。

### 关键数据模型

| 模型                                      | 说明                                                                                                                                                                                                                                          |
| ----------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ClientLogItem`                           | 单条上报日志（`level` / `namespace` / `timestamp` / `message` / `data` / `context` / `traceId` / `sessionId` / `route` / `release`）                                                                                                          |
| `ClientLogReportRequest`                  | 批量上报请求体：`app` / `release` / `sessionId` / `logs[]`                                                                                                                                                                                    |
| `ClientLogReportResultVO`                 | 上报结果：`accepted` / `rejected`                                                                                                                                                                                                             |
| `ClientLogVO`                             | 服务端返回的日志记录（含服务端生成的 `id` / `serverTime` / `ip`）                                                                                                                                                                             |
| `ClientLogStatsVO`                        | 聚合结果：`total` / `levels[]` / `startTime` / `endTime`                                                                                                                                                                                      |
| `PageResultClientLogVO`                   | 分页结果：`records` / `total` / `page` / `size` / `pages`                                                                                                                                                                                     |
| `ClientEventItem`                         | 单条上报事件（`eventId` / `eventName` / `userId` / `deviceId` / `sessionId` / `route` / `release` / `props` / `propsVersion` / `timestamp`）                                                                                                  |
| `ClientEventReportRequest`                | 批量上报请求体：`app`（必填） / `release` / `sessionId` / `events[]`（必填）                                                                                                                                                                  |
| `ClientEventReportResultVO`               | 上报结果：`accepted` / `rejected`                                                                                                                                                                                                             |
| `ClientEventVO`                           | 服务端返回的事件记录（含服务端生成的 `id` / `serverTime` / `ip`）                                                                                                                                                                             |
| `ClientEventStatsVO`                      | 聚合结果：`total` / `names[]`（`ClientEventNameCount`） / `startTime` / `endTime`                                                                                                                                                             |
| `PageResultClientEventVO`                 | 分页结果，结构同日志域                                                                                                                                                                                                                        |
| `HealthVO`                                | 健康检查：`status` / `application` / `dependencies`                                                                                                                                                                                           |
| `LoginRequest`                            | 登录入参：`tenantCode` / `username` / `password`（**三者均必填**）                                                                                                                                                                            |
| `LoginVO`                                 | 登录结果：`accessToken` / `refreshToken` / `tokenType` / `expiresIn` / `userId` / `username` / `displayName` / `tenantId` / `tenantCode` / `roles[]` / `permissions[]`                                                                        |
| `RefreshRequest`                          | 刷新入参：`refreshToken`（必填）                                                                                                                                                                                                              |
| `MeVO`                                    | 当前登录者：`userId` / `username` / `displayName` / `tenantId` / `tenantCode` / `roles[]` / `permissions[]`                                                                                                                                   |
| `TenantSaveRequest`                       | 租户新建/更新入参：`tenantCode`\* / `tenantName`\* / `parentId` / `description` / `status` / `allowRegister` / `allowAdd`（**`tenantCode` 创建后不可改**）                                                                                    |
| `TenantVO`                                | 租户视图：`id` / `tenantCode` / `tenantName` / `parentId` / `tenantLevel` / `status` / `description` / `allowRegister` / `allowAdd` / `createTime` / `updateTime` / `hasChild`                                                                |
| `UserSaveRequest`                         | 用户新建/更新入参：`tenantId` / `username`\* / `displayName` / `email` / `phone` / `password` / `status`                                                                                                                                      |
| `PasswordResetRequest`                    | 重置口令入参：`password`\*（重置后置为"需改密"并解锁账号）                                                                                                                                                                                    |
| `UserVO`                                  | 用户视图：`id` / `tenantId` / `username` / `displayName` / `email` / `phone` / `status` / `failedAttempts` / `lockedUntil` / `lockReason` / `lockLevel` / `lastLoginAt` / `mfaEnabled` / `avatarUrl` / `createTime` / `updateTime` —— **不含任何口令字段，连哈希也不返回**。锁定三项是给运维看的：`lockLevel` 决定退避时长（第几次被锁），`lockReason` 说明为什么锁 |
| `ResourceSaveRequest`                     | 资源新建/更新入参：`resourceCode`\*（**须为三段小写**，创建后不可改） / `resourceName`\* / `resourceType`\* / `resourceCategory` / `parentCode` / `requestUrl` / `requestMethod` / `icon` / `sortNo` / `isEnabled` / `isWhitelist` / `isDeprecated` / `resourceVersion` / `description`。**没有 `tenantId`**：资源是平台级的 |
| `ResourceVO`                              | 资源视图：`id` / `tenantId`（当前恒为 null） / `resourceCode` / `resourceName` / `resourceType` / **`requestScoped`（由 `resourceType` 派生，不在入参里）** / `resourceCategory` / `parentCode` / `requestUrl` / `requestMethod` / `icon` / `sortNo` / `isEnabled` / `isWhitelist` / `isDeprecated` / `resourceVersion` / `description` / `createTime` / `updateTime` |
| `ResourceTreeNode`                        | 树节点：`id` / `resourceCode` / `resourceName` / `resourceType` / `requestScoped` / `parentCode` / `icon` / `sortNo` / `isEnabled` / `children[]`。字段刻意比 `ResourceVO` 少 —— 树会一次返回全量节点，明细走详情接口 |
| `PageResultResourceVO`                    | 分页结果，结构同日志域（`records` / `total` / `page` / `size` / `pages`）                                                                                                                                                                     |
| `RoleSaveRequest`                         | 角色新建/更新入参：`roleCode`\*（**创建后不可改**） / `roleName`\* / `tenantId` / `parentId` / `roleLevel` / `category` / `dataScope` / `filterJson` / `inheritStrategy` / `isDefault` / `sortNo` / `status` / `description`。**有判定语义的字段严格校验**（`status` / `dataScope` / `isDefault`），**只存不用的宽松接受**（`category` / `inheritStrategy` / `filterJson`） |
| `RoleVO`                                  | 角色视图：`id` / `tenantId` / `roleCode` / `roleName` / `parentId` / `roleLevel` / `category` / `isDefault` / `inheritStrategy` / `dataScope` / `filterJson` / `sortNo` / `status` / `description` / `createTime` / `updateTime`。`dataScope` 等字段标了"**当前只存不用**"——数据权限求值未实现 |
| `RoleResourceGrantRequest`                | 授权入参：`resourceId`\*（**主键，不是资源标识**） / `effect`（`ALLOW` / `DENY`，缺省 `ALLOW`） / `priority` / `effectiveFrom` / `effectiveTo`（毫秒；区间写反返回 `40001`） |
| `RoleResourceVO`                          | 已授资源：`grantId` / `resourceId` / `resourceCode` / `resourceName` / `resourceType` / **`requestScoped`（派生）** / `isEnabled` / `effect` / `priority` / `effectiveFrom` / `effectiveTo` / **`effectiveNow`（派生：此刻是否落在生效区间内）** / `createTime` |
| `PageResultRoleVO` / `PageResultResourceVO` | 分页结果，结构同日志域（`records` / `total` / `page` / `size` / `pages`）                                                                                                                                                                     |
| `PageResultTenantVO` / `PageResultUserVO` | 分页结果，结构同日志域（`records` / `total` / `page` / `size` / `pages`）                                                                                                                                                                     |
| `AuditResult`                             | 枚举 `SUCCESS` / `FAILURE`（线上取值即大写枚举名）                                                                                                                                                                                            |
| `AuditLogVO`                              | 审计记录：`id` / `action` / `resource` / `targetId` / `operatorId` / `operatorName` / `tenantId` / `result` / `errorCode` / `errorMessage` / `httpMethod` / `requestUri` / `ip` / `userAgent` / `traceId` / `durationMs` / `serverTime`（**无客户端时间、无 `update*` / `deleted`**） |
| `AuditLogQuery`                           | 分页查询入参：`page` / `size` + `action` / `resource` / `targetId` / `operatorId` / `operatorName` / `result` / `startTime` / `endTime`（`*Time` 显式为 `int64`，避免被推平成 string）                                                          |
| `AuditActionCount`                        | 单动作聚合项：`action` / `actionCount` / `failureCount`                                                                                                                                                                                       |
| `AuditStatsVO`                            | 聚合结果：`total` / `failureTotal` / `startTime` / `endTime` / `actions[]`（`AuditActionCount`）                                                                                                                                              |
| `PageResultAuditLogVO`                    | 分页结果，结构同日志域                                                                                                                                                                                                                        |
| `RVoid`                                   | 无数据的成功响应（`data` 为空），用于删除与重置口令这类"只关心成败"的接口                                                                                                                                                                     |
| `R*` 前缀                                 | 统一响应包装 `R<T>`：`code` / `message` / `data` / `timestamp`                                                                                                                                                                                |

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

- 组织 / 用户组的管理接口尚未落地，**角色成员关系**（给用户 / 用户组授角色）也没有接口
  （权限域的 12 张表、实体与 Mapper 已就绪，见 ADR-0016 末尾的"未覆盖清单"）。
  **资源目录已落地**（ADR-0021）、**角色管理与角色资源授权已落地**（ADR-0022，8 条路径）
- 前端的域接线进度：**认证 / 会话**（`src/auth.ts`）、**权限域管理**（`src/access.ts`，users / tenants）、**审计**（`src/audit.ts`，只读）均已接入。
  资源目录的 6 条路径与角色的 8 条路径**后端已就绪但前端未接线**，`AccessPermissions` 也因此刻意还没加 `auth:resource:*` / `auth:role:*`（该文件的规则是"只列前端真的用到的"）——
  等 `platform-admin` 的资源管理页与角色管理页落地时一并补。组织 / 用户组待后端补齐接口后按同样写法加入 `access.ts`
- 契约尚未按域拆分为多文件；接口量变大后再考虑 `openapi/` 目录方案
