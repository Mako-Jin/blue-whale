# 城池场景导航服务设计（whale-scene）

> 状态：**M1/M2 2D 已落地**；M3 3D 保持可选（未启动）
> 关联需求：REQ-FUNC-034、REQ-ARCH-016、REQ-SEC-026
> 关联规划：[Wave 5.77 场景与 CRM 规划](../plans/2026-11-03-wave5.77-scene-and-crm.md) §3.1 / §9.1
> 关联基线：[service-catalog.md](../service-catalog.md)、[naming-conventions.md](../naming-conventions.md)、[frozen-optional-modules.md](frozen-optional-modules.md)
> 实现位置：`whale-backend/whale-services/whale-scene`、`whale-frontend/platform-apps/whale-scene`

---

## 1. 结论速览

| # | 结论 |
|---|---|
| 1 | 场景导航是**支撑型子域**：只编排入口与布局，不拥有 OA/ERP/CRM 等业务数据。 |
| 2 | **2D 为主生产力界面**：纯 CSS 渲染，无 WebGL/Canvas 依赖；`threeDEnabled` 默认 `false`。 |
| 3 | 3D 不是新服务、不重建引擎：仅在 F-09 解冻四条件满足后，作为 M3 **可选增强层**复用冻结的 `whale-common-three`。 |
| 4 | 身份与权限判定只来自网关注入的身份头与 auth 权限码，本服务不做身份源。 |
| 5 | 跨租户一律按不存在处理（404），不泄露存在性；入口 URL 做协议 + 内网 + 开放重定向白名单。 |

---

## 2. 职责边界

| 拥有 | 不拥有 |
|---|---|
| 场景（城池）元数据与状态机 | 任何业务系统数据（OA/ERP/CRM 等） |
| 区域 / 建筑 / 入口节点树 | 身份、角色、权限判定结果（auth 唯一身份源） |
| 节点入口绑定（系统 / 页面 / 知识库 / 外链） | 文件字节（`whale-file`） |
| 场景/节点可见性规则 | 知识检索与问答（`whale-agent` / `whale-doc`） |
| 发布布局快照与 checksum | 3D 引擎实现（前端可选渲染层） |

关联上下文：`whale-scene → auth` 为遵奉者；`whale-portal → whale-scene` 读取导航；
`whale-scene → 业务应用` 只保存入口 URL 与权限码，**不直连业务库**；知识入口只跳转，
权限透传由目标系统再次鉴权。

---

## 3. 架构与依赖规则

采用 DDD 四层 + Ports & Adapters，与 auth/file/doc/music/video 一致：

```text
interfaces（Controller / 参数校验）
    ↓
application（命令服务 / 查询服务 / 视图装配 / 布局快照）
    ↓
domain（聚合根 / 领域行为 / 仓储端口 / 错误码）
    ↑
infrastructure（MyBatis-Plus 实体 / Mapper / 仓储实现）
```

ArchUnit 固化（`SceneArchitectureTest`，4 条规则）：

1. `interfaces` 不得直接依赖 `infrastructure`；
2. `application` 不得依赖 `infrastructure`；
3. `domain` 不得依赖 Spring / MyBatis / Controller DTO；
4. 分层包依赖方向单向 `interfaces → application → domain`。

不引入完整 CQRS：写走 `SceneAppService`，读走 `SceneNavigationAppService` 与视图装配；
事务只包住「聚合加载 → 领域行为 → 保存」；发布快照生成在本地事务内完成，无出站调用。

---

## 4. 领域模型与业务规则

### 4.1 聚合与值对象

| 对象 | 类型 | 关键行为 |
|---|---|---|
| `Scene` | 聚合根 | `publish(version, time)`、`offline()`；状态 `DRAFT / PUBLISHED / OFFLINE` |
| `SceneNode` | 实体 | 树形节点，`nodeKey` 场景内唯一；有子节点禁止删除 |
| `NodeBinding` | 实体 | 一节点至多一个入口；`visibleTo(permissions)` 过滤入口权限码 |
| `SceneVisibility` | 实体 | `matches(roles, userId, deptId)`；`nodeId=0` 为场景级；DENY 优先 |
| `LayoutVersion` | 实体 | 发布快照 `layoutJson` + SHA-256 `checksum` |

### 4.2 发布校验（硬卡口）

| 校验 | 上限/规则 | 错误码 |
|---|---|---|
| 节点数 | ≤ `maxNodesPerScene`（默认 200） | 40006 |
| 树深度 | ≤ `maxNodeDepth`（默认 5） | 40005 |
| 环 | 父引用必须可上溯到根 | 40004 |
| 入口 | ≥ 1 个启用入口 | 40007 |
| 入口地址 | 平台内 `/` 路径或 `http/https`；拒绝危险协议、内网、userinfo 开放重定向 | 40008 |
| 节点数上限（导航响应） | ≤ `navigationNodeLimit`（默认 200） | —— |

发布成功后生成布局快照（版本 + JSON + SHA-256 checksum），并写 `layout_version`。

