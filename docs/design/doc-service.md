# 在线文档 / Wiki 服务（whale-doc）设计

> 状态：**已交付（M1–M5，2026-10-29）**
> 关联需求：REQ-FUNC-027、REQ-FUNC-028、REQ-ARCH-012、REQ-SEC-018
> 关联计划：[2026-10-29-wiki-doc-service.md](../plans/2026-10-29-wiki-doc-service.md)
> 定位：平台核心服务，提供组织级 Wiki / 在线文档能力；不替代 `whale-file` 的文件存储，也不复制 `whale-agent` 的检索能力。

---

## 一、目标与边界

### 1.1 目标

1. 以「空间 → 页面树 → 页面」组织组织知识，支持富文本/Markdown 快捷输入、附件、评论、版本历史、搜索与收藏。
2. 权限分两层：平台 RBAC 功能权限 + 空间成员角色数据权限；默认支持「全员可读空间」与「仅成员空间」。
3. 复用现有平台能力：`whale-service-auth` 身份/租户/权限、`whale-file` 文件存储、`whale-agent` 知识检索、`whale-message` 通知、`whale-workflow` 审批、`whale-archive` 归档、`whale-audit` 审计、网关与 qiankun 前端体系。
4. 保存路径必须具备并发冲突检测，任何情况下不得静默覆盖他人内容。

### 1.2 不负责

- 不存储文件字节、不做对象存储生命周期/配额（归 `whale-file`）。
- 不做 IM 会话、已读、在线状态（归 `whale-im`）。
- 不做知识问答编排与向量检索（归 `whale-agent` / `whale-ai`）。
- 不替代 Office 在线编辑、电子表格、白板、邮件。
- M1 不做多人实时协作、不做匿名公开站点、不做跨租户发布。
- 不新建消息队列；核心保存链路只依赖本地事务 + 数据库。

### 1.3 成功标准

| 维度 | 标准 |
|---|---|
| 功能 | 普通用户 5 分钟内完成「建空间 → 建页面 → 编辑 → 发布 → 评论」闭环 |
| 一致性 | 两个用户同时保存同一页面，后提交者收到冲突提示，不静默覆盖 |
| 性能 | 页面详情 P95 < 300ms；保存 P95 < 500ms（正文 ≤ 2MB）；5,000 页空间树 < 1s |
| 安全 | 跨租户、跨空间、跨页面附件均不可越权访问；富文本不执行脚本 |
| 可运维 | 健康检查、Prometheus 指标、审计日志、启动脚本、部署清单齐备 |

---

## 二、限界上下文与统一语言

### 2.1 限界上下文

**文档协作上下文（Doc Collaboration）** 是核心域，拥有空间、页面、版本、评论、附件引用、页面级权限、模板与分享；不拥有文件字节、用户身份、审批引擎、消息投递。

| 上下文 | 关系 | 契约 |
|---|---|---|
| `whale-service-auth` | 上游（支撑域） | JWT 注入 `X-User-Id` / `X-Tenant-Id` / `X-Permission-Codes`；用户/部门查询走 SDK 或内网接口 |
| `whale-file` | 上游（支撑域） | `POST /file/internal/upload` 上传，`GET /file/internal/{id}/content` 读取；桶 `whale-doc` |
| `whale-doc` | 核心域 | 本上下文，独占 `bluewhale_doc` |
| `whale-agent` | 下游（支撑域） | 消费已发布公开页面的变更流，建立知识索引；不反向修改文档 |
| `whale-message` / `whale-im` | 下游（通用域） | 评论提及、审批待办的站内通知；失败不回滚文档事务 |
| `whale-workflow` | 下游/上游（通用域） | M3 发布审批；`businessKey=doc:{pageId}:v{revision}`，回调推进发布 |
| `whale-archive` | 下游（通用域） | M3/M4 归档已发布页面快照，按 `(sourceType,sourceId)` 幂等 |
| `whale-audit` | 下游（通用域） | 写操作审计；审计不记录正文，只记录页面 ID、标题、版本号 |

### 2.2 统一语言

| 术语 | 含义 | 禁止混用 |
|---|---|---|
| 空间 Space | 文档集合与权限边界，有唯一 `spaceKey` | 项目、目录、知识库 |
| 页面 Page | 空间内可编辑的文档节点，可嵌套 | 文档、文章、笔记 |
| 目录 Folder | 仅用于组织页面树的容器节点，无正文 | 文件夹、分组 |
| 草稿 Draft | 仅空间成员可见的未发布页面 | 临时页、私有页 |
| 发布 Publish | 页面进入所有可读成员可见状态 | 上线、公开 |
| 归档 Archive | 页面只读并退出常规导航 | 删除、冻结 |
| 版本 Version | 某一 `revision` 的不可变正文快照 | 历史、备份 |
| 评论 Comment | 页面或块级讨论，可回复、可解决 | 留言、批注 |
| 附件 Attachment | 页面引用的 `whale-file` 文件记录 | 文件、图片 |
| 成员角色 MemberRole | `OWNER / ADMIN / EDITOR / READER` | 权限、角色 |
| 回收站 Recycle Bin | M2 逻辑删除页面的可恢复集合 | 垃圾箱 |

---

## 三、聚合、不变量与状态机

### 3.1 聚合

#### DocSpace（空间聚合根）

不变量：

1. `spaceKey` 在租户内唯一，创建后不可修改（契约级 URL 标识）。
2. 创建者自动成为 `OWNER`；任意时刻至少保留 1 名 `OWNER`，最后一名 `OWNER` 不可被移除或降级。
3. 角色层级：`OWNER > ADMIN > EDITOR > READER`；成员可被管理员调整，但不能越级授予 `OWNER`（只有 `OWNER` 可转让）。
4. `PUBLIC` 空间：租户内所有具备 `sys:doc:page:read` 的用户可读已发布页面；`MEMBER` 空间：仅成员可读，`READER` 及以上可见草稿。
5. 归档空间后不再允许新建页面；空间不物理删除，页面保留。

#### DocPage（页面聚合根）

不变量：

1. 标题 1–200 字符；正文 JSON ≤ 2MB，纯文本 ≤ 1MB。
2. `slug` 在空间内唯一；缺省为 `p-{id}`；只允许 `[a-z0-9-]`；发布后不可修改（M2 引入旧链接重定向后再放开）。
3. `parentId` 必须属于同一空间且未删除；层级 ≤ 10；移动不得形成环。
4. `FOLDER` 不保存正文；`PAGE` 可保存正文。
5. 每次内容/标题/父级变更必须携带 `baseRevision`；成功后 `revision + 1`。更新 SQL 使用 `WHERE id=? AND revision=?`，受影响行数为 0 即冲突。
6. 状态流转：`DRAFT → PUBLISHED → ARCHIVED`；`PUBLISHED` 可直接编辑（Wiki 模式，读者看到最新已发布内容）；`ARCHIVED → DRAFT` 为恢复；M3 可选 `PENDING_APPROVAL`。
7. 删除页面时若有子页面，M1 拒绝删除；M2 将整棵子树移入回收站，页面 `deleted=1` 且 `slug` 改写为 `{slug}-trash-{id}`，避免唯一键冲突。删除时同步逻辑删除附件引用与评论；版本行保留（append-only），供回收站恢复与审计。
8. 附件只解除页面引用，不物理删除 `whale-file` 文件；孤儿文件由后续清理任务处理。

