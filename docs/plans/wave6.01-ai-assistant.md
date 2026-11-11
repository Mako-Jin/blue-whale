# Wave 6.01 — AI 助手平台（whale-assistant）详细设计

> 状态：**实施中（M0 + M1 + M2 + M3 已落地）** · 起草：2026-11-09
> 总体规划：[wave6-iot-twin-ai-assistant-plan.md](wave6-iot-twin-ai-assistant-plan.md)（§四 平台三）

---

## 一、目标与定位

把现有「知识 Agent」升级为**多助手运营平台**：任意业务应用可创建、配置、内嵌自己的 AI 助手。

**三层职责不变，只加一层：**

| 层 | 服务 | 职责 | 是否变更 |
|---|---|---|---|
| 管理层（新） | `whale-assistant`(8087, Java) | 助手定义/生命周期、会话与消息持久化、工具授权、开放 API、用量统计 | 新增 |
| 编排层 | `whale-ai`(8092, Python, 仅内网) | LangGraph 编排、SSE 流式、记忆压缩 | 配置化改造 |
| 知识层 | `whale-agent`(8090, Java) | Lucene 检索、事实通道、引用校验 | 不变 |

**铁律**：`whale-ai` 继续不注册网关/Nacos、不持业务库凭据；助手配置由 `whale-assistant` 解析后**随请求下发**，Python 侧保持无状态。

## 二、M0 已落地（本次）

| 交付物 | 位置 |
|---|---|
| Maven 模块（单模块分包，对齐 whale-agent） | `whale-backend/whale-services/whale-assistant` |
| 启动类 + `/assistant/info` 自检端点 | `WhaleAssistantApplication` / `AssistantInfoController` |
| `application.yml`（端口 8087、`bluewhale_assistant`、Flyway、权限码头拦截） | `src/main/resources/application.yml` |
| Flyway V1 基线 5 表 | `src/main/resources/db/migration/V1__baseline.sql` |
| ArchUnit 分层门禁（mapper/entity 空包期 `allowEmptyShould`） | `src/test/.../AssistantArchitectureTest.java` |
| 网关路由 `/assistant/**` → `lb://whale-assistant` | `whale-gateway/application.yml`（34 条，1 条规划） |
| service-catalog 登记（§一 #33 / §二 / §五 / §七） | `docs/service-catalog.md` |

编译验证：`mvnw -f whale-backend/pom.xml -pl whale-services/whale-assistant -am compile` ✅（需 `JAVA_HOME=D:\tools\installer\jdk\java21`，默认 JAVA_HOME 指向 17 会报"不支持发行版本 21"）。

## 三、数据模型（V1 基线，bluewhale_assistant）

| 表 | 职责 | 关键设计 |
|---|---|---|
| `asst_definition` | 助手定义 | `(tenant_id, code)` 唯一；`system_prompt` + `model_params_json` 随请求下发 whale-ai；`status`: draft/published/disabled；`visibility_json` 控制可见范围 |
| `asst_session` | 会话 | `source`: platform/embed/openapi；`channel_ref` 供业务侧回传关联 |
| `asst_message` | 消息 | SSE `done` 帧回写 `citations_json`/`tokens`/`elapsed_ms`/`degraded`；用户级用量从本表聚合 |
| `asst_tool_grant` | 工具授权 | `(tenant_id, assistant_id, tool_code)` 唯一；`tool_code` 对齐 whale-ai `/v1/tools` 注册表 |
| `asst_usage` | 用量日聚合 | `(tenant_id, assistant_id, stat_date)` 唯一；计费预留 |

## 四、核心接口设计（M1）

| 端点 | 方法 | 说明 |
|---|---|---|
| `/assistant/definitions` | POST/GET | 创建/分页查询助手（`sys:assistant:define`） |
| `/assistant/definitions/{id}` | GET/PUT/DELETE | 详情/更新（发布/停用走 status）/删除 |
| `/assistant/definitions/{id}/tools` | PUT | 批量设置工具授权（白名单） |
| `/assistant/sessions` | POST/GET | 创建/查询会话（校验助手已发布且当前用户可见） |
| `/assistant/sessions/{id}/stream` | POST | **SSE 问答**：落 user 消息 → 内网调 whale-ai → 透传 SSE → done 帧回写 assistant 消息 |
| `/assistant/sessions/{id}/messages` | GET | 历史消息分页 |
| `/assistant/openapi/v1/chat` | POST | 开放 API：业务应用凭 JWT + assistant code 换会话内嵌（M2） |
| `/assistant/usage` | GET | 用量看板（按天/助手聚合） |

## 五、whale-ai 多助手配置化装配（M1 前置，本次实施）

**原则：同一 Graph 拓扑，参数注入；不引入动态拓扑。**

### 5.1 请求契约扩展（向后兼容）

