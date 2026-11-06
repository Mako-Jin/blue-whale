# Wave 5.88 M-P2：党建内容与先审后发 — 实施计划

## Context

`whale-party`（9009 / `/party/**` / `bluewhale_party`）已完成 M-P0 底座与 M-P1 党建台账（7 张表 / 28 端点）。M-P2 补齐党建内容域：分类/封面/正文/附件、先审后发状态机、可见范围、阅读记录。

验收口径（5.88 计划 §4）：
1. 未审内容不可见（作者与审核人除外）；
2. 发布态修改必须重新走审核（改回待审，不允许编辑即生效）；
3. 下线保留历史与阅读数；
4. 退回必须填审核意见。

现状核对：权限 1959–1961、菜单 475–476、错误码 43030–43036 均未占用；`content` 上下文已在 ArchUnit 门禁中预留；`PartyProperties.contentPageLimit` 已存在。

## 关键设计

### 状态机（单向）

```
DRAFT ──submit──► PENDING ──approve──► PUBLISHED ──offline──► OFFLINE（终态）
  ▲                 │
  └─────reject──────┘（退回：opinion 必填）
PUBLISHED ──edit──► PENDING（发布态修改自动退回待审，记 EDIT 审计）
```

- 仅 DRAFT 可编辑；PENDING 不可编辑；OFFLINE 终态只读；PUBLISHED 编辑即转 PENDING。
- 每次状态流转写 `pty_content_audit`（SUBMIT/APPROVE/REJECT/OFFLINE/EDIT + from/to + opinion）。

### 可见范围

- `ALL_STAFF`：登录用户均可读；
- `ALL_MEMBERS`：`pty_member.user_id` 命中在册党员（`party_admin` / `tenant_admin` 视为党员口径）；
- `BRANCHES`：`branch_scope`（逗号分隔支部 ID）命中操作人所属支部。
- 阅读列表只返回 `PUBLISHED`；管理列表可看全部状态（需 `content:list`）。详情对非作者/非审核人强制可见性校验。
- 阅读记录：`pty_content_read` 唯一键 `(tenant_id, content_id, user_id)`，首次阅读累加 `pty_content.read_count`，重复阅读只刷新时间。

### 编号

| 项 | 值 |
|---|---|
| 迁移 | `V4__party_content.sql`（`pty_content` / `pty_content_audit` / `pty_content_read`） |
| 权限 | 1959 `sys:party:content:list` / 1960 `sys:party:content:manage` / 1961 `sys:party:content:audit` |
| 菜单 | 475 党建内容（管理）/ 476 党建园地（阅读） |
| 错误码 | 43030 不存在 / 43031 状态冲突 / 43032 字段非法 / 43033 退回意见必填 / 43034 无权访问 / 43035 可见范围非法 / 43036 支部范围无效 |
| Auth 迁移 | `migration-5.88-party-m2.sql`（幂等 NOT EXISTS） |

授权：`party_admin` / `tenant_admin` 1959–1961；`branch_secretary` 1959–1960（可投稿、可查看，不给审核权，保留审核牵制）。

## 后端改动（`backend/whale-apps/whale-party`）

新增 `content` 上下文，严格沿用 M-P1 四层结构：

- `domain/model/ContentStatus.java`、`ContentVisibility.java`、`AuditAction.java`、`PartyContent.java`、`ContentAudit.java`、`ContentRead.java`、`ContentQuery.java`、`ContentPortalQuery.java`、`ContentScope.java`
- `domain/repository/ContentRepository.java`
- `infrastructure/persistence/entity/{PartyContentEntity,ContentAuditEntity,ContentReadEntity}.java`
- `infrastructure/persistence/mapper/{PartyContentMapper,ContentAuditMapper,ContentReadMapper}.java`
- `infrastructure/persistence/repository/ContentRepositoryImpl.java`
- `application/command/{ContentSaveCommand,ContentRejectCommand}.java`
- `application/service/ContentAppService.java` + `impl/ContentAppServiceImpl.java`
- `application/view/{ContentView,ContentAuditView,ContentReadView}.java`
- `interfaces/controller/PartyContentController.java`
- 修改 `common/PartyErrorCode.java`、`common/PartyPermissions.java`

端点（11 个）：
- `GET /party/content/page`（content:list，管理列表）
- `GET /party/content/{id}`（content:list，详情；未审仅作者/审核人）
- `POST /party/content`、`PUT /party/content/{id}`（content:manage）
- `POST /party/content/{id}/submit`（content:manage）
- `POST /party/content/{id}/approve`、`/reject`、`/offline`（content:audit；reject 的 opinion 必填）
- `GET /party/content/{id}/audits`（content:list，审核轨迹）
- `GET /party/content/portal/page`（browse，阅读列表，仅已发布 + 可见范围）
- `POST /party/content/{id}/read`（browse，记录阅读并返回阅读数）
- `GET /party/content/{id}/reads`（content:list，阅读记录）

写接口全部 `@AuditLog` + `@RequirePermission`；领域层不依赖 Spring/MyBatis。

迁移 `V4__party_content.sql`：三张表、`IF NOT EXISTS`、无 DROP/DELETE/INSERT 种子；唯一键 `uk_pty_content_read (tenant_id, content_id, user_id)`；正文 `MEDIUMTEXT`；附件与封面只存 `fileId` 引用。

## Auth

新增 `backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/migration-5.88-party-m2.sql`，按 m0/m1 模板写权限 1959–1961、菜单 475–476、授权（MD5 派生 id + NOT EXISTS）。

## 前端（`frontend/platform-apps/whale-party`）

- `src/api/party.ts`：新增 Content 类型与 11 个接口封装。
- `src/pages/ContentPage.tsx`：管理列表（关键字/分类/状态筛选）、新建/编辑抽屉（分类/标题/摘要/封面 fileId/正文/附件 fileId/可见范围/指定支部）、提交、审核（通过/退回意见）、下线、审核轨迹。
- `src/pages/ContentPortalPage.tsx`：党建园地阅读列表 + 详情 + 阅读登记。
- `src/App.tsx`、`src/layout/PartyLayout.tsx`：增 `/content` 与 `/content/portal` 路由与导航项。

## 文档

- `docs/plans/2026-11-06-wave5.88-party-union-service.md`：状态行 M-P2 ✅、波次表、编号表、变更记录。
- `docs/plans/roadmap.md`：13.7 勾选并记录交付内容。
- `docs/requirements/register.md`：REQ-FUNC-091 M-P2 进展、权限 1959–1961 / 菜单 475–476。
- `docs/service-catalog.md`：服务行表数 10 / 端点 39、前端行新增两页、8116 与 9009 端口行。
- `docs/devlog/2026-11-06.md`：追加 M-P2 章节。
- `docs/reviews/2026-11-06-wave5.88-party-m2.md`：新增检查报告。

## 验证

1. 后端：`$env:JAVA_HOME='D:/tools/installer/jdk/java21/'; mvn -o -pl backend/whale-apps/whale-party test`
   - 新增 `ContentDomainTest`（状态机/可见范围/退回意见）、`ContentAppServiceImplTest`（发布态编辑退回、未审不可见、阅读幂等、审核权限）。
   - `PartyFlywayMigrationScriptTest` 增 V4 三表与阅读唯一键断言。
   - ArchUnit 既有规则自动覆盖 `content` 上下文。
2. 前端：`pnpm --filter whale-party lint` / `build`。
3. 文档：`powershell -File scripts/check-doc-consistency.ps1` PASS。
4. 提交并推送 `origin/main`，产出「需求项 / 完成项 / 待做项 / 已修正问题项」报告。
