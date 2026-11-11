# Wave 6.17 · 权限中心用户/租户配置 + 告警按用户偏好（计划 v2，已按审阅意见修订）

> 状态：**待审（v2）**。v1 方案把偏好中心放 whale-message，2026-11-11 审阅意见：
> **租户配置与用户配置应集中到权限中心（auth），建通用用户配置表存放各种用户配置**。
> v2 据此重写：auth 提供通用配置存储，通知偏好降级为第一个消费场景。

## 一、现状与问题

- auth（权限中心）目前只有身份域表：`sys_user` / `sys_tenant` / `sys_role*` / `sys_menu` /
  `sys_permission` / `sys_tenant_password_policy` 等，**没有任何用户配置表、租户配置表**。
- 用户级偏好散落各服务：`whale-scene` 自建 `user_preference`（V3，场景专属）；
  本计划的通知偏好若按 v1 落 whale-message，就是第二处分散——正是审阅意见要避免的。
- 租户级配置同样分散：配额在 `sys_tenant.quota_json`、口令策略独立表、其余无处安放。

**v2 原则**：权限中心是身份与配置的唯一权威。auth 提供**通用、不解释业务语义**的
配置存储（key-value + JSON 值），各业务域按 `config_key` 前缀认领自己的命名空间并
自行解释（`notify.*` 归通知、`ui.*` 归前端、后续场景域可收敛 scene 自建表）。

## 二、数据模型（bluewhale_auth，迁移脚本 `migration-6.17-user-tenant-config.sql`，幂等）

```sql
-- 用户配置（通用；一个 key 一行，值为 JSON，结构由 config_key 所属业务域定义）
CREATE TABLE IF NOT EXISTS sys_user_config (
  id           BIGINT       NOT NULL PRIMARY KEY,
  tenant_id    BIGINT       NOT NULL,
  user_id      BIGINT       NOT NULL,
  config_key   VARCHAR(128) NOT NULL,            -- 前缀分域：notify.twin.alert / ui.theme / ...
  config_value TEXT         NOT NULL,            -- JSON；auth 只存取不解释
  updated_by   BIGINT       NULL,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_config (tenant_id, user_id, config_key)
) COMMENT '用户配置';

-- 租户配置（通用；管理员维护，作为该租户用户的默认值层）
CREATE TABLE IF NOT EXISTS sys_tenant_config (
  id           BIGINT       NOT NULL PRIMARY KEY,
  tenant_id    BIGINT       NOT NULL,
  config_key   VARCHAR(128) NOT NULL,
  config_value TEXT         NOT NULL,
  updated_by   BIGINT       NULL,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant_config (tenant_id, config_key)
) COMMENT '租户配置';
```

**读取优先级（约定）**：`用户配置 > 租户配置 > 消费方内置默认`。auth 提供合并读取端点，
不做强制——业务域可以只读其中一层。`config_key` 用反域名点号前缀
（`notify.twin.alert`、`notify.ops.alert`、`ui.theme`），auth 侧仅校验
`^[a-z][a-z0-9-]*(\.[a-z0-9][a-z0-9-]*)+$` 与 value 是合法 JSON、大小 ≤ 8KB。

## 三、接口设计

### 用户端（经网关 `/auth/v1/**` 转发，**自助管理自己的配置，无需新权限码**）

| 方法 | 路径（auth 裸路径） | 说明 |
|---|---|---|
| GET | `/user-config?prefix=notify.` | 当前用户配置列表（可按前缀过滤） |
| PUT | `/user-config` | upsert 当前用户一条配置 `{configKey, configValue}` |
| DELETE | `/user-config/{configKey}` | 删除（回到租户默认/消费方默认） |

### 租户端（租户管理员维护默认值；**新权限码必须种子化**）

| 方法 | 路径 | 权限码 |
|---|---|---|
| GET | `/tenant/config?prefix=` | `sys:tenant:config:list` |
| PUT | `/tenant/config` | `sys:tenant:config:edit` |

权限码进 auth 迁移 SQL（sys_permission 种子 + tenant_admin 角色授予），并纳入
frontend-perm-mapping 门禁核对。

