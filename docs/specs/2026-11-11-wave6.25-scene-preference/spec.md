# Wave 6.25　scene 用户偏好收敛到 auth 统一配置

> 状态：**待评审**（决策点见 §8）
> 关联：Wave 6.17 通知偏好（auth 为配置唯一权威）、Wave 6.23 twin 告警偏好链路
> 作者：AI 起草，待用户拍板

## 1. 背景

同一语义「用户偏好」当前存在两套真相：

| 场景 | 存储 | 读取方 | 状态 |
|---|---|---|---|
| 通知偏好（渠道/静默/门槛） | auth `sys_user_config`，键 `notify.<bizType>` | whale-message `NotifyPreferenceResolver` | Wave 6.17 已收敛 |
| 场景视图偏好（2D/3D + 默认场景） | scene 自建 `sc_user_preference` | whale-scene `ScenePreferenceAppServiceImpl` | **本次收敛对象** |

若放任，assistant / agent / twin 各自再建一套偏好表只是时间问题，语义漂移与「改了这一处、那处不生效」会持续放大。

### 1.1 现状核实（本次实测，决定改造窗口）

| 项 | 实测结果 | 含义 |
|---|---|---|
| `bluewhale_scene` 库 | **空库**：无任何表、无 `flyway_schema_history` | scene 服务从未启动过，V3 迁移从未执行 |
| `sc_user_preference` 表 | **不存在**（全库 `information_schema` 无匹配） | **存量数据 0 行，迁移成本为 0** |
| `sys_user_config` 表 | 存在，当前 0 行 | auth 侧无历史数据负担 |
| `bw-scene` 容器 | 不存在（`profiles: ["aiot","ext"]`，jar 已在 `deploy/backend/jars/scene`） | 未部署 |
| 前端调用点 | 仅 `whale-scene/src/api/scene.ts` 的 `GET/PUT /scene/preference`；`Scene3DPage.tsx` 消费 `defaultSceneKey` | **契约保持不变即可前端零改动** |

结论：**这是收敛的最佳也是最后的低成本窗口**（一旦 scene 上线产生真实偏好，就要做跨库数据搬迁）。

## 2. 目标

1. 场景偏好存储统一到 auth `sys_user_config`，成为唯一真相。
2. scene 保留全部**业务语义**校验（viewMode 枚举、默认场景必须已发布），auth 只存取不解释（与 6.17 的分工一致）。
3. 对外契约 `GET/PUT /scene/preference` **完全不变**（前端零改动）。
4. 无配置时的行为与现状一致：默认 2D、无默认场景。

## 3. 非目标

- 不新建「偏好服务」，不引入独立偏好库。
- 不改 message 的通知偏好解析链路（`/message/internal/preferences/resolve` 的语义解释权仍在 message）。
- 不改前端任何代码。
- 不做租户级场景偏好默认值（`sys_tenant_config` 侧能力已具备，本次只接用户级）。

## 4. 存储契约

| 项 | 取值 |
|---|---|
| 配置键 | `scene.view`（符合 auth `ConfigKeyValidator` 的反域名小写点号口径，≥2 段） |
| 配置值 | JSON：`{"viewMode":"2D","defaultSceneKey":"city-wall"}` |
| `viewMode` | `2D` / `3D`（大写，与现有领域常量一致；`three-d-enabled=false` 时前端仍可存 3D，是否可用由 scene 侧开关决定） |
| `defaultSceneKey` | 已发布场景的 `scene_key`，允许 `null`/缺省 |
| 大小 | 实测该 JSON < 100 字节，远低于 auth 上限 8192 |
| 无记录语义 | **= 默认值**（2D + 无默认场景）。因此「清空偏好」= 删除该配置行，不写空 JSON |

## 5. 读写通道

### 5.1 读（scene → auth）

```
POST {authBaseUrl}/user/internal/config/batch-get
Headers: X-Internal-Token: <token>, X-Tenant-Id: <tenantId>
Body: {"tenantId":1,"userIds":[123],"keys":["scene.view"]}
```

- 复用既有端点，**无需改网关白名单**（`/user/internal/**` 已在 auth `permitAll` 且被网关 `internal-deny` 拦截）。
- 响应 `source=NONE` 或 `value=null` → 按默认偏好返回。
- **失败策略 fail-open**：网络/解析/业务码失败一律返回默认偏好并记 WARN。理由：偏好是旁路，读不到不该让用户进不去场景页（与 message 侧口径一致）。

### 5.2 写（scene → auth，本期新增）

