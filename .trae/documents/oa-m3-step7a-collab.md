# OA M3 P1 协同底座（Step 7a）实施计划

## Context（背景与目标）

Step 1–6 已完成并推送（最近提交 `50dac457` Step 6b）。当前进入 Step 7 第一可落地批次：**OA M3 P1 协同底座**，只做会议 / 公告 / 任务三域，日程、网盘、通讯录、工作台留 7b，CRM/ERP 被决策阻塞部分不动。

目标：在现有 `whale-oa`（9004、`/oa/**`、`bluewhale_oa`）内补齐协同办公最小闭环：

- 会议：会议室主数据、预约时段冲突校验、参会人快照、会议纪要、行动项自动生成个人任务。
- 公告：全员/部门/用户范围、接收人快照、发布/撤回、已读统计。
- 任务：个人待办、状态机、优先级、截止日期、来源（个人/会议）、评论。

约束：复用既有四层结构（interfaces → application → domain → infrastructure）、`OaErrorCode`、`SequenceAppService`、MyBatis-Plus、`BaseEntity`、`PageQuery`、`@RequirePermission`、`@AuditLog`；不新增服务/库/前端应用；OA M3 权限 1730–1739、菜单 383–385；Flyway 新增 `V9__collab.sql`，只追加。

## 一、数据表（`V9__collab.sql`）

对齐 V8 风格：`CREATE TABLE IF NOT EXISTS`、逻辑外键、`tenant_id`、`deleted`、utf8mb4、无物理外键、无 DROP。

1. `oa_meeting_room`：`name`(UK tenant,name)、`location`、`capacity`、`status`(ENABLED/DISABLED)、`remark`。
2. `oa_meeting`：`code`(UK)、`title`、`room_id`、`organizer/organizer_id/dept_id`、`start_time/end_time`、`agenda`、`status`(SCHEDULED/FINISHED/CANCELLED)、`remark`；索引 `(tenant_id, room_id, start_time, end_time)`、`(tenant_id, status, start_time)`。
3. `oa_meeting_participant`：`meeting_id`、`user_id`、`user_name`、`required`(0/1)、`status`(INVITED/ATTENDED/ABSENT)、`remark`；UK(tenant, meeting_id, user_id)。
4. `oa_meeting_minute`：`meeting_id`(UK tenant,meeting_id)、`content`、`author_id`、`author`。
5. `oa_meeting_action`：`meeting_id`、`content`、`owner_id`、`owner_name`、`due_date`、`status`(OPEN/DONE)、`task_id`（逻辑关联 `oa_task.id`）。
6. `oa_notice`：`title`、`content`、`scope_type`(ALL/DEPT/USER)、`scope_value`、`status`(DRAFT/PUBLISHED/WITHDRAWN)、`publisher/publisher_id`、`publish_time`、`remark`；索引 `(tenant_id, status, publish_time)`。
7. `oa_notice_recipient`：`notice_id`、`user_id`、`user_name`、`read_flag`、`read_time`；UK(tenant, notice_id, user_id)。
8. `oa_task`：`title`、`description`、`owner_id/owner_name`、`creator_id/creator_name`、`priority`(HIGH/NORMAL/LOW)、`status`(TODO/DOING/DONE/CANCELLED)、`due_date`、`source`(PERSONAL/MEETING/NOTICE)、`source_id`、`finish_time`；索引 `(tenant_id, owner_id, status, due_date)`、`(tenant_id, source, source_id)`。
9. `oa_task_comment`：`task_id`、`user_id`、`user_name`、`content`。

## 二、后端（`com.bluewhale.oa.collab`）

按 administrative 模块的分层与命名逐层落地：