#### DocPageVersion（版本快照）

不可变，追加写；`(pageId, revisionNo)` 唯一；不设 `update_time/deleted`（append-only 例外）。

快照策略：

- 手动「保存版本」、发布、恢复历史版本、导入时必建快照；
- 自动保存默认每 10 分钟或每 20 个 revision 建一次快照，可通过 `whale.doc.version.auto-snapshot-*` 调整；
- 快照与当前正文解耦，恢复历史版本 = 以旧正文生成新 revision，并追加 `RESTORE` 快照，绝不覆盖历史行。

#### DocComment（评论聚合）

1. 正文 1–2,000 字符；可带 `anchorBlockId` / `anchorText`，锚点失效时降级为页面级评论。
2. `parentId` 必须属于同一页面；只允许两级（评论 + 回复），M2 再评估更深层级。
3. 解决/删除：评论作者、空间 `ADMIN/OWNER`、平台 `sys:doc:admin`。
4. 评论不进入版本历史。

#### DocAttachment（附件引用）

1. 一个 `fileId` 在同一页面只登记一次；`usageType=ATTACHMENT/INLINE`。
2. 读取必须同时校验页面可读与附件属于该页面，禁止仅凭 `fileId` 越权读取。

### 3.2 状态机

```
DRAFT ──publish──▶ PUBLISHED ──archive──▶ ARCHIVED
  │                   │  ▲                  │
  │                   │  └──edit────────────┘（ARCHIVED 不可编辑）
  │                   └──publish（重新发布）
  └──archive──▶ ARCHIVED ──restore──▶ DRAFT
（M3 可选）DRAFT/PUBLISHED ──submit──▶ PENDING_APPROVAL ──approve──▶ PUBLISHED
                                      └──reject──▶ DRAFT
```

### 3.3 领域事件

| 事件 | 触发 | 后续（M1 同步/本地；M3 起 after-commit 通知） |
|---|---|---|
| `PageCreated` | 新建页面 | 空间页面数缓存、活动流（M2） |
| `PageSaved` | 内容/标题保存 | 版本快照策略、变更流游标 |
| `PagePublished` | 发布 | 知识索引（M4）、归档候选（M3） |
| `PageArchived` | 归档 | 下线知识索引、归档快照 |
| `PageMoved` | 移动/排序 | 树缓存失效 |
| `PageDeleted` | 删除 | 回收站（M2）、下线索引、通知 |
| `VersionRestored` | 恢复版本 | 创建新 revision |
| `CommentAdded/Resolved` | 评论 | 提及通知（M3） |
| `MemberChanged` | 成员变更 | 权限缓存失效（M2） |

> M1 使用 Spring `ApplicationEventPublisher`，监听器只在事务提交后执行外部动作；不引入 MQ。

---

## 四、架构与代码落点

### 4.1 服务坐标

| 项 | 取值 |
|---|---|
| 服务/模块 | `whale-doc`（`backend/whale-services/whale-doc`） |
| Maven 坐标 | `com.bluewhale:whale-doc` |
| Nacos / `spring.application.name` | `whale-doc` |
| 端口 | `8095`（`${SERVER_PORT:8095}`） |
| 库 | `bluewhale_doc` |
| Java 包根 | `com.bluewhale.doc` |
| 对外前缀 | `/doc/**`（网关不 StripPrefix） |
| 内网前缀 | `/doc/internal/**`（网关 `internal-deny` 403） |
| 前端 | `frontend/platform-apps/whale-doc`，端口 `8110`，base `/doc` |
| 配置前缀 | `whale.doc.*` |
| Redis 命名空间 | `whale:doc:*` |
| 表前缀 | `doc_` |

### 4.2 分层与包结构

```text
backend/whale-services/whale-doc/
└── src/main/java/com/bluewhale/doc/
    ├── WhaleDocApplication.java
    ├── interfaces/
    │   ├── controller/          # Space/Page/Version/Comment/Attachment/Search/Internal
    │   └── dto/                 # Request / Response / VO
    ├── application/
    │   ├── service/             # 事务编排：SpaceAppService、PageAppService ...
    │   ├── command/             # 命令对象
    │   └── guard/               # DocAccessGuard（平台权限 + 空间角色）
    ├── domain/
    │   ├── model/               # DocSpace、DocPage、DocPageVersion、DocComment、DocAttachment
    │   ├── repository/          # 仓储端口（接口）
    │   ├── event/               # 领域事件
    │   └── exception/           # 文档域错误码
    └── infrastructure/
        ├── persistence/         # MyBatis-Plus Mapper / PO / Converter
        ├── client/              # FileDocClient、MessageDocClient、AgentDocClient
        ├── websocket/           # M2 Yjs 协作（可选）
        └── config/              # MyBatis、租户、Redis、协作开关
```

约束：

1. `interfaces` 只依赖 `application`，不直接调用 Mapper。
2. `domain` 不依赖 Spring/MyBatis；仓储以接口表达。
3. `application` 管理本地事务；外部 HTTP 调用不放在事务内，事务提交后再发通知。
4. 查询侧允许 `application → infrastructure` 的只读查询服务，不为简单列表强上 CQRS。
5. 写操作统一加 `@AuditLog`，不记录正文。
6. 复用 starter 的 `TenantContext`、租户行级拦截、`Result/PageResult`、`BizException`、`@RequirePermission`、`snowflake id`。
7. 服务内不新建身份/权限表，只存 `userId` 与空间成员关系。

### 4.3 事务与并发

- 保存页面：**短事务** `SELECT ... FOR UPDATE` 可选 + `UPDATE ... WHERE revision=?`；插入版本快照同事务；提交后再做通知/索引。
- 外部上传：先校验页面编辑权限 → 调 `whale-file` 上传（事务外）→ 登记 `doc_page_attachment`（短事务）；登记失败则记录孤儿文件，后台清理。
- 通知：`@TransactionalEventListener(phase=AFTER_COMMIT)`；失败只告警，不回滚。
- 自动保存：前端 1.5s debounce；服务端按 `(pageId,userId)` Redis 令牌桶限流，防止刷接口。

---

## 五、数据模型

> 所有表默认含 `tenant_id`、`create_time`、`update_time`、`deleted`；append-only 表例外。字符集 `utf8mb4`。

### 5.1 M1 核心表

#### `doc_space`

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | 雪花 ID |
| `tenant_id` | BIGINT | 租户 |
| `space_key` | VARCHAR(32) | 租户内唯一，创建后不可改 |
| `name` | VARCHAR(100) | 空间名 |
| `description` | VARCHAR(500) | 描述 |
| `icon` | VARCHAR(64) | 图标标识 |
| `visibility` | VARCHAR(16) | `PUBLIC` / `MEMBER` |
| `owner_id` | BIGINT | 创建者/当前 owner |
| `status` | TINYINT | 1 正常 / 0 归档 |
| `sort_no` | INT | 我的空间排序 |
| `deleted` | TINYINT | 逻辑删除 |

