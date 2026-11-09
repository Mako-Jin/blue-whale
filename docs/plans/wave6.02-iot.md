# Wave 6.02 — IoT 设备联网平台（whale-iot）详细设计

> 状态：**实施中（M0 + M1 + M2 已落地）** · 起草：2026-11-09
> 总体规划：[wave6-iot-twin-ai-assistant-plan.md](wave6-iot-twin-ai-assistant-plan.md)（§二 平台一）

---

## 一、定位与边界

- **职责**：设备接入与认证（EMQX 认证 Webhook 提供方）、物模型（属性/事件/服务）、遥测上下行、设备影子、指令下发与回执、规则引擎（条件→动作）。
- **不做**：不做业务语义（MES 设备台账仍归 MES）；首期仅 **MQTT** 协议，HTTP/Modbus/OPC-UA 协议网关留 M4 之后；深度分析报表不做（遥测落库 + 简单聚合即可）。
- **关键决策（已定）**：
  - Broker 选型 **EMQX 5.8**（社区版自带认证 Webhook / WS / 管理面）；
  - 遥测存储首期 **MySQL 按月分区**，不引入时序库（>1 万设备再评估 TimescaleDB）；
  - **whale-iot 不做常驻 MQTT 客户端**——遥测与上下线事件经 EMQX Webhook / HTTP 桥接回调本服务 internal 接口，落库与推送统一在 REST 侧完成（无重连/会话态问题，与平台既有范式一致）。

## 二、M0 落地清单（本次完成）

| 项 | 内容 |
|---|---|
| 模块 | `backend/whale-services/whale-iot`（8088 / `bluewhale_iot`，对齐 whale-assistant 骨架） |
| V1 DDL | `iot_product` / `iot_thing_model` / `iot_device` / `iot_telemetry`（按月分区，主键 `(id, ts)`）/ `iot_command`（`message_id` 幂等键）/ `iot_rule` |
| 自检端点 | `GET /iot/info` |
| EMQX | compose `bw-emqx`（1883 / 1884→容器 8083 / 18083；`EMQX_DASHBOARD_PASSWORD` 弱口令硬校验；healthcheck `emqx ctl status`）；`.env.example` 补端口/镜像/口令变量 |
| 网关 | `/iot/**` → `lb://whale-iot`（不 StripPrefix；`/iot/internal/**` 由 internal-deny 403） |
| 登记 | service-catalog §一 #34、§二路由表、§四中间件 #10（EMQX）、§五 MySQL 库清单、§七两处 |
| 验证 | `mvn compile` ✅、`mvn test` 4/4 ✅（ArchUnit 门禁 + Checkstyle） |

**端口修正**：总体规划草案把前端子应用定为 8115/8116，实机核验 8114–8119 已全被占用，**whale-iot 前端改为 8121、whale-twin 预定 8122**（M3 落地时登记）。

## 二之二、M1 落地清单

| 项 | 内容 |
|---|---|
| 产品 | `IotProductService` + `/iot/products`：CRUD、分页（附带设备计数）、`publish`/`disable`；**已发布禁改**（需先停用）、删除要求产品下无设备 |
| 物模型 | `IotThingModelService` + `GET/PUT /iot/products/{id}/thing-model`：整包覆盖（**物理替换**，避开逻辑删除残留占 `uk_product_kind_identifier` 的坑）、条目去重、`ThingModelValidator`（属性必带 dataType；数值属性 min≤max、step>0；非数值型禁配 min/max）；**已发布产品物模型冻结** |
| 设备 | `IotDeviceService` + `/iot/devices`：注册（`SecureRandom` 32 字节 → 64 位 hex secret，**仅创建响应返回一次**）、分页/详情（不回显 secret）、更新昵称/状态、`POST /devices/{id}/secret` 重置（旧 secret 立即失效）、删除 |
| EMQX 回调 | `/iot/internal/emqx/auth` 与 `/webhook`（**响应体为 EMQX 约定形状**，不走 Result 包装）：`username = {productCode}/{deviceName}`、`password = HMAC-SHA256(secret, username)` 小写 hex；fail-closed（产品未发布/设备禁用/HMAC 不符/用户名不合法一律 deny）；连接/断开事件维护 `online`/`last_online_time` |
| 权限码 | `sys:iot:product`（产品+物模型）、`sys:iot:device`（设备）；全部写操作带 `@AuditLog` |
| 验证 | `mvn test` **34/34 全绿**（产品 7 + 物模型 7 + 设备 5 + EMQX 11 + ArchUnit 4），Checkstyle 通过 |

**EMQX 联调配置（M1 验收用）**：Authentication → HTTP Server → `POST http://whale-iot:8088/iot/internal/emqx/auth`（携带 `X-Internal-Token`）；Webhook → `POST http://whale-iot:8088/iot/internal/emqx/webhook`（事件 `client.connected` / `client.disconnected`）。设备侧用 `{productCode}/{deviceName}` + HMAC 口令连接 1883。

## 二之三、M2 落地清单

