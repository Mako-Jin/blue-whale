# 论坛服务设计（whale-forum）

> 状态：**设计稿（待评审，未落码）**
> 关联基线：[service-catalog.md](../service-catalog.md)（§一 / §七 端口分段）、[naming-conventions.md](../naming-conventions.md)（§八 域词表须先登记 `forum` 词根与 `fm_` 表前缀）、[network-boundary.md](network-boundary.md)、[security-baseline.md](security-baseline.md)
> 关联需求：REQ-ARCH-003（网关不剥前缀）、REQ-ARCH-004（命名规范）、REQ-ARCH-005（端口清单）、REQ-SEC-026（内网边界）
> 拟实现位置：`whale-backend/whale-services/whale-forum`、`whale-frontend/platform-apps/whale-forum`
> 参照实现：`whale-party`（先审后发状态机）、`whale-mall`（软删释放唯一键）、`whale-music`（热榜滚动窗口）、`whale-doc`（富文本消毒与 ngram 检索）

---

## 0. 落位速查表（实施前先在此对号入座）

| 项 | 值 | 说明 |
|---|---|---|
| 服务名 | `whale-forum` | 五同铁律：模块目录 == artifactId == spring.application.name == Nacos 服务名 == 容器名 |
| 所属分组 | `whale-services` | 平台社区型服务（与 music/video/scene/mail 同级） |
| 服务端口 | **9011** | **例外登记**：8081–8100 已满员，8101–8199 归前端子应用段（service-catalog §七），故借用业务应用段 9011；须在 §七 登记该例外并注明原因 |
| 对外一级前缀 | `/forum/**` | 网关只转发不剥前缀（REQ-ARCH-003）；**不配 StripPrefix** |
| 内网接口 | `/forum/internal/**` | 网关 internal-deny 403，仅服务间内网直调 |
| 数据库 | `bluewhale_forum` | utf8mb4 / utf8mb4_general_ci，连接参数按规范 |
| 表前缀 | `fm_` | 单数、snake_case；naming-conventions §二/§八 登记域词 |
| 前端子应用 | **8123**，base `/forum` | qiankun 子应用；8117/8118 分别被 mail/预留占用，取 8123 避让 portal 8101–8122 段 |
| 权限号段 | **2330–2359** | 23xx 段已占 2300–2306（CRM 分析）、2313（twin）、2320–2321（PM）；落码前必须过 `scripts/security/scan-id-ranges.py` 负向验证 |
| 菜单号段 | **510–519** | 现有最大 503（商城物流）；跳过 504–509 留给在途批次 |
| Flyway | `V1__baseline.sql` 起 | `audit_log` + `api_log` 两张公共表进 V1 |

---

## 1. 结论速览

| # | 结论 |
|---|---|
| 1 | 论坛是**社区讨论域**：版块 / 帖子 / 楼层回复 / 互动 / 审核 / 检索，一套服务覆盖租户内公开讨论与版块内受限讨论，不拆多服务。 |
| 2 | **回复采用「扁平 + 引用」而非深层树**：楼层号由服务端顺序分配、软删不回收；树形回复在论坛场景写放大严重且分页语义不可控。 |
| 3 | **计数一律不信任**：`reply_count / like_count` 走条件更新维护 + 定时对账 Job 校正，展示值与统计口径分离。 |
| 4 | **先审后发是版块级开关**（默认关）：开启时走 `DRAFT→PENDING→PUBLISHED/OFFLINE` 单向状态机，发布态编辑自动退回 PENDING（复用党政内容口径）。 |
| 5 | 身份只来自网关注入的身份头与 auth 权限码；匿名发帖**不做**——最多「昵称展示匿名」，库内 `user_id` 恒存（审计红线）。 |
| 6 | 富文本入库前服务端白名单消毒（XSS 硬卡口）；附件字节归 `whale-file`、通知归 `whale-message`、@提及解析走 auth 内网通讯录，本库只存引用。 |

---

## 2. 职责边界

