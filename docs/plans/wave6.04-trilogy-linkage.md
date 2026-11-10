# Wave 6.04：三平台联动收口（IoT 数据 + 孪生场景 + AI 解读）

> 状态：**M0 + M1 已落地（2026-11-10）** · 起草：2026-11-10
> 总规划：[wave6-iot-twin-ai-assistant-plan.md](wave6-iot-twin-ai-assistant-plan.md) §六「收口」行
> 定位：Wave 6.01–6.03 三平台 M0–M3 交付后的联动收口——让 AI 助手能"看见"设备与孪生。

## 一、联动链路（工具由 Java 执行的既有范式，规划 §6.3）

```
用户提问 → whale-assistant(8087) → whale-ai(LangGraph, 工具契约注册表)
         → whale-agent(8086) /agent/tools/{endpoint}（工具执行端，内网令牌）
         → whale-iot(8088) /iot/internal/**   （实时态/遥测历史）
         → whale-twin(8089) /twin/internal/** （场景/实体/绑定/标注概要）
```

## 二、M0 落地清单（2026-11-10）

| 层 | 变更 |
|---|---|
| whale-iot | `IotInternalDeviceController` 新增 `GET /iot/internal/device/telemetry/history`（deviceId 或三元组定位；identifier/from/to/limit 过滤，复用 `IotTelemetryService.query` 钳制） |
| whale-twin | 新 `TwinInternalSceneController`：`GET /twin/internal/scene/overview`（sceneId/sceneCode 二选一）返回 scene + 实体树 + 绑定 + 标注概要；`TwinSceneService.resolveSceneId/findByCode` 承接定位（ArchUnit controller-不碰-entity 门禁保持） |
| whale-agent | `AgentProperties.platform`（iot-base-url/twin-base-url/internal-token/timeout）；`PlatformToolClient`（java.net.http 内网 GET + Result 解包 + `PlatformToolException`）；`AgentPlatformToolController` 三个工具端点：`POST /agent/tools/iot-device-state`、`/iot-device-history`、`/twin-scene-overview` |
| whale-ai | `tools/registry.py` 注册 3 个工具契约：`iot_device_state` / `iot_device_history` / `twin_scene_overview`（全部 **L1**、permission `sys:ai:data:read`，与 data_query 同族，不新增权限码） |
| 前端/入口 | 本轮不动（工具经助手问答自动可用）；孪生查看器「AI 解读」按钮留 M1 |

**验证**：whale-iot / whale-twin / whale-agent `mvn test` BUILD SUCCESS（Checkstyle 0、ArchUnit 全绿）；whale-ai pytest 51/51。

**环境坑（已修）**：本机 WorkBuddy 注入 `PYTHONPATH/PYTHONHOME` 导致 venv 包被全局旧版遮蔽（`langchain_protocol` PEP 728 报错）——跑 whale-ai 测试须 `env -u PYTHONPATH -u PYTHONHOME`；venv `typing_extensions` 升 4.16。

## 三、Demo 验收路径（端到端）

1. 环境：whale-iot/whale-twin/whale-agent/whale-ai/whale-assistant 全部就绪（`whale.agent.platform.internal-token` 与平台内网令牌一致）；
2. IoT 侧设备上报温度（Wave 6.02 三元组 + EMQX）；
3. admin「助手管理」给目标助手**授权工具**：勾选 `iot_device_state` / `iot_device_history` / `twin_scene_overview`（注册表校验 fail-closed，Wave 6.01 M2 机制）；
4. 助手问答：「1号锅炉现在温度多少？」「厂区场景里有哪些设备、状态如何？」「看下锅炉最近 1 小时温度趋势」——AI 应分别调用三个工具并综合解读；
5. 预期：引用/工具调用在会话流中可见，用量看板（`asst_usage`）计数增长。

## 四、M1（✅ 已落地 2026-11-10，提交 5751e441）：孪生查看器「AI 解读」入口

- whale-twin 查看器选中实体 →「AI 解读」按钮 → 组装上下文（实体 + 最新绑定实时态 + 场景概要）调 `POST /assistant/openapi/v1/chat`（Wave 6.01 M2 开放 API，`X-Internal-Token` + `X-User-Id`）→ 弹窗流式渲染解读；
- 与孪生 SSE 并存：解读不中断实时订阅。

## 五、边界

- 工具全部只读（L1）；写操作仍走 platform_op 确认卡（T15）范式，本轮不扩。
- whale-agent 平台客户端为单实例内存调用，失败即降级（BizException → 编排层告知用户），不缓存不重试。
- 历史遥测量级由 whale-iot `telemetry-query-max` 钳制（默认 100），超量走 data_query 只读视图。

## 六、M1 落地清单（2026-11-10）：孪生查看器「AI 解读」

**whale-twin 后端**：

- `TwinProperties.Assistant`（`whale.twin.assistant.*`）：`base-url`（默认 `http://127.0.0.1:8087`）、
  `assistant-code`（**为空即未启用**，服务端抛 BizException，前端入口按钮仍可点但弹窗提示失败）、`timeout-seconds`（60）
- `support/AssistantChatClient`：内网直连 `POST /assistant/openapi/v1/chat`（Wave 6.01 M2 开放 API），
  `InternalTokenSupport.restClient()` 附带 `X-Internal-Token`，`X-User-Id`/`X-Tenant-Id` 透传最终用户；
  与 `IotStateClient` 的旁路降级相反，解读是用户主动触发的主链路——失败抛 BizException 让弹窗可见
- `TwinInsightService`：上下文 = 场景元数据 + 目标实体（entityId 可空 = 整场景）+ 绑定实时态
  （复用 `TwinBindingRuntimeService.currentStates`，按实体过滤；上限 20 条防问题超长）；
  问题文本服务端组装（上下文为正文 + 用户追问为诉求），`channelRef = twin:{sceneId}:{entityId|-}`
- `TwinInsightController`：`POST /twin/scenes/{sceneId}/ai/insight`（`sys:twin:scene`，不新增权限码）；
  内网令牌全程留在服务端
- DTO：`InsightRequest`（entityId/question 均可空）、`InsightVO`（answer/degraded/tokens/elapsedMillis/
  sessionId/citations，citations 以 `List<Map>` 透传，不引 assistant 模块依赖）

**whale-twin 前端（8122）**：

- 查看器工具栏新增「AI 解读」按钮（选中实体 → 解读该实体；未选中 → 整场景概要）；
- 解读弹窗：追问输入（可空，回车/按钮触发）、结果区（pre-wrap 正文 + 降级 tag + tokens/耗时/引用数 tag），
  loading 期间禁止关闭；与孪生 SSE 并存（解读不中断实时订阅）
- 构建：tsc 0 错误、vite 构建通过（three 拆包维持 585KB/1.17MB）

**验证**：whale-twin `mvn test` 50/50（新增 `TwinInsightServiceTest` 5 例：上下文组装/整场景/实体归属校验/
缺失实体/降级透传），Checkstyle 0、ArchUnit 4/4。

**演示路径**：IoT 设备在线上报 → 孪生场景绑定阈值 → 查看器选中「1号锅炉」→ AI 解读 →
回答含实时温度/超阈值风险与处置建议（assistant-code 须为已发布助手，且助手已授权
`iot_device_state`/`twin_scene_overview` 等工具时可进一步自主取数）。

**M1 边界**：同步聚合返回（开放 API 无流式端点，流式留 Wave 6.05 评估）；无会话续聊
（sessionId 仅透出）；解读问题长度受 question 500 字 + 上下文 20 条实时态钳制。