### domain
- `model/MeetingRoom.java`、`Meeting.java`、`MeetingParticipant.java`、`MeetingMinute.java`、`MeetingAction.java`、`Notice.java`、`NoticeRecipient.java`、`OaTask.java`、`TaskComment.java`、`CollabQuery.java`
- 状态枚举：`MeetingStatus`、`ParticipantStatus`、`NoticeScopeType`、`NoticeStatus`、`TaskStatus`、`TaskPriority`、`TaskSource`
- 聚合内校验：会议时间区间、仅 `SCHEDULED` 可编辑/取消/纪要、任务状态机（TODO↔DOING，TODO/DOING→DONE/CANCELLED，终态不可回流，DONE 写 `finishTime`）
- `repository/CollabRepository.java`（统一仓储，方法对齐 `AdministrativeRepository` 粒度）
- `gateway/CollabUserDirectoryGateway.java`（公告接收人候选：复用 Auth `GET /user/internal/directory`，参考 `approval/infrastructure/client/UserDirectoryGatewayImpl` 的 `DiscoveryClient` + `InternalTokenSupport` 写法）

### application
- `command/CollabCommands.java`：会议室保存、会议保存（含参会人）、会议状态、纪要保存（含行动项 `createTask`）、公告保存/发布/撤回、任务保存/状态/评论
- `view/CollabViews.java`：各视图 + 会议详情（参会人/纪要/行动项）、公告详情（接收/已读统计）
- `service/MeetingAppService`、`NoticeAppService`、`TaskAppService` 及 `impl`
- 会议创建：校验会议室启用、`start<end`、同会议室时段重叠（排除 `CANCELLED` 与自身）；单号 `MEETING`/`MTG` 走 `SequenceAppService.nextCode`
- 纪要保存：upsert 纪要、替换行动项；`createTask=true` 时同事务创建 `oa_task`（`source=MEETING`、`sourceId=meetingId`）并回写 `taskId`
- 公告发布：接收人非空校验 → 写快照 → `PUBLISHED` + `publishTime`；撤回仅 `PUBLISHED→WITHDRAWN`；已读接口幂等
- 任务：按 `ownerId` 过滤为主；状态变更走聚合；评论仅任务存在时允许

### infrastructure
- 每表一个 `Entity`（继承 `BaseEntity`）+ `Mapper` + `CollabRepositoryImpl`
- 查询条件支持 `keyword/status/priority/ownerId/roomId/scopeType/source/dateFrom/dateTo`，分页上限 200，`orderByDesc(id)`

### interfaces
- `MeetingController` `/oa/meeting`：`/rooms/page`、`/rooms`、`/rooms/{id}`、`/page`、`/{id}`、`/{id}/status`、`/{id}/minute`、参会人状态
- `NoticeController` `/oa/notice`：`/page`、`/my/page`、`/{id}`、`/`(POST)、`/{id}`(PUT)、`/{id}/publish`、`/{id}/withdraw`、`/{id}/read`、`/{id}/stats`、`/recipients`（候选代理）
- `TaskController` `/oa/task`：`/page`、`/`(POST)、`/{id}`(PUT/DELETE)、`/{id}/status`、`/{id}/comments`、`/comments/{id}`(DELETE)
- 审计 `@AuditLog` 与现有行政模块一致

### 错误码
`OaErrorCode` 追加协同段：`COLLAB_INVALID=40018`、会议室 40427/40930、会议 40428/40931/40932/40933、公告 40019/40429/40934/40935/40430、任务 40020/40431/40936，并补默认文案。

## 三、权限与菜单（`migration-5.82-oa-m3-collab.sql`，auth 库）

格式对齐 `migration-5.82-oa-m2-attendance.sql`：`NOT EXISTS` 幂等、`id=0` 顶层、`sys_role_permission` MD5 生成 ID。