| 拥有 | 不拥有 |
|---|---|
| 版块（Board）元数据、版块级可见性与审核开关 | 身份 / 角色 / 权限判定（auth 唯一身份源） |
| 帖子（Thread）聚合、编辑历史、浏览计数 | 文件字节（`whale-file`，本库只存 `fileId`） |
| 楼层回复（Post）、引用关系、楼层号 | 消息投递与模板（`whale-message`，只发起内网投递） |
| 点赞 / 收藏 / 表态 | 即时聊天（`whale-im`/`whale-message`）——论坛是异步讨论，不做实时会话 |
| 举报与处置记录（删除 / 屏蔽 / 解封） | 知识库与文档协作（`whale-doc`）——精华帖「转知识库」只做跳转登记，不复制正文 |
| 敏感词命中记录与热榜快照 | 积分商城（`whale-mall`）——论坛积分仅本域记账，兑换属商城 |

上下文关系：`whale-forum → auth` 遵奉者（身份头 + 权限码 + 内网通讯录）；`whale-forum → whale-file` 内网登记/代理下载；`whale-forum → whale-message` 内网投递（回复 / 引用 / @提及 / 审核结果通知）；**不读 `bluewhale_oa` / `bluewhale_erp` 任何表**；门户（`whale-portal`）只读热榜与置顶快照。

---

## 3. 架构与依赖规则

DDD 四层 + Ports & Adapters，与 whale-scene / whale-mail 一致：

```text
interfaces（Controller / 参数校验 / 视图 VO）
    ↓
application（命令服务 / 查询服务 / 消毒 / 计数器 / 热榜快照）
    ↓
domain（聚合根 / 领域行为 / 仓储端口 / 错误码 30xxx）
    ↑
infrastructure（MyBatis-Plus 实体 / Mapper / 仓储实现 / 内网客户端）
```

ArchUnit 固化（`ForumArchitectureTest`，与 scene 同款 4 条规则）：

1. `interfaces` 不得直接依赖 `infrastructure`；
2. `application` 不得依赖 `infrastructure`；
3. `domain` 不得依赖 Spring / MyBatis / Controller DTO；
4. 分层依赖单向 `interfaces → application → domain`。

不引入 CQRS；事务只包住「聚合加载 → 领域行为 → 保存」；计数增减与热榜刷新在事务外异步/定时完成，失败不阻断主流程。

---

## 4. 领域模型与业务规则

### 4.1 聚合与值对象

| 对象 | 类型 | 关键行为 |
|---|---|---|
| `Board` | 聚合根 | `auditMode(on/off)`、`visibleScope(ALL/ROLES)`、`postPolicy(OPEN/READONLY)`；有帖版块禁止物理删除，只能停用 |
| `Thread` | 聚合根 | `publish()`、`edit(content, reason)`（可配置退回 PENDING）、`lock()`、`pin()/unpin()`、`essence()`、`remove()`；状态 `DRAFT/PENDING/PUBLISHED/OFFLINE/REMOVED` |
| `ThreadEditHistory` | 实体 | append-only，编辑留存原文快照（PUBLISHED 态编辑必留痕） |
| `Post` | 实体（楼层） | `floor` 服务端分配；`replyToPostId` 引用（同帖内、被引用楼层软删后引用仍可读「已删除」占位） |
| `Reaction` | 实体 | 点赞 / 收藏，`(tenant_id, user_id, target_type, target_id)` 唯一，取消即物理删除 |
| `ModerationRecord` | 实体 | 审核 / 举报 / 处置轨迹 append-only：`PENDING→APPROVED/REJECTED`，意见必填（REJECTED） |
| `SensitiveHit` | 实体 | 敏感词命中留痕（词、位置、动作：拦截/替换/放行待审） |

### 4.2 楼层与回复语义（硬规则）

| 规则 | 说明 | 错误码 |
|---|---|---|
| 楼层号 | 服务端按帖内顺序分配，从 1 起（1 楼=主帖正文）；软删**保留楼层号不回收**，后续楼层顺延——否则引用与「N 楼」语义全错位 | 30010 |
| 回复上限 | 单帖楼层 ≤ `maxPostsPerThread`（默认 5000），达到后帖子自动转 `lock` 提示开新帖 | 30011 |
| 引用 | 仅限同帖内引用；被引用楼层已删除时展示「已删除」占位，不泄露原文 | —— |
| 帖子编号 | `thread_no`（`FM-yyyyMMdd-XXXX` 流水）唯一键 `(tenant_id, thread_no)`；软删时改写为 `原号#D{id}` 释放唯一键（复用商城口径） | 30001 |

### 4.3 可见性与审核