```
POST {authBaseUrl}/user/internal/config/set
Headers: X-Internal-Token: <token>, X-Tenant-Id: <tenantId>
Body: {"tenantId":1,"userId":123,"configKey":"scene.view","configValue":"{...}","operatorId":123}
```

auth 侧新增（`UserConfigInternalController` 加一个方法）：

- 复用 `ConfigKeyValidator.requireValidKey/requireValidValue` 做存储安全校验；
- 复用 `UserConfigAppService.upsert(tenantId, userId, SaveUserConfigCommand)` 落库（**不新增写逻辑**）；
- 内网端点**不加** `@RequirePermission`（服务间调用无权限码，加了必然 403）。

- **失败策略 fail-closed**：写失败向调用方抛错，scene 返回业务失败，绝不「假成功」（否则用户以为存了，下次进来又变回去）。

### 5.3 为什么写走 scene 转调，而不是前端直连 auth

| 方案 | 问题 |
|---|---|
| 前端直连 auth `PUT /user/config` | 场景必须已发布、viewMode 枚举等**业务规则无人校验**，前端可绕过；且前端要改 |
| **scene 转调 auth 内网写（选定）** | 校验留在 scene，契约不变，前端零改动 |

## 6. 改造清单

### 6.1 auth（新增内网写端点）

| 文件 | 改动 |
|---|---|
| `whale-service-auth-interfaces/.../controller/UserConfigInternalController.java` | 加 `POST /set`，调 `UserConfigAppService.upsert` |
| `whale-service-auth-interfaces/.../dto/ConfigSetRequest.java` | 新增 record（tenantId/userId/configKey/configValue/operatorId，带 `@Valid`/`@NotNull`） |
| 测试 | `UserConfigInternalControllerTest`：合法写入、非法 key 400、缺 token 401（由既有 `InternalTokenFilter` 覆盖） |

### 6.2 scene（改为走 auth）

| 文件 | 改动 |
|---|---|
| `application/service/impl/ScenePreferenceAppServiceImpl.java` | 改用 `AuthScenePreferenceClient`；保留 viewMode 校验与「场景必须已发布」校验 |
| `support/AuthScenePreferenceClient.java`（新增） | 读（batch-get）+ 写（set），`InternalTokenSupport.restClient()`，JSON 用 **Jackson 3（`tools.jackson`）** |
| `config/ScenePreferenceProperties.java`（新增） | `whale.scene.auth-base-url`（默认 `http://bw-auth:8081`） |
| `interfaces/support/SceneUserContext.java` | 加 `requireTenantId(request)`（取 `WebHeaders.TENANT_ID`，缺失按未认证拒绝——缺租户上下文写库会串租户） |
| `domain/repository/UserPreferenceRepository.java` 等 5 个文件 | **删除**（`UserPreferenceRepository`、`UserPreferenceRepositoryImpl`、`UserPreferenceMapper`、`UserPreferenceEntity`、`domain.model.UserPreference`） |
| 领域常量 | `VIEW_MODE_2D/3D` 与 `isValidViewMode` 迁到 `application/view/ScenePreferenceView` 的静态常量/方法，避免把整个领域对象搬走 |
| `db/migration/V3__user_preference.sql` | 见决策点 D3 |
| 测试 | `ScenePreferenceAppServiceImplTest`：默认值、写入、非法 viewMode、未发布场景、auth 读失败 fail-open |

### 6.3 网关 / 前端

- 网关：**无需改动**（`/scene/**` 路由已存在；auth 内网端点复用既有前缀规则）。
- 前端：**零改动**（`/scene/preference` 契约不变）。

## 7. 验收标准（实机）

| # | 场景 | 期望 |
|---|---|---|
| 1 | `GET /scene/preference`（无配置） | 200 `{viewMode:"2D", defaultSceneKey:null}`；`sys_user_config` 不新增行 |
| 2 | `PUT /scene/preference` `{"viewMode":"3D","defaultSceneKey":"<published>"}` | 200；`sys_user_config` 出现 `scene.view`，值 JSON 正确 |
| 3 | 再 `GET` | 返回 3D + 该场景键（跨请求持久） |
| 4 | `PUT` 未发布/不存在的场景键 | 400 `PREFERENCE_SCENE_INVALID`，配置行不变 |
| 5 | `PUT` `viewMode:"4D"` | 400 `PREFERENCE_INVALID`，配置行不变 |
| 6 | 直接删除 `scene.view` 行后 `GET` | 回默认（验证「无配置 = 默认」而非报错） |
| 7 | 停掉 auth 后 `GET` | 200 默认值 + WARN 日志（fail-open） |
| 8 | 直接 `POST /user/internal/config/set` 无 `X-Internal-Token` | 401 |
| 9 | 同端点经网关访问 | 403（`internal-deny` 生效） |
| 10 | 造数还原 | `sys_user_config` 与 scene 相关表复核归零 |

