# Wave 5.52 规划：WebIM + LangChain 智能体

> 制定日期：2026-10-24 ｜ 状态：**实现中**（M0 设计冻结 + M1 T2–T6、M2 T7 已完成，见 §八）
> 关联：`whale-agent`（8090，Wave 5.47）、`whale-gateway`（8080）、`whale-file`（8085）、`whale-auth`（8081）

## 一、目标

1. **WebIM**：自研轻量即时通讯，支持 1:1、群聊、消息历史、未读/已读、在线状态、文件与图片，并内置「蓝鲸助手」机器人。
2. **LangChain 智能体**：能回答平台架构、技术实现、数据口径问题，并在授权下执行平台操作（建表单、发起流程、查配置等）。
3. **IM 作为智能体入口**：单聊机器人 + 群内 `@蓝鲸助手`，流式回答、引用可回溯、工具轨迹可折叠查看。

## 二、已确认决策

| 决策项 | 结论 | 理由 |
|---|---|---|
| LangChain 运行形态 | **Python 独立服务**（FastAPI + LangChain/LangGraph） | 生态与迭代效率；与 Java 业务通过 HTTP 解耦 |
| WebIM 范围 | **自研轻量 IM** | 复用现有 JWT/RBAC/审计与 `whale-file`，不引入 OpenIM 的独立账号体系 |
| 操作权限 | **分级 + 人工确认** | 只读自动执行；写操作必须确认卡片 + RBAC + 审计 |
| 一期交付 | IM 核心先行，机器人接入随后，LangChain 与服务端解耦上线 | 先打通“人能聊天”，再打通“机器人能答” |

## 三、总体架构

```text
浏览器
├─ whale-platform 基座 (8000) ──qiankun──> whale-im 子应用 (8107, base /im)
├─ whale-agent 工作台 (8004, 独立 SPA，保留)
└─ whale-admin (8001) / whale-ops (8104)
        │ HTTPS + WSS
┌───────▼─────────────────────────────────────────────────────────┐
│ whale-gateway 8080：JWT 鉴权 / 黑名单 / 限流 / TraceId            │
│  ├─ /im/**      → lb://whale-im      (REST，复用现有路由机制)      │
│  ├─ /im/ws      → lb:ws://whale-im   (WebSocket 升级)            │
│  ├─ /agent/**   → lb://whale-agent   (现有 SSE 问答)             │
│  └─ 既有服务路由                                                   │
└───────┬─────────────────────────────────────────────────────────┘
        │
┌───────▼──────────────┐       内部 HTTP        ┌──────────────────┐
│ whale-im 8091        │ ────────────────────> │ whale-agent 8090 │
│ bluewhale_im (5 表)  │  机器人消息 / SSE 转发  │ RAG + 事实 + 工具 │
│ Redis：在线/未读/ticket│                        └────────┬─────────┘
│ 附件 → whale-file    │                                 │ 内部 HTTP
└──────────────────────┘                        ┌────────▼─────────┐
                                                │ whale-ai 8092    │
                                                │ Python LangChain │
                                                │ 不持有 DB 凭据    │
                                                └────────┬─────────┘
                                                         │ 工具回调（HTTP）
                                                ┌────────▼─────────┐
                                                │ 平台工具 API（Java）│
                                                │ RBAC + 确认 + 审计 │
                                                └──────────────────┘
```

**核心原则**：Python 只做编排与推理，**不直连业务库、不持有任何业务凭据**；所有检索、数据查询、平台操作都回到 Java 侧执行，权限与审计只有一套。

## 四、服务与端口（规划）

| 服务 | 端口 | 对外前缀 | 库 | 说明 |
|---|---|---|---|---|
| `whale-im`（Java / Spring Boot） | **8091** | `/im/**`、`/im/ws` | `bluewhale_im` | IM 核心：会话、消息、已读、在线、机器人 |
| `whale-ai`（Python / FastAPI） | **8092** | 不注册网关（仅内网调用） | 无（Redis 记忆） | LangChain/LangGraph 编排、工具循环、确认态 |
| `whale-im` 前端 | **8107** | base `/im` | —— | qiankun 子应用，复用 `@bluewhale/whale-ui` |

- 路由新增 2 条：`Path=/im/**`（REST）与 `Path=/im/ws`（`lb:ws://whale-im`，需保留 `Upgrade`/`Connection` 头）。
- 端口落在既有分段：8091/8092 属「8081–8099 核心服务」，8107 属「8101–8199 子应用」。
- 命名遵循 `REQ-ARCH-004`：库 `bluewhale_im`、Redis key `whale:im:*`、Java 包 `com.bluewhale.im`。

## 五、WebIM 设计

### 5.1 数据模型（`bluewhale_im`，5 表）