### 4.3 可见性语义

1. 场景级规则（`node_id = 0`）先判定：任一命中 `DENY` → 整个场景不可见；
2. 节点级：同一节点 `DENY` 优先；存在任一 `ALLOW` 时按**白名单**语义（未显式允许即不可见）；
3. 节点被拒绝 → 整棵子树隐藏；
4. 入口再按 `permission_code` 过滤；为空的入口登录即可见；
5. 无任何规则 → 默认全体登录用户可见。

### 4.4 入口 URL 安全

`SceneValidator#validateTargetPath` 规则：

- 协议白名单（默认 `http,https`），平台内路径必须以单个 `/` 开头；
- 拒绝 `javascript:` / `data:` / `file:` 等危险协议；
- 拒绝协议相对 `//host`；
- 拒绝 userinfo（`http://user@host`）造成的开放重定向；
- 拒绝内网/回环：`127.*`、`10.*`、`172.16–31.*`、`192.168.*`、`169.254.*`、
  `localhost`、`.internal`、`.local`。

---

## 5. ER 图

```text
sc_scene (场景聚合根)
  │ 1
  ├── N  sc_scene_node (节点树, parent_key 自关联)
  │        │ 1
  │        └── 0..1 sc_node_binding (节点入口)
  ├── N  sc_visibility (节点级 node_id > 0 / 场景级 node_id = 0)
  └── N  sc_layout_version (发布快照)
```

| 表 | 关键字段 | 索引 |
|---|---|---|
| `sc_scene` | `scene_key` / `name` / `status` / `layout_version` / `published_at` | `uk_sc_scene_key(tenant_id, scene_key)`、`idx_sc_scene_status(tenant_id, status, update_time)` |
| `sc_scene_node` | `node_key` / `parent_key` / `node_type` / `coordinate_x` / `coordinate_y` / `coordinate_z`(M3) / `sort_no` / `status` | `uk_sc_node_key(tenant_id, scene_id, node_key)`、`idx_sc_node_tree(tenant_id, scene_id, parent_key, sort_no)` |
| `sc_node_binding` | `entry_type` / `target_path` / `permission_code` / `enabled` | `uk_sc_binding_node(tenant_id, node_id)` |
| `sc_visibility` | `node_id` / `subject_type` / `subject_id` / `effect` | `idx_sc_visibility_scene(tenant_id, scene_id, node_id)` |
| `sc_layout_version` | `version` / `layout_json` / `checksum` | `uk_sc_layout_version(tenant_id, scene_id, version)` |

> 所有业务表带 `tenant_id`，由 starter 租户行插件自动追加与填充；Flyway 基线脚本
> 禁止 `DROP` / `TRUNCATE` / `DELETE`，种子使用 `INSERT ... WHERE NOT EXISTS`（幂等）。

---

## 6. API 契约（以代码为准）

| 方法 | 路径 | 用途 | 权限 |
|---|---|---|---|
| `GET` | `/scene/meta` | 服务能力与预算参数（`threeDEnabled` / 节点上限 / 深度 / 导航上限） | 登录即可 |
| `GET` | `/scene/navigation` | 当前用户可见场景导航（已过滤节点/入口） | 登录即可 |
| `GET` | `/scene/page` | 场景分页（管理视图） | `sys:scene:list` |
| `GET` | `/scene/{id}` | 场景详情（场景 + 节点 + 绑定 + 可见性 + 最近快照） | `sys:scene:list` |
| `POST` | `/scene` | 新建场景（草稿） | `sys:scene:add` |
| `PUT` | `/scene/{id}` | 编辑场景（已发布场景禁止改 `sceneKey`） | `sys:scene:edit` |
| `POST` | `/scene/{id}/publish` | 发布（校验并生成快照） | `sys:scene:publish` |
| `POST` | `/scene/{id}/offline` | 下线 | `sys:scene:offline` |
| `POST` | `/scene/{id}/node` | 新增节点 | `sys:scene:node:manage` |
| `PUT` | `/scene/{id}/node/{nodeId}` | 编辑节点 | `sys:scene:node:manage` |
| `DELETE` | `/scene/node/{nodeId}` | 删除节点（有子节点拒绝） | `sys:scene:node:manage` |
| `PUT` | `/scene/node/{nodeId}/binding` | 设置入口绑定（重复保存即覆盖） | `sys:scene:binding:manage` |
| `PUT` | `/scene/{id}/visibility` | 整体替换可见性规则（请求体 `{ rules: [...] }`） | `sys:scene:visibility:manage` |

**与规划草案的差异（已实现并以此为准）**：

1. 增加 `GET /scene/page`（管理列表需要分页）；
2. 下线使用独立权限 `sys:scene:offline`（避免发布权限隐含下线能力）；
3. 增加 `PUT /scene/{id}/node/{nodeId}`（编辑节点；规划只写了新增/更新混合接口）；
4. 可见性请求体包裹为 `{ rules: [...] }`，使集合校验生效；
5. `GET /scene/{id}/stats` 留待 M3（本波不提供）。