索引：`uk_doc_space_key(tenant_id, space_key)`；`idx_doc_space_owner(tenant_id, owner_id, deleted)`；`idx_doc_space_visibility(tenant_id, visibility, status, deleted)`。

#### `doc_space_member`

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | |
| `tenant_id` | BIGINT | |
| `space_id` | BIGINT | 空间 |
| `user_id` | BIGINT | 用户 |
| `member_role` | VARCHAR(16) | `OWNER/ADMIN/EDITOR/READER` |
| `deleted` | TINYINT | |

索引：`uk_doc_space_member(tenant_id, space_id, user_id)`；`idx_doc_member_user(tenant_id, user_id, deleted)`。

> 移除成员为逻辑删除；重新添加复用同一行（`deleted=0` + 新角色），避免唯一键冲突。OWNER 转让需在同一事务内更新 `doc_space.owner_id` 与原/新 OWNER 的成员角色。

#### `doc_page`

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | |
| `tenant_id` | BIGINT | |
| `space_id` | BIGINT | |
| `parent_id` | BIGINT | 0=根 |
| `title` | VARCHAR(200) | |
| `slug` | VARCHAR(128) | 空间内唯一 |
| `page_type` | VARCHAR(8) | `PAGE` / `FOLDER` |
| `content_json` | LONGTEXT | TipTap/ProseMirror JSON；应用层 ≤2MB |
| `content_text` | LONGTEXT | 前端 `editor.getText()` 投影，用于搜索；≤1MB |
| `status` | VARCHAR(16) | `DRAFT/PUBLISHED/ARCHIVED`（M3 可选 `PENDING_APPROVAL`） |
| `revision` | BIGINT | 乐观锁版本，每次保存 +1 |
| `published_revision` | BIGINT | 最近发布时的 revision |
| `sort_no` | BIGINT | 同级排序 |
| `icon` | VARCHAR(64) | |
| `author_id` | BIGINT | 创建人 |
| `last_editor_id` | BIGINT | 最近编辑人 |
| `published_at` | DATETIME | |
| `archived_at` | DATETIME | |
| `deleted` | TINYINT | |
| M2 增列 | `deleted_at` / `deleted_by` / `is_restricted` | 回收站/页面级权限 |

索引：`uk_doc_page_slug(tenant_id, space_id, slug)`；`idx_doc_page_tree(tenant_id, space_id, parent_id, deleted, sort_no)`；`idx_doc_page_status(tenant_id, space_id, status, deleted)`；`idx_doc_page_update(tenant_id, update_time)`。

> 删除时把 `slug` 改写为 `{slug}-trash-{id}`，保证唯一键不与新页面冲突；M2 回收站恢复时恢复可用的 `slug`（若被占用则生成 `p-{id}`）。

#### `doc_page_version`

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | |
| `tenant_id` | BIGINT | |
| `page_id` | BIGINT | |
| `revision_no` | BIGINT | 快照对应的 revision |
| `title` | VARCHAR(200) | |
| `content_json` | LONGTEXT | |
| `content_text` | LONGTEXT | |
| `version_kind` | VARCHAR(16) | `MANUAL/PUBLISH/AUTO/RESTORE/IMPORT` |
| `change_summary` | VARCHAR(200) | |
| `editor_id` | BIGINT | |
| `content_hash` | CHAR(64) | SHA-256(JSON)，用于去重 |
| `create_time` | DATETIME | |

索引：`uk_doc_version(tenant_id, page_id, revision_no)`；`idx_doc_version_time(tenant_id, page_id, create_time)`。
**append-only**：不设 `update_time/deleted`。

#### `doc_page_attachment`

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | |
| `tenant_id` | BIGINT | |
| `page_id` | BIGINT | |
| `file_id` | BIGINT | `whale-file` 文件 ID |
| `original_name` | VARCHAR(255) | |
| `content_type` | VARCHAR(128) | |
| `file_size` | BIGINT | |
| `usage_type` | VARCHAR(16) | `ATTACHMENT` / `INLINE` |
| `sort_no` | INT | |
| `deleted` | TINYINT | |

索引：`uk_doc_attachment(tenant_id, page_id, file_id)`；`idx_doc_attachment_file(tenant_id, file_id)`。

#### `doc_comment`

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | |
| `tenant_id` | BIGINT | |
| `page_id` | BIGINT | |
| `parent_id` | BIGINT | 0=主评论 |
| `anchor_block_id` | VARCHAR(64) | TipTap block id |
| `anchor_text` | VARCHAR(500) | 锚点摘要 |
| `content` | VARCHAR(2000) | |
| `author_id` | BIGINT | |
| `resolved` | TINYINT | |
| `resolved_by` | BIGINT | |
| `resolved_at` | DATETIME | |
| `deleted` | TINYINT | |

索引：`idx_doc_comment_page(tenant_id, page_id, resolved, create_time)`；`idx_doc_comment_parent(tenant_id, page_id, parent_id)`。

#### `doc_page_star`

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | |
| `tenant_id` | BIGINT | |
| `user_id` | BIGINT | |
| `page_id` | BIGINT | |
| `deleted` | TINYINT | 取消收藏置 1，重新收藏复用行 |

索引：`uk_doc_star(tenant_id, user_id, page_id)`；`idx_doc_star_user(tenant_id, user_id, create_time)`。

### 5.2 M2/M3 增量表

**已落地（M2/M3）：**

| 表/列 | 用途 | 关键列 |
|---|---|---|
| `doc_page.deleted_at/deleted_by/trash_root_id` | M2 回收站 | 子树软删除、恢复根标识；`idx_doc_page_recycle` |
| `doc_page_share` | 登录内分享 | `page_id,share_token,share_role,expires_at,status,created_by`；撤销时 token 改写为 `revoked-{id}` |
| `doc_template` | 模板 | `space_id,name,description,page_type,icon,content_json,content_text,status,sort_no,author_id`；`space_id=0` 表示租户级模板 |
| `doc_page.approval_instance_id/approval_status/approval_submitted_at` | M3 发布审批 | `PENDING/APPROVED/REJECTED`；`idx_doc_page_approval(tenant_id,status,approval_instance_id)` |
| `doc_page.archive_item_id/archive_file_id/archive_at` | M3 归档追溯 | 档案条目 ID、快照文件 ID、入档时间 |

**M2 已落地（页面级权限/标签/反链/动态/最近访问/评论未读）：**