| 项 | 内容 |
|---|---|
| 遥测入库 | `IotTelemetryService`：EMQX `message.publish` 桥接 → 解析主题定位设备 → 按物模型 `data_type` 分流（`int`/`double` → `value`，其余 → `value_text`）→ 落 `iot_telemetry`（按月分区）→ 更新影子与 `last_report_time`。**脏数据丢弃计数**（未知标识符 / 类型不符 / 超批次上限），接口恒 200，避免 Broker 重投风暴 |
| 设备影子 | `IotShadowService`：`shadow_json` = `{reported, desired, version, reportedTime, updatedTime}`；上报命中期望值即从 desired 移除（期望值只在未达成时存在）；指令参数中与属性同名的键写入 desired；设备上线（`client.connected`）若有未达成期望，经 EMQX 发布 `down/shadow` 补推 |
| 实时推送 | `IotStreamService` + `GET /iot/devices/{id}/stream`（SSE，单设备粒度）：事件 `ready` / `telemetry` / `event` / `command`；连接不设超时，按 `stream-heartbeat-seconds`（默认 30s）发注释帧保活，写回失败即摘除连接 |
| 指令下发 | `IotCommandService` + `POST /iot/devices/{id}/commands`：`message_id` 幂等（重复返回既有指令）、物模型 service 校验（模型无服务定义时不限制）、落 `pending` → EMQX REST `/api/v5/publish` 出网成功后转 `sent`；**投递失败保持 `pending`**（报文没出去不判定设备失败，留待 M3 重投） |
| 指令回执 | 设备上报 `up/reply`（`{"id":"消息ID","code":0,"data":{...}}`）→ 按 `message_id` 收敛 `success`/`failed`，重复回执幂等忽略，状态变化经 SSE 推 `command` |
| EMQX 出网 | `EmqxPublishClient`（`POST /api/v5/publish`，Basic 认证 API Key/Secret）：**不建常驻 MQTT 客户端**；凭据未配置时降级为只落库不投递（本地无 Broker 也能跑通链路） |
| 内网只读 | `GET /iot/internal/device/state`（`deviceId` 或 `productCode+deviceName`）返回影子实时态，供数字孪生 / AI 助手工具化调用（M4 正式接入） |
| 配置 | `whale.iot.*` 收敛到 `IotProperties`（EMQX 地址/凭据、遥测批次与查询上限、SSE 心跳、指令超时）；新增 `WHALE_IOT_EMQX_API_SECRET` 等环境变量 |
| 验证 | `mvn test` **62/62 全绿**（M1 34 + M2 28：影子 7 / 遥测 7 / 指令 10 / 推送 4），Checkstyle 0，ArchUnit 4/4 |

## 三、数据模型要点

- **三元组**：`productId + deviceName + secret`，产品内 `device_name` 唯一（`uk_product_device`）；secret 签发后仅可重置、接口不回显。
- **物模型**：`iot_thing_model` 按 `product_id + kind + identifier` 唯一；属性 `spec_json` 存取值范围/步长/枚举，遥测落库前按属性 `data_type` 校验并分流 `value`（数值）/`value_text`（文本）。
- **设备影子**：`iot_device.shadow_json` 存"最新上报快照 + 期望值"两段；期望值由指令下发路径写入，设备上线 Webhook 时做版本比对合并。
- **遥测分区**：`RANGE TO_DAYS(ts)` 预建 p202611–p202701 + `p_future` 兜底；M2 起定时任务按月 `REORGANIZE PARTITION` 滚动预建（复用 whale-job）。
- **指令幂等**：`message_id` 唯一键，重复下发返回既有指令不重复执行。

## 四、MQTT 接入设计（M1）

### 4.1 Topic 约定

```
上行：  bw/{tenant}/{productCode}/{deviceName}/up/property    （属性遥测）
        bw/{tenant}/{productCode}/{deviceName}/up/event       （事件上报）
        bw/{tenant}/{productCode}/{deviceName}/up/reply       （指令回执）
下行：  bw/{tenant}/{productCode}/{deviceName}/down/command   （指令下发）
状态：  $SYS/brokers/+/clients/... 由 EMQX Webhook 替代，不自订阅
```

### 4.2 认证链路（EMQX → whale-iot）

- EMQX 配置 Authentication → HTTP Server，指向 `POST http://whale-iot:8088/iot/internal/emqx/auth`：
  - whale-iot 按 `productCode/deviceName` 查设备、以 HMAC(deviceSecret, password) 校验口令，成功返回 200 + 设备主键，失败 401（fail-closed：设备禁用/产品停用一律拒绝）。
- Webhook（连接/断开/断言）指向 `/iot/internal/emqx/webhook`：维护 `iot_device.online` 与 `last_online_time`。
- 遥测桥接：首期用 EMQX **Webhook `message.publish`**（仅匹配 `*/up/property|event|reply` 主题）转发到 `/iot/internal/emqx/telemetry`；M2 若吞吐不足改 Data-Bridges HTTP。
- **安全**：internal 路径走 `X-Internal-Token`（starter 默认 `/**/internal/**` 模式已覆盖）；EMQX 与 whale-iot 同 compose 网络 `bw-net`。