| 表 | 关键字段 | 说明 |
|---|---|---|
| `im_conversation` | `id, type(SINGLE/GROUP/BOT), title, owner_id, last_seq, last_message_id, last_active_at, deleted` | 会话主体；BOT 会话为“用户 × 助手” |
| `im_conversation_member` | `conversation_id, user_id, member_type(USER/BOT), role(OWNER/ADMIN/MEMBER), last_read_seq, mute_until, join_time` | 成员与已读游标合一，避免额外表 |
| `im_message` | `id, conversation_id, seq, sender_id, sender_type(USER/BOT/SYSTEM), content_type(TEXT/MARKDOWN/IMAGE/FILE/CARD), content, client_msg_id, reply_to, status, created_at, deleted` | `(conversation_id, client_msg_id)` 唯一索引做幂等 |
| `im_agent_binding` | `conversation_id, bot_user_id, agent_scenario(DEFAULT/ARCH/DATA/OPS), agent_conversation_id, enabled` | 会话与智能体场景绑定 |
| `im_agent_run` | `id, conversation_id, request_message_id, response_message_id, status(PENDING/RUNNING/WAITING_CONFIRM/SUCCESS/FAILED), tool_trace_json, confirmation_json, started_at, finished_at` | 一次提问的运行轨迹与确认态 |

### 5.2 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/im/conversations` | 我的会话列表（含未读数、最后一条消息摘要） |
| POST | `/im/conversations` | 建 1:1 / 群聊（成员数 ≤ 200） |
| GET | `/im/conversations/{id}/messages?beforeSeq=&limit=` | 历史消息，倒序游标 |
| POST | `/im/conversations/{id}/messages` | 发送消息（`clientMsgId` 幂等） |
| POST | `/im/conversations/{id}/read` | 上报已读 `seq` |
| POST | `/im/ws-ticket` | 换取 60s 一次性 WS ticket |
| POST | `/im/agent-runs/{id}/confirm` | 确认执行写操作（一次性、5 分钟有效） |
| WS | `/im/ws?ticket=` | 消息、在线、机器人流式帧 |

### 5.3 消息可靠性

- **序号单调**：`im_message.seq` 由 Redis `INCR whale:im:seq:<conversationId>` 分配，落库失败按 `max(seq)` 回填。
- **客户端幂等**：`clientMsgId` + 唯一索引，重发返回已存在消息。
- **断线补拉**：客户端记录 `lastSeq`，重连后先 `GET messages?afterSeq=` 补差量，再恢复 WS 推送。
- **在线状态**：`whale:im:presence:<userId>` TTL 90s，心跳续期；离线不阻塞消息入库。
- **多实例预留**：一期单实例；二期以 Redis Pub/Sub 广播 `message.new`，DB 仍是唯一事实源。

### 5.4 WebSocket 鉴权（安全红线）

浏览器 WebSocket 无法自定义 `Authorization` 头，**禁止把 JWT 放 URL**（会落网关/代理日志）。采用：

1. 已登录用户 `POST /im/ws-ticket`（走标准 JWT 鉴权）→ 服务端写 `whale:im:ticket:<uuid>`（60s，一次性，绑定 userId）。
2. 客户端 `wss://host/im/ws?ticket=<uuid>`；网关校验 ticket 存在且未使用，消费即删。
3. 连接建立后第一帧返回 `ready`（userId、服务器时间、心跳间隔）。

### 5.5 机器人接入

- 用户与「蓝鲸助手」的 `BOT` 会话；群聊中 `@蓝鲸助手` 触发（同一条消息只触发一次）。
- `whale-im` 收到请求 → 调用 `whale-agent POST /agent/chat`（透传 `X-User-Id` / `X-Permission-Codes`）→ 将 SSE 的 `meta/delta/done/error` 转成 WS 帧 `agent.meta/agent.delta/agent.done/agent.error`。
- 回答落库为 `sender_type=BOT` 的 assistant 消息；`im_agent_run.tool_trace_json` 保存引用与工具轨迹。
- 失败/超时（默认 120s）落系统提示，不阻塞会话。

## 六、LangChain 智能体设计

### 6.1 服务形态

- 目录：`ai/whale-ai`（不进入 Maven reactor），Python 3.11 + `uv` + FastAPI + LangChain + LangGraph + pydantic。
- 接口：`POST /v1/agent/stream`（SSE）、`POST /v1/agent/confirm`、`GET /v1/tools`、`GET /health`。
- 只在内网 8092 监听，不注册 Nacos、不暴露网关；由 `whale-agent` 通过 `WHALE_AI_BASE_URL` 调用。
- 开关：`whale.agent.qa.engine=legacy|langchain`，默认 `legacy`，验证通过后切换；IM 机器人优先走 `langchain`。

### 6.2 编排图（LangGraph）

