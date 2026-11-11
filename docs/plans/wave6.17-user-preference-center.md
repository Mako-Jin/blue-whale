# Wave 6.17 · 用户偏好中心 + 告警按用户偏好（计划，待审）

> 状态：**待审**。本文档只定方案，不动代码。审阅通过后按 6.17a → 6.17b →（可选）6.17c 实施。

## 一、现状盘点（为什么要做）

平台现有三条通知链路，互不复用，「用户偏好」无处落地：

| 链路 | 收件人来源 | 渠道 | 用户可控性 |
|---|---|---|---|
| whale-alert（运维告警） | **yml 静态配置** `email.to` / `sms.to` | EMAIL/SMS/WEBHOOK | 无（改收件人要改配置重启） |
| whale-twin（孪生告警外发） | 场景级收件人配置（人/部门，经 auth 通讯录检索） | 复用 `/message/internal/send` | 场景管理员配，**无个人偏好** |
| whale-message（消息服务） | 调用方传入 `recipients[]` | EMAIL/SMS/IM/INAPP（站内信=INAPP，归一 INBOX） | 无 |

问题：用户想「下班后不收邮件提醒、critical 才收短信、营销类站内信直接关」——
现在没有任何一张表、一个接口能表达这件事。每接入一个新通知场景就要重写一遍收件人管理。

## 二、目标与边界

**目标**：一个用户级通知偏好中心，落在 **whale-message**（它已有多渠道投递、幂等、
回执、INBOX 归一，是新域的最自然宿主；不新建服务——8G VM 编排只减不增）。

**MVP 边界（6.17a+b）**：
- 偏好维度：`租户 × 用户 × 业务类型(bizType)`；
- 偏好内容：**渠道开关集**（EMAIL/SMS/IM/INAPP 子集）、**静默时段**（HH:mm，语义与 twin 对齐）、**最低级别阈值**（低于阈值不发外发，站内信不受阈值限制——站内信是落库不是打扰）；
- 消费方：twin 告警外发（6.17b 必做）、alert 用户组收件人（6.17c 可选）；
- **不做**：消息模板订阅管理、定时摘要、渠道账号绑定（邮箱/手机号仍以 auth 用户资料为准）。

## 三、数据模型

`bluewhale_message` 新表 `user_notify_preference`（迁移脚本 `migration-6.17-user-preference.sql`，幂等）：

```sql
CREATE TABLE IF NOT EXISTS user_notify_preference (
  id           BIGINT       NOT NULL PRIMARY KEY,
  tenant_id    BIGINT       NOT NULL,
  user_id      BIGINT       NOT NULL,
  biz_type     VARCHAR(64)  NOT NULL,            -- 如 twin.alert / ops.alert / crm.reminder
  channels     VARCHAR(128) NOT NULL,            -- 逗号分隔启用渠道，空串=全关
  min_level    VARCHAR(16)  NULL,                -- INFO/WARN/ERROR，NULL=不设阈值
  quiet_start  VARCHAR(5)   NULL,                -- HH:mm，NULL=不静默
  quiet_end    VARCHAR(5)   NULL,
  updated_by   BIGINT       NULL,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_pref (tenant_id, user_id, biz_type)
) COMMENT '用户通知偏好';
```

语义约定（全部对齐 twin 已验证的规则，Wave 6.10/6.11 踩过的坑不重踩）：
- 静默窗口 `HH:mm` 归一、**左闭右开**、可跨零点、非法/`start==end` **fail-open**（不静默）；
- **无偏好记录 = 默认全渠道放行**（fail-open）：偏好中心宕机/未配置时行为与现状完全一致，接入方零风险；
- 渠道归一复用既有规则 `INAPP/IM → INBOX`。

## 四、接口设计

### 用户端（经网关，admin/基座「通知偏好」设置页用）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/message/preferences?bizType=` | 当前用户偏好（无记录返回默认值） |
| PUT | `/message/preferences` | 保存当前用户偏好（upsert，按 bizType） |
| GET | `/message/preferences/biz-types` | 可订阅业务类型枚举（服务端写死+配置扩展） |

权限码种子（**必须进 auth 迁移 SQL**，frontend-perm-mapping 门禁会查前端引用）：
`sys:message:preference:view` / `sys:message:preference:edit`（普通用户可自助编辑自己的偏好，`view` 控制入口可见）。

### 内网解析端点（供 twin / alert 过滤）

