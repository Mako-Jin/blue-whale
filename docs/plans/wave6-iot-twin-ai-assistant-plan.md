# Wave 6 规划：IoT 设备联网平台 · 数字孪生平台 · AI 助手平台

> 状态：**草案（待舰长审阅）** · 起草：2026-11-09
> 依据：`docs/architecture.md`、`docs/service-catalog.md`（§七端口分段）、`docs/naming-conventions.md`、现状调研（2026-11-09）

---

## 〇、现状结论（规划依据）

| 领域 | 现状 | 结论 |
|---|---|---|
| IoT / MQTT | 无任何基础设施。仅 MES 有设备台账 + 采集 15 表（`whale-mes`，SSE 推送） | **从零规划**，中间件需新增 MQTT Broker |
| 数字孪生 | `whale-frontend/shared-packages/whale-common-three`（three ^0.169）已建但**零消费方、未做渲染验收**；`whale-scene`(8097) 2D 导航已落地、3D 增强未启动 | **挂点已预留**，前后端均在其上扩展 |
| AI 助手 | `whale-agent`(8090, 知识检索) + `whale-ai`(8092, Python/LangGraph 编排，仅内网) + `whale-im`(8091, 机器人会话) 三层已就位 | **不另起炉灶**，在此骨架上演进为多助手平台 |
| 接入范式 | 网关 `lb://` 不 StripPrefix、内网 `/**/internal/**` + `X-Internal-Token`、一服务一库 `bluewhale_<域>`、Flyway、ArchUnit、service-catalog 登记 | 三平台全部沿用，无例外 |

---

## 一、总体架构与相互关系

```
[设备侧] MQTT Broker (EMQX)
             │ 上行遥测 / 下行指令
             ▼
      whale-iot (8088) ──规则引擎/告警──▶ whale-alert (9103)
             │ internal REST（设备实时态）
             ▼
      whale-twin (8089) ◀── 场景/节点/绑定 ── whale-scene (8097, 2D 底座)
             │
             ▼
      whale-twin 前端 (8115) ←─ whale-common-three（首个消费方，完成渲染态验收）

      whale-assistant (8087, 助手管理+会话) ──内网──▶ whale-ai (8092, LLM 编排)
             │                                          │
             ▼                                          ▼
      whale-assistant 前端 (8116)              whale-agent (8090, 知识检索)
                                                ▲
      whale-iot / whale-twin 均可注册只读工具给 whale-ai 工具注册表
```

三个平台的协作主线：**IoT 供数据 → 孪生做呈现 → AI 做解读**（如"3D 厂区里点一台设备，AI 助手解读其遥测与告警"）。

---

## 二、平台一：IoT 设备联网平台（whale-iot）

### 2.1 定位与边界

- **职责**：设备接入与认证、物模型（属性/事件/服务）、遥测上下行、指令下发与回执、规则引擎（条件→动作）、设备影子。
- **不做**：不做业务语义（MES 设备台账仍归 MES）；不做数据持久化分析报表（遥测落库 + 简单聚合即可，深度分析后续再议）。
- **协议策略**：首期只做 **MQTT**（Broker 由平台托管），HTTP/Modbus/OPC-UA 等协议网关留到 M3 之后。

### 2.2 服务与中间件

| 项 | 值 | 说明 |
|---|---|---|
| 后端 | `whale-iot`，端口 **8088**（核心服务区段空位） | 模块位置 `whale-backend/whale-services/whale-iot`，DDD 四层，对齐 auth/archive 骨架 |
| 数据库 | `bluewhale_iot` | 一服务一库 |
| 网关路由 | `/iot/**` → `lb://whale-iot` | 不 StripPrefix |
| MQTT Broker | **EMQX**（compose `bw-emqx`） | 端口：MQTT **1883**、MQTT-over-WS **8085→改 1884**（8085 已被 file 占用，宿主机映射避让）、Dashboard **18083**；端口经 `.env` 可覆盖 |
| Broker 认证 | EMQX → `whale-iot` 内网 Webhook 认证 | 设备三元组（productId/deviceName/secret）由 whale-iot 签发，JWT 或 HMAC 直连 token |
| 前端 | `whale-iot` 子应用，端口 **8115** | 挂 qiankun 基座 + admin 菜单 |

