# 论坛服务设计（whale-forum）

> 状态：**设计稿（待评审，未落码）**
> 关联功能规格：[鲸议台 2D 功能规格 v1.3](../requirements/2026-11-12-forum-2d-functional-spec-v1.3.md)（需求侧唯一口径，功能编号 F-xx-xx 以该文件为准）
> 关联基线：[service-catalog.md](../service-catalog.md)（§一 / §七 端口分段，9011 例外已登记）、[naming-conventions.md](../naming-conventions.md)（§八 域词表须先登记 `forum` 词根与 `fm_` 表前缀）、[network-boundary.md](network-boundary.md)、[security-baseline.md](security-baseline.md)
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
| 权限号段 | **2340–2350** | 原定 2330 与 whale-assistant 撞号（V8 已应用），整体后移；23xx 其余占用 2300–2306 / 2313 / 2320–2323；落码前过 `scripts/security/scan-id-ranges.py` 负向验证 |
| 菜单号段 | **510–519** | 现有最大 505（商城套餐）；跳过 506–509 留给在途批次 |
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

1. `reply_count / like_count / view_count` 条件更新自增；**浏览计数按会话去重**（同用户同帖 24h 内只 +1，落库到 `fm_thread_view`，唯一键 `(tenant_id, thread_id, user_id)` + 滚动窗口条件更新——详情是读主路径，不引入 Redis 依赖，缓存不可用也不该让详情 500）；
2. 每日对账 Job 以明细表 `COUNT` 校正展示计数，差异写审计；
3. 热榜近 7 天滚动窗口（复用音乐热榜口径）：权重 = 回复×3 + 投花×5 + 浏览×0.2 − 处置扣分×5（处置=窗口内 `REMOVE`/`AUDIT_REJECT` 轨迹条数），快照 30 分钟刷新到 `fm_hot_snapshot`（租户内整表覆盖），portal 只读快照；快照为空时接口用实时头部兜底，不让首页空白。

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
  ├── N  fm_thread_view (浏览去重, 同人同帖 24h 一行, 唯一键 tenant+thread+user)
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
| 检索 | `GET /forum/search`、`GET /forum/search/status`、`POST /forum/search/reindex`（3） |
| 标签 | `GET /forum/tag/list`（M7：标签聚合，按帖子数降序，仅统计可见版块内已发布帖） |
| 内网 | `GET /forum/internal/thread/{id}`（供 portal/agent 只读快照，internal-deny） |

M4 落地口径：检索走 `FULLTEXT(title, content_text) WITH PARSER ngram`（`content_text` 为写入时提取的纯文本，
不索引富文本源码），关键词长度 < `ngram_token_size` 时回退 `LIKE`；
计数对账以明细 `COUNT` 校正展示值并落 `fm_count_reconcile` 轨迹。@提及通知留给后续批次。

---

## 7. 权限与菜单

| 权限号 | 权限码 | 说明 |
|---|---|---|
| 2340 | `sys:forum:browse` | 登录可见论坛（版块/帖子浏览） |
| 2341 | `sys:forum:thread:add` | 发帖 |
| 2342 | `sys:forum:thread:edit` | 编辑本人帖（服务端仍校验所有权） |
| 2343 | `sys:forum:thread:remove` | 删除本人帖 |
| 2344 | `sys:forum:post:add` | 回复 |
| 2345 | `sys:forum:reaction:use` | 点赞/收藏 |
| 2346 | `sys:forum:manage:list` | 管理端入口（待审/举报/敏感词） |
| 2347 | `sys:forum:manage:audit` | 审核（通过/退回，意见必填） |
| 2348 | `sys:forum:manage:dispose` | 置顶/精华/锁定/违规处置 |
| 2349 | `sys:forum:manage:board` | 版块管理（含审核开关、可见范围） |
| 2350 | `sys:forum:hot:refresh` | 热榜手工刷新 |

菜单（510–519）：`510 论坛`（帖子页）、`511 我的收藏`、`512 论坛管理`（下挂待审/举报/敏感词/版块，按 2346–2350 分权）。授权语句**按 perm_code 不按 ID 区间**（2026-11-09 撞号教训）；落码前跑 `scan-id-ranges.py` 与 `check-perm-coverage.py` 双门禁。

> **号段修订（落码时发现）**：原定 2330–2359 与 `whale-assistant` 冲突——`V8__assistant_permission_menu.sql` 已占用 2330（`sys:assistant:define`）/2331（`sys:assistant:chat`），2330–2339 整段归助手域。论坛整体后移至 **2340–2350**，迁移见 `migration-6.43-forum.sql`。

---

## 8. 里程碑