| 表 | 用途 | 关键列 |
|---|---|---|
| `doc_page.is_restricted` | 页面级权限开关 | `TINYINT NOT NULL DEFAULT 0`；开启后仅空间 `OWNER/ADMIN` 与 ACL 用户可见 |
| `doc_page` 全文索引 | 中文检索 | `FULLTEXT KEY ft_doc_page_text (title, content_text) WITH PARSER ngram` |
| `doc_tag` | 空间标签 | `space_id,name,color`；`uk(tenant,space,name)` |
| `doc_page_tag` | 页面标签 | `page_id,tag_id`；`uk(tenant,page,tag)`；物理删除避免唯一键冲突 |
| `doc_page_link` | 页面引用/反向链接 | `source_page_id,target_page_id,link_text`；`idx(target)`、`idx(source)`；正文保存时全量重建 |
| `doc_page_activity` | 页面动态 | `page_id,action,actor_id,detail`；append-only，无 `update_time/deleted` |
| `doc_page_acl` | 页面级权限 | `page_id,user_id,acl_role(READ/EDIT)`；`uk(tenant,page,user)`；仅 `is_restricted=1` 时生效 |
| `doc_page_recent` | 最近访问 | `user_id,page_id,visited_at`；`uk(tenant,user,page)`，访问时 upsert |
| `doc_comment_read` | 评论未读游标 | `page_id,user_id,last_read_comment_id,last_read_at`；`uk(tenant,page,user)` |
| `doc_import_job` | 导入任务 | `space_id,source_file_id,status,total,succeeded,failed`（M3 首版为同步导入，不落任务表） |

### 5.3 内容格式

- 正文使用 TipTap（ProseMirror）JSON，顶部 `type=doc`；允许节点：段落、标题 1–4、有序/无序/任务列表、引用、代码块、分割线、表格、图片、提及、页面引用。
- 禁止存储原始 HTML 节点；粘贴内容由编辑器 schema 过滤。
- `content_text` 由服务端从 JSON 的 `text` 节点提取（块级节点补换行），用于搜索、摘要、AI 索引；不接受前端提交值作为权威数据，前端可传仅用于本地预览；提取结果 ≤1MB。
- 图片节点只保存 `attachmentId`（可带 `fileId` 用于去重/清理）及可选 `width/align`，不保存外部 URL；预览经 `/doc/page/{pageId}/attachment/{attachmentId}/content` 代理，避免把 `whale-file` 权限暴露给读者。

---

## 六、API 合同

> 统一返回 `Result<T>` / `PageResult<T>`；分页上限 200；所有接口经网关，`/doc/internal/**` 仅内网。

### 6.1 空间与成员

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/doc/space/page` | `sys:doc:space:list` | 我可访问空间分页（公开 + 成员） |
| GET | `/doc/space/my` | `sys:doc:space:list` | 我加入/拥有的空间 |
| GET | `/doc/space/{id}` | `sys:doc:space:list` | 空间详情 + 我的角色 |
| POST | `/doc/space` | `sys:doc:space:add` | 创建空间，创建者自动 OWNER |
| PUT | `/doc/space/{id}` | `sys:doc:space:manage` | 改名/描述/图标/可见性 |
| DELETE | `/doc/space/{id}` | `sys:doc:space:manage` | 归档空间（OWNER） |
| GET | `/doc/space/{id}/member` | `sys:doc:member:list` | 成员列表 |
| POST | `/doc/space/{id}/member` | `sys:doc:member:manage` | 添加成员/批量添加 |
| PUT | `/doc/space/{spaceId}/member/{memberId}` | `sys:doc:member:manage` | 调整角色（OWNER 转让同事务） |
| DELETE | `/doc/space/{spaceId}/member/{memberId}` | `sys:doc:member:manage` | 移除成员（不可移除最后 OWNER） |
| GET | `/doc/space/{id}/tree` | `sys:doc:page:list` | 页面树（M1 全量；M2 可懒加载） |

### 6.2 页面与版本

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/doc/page` | `sys:doc:page:add` | 新建页面/目录 |
| GET | `/doc/page/{id}` | `sys:doc:page:read` | 详情 + 正文 + `canEdit/canComment/canManage` + `revision` |
| GET | `/doc/page/by-slug` | `sys:doc:page:read` | `spaceId + slug` 定位 |
| PUT | `/doc/page/{id}` | `sys:doc:page:edit` | 保存正文/标题，必须传 `baseRevision` |
| PUT | `/doc/page/{id}/title` | `sys:doc:page:edit` | 仅改标题（同 revision 规则） |
| POST | `/doc/page/{id}/move` | `sys:doc:page:move` | 改父级/排序 |
| POST | `/doc/page/{id}/publish` | `sys:doc:page:publish` | DRAFT→PUBLISHED |
| POST | `/doc/page/{id}/archive` | `sys:doc:page:archive` | →ARCHIVED |
| POST | `/doc/page/{id}/restore` | `sys:doc:page:edit` | ARCHIVED→DRAFT（M2 回收站恢复另列） |
| DELETE | `/doc/page/{id}` | `sys:doc:page:delete` | M1 仅无子页面；M2 子树入回收站 |
| GET | `/doc/page/{id}/version/page` | `sys:doc:version:list` | 版本分页 |
| POST | `/doc/page/{id}/version` | `sys:doc:page:edit` | 手动保存当前 revision 快照（可带 `changeSummary`） |
| GET | `/doc/page/{id}/version/{revision}` | `sys:doc:version:list` | 版本详情 |
| POST | `/doc/page/{id}/version/{revision}/restore` | `sys:doc:version:restore` | 以旧正文生成新 revision |

**保存请求示例：**

```json
{
  "baseRevision": 12,
  "title": "新人入职指南",
  "contentJson": { "type": "doc", "content": [] },
  "changeSummary": "补充权限申请流程"
}
```

**保存响应示例：**

```json
{ "id": 1001, "revision": 13, "status": "DRAFT", "savedAt": "2026-10-29T10:00:00" }
```

冲突返回业务码 `40901`（HTTP 200 + `Result.code=40901`，或统一异常映射为 409；以项目现有异常规范为准），前端必须保留本地草稿并提示「加载最新 / 覆盖（需二次确认）/ 对比」。

