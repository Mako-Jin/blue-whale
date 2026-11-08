# OA M5 Step 7d-1：企业文化 / 投票问卷 / 建议箱 实施方案

> 里程碑：Wave 5.82 M5 文化与人员异动（第一批）
> 关联计划：`docs/plans/2026-11-04-wave5.82-oa-panorama.md` §7.5
> 状态：待执行

---

## 一、Context

OA M4 公文与档案已交付并推送（`f6b2ceba`）。M5 拆两批：本批 7d-1 交付「企业文化、投票问卷、建议箱」三个轻量协同能力；7d-2 再交付「人员入转调离」。本批不新增服务、不新增审批流，全部落在 `whale-oa` 单服务与 `bluewhale_oa` 单库；消息投递与 IM 不在本批范围。

生日/周年不建独立日程表，直接由 `oa_employee_profile.birthday` / `hire_date` 派生，由 `culture_visible` 控制是否进入文化墙；匿名建议仅在视图层隐藏提交人，库内保留提交人用于审计追责。

## 二、数据库（V13__culture.sql，追加不修改历史）

新建 7 张表（`CREATE TABLE IF NOT EXISTS`，无 DROP/TRUNCATE/DELETE，逻辑外键）：

| 表 | 关键列 | 约束/索引 |
|---|---|---|
| `oa_culture_event` | event_type(MILESTONE/ACTIVITY/NEWS)、title、content、event_date、cover_file_id、status(DRAFT/PUBLISHED/ARCHIVED)、publisher_id/name、publish_time | `idx_oa_culture_event_status(tenant_id,status,event_date)`、`idx_oa_culture_event_type(tenant_id,event_type)` |
| `oa_culture_wish` | wish_type(BIRTHDAY/ANNIVERSARY)、target_user_id/name、content、sender_id/name、wish_date | `idx_oa_culture_wish_target(tenant_id,target_user_id,wish_date)`、`idx_oa_culture_wish_date(tenant_id,wish_date)` |
| `oa_poll` | title、description、multiple_choice、anonymous、max_choices、start_time、end_time、status(DRAFT/PUBLISHED/CLOSED)、creator_id/name | `idx_oa_poll_status(tenant_id,status,start_time)` |
| `oa_poll_option` | poll_id、option_text、sort_no | `idx_oa_poll_option_poll(tenant_id,poll_id,sort_no)` |
| `oa_poll_vote` | poll_id、option_id、user_id/name | `uk_oa_poll_vote_option(tenant_id,poll_id,user_id,option_id)`、`idx_oa_poll_vote_poll(tenant_id,poll_id,option_id)` |
| `oa_suggestion` | title、category(SUGGESTION/COMPLAINT/PRAISE/OTHER)、content、anonymous、submitter_id/name、dept_id、status(PENDING/PROCESSING/REPLIED/CLOSED)、handler_id/name、handled_time | `idx_oa_suggestion_status(tenant_id,status,create_time)`、`idx_oa_suggestion_submitter(tenant_id,submitter_id)` |
| `oa_suggestion_reply` | suggestion_id、replier_id/name、content | `idx_oa_suggestion_reply_suggestion(tenant_id,suggestion_id,create_time)` |

无审批种子（投票/建议/文化不走 Workflow）。

## 三、后端（`com.bluewhale.oa.culture`，四层）

沿用 document/collab 范式：`interfaces→application→domain→infrastructure`，领域层不依赖 Spring/MyBatis，应用层不依赖 infrastructure。

### 3.1 领域模型
- 枚举：`CultureEventType`、`CultureEventStatus`、`CultureWishType`、`PollStatus`、`SuggestionStatus`、`SuggestionCategory`（`of(String)` 非法值抛 `OaErrorCode.exception`）
- 模型：`CultureEvent`、`CultureWish`、`Poll`、`PollOption`、`PollVote`、`Suggestion`、`SuggestionReply`、`CultureQuery`（继承 `PageQuery`）、`BirthdayCelebration`（派生视图模型）
- 仓储端口 `CultureRepository`：事件/祝福/投票/选项/投票记录/建议/回复的 page/find/save/update/delete/统计（`countVotes(pollId, optionId)`、`countUserVotes(pollId,userId)`、`existsVote(pollId,userId,optionId)`、生日查询委托 `EmployeeProfileRepository` 或直接注入员工档案 Mapper？→ 采用领域端口 `CultureEmployeeGateway`，由 infrastructure 实现查询 `oa_employee_profile`）

### 3.2 应用服务
- `CultureEventAppService`：分页/详情/新增/编辑/删除/发布；发布后不可再编辑（仅 ARCHIVED 可归档）
- `CultureWishAppService`：祝福分页、当日生日/周年列表、送祝福（同一天同类型同对象同一人只允许一条）
- `PollAppService`：分页/详情（含选项票数与我已投选项）/新增/编辑/删除/发布/关闭/投票
  - 投票校验：已发布且未结束、选项属于该投票、单选仅 1 项且多选不超过 `max_choices`、同一用户同一投票只能投一次
- `SuggestionAppService`：分页（`mine=true` 只看自己）、详情（含回复，匿名隐藏提交人）、提交、回复（回复后状态置 REPLIED 并记录处理人）、关闭