### 2.3 数据模型（首期核心表，Flyway `V1__iot_*.sql`）

- `iot_product`（产品/物模型定义）、`iot_thing_model`（属性/事件/服务，JSON Schema）
- `iot_device`（设备实例、三元组、影子状态 JSON、在线态）
- `iot_telemetry`（遥测时序，按月分区；量级大时 M2 评估切 TimescaleDB/独立存储，先不引入）
- `iot_command`（指令下发/回执/幂等）
- `iot_rule`（规则：触发条件→动作：告警/webhook/调 internal 接口）

### 2.4 里程碑

| 里程碑 | 内容 | 验收 |
|---|---|---|
| M0 | 模块骨架 + EMQX 进 compose + service-catalog/architecture 登记 | 服务可启动、注册 Nacos、网关可路由 |
| M1 | 产品/物模型/设备管理 CRUD + 三元组签发 + EMQX 认证 webhook | 可建产品、注册设备、设备用 token 连上 1883 |
| M2 | 遥测上行落库 + 设备影子 + 实时推送（SSE，对齐 MES 先例）+ 指令下发/回执 | 模拟设备发遥测，前端实时看到；指令下发有回执 |
| M3 | 规则引擎（条件→告警动作，接 whale-alert）+ 前端子应用（产品/设备/遥测/规则四页） | 一条规则可触发一条告警 |
| M4（远期） | 协议网关（Modbus/HTTP）、边缘接入、遥测冷热分层 | 另立 wave 计划 |

---

## 三、平台二：数字孪生平台（whale-twin）

### 3.1 定位与边界

- **职责**：3D 场景管理（场景/图层/实体绑定）、孪生实体与数据源绑定（绑到 whale-iot 设备或任意 internal 接口）、实时状态驱动 3D 呈现、视角/标注/热点。
- **与 whale-scene 的关系**：whale-scene (8097) 是 **2D 导航底座**（已落地），whale-twin 是 **3D 孪生层**，可引用 scene 的场景树结构做目录组织，但不改 scene 的表；`WHALE_SCENE_3D_ENABLED` 开关保持不动，避免两条 3D 路线互相污染。
- **前端**：`whale-common-three` 迎来**首个消费方**，同步补齐渲染态验收（`docs/reviews/2026-10-26-p3-three-scene.md` 遗留项）。

### 3.2 服务划分

| 项 | 值 | 说明 |
|---|---|---|
| 后端 | `whale-twin`，端口 **8089**（核心服务区段空位） | `whale-backend/whale-services/whale-twin`，一服务一库 `bluewhale_twin` |
| 网关路由 | `/twin/**` → `lb://whale-twin` | |
| 实时推送 | SSE（`/twin/stream/**`） | 数据来源：whale-iot internal 订阅 + 自身轮询聚合；不引入 WebSocket 双栈，对齐 IM/scene 现状 |
| 模型资产 | GLB/GLTF 存 `whale-file`(8085) | 不自建对象存储；`whale-common-three` 的 GLTFLoader 直接吃 file 的预览 URL |
| 前端 | `whale-twin` 子应用，端口 **8116** | three.js 渲染 + antd 面板 |

### 3.3 数据模型（首期）

- `twin_scene`（场景：名称、缩略图、版本、发布态）
- `twin_entity`（孪生实体：场景内编号、模型资产 file_id、变换矩阵、父节点——场景树）
- `twin_binding`（实体 ↔ 数据源：类型 `iot_device` / `internal_api`、绑定键、属性映射 JSON）
- `twin_marker`（标注/热点：位置、内容、跳转）
- `twin_snapshot`（场景发布版本快照，回滚用）

### 3.4 里程碑