| 权限 | 码 | 覆盖端点 |
|---|---|---|
| 1730 | `sys:oa:meeting:room:list` | 会议室查询 |
| 1731 | `sys:oa:meeting:room:manage` | 会议室增改删 |
| 1732 | `sys:oa:meeting:list` | 会议查询/详情/纪要查看 |
| 1733 | `sys:oa:meeting:manage` | 会议增改/状态/纪要/行动项 |
| 1734 | `sys:oa:notice:list` | 公告查询/我的/详情/已读 |
| 1735 | `sys:oa:notice:manage` | 公告创建/发布/撤回/候选接收人 |
| 1736 | `sys:oa:notice:stats` | 已读统计 |
| 1737 | `sys:oa:task:list` | 任务分页/评论查询 |
| 1738 | `sys:oa:task:manage` | 任务增改删/状态 |
| 1739 | `sys:oa:task:comment` | 评论新增/删除 |

菜单 383「会议」`/oa/collab/meeting`、384「公告」`/oa/collab/notice`、385「任务」`/oa/collab/task`。

授权：`oa_admin/oa_manager/tenant_admin` 1730–1739；`oa_member` 1730/1732/1734/1737/1739。

## 四、前端（`frontend/platform-apps/whale-oa`）

1. `src/api/collab.ts`：类型 + `buildQuery` + 会议室/会议/纪要/公告/任务/评论函数。
2. `src/pages/collab/meeting.tsx`：Tab「会议室 / 会议 / 纪要」；会议室 CRUD、会议创建（会议室/时段/参会人/议程）、详情抽屉（参会人、纪要编辑、行动项→生成任务）。
3. `src/pages/collab/notice.tsx`：公告列表 + 新建/编辑（范围 ALL/DEPT/USER + 接收人选择器 `GET /oa/notice/recipients`）、发布/撤回、已读统计抽屉、「我的公告」Tab 与标记已读。
4. `src/pages/collab/task.tsx`：任务列表/看板式状态筛选、新建/编辑、状态流转、评论抽屉。
5. `src/App.tsx`：注册 3 个菜单项与路由，权限码按上表。

## 五、测试与验收

- 单测（Mockito，对齐现有 `*AppServiceImplTest`）：
  - `MeetingAppServiceImplTest`：时段非法、会议室停用、同室冲突、取消终态、纪要+行动项生成任务。
  - `NoticeAppServiceImplTest`：发布空接收人拒绝、快照落库、撤回状态机、已读幂等、统计口径。
  - `TaskAppServiceImplTest`：状态机、完成时间、来源校验、评论任务存在性。
- `OaFlywayMigrationScriptTest`：按现有断言方式补充 V9（实施时先读取该测试的实际断言结构再改）。
- 后端：`backend` 下 `mvn -o -pl whale-apps/whale-oa test "-Dcheckstyle.skip=true"`。
- 前端：`frontend` 下 `pnpm --filter whale-oa lint` / `build`。
- 文档一致性：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-doc-consistency.ps1` → PASS。
- 文档回填：`docs/service-catalog.md`（V9/协同能力）、`docs/plans/2026-11-06-five-services-backlog-stepwise.md`（Step 7a 进度）、`docs/plans/2026-11-04-wave5.82-oa-panorama.md`（M3 P1 状态）、`docs/devlog/2026-11-07*.md`。
- 提交：只暂存本批文件（V9、collab 包、权限脚本、collab.ts、3 页面、App.tsx、文档），推送 `origin/main`，输出四段式报告（需求项/完成项/待做项/已修正问题项）。

## 六、待实施时确认的少量细节（不影响总体方案）

- Auth `GET /user/internal/directory` 响应字段是否含 `deptId`：若含，按部门在 OA 侧过滤；若不含，部门公告由发布人在候选列表中显式勾选接收人（`scopeType=DEPT` 仅作标记），全员公告由网关分页拉全量候选。
- `OaFlywayMigrationScriptTest` 的实际断言结构（表名枚举或文件清单），按其结构补 V9。
- 现有考勤控制器获取当前用户 ID 的方式（`LoginUser` 工具），任务/评论/已读操作沿用同一方式；请求体仍保留 `userName` 供展示。