### 3.3 控制器（路径与权限）
| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/oa/culture/events/page`、`/events/{id}` | `sys:oa:culture:list` |
| POST/PUT/DELETE | `/oa/culture/events`、`/events/{id}`、`PUT /events/{id}/publish` | `sys:oa:culture:add` |
| GET | `/oa/culture/birthdays`、`/oa/culture/wishes/page` | `sys:oa:culture:list` |
| POST | `/oa/culture/wishes` | `sys:oa:culture:list`（祝福为轻社交动作，不单设权限点） |
| GET | `/oa/poll/page`、`/oa/poll/{id}` | `sys:oa:poll:list` |
| POST/PUT/DELETE | `/oa/poll`、`/poll/{id}`、`/poll/{id}/publish`、`/poll/{id}/close` | `sys:oa:poll:add` |
| POST | `/oa/poll/{id}/vote` | `sys:oa:poll:vote` |
| GET | `/oa/suggestion/page`、`/suggestion/{id}` | `sys:oa:suggestion:list` |
| POST | `/oa/suggestion` | `sys:oa:suggestion:add` |
| POST/PUT | `/suggestion/{id}/reply`、`/suggestion/{id}/close` | `sys:oa:suggestion:reply` |

审计日志沿用 `@AuditLog`；当前用户取 `UserContext.requireUserId(request)`、姓名取 `WebHeaders.username(request)`；列表分页用 `PageQuery.toPage()`、`PageResult.of(...)`。

### 3.4 错误码（`OaErrorCode` 追加）
`CULTURE_INVALID=40024`、`CULTURE_EVENT_NOT_FOUND=40438`、`CULTURE_EVENT_STATUS_CONFLICT=40944`、`POLL_INVALID=40025`、`POLL_NOT_FOUND=40439`、`POLL_STATUS_CONFLICT=40945`、`POLL_ALREADY_VOTED=40946`、`POLL_OPTION_REQUIRED=40947`、`SUGGESTION_INVALID=40026`、`SUGGESTION_NOT_FOUND=40440`、`SUGGESTION_STATUS_CONFLICT=40948`，并补 `MESSAGES`。

## 四、权限与菜单（Auth 增量脚本）

新增 `backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/migration-5.82-oa-m5-culture.sql`（幂等，风格同 M4 脚本）：

- 权限 1780–1787：`sys:oa:culture:list`、`sys:oa:culture:add`、`sys:oa:poll:list`、`sys:oa:poll:add`、`sys:oa:poll:vote`、`sys:oa:suggestion:list`、`sys:oa:suggestion:add`、`sys:oa:suggestion:reply`
- 授权：`oa_admin`/`tenant_admin` 全量；`oa_manager` 1780/1781/1782/1783/1785/1786/1787；`oa_member` 1780/1782/1783/1784/1785
  （说明：`poll:add` 为管理类给 manager/admin；member 只读+投票+提建议；culture:add 为管理类）
- 菜单 395 企业文化 `/oa/culture`、396 投票问卷 `/oa/poll`、397 建议箱 `/oa/suggestion`

## 五、前端（`frontend/platform-apps/whale-oa`）

- 新增 `src/api/culture.ts`：类型与 7 组接口（文化事件/生日祝福/投票/建议）
- 新增页面：
  - `src/pages/culture/index.tsx`：文化动态列表 + 发布弹窗（`sys:oa:culture:add`）+ 今日生日/周年卡片 + 送祝福
  - `src/pages/poll/index.tsx`：投票列表 + 新建/发布/关闭（`poll:add`）+ 投票弹窗（单选/多选、已投状态）
  - `src/pages/suggestion/index.tsx`：建议列表（我的/全部）+ 提交 + 详情回复（`suggestion:reply`）+ 匿名标识
- `src/App.tsx`：`MENU_ITEMS` 增加 `/culture`、`/poll`、`/suggestion` 与对应 `Routes`
- 交互约定：`http`、`hasPermission`、`App.useApp().message`，时间展示 `T→空格` 截断；不新增上传组件（文化动态封面暂只存 `coverFileId`，前端本批不做上传）。

## 六、测试

- `OaFlywayMigrationScriptTest` 增加 `v13ShouldCreateCultureTables`：表集合 7 张 + 关键唯一索引 `uk_oa_poll_vote_option` + 关键列。
- 新增 `CultureAppServiceImplTest`（Mockito）：事件发布/状态冲突、投票重复拒绝、单选超限拒绝、建议匿名视图隐藏提交人、回复状态流转。
- 后端：`mvn -o -pl whale-apps/whale-oa test "-Dcheckstyle.skip=true"`（cwd `backend`）
- 前端：`pnpm --filter whale-oa lint`、`pnpm --filter whale-oa build`（cwd `frontend`）
- 文档一致性：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-doc-consistency.ps1`

## 七、文档回填

- `docs/plans/2026-11-04-wave5.82-oa-panorama.md`：M5 状态与进度、版本 v0.8
- `docs/plans/2026-11-06-five-services-backlog-stepwise.md`：7d-1 完成
- `docs/service-catalog.md`：表数、端点、权限菜单、前端页
- `docs/devlog/2026-11-07.md`：新增 Step 7d-1 小节

## 八、提交边界（红线）

只暂存本批新增/修改的业务文件；**绝不提交** `.trae/documents/**`、`docs/reviews/2026-11-07-workspace-scan.md`、`backend/whale-apps/whale-mall/**`、`frontend/platform-apps/whale-mall/**` 及 whale-pay/5.89 邮箱/5.91 地图未提交文件。提交信息：`feat(oa): M5 文化/投票/建议（V13 7 表 / 权限 1780-1787 / 菜单 395-397 / 前端 3 页）`。