```text
intent ──> retrieve(knowledge/facts) ──> think ──> tools ──> verify_citations ──> answer
                     ▲                       │
                     └────── need_more ──────┘
```

- 最大工具步数 6；每步超时 20s；总预算 120s。
- 记忆：最近 10 轮 + 会话摘要，Redis `whale:ai:memory:<conversationId>`，TTL 7 天。
- 模型：OpenAI 兼容协议（本机 Ollama / 云端），参数沿用 `WHALE_AGENT_LLM_*`，不在 Python 侧另立密钥。
- 引用：沿用现有 `CitationValidator` 规则，答案正文标记与 `done` 引用集合必须一致。

### 6.3 工具清单（全部由 Java 侧执行）

| 工具 | 风险级 | 权限码 | 说明 |
|---|---|---|---|
| `knowledge_search` | L0 只读 | `sys:agent:chat` | 调 `whale-agent` Lucene 混合检索 |
| `fact_get` | L0 只读 | `sys:agent:chat` | 服务/端口/DDL/依赖/运行时事实 |
| `doc_read` | L0 只读 | `sys:agent:chat` | 按仓库路径读取文档片段 |
| `data_query` | L1 只读数据 | `sys:ai:data:read` | 白名单视图；仅 SELECT；自动 `LIMIT 200`；5s 超时；敏感字段脱敏 |
| `platform_op` | L2 写操作 | `sys:ai:ops:<domain>` | 建表单/发流程/改配置等，**必须确认** |

### 6.4 写操作确认协议（分级）

1. 模型返回 `tool_call(platform_op, args)` → 智能体**不执行**，生成 `WAITING_CONFIRM` 与 `confirmation_id`。
2. `whale-agent` 生成确认卡片：操作名、参数摘要、影响范围、所需权限码、有效期 5 分钟。
3. `whale-im` 渲染卡片；用户点「确认执行」→ `POST /im/agent-runs/{id}/confirm`。
4. `whale-agent` 校验：同一用户、权限码、未过期、未使用 → 调用目标业务服务 → 回写结果与审计。
5. 拒绝/超时 → 卡片置灰，明确提示“未执行”，不重复下发。

审计字段：发起人、确认人、工具名、参数哈希（不落原值中的敏感项）、目标服务、结果码、耗时。

### 6.5 数据问答安全

- 独立只读账号 `whale_ai_ro`，仅授权白名单视图，禁止访问 `mysql`/`information_schema` 之外系统库。
- SQL 解析（`sqlglot` 或 JSqlParser）仅允许 `SELECT`/`UNION`；禁 DDL/DML/多语句/写 CTE。
- 结果集 ≤ 200 行；手机号、身份证、密钥、令牌按规则脱敏。
- 响应附带实际执行 SQL（供用户与审计核对），但不得回显连接串与账号。

## 七、前端设计

- 新增 `frontend/platform-apps/whale-im`（8107，base `/im`），qiankun 子应用；`vite.config.ts` 走既有 `Accept: text/html` 旁路，避免 `/im` API 与路由冲突。
- 布局：左侧会话列表（未读红点、搜索）、中间消息流（文本/Markdown/图片/文件/确认卡片）、右侧成员/机器人信息。
- 机器人消息：流式打字、引用角标（点击跳到文档行）、工具轨迹折叠、👍/👎 反馈、确认按钮。
- 基座导航新增「消息」入口与全局未读角标；权限码 `sys:im:*` 接入 `PermissionProvider`。
- 日志统一使用 `createLogger('whale-im › ...')`，不直接使用 `console`。

## 八、阶段计划与任务

### M0 设计冻结

- [x] T0 评审本文档；在 `docs/requirements/register.md` 登记 REQ-FUNC-011/012/013、REQ-SEC-007（2026-10-24 完成，见登记册第四节）
- [x] T1 在 `docs/service-catalog.md` 登记 8091/8092/8107、`/im/**` 与 WS 路由、`bluewhale_im`（2026-10-24 完成）

### M1 IM 核心

- [x] T2 `whale-im` 模块骨架（端口/库/配置/启动类/Nacos）（2026-10-24 完成，构建 + Checkstyle 通过）
- [x] T3 5 张表 DDL + 幂等迁移脚本（2026-10-24 完成，`db/init.sql` `CREATE TABLE IF NOT EXISTS`；建库执行留待联调）
- [x] T4 会话与消息 REST（含 `clientMsgId` 幂等、游标分页）（2026-10-24 完成，27 用例全绿 + Checkstyle 0，见 devlog §七）
- [x] T5 WebSocket 通道 + ticket 鉴权 + 心跳 + 在线状态（2026-10-24 完成，51 用例全绿 + Checkstyle 0，见 devlog §八）
- [x] T6 已读/未读、群成员管理、附件走 `whale-file`（2026-10-24 完成，79 用例全绿 + Checkstyle 0，见 devlog §九）