| 里程碑 | 内容 | 验收 |
|---|---|---|
| M0 | 模块骨架 + whale-common-three 补渲染态验收（空场景跑通 WebGL，出验收记录） | three 包首次实机渲染通过，评审文档闭环 |
| M1 | 场景/实体/模型资产 CRUD + 前端 3D 场景查看器（加载 GLB、场景树、相机控制） | 上传一个 GLB 摆进场景并可保存/回显 |
| M2 | 数据绑定 + SSE 实时驱动（绑 iot 设备属性 → 3D 实体状态变色/数值标注） | 模拟设备遥测变化，3D 实体实时联动 |
| M3 | 标注/热点 + 发布快照/回滚 + admin 菜单接入 | 一个可演示的"厂区设备孪生" Demo 场景 |
| M4（远期） | 与 AI 助手联动（选中实体→AI 解读）、摄像机漫游、告警空间定位 | 另立 wave 计划 |

---

## 四、平台三：AI 助手平台（whale-assistant，演进现有三层）

### 4.1 定位与边界

现状三层各司其职、边界清晰，**新平台 = 在其上加"多助手运营层"，不推翻**：

- `whale-ai`(8092, Python)：继续仅内网，承担 LLM 编排。演进：从"单一定制 Graph"升级为**可配置多助手**（每个助手 = system prompt + 工具集 + 知识范围 + 模型参数）。
- `whale-agent`(8090)：继续承担知识检索（Lucene 索引），增加按助手隔离的知识范围过滤。
- **新增 `whale-assistant`**：助手定义/生命周期管理、会话与消息持久化、面向业务应用的开放 API（各业务应用可内嵌自己的助手）、用量统计。

### 4.2 服务划分

| 项 | 值 | 说明 |
|---|---|---|
| 后端 | `whale-assistant`，端口 **8087**（i18n 服务删除后释放的空位） | `whale-backend/whale-services/whale-assistant`，一服务一库 `bluewhale_assistant` |
| 网关路由 | `/assistant/**` → `lb://whale-assistant` | 首个**经网关**暴露的 AI 会话入口；whale-ai 仍不注册网关 |
| 内网调用链 | whale-assistant → whale-ai（SSE 透传）→ 工具循环内调 whale-agent / 新增的 iot·twin 只读工具 | whale-ai 工具注册表（`whale_ai/tools/registry.py`）扩展 |
| 前端 | `whale-assistant` 子应用，端口 **8117** | 助手对话工作台 + 管理端页面进 admin |

### 4.3 数据模型（首期）

- `asst_definition`（助手：名称/头像/system prompt/模型参数/工具白名单/知识范围/可见租户与用户）
- `asst_session`、`asst_message`（会话与消息，含 token 用量、引用记录；长会话摘要仍由 whale-ai Redis 层承担，落库的是可查询历史）
- `asst_tool_grant`（助手 ↔ 工具授权；工具登记复用 whale-ai registry，此处只存授权关系）
- `asst_usage`（按天/租户/助手聚合用量，计费预留）

### 4.4 里程碑

| 里程碑 | 内容 | 验收 |
|---|---|---|
| M0 | 模块骨架 + 网关路由 + 登记 | 服务可启动可路由 |
| M1 | 助手定义 CRUD + whale-ai 多助手改造（builder 按 definition 装配 Graph）+ 会话/消息落库 | 创建一个自定义助手并完成一次带引用的流式问答 |
| M2 | 工具授权体系（registry 扩展 + grant 审批）+ 开放 API（业务应用凭 JWT 换会话内嵌助手） | MES/OA 任一子应用内嵌对话入口跑通 |
| M3 | 前端工作台（会话列表/引用展示/反馈）+ 用量看板 + admin 管理 | 端到端演示：多助手、多入口、用量可见 |
| M4（远期） | IoT 工具（查设备实时态/历史遥测）、Twin 工具（查孪生实体）接入，打通三平台联动 | 另立 wave 计划 |

---

## 五、统一登记表（落地时同步进 service-catalog.md §一/§五/§七）