1. 版块 `visibleScope=ALL` 登录即可见；`ROLES` 白名单命中角色码才可见，**不可见统一 404 不泄露存在性**；
2. 未审内容（`PENDING`）对无审核权者一律不可见；管理列表只返回「本人稿件 + 已发布」；
3. 审核模式版块：发布态编辑自动退回 `PENDING` 并写 `EDIT` 审计；退回必须填意见（30020）；
4. `REMOVED`（违规处置）仅管理员与作者可见占位；处置必须落 `ModerationRecord`；
5. 锁定（`lock`）后禁止新楼层与编辑，主帖仍可读；置顶 / 精华只对 `PUBLISHED` 有效。

### 4.4 内容安全（硬卡口）

| 项 | 规则 |
|---|---|
| 富文本消毒 | 服务端白名单标签 + 属性过滤（复用 whale-doc 消毒器口径），`script/iframe/on*` 属性一律剥离；消毒失败拒收 30030 |
| 敏感词 | 词库走 `whale-config` 配置项，动作三档：拦截（30031）/ 替换 `*` / 转待审；命中必落 `SensitiveHit` |
| 附件 | 类型与大小按 board 配置校验，魔数校验复用 whale-video 口径；字节归 `whale-file`，下载走本服务代理鉴权 |
| 限流 | 发帖 / 回复 / 点赞固定窗口限流（Redis，网关同款），超限 429；`/forum/hot` 独立更宽窗口 |
| 匿名 | 不提供真匿名；`nickname_alias` 仅展示层，`user_id` 恒存 |

### 4.5 计数与热榜

1. `reply_count / like_count / view_count` 条件更新自增；**浏览计数按会话去重**（同用户同帖 24h 内只 +1，Redis SETNX）；
2. 每日对账 Job 以明细表 `COUNT` 校正展示计数，差异写审计；
3. 热榜近 7 天滚动窗口（复用音乐热榜口径）：权重 = 回复 + 点赞加权 − 处置扣分，快照 30 分钟刷新，portal 只读快照。

---

## 5. ER 图

```text
fm_board (版块聚合根)
  │ 1
  ├── N  fm_thread (帖子聚合根, thread_no 流水)
  │        │ 1
  │        ├── N  fm_thread_edit_history (编辑快照, append-only)
  │        ├── N  fm_post (楼层, floor 顺序号, reply_to_post_id 自关联)
  │        │        │ 1
  │        │        └── N  fm_reaction (target_type=POST)
  │        └── N  fm_reaction (target_type=THREAD, 点赞/收藏)
  ├── N  fm_moderation_record (审核/举报/处置轨迹)
  └── N  fm_hot_snapshot (热榜快照, tenant 内每 30min 覆盖)

fm_sensitive_hit (敏感词命中, 独立表)
```

必备列：`id`（雪花 BIGINT）、`tenant_id`、`create_time`、`update_time`、`deleted`；`fm_thread_edit_history` / `fm_moderation_record` / `fm_sensitive_hit` 为 append-only，**不设** `update_time`/`deleted`（规范 §2.2 例外）。

---

## 6. 端点清单（M0–M1，约 32 个）

| 域 | 端点（前缀 `/forum`） |
|---|---|
| 元信息 | `GET /forum/meta`（门禁探活） |
| 版块 | `GET/POST/PUT/DELETE /forum/board`、`GET /forum/board/page`（管理 5）+ `GET /forum/portal/boards`（用户可见版块树） |
| 帖子 | `POST /forum/thread`、`PUT/DELETE /forum/thread/{id}`、`GET /forum/thread/page`、`GET /forum/thread/{id}`（浏览 +1）、`PUT /forum/thread/{id}/publish`、`GET /forum/thread/my`（7） |
| 楼层 | `POST /forum/thread/{id}/post`、`GET /forum/thread/{id}/post/page`、`PUT/DELETE /forum/post/{id}`（4） |
| 互动 | `PUT/DELETE /forum/reaction/thread/{id}`、`GET /forum/thread/{id}/reactions/my`、`GET /forum/my/collections`（4） |
| 管理端 | `PUT /forum/manage/thread/{id}/pin`、`/essence`、`/lock`、`/remove`、`GET /forum/manage/thread/pending/page`、`POST /forum/manage/thread/{id}/audit`、`GET /forum/manage/report/page`、`POST /forum/manage/report/{id}/dispose`、`GET /forum/manage/sensitive/page`（9） |
| 热榜 | `GET /forum/hot`、`POST /forum/hot/refresh`（2） |
| 内网 | `GET /forum/internal/thread/{id}`（供 portal/agent 只读快照，internal-deny） |