### M2 机器人接入

- [x] T7 机器人账号与 BOT 会话初始化（2026-10-24 完成，85 用例全绿 + Checkstyle 0，见 devlog §十）
- [ ] T8 SSE → WS 帧转换、消息落库、超时降级
- [ ] T9 群内 @ 触发与去重

### M3 LangChain 服务

- [ ] T10 `ai/whale-ai` 骨架（FastAPI/LangGraph/配置/健康检查）
- [ ] T11 `knowledge_search`、`fact_get`、`doc_read` 三个只读工具打通
- [ ] T12 引用校验与流式输出，接入 `whale-agent` 引擎开关
- [ ] T13 记忆（Redis）与会话摘要

### M4 平台操作

- [ ] T14 `data_query` 只读数据通道（白名单视图 + SQL 守卫 + 脱敏）
- [ ] T15 `platform_op` 工具 + 确认卡片 + 一次性确认令牌
- [ ] T16 审计落库与轨迹展示

### M5 加固与验收

- [ ] T17 限流、敏感内容遮蔽、异常降级
- [ ] T18 前端 build/lint、后端单测、Python pytest、Checkstyle
- [ ] T19 实机联调与验收记录（devlog + review）

## 九、验收口径

| # | 验收项 | 口径 |
|---|---|---|
| 1 | 实时性 | 同会话双端收发 P95 < 500ms（本机网关链路） |
| 2 | 不丢消息 | 断开 30s 后重连，按 `lastSeq` 补拉后消息完整且顺序正确 |
| 3 | WS 安全 | WS URL 不含 JWT；ticket 一次性、60s 失效；无 ticket 拒绝升级 |
| 4 | 机器人 | 单聊/@ 机器人均流式输出，`done` 引用与实际文档一致 |
| 5 | 只读数据 | 非白名单表/写 SQL/多语句全部被拒；结果 ≤200 行且脱敏 |
| 6 | 写操作 | 100% 经确认卡片；未确认不下发；审计可按 `confirmation_id` 追溯 |
| 7 | 质量门禁 | 后端模块单测全绿 + Checkstyle 0；Python pytest 全绿；前端 build/lint EXIT=0 |
| 8 | 权限 | 无 `sys:im:*` 不可进 IM；无 `sys:ai:ops:*` 确认按钮不可用 |

## 十、风险与待办

| 风险 | 影响 | 处置 |
|---|---|---|
| Python 运行时新增运维成本 | 部署链路变复杂 | 容器化并纳入 compose；健康检查 + 就绪探针 |
| WS 多实例广播 | 单实例可跑，扩容后消息不同步 | 一期单实例；预留 Redis Pub/Sub，DB 为事实源 |
| 本地小模型工具调用不稳 | 工具参数错误、确认卡片误触发 | 生产用 7B+ 或云端；参数 schema 强校验 + 确认兜底 |
| 平台写操作语义复杂 | 误操作影响业务数据 | 逐操作定义权限码、参数校验与回滚策略；先开放低风险操作 |
| `/im` 同时是前端路由与 API 前缀 | 刷新 404 | 复用基座已有 `Accept: text/html` 旁路；Nginx 同步规则 |

## 十一、需求项 / 完成项 / 待做项 / 已修正问题项

### 11.1 需求项

| 编号（待登记） | 需求 | 优先级 |
|---|---|---|
| REQ-FUNC-011 | 自研 WebIM：1:1/群聊、历史、未读/已读、在线、附件、机器人会话 | 高 |
| REQ-FUNC-012 | LangChain 智能体：架构/技术/数据问答，流式输出、引用可回溯 | 高 |
| REQ-FUNC-013 | 平台操作能力：分级执行 + 人工确认 + 审计 | 高 |
| REQ-SEC-007 | 智能体工具权限与确认令牌：L0/L1/L2 分级、一次性确认、全链路审计 | 高 |

### 11.2 完成项

- 本文档：完成现状调研（现有 `whale-agent` 能力、端口/路由/库规范）、三项关键决策确认、总体架构与 5 个里程碑任务拆解。

### 11.3 待做项

- M0–M5 全部编码与验收任务（见第八节）。
- 需求登记册、服务清单、架构文档在 M0 同步更新。

### 11.4 已修正问题项

- 澄清「LangChain 智能体」不是替换现有 Java `whale-agent`，而是新增 Python 编排层，现有检索/事实/工具继续复用。
- 澄清 WebSocket 不能沿用「JWT 放 query」的常见做法，改为一次性 ticket，避免令牌落日志。
- 明确 `/im` 前缀同时承载前端路由与后端 API 的冲突及旁路方案。