```
POST /message/internal/preferences/resolve
X-Internal-Token: ...
{
  "tenantId": 1,
  "bizType": "twin.alert",
  "userIds": [101, 102],
  "defaults": { "channels": ["INAPP"], "minLevel": null }   // 调用方声明默认行为
}
→ { "items": [ { "userId": 101, "channels": ["EMAIL","INAPP"],
                "quiet": false, "suppressed": false }, ... ] }
```

- 命名 `/message/internal/**` 单段——过 internal-boundary 门禁（网关 `/*/internal/**` 收口、`InternalTokenFilter` 令牌、message 非 auth 无 Spring Security 层）；
- **内网控制器不得标 `@RequirePermission`**（服务间无权限码）；
- 调用方必须带 `X-Tenant-Id`（auth 内网接口教训）；
- 解析是**纯查询**，不改投递态；调用方拿到结果后仍走既有 `/message/internal/send`（幂等键、投递态只前进、MOCKED 不算成功的红线全部不动）。

## 五、接入方案

### 6.17a 偏好中心 + 设置页（本期主体）

1. message：表 + `UserNotifyPreferenceController`（用户端）+ `MessagePreferenceInternalController`（内网 resolve）+ 服务层静默/阈值判断（从 twin 的 `TwinAlertNotifyFilter` 提取语义做单测对齐）；
2. auth：权限码种子迁移脚本 + （如需要）用户邮箱/手机号已在 `/user/internal/directory` 返回，无需改 auth；
3. 前端：基座「个人中心」或 admin 加「通知偏好」页（渠道开关 + 静默时段 + 阈值，按 bizType 分组）；新权限码进种子、前端引用与种子对齐（门禁自查）。

### 6.17b twin 告警外发按偏好过滤

- **插入点**：既有拦截链 `门槛 → 静默 → 冷却` 之后、`/message/internal/send` 之前；
- 对每个收件人调 resolve：按偏好剔除渠道（收件人渠道被剔光 → 该人跳过，不影响他人）、`quiet=true` 的非 critical 收件人跳过（critical 穿透语义与 twin 全局开关保持一致：偏好只能**收紧**，critical 全局开关没开时偏好也开不了）；
- 铁律不动：SSE 帧 → 落库 → 外发；偏好解析失败/超时按**无偏好（默认放行）**处理并 WARN——外发是旁路，绝不阻塞落库。

### 6.17c（可选）alert 收件人升级

yml 静态 `email.to`/`sms.to` 保留兜底；新增可选 `AlertNotifyProperties.UserGroup`（用户目录解析 + 偏好过滤）。运维告警的「人」语义与业务消息不同，是否值得接入待定，默认缓做。

## 六、门禁债务清单（实施时必须逐项过）

1. `check-internal-boundary.py`：新内网端点单段 internal、令牌覆盖、无权限注解；
2. `check-frontend-perm-mapping.py`：新权限码先种后引，设置页组件若走 admin 动态路由须同步 `COMPONENT_MAP` 契约键（如走基座静态菜单则对齐 menu.ts 权限码）；
3. 迁移版本不撞号（合并教训第一杀手）；`init.sql` 与迁移脚本保持「全量=之和」语义；
4. twin 既有 193 项测试不许回归；message 新域单测覆盖静默/阈值/渠道归一/fail-open。

## 七、风险与开放问题（请审阅时拍板）

1. **bizType 枚举由谁定义**：方案为 message 写死枚举 + `MessageChannelProperties` 配置扩展；若希望各服务自助注册则要加注册端点（本期不做）。
2. **admin 设置页 or 基座个人中心**：用户偏好本质是「个人设置」，建议放**基座个人中心**（所有用户可见），admin 只留租户级只读审计页（本期可不做）。请确认。
3. **6.17c 是否本期做**：建议缓做，alert 的 yml 收件人在运维场景里反而更可靠（不依赖用户资料完整性）。
4. 静默时段粒度是「每 bizType 一个窗口」（本方案）还是「全局一个窗口 + bizType 覆盖」——本方案选前者，简单且够用。

## 八、验收口径

- 偏好 CRUD 实机验证：设偏好 → resolve 返回过滤结果 → 恢复无记录 → 默认放行；
- twin 外发实机验证（沿用 6.10 的造数脚本）：某收件人设静默 → 告警触发 → 该人无邮件/站内信外发但**落库与 SSE 正常**；critical 穿透按全局开关；
- 全部门禁 PASS、twin/message 全量单测绿、`git status` 干净、推送完成。