#### 6.2.1 回收站（M2 已落地）

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/doc/space/{spaceId}/recycle/page` | `sys:doc:page:list` | 分页查询回收站根节点（`childCount`、原父级标题、删除人/时间） |
| POST | `/doc/page/{id}/trash/restore` | `sys:doc:page:edit` | 恢复子树；仅允许恢复根节点（`40008`），原父级必须存活且同空间（`40007`） |
| DELETE | `/doc/page/{id}/purge` | `sys:doc:page:delete` | 物理清除子树（版本/评论/附件引用/收藏 + 页面行） |
| DELETE | `/doc/space/{spaceId}/recycle` | `sys:doc:page:delete` | 清空空间回收站（分批 200 条） |

删除语义：`DELETE /doc/page/{id}` 将整棵子树 `deleted=1`，写入 `deleted_at/deleted_by/trash_root_id`，并把 `slug` 改写为 `{slug}-trash-{id}`；评论、附件引用与收藏保留，恢复后继续可见。
恢复时优先还原原 `slug`，冲突回退 `p-{id}`，并校验层级深度上限。`DocRecycleCleanupJob` 每天 03:30 清理超过 `whale.doc.recycle-retention-days`（默认 30 天）的根节点。

### 6.3 附件、评论、搜索、收藏

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/doc/page/{id}/attachment` | `sys:doc:attachment:manage` | multipart；校验编辑权限后转 `file/internal/upload`（桶 `whale-doc`），登记引用 |
| GET | `/doc/page/{id}/attachment` | `sys:doc:page:read` | 附件列表 |
| DELETE | `/doc/page/{id}/attachment/{attachmentId}` | `sys:doc:attachment:manage` | 解除引用，不删除文件 |
| GET | `/doc/page/{id}/attachment/{attachmentId}/content` | `sys:doc:page:read` | 代理文件内容；图片 inline，其余 attachment；`nosniff` |
| GET | `/doc/page/{id}/comment` | `sys:doc:comment:list` | 评论列表（含已解决过滤；需页面可读） |
| POST | `/doc/page/{id}/comment` | `sys:doc:comment:add` | 新增评论/回复（需成员或 ACL 授权） |
| GET | `/doc/page/{id}/comment/unread` | `sys:doc:comment:list` | 未读数（`last_read_comment_id` 之后、排除本人） |
| POST | `/doc/page/{id}/comment/read` | `sys:doc:comment:list` | 标记已读（游标推进至页面最大评论 ID） |
| PUT | `/doc/comment/{id}/resolve` | `sys:doc:comment:manage` | 解决/重新打开 |
| DELETE | `/doc/comment/{id}` | `sys:doc:comment:manage` | 逻辑删除 |
| GET | `/doc/search` | `sys:doc:search` | 关键词、空间、标签过滤；返回高亮片段 |
| POST | `/doc/page/{id}/star` | `sys:doc:page:read` | 收藏/取消收藏（幂等） |
| GET | `/doc/page/star/page` | `sys:doc:page:read` | 我的收藏（过滤 ACL 后不可读页面） |
| GET | `/doc/recent` | `sys:doc:page:read` | 我最近访问（服务端，默认 10、上限 20） |
| GET/PUT | `/doc/page/{id}/acl` | `sys:doc:page:acl` | 页面级权限查询/全量替换（空间 ADMIN+） |
| GET/POST | `/doc/space/{spaceId}/tag` | `sys:doc:page:read` / `sys:doc:tag:manage` | 空间标签列表/创建 |
| PUT/DELETE | `/doc/tag/{id}` | `sys:doc:tag:manage` | 标签重命名/物理删除 |
| GET/PUT | `/doc/page/{id}/tag` | `sys:doc:page:read` / `sys:doc:tag:manage` | 页面标签查询/全量替换（≤20） |
| GET | `/doc/page/{id}/activity` | `sys:doc:page:read` | 页面动态分页 |
| GET | `/doc/page/{id}/backlink` | `sys:doc:page:read` | 反向链接（仅返回当前用户仍可读的源页面） |

> 附件上传响应：`{attachmentId, fileId, originalName, contentType, fileSize, usageType}`；正文图片节点写入 `attachmentId`。

搜索可见性 SQL 口径（实现见 `DocPageMapper#search/countSearch`，两侧条件保持一致）：

```sql
SELECT ...
FROM doc_page p
JOIN doc_space s ON s.id = p.space_id AND s.deleted = 0 AND s.status = 1
WHERE p.tenant_id = ? AND s.tenant_id = ?
  AND p.deleted = 0
  AND p.page_type = 'PAGE'
  AND p.status = 'PUBLISHED'
  AND (? IS NULL OR p.space_id = ?)                 -- 空间过滤
  AND ((p.is_restricted = 0 AND (s.visibility = 'PUBLIC' OR EXISTS (member)))
       OR (p.is_restricted = 1 AND (EXISTS (ACL) OR EXISTS (OWNER/ADMIN member))))
  AND (? IS NULL OR EXISTS (SELECT 1 FROM doc_page_tag pt
                            WHERE pt.page_id = p.id AND pt.tag_id = ? AND pt.deleted = 0))
  -- 关键词≥2 使用全文索引；关键词<2 回退 LIKE（二者择一，由应用层决定）
  AND (MATCH(p.title, p.content_text) AGAINST (? IN NATURAL LANGUAGE MODE)
       OR (p.title LIKE ? OR p.content_text LIKE ?))
ORDER BY <MATCH 相关度 DESC,> p.update_time DESC, p.id DESC
```

- `FULLTEXT ... WITH PARSER ngram` 适配中文；关键词长度 ≥2 走全文索引，1 个字符回退 `LIKE`。
- 搜索与 `countSearch` 均标注 `@InterceptorIgnore(tenantLine="true")`，SQL 显式携带租户条件；避免 JSqlParser 解析 `MATCH ... AGAINST` 失败。
- 受限页面（`is_restricted=1`）仅空间 `OWNER/ADMIN` 与 ACL 授权用户可命中；成员可见的草稿仍不进入本搜索。
- 标签列表的 `pageCount` 通过 `COUNT(CASE WHEN ... THEN 1 END)` 统计，仅计数当前用户可读页面，标签本身始终返回。

### 6.4 内网接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/doc/internal/page/changes?since=&current=&size=` | 已发布且公开空间页面增量变更（含删除/归档墓碑与 `restricted` 标记），供 agent 消费 |
| GET | `/doc/internal/page/{id}` | 页面快照（正文文本、标题、slug、revision、更新时间、可见性）；`is_restricted=1` 返回 `40401` |
| GET | `/doc/internal/refs?fileId=` | 文件被哪些页面引用（用于文件删除前校验/清理；M2） |
| POST | `/doc/internal/ops/orphan-attachment-cleanup` | M5 手工触发无主附件引用清理，返回 `{ removed }`；任务关闭时返回 0 |

内部接口不校验用户权限，但必须携带租户（`X-Tenant-Id`）；网关 `internal-deny` 已屏蔽 `/doc/internal/**`。

> 受限页面语义（M2-4 与 M4 边界）：`changes` 仍返回受限页面并带 `restricted=true`，agent 的 `RemoteDocChange#collectable()` 将其视为不可采集并从索引移除；快照接口直接拒绝，防止 AI 侧通过页面 ID 绕过 ACL。

### 6.5 M2 协作接口（可选）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/doc/ws-ticket` | 生成 60 秒一次性 WS ticket，绑定 `userId/tenantId/pageId`，Redis 存 `whale:doc:ws:ticket:<sha256>` |
| WS | `/doc/ws?ticket=&pageId=` | Yjs 更新/感知广播；网关 `lb:ws://whale-doc`，路由优先级 -1 |

权限模型（M2-4）：`DocAccessGuard` 统一裁决 `canRead / canEdit / canComment / canManage`。受限页面在空间 `OWNER/ADMIN` 之外必须存在 ACL 行；`READ` 可读可评论、`EDIT` 可编辑；非受限页面保持 M1 的空间可见性 + 成员角色语义。页面树、搜索、收藏、评论、附件与分享 token 均复用该守卫，避免旁路。