`POST /v1/agent/stream` 的 `ChatRequest` 增加可选 `assistant` 对象：

```json
{
  "conversationId": 123,
  "question": "...",
  "scope": null,
  "assistant": {
    "assistantId": "platform-helper",
    "systemPrompt": "你是 MES 助手……",
    "modelParams": { "model": "gpt-4o-mini", "temperature": 0.3, "maxTokens": 2048 },
    "toolWhitelist": ["knowledge_search", "fact_get"]
  }
}
```

`assistant` 为空 → 完全等同现状（默认平台助手）。

### 5.2 代码改造点

| 文件 | 改动 |
|---|---|
| `api/schemas.py` | 新增 `AssistantConfig` / `ModelParams` 模型；`ChatRequest.assistant` |
| `graph/state.py` | `AgentState` 增 `assistant: dict[str, Any] | None` |
| `graph/builder.py` | `run_agent()` 增 `assistant` 参数，注入初始状态 |
| `api/routes.py` | 透传 `request.assistant`；`meta` 事件带 `assistantId` |
| `llm/prompts.py` | `build_messages()` 增 `system_prompt` 覆盖参数（缺省仍用 `SYSTEM_PROMPT`） |
| `llm/client.py` | `get_llm_client(model_params)`：按助手 `modelParams` 覆盖 model/temperature/max_tokens（base_url/api_key 仍取全局配置，**助手不得自带密钥**） |
| `graph/nodes.py` | `answer_node` 用助手 system prompt 与客户端；`retrieve_node` 按 `toolWhitelist` 过滤工具（白名单为空 = 全部放行） |

### 5.3 安全口径

- 助手 `model_params` 只允许 model/temperature/max_tokens 三个字段，`extra="forbid"`；
- `system_prompt` 长度上限 8000 字符（`whale-assistant` 落库前校验）；
- 工具白名单只能收敛、不能放大：请求里的工具必须存在于 whale-ai 注册表，越权工具直接忽略并记日志；
- whale-assistant 调 whale-ai 仍走内网直连（`WHALE_AI_BASE_URL`），不 经网关。

## 六、里程碑与验收

| 里程碑 | 内容 | 验收 |
|---|---|---|
| M0 ✅ | 模块骨架 + 网关路由 + 登记 + V1 DDL | compile/test 通过；service-catalog 四处登记完成 |
| M1 ✅ | whale-ai 配置化装配 + 助手定义/会话/消息 CRUD + `/sessions/{id}/stream` SSE | 单测 21 项全绿（SSE 端到端经内嵌 HttpServer 验证助手配置下发与落库）；前端子应用 8117 构建通过 |
| M2 ✅ | 工具授权体系（工具注册表校验）+ 开放 API（业务内嵌） | 后端 29 项单测全绿；子应用内嵌演示入口随 M3 |
| M3 ✅ | admin 管理页 + 用量看板 + 基座菜单/权限码注册（sys:assistant:define / sys:assistant:chat）+ 内嵌演示入口 | 端到端演示（实机待环境联调） |
| M4 | IoT/Twin 只读工具接入，三平台联动 | 另立 wave 计划 |

### M1 落地清单（2026-11-09）

- 后端：`AsstDefinitionService`（状态机 draft→published⇄disabled、模型参数键白名单 model/temperature/max_tokens、工具授权覆盖式 upsert）、`AsstSessionService`（归属隔离、消息条数/字节上限、来源白名单 platform/embed/openapi）、`AssistantChatService`（SSE 透传 + done 帧回写落库 + 用量聚合）、`AssistantStreamClient`（内网直连 whale-ai）、`AsstUsageService`（助手×天 upsert 聚合）、控制器 2 个（`/assistant/definitions`、`/assistant/sessions`，权限码 `sys:assistant:define` / `sys:assistant:chat`，审计注解齐备）。
- 前端：`whale-frontend/platform-apps/whale-assistant`（8117，qiankun，base `/assistant`）——会话工作台（选助手→建会话→流式问答→引用标签渲染），SSE POST 手工分帧（`postStream`）。
- 登记：service-catalog §七 前端区段补 8117。
- 待办：`sys:assistant:*` 权限码与菜单需在 whale-auth/admin 注册（M3）。

### M2 落地清单（2026-11-09）

