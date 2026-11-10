# Wave 6.03：数字孪生平台（whale-twin）

> 状态：**已交付（M0–M3 全部落地，2026-11-10）** · 起草：2026-11-10
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
| M3 | 标注/热点 + 发布快照/回滚 + 基座菜单接入 | 一个可演示的"厂区设备孪生" Demo 场景 | ✅ 2026-11-10 |

## 三、M0 落地清单（2026-11-10）

**后端骨架**（`whale-backend/whale-services/whale-twin`）：

- `WhaleTwinApplication`（`@SpringBootApplication` + `@EnableDiscoveryClient`），`application.yml`：8089、`bluewhale_twin` 数据源、Flyway、Nacos、internal-token 守卫（`/**/internal/**`）、权限码头拦截开启
- V1 基线 DDL 5 表：`twin_scene`（场景：编码/缩略图/scene_config/version/状态机 draft→published⇄disabled）、`twin_entity`（场景树：entity_code/parent_id/model_file_id/transform_json）、`twin_binding`（source_type: `iot_device`/`internal_api`、bind_key、property_map、poll_interval_ms）、`twin_marker`（label/hotspot/alarm、position_json、link_url）、`twin_snapshot`（scene_id+version 唯一、snapshot_json 整包冻结）
- ArchUnit 分层门禁 4 规则（空包期 `allowEmptyShould(true)`，M1 落实体后自动生效）
- pom 注册进 `whale-services` 聚合；`mvn test` 4/4 全绿，Checkstyle 0

**渲染态验收**（`whale-frontend/shared-packages/whale-common-three/acceptance/`）：

- `index.html`：import map 解析 `three` 裸说明符（包以 `external: ['three']` 构建）+ `WhaleSceneManager` 空场景 + 旋转立方体（`MeshNormalMaterial`）
- `serve.mjs`：零依赖静态服务器（`127.0.0.1:8199`，MIME 覆盖 `.mjs`）
- `verify.mjs`：Playwright 无头断言（从 `whale-frontend/e2e` 工作区解析 `playwright-core`），结果 JSON 打印，退出码 0/1
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

## 七、M3 落地清单（2026-11-10）

**后端**（`whale-services/whale-twin`）：

- `TwinMarkerService` + `/twin/scenes/{sceneId}/markers`：标注 CRUD；类型白名单（`label`/`hotspot`/`alarm`）、`positionJson` 必须为 JSON 对象（`{"x","y","z"}`，语义前端解释）、挂靠实体须属于当前场景（可空 = 场景级锚点）
- `TwinSnapshotService`：
  - **冻结**：`TwinSceneService.publish(id, remark, userId)` 状态切 published 后把「场景元数据 + 实体树 + 绑定 + 标注」整包序列化落 `twin_snapshot`（version 取发布时场景版本）；同版本重新发布（发布→停用→未改动再发布）先物理移除旧行避免撞 `uk_scene_version`；`SceneService ↔ SnapshotService` 循环依赖用 `ObjectProvider` 打断
  - **回滚**：`POST /twin/scenes/{id}/snapshots/{snapshotId}/rollback`——场景须为草稿/停用态；先**物理删除**当前实体/绑定/标注（对齐 whale-iot 物模型「物理替换」先例，规避 `@TableLogic` 逻辑删除残留占 `uk_scene_code`），再按快照 JSON 重建：实体拿新雪花 ID、旧→新映射回填父子关系，绑定/标注挂靠按映射换算（实体缺失的绑定丢弃、标注退化为场景级）；场景元数据回滚（编码不动），version 自增、状态回 draft
- 快照列表/详情：详情接口才携带整包 JSON（列表不膨胀响应）
- 单测 45/45（新增标注 5 + 快照 6 + 场景发布 2），Checkstyle 0，ArchUnit 4/4

**前端**（8122）：

- **查看器页·标注**：标注抽屉（类型/挂靠实体/内容/坐标/跳转/启停）；`twin_marker` 按 `positionJson` 渲染为文字 Sprite（离屏 canvas → CanvasTexture，label 黄 / hotspot 青 / alarm 红，重建时释放旧纹理）；Raycaster 点击 Sprite，带 `linkUrl` 的热点新窗口打开；标注根节点独立于实体 root，实体重建互不影响
- **场景页·发布**：发布改走弹窗（发布说明随发布冻结进快照）；「快照」抽屉列版本倒序快照（版本/说明/时间），支持整包 JSON 预览与回滚（Popconfirm 二次确认；published 场景禁用回滚）
- **构建**：`manualChunks` 把 three 全家桶拆独立 chunk（M1 遗留项）——three 585KB（gzip 149KB）/ 主包 1.17MB（gzip 370KB，原 1.76MB）

**基座接入**：M1 已注册（`apps.ts` 8122 + `/twin` activeRule、`menu.ts`「数字孪生」`sys:twin:scene`）；对齐 IoT M3 口径**不新增 admin 页面**，入口由基座静态菜单承载；标注/快照复用 `sys:twin:scene` 权限码，未新增权限项。

**Demo 验收路径（"厂区设备孪生"）**：

1. IoT 侧注册产品（如 `plant-demo`）+ 设备若干，设备用三元组连上 EMQX 并上报温度；
2. 孪生侧建场景 `plant-demo`（厂区），上传厂区 GLB（或用占位体），摆若干实体并把 `modelFileId` 指到 whale-file；
3. 查看器页给实体建绑定（选设备 + propertyMap 配 `{"temperature":{"stateKey":"color","thresholds":[50,80]}}`）；
4. 给锅炉实体加 alarm 标注（坐标放设备上方，linkUrl 可指 `/iot/devices`）、给入口加 label 标注；
5. 场景页「发布」填说明（v1 冻结），改一处实体后从「快照」回滚 v1 验证工作副本重建；
6. 查看器页「订阅实时」，模拟设备温度跨越阈值，观察实体绿→橙→红变色与实时面板。

**M3 边界**：标注坐标暂靠手填 JSON（可视化 gizmo 拾取留后续 Wave）；快照 JSON 含实体/绑定/标注全量，量级大后可考虑分表快照；`internal_api` 绑定轮询仍未接（M2 留白，运行时只留配置）。
