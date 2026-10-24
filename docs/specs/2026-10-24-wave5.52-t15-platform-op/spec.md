# Spec：Wave 5.52 M4-T15 `platform_op` + 确认卡片 + 一次性确认令牌

> 日期：2026-10-24 ｜ 状态：待批准 ｜ 关联规划：`docs/plans/wave5.52-webim-langchain-agent.md` §6.4、§八 M4-T15

## 一、背景与已确认事实

| 项 | 结论 |
|---|---|
| 权限注解 | `@RequirePermission` 仅支持精确匹配 + `super_admin`，无通配符（`RequirePermissionInterceptor`） |
| whale-agent | 无 Redis；有 Java 11 `HttpClient` 与 Jackson 静态 `ObjectMapper` 用法（`LangchainChatService`） |
| whale-im | 有 Redis；`im_message.content_type` 支持 `CARD`；`im_agent_run` 已有 `status`、`tool_trace_json`、`confirmation_json` |
| Python | 工具全部回调 Java；`/v1/agent/confirm` 当前为 501 占位 |
| 目标写操作 | `whale-form`（8084）`POST /form/definition`，权限码 `sys:form:add`，请求体 `FormDefinitionSaveRequest`（`code`/`name` 必填） |
| 前端 | 无 WebIM 前端，T15 只做后端与卡片数据，不新增页面 |

## 二、范围

- 新增 1 个 L2 写操作 `form.definition.create`（建表单定义），必须人工确认后执行。
- 新增确认令牌表 `agent_operation`，5 分钟有效、一次性、绑定用户。
- 新增 Java 确认/拒绝接口与 Python 代理；whale-im 收到 `confirm` 事件后落 CARD 消息并提供确认接口。
- 不实现：其他写操作（发流程/改配置）、前端确认卡片渲染（T18 前无前端）。

## 三、设计

### 3.1 操作目录（whale-agent）

`com.bluewhale.agent.support.ops.PlatformOperationCatalog`：

- 仅 1 项 `form.definition.create`：
  - `domain=form`，`permission=sys:ai:ops:form`
  - `target=form`，`method=POST`，`path=/form/definition`
  - 必填参数 `code`、`name`；可选 `description`
  - `summary(params)` 生成「创建表单定义：<name>（<code>）」摘要
- 未登记操作一律拒绝（未知操作不落库）。

### 3.2 表 `agent_operation`（whale-agent `db/init.sql`）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 雪花主键 |
| `confirmation_id` | VARCHAR(64) | 唯一，UUID |
| `user_id` | BIGINT | 发起用户 |
| `conversation_id` | BIGINT NULL | 来源会话 |
| `operation` | VARCHAR(64) | 操作名 |
| `params_json` | TEXT | 参数 JSON |
| `params_hash` | VARCHAR(64) | SHA-256 参数哈希（审计可比对） |
| `permission` | VARCHAR(64) | 所需权限码 |
| `status` | VARCHAR(16) | PENDING/CONFIRMED/REJECTED/EXPIRED/SUCCESS/FAILED |
| `expires_at` | DATETIME | 创建 + 300s |
| `confirmed_at` | DATETIME NULL | 确认时间 |
| `result_json` | TEXT NULL | 下游响应 |
| `error_msg` | VARCHAR(512) NULL | 失败原因 |
| 公共字段 | —— | `create_time`/`update_time`/`deleted` |

实体 `AgentOperation` + `AgentOperationMapper`。

### 3.3 `PlatformOpService`（whale-agent）

- `create(operation, params, userId, conversationId)`：校验操作存在与必填参数 → 生成 UUID → `PENDING` + 300s → 返回 `{confirmationId, operation, summary, permission, expiresAt, status}`。
- `confirm(confirmationId, userId, permissionCodes)`：
  1. 查询令牌；不存在 → 404；非本人 → 403；已过期 → 置 `EXPIRED` 并 410。
  2. 权限：`super_admin` 或权限码集合含 `sys:ai:ops:form`，否则 403。
  3. 原子一次性：`UPDATE agent_operation SET status='CONFIRMED', confirmed_at=now() WHERE confirmation_id=? AND status='PENDING' AND expires_at>now()`；影响行数 0 → 409（已使用/已过期）。
  4. 调下游：`POST {targets.form}/form/definition`，透传 `X-User-Id`、`X-Permission-Codes`；2xx 且 `code=0` → `SUCCESS` 并写 `result_json`；否则 `FAILED` + `error_msg`，抛出业务异常由控制器转为 `Result` 错误。
