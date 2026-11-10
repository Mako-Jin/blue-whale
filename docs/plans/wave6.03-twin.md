# Wave 6.03：数字孪生平台（whale-twin）

> 状态：**实施中（M0 + M1 + M2 已落地）** · 起草：2026-11-10
> 总规划：[wave6-iot-twin-ai-assistant-plan.md](wave6-iot-twin-ai-assistant-plan.md) §三
> 上游依赖：Wave 6.02 IoT M2 已交付（`GET /iot/internal/device/state` 内网实时态只读接口）

## 一、服务定位（对齐总规划 §3.1–3.3，不再重复）

- 后端 `whale-twin`：8089 / `bluewhale_twin` / `/twin/**`（网关不 StripPrefix，内网 `/twin/internal/**` 由 internal-deny 403）
- 前端 `whale-twin` 子应用：8122 / base `/twin`（M1 起建；原草案 8116 已被 whale-party 占用）
- 与 whale-scene（8097，2D 底座）互不改表；`WHALE_SCENE_3D_ENABLED` 保持不动
- 模型资产 GLB/GLTF 存 whale-file（8085）；实时数据取 whale-iot internal 只读接口，不自连 MQTT

## 二、里程碑

| 里程碑 | 内容 | 验收 | 状态 |
|---|---|---|---|
| M0 | 后端模块骨架 + V1 DDL 5 表 + whale-common-three 首个消费方渲染态验收 | 空场景 WebGL 跑通，评审文档遗留项闭环 | ✅ 2026-11-10 |
| M1 | 场景/实体/模型资产 CRUD + 前端 3D 场景查看器（加载 GLB、场景树、相机控制） | 上传一个 GLB 摆进场景并可保存/回显 | ✅ 2026-11-10（GLB 经 whale-file file_id 引用） |
| M2 | 数据绑定 + SSE 实时驱动（绑 iot 设备属性 → 3D 实体状态变色/数值标注） | 模拟设备遥测变化，3D 实体实时联动 | ✅ 2026-11-10 |
| M3 | 标注/热点 + 发布快照/回滚 + admin/基座菜单接入 | 一个可演示的"厂区设备孪生" Demo 场景 | 待开始 |

## 三、M0 落地清单（2026-11-10）

**后端骨架**（`backend/whale-services/whale-twin`）：

- `WhaleTwinApplication`（`@SpringBootApplication` + `@EnableDiscoveryClient`），`application.yml`：8089、`bluewhale_twin` 数据源、Flyway、Nacos、internal-token 守卫（`/**/internal/**`）、权限码头拦截开启
- V1 基线 DDL 5 表：`twin_scene`（场景：编码/缩略图/scene_config/version/状态机 draft→published⇄disabled）、`twin_entity`（场景树：entity_code/parent_id/model_file_id/transform_json）、`twin_binding`（source_type: `iot_device`/`internal_api`、bind_key、property_map、poll_interval_ms）、`twin_marker`（label/hotspot/alarm、position_json、link_url）、`twin_snapshot`（scene_id+version 唯一、snapshot_json 整包冻结）
- ArchUnit 分层门禁 4 规则（空包期 `allowEmptyShould(true)`，M1 落实体后自动生效）
- pom 注册进 `whale-services` 聚合；`mvn test` 4/4 全绿，Checkstyle 0

**渲染态验收**（`frontend/shared-packages/whale-common-three/acceptance/`）：

- `index.html`：import map 解析 `three` 裸说明符（包以 `external: ['three']` 构建）+ `WhaleSceneManager` 空场景 + 旋转立方体（`MeshNormalMaterial`）
- `serve.mjs`：零依赖静态服务器（`127.0.0.1:8199`，MIME 覆盖 `.mjs`）
- `verify.mjs`：Playwright 无头断言（从 `frontend/e2e` 工作区解析 `playwright-core`），结果 JSON 打印，退出码 0/1
- 结果：`webglVersion=WebGL 2.0 (OpenGL ES 3.0 Chromium)`、48 帧渲染循环递增、resize 生效、dispose 后画布摘除且帧停止、0 控制台错误
- 评审闭环：[2026-10-26-p3-three-scene.md](../reviews/2026-10-26-p3-three-scene.md) 遗留项「无消费方 → 无浏览器渲染态验证」已关闭

**登记**：网关 `/twin/**` → `lb://whale-twin`；service-catalog 登记 35 号服务、路由、8089/8122 端口

## 四、M1 落地清单（2026-11-10）

**后端**（`whale-services/whale-twin`）：

- `TwinSceneService`：创建（draft/version=1）/更新（已发布禁改、version 自增）/发布/停用/删除（要求无实体）/分页（带 entityCount）/详情；编码租户内唯一
- `TwinEntityService`：场景树节点 CRUD；`assertParentInScene` 沿父链上溯做**环检测**（不能挂到自身/子孙下），父节点跨场景拒绝；删除要求无子节点；`tree()` 按 sortOrder 升序递归装配，孤儿节点（父缺失）兜底为根
- 控制器：`/twin/scenes/**` + `/twin/scenes/{sceneId}/entities/**`，统一 `@RequirePermission("sys:twin:scene")` + 审计注解
- 权限迁移：`migration-6.03-twin.sql`（sys:twin:scene 2313 + tenant_admin 授权；不写 sys_menu，入口走基座静态菜单）
- 单测 17/17（架构 4 + 场景 6 + 实体 7），Checkstyle 0