| 里程碑 | 内容 | 验收 |
|---|---|---|
| M0 底座 | V1 基线（audit/api_log）+ V2 版块/帖子/楼层/互动 8 表、`/forum/meta` 门禁、网关路由 + internal-deny、mysql init 建库、Prometheus job、镜像矩阵 9011、前端子应用 8123 骨架（qiankun `renderWithQiankun`） | `mvn verify` + ArchUnit 4/4 + 单测；起服冒烟 `/forum/meta` |
| M1 闭环 | 发帖/回复/引用/编辑历史/点赞收藏/我的、通知接 `whale-message`（回复/引用/@提及，幂等留痕）、附件接 `whale-file`、浏览去重 | 单测 ≥ 80 项；前后端路径过 `check-api-path-drift.py` |
| M2 治理 | 审核模式（状态机 + 退回必填意见）、举报与处置、敏感词三档、限流 429 实测 | 负向验证：未审内容对普通用户 404；处置必落轨迹 |
| M3 互动与热榜 | 投花/藏帖（幂等 + 计数原子增减）、我的收藏、浏览 24h 去重、热榜打分与快照（`GET /forum/hot`、`POST /forum/hot/refresh`，受管任务 `forum.hot.snapshot`） | 重复投花不重复计数、取消后可再投；同日重复打开只 +1 浏览；快照刷新为整表覆盖不累加 |
| M4 检索与收口 | ngram 全文索引（`fm_thread.title + content_text`）、单字回退 LIKE、`GET /forum/search` + `/status` + `/reindex`、计数对账（`fm_count_reconcile` + 受管任务 `forum.count.reconcile` + `POST /forum/admin/count/reconcile`）、运维手册 `docs/ops/forum-service-ops.md`、SLO-12/13/14 | ✅ 实机：回填前正文词命中 0 → 回填 6 条后命中 1；单字走 LIKE 命中 3；对账 dry-run 报 2 处差异、`fix=true` 修正并留 4 行轨迹 |
| M5 互动增强（P1） | `V8__mention.sql`（`fm_mention`）；@提及（F-03-10）：解析 → auth 内网通讯录解析 → 落库 → outbox 通知，**afterCommit 派发**；引用回复摘要（F-04-02，被引楼层删除只回占位）；楼层正/倒序（F-02-06，下推 `ORDER BY`） | ✅ 实机：发帖 `@admin @qa_admin` 落 2 条 `fm_mention` + 2 条 outbox `SENT`；跨帖 @ 同一人各自成条（唯一键含 `thread_id`）；回帖引用回填摘要，被引楼层 `REMOVE` 后仅 `deleted=true`；`order=desc` 返回 `[3,2]`；单测 111 项全绿 |
| M6 成长与热榜增强（P1） | `V9__hot_window_exp.sql`（`fm_hot_snapshot.hot_window` + `fm_exp_ledger`）；热榜双档 24h/7d（24h 档按小时指数衰减，`GET /forum/hot?window=` 缺省 7d）；题金榜 / 撤销精华（`POST /forum/admin/thread/{id}/essence|unessence`）；论坛经验本地账本（发帖 5 / 被投花 2 / 精华 200 / 金榜 Top1 1000，匿名不计，同人同帖同事件唯一）与 `GET /forum/exp/me` | 单测 117 项全绿；24h 与 7d 快照各自覆盖互不覆盖；同帖重复题金榜不重复发经验 |
| M7-1 发现页（P1） | `V10__thread_tag.sql`（`fm_thread_tag` + 历史标签递归回填）；发帖/改帖同步标签行；`GET /forum/tag/list` 标签聚合；`ThreadQuery` 增 `authorId` / `from` / `to` / `tag`，列表与检索共用同一谓词；精华榜复用 `GET /forum/thread/page?essenceOnly=true`；前端「发现」页（精华榜 / 标签页 + 版块、作者、时间范围、只看精华筛选） | 单测覆盖标签同步、超限拒绝、筛选透传、聚合排序；前端 lint + build 通过；`check-api-path-drift.py` / `check-long-id-typing.py` 全绿 |
| M7-2 个人中心（P1） | `GET /forum/profile/me` 与 `GET /forum/profile/{userId}`（鲸阶、经验、发帖数、获花数、精华数、禁言状态）；`GET /forum/my/threads`（含审核中/已处置，作者可见）；`GET /forum/my/posts`（联 `fm_thread` 排除已删帖）；鲸阶阈值走 `whale.forum.level-thresholds`；前端「我的」页（资料卡 + 我的帖子 / 回复 / 收藏） | 单测覆盖鲸阶边界、禁言映射、我的帖子/回复分页；前端 lint + build；两项门禁全绿 |
| M7-3 结契（P1） | `V11__follow.sql`（`fm_follow`，关注作者 / 版块）；`PUT|DELETE /forum/follow/{targetType}/{targetId}`（幂等）；`GET /forum/follow/{targetType}/{targetId}` 回关注态；`GET /forum/my/follows` 我的关注；发布态新帖向关注者写 outbox 通知（幂等、排除作者本人、单帖上限 `follow-notify-limit`）；前端详情页关注作者、列表页关注版块、「我的」页关注 Tab | 单测覆盖自关注拒绝、版块可见性校验、幂等关注/取关、关注列表分页与通知去重；前端 lint + build；两项门禁全绿 |