### 6.6 M3 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/doc/template?spaceId=` | 模板列表（租户级 + 指定空间） |
| GET | `/doc/template/{id}` | 模板详情 |
| POST/PUT/DELETE | `/doc/template`、`/doc/template/{id}` | 模板维护（`sys:doc:template:manage`） |
| POST | `/doc/template/{id}/apply` | 按模板创建页面 |
| POST | `/doc/page/{id}/duplicate` | 页面/子树复制（附件引用重映射） |
| POST | `/doc/page/{id}/import` | Markdown/HTML 导入（Word 可选） |
| GET | `/doc/page/{id}/export?format=md\|html` | 导出 |
| POST | `/doc/page/{id}/submit-approval` | 提交发布审批（开关关闭时直接发布；复用 `sys:doc:page:publish`） |
| GET | `/doc/page/{id}/approval` | 查询审批状态；待审批时向工作流补偿同步 |
| POST | `/doc/internal/approval/callback` | 工作流回调（内网，按 `(pageId,instanceId,PENDING)` 条件更新幂等） |
| GET/POST | `/doc/page/{id}/share` | 分享列表/创建登录内分享链接（`sys:doc:share:manage`） |
| DELETE | `/doc/page/{id}/share/{shareId}` | 撤销分享，立即失效 |
| GET | `/doc/share/{token}` | 校验 token + 当前登录态后返回只读页面（`canEdit=false`） |
| GET | `/doc/meta` | 返回 `approvalEnabled` 等 M3 开关供前端展示 |

### 6.7 实时协作（M2，可选）

1. 客户端先 `POST /doc/ws-ticket` 获取 60 秒一次性 ticket（Redis `whale:doc:collab:ticket:{tenant}:{ticket}` 原子消费）；WS 建连时校验 ticket + 页面写权限，票据立即失效。
2. 该页面存在活跃协作会话（Redis presence 心跳，TTL 180s）时，编辑器以 Yjs 为唯一内容源，**暂停 REST 自动保存**；普通保存接口返回 `40902`（协作模式请走 WS），避免双写；无会话页面仍走 M1 保存。
3. Java WS 节点只中继 base64 编码的 Yjs 更新与 awareness，经 Redis Pub/Sub `whale:doc:collab:channel:{tenant}:{pageId}` 跨实例转发；更新日志 `whale:doc:collab:updates:{tenant}:{pageId}` 保留最近 2000 条、TTL 2 小时，用于断线重放；新客户端无本地状态时由首个客户端以 `seed` 帧写入初始正文。
4. 持久化：服务端用 Redis `SET NX EX` 选出 leader（掉线自动选举），leader 客户端每 `whale.doc.collab-snapshot-seconds`（默认 12s）通过 WS 控制消息 `{"type":"snapshot","title":...,"contentJson":...}` 提交 TipTap JSON；服务端用 Lua 比对锁归属后以页面当前 `revision` 落库，`content_text` 未变化时跳过，并复用版本历史自动快照。无客户端时不强行生成 JSON，更新日志保留待下次重放。
5. 所有客户端离开或开关关闭后，恢复正常 REST 乐观锁；下次保存携带服务端最新 `revision`。
6. 失败降级：WS 不可用/开关关闭时，前端回退 M1 自动保存（`40903` 表示协作不可用），不阻塞编辑；重连采用退避并最多 3 次，重连成功后发送全量状态合并离线修改。

---

## 七、权限模型

### 7.1 平台权限码（预占 251–280）

| ID | 权限码 | M | 说明 |
|---|---|---|---|
| 251 | `sys:doc:space:list` | M1 | 空间列表/详情 |
| 252 | `sys:doc:space:add` | M1 | 创建空间 |
| 253 | `sys:doc:space:manage` | M1 | 改/归档空间 |
| 254 | `sys:doc:member:list` | M1 | 成员列表 |
| 255 | `sys:doc:member:manage` | M1 | 成员增删改 |
| 256 | `sys:doc:page:list` | M1 | 页面树/列表 |
| 257 | `sys:doc:page:read` | M1 | 页面详情/附件读取 |
| 258 | `sys:doc:page:add` | M1 | 新建页面 |
| 259 | `sys:doc:page:edit` | M1 | 保存/改标题/恢复 |
| 260 | `sys:doc:page:delete` | M1 | 删除页面 |
| 261 | `sys:doc:page:move` | M1 | 移动/排序 |
| 262 | `sys:doc:page:publish` | M1 | 发布 |
| 263 | `sys:doc:page:archive` | M1 | 归档 |
| 264 | `sys:doc:version:list` | M1 | 版本列表/详情 |
| 265 | `sys:doc:version:restore` | M1 | 恢复版本 |
| 266 | `sys:doc:attachment:manage` | M1 | 附件上传/解除 |
| 267 | `sys:doc:comment:list` | M1 | 评论列表 |
| 268 | `sys:doc:comment:add` | M1 | 新增评论 |
| 269 | `sys:doc:comment:manage` | M1 | 解决/删除评论 |
| 270 | `sys:doc:search` | M1 | 搜索 |
| 271 | `sys:doc:template:manage` | M3 | 模板管理 |
| 272 | `sys:doc:share:manage` | M3 | 分享管理 |
| 273 | `sys:doc:import` | M3 | 导入 |
| 274 | `sys:doc:export` | M3 | 导出 |
| 275 | `sys:doc:approval` | M3（预留未启用） | 发布审批复用 262 `sys:doc:page:publish`，本码保留给后续独立审批人角色 |
| 276 | `sys:doc:ai` | M4 | 文档 AI 问答/摘要 |
| 277 | `sys:doc:admin` | M4 | 跨空间管理兜底 |

### 7.2 业务角色与授权

| 角色 | 权限范围 | 说明 |
|---|---|---|
| `doc_member` | 251–274 | 具备全部文档功能入口（含 M3 模板/分享/导入/导出）；可创建空间并成为 OWNER；具体编辑/发布/删除/成员管理仍由空间角色二次校验 |
| `doc_manager` | 251–274（M4 增 277） | M1/M3 与 `doc_member` 功能相同，作为文档管理员标记；M4 可授予跨空间兜底 `sys:doc:admin` |
| `tenant_admin` | 251–274（M4 增 277） | 平台租户管理员 |

> 权限码只控制「功能入口」；数据可见性由 `DocAccessGuard` 的空间成员关系二次校验。`sys:doc:admin` 是 M4 兜底，M1 不启用，避免误放开私有空间。
>
> 角色授予：迁移脚本只创建 `doc_manager` / `doc_member` 并给 `tenant_admin` 授权；`doc_member` 由租户管理员按组织范围授予（M1 提供批量授予 SQL/操作说明），不默认全量放开，避免权限扩散。

### 7.3 访问矩阵

| 操作 | 平台权限码 | 数据条件 |
|---|---|---|
| 读公开空间已发布页面 | `page:read` | 租户内任意 `doc_member` |
| 读成员空间/草稿 | `page:read` | `OWNER/ADMIN/EDITOR/READER` |
| 新建页面 | `page:add` | `EDITOR` 及以上 |
| 编辑/移动/附件 | `page:edit/move/attachment:manage` | `EDITOR` 及以上 |
| 发布/归档 | `page:publish/archive` | `EDITOR` 及以上（M3 可收紧到 `ADMIN` + 审批） |
| 删除页面 | `page:delete` | `ADMIN/OWNER` |
| 成员管理 | `member:manage` | `ADMIN/OWNER`；转让 OWNER 仅 `OWNER` |
| 归档空间 | `space:manage` | `OWNER` |
| 评论 | `comment:add` | 可读页面即可；解决/删除为作者或 `ADMIN/OWNER` |