- **工具注册表校验（fail-closed）**：新增 `ToolRegistryClient`（内网 GET whale-ai `GET /v1/tools` 解析工具编码全集）；`AsstDefinitionService.setTools` 落库前校验——未知工具编码直接拒绝（列明非法项）、注册表不可用也拒绝（whale-ai 未就绪时不允许盲授权）；空白名单跳过校验。
- **开放 API**：`POST /assistant/openapi/v1/chat`（`AssistantOpenApiController`）。鉴权按 §七口径：`X-Internal-Token`（`/assistant/openapi/**` 已纳入服务侧令牌保护，`application.yml` 扩展 `bluewhale.internal-token.path-patterns`）+ `X-User-Id` 必传透传 + `X-Permission-Codes` 可选透传。**不经网关**（业务后端经 Nacos 直连，网关本就剥离内网令牌）。
- 请求：`assistantCode`（须已发布）+ `question`（≤2000 字）+ 可选 `sessionId` 续聊（归属校验 + 助手匹配校验）+ 可选 `channelRef`；无 sessionId 时自动新建 `source=openapi` 会话。
- 同步聚合：`AssistantChatService` 重构出共享执行核心 `execute()`（SSE 与同步两条路复用）——用户消息落库、whale-ai 流式调用、事件累积（`accumulate`）与 SSE 转发（`relay`）解耦、done 帧回写 assistant 消息 + 引用 JSON + 用量聚合；开放 API 聚合为 `OpenChatVO`（sessionId/messageId/answer/citations/tokens/degraded/elapsedMillis），错误统一 BizException 透传。
- `AsstSessionService.createSession` 拆分（返回实体），`requirePublishedByCode` 新增。
- 测试：29/29 全绿（新增：注册表未知工具拒绝、注册表不可用 fail-closed、requirePublishedByCode、chatSync 端到端聚合/续聊/会话不匹配/上游 503）。
- 待办（M3）：admin 管理页、用量看板、基座菜单/权限码注册、任一子应用内嵌演示入口。

### M3 落地清单（2026-11-09）

- **端口修正**：M1 曾把前端子应用误登记 8117——该端口权威归属 `whale-mail`（Wave 5.89）。`whale-assistant` 前端自 M3 起改 **8120**（vite.config + service-catalog §七三处），基座 `apps.ts` DEV_PORTS 同步。
- **权限码与菜单（SQL）**：`whale-service-auth-starter/db/migration-6.01-assistant.sql`（幂等）——`sys_permission` 2330 `sys:assistant:define` / 2331 `sys:assistant:chat`（2026-11-11 订正：原 2300/2301 与 CRM 分析权限撞号，两条 INSERT 被幂等条件整条跳过，助手权限从未入库）；`sys_role_permission` 按 `perm_code` 联查授权 tenant_admin（MD5 派生幂等 id）；`sys_menu` 按 `(tenant_id, path)` 判重、主键 MD5 派生，注册「助手管理」`/assistant/definition`（component `assistant/definition/index`）与「助手用量」`/assistant/usage`，均绑 `sys:assistant:define`；存量库由 Flyway `V8__assistant_permission_menu.sql` 补齐并复制到业务租户。
- **后端新增**：`GET /assistant/usage/page`（助手×天聚合分页，date 过滤校验、助手被删回退 ID 展示）、`GET /assistant/tools`（whale-ai 注册表透传，授权页可选工具全集）、`GET /assistant/definitions/published`（问答视角已发布清单，`sys:assistant:chat` 即可）。
- **admin 管理页**（`whale-frontend/admin`，构建通过）：`api/assistant.ts` 全套封装；`pages/assistant/DefinitionList.tsx`（分页/新增/编辑/发布/停用/删除 + 工具授权抽屉——勾选注册表工具，服务端强校验）；`pages/assistant/UsageBoard.tsx`（日期范围/助手过滤看板）；`dynamicRoutes.tsx` 注册 `assistant/definition`、`assistant/usage`；vite proxy 白名单补 `assistant`。
- **基座注册**：`whale-platform` `apps.ts` 注册 `whale-assistant`（8120，activeRule `/assistant`）；`menu.ts` 加「AI 助手」入口（permission `sys:assistant:chat`）。
- **内嵌演示入口**：`whale-portal` 新增「AI 助手」页（`pages/Assistant.tsx` + `api/assistant.ts`）——选已发布助手 → embed 来源建会话 → SSE 流式问答（delta 增量渲染），构建通过。
- 待办（M4 / 实机联调）：三库迁移与 SQL 执行、EMQX/IoT 对接另立 wave。

## 七、风险与开放问题

1. **会话记忆分裂**：whale-ai 的 Redis 记忆 key 用 `conversation_id`；whale-assistant 的 session id 与 whale-agent 的 conversation id 空间不同，接入时统一用 `asst_session.id` 作 `conversation_id` 传入，天然不撞（雪花 ID）。
2. **长会话成本**：`asst_message` 无限增长——落库保留全量，上下文窗口仍由 whale-ai 记忆压缩控制；`session-max-messages` 只约束单会话消息条数上限。
3. **开放 API 鉴权**（M2 设计项）：业务应用服务对服务调用建议复用 `X-Internal-Token` + 最终用户 `X-User-Id` 透传，避免引入第二套 token。