---

## 8.1 M5 @提及实现口径（P1）

| 项 | 口径 | 为什么 |
|---|---|---|
| 解析输入 | 富文本先经 `RichTextSanitizer.summarize(content, searchTextLength)` 转纯文本，再交给 `MentionParser` | 直接解析 HTML 会把 `@张三</p>` 的标签算进昵称，也会让 `href="mailto:a@b"` 变成一次提及 |
| 匹配规则 | `(?<![\p{L}\p{N}_.-])@([\p{L}\p{N}_.-]{1,32})`，`＠` 归一化为 `@`，尾部 ASCII 标点剔除 | 全角 `＠` 在中文输入法下极常见；后行断言排除邮箱；尾部标点剔除避免 `@wang.` 把句号当名字 |
| 歧义 | 中文无分隔符时**最长匹配**，解析不到就跳过 | 「@张三的说法」无法区分，宁可少通知一个人，也不能 @ 错人；前端用选择器插入「@昵称 」从源头消除 |
| 上限 | 单帖 ≤ `max-mentions-per-post`（默认 20），超出截断并 `log.info` 留痕 | 与 F-03-10 一致；截断不是错误但必须可解释 |
| 身份解析 | auth `GET /user/internal/directory?keyword=` + `X-Tenant-Id`，按 `nickname`/`username` **精确等于**过滤（忽略大小写） | 模糊命中多条时不去猜；缺租户头 auth 直接回「缺少租户上下文」，不是查不到 |
| 落库 | `fm_mention`，`post_id=0` 表示主帖；唯一键 `(tenant_id, thread_id, post_id, user_id)` | 主键是雪花 ID，含主键的唯一键永不冲突；主帖 `post_id` 恒 0，**唯一键必须带 `thread_id`**，否则跨帖 @ 同一人互相当成重复 |
| 通知 | outbox，幂等键 `forum:mention:{threadId}:{postId}:{userId}`，`biz_type=MENTION` | 键不带 `threadId` 时，第二个帖子的主帖 @ 同一个人会撞 outbox 唯一键而静默丢通知 |
| 派发时机 | 事务内注册 `afterCommit`；无事务时同步执行 | 不在事务里打 HTTP（避免长事务空等网络），也避免通知一个可能回滚的帖子 |
| 跳过规则 | 自己 @ 自己、解析不到、同层重复、待审帖/待审楼层 | 待审内容不可见，通知等于给一个「点进去看不到」的链接 |
| 失败策略 | 通讯录不可达/超时（800ms 连接、1500ms 读）只 `warn` 并跳过 | @提及是附属能力，不能让「@了谁」把「帖子发出去」拖住 |

---

## 8.2 M6 热榜双档与经验账本实现口径（P1）

| 项 | 口径 | 为什么 |
|---|---|---|
| 快照分区 | `fm_hot_snapshot` 新增 `hot_window`（`24h` / `7d`），唯一键 `uk(tenant_id, hot_window, rank_no)`；刷新按 `(tenant_id, hot_window)` 先删后插 | 一档一表清洗会互相抹除；分区后两档可独立刷新、独立回退 |
| 24h 衰减 | 查询时按 `TIMESTAMPDIFF(HOUR, create_time, NOW())` 计算指数衰减 `EXP(-h/τ)`，τ（`hot-decay-half-life-hours`）默认 24，不落衰减系数列；7d 档不衰减 | 落系数意味着每隔一小时要回写全表；查询时算天然无状态，重建快照即可修正历史口径 |
| 窗口参数 | `GET /forum/hot?window=24h|7d`，非法/缺省一律 7d；`limit` 仍受配置上限截断 | portal 不传参保持旧行为；非法值不应 400 打断首屏 |
| 精华加成 | 24h/7d 查询统一加 `essence × essenceBonus`（默认 50），不用额外排序分支 | 题金榜本身就是强信号，两档都需要体现 |
| 题金榜/撤精 | `essence` 置 1/0 + `fm_moderation_record` 留 `ESSENCE`/`UNESSENCE` 轨迹 + 通知作者；已精/已撤幂等返回 | 重复点击不应灌重复轨迹，更不能重复发经验 |
| 经验事件 | `fm_exp_ledger` 唯一键 `(tenant_id, user_id, event, ref_thread_id)`，`INSERT IGNORE`；匿名帖/无作者一律跳过；失败只 `warn` 返回 0 | 账本 append-only，重放安全；经验是附属能力，不能把发帖/投花打挂 |
| 经验口径 | 发帖 +5、被投花 +2、精华 +200、7d 金榜 Top1 +1000；同一帖子重复霸榜只发一次；撤销精华不回收已发经验 | 与 F-07-04 一致；回收会造成总额负数与对账复杂度 |
| 读取 | `GET /forum/exp/me` 返回 `total` + 最近 N 条（默认 20）流水 | 前端只读展示，不做本地累计 |