| 服务/组件 | 类型 | 端口 | 库 | 路由 | 备注 |
|---|---|---|---|---|---|
| whale-iot | 后端核心服务 | 8088 | bluewhale_iot | `/iot/**` | EMQX 认证 webhook 提供方（M0 已落地，Wave 6.02） |
| whale-twin | 后端核心服务 | 8089 | bluewhale_twin | `/twin/**` | SSE `/twin/stream/**`（待启动，Wave 6.03） |
| whale-assistant | 后端核心服务 | 8087 | bluewhale_assistant | `/assistant/**` | 内网调 whale-ai:8092（M0–M3 已落地，Wave 6.01） |
| EMQX | 中间件 | 1883 / 1884 / 18083 | —— | —— | compose `bw-emqx`；**1884 避让已占用号段**，经 `.env` 覆盖（M0 已加入 compose，Wave 6.02） |
| whale-iot | 前端子应用 | **8121** | —— | base `/iot` | qiankun（原草案 8115 已被占用，实机核验后修正） |
| whale-twin | 前端子应用 | **8122** | —— | base `/twin` | qiankun；whale-common-three 首个消费方（原草案 8116 已被占用，实机核验后修正） |
| whale-assistant | 前端子应用 | **8120** | —— | base `/assistant` | qiankun（原草案 8117 归 whale-mail，Wave 6.01 M3 已修正） |

> 端口取自 §七分段空位（核心服务 8087/8088/8089、前端 8115–8117），落地前需实机核验无占用。

---

## 六、实施顺序与依赖

```
并行线 A（AI 助手，依赖最少，见效最快）：
  A-M0 → A-M1 → A-M2 → A-M3

并行线 B（IoT，需新增中间件）：
  B-M0 → B-M1 → B-M2 → B-M3

串行线 C（孪生，依赖 IoT M2 的实时数据才有"孪生"意义）：
  C-M0（可与 A/B 并行，three 渲染验收独立）→ C-M1 → 等 B-M2 → C-M2 → C-M3

收口：Wave 6 收尾 wave：三平台联动 Demo（孪生场景 + 设备数据 + AI 解读）
```

建议节奏：**A 线先行**（复用度最高、风险最低、立即有可见产出）→ B 线跟进 → C 线渲染验收先行、数据驱动随后。

---

## 七、风险与决策点（需舰长拍板）

| # | 风险/决策 | 建议 |
|---|---|---|
| 1 | MQTT Broker 选型（EMQX vs Mosquitto vs Memurai 是否够用） | **EMQX**：社区版自带认证 webhook/规则/WS，与平台内网 REST 范式吻合；Mosquitto 无管理面，后续规则引擎要自己写 |
| 2 | 遥测存储：首期 MySQL 分区 vs 直接上时序库 | **首期 MySQL 按月分区**，控制新中间件数量；量级上来（>1 万设备）再评估 TimescaleDB |
| 3 | whale-assistant 是否值得独立服务（也可挂在 whale-agent 里） | **独立**：whale-agent 职责是"知识检索"，塞入助手运营/计费/开放 API 会破坏单一职责；且 8087 是现成空位 |
| 4 | whale-ai 从"单 Graph"到"多助手"改造涉及 LangGraph builder 重构 | M1 内先做**配置化装配**（同一 Graph 拓扑、参数注入），不引入动态拓扑；动态编排留远期 |
| 5 | 数字孪生与 whale-scene 的 3D 开关边界 | twin 独立成服务，不动 scene 的 `WHALE_SCENE_3D_ENABLED`，避免既有 2D 功能回归 |
| 6 | Wave 编号 | 本计划占 **Wave 6.x**（当前最高 5.98），三个平台分别 6.01 / 6.02 / 6.03 |
| 7 | 本机 C 盘铁律 | EMQX（Docker compose）与新增依赖全部落 `f:\blue-whale\.toolchains` 约束内，遵守 AGENTS.md |

---

## 八、下一步（审阅通过后）

1. 按 A 线启动 `wave6.01-ai-assistant` 详细设计（表结构 DDL + whale-ai builder 改造点）。
2. `docs/service-catalog.md` §七新增三个端口登记（标注"规划"状态）。
3. `infrastructure/docker/docker-compose.yml` 增补 `bw-emqx`（`.env.example` 同步端口变量）。
4. 各平台按里程碑拆任务，每个 M 出独立验收清单（对齐 `docs/checklists/` 惯例）。