- `reject(confirmationId, userId)`：本人 + `PENDING` → `REJECTED`（同样原子更新）。
- HTTP 调用抽接口 `OperationInvoker`（默认 `HttpClient` 实现），便于单测注入。

配置（`whale.agent.ops.*`，`AgentProperties.Ops`）：

```yaml
whale:
  agent:
    ops:
      enabled: ${WHALE_AGENT_OPS_ENABLED:false}
      ttl-seconds: ${WHALE_AGENT_OPS_TTL_SECONDS:300}
      timeout-seconds: ${WHALE_AGENT_OPS_TIMEOUT_SECONDS:10}
      targets:
        form: ${WHALE_AGENT_OPS_TARGET_FORM:http://127.0.0.1:8084}
```

`enabled=false` 时创建/确认均返回「平台操作未启用」。

### 3.4 控制器 `AgentOpsController`（whale-agent）

类级 `@RequirePermission("sys:agent:chat")`：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/agent/tools/platform-op` | Python 工具回调，创建确认令牌 |
| POST | `/agent/ops/{confirmationId}/confirm` | 用户确认执行 |
| POST | `/agent/ops/{confirmationId}/reject` | 用户拒绝 |

均加 `@AuditLog`；confirm 的 `targetType=agent_operation`、`targetId=confirmationId`。

### 3.5 Python（whale-ai）

- `registry.py`：新增 `platform_op`（endpoint `platform-op`，`risk_level="L2"`，`permission="sys:ai:ops:*"`，payload `{operation, params}`，必填 `operation` 且 `params` 为对象）。
- `client.py`：新增 `confirm(confirmation_id, approved, user_id, permission_codes)` 调 `/agent/ops/{id}/confirm|reject`。
- `routes.py`：`/v1/agent/confirm` 改为代理 Java 确认接口（透传 `X-User-Id`/`X-Permission-Codes`），返回 Java `data`；失败转 `HTTPException`。

### 3.6 whale-im

- `AgentStreamClient`：`confirm` 事件透传；新增 `confirmOperation(runId, confirmationId, approved, userId, permissionCodes)` 调 whale-agent。
- `ImAgentChatService`：收到 `confirm` 事件时：
  - 落 `content_type=CARD` 的 BOT 消息（JSON：`type=platform_op`、`confirmationId`、`operation`、`summary`、`permission`、`expiresAt`）；
  - run 置 `WAITING_CONFIRM`，`confirmation_json` 写卡片 JSON；
  - 广播 `agent.confirm` 帧。
- 新增 `ImAgentRunController` `POST /im/agent-runs/{id}/confirm`：校验会话成员；`approved=true` 调 Java confirm，回写 run `SUCCESS/FAILED` 并落结果消息 + 广播；`approved=false` 调 reject 并置 `FAILED`。
- `WsFrames`：新增 `TYPE_AGENT_CONFIRM="agent.confirm"` 与构造方法。

### 3.7 测试

- whale-agent：`PlatformOpServiceTest` —— 创建成功、未知操作拒绝、过期、非本人、越权、一次性（第二次 409）、拒绝；`OperationInvoker` 用桩。
- Python：`test_tools.py` 增加 `platform_op` payload/权限/风险级用例；`test_tools.py` 或新增确认代理用例。
- whale-im：`ImAgentChatServiceTest` 增加 `confirm` 事件落 CARD + `WAITING_CONFIRM` 用例。

## 四、验收

1. 未确认的 `platform_op` 不触发任何下游写请求。
2. 同一 `confirmationId` 仅能成功执行一次；过期/越权/非本人均被拒绝且状态可追溯。
3. 审计可按 `confirmationId` 查到创建、确认、结果。
4. `whale-agent`、`whale-im` 单测全绿 + Checkstyle 0；Python `compileall` + `pytest` 全绿。
5. `/v1/tools` 暴露 `platform_op`（L2、`sys:ai:ops:*`）。

## 五、风险

- 目标服务接口字段以 `whale-form` 现有 DTO 为准，缺字段时仅透传 `code`/`name`/`description`。
- `enabled=false` 默认关闭，需显式开启才可创建令牌。