`DocAccessGuard` 只暴露三个方法，避免权限逻辑散落：

```java
void requireRead(Long pageId, Long userId);
void requireEdit(Long pageId, Long userId);
void requireManage(Long spaceId, Long userId);
```

实现顺序：加载页面/空间（含 `deleted`）→ 校验租户 → 校验平台权限码（拦截器已做）→ 校验 `PUBLIC` 已发布或成员关系 → 校验角色层级。

---

## 八、与现有服务的集成契约

### 8.1 文件（whale-file）

- 上传：`POST /file/internal/upload?bucket=whale-doc`，multipart 字段 `file`；返回 `FileUploadVO.id`（文件 ID）及 `originalName/contentType/fileSize` 等。文档服务调用时必须透传 `X-Tenant-Id` / `X-User-Id`（文件服务据此回填上传人并做租户隔离）。
- 读取：`GET /file/internal/{id}/content`，返回流；doc 服务转发时覆盖 `Content-Disposition`（图片 `inline`，其余 `attachment`）并保留 `X-Content-Type-Options: nosniff`。
- 不调用 `/file/internal/{id}/lock`（锁定是档案语义；文档附件不锁定）。
- 页面删除/解除附件不物理删除文件；M2 提供 `/doc/internal/refs?fileId=` 供后续清理任务判断引用。
- 桶名 `whale-doc`；生产 MinIO 可自动建桶，本机存储按现有默认桶策略。

### 8.2 知识 Agent（whale-agent）

**安全前置（已落地）**：`whale-doc` 桶已作为默认值加入 `whale.agent.knowledge.excluded-buckets`（可通过 `WHALE_AGENT_KNOWLEDGE_EXCLUDED_BUCKETS` 覆盖），文件知识同步不会采集私有空间附件；文档只通过 DOC 变更流索引已发布公开正文。

**文件知识桶策略（W5.75 补丁）**：文件采集改为 **default-deny 白名单**，只有命中 `whale.agent.knowledge.included-buckets`（`WHALE_AGENT_KNOWLEDGE_INCLUDED_BUCKETS`，默认 `bluewhale,whale-archive`，前缀匹配）且不在 `excluded-buckets` 中的桶才会入库；未命中、桶名缺失或白名单置空一律置 `SKIPPED`（`lastError=文件桶未纳入知识库白名单`）。这样个人/部门桶（如 `oa-*`、`whale-im`）默认不会进入同租户全员可检索的公开知识池；新增知识库桶需显式配置。

**DOC 知识源（M4 已落地）**

1. `whale-doc` 提供 `/doc/internal/page/changes`（游标为 `update_time`，返回租户内全部页面，含 `spaceVisibility/spaceActive/deleted` 墓碑）与 `/doc/internal/page/{id}` 快照（仅 `PUBLISHED + PUBLIC + 空间启用 + page_type=PAGE`，并同样返回 `spaceVisibility/spaceActive`，供单页重建复用采集判定）。
   - 游标为**闭区间**（`update_time >= since`）：`doc_page.update_time` 仅精确到秒，同一秒内的发布/归档/恢复会产生相同时间戳，开区间会漏掉变更；采集方按 revision/状态判重，重复拉取不会重复写索引。分页期间固定起始游标，避免 offset 与游标推进叠加跳过同秒页面。
2. `whale-agent` 的 `DocKnowledgeClient` + `DocKnowledgeSyncService`：
   - 只采集 `PUBLISHED + PUBLIC + 空间启用 + 未删除` 正文页面，目录节点（FOLDER）不采集；
   - 复用 `FileChunker`、`SensitiveContentMasker`、`AgentIndexService`；
   - 台账 `agent_knowledge_doc`：`page_id` 唯一，`revision/content_hash` 判重，`source_update_time` 作增量游标，失败指数退避、上限 3 次，`status=PENDING/INDEXING/INDEXED/SKIPPED/FAILED`；
   - 索引 kind=`doc_page`、路径 `docpage:{pageId}`，权限码 `sys:doc:page:read`；
   - 检索结果附 `url=/doc/space/{spaceId}?pageId={pageId}`（Lucene 存储字段 `doc_page_id/space_id/url`，`/agent/search` 与问答 `CitationVO` 均透出）。
3. 页面归档/删除/撤销发布/空间转私有或停用时，变更流带墓碑，Agent 删除对应索引块；`/agent/knowledge/doc/sync`、`/doc/{pageId}/reindex`、`/doc/{pageId}/enabled`、`/doc/rebuild` 提供手动运维入口。
4. 开关：`whale.agent.knowledge.doc-enabled`（默认 `false`）、`doc-sync-on-startup`、`doc-sync-interval-seconds`、`doc-url`、`doc-permission-code`、`doc-max-chars`。

**备选（未采用）：** 发布时导出 Markdown 到独立桶复用文件知识同步；需维护「桶 → 权限码/URL 模板」映射，长期不采用。

### 8.3 消息（whale-message）/ IM

- 评论提及、审批待办、空间成员变更：`POST /message/internal/send`，显式传 `tenantId`；`idempotencyKey` 建议 `doc:{event}:{id}:{userId}`；INAPP 渠道。
- 页面正文 `@user` 提及只在保存时解析并生成通知，不阻塞保存；通知失败只告警。
- 与 `whale-im` 的会话跳转由消息模板携带 `url`，文档服务不直接写 IM 会话。

### 8.4 工作流（whale-workflow）

- M3 发布审批：`businessKey=doc:{pageId}:v{revision}`；提交后页面 `PENDING_APPROVAL`，审批通过回调置 `PUBLISHED`，驳回置 `DRAFT`。
- 回调必须幂等（流程实例 ID + 页面 revision），重复回调不重复变更。
- 默认开关 `whale.doc.approval.enabled=false`，未开启时发布直接生效。

### 8.5 档案（whale-archive）

- M3/M4 归档：页面生成 Markdown/HTML 快照 → `POST /file/internal/upload` 到档案桶 → `POST /archive/internal/items`，`sourceType=DOC_PAGE`、`sourceId=pageId`，按 `(sourceType,sourceId)` 幂等。
- **依赖档案服务增量改造**：`ArchiveIngestService.ALLOWED_SOURCE_TYPES` 当前仅允许 `CONTRACT`，需加入 `DOC_PAGE` 并登记 `definitionCode=DOC`；`snapshotJson` 必填且 ≤1MB，文档侧传页面元数据 + 截断正文，完整快照放 `originalFileId`。
- 归档后文档仍可读；删除/归档文档不删除档案快照。

### 8.6 审计与 i18n

- `@AuditLog(operation="保存文档", type=OperateType.UPDATE, targetType="doc_page", targetId="#id")`，不记录正文。
- 服务内 `i18n/messages*.properties` 注册文档错误码；前端文案走平台 i18n（若已接入）。

---

## 九、安全、性能与可观测性

### 9.1 安全

