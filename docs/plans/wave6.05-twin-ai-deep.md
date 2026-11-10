# Wave 6.05：孪生 × AI 深化（流式解读 / 可视化拾取 / internal_api 源）

> 状态：**M0 已落地（2026-11-10）** · 起草：2026-11-10
> 上游：Wave 6.04 M0+M1 已交付（三平台工具链 + 孪生 AI 解读同步版）

## 一、里程碑

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M0 | 流式 AI 解读：assistant 开放 SSE 端点 + twin 流式代理 + 前端逐帧渲染 | ✅ 2026-11-10 |
| M1 | 标注可视化拾取：3D 场景点击拾取坐标自动填 `positionJson` | 待开始 |
| M2 | `internal_api` 绑定源运行时（M2 留白项：twin 轮询平台内部只读 API） | 待开始 |

## 二、M0 落地清单（2026-11-10）

**whale-common（starter）**：

- `InternalTokenSupport` 新增静态 `token()` / `headerName()`——流式通道（java.net.http
  逐行 SSE）无法走 RestClient 拦截器，取值与拦截器同一配置源（Spring 绑定优先，
  回退 `WHALE_INTERNAL_TOKEN` 环境变量）

**whale-assistant**：

- `AssistantChatService.chatStream`：会话解析与 `chatSync` 完全一致（openapi 来源、
  sessionId 续聊校验），复用既有私有 `answer()` 路径（execute + delta 透传 + done/error
  收敛 + 落库）；`POST /assistant/openapi/v1/chat/stream`（produces text/event-stream，
  `X-Internal-Token` 守卫不变）
- 帧契约与内部会话流一致：`delta`/`done`（sessionId/messageId/tokens/degraded/elapsedMillis/citations）/`error`

**whale-twin**：

- `AssistantChatClient.chatStream`：内网直连 assistant SSE（手工分帧解析 event/data，
  与 whale-assistant 消费 whale-ai 的同款实现）；令牌以 `InternalTokenSupport.token()`
  显式附加
- `TwinInsightService.stream`：上下文组装（同步）→ 虚拟线程转发——delta/done 原样透传，
  异常统一转 `error` 帧；`GET /twin/scenes/{sceneId}/ai/insight/stream?entityId=&question=`
- 上下文/权限/降级语义与 M1 同步版完全一致

**前端（8122）**：

- 解读弹窗改流式：复用 `subscribeSceneStream`（fetch 手工分帧）逐帧追加正文
  （`▍` 光标），`done` 落 tokens/耗时/引用统计，可随时「停止」（AbortController）；
  同步端点 `insightApi.ask` 保留为降级通道（前端当前走流式）

**验证**：whale-assistant 29/29、whale-twin 50/50、starter 147/147 全绿；前端 tsc 0 错误、vite 构建通过。

**M0 边界**：assistant `chatStream` 复用 `answer()` 共享路径（同步版单测覆盖累积/落库逻辑），
未单独造 SseEmitter 集成测试；twin→assistant 每次解读一条新连接，不缓存。

## 三、M1（待开始）：标注可视化拾取

- 查看器标注抽屉加「拾取坐标」：开启拾取模式后 Raycaster 命中模型表面点 →
  `positionJson` 自动填充（含挂靠实体自动带出）→ Esc/再次点击退出
- 纯前端改动，坐标语义不变（世界坐标系）

## 四、M2（待开始）：internal_api 绑定源

- `TwinBindingRuntimeService` 支持 `sourceType=internal_api`：按 `bindKey`（服务名+路径）
  经网关/内网 GET 拉取，`propertyMap` stateKey 指向响应 JSON 路径
- 上线前置：服务目录（service-catalog）约束可调用的白名单端点，全部只读