---

## 8.3 M7 标签聚合与发现筛选实现口径（P1）

| 项 | 口径 | 为什么 |
|---|---|---|
| 标签数据源 | 新增 `fm_thread_tag`（`tenant_id + thread_id + tag` 唯一），仍保留 `fm_thread.tags` 逗号串作为展示快照 | 逗号串无法建索引、`LIKE '%tag%'` 会把 `后端` 命中 `前端后端`；标签页需要 `GROUP BY` 聚合，必须有行式数据源 |
| 回填 | V10 用递归 CTE 拆 `fm_thread.tags`，`INSERT IGNORE` 幂等；标签首尾空白剔除、空标签丢弃 | 迁移可重复执行；历史帖不迁移则标签页只显示新帖，验收「标签聚合」必然对不上 |
| 同步 | 发帖/改帖时按「删旧行 + 插新行」整体重建；单帖 ≤ `max-tags-per-thread`（默认 10）、单标签 ≤ `max-tag-length`（默认 32），超限 400 | 差量对比在并发编辑下会漏行；上限与列宽对齐，否则 `VARCHAR(32)` 截断或 1406 报 500 |
| 聚合口径 | `GET /forum/tag/list?limit=`：只统计当前用户**可见版块**内 `PUBLISHED/SOLVED/CLOSED` 且 `deleted=0` 的帖子；按帖子数降序、标签名升序；`limit` 默认 50、上限 200 | 标签页不能把私密版块的标签与计数暴露给无权用户；同数时按名称排序保证分页稳定 |
| 筛选 | `ThreadQuery` 增 `authorId` / `from` / `to`（`yyyy-MM-dd`，含首尾日）/ `tag`；列表通道用 `EXISTS fm_thread_tag`，检索通道把同样的条件写进 `MATCH` 语句 | 检索通道若不带筛选，用户「搜索 + 时间范围」会静默返回范围外结果，看起来像缓存没生效 |
| 精华榜 | 不新增端点，复用 `GET /forum/thread/page?essenceOnly=true`（跨版块、按发布时间倒序，悬榜优先） | 同一份查询再包一个 `/essence/page` 只会产生两个会分叉的契约 |
| 作者筛选 | 传 `authorId`，不传作者昵称 | 平台无跨服务用户名查询，按昵称筛会退化成「先查 auth 再 IN」，分页 total 与实际条数会对不上 |

---

## 8.4 M7-2 个人中心与鲸阶实现口径（P1）

| 项 | 口径 | 为什么 |
|---|---|---|
| 鲸阶算法 | `whale.forum.level-thresholds` 升序（默认 0/100/500/2000/5000/10000），`level` = 满足 `total ≥ 阈值` 的最大下标 + 1；`level-names` 默认 初入鲸门/观澜/听潮/破浪/领航/鲸首 | 经验账本是 append-only，总额只增，等级天然「只升不降」，不需要独立等级表与降级申诉流 |
| 个人主页数据源 | 只回本服务可得项：userId、等级、经验总额、发帖数、获花数、精华数、禁言状态；**头像 / 部门 / 勋章本批不返回** | auth 无批量通讯录 SDK、勋章是 P2；接口先给稳定字段，后补项只加不改 |
| 统计口径 | 发帖 / 获花 / 精华对 `fm_thread` 按 `author_id` 聚合，仅 `PUBLISHED/SOLVED/CLOSED` 且 `deleted=0` | 与列表页可见性一致；对明细表逐条 `COUNT` 在主页这种高频读上没必要 |
| 我的帖子 | `GET /forum/my/threads`：`author_id = 当前用户`，**含 PENDING / REMOVED**，按创建时间倒序 | 作者必须能看到自己审核中与被处置的帖子，否则「发了没显示」只能来问客服 |
| 我的回复 | `GET /forum/my/posts`：`fm_post` 内联 `fm_thread`（`deleted=0`）排除已删帖，按楼层创建时间倒序，返回帖子标题与帖子ID | 帖子被删后回复没有落点，返回了也点不进去；分页先过滤再分页，避免「每页条数越翻越少」 |
| 禁言展示 | `ProfileVO` 只回 `banned` / `banType` / `banUntil`，**不回原因** | 处置原因是治理信息，公开展示等于把举报理由挂到对方主页 |
| 权限 | 个人主页与我的内容均只要求 `sys:forum:browse`；`/my/*` 一律以身份头用户为准，不接受 `userId` 入参 | 允许传 userId 的「我的」等于给越权读开入口，且没人需要「查别人的我的回复」 |