单测：auth 1 组 + scene 1 组；Checkstyle 0 违规；`check-internal-boundary.py` 通过（新增 1 个内网端点后计数更新）。

## 8. 决策点（待确认）

### D1　配置键命名
- **A（推荐）`scene.view`** —— 与 `notify.twin-alert` 同族命名习惯，域名在前，前缀过滤 `prefix=scene` 可批量取。
- B　`scene.preference` —— 语义更宽，但后续若加 `scene.thumbnail` 之类会显得前缀不一致。

### D2　写通道
- **A（推荐）scene 转调 auth 内网写** —— 业务校验留在 scene，前端零改动。
- B　前端直连 auth `PUT /user/config` —— 校验缺失、前端要改，不推荐。

### D3　`sc_user_preference` 存量表怎么处理
- **A（推荐）保留 V3 迁移不动，代码不再引用** —— Flyway 历史不可变是惯例；本机从未建表，留着只是一张空表（若某环境已跑过 V3 也零数据风险）。代价：库里留一张废弃表。
- B　修改 V3 让表不再创建 —— 若任何环境执行过 V3，checksum 校验会直接让 scene 启动失败。
- C　新增 V4 `DROP TABLE IF EXISTS sc_user_preference` —— 干净，但若某环境跑过 V3 且有数据会丢；且跨库搬迁无法在单库 Flyway 内完成。

### D4　读取失败策略
- **A（推荐）fail-open**：auth 不可用时返回默认偏好 + WARN —— 与 message 通知偏好口径一致，偏好不该阻塞进页面。
- B　fail-closed：500 报错 —— 语义更「严格」，但 auth 抖动会导致场景页整体打不开。

## 9. 实施顺序

1. auth 内网写端点 + 单测（独立可验收）
2. scene 客户端 + 服务改造 + 删旧仓储 + 单测
3. 起 `bw-scene`（`--profile aiot`）+ auth/mysql，跑 §7 全量验收
4. 造数还原、镜像与制品刷新、文档与 memory 更新、提交推送

---

## 10. 实现记录（2026-11-11 20:00-20:15，决策点按推荐项确认）

### 决策落地

| 决策 | 采纳 | 说明 |
|---|---|---|
| D1 配置键 | **`scene.view`** | 与 `notify.twin-alert` 同族；`prefix=scene` 可批量取 |
| D2 写通道 | **scene 转调 auth 内网写** | 业务校验留在 scene，前端零改动 |
| D3 存量表 | **保留 V3 迁移，代码不再引用** | 表仍在（Flyway 建出空表），`sc_user_preference` 实测 0 行 |
| D4 读失败 | **fail-open 返回默认 2D + WARN** | 与 message 通知偏好口径一致 |

### 落地清单

| 层 | 产物 |
|---|---|
| auth | `ConfigSetRequest`（record，`@NotNull/@NotBlank`）、`UserConfigInternalController#set`（校验 `tenantId` 与 `X-Tenant-Id` 一致后调 `UserConfigAppService.upsert`，复用 `ConfigKeyValidator` 做存储安全校验） |
| scene | `AuthScenePreferenceClient`（读 batch-get / 写 set，Jackson 3，读 fail-open、写 fail-closed）、`SceneViewModes`（枚举常量与校验，承接原 `UserPreference`）、`SceneProperties.authBaseUrl`、`SceneUserContext.requireTenantId`、`ScenePreferenceAppServiceImpl` 重写 |
| 删除 | `domain.model.UserPreference`、`UserPreferenceRepository`、`UserPreferenceRepositoryImpl`、`UserPreferenceMapper`、`UserPreferenceEntity`（5 个文件） |
| 编排 | `bw-scene` 注入 `WHALE_SCENE_AUTH_BASE_URL=http://bw-auth:8081`；auth/scene 补 `WHALE_INTERNAL_TOKEN: "${WHALE_INTERNAL_TOKEN:-}"`（默认空 = 行为不变） |

### 实现期踩坑