### 4.3 三元组签发

`POST /iot/devices`（`sys:iot:device` 权限）创建设备时服务端生成 32 字节随机 secret，**响应仅返回一次**；提供 `PUT /iot/devices/{id}/secret` 重置（旧 secret 立即失效，EMQX 侧无需额外清理——连接时再校验）。

## 五、接口规划（M1–M3）

| 资源 | 端点（`/iot` 前缀） | 权限码 | 里程碑 |
|---|---|---|---|
| 产品 | `POST/GET/PATCH/DELETE /products`、`POST /products/{id}/publish` | `sys:iot:product` | M1 |
| 物模型 | `PUT /products/{id}/thing-model`（整包覆盖式保存）、`GET /products/{id}/thing-model` | `sys:iot:product` | M1 |
| 设备 | `POST/GET/PATCH/DELETE /devices`、`POST /devices/{id}/secret` | `sys:iot:device` | M1 |
| 影子/遥测 | `GET /devices/{id}/shadow`、`GET /devices/{id}/telemetry?identifier=&range=`、`GET /devices/{id}/stream`（SSE，对齐 MES 先例） | `sys:iot:device` | M2 |
| 指令 | `POST /devices/{id}/commands`（同步落 pending，EMQX 桥接出网后转 sent）、`GET /commands?deviceId=` | `sys:iot:device` | M2 |
| 规则 | `POST/GET/PATCH/DELETE /rules`、`POST /rules/{id}/enable` | `sys:iot:rule` | M3 |
| internal | `/iot/internal/emqx/auth|webhook|telemetry`、`/iot/internal/device/state`（twin/assistant 只读实时态，M4 工具化） | 内网令牌 | M1/M2 |

状态机：产品 `draft→published⇄disabled`（已发布产品禁止修改物模型，需先停用）；设备无状态机，仅 active/disabled 开关。

## 六、规则引擎（M3）

- 条件 JSON：`{"identifier":"temperature","op":">","value":80,"durationSec":60}`（连续满足才触发，去抖）。
- 动作首期 `alert`：调 whale-alert `/alert/internal`（9103，内网令牌）创建告警记录，携带设备/产品/规则上下文；`webhook` 动作留配置位。
- 限流去重：同规则距 `last_fired_time` 不足静默期（默认 300s，规则可配）不重复触发。

## 七、里程碑与验收

| 里程碑 | 内容 | 验收 |
|---|---|---|
| M0 ✅ | 模块骨架 + EMQX 进 compose + 网关路由 + 登记 | compile/test 通过；compose `up -d emqx` 可启动（实机待环境） |
| M1 ✅ | 产品/物模型/设备 CRUD + 三元组签发 + EMQX 认证 Webhook | 后端 34 项单测全绿；设备用三元组 connect 1883 的实机验收随联调 |
| M2 ✅ | 遥测落库 + 影子 + SSE 实时推送 + 指令下发/回执 | 后端 62 项单测全绿（影子/遥测/指令/推送 28 项新增）；设备发遥测后 SSE 即时可见、指令回执转 success 的**实机验收随 EMQX 联调**（无 Broker 时下发降级为 pending，链路可测） |
| M3 | 规则引擎（→whale-alert）+ 前端子应用 8121（产品/设备/遥测/规则四页）+ admin/基座接入 | 一条规则可触发一条告警 |
| M4（远期） | 协议网关、边缘接入、遥测冷热分层、AI/Twin 只读工具注册 | 另立 wave 计划 |

## 八、风险与备注

- **实机依赖**：本机 Docker 环境（EMQX 拉镜像）与 MySQL `bluewhale_iot` 建库在联调时完成；M0–M2 交付以编译/测试/文档为准。
- **EMQX 出网凭据**：指令与影子补推走 REST `/api/v5/publish`，需在 EMQX Dashboard → API Keys 生成 Key/Secret 并配 `WHALE_IOT_EMQX_API_KEY` / `WHALE_IOT_EMQX_API_SECRET`；未配时链路降级为「只落库 pending」，不报错也不丢数据。
- **遥测桥接形态**：首期用 Webhook `message.publish`（同步转发，EMQX 侧配置主题过滤 `up/property|event|reply`）；单设备高频上报（>50 msg/s）时改 Data-Bridges HTTP 批量投递，接口契约不变。
- **EMQX 镜像拉取**：国内网络可把 `.env` 的 `EMQX_IMAGE` 指向镜像站前缀（对齐 NGINX_BASE_IMAGE 惯例）。
- **遥测量级**：首期单分区索引 `(device_id, identifier, ts)` 覆盖设备视角查询；租户级报表走 `iot_telemetry` 聚合 SQL，不够再补汇总表。
- **与 MES 边界**：MES 设备台账（业务语义）与 IoT 设备实例（接入语义）各自独立；如需关联，MES 侧存 `iot_device_id` 引用，不做双写。