---

## 8.5 M7-3 结契（关注）实现口径（P1）

| 项 | 口径 | 为什么 |
|---|---|---|
| 数据模型 | 新增 `fm_follow`，唯一键 `(tenant_id, user_id, target_type, target_id)`，`target_type` 只允许 `USER` / `BOARD`；关系表不设 `deleted`，取关即物理删行 | 关注是纯关系数据、无审计需求；软删会让唯一键挡住「取关后再关注」，还得额外写恢复分支 |
| 自关注 | `targetType=USER` 且 `targetId=当前用户` 返回 400 | 自关注只会给自己灌自己的新帖通知 |
| 目标校验 | `BOARD` 关注前过 `BoardService.requireVisibleBoard`；`USER` 只做空值与自关注校验，不反查 auth | 关注不可见版块等于订阅一个永远看不到的流；跨服务用户目录只用于 @提及解析，不给关注做存在性反查 |
| 幂等 | 关注用 `INSERT IGNORE`（唯一键兜底），重复关注不报错；取关直接 `DELETE`，未关注也返回成功 | 与投花/藏帖同语义：把状态置为目标值，按钮连点与网络重试都不该报错 |
| 新帖通知 | 发布态帖子（直接发布与审核通过两条路径）向「关注作者」与「关注版块」的用户各写一条 outbox；幂等键 `forum:follow:thread:{threadId}:{userId}`；排除作者本人；单帖最多通知 `follow-notify-limit`（默认 200）人 | 键带 threadId，重复触发只打扰一次；上限防止大版块发帖把 outbox 与 whale-message 打爆 |
| 通知时机 | 与发帖/审核通过同事务写 outbox（与发帖经验一致），不额外注册 afterCommit | outbox 行跟帖子一起提交或回滚，不会通知一个被回滚的帖子；投递失败仍由 flush 补偿 |
| 读取 | `GET /forum/follow/{targetType}/{targetId}` 回 `{following}`；`GET /forum/my/follows?targetType=` 按关注时间倒序分页，只回 `targetType/targetId/createTime` | 版块名由前端用已加载的版块树本地映射，不为了一个展示字段在论坛服务拼跨表查询 |
| 权限 | 读用 `sys:forum:browse`，关注/取关用 `sys:forum:reaction:use` | 结契属互动行为，复用互动权限码；新增权限点要走「常量 → 迁移 → 注解」三步，收益不抵成本 |

---

## 9. 编排与门禁接线清单（实施时逐项打勾）

- [ ] `whale-backend/pom.xml` 聚合 + `whale-services/whale-forum` 模块（五同名）
- [ ] `whale-gateway/application.yml`：`lb://whale-forum` + `Path=/forum/**` + internal-deny `/forum/internal/**`（**不配 StripPrefix**）
- [ ] `infrastructure/docker/docker-compose.services.yml` + mysql init 建库 `bluewhale_forum` + Prometheus `job_name: whale-forum` + 镜像矩阵 9011
- [ ] naming-conventions §八 登记 `forum` 域词 / `fm_` 前缀；service-catalog §一/§七 登记 9011 例外与前端 8123
- [ ] 权限迁移幂等（`IF NOT EXISTS`）+ 授权按 perm_code + 角色播种走 `check-role-coverage.py`
- [ ] 门禁全绿：`check-internal-boundary.py` / `check-frontend-perm-mapping.py` / `check-qiankun-lifecycle.py` / `check-api-path-drift.py` / `check-long-id-typing.py` / `scan-id-ranges.py` / `check-perm-coverage.py`
- [ ] 前端子应用：React 19 antd 补丁、`renderWithQiankun` 生命周期、`onReauthRequired` 接入（不重蹈 admin 外子应用未接的遗留）