1. **`ErrorCode.INTERNAL_ERROR` 不存在** —— 项目只有 `SYSTEM_ERROR(500)`，改用后者。
2. **`maven-jar-plugin` 跳过重打 jar**：`interfaces` 作为依赖打进 `BOOT-INF/lib`，不算 starter 的输入，
   `install` 后 jar 时间戳与大小均未变，**里面仍是旧 `interfaces`**（新端点不存在）。
   判定方法：`unzip -p app.jar BOOT-INF/lib/whale-service-auth-interfaces*.jar | grep ConfigSetRequest`。
   修复：先 `rm` 旧 jar 再 `package`。
3. **dev 环境内网令牌 fail-open**：`InternalTokenFilter` 在令牌未配置时直接放行（`InternalTokenGuard` 只告警），
   因此「直连无 token 应 401/403」在默认 dev 编排下**不成立**。正确验证方式是临时注入令牌重启再测
   （见 §11 第 8 项）。
4. **编排从未引用内网令牌**：`docker-compose.services.yml` 全仓无 `WHALE_INTERNAL_TOKEN`，
   意味着非 dev 部署必然被 `InternalTokenGuard` 拒绝启动且无配置入口。本次补上可注入项（默认空），
   auth/scene 先落地，其余服务后续按需补。

### 单测

- `ScenePreferenceAppServiceImplTest` 7 例（默认值、存储值透传、保存规范化、空命令、非法枚举、未发布场景、超长键）
- `AuthScenePreferenceClientTest` 7 例（解析、未配置、auth 不可用 fail-open、脏 JSON、非法枚举回落、写入规范化、业务码非 0 fail-closed）
- `UserConfigInternalControllerTest` 5 例（写入透传、租户不一致拒绝、缺租户上下文拒绝、空 key 校验失败、batch-get 形状）
- 合计 19/19 通过；三模块 Checkstyle 0 违规

## 11. 验收结果（实机，bw-scene + bw-auth + MySQL）

| # | 场景 | 期望 | 实测 |
|---|---|---|---|
| 1 | `GET /scene/preference` 无配置 | 2D + null | ✅ `{"defaultSceneKey":null,"viewMode":"2D"}` |
| 2 | `PUT` 3D + 已发布场景 | 200 code=0 | ✅ 落 `sys_user_config`：`2119069480070197249 / scene.view / {"viewMode":"3D","defaultSceneKey":"bluewhale-city"}` |
| 3 | 再 `GET` | 跨请求持久 | ✅ 返回 3D + bluewhale-city |
| 4 | `PUT` 未发布场景 `e2e-draft` | 40013 | ✅ `code=40013 默认场景不存在或未发布` |
| 5 | `PUT` `viewMode:"4D"` | 40012 | ✅ `code=40012` |
| 5b | 失败用例后复核 | 原值不变 | ✅ 仍 3D |
| 6 | 删除配置行后 `GET` | 回默认 | ✅ 200 + 2D |
| 7 | **停 auth** 后 `GET` | 200 + 默认（fail-open） | ✅ 200 + 2D，日志：`场景偏好读取失败（按无配置处理）... Host is unreachable` |
| 8 | 配令牌后直连 `POST /user/internal/config/set` **无令牌** | 403 | ✅ `403 内网接口令牌缺失或不正确`；**带令牌** → `code=0` |
| 8b | 配令牌后 scene 读写 | 正常（出站自动带令牌） | ✅ GET 3D / PUT 2D 均 code=0 |
| 9 | 经网关调用内网端点 | 403 | ✅ `403 内网接口不允许外部访问`（deny 先于鉴权，无需登录态） |
| 10 | 造数还原 | 归零 | ✅ `sc_scene=1`（仅原有 bluewhale-city）、`sc_user_preference=0`、`sys_user_config=0`、e2e 行 0 |

**关键证据**：`sc_user_preference` 全程 0 行 —— 代码确实不再写自建表，偏好只落 auth。

内网门禁：`check-internal-boundary.py` 120 → **121 条端点**（`user` 前缀 5 → 6），PASS。

### 环境已还原

`.env` 临时令牌已删除、auth/scene 容器已按空令牌重建（dev fail-open 原状）、`bw-scene` 已停（aiot profile）、
`bluewhale_scene` 的 `audit_log`/`api_log` 本批测试行已清理。

### 未做

- 其余业务服务的 `WHALE_INTERNAL_TOKEN` 注入（本次只补了 auth/scene 两处，其余按需）
- 租户级场景默认值（`sys_tenant_config` 能力已具备，本次只接用户级）
- 前端 3D 开关联动（`three-d-enabled` 仍由 scene 侧配置控制，前端未读）