### 内网端点（服务间读取；`/user/internal/**`、`/tenant/internal/**` 白名单已存在，零白名单改动）

```
POST /user/internal/config/batch-get      X-Internal-Token + X-Tenant-Id
{ "userIds": [101,102], "keys": ["notify.twin.alert"], "withTenantDefaults": true }
→ { "items": [ { "userId": 101, "configKey": "notify.twin.alert",
                "value": {...}, "source": "USER|TENANT|NONE" }, ... ] }
```

## 四、通知偏好作为第一个消费场景（语义归 whale-message）

**存储在 auth（集中），解释语义在 message（一处），消费方只调一个端点**：

1. 通知偏好的值结构由 message 定义并写入约定文档：
   `notify.<bizType>` = `{ "channels": ["EMAIL","INAPP"], "minLevel": "WARN",
   "quietStart": "22:00", "quietEnd": "07:00" }`；
2. message 保留内网解析端点 `POST /message/internal/preferences/resolve`
   （v1 设计不变）：内部调 auth `batch-get`（含租户默认合并）→ 应用通知语义
   （静默窗口左闭右开/可跨零点/非法 fail-open、渠道归一 `INAPP/IM→INBOX`、
   站内信不受 minLevel 限制）→ 返回每用户生效渠道与是否被静默；
3. **无配置 = 默认全渠道放行**（fail-open）：auth 或偏好任何一环不可用时，行为
   与现状一致，接入方零风险；
4. 前端「通知偏好」设置页读写的是 auth `/user-config`（通用接口），页面只是
   把 JSON 结构可视化为渠道开关/静默时段/阈值表单。

## 五、接入顺序

- **6.17a** auth：两张表 + 用户端/租户端/内网端点 + 权限码种子 + 单测；
- **6.17b** message：resolve 端点接 auth batch-get + 通知语义 + 单测；
- **6.17c** twin：告警外发在「门槛→静默→冷却」之后、send 之前按 resolve 过滤
  （偏好只能收紧；critical 穿透看全局开关；解析失败按默认放行并 WARN；
  SSE→落库→外发铁律不动）；
- **6.17d** 前端：基座个人中心「通知偏好」页（读写 `/auth/v1/user-config`）+
  admin 租户配置页（可选，本期可缓）；
- **（待办，另开波次）** scene 自建 `user_preference` 收敛到 auth 通用配置。

## 六、门禁债务清单（实施时逐项过）

1. internal-boundary 门禁：两个新内网端点单段 internal、令牌覆盖、无 `@RequirePermission`；
   auth 内网端点已由既有 permitAll 白名单覆盖（`/user/internal/**`、`/tenant/internal/**`）；
2. frontend-perm-mapping 门禁：`sys:tenant:config:*` 先种后引；设置页若挂 admin 动态
   路由须同步 `COMPONENT_MAP`（推荐挂基座静态菜单，走 `sys:tenant:config:list` 可见性）；
3. 迁移版本不撞号；auth 的 `init.sql = 全部迁移之和` 语义保持（迁移测试有断言）；
4. 用户配置表在租户删除/成员移除时的清理策略：随 `sys_user_tenant` 关系删除
   （属主数据生命周期与用户一致，避免孤儿行）。

## 七、遗留开放问题（v1 第 1/4 问已随方案收敛，剩余两项）

1. **设置页位置**：v2 默认基座个人中心（自助配置无需权限码）；admin 租户默认值页
   本期缓做——如你希望本期一起做，请说明。
2. **6.17c 之外是否本期顺带做 scene 收敛**：建议另开波次（涉及 scene 表迁移与
   数据搬迁，风险独立）。

## 八、验收口径

- auth：配置 CRUD 实机验证（用户自助 + 租户默认 + batch-get 合并优先级正确）；
- message：resolve 语义单测（静默/阈值/渠道归一/fail-open）+ 实机 resolve；
- twin：外发实机验证——某收件人设静默 → 告警触发 → 该人无外发但落库与 SSE 正常；
- 全部门禁 PASS、auth/message/twin 全量单测绿、`git status` 干净、推送完成。
