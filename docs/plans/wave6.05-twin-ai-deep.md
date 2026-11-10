# Wave 6.05：孪生 × AI 深化（流式解读 / 可视化拾取 / internal_api 源）

> 状态：**M0 + M1 已落地（2026-11-10）** · 起草：2026-11-10
> 上游：Wave 6.04 M0+M1 已交付（三平台工具链 + 孪生 AI 解读同步版）

## 一、里程碑

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M0 | 流式 AI 解读：assistant 开放 SSE 端点 + twin 流式代理 + 前端逐帧渲染 | ✅ 2026-11-10 |
| M1 | 标注可视化拾取：3D 场景点击拾取坐标自动填 `positionJson` | ✅ 2026-11-10 |
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

## 三、M1 落地清单（2026-11-10）：标注可视化拾取

纯前端改动（`whale-frontend/platform-apps/whale-twin/src/pages/ViewerPage.tsx`）：

- **实体网格打标**：装配实体时给 holder 打 `userData.__entityId`，供射线命中后回溯归属；
- **拾取模式**：标注抽屉坐标项下新增「拾取坐标（点击模型表面）」，开启后画布十字光标
  （`crosshair`）+ 顶部提示；点击时 `Raycaster.intersectObjects(entityRoot, true)` 取
  首个命中点世界坐标，回填 `{"x","y","z"}`（3 位小数）并**自动带出挂靠实体**（命中网格
  向上回溯 `__entityId`），随后自动退出拾取；
- **防误触**：`pointerdown` 记录起点，位移 > 4px 视为拖拽（旋转相机）不触发拾取/热点跳转；
- **退出**：Esc 键、再次点按钮、关闭标注抽屉、创建/编辑标注切换均复位拾取态；
- 未命中模型表面时提示（不静默失败）。

**验证**：tsc 0 错误、vite 构建通过。拾取坐标系为世界坐标（与既有 `positionJson` 语义一致）。

## 四、M2（待开始）：internal_api 绑定源

- `TwinBindingRuntimeService` 支持 `sourceType=internal_api`：按 `bindKey`（服务名+路径）
  经网关/内网 GET 拉取，`propertyMap` stateKey 指向响应 JSON 路径
- 上线前置：服务目录（service-catalog）约束可调用的白名单端点，全部只读
