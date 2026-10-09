# docs/api — 前后端契约

本目录存放**跨端唯一真相源**：OpenAPI 3.1 契约文件。

```
docs/api/openapi.json
```

它由 `scripts/export-openapi.sh` 从**运行中的应用**导出，不是手写的。原因与取舍见 `docs/decisions/0006-接口契约的生成与消费.md`。

---

## 一、当前接口清单

契约版本 `0.1.0`，标题「BlueWhale 服务端 API」，共 **6 个接口**。

### 日志域（`sts-server-log`）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/logs/client` | 批量上报客户端日志，单批最多 500 条 |
| `GET` | `/api/logs/client` | 分页查询，支持级别 / 命名空间 / 关键词 / 时间窗过滤 |
| `GET` | `/api/logs/client/{id}` | 查询单条日志详情 |
| `GET` | `/api/logs/client/trace/{traceId}` | 按链路标识取整条链路，按时间升序 |
| `GET` | `/api/logs/client/stats` | 按级别聚合统计 |

### 系统域（`sts-app-server`）

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/health` | 服务健康检查，含数据库探活结果 |

### 关键数据模型

| 模型 | 说明 |
|------|------|
| `ClientLogItem` | 单条上报日志（`level` / `namespace` / `timestamp` / `message` / `data` / `context` / `traceId` / `sessionId` / `route` / `release`） |
| `ClientLogReportRequest` | 批量上报请求体：`app` / `release` / `sessionId` / `logs[]` |
| `ClientLogReportResultVO` | 上报结果：`accepted` / `rejected` |
| `ClientLogVO` | 服务端返回的日志记录（含服务端生成的 `id` / `serverTime` / `ip`） |
| `ClientLogStatsVO` | 聚合结果：`total` / `levels[]` / `startTime` / `endTime` |
| `PageResultClientLogVO` | 分页结果：`records` / `total` / `page` / `size` / `pages` |
| `HealthVO` | 健康检查：`status` / `application` / `dependencies` |
| `R*` 前缀 | 统一响应包装 `R<T>`：`code` / `message` / `data` / `timestamp` |

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

| 文件 | 是否入库 | 说明 |
|------|----------|------|
| `docs/api/openapi.json` | ✅ 入库 | 跨端唯一真相源 |
| `web/packages/common-api/src/generated/schema.d.ts` | ✅ 入库 | 前端类型，**禁止手改** |

生成物入库的目的有两个：前端构建不依赖后端可运行；契约变更在 code review 里显式出现为一个 diff。

---

## 三、为什么导出脚本用 18080 端口

8080 上很可能正跑着开发用的实例。端口冲突时脚本会**连上另一个服务并导出一份错误的契约，而且没有任何报错** —— 这是最难发现的一类错误。因此脚本默认使用 `OPENAPI_PORT`（默认 `18080`），并要求显式覆盖：

```bash
OPENAPI_PORT=18081 bash scripts/export-openapi.sh
```

---

## 四、契约里"任意值"字段的处理

日志的 `data` / `context` 在后端声明为 `Object`，OpenAPI 的单层 schema 表达不了"任意 JSON"，生成器只能给出保守形态——而且两代生成链给出的形态**都不理想**：

| 生成链 | 对"任意值"的渲染 | 问题 |
|--------|------------------|------|
| springdoc 2.x（OpenAPI 3.0） | `Record<string, never>` | 语义是"不允许有任何字段的对象"，`{ orderId: '…' }` 编译期就被拒 |
| springdoc 3.x（OpenAPI 3.1，**当前**） | `unknown` | 过宽：函数、Symbol 也能通过编译，而它们会让 `JSON.stringify` 抛错，一条脏数据就能让整批上报失败 |

因此这一缺口在 `web/packages/common-api/src/types.ts` 用 `JsonValue` **收紧**（既容纳任意 JSON 结构，又挡住不可序列化的值），且只替换 `data` / `context` 两个字段，其余字段继续完全由契约生成。详见 ADR-0006 第 4 条与 ADR-0008 第 5 条。

> 升到 OpenAPI 3.1 还有一个连带修复：分页结果 `records` 的元素与导出的 `ClientLogVO`
> 曾不是同一个类型（前者未经收紧）。这个裂缝在 3.0 时代被 `Record<string, never>` 的
> 可赋值性掩盖，3.1 后立刻变成编译错误——已用 `WithJsonRecords` 修复，并有类型断言钉住。

---

## 五、待补充

- 接口的鉴权方案（认证过滤器 + 鉴权拦截器）尚未落地，`40100` / `40300` 两个错误码已在 `sts-common-basic` 中定义
- 契约尚未按域拆分为多文件；接口量变大后再考虑 `openapi/` 目录方案