1. **租户隔离**：所有表带 `tenant_id`，依赖 starter 租户拦截；跨租户 ID 访问返回 404。
2. **越权**：所有页面/附件/评论接口先 `requireRead/requireEdit`；附件内容必须校验「附件属于该页面」，禁止仅凭 `fileId` 读取。
3. **XSS**：只存 TipTap JSON，不存原始 HTML；M5 导出侧对文本转义，链接协议白名单 `http/https/mailto/相对路径/#`，`javascript:`/`data:`/`vbscript:` 降级纯文本；图片仅允许 `http/https` 与站内路径；SVG 禁止 inline；附件响应统一 `nosniff`。
4. **越权分享**：M1 不做匿名分享；M3 分享 token 需登录 + 页面可读 + 过期校验。
5. **文件旁路**：`doc_member` 默认不授予 `sys:file:download`；文档附件仅经 doc 代理读取。若用户同时拥有其他角色的文件下载权限，仍可能凭文件 ID 访问，需在 M2 评估 `whale-file` 按桶/来源 ACL 或文档附件专用桶策略。
6. **暴力保存**：网关统一限流 + `revision` 条件更新；正文大小、节点深度、标题长度服务端校验。服务内未实现按 `(pageId,userId)` 令牌桶，若出现刷接口再按 M5 运行手册追加。
7. **审计脱敏**：审计与日志不写正文；错误日志截断。
8. **AI 泄漏**：只索引 `PUBLISHED + PUBLIC + 未限制` 页面；`MEMBER` 空间、页面级 ACL、回收站内容不进入公开知识索引。

### 9.2 性能

| 场景 | 目标 | 手段 |
|---|---|---|
| 页面详情 | P95 < 300ms | 单行查询；正文按需返回；附件/评论独立接口 |
| 保存 | P95 < 500ms | 单行 UPDATE + 条件插入版本；不做全树重算 |
| 空间树 5k 页 | < 1s | 一次查询 + 内存建树；M2 懒加载 |
| 搜索 2 万页 | < 500ms | M1 LIKE 限量；M2 FULLTEXT ngram |
| 协作 | 输入延迟 < 150ms | M2 Yjs + Redis 广播；非核心链路可关闭 |

### 9.3 可观测性

- Micrometer（已实现）：`http_server_requests_seconds`（开启直方图桶）、`doc_page_save_conflict_total`、`doc_search_seconds{mode=fulltext/like}`、`doc_recycle_purged_total`、`doc_orphan_attachment_removed_total`、`doc_orphan_attachment_failed_total`。
- 健康检查 `/actuator/health`；Prometheus 抓取 `127.0.0.1:8095`（`deploy/monitoring/prometheus.yml` 的 `whale-doc` job）。
- 告警：`DocSaveConflictSpike`、`DocSearchP95Slow`、`DocOrphanCleanupFailed`；SLO-4/5 见 [slo.md](../ops/slo.md)。
- 审计写操作；关键状态变更（发布/归档/恢复/成员变更）额外记录业务日志。
- 运维入口：[doc-service-ops.md](../ops/doc-service-ops.md)（部署、配置、备份恢复、清理、排障、容量、上线清单、回滚）。

### 9.4 M5 上线硬化补充

1. **导出安全**：HTML/Markdown 导出共用 `DocMarkupSupport.safeLinkHref/safeImageSrc`，协议白名单外一律降级为文本；导入 Markdown 时同样过滤链接协议。
2. **备份恢复**：`scripts/ops/doc-backup-restore.ps1` 用 `mysqldump --single-transaction` 备份到独立校验库，校验 14 张核心表、ngram 索引、`is_restricted` 与页面行数后清理校验库。
3. **孤儿附件引用**：`DocOrphanAttachmentCleanupJob` 每天 04:30 清理页面已物理不存在且超过 24h 的 `doc_page_attachment` 行；只删引用，不删 `whale-file` 字节。内网可手工触发 `POST /doc/internal/ops/orphan-attachment-cleanup`。
4. **受限内网接口**：`/doc/internal/**` 由网关统一 403，运维触发接口仅限内网直连。
5. **前端基础适配**：编辑器图标按钮补 `aria-label`；≤768px 空间工作台纵向堆叠、树限高、编辑器最小高度与内边距收敛。

---

## 十、设计决策记录（ADR 摘要）

| 编号 | 决策 | 理由 | 备选/代价 |
|---|---|---|---|
| D1 | 新建独立 `whale-doc` 服务，不扩展 `whale-file` | 文档有版本/树/评论/ACL 生命周期；文件服务只负责字节 | 代价：多一个服务与库 |
| D2 | 正文存 TipTap JSON + 纯文本投影，不存 HTML | 结构化、可校验、避免 XSS；纯文本供搜索/AI | 代价：导出需前端或模板转换 |
| D3 | M1 乐观锁 `revision`，不做实时协作 | 先保证不丢数据、上线快 | 代价：同时编辑需冲突提示；M2 引入 Yjs |
| D4 | M1 空间级权限，页面级 ACL 放 M2 | 控制复杂度；先覆盖 80% 场景 | 代价：公开空间内无法单页私密 |
| D5 | M1 搜索用数据库 LIKE，M2 升级 FULLTEXT ngram | 避免过早引入搜索引擎 | 代价：大数据量下 M1 搜索有限 |
| D6 | 核心保存链路不引入 MQ | 本地事务 + 条件更新即可保证一致性 | 代价：通知/索引最终一致，需补偿 |
| D7 | AI 只索引公开已发布页面 | 最小权限泄漏面，避免复杂按人过滤 | 代价：私有知识不进入通用问答；M4 再评估 |
| D8 | 实时协作 M2 采用 Yjs + Java WS 中继，默认开关关闭 | 不新增 Node 运行时；保留回退 | 代价：快照持久化需 leader 客户端；复杂场景可换 Hocuspocus |
| D9 | 前端新建 qiankun 子应用 `whale-doc`（8110，base `/doc`） | 与现有微前端体系一致；独立迭代 | 代价：需注册基座与菜单 |
| D10 | 文件上传走 `file/internal/upload`，读取经 doc 代理 | 读者不需要 `sys:file:download`；文档 ACL 统一收口 | 代价：多一跳代理与带宽 |

---

## 十一、开放问题（需产品/架构确认）

| # | 问题 | 默认建议 |
|---|---|---|
| Q1 | M1 是否必须实时多人协作？ | 否，M1 乐观锁；M2 再上 |
| Q2 | 是否需要页面级私密/部门可见？ | M2 页面级 ACL；M1 空间级 |
| Q3 | 是否允许普通成员创建空间？ | 是（`doc_member` + `space:add`），创建者为 OWNER |
| Q4 | 是否需要 Word/PDF 导入导出？ | M3 先 Markdown/HTML；Word/PDF 按需求追加 |
| Q5 | 单页正文/附件上限？ | 正文 2MB、附件 50MB（沿用文件服务） |
| Q6 | AI 是否只索引公开已发布页面？ | 是；私有页面不进入通用问答 |
| Q7 | 是否需要匿名公开分享？ | 否；M3 仅登录内分享 |
| Q8 | 发布是否需要审批？ | 默认关闭；M3 配置开启 |
