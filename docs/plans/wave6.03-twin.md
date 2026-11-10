# Wave 6.03：数字孪生平台（whale-twin）

> 状态：**实施中（M0 已落地）** · 起草：2026-11-10
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
| M1 | 场景/实体/模型资产 CRUD + 前端 3D 场景查看器（加载 GLB、场景树、相机控制） | 上传一个 GLB 摆进场景并可保存/回显 | 待开始 |
| M2 | 数据绑定 + SSE 实时驱动（绑 iot 设备属性 → 3D 实体状态变色/数值标注） | 模拟设备遥测变化，3D 实体实时联动 | 待开始 |
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

## 四、M1 设计要点（预告）

- 后端：`TwinSceneService`（状态机对齐 iot_product：draft→published⇄disabled）、`TwinEntityService`（场景树 CRUD + 环检测：parent 链路不得成环）、模型资产走 whale-file 上传（GLB/GLTF 白名单 + 大小上限），VO 下发 file 预览 URL
- 前端 8122：`@bluewhale/whale-common-three` 渲染 + OrbitControls（按需补进 three 包或子应用自引 three/addons）+ 场景树面板 + 实体变换编辑（gizmo 留 M3）
- 权限：`sys:twin:scene`（2313）入 `migration-6.03-twin.sql`，基座菜单「数字孪生」

## 五、风险与边界

- **多实例 SSE**：M2 SSE 单实例内存注册表，与 whale-iot 同口径；横向扩容时统一改造（Redis pub/sub 或粘性路由），Wave 6 内不展开
- **whale-file 大文件**：GLB 可达几十 MB，M1 上传走 file 服务既有分片能力；预览 URL 需带内网令牌的签名策略在 M1 定
- **three 版本**：`whale-common-three` 锁 three ^0.169，子应用必须复用同一实例（peer），避免双打包