**统一响应**：`Result{code=0,message,data}`；分页为 `PageResult{records,total,current,size}`。
业务错误码见 `SceneErrorCode`（40001–40011 / 40401–40402 / 40901–40903）。

---

## 7. 权限、角色与菜单

| 权限 ID | 权限码 | 说明 |
|---|---|---|
| 323 | `sys:scene:list` | 查看场景管理数据 |
| 324 | `sys:scene:add` | 新建场景 |
| 325 | `sys:scene:edit` | 编辑场景 |
| 326 | `sys:scene:publish` | 发布场景 |
| 327 | `sys:scene:offline` | 下线场景 |
| 328 | `sys:scene:node:manage` | 管理节点 |
| 329 | `sys:scene:binding:manage` | 管理入口绑定 |
| 330 | `sys:scene:visibility:manage` | 管理可见性 |

- 角色：**25107 `scene_admin`**（平台 + 业务租户复制，MD5 业务键授权 ID），与 `tenant_admin` 共享 8 项权限；
- 菜单：**53 `城池导航`（DIR，无权限码，登录可见）+ 54 场景管理 + 55 入口绑定 + 56 可见性**；
- 331–336 与菜单 57–62 保留，避免与后续增量冲突；
- 迁移脚本：`whale-service-auth-starter/src/main/resources/db/migration-5.77-scene.sql`（幂等）。

---

## 8. 2D 呈现与性能预算

| 项 | 2D 基线（M2） |
|---|---|
| 渲染 | 纯 CSS 绝对定位 + 网格背景；无 WebGL / Canvas / 3D 资源 |
| 导航响应 | 只返回可见节点与入口；目标 ≤ 200KB（不含 3D），节点上限 200 |
| 首屏 | 导航与地图同步渲染；目标 ≤ 1.5s（本地基线，待产品确认） |
| 坐标 | `x / y ∈ [0,1000]`，前端换算百分比；缺坐标按序号网格兜底 |
| 可访问性 | 节点为原生 `<button>`（Tab 聚焦 + Enter/Space）；提供键盘友好的列表视图 |
| 降级 | 默认即 2D；`threeDEnabled=false` 时前端不加载任何 3D 依赖 |

**M3 3D 启用条件（四条件同时满足）**：

1. 出现真实 3D 可视化需求并确认验收指标（不是“为了 3D 而 3D”）；
2. WebGL/GPU 能力探测与性能预算达标：首屏 3D 资源 ≤ 8MB、桌面首帧 ≤ 5s、
   连续 5s FPS < 30 自动降级 2D；
3. 3D 素材来源、许可证、贴图/模型体积登记完成，未授权素材不上线；
4. 降级与无障碍验收通过（低配/移动端仍可 2D 访问，键盘可操作）。

> 未满足条件时保持 2D，不强行上线；3D 启用只改前端渲染层与配置，不改本服务 API 契约。

---

## 9. 配置项

| 变量 | 默认 | 说明 |
|---|---|---|
| `SERVER_PORT` | 8097 | 服务端口 |
| `WHALE_SCENE_MAX_NODES_PER_SCENE` | 200 | 单场景节点上限（发布校验） |
| `WHALE_SCENE_MAX_NODE_DEPTH` | 5 | 节点树深度上限 |
| `WHALE_SCENE_NAVIGATION_NODE_LIMIT` | 200 | 导航接口返回节点上限 |
| `WHALE_SCENE_3D_ENABLED` | false | M3 前固定 false；前端以 `/scene/meta` 读取 |
| `WHALE_SCENE_ALLOWED_SCHEMES` | http,https | 入口外链协议白名单 |

---

## 10. 测试与验证

| 项 | 结果 |
|---|---|
| 单元/集成测试 | 39 项（Validator 8、布局快照 2、导航服务 8、应用服务 15、Flyway 脚本 2、ArchUnit 4） |
| 覆盖率 | 行 446/713 ≈ 62.6%，分支 223/334 ≈ 66.8%（`-Pcoverage-gate` 行 ≥40% / 分支 ≥30%） |
| 前端 | `pnpm --filter whale-scene lint`（max-warnings 0）与 `build` 通过 |
| 网关/实机冒烟 | 待环境（需 MySQL `bluewhale_scene` + Nacos）；路由与 internal-deny 已配置 |

---

## 11. 待确认

| # | 问题 | 当前处理 |
|---|---|---|
| D3 | 可见性是否支持部门粒度？ | 表结构保留 `DEPT`，解析暂仅按角色/用户/全体生效；部门后置 |
| D4 | 外部系统域名白名单？ | 当前只允许 http/https + 拒绝内网；域名白名单待运营登记后收窄 |
| D5 | 3D 素材来源与许可证？ | M3 前必须登记，未登记不接入 |
| —— | 访问统计 `/scene/{id}/stats` | M3 可选，本波不提供 |