**前端**（`platform-apps/whale-twin`，8122，base `/twin`）：

- **场景管理页**：场景 CRUD + 状态机操作 + 「查看」进入查看器
- **3D 查看器页**：`WhaleSceneManager`（M0 验收过的渲染核心）+ OrbitControls（three/addons）；实体树面板（antd Tree）；有 `modelFileId` 的实体从 `/file/{id}/preview` fetch 为 Blob → objectURL → `loadGltf`（GLTFLoader 不能带请求头），加载失败降级为暗红占位体；无模型实体为蓝色占位立方体；`transformJson` 约定 `{position,rotation,scale}`（弧度欧拉角）；相机按场景包围盒自动取景
- 基座接入：`apps.ts`（8122 + `/twin` activeRule）、`menu.ts`「数字孪生」（sys:twin:scene）、vite `/twin` 代理；子应用独立运行时 `/twin` `/file` 双代理
- 构建通过（tsc + vite）；产物 ~1.7MB（three.js 打入主 chunk，gzip 507KB，M3 按需 manualChunks 拆分）

**M1 边界**：GLB 上传本身走 whale-file（前端直传后把 file_id 填给实体），孪生服务不自建对象存储；实体变换的可视化 gizmo 编辑、标注/热点、发布快照留 M2/M3。

## 五、风险与边界

- **多实例 SSE**：M2 SSE 单实例内存注册表，与 whale-iot 同口径；横向扩容时统一改造（Redis pub/sub 或粘性路由），Wave 6 内不展开
- **whale-file 大文件**：GLB 可达几十 MB，M1 上传走 file 服务既有分片能力；预览 URL 需带内网令牌的签名策略在 M1 定
- **three 版本**：`whale-common-three` 锁 three ^0.169，子应用必须复用同一实例（peer），避免双打包

## 六、M2 落地清单（2026-11-10）

**后端**（`whale-services/whale-twin`）：

- `TwinBindingService`：绑定 CRUD；校验实体归属场景、数据源类型白名单（`iot_device`/`internal_api`）、`bindKey` 形态（设备ID纯数字 / 接口路径以 `/` 开头）、`propertyMap` 必须为 JSON 对象；**数据源类型不可改**（改则删了重建）；列表跳过实体已删的脏绑定
- `TwinStreamService`：按<b>场景</b>粒度 SSE（`GET /twin/scenes/{id}/stream`），事件 `ready`/`binding`，心跳注释帧（对齐 whale-iot 设备流）；`activeScenes()` 供运行时只刷有订阅者的场景
- `IotStateClient`：内网 GET `{whale.twin.iot.base-url}/iot/internal/device/state?deviceId=`，`InternalTokenSupport` 自动带 `X-Internal-Token` + `X-Tenant-Id`；失败返回 null（渲染旁路不拖垮场景）
- `TwinBindingRuntimeService`：全局 tick（默认 5s）→ 场景内逐绑定按 `pollIntervalMs` 到期判断 → 同 tick 同设备共享一次拉取 → 按 `propertyMap` 抽值（映射值为字符串直接换键；为对象取 `stateKey`，`thresholds` 留给前端）→ **绑定粒度差分**（`online+values` 指纹）→ 只推变化批次；单场景设备数上限 50；`internal_api` 源 M2 只留配置
- `TwinBindingController`（CRUD + `GET /bindings/state` 快照）与 `TwinStreamController`（SSE）；配置收口 `TwinProperties`（`whale.twin.iot.*` / `runtime.*`）
- 单测 32/32（新增绑定 8 + 运行时 7：差分/离线/映射/停用跳过/tick 异常吞噬），Checkstyle 0

**前端**（8122 查看器页增强）：

- 数据绑定抽屉：实体选择、IoT 设备选择器（跨服务拉 `/iot/devices`）、`internal_api` 路径切换、属性映射 JSON（带阈值示例）、轮询间隔、启停；绑定列表就地编辑/删除
- 实时联动：`订阅实时` 先拉 `bindings/state` 快照再开 SSE（fetch 读流手工分帧）；`binding` 事件按 `thresholds` 驱动实体 **emissive 变色**（离线灰 / <t1 绿 / t1~t2 橙 / >t2 红 / 在线无阈值青底光；GLB 材质只动 emissive 不毁贴图，本色存 `userData` 可恢复）；右上角实时面板展示各绑定最新值
- 切场景自动断流、清面板、恢复本色

**M2 边界**：`internal_api` 轮询、实体 gizmo 变换编辑、标注/热点、发布快照均留 M3；SSE 与 whale-iot 同为单实例内存注册表，横向扩容统一改造。