M2 起追加：检索（ngram，`GET /forum/search`）、@提及通知、精华转知识库登记。

---

## 7. 权限与菜单

| 权限号 | 权限码 | 说明 |
|---|---|---|
| 2330 | `sys:forum:browse` | 登录可见论坛（版块/帖子浏览） |
| 2331 | `sys:forum:thread:add` | 发帖 |
| 2332 | `sys:forum:thread:edit` | 编辑本人帖（服务端仍校验所有权） |
| 2333 | `sys:forum:thread:remove` | 删除本人帖 |
| 2334 | `sys:forum:post:add` | 回复 |
| 2335 | `sys:forum:reaction:use` | 点赞/收藏 |
| 2340 | `sys:forum:manage:list` | 管理端入口（待审/举报/敏感词） |
| 2341 | `sys:forum:manage:audit` | 审核（通过/退回，意见必填） |
| 2342 | `sys:forum:manage:dispose` | 置顶/精华/锁定/违规处置 |
| 2343 | `sys:forum:manage:board` | 版块管理（含审核开关、可见范围） |
| 2344 | `sys:forum:hot:refresh` | 热榜手工刷新 |

菜单（510–519）：`510 论坛`（帖子页）、`511 我的收藏`、`512 论坛管理`（下挂待审/举报/敏感词/版块，按 2340–2344 分权）。授权语句**按 perm_code 不按 ID 区间**（2026-11-09 撞号教训）；落码前跑 `scan-id-ranges.py` 与 `check-perm-coverage.py` 双门禁。

---

## 8. 里程碑

| 里程碑 | 内容 | 验收 |
|---|---|---|
| M0 底座 | V1 基线（audit/api_log）+ V2 版块/帖子/楼层/互动 8 表、`/forum/meta` 门禁、网关路由 + internal-deny、mysql init 建库、Prometheus job、镜像矩阵 9011、前端子应用 8123 骨架（qiankun `renderWithQiankun`） | `mvn verify` + ArchUnit 4/4 + 单测；起服冒烟 `/forum/meta` |
| M1 闭环 | 发帖/回复/引用/编辑历史/点赞收藏/我的、通知接 `whale-message`（回复/引用/@提及，幂等留痕）、附件接 `whale-file`、浏览去重 | 单测 ≥ 80 项；前后端路径过 `check-api-path-drift.py` |
| M2 治理 | 审核模式（状态机 + 退回必填意见）、举报与处置、敏感词三档、限流 429 实测 | 负向验证：未审内容对普通用户 404；处置必落轨迹 |
| M3 检索 | ngram 全文索引（帖标题/正文）、单字回退 LIKE、重建接口（复用 mail 口径） | 检索冒烟 + `GET /forum/search/status` |
| M4 收口 | 热榜快照 + portal 接线、计数对账 Job、运维手册 `docs/ops/forum-service-ops.md`、SLO 接 Prometheus/Grafana | 对账演练 + 备份恢复演练 |

---

## 9. 编排与门禁接线清单（实施时逐项打勾）

- [ ] `whale-backend/pom.xml` 聚合 + `whale-services/whale-forum` 模块（五同名）
- [ ] `whale-gateway/application.yml`：`lb://whale-forum` + `Path=/forum/**` + internal-deny `/forum/internal/**`（**不配 StripPrefix**）
- [ ] `infrastructure/docker/docker-compose.services.yml` + mysql init 建库 `bluewhale_forum` + Prometheus `job_name: whale-forum` + 镜像矩阵 9011
- [ ] naming-conventions §八 登记 `forum` 域词 / `fm_` 前缀；service-catalog §一/§七 登记 9011 例外与前端 8123
- [ ] 权限迁移幂等（`IF NOT EXISTS`）+ 授权按 perm_code + 角色播种走 `check-role-coverage.py`
- [ ] 门禁全绿：`check-internal-boundary.py` / `check-frontend-perm-mapping.py` / `check-qiankun-lifecycle.py` / `check-api-path-drift.py` / `check-long-id-typing.py` / `scan-id-ranges.py` / `check-perm-coverage.py`
- [ ] 前端子应用：React 19 antd 补丁、`renderWithQiankun` 生命周期、`onReauthRequired` 接入（不重蹈 admin 外子应用未接的遗留）
