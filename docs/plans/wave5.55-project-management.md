# Wave 5.55 · 项目管理服务（whale-pm）规划

> 目标：新建一个对标 Jira / 禅道的项目管理服务，覆盖「项目空间 → 工作项 → 迭代 → 看板 → 报表」主线，并与平台既有的认证、权限、租户、文件、工作流、IM、审计能力打通。
> 制定日期：2026-10-25 ｜ 状态：**M5 已实施 + 本机全栈部署验证完成**（项目域 + 工作项域 + 迭代看板 + 报表与前端 + 集成收尾，Wave 5.55 全部里程碑完成；部署侧 5 个冒烟脚本全通过，见 [部署验证报告](../reviews/2026-10-25-wave5.55-deployment-verification.md)） ｜ 关联：REQ-FUNC-018 / REQ-FUNC-019 / REQ-ARCH-008 / REQ-SEC-009
> 上游依据：[roadmap.md](roadmap.md)、[service-catalog.md](../service-catalog.md)、[naming-conventions.md](../naming-conventions.md)、[wave5.46 业务应用规划](wave5.46-phase4-business-apps.md)、[wave5.54 多租户评估](wave5.54-multi-tenant/assessment.md)

---

## 〇、结论摘要

| 项 | 结论 |
|---|---|
| 服务归属 | **新建 `backend/whale-apps/whale-pm`**（业务应用，与 erp/mes/pdm/oa 同级），不塞进 OA |
| 端口 / 前缀 / 库 | **9005** / **`/pm/**`** / **`bluewhale_pm`** |
| 包名 / 配置前缀 | `com.bluewhale.pm` / `whale.pm.*`；Redis key `whale:pm:*` |
| 前端 | 新建 `frontend/platform-apps/whale-pm`，**qiankun 子应用**，base `/pm`，dev 端口 **8108** |
| 分层 | DDD-lite：`interfaces / application / domain / infrastructure`（与 [ddd-whale-auth.md](../design/ddd-whale-auth.md) 同口径，不推倒现有三层规范） |
| 复用的平台能力 | auth（RBAC + 租户）、gateway（JWT/路由/限流）、file（附件）、workflow（评审/变更审批，可选）、im（通知，可选）、audit（审计）、job（定时报表，可选） |
| 明确不做 | 插件市场、自动化规则引擎、外部 Jira 导入导出、甘特图/资源日历、移动端原生 |
| 交付节奏 | M0 设计定稿 → M1 项目域 → M2 工作项域 → M3 迭代看板 → M4 报表与前端 → M5 集成收尾 |

---

## 一、现状与差距

| 维度 | 现状 | 对本服务的影响 |
|---|---|---|
| 项目管理能力 | 全仓 **无** PM / 任务 / 缺陷 / 迭代相关服务、表、权限点 | 全新域，无历史包袱，可按目标模型一次设计到位 |
| 业务应用模板 | `whale-apps/whale-oa` 已完成「模块骨架 + 库 + 6 端点 + 权限点 + 审计 + 前端子应用 + 网关路由」闭环 | **可直接复制为 M1 骨架模板**，降低搭建成本 |
| 权限点 ID | 已用至 **171**（OA 请假 168–171） | PM 权限点从 **172** 起编，预留 172–199 |
| 菜单 ID | 已用至 **17**（租户成员） | PM 菜单从 **18** 起编，预留 18–29 |
| 端口 | 9001–9004 已占；前端 8101–8107 已占 | 后端取 **9005**，前端取 **8108** |
| 多租户 | Wave 5.54 已落地 `tenant_id` 行级隔离 + MyBatis-Plus 租户插件 + 网关可信注入 | PM 所有业务表**必须带 `tenant_id`**，并复用插件，不另造隔离机制 |
| 状态机 | `whale-workflow` 已有流程引擎（审批流） | PM 内部「工作项状态流转」是**执行状态机**，不复用审批引擎；仅「需求评审/变更审批」按需调用 workflow |
| 文件/通知/审计 | `whale-file`、`whale-im`、`whale-audit` 均已可用 | 附件只存 `file_id` 引用；站内通知异步走 IM；写操作统一 `@AuditLog` |
| 质量门禁 | Checkstyle `severity=error`、JaCoCo `coverage-gate`、CI 镜像矩阵 18 个服务 | 新服务需同步纳入构建、检查清单、镜像矩阵与覆盖率门禁 |

---

## 二、范围与边界

### 2.1 能力对标

| 能力 | Jira / 禅道 | whale-pm 方案 | 波次 |
|---|---|---|---|
| 项目空间 | Project / 产品 | `pm_project`（key、名称、类型、可见性、负责人、状态） | M1 |
| 成员与角色 | Project Role | `pm_project_member`（ADMIN / MEMBER / VIEWER）+ 平台权限码双层校验 | M1 |
| 工作项 | Epic/Story/Task/Bug/Subtask | **单表统一模型** `pm_work_item` + `type` 判别（避免 5 张同构表） | M2 |
| 状态与流转 | Workflow Scheme | `pm_status`（TODO/IN_PROGRESS/DONE 三分类）+ `pm_transition`（允许边） | M1 配置 / M2 使用 |
| 看板 | Board + Column + WIP | `pm_board` / `pm_board_column`，列映射状态，支持 WIP 上限 | M3 |
| Backlog 排序 | Rank | 工作项 `rank` 字段（LexoRank 风格字符串），拖拽只改一条 | M3 |
| 迭代 / 版本 | Sprint / Version | `pm_sprint`（FUTURE/ACTIVE/CLOSED）+ 可选 `pm_version` | M3 |
| 评论 / 附件 / 动态 | Comment / Attachment / Activity | `pm_comment`、`pm_attachment`、`pm_activity`（append-only） | M2 |
| 工作项关联 | Link（blocks/relates/duplicates） | `pm_work_item_link` | M2 |
| 工时 | Worklog | `pm_worklog`（首版可选，默认关闭入口） | M4 可选 |
| 报表 | Burndown / Velocity / CFD / Workload | 4 个只读报表端点，MySQL 聚合 | M4 |
| 自定义字段 | Custom Field | 首版用 `extension_json` 预留，**不做字段设计器** | 后续 |
| 权限 | Global + Project Role | 平台权限码（功能）+ 项目角色（数据范围） | M1 |
| 通知 | 邮件 / 站内 | 复用 `whale-im` 机器人/系统消息，异步失败不影响主事务 | M5 可选 |
| 审批 | 需求评审 / 变更审批 | 按需调用 `whale-workflow`，PM 不内置审批引擎 | M5 可选 |
| 自动化 / 插件 | Automation / Marketplace | **不做** | — |

### 2.2 In Scope

1. 项目空间、成员与项目角色、状态与流转配置。
2. 统一工作项模型（Epic / Story / Task / Bug / Subtask）、父子层级、指派、优先级、截止日期、故事点、标签。
3. 工作项状态流转、评论、附件、动态时间线、工作项关联。
4. 迭代（Sprint）生命周期、Backlog 排序、看板列与拖拽流转、WIP 限制。
5. 4 类基础报表（燃尽、速率、累积流、成员负载）。
6. 前端子应用 8 个页面与平台基座挂载、菜单、路由、权限按钮。
7. 网关路由、权限点/菜单种子、租户隔离、审计、构建与镜像矩阵。

### 2.3 Out of Scope（明确不做）

- 插件市场、脚本/自动化规则引擎、Webhook 编排。
- 甘特图、资源日历、跨项目依赖图、基线管理。
- 外部 Jira / 禅道数据迁移工具与双向同步。
- 自定义字段设计器（首版仅预留 `extension_json`）。
- 移动端原生 App（沿用响应式 Web）。
- 富文本协同编辑（首版为 Markdown / 纯文本 + 附件）。

---

## 三、总体设计

### 3.1 服务落位与命名（REQ-ARCH-004 / REQ-ARCH-005）

| 项 | 值 | 落点 |
|---|---|---|
| Maven 模块 | `backend/whale-apps/whale-pm` | `whale-apps/pom.xml` 增加 `<module>whale-pm</module>` |
| artifactId / 服务名 | `whale-pm` / `whale-pm` | `pom.xml`、`application.yml`、Nacos 服务名 |
| 端口 | `${SERVER_PORT:9005}` | `application.yml` |
| 对外前缀 | `/pm/**`（服务自持，网关**不剥离**） | Controller `@RequestMapping("/pm/...")` |
| 数据库 | `bluewhale_pm` | `db/init.sql` + `db/migration-5.55-*.sql` |
| Java 包 | `com.bluewhale.pm` | `controller/service/domain/infrastructure` |
| 配置前缀 | `whale.pm.*` | 如 `whale.pm.rank.step`、`whale.pm.notify.enabled` |
| Redis key | `whale:pm:<用途>:<标识>` | 如 `whale:pm:project:lock:<id>` |
| 前端 | `frontend/platform-apps/whale-pm`，base `/pm`，端口 8108 | `vite.config.ts`、`qiankun/apps.ts` |

### 3.2 架构与依赖关系

```
浏览器（qiankun 基座 whale-platform，8000）
   │  /pm 子应用（8108）
   ▼
whale-gateway（8080）── JWT 鉴权 / 租户注入 / 限流 / 熔断
   │  lb://whale-pm（Path=/pm/**）
   ▼
┌──────────────────────── whale-pm（9005） ────────────────────────┐
│ interfaces   Controller / DTO / VO                               │
│ application  应用服务（用例编排、事务边界、权限与租户校验）        │
│ domain       Project / WorkItem / Sprint / Board 聚合 + 状态机    │
│ infrastructure MyBatis-Plus Mapper / Repository / 外部服务客户端   │
└──────┬───────────────┬───────────────┬───────────────┬───────────┘
       │               │               │               │
   whale-auth      whale-file      whale-workflow   whale-im / whale-audit
   （权限/租户）    （附件）        （可选审批）     （通知 / 审计）
       │
   MySQL bluewhale_pm
```

集成原则：

- **只经网关对外**，服务间调用走 `lb://whale-*`（Nacos 直连，不经网关）。
- PM 不保存用户、部门、角色主数据，只保存 `user_id` 引用，展示名由 auth 侧解析或前端缓存。
- PM 不保存文件二进制，只存 `file_id`；删除附件只解除引用，不删 `whale-file` 记录。
- PM 不内置审批引擎；需要审批时以 `businessKey = work_item_id` 发起 workflow 实例。

### 3.3 分层与包结构

```
com.bluewhale.pm
├── interfaces/controller        # /pm/** REST 端点
├── interfaces/dto               # 请求对象（含校验注解）
├── interfaces/vo                # 响应对象（id 一律 string）
├── application/service          # ProjectService / WorkItemService / SprintService / BoardService / ReportService
├── application/assembler        # Entity <-> VO 转换
├── domain/model                 # Project / WorkItem / Sprint / Board 聚合根与值对象
├── domain/service               # 状态机、Rank 计算、项目角色校验
├── domain/repository            # 仓储接口（由 infrastructure 实现）
└── infrastructure
    ├── mapper / repository      # MyBatis-Plus
    ├── client                   # auth / file / workflow / im 内网客户端
    └── config                   # Redis、线程池、属性
```

约束：`interfaces` 不得直接依赖 `infrastructure`；跨聚合只经 `application` 编排；`domain` 不依赖 Spring Web / MyBatis。

### 3.4 领域模型

| 聚合根 | 关键属性 | 不变量 |
|---|---|---|
| **Project** | id、tenantId、key（如 `BW`）、name、type（SCRUM/KANBAN）、visibility、leadUserId、status、statusSetId | `key` 租户内唯一；归档项目不可新增工作项 |
| **WorkItem** | id、projectId、key（如 `BW-102`）、type、title、description、statusId、priority、assigneeId、reporterId、parentId、sprintId、storyPoints、dueDate、rank、extensionJson、version | 子任务必须有父项且父子同项目；`DONE` 分类状态不可再流转到 `TODO`（除非配置允许）；`rank` 项目内唯一 |
| **Sprint** | id、projectId、name、goal、startDate、endDate、status | 同一项目同时最多 1 个 `ACTIVE`；关闭后不可再加入工作项 |
| **Board** | id、projectId、name、columns[] | 列必须映射同项目状态；WIP 超限拒绝拖入 |
| **Status / Transition** | statusId、name、category、sort；from → to、name | 流转必须命中配置边；删除状态前须迁移工作项 |

工作项状态机（默认模板）：

```
TODO ──开始──► IN_PROGRESS ──完成──► DONE
  ▲                 │                  │
  └─────重新打开─────┴──────────────────┘
        （可配置：是否允许回退、是否要求评论/工时）
```

工作项 key 生成：项目 key + 项目内自增序列，使用 Redis `INCR whale:pm:itemseq:<projectId>` 并在库内以 `(tenant_id, project_id, item_no)` 唯一键兜底；`item_no` 与 `key` 同时落库，避免并发重号。

### 3.5 数据库设计（`bluewhale_pm`）

统一约定：`id BIGINT`（雪花）、`tenant_id BIGINT NOT NULL DEFAULT 1`、`create_time`、`update_time`、`deleted`（逻辑删除）；表间不建物理外键；状态/枚举用 `VARCHAR`，注释列全量枚举值。

| 表 | 波次 | 关键字段 | 关键索引 / 约束 |
|---|---|---|---|
| `pm_project` | M1 | key、name、type、visibility、lead_user_id、status、status_set_code | `uk_tenant_key(tenant_id, key)` |
| `pm_project_member` | M1 | project_id、user_id、project_role、joined_time | `uk_project_user(tenant_id, project_id, user_id)` |
| `pm_status` | M1 | project_id（0=模板）、code、name、category、sort_no、is_default | `uk_project_code(tenant_id, project_id, code)` |
| `pm_transition` | M1 | project_id、from_status_id、to_status_id、name、require_comment、require_permission | `uk_edge(tenant_id, project_id, from_status_id, to_status_id)` |
| `pm_work_item` | M2 | project_id、item_no、item_key、type、title、description、status_id、priority、assignee_id、reporter_id、parent_id、sprint_id、story_points、due_date、rank_value、extension_json、version | `uk_tenant_project_item_no`、`uk_tenant_project_rank`、`idx_project_status`、`idx_project_assignee`、`idx_parent` |
| `pm_work_item_link` | M2 | project_id、source_id、target_id、link_type | `uk_link(tenant_id, source_id, target_id, link_type)` |
| `pm_comment` | M2 | work_item_id、author_id、content、parent_id | `idx_item_time` |
| `pm_attachment` | M2 | work_item_id、file_id、file_name、uploader_id | `uk_item_file(tenant_id, work_item_id, file_id)` |
| `pm_activity` | M2 | project_id、work_item_id、actor_id、action、field、old_value、new_value、trace_id | append-only，`idx_item_time` |
| `pm_sprint` | M3 | project_id、name、goal、start_date、end_date、status | `uk_tenant_project_name`、`idx_project_status` |
| `pm_board` | M3 | project_id、name、type | `uk_tenant_project_name` |
| `pm_board_column` | M3 | board_id、status_id、name、sort_no、wip_limit | `uk_board_status` |
| `pm_version`（可选） | M4 | project_id、name、release_date、status | `uk_tenant_project_name` |
| `pm_worklog`（可选） | M4 | work_item_id、user_id、work_date、hours、remark | `idx_item_user_date` |

DDL 落地方式：`db/init.sql`（全量、可重建）+ `db/migration-5.55-*.sql`（增量、幂等）；权限与菜单种子放 `whale-auth/src/main/resources/db/migration-5.55-pm.sql`，并同步回 `init.sql` 保持同构。

工作项创建幂等：请求体带 `clientRequestId`，唯一键 `(tenant_id, project_id, client_request_id)`；重复提交返回首次结果，不重复落库。

### 3.6 关键机制

| 机制 | 方案 |
|---|---|
| 租户隔离 | 全表 `tenant_id` + `whale-spring-boot-starter` 租户行插件；跨租户查询/详情/修改一律按「不存在」404；写操作强制带租户条件 |
| 功能权限 | `@RequirePermission("sys:pm:*")`，网关注入 `X-Permission-Codes`，starter 拦截器校验 |
| 数据范围 | 项目角色 `ADMIN / MEMBER / VIEWER`：VIEWER 只读、MEMBER 可建改工作项、ADMIN 可改项目配置与成员；**功能权限与项目角色同时满足**才放行 |
| 审计 | 写操作（项目/工作项/迭代/看板/成员）统一 `@AuditLog`，`targetType` 用表名，`targetId` 传主键；只读与报表不审计 |
| 分布式 ID | 复用平台雪花 ID；接口出入参 Long → String，前端全链路 `id: string` |
| 乐观锁 | `pm_work_item.version` + MyBatis-Plus `@Version`；同一条工作项并发编辑返回冲突 409 语义 |
| 排序 | `rank_value VARCHAR(64)`，LexoRank 风格（相邻项之间取中间串，空间不足时局部重排）；唯一键兜底，冲突重试最多 3 次 |
| 状态流转 | 领域服务校验「边存在 + 项目角色 + WIP 上限 + 必填评论」；流转与动态、审计同事务；通知在 `afterCommit` 异步 |
| 附件 | 前端先调 `/file/upload` 拿 `file_id`，再调 PM 绑定；下载走 `/file/{id}/download`；PM 只校验同租户与项目可见性 |
| 通知 | 被指派、被评论、状态变更生成站内通知；`whale.pm.notify.enabled=false` 时仅落动态不推送；失败只 warn |
| 审批（可选） | 需求评审/变更审批以 `businessKey=workItemId` 调 `/workflow/instance`，回调更新工作项标记；默认关闭 |
| 搜索 | v1 用 `title` 前缀/`LIKE` + 组合索引，单项目分页；v2 再考虑接 `whale-agent` 知识检索 |
| 报表 | MySQL 聚合（按天快照/事件推导）；单项目查询上限 1 年，结果缓存 Redis 5 分钟 |

### 3.7 接口契约（`/pm/**`）

统一返回 `Result<T>` / `PageResult<T>`，分页参数 `pageNum/pageSize`，`id` 全部 string。

| 分组 | 端点 | 权限码 |
|---|---|---|
| 项目 | `GET /pm/project/page`、`GET /pm/project/{id}` | `sys:pm:project:list` |
| 项目 | `POST /pm/project`、`PUT /pm/project/{id}` | `sys:pm:project:add` / `sys:pm:project:edit` |
| 项目 | `DELETE /pm/project/{id}` | `sys:pm:project:delete` |
| 成员 | `GET /pm/project/{id}/member/page`、`POST/PUT/DELETE /pm/project/{id}/member` | `sys:pm:project:member:list` / `sys:pm:project:member:manage` |
| 状态配置 | `GET /pm/project/{id}/status`、`PUT /pm/project/{id}/status`、`GET/PUT /pm/project/{id}/transition` | `sys:pm:project:config` |
| 工作项 | `GET /pm/work-item/page`、`GET /pm/work-item/{id}` | `sys:pm:workitem:list` |
| 工作项 | `POST /pm/work-item`、`PUT /pm/work-item/{id}`、`DELETE /pm/work-item/{id}` | `sys:pm:workitem:add/edit/delete` |
| 工作项 | `POST /pm/work-item/{id}/transition`、`POST /pm/work-item/{id}/rank` | `sys:pm:workitem:transition` |
| 评论 | `GET/POST /pm/work-item/{id}/comment`、`DELETE /pm/comment/{id}` | `sys:pm:comment:list/add/delete` |
| 附件 | `GET/POST /pm/work-item/{id}/attachment`、`DELETE /pm/attachment/{id}` | `sys:pm:attachment:add/delete` |
| 动态 | `GET /pm/work-item/{id}/activity`、`GET /pm/project/{id}/activity` | 复用 `sys:pm:workitem:list` |
| 关联 | `POST /pm/work-item/{id}/link`、`DELETE /pm/work-item/link/{id}` | `sys:pm:workitem:edit` |
| 迭代 | `GET/POST /pm/sprint`、`PUT /pm/sprint/{id}`、`POST /pm/sprint/{id}/start`、`POST /pm/sprint/{id}/close` | `sys:pm:sprint:list/manage` |
| 看板 | `GET /pm/board/{projectId}`、`PUT /pm/board/{projectId}/column` | `sys:pm:board:view/manage` |
| 报表 | `GET /pm/report/burndown`、`/velocity`、`/cumulative-flow`、`/workload` | `sys:pm:report:view` |

### 3.8 权限点与菜单

- **权限点 172–199**（预留 28 个，首版使用 172–190）：`sys:pm:project:{list,add,edit,delete,config}`、`sys:pm:project:member:{list,manage}`、`sys:pm:workitem:{list,add,edit,delete,transition}`、`sys:pm:comment:{list,add,delete}`、`sys:pm:attachment:{add,delete}`、`sys:pm:sprint:{list,manage}`、`sys:pm:board:{view,manage}`、`sys:pm:report:view`。
- **菜单 18–29**（预留 12 个，首版使用 18–23）：18「项目管理」目录；19 项目列表 `/pm/project`；20 看板 `/pm/board`；21 迭代 `/pm/sprint`；22 报表 `/pm/report`；23 项目设置 `/pm/setting`。
- **实际落地（M1 + M2 + M3 + M4 + M5）**：权限点 172–186 项目域（`sys:pm:project|member|status|transition:*`）、187–196 工作项域（`sys:pm:item|comment|attachment|link:*`）、197–200 迭代与看板、201 报表（`sys:pm:report:view`）、202 审批（`sys:pm:item:approval`）；菜单 18–24（项目管理 / 项目列表 / 状态配置 / 流转配置 / 迭代管理 / 看板 / 项目报表），M6+ 继续 25–29。
- 菜单随 `business` 租户开通模板复制（沿用 Wave 5.54 Phase 5.3 机制）。
- 网关新增 `/pm/internal/**` 内网屏蔽，外部访问 403；M5 同步为 whale-im 增加 `/im/internal/**` 内网屏蔽（站内通知通道）。

### 3.9 前端设计（`whale-pm`）

| 页面 | 路由（base `/pm`） | 关键能力 |
|---|---|---|
| 项目列表 | `/project` | 卡片/表格切换、关键字与状态筛选、新建/归档、进入项目 |
| 项目概览 | `/project/:id/overview` | 工作项统计、迭代进度、成员负载摘要 |
| Backlog | `/project/:id/backlog` | 迭代分组、拖拽排序、批量指派/移动迭代、快速新建 |
| 看板 | `/project/:id/board` | 列按状态、拖拽流转、WIP 上限提示、按负责人/类型筛选 |
| 工作项详情 | `/project/:id/item/:itemId` | 属性编辑、状态流转、评论、附件、动态、关联、子任务 |
| 迭代管理 | `/project/:id/sprint` | 创建/编辑/启动/关闭、容量与故事点汇总 |
| 报表 | `/project/:id/report` | 燃尽、速率、累积流、负载四图 |
| 项目设置 | `/project/:id/setting` | 成员与角色、状态与流转、看板列、归档 |

实现方式：

- 复用 `whale-oa` 的 `api/client.ts` + 真实登录 + 网关直连模式，不引入新的请求库。
- 拖拽复用 `@dnd-kit`（表单/工作流设计器已在用）；排序只提交 `rank`，不做全量重排。
- 组件：`WorkItemCard`、`StatusTag`、`PriorityTag`、`UserPicker`、`AttachmentList`、`ActivityTimeline`、`RankableList`、`WipBadge`。
- 权限按钮用 `@bluewhale/whale-ui` 的 `AuthButton`；`id` 全链路 string。
- 基座接入：`qiankun/apps.ts` 增加 `whale-pm`（activeRule `/pm`）、`router/menu.ts` 增加菜单、`MainLayout` 增加图标、`SubAppContainer` 路由段。

---

## 四、里程碑与任务拆分

### M0 设计定稿与登记（本轮）

| 任务 | 交付物 | 验收 |
|---|---|---|
| M0-T1 需求登记 | [register.md](../requirements/register.md) 新增 REQ-FUNC-018/019、REQ-ARCH-008、REQ-SEC-009 | 编号、优先级、验收标准齐全，门禁可追溯 |
| M0-T2 规划评审 | 本文档定稿 | 范围、非目标、端口/库/权限区间确认 |
| M0-T3 服务清单登记 | [service-catalog.md](../service-catalog.md) 增 `whale-pm` 9005 / `/pm/**` / `bluewhale_pm` / 前端 8108 | 端口分区、路由、库、职责齐全 |
| M0-T4 命名登记 | [naming-conventions.md](../naming-conventions.md) §八 增 `pm` 域词 | E15 命名符合性可核对 |
| M0-T5 路线图登记 | roadmap / task-breakdown / architecture 模块表 | 波次与依赖可追溯 |

### M1 服务骨架与项目域

| 任务 | 交付物 | 验收 |
|---|---|---|
| M1-T1 模块骨架 | `whale-apps/pom.xml`、`whale-pm/pom.xml`、`WhalePmApplication`、`application.yml` | 9005 可启动、注册 Nacos、暴露 `/actuator/health` |
| M1-T2 库与表 | `db/init.sql`（4 表 + 默认状态/流转种子）、`db/migration-5.55-m1.sql` | `bluewhale_pm` 可重建，索引/唯一键齐全 |
| M1-T3 项目域 | 项目 CRUD、成员与角色、状态/流转配置、项目 key 唯一、归档 | 6 类端点经网关 `code=0`；跨租户 404 |
| M1-T4 权限与菜单 | `whale-auth` `migration-5.55-pm.sql`（权限 172–190、菜单 18–23、管理员授权） | 种子幂等重跑；无权限码 403 |
| M1-T5 审计与网关 | `@AuditLog` 覆盖写操作；`/pm/**` 路由；`/pm/internal/**` 403 | `audit_log` 可按项目主键追溯 |
| M1-T6 验证与报告 | 单测 ≥ 20；`scripts/wave5.55-pm-m1.ps1` 冒烟；`docs/reviews/2026-10-25-wave5.55-m1-pm-foundation.md` | 全模块构建 SUCCESS、Checkstyle 0 |

### M2 工作项域

| 任务 | 交付物 | 验收 |
|---|---|---|
| M2-T1 工作项模型 | 统一 CRUD、父子层级、类型/优先级/指派/故事点/截止日期/标签、`item_key` 生成 | 子任务必须同项目且有父项；key 项目内唯一 |
| M2-T2 状态流转 | 流转校验、必填评论、动态写入、WIP 预检 | 非法边 400；跨项目状态拒绝 |
| M2-T3 排序 | `rank` 计算与并发重试、拖拽端点 | 20 并发插入排序不重号、顺序稳定 |
| M2-T4 协作 | 评论、附件引用、动态时间线、工作项关联 | 附件只存 `file_id`；删除引用不删文件 |
| M2-T5 幂等与并发 | `clientRequestId` 唯一键、`version` 乐观锁、`@Idempotent` | 重复创建只落 1 行；并发编辑冲突可识别 |
| M2-T6 验证与报告 | 单测 ≥ 25；M2 冒烟；检查报告 | 构建/Checkstyle/审计证据齐全 |

### M3 迭代与看板

| 任务 | 交付物 | 验收 |
|---|---|---|
| M3-T1 迭代 | 创建/编辑/启动/关闭、同项目单 ACTIVE、关闭后冻结 | 双 ACTIVE 拒绝；关闭后新增工作项拒绝 |
| M3-T2 看板 | 看板与列 CRUD、列映射状态、WIP 上限 | 超 WIP 拖拽返回业务错误 |
| M3-T3 拖拽流转 | 看板列间拖拽 = 状态流转；Backlog 拖拽 = rank 变更 | 端点幂等；刷新后顺序一致 |
| M3-T4 验证与报告 | 单测 ≥ 20；M3 冒烟；检查报告 | 构建/门禁通过 |

### M4 报表与前端

| 任务 | 交付物 | 验收 |
|---|---|---|
| M4-T1 报表后端 | 燃尽、速率、累积流、负载 4 端点 + Redis 缓存 | 空数据返回空序列不 500；1 年范围护栏 |
| M4-T2 前端骨架 | `frontend/platform-apps/whale-pm`（vite + qiankun + 8108 + base `/pm`） | `pnpm build` / `lint` 通过 |
| M4-T3 页面 | 8 个页面 + 组件 + 拖拽 + 权限按钮 | 页面可经基座 `/pm` 访问 |
| M4-T4 基座接入 | `qiankun/apps.ts`、`router/menu.ts`、`MainLayout`、`SubAppContainer` | 菜单点击进入、刷新不 404 |
| M4-T5 联调与报告 | 浏览器 E2E 清单；检查报告 | 项目→工作项→看板→报表全链路可走通 |

### M5 集成收尾

| 任务 | 交付物 | 验收 |
|---|---|---|
| M5-T1 IM 通知（可选） | whale-im 新增 `POST /im/internal/notify` 内网通道；pm 侧 `ImNotifyClient` + `PmNotifyPublisher`（`afterCommit` 异步、租户透传、失败仅 warn），指派/评论/流转三类触发；`whale.pm.notify.enabled` 默认关闭 | 关闭态零副作用；开启失败仅 warn（已由单测覆盖） |
| M5-T2 工作流审批（可选） | `WorkItemApprovalAppService` + `WorkflowApprovalClient`，`businessKey=workItemId`，身份与权限码透传；`POST /pm/work-item/{id}/approval`（权限 202）；`whale.pm.approval.enabled` 默认关闭 | 关闭态返回业务提示且无外部调用；开启后返回实例ID并记录动态 |
| M5-T3 CI 与镜像 | `docker-deploy.yml` 矩阵（M1 已纳入 whale-pm 9005）+ `coverage-gate` | 新服务进入矩阵；覆盖率达标 |
| M5-T4 全量验证 | `mvn clean install`、全量单测、Checkstyle、前端全量构建 | 全绿（pm 127 例 / im 138 例） |
| M5-T5 文档与交付 | service-catalog 终态、roadmap 勾选、devlog 四类信息、最终检查报告、集成冒烟脚本 | 文档齐全 |
| M5-T6 提交推送 | 提交 + 推送 Gitee `origin/main` | 工作树干净、远端同步 |

---

## 五、验收标准（DoD）

1. **功能**：项目 → 工作项 → 迭代 → 看板 → 报表五段主线经网关 8080 全链路可走通，前端 `/pm` 可操作。
2. **权限**：无 `sys:pm:*` 返回 403；VIEWER 不能写；非项目成员按 404/403 语义处理；跨租户一律不可见。
3. **审计**：项目、工作项、迭代、看板、成员的写操作均落 `audit_log`，可按 `targetType + targetId` 追溯。
4. **数据**：`item_key`、`rank`、项目 `key` 均唯一；重复创建幂等；并发编辑有冲突语义。
5. **质量**：新模块单测覆盖正常/无权限/不存在/跨租户/并发 5 类路径；全模块 `BUILD SUCCESS`；Checkstyle 0 违规；前端 `build` + `lint` 通过。
6. **文档**：REQ 登记、设计稿、任务拆分、检查报告、devlog 四类信息、服务清单更新全部齐备。

---

## 六、风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 对标 Jira 导致范围失控 | 交付无限延长 | 以本文 §2.2 为 MVP 门禁；自定义字段、自动化、甘特图明确延后 |
| 与 `whale-workflow` 职责混淆 | 两套状态机、数据不一致 | PM 只做执行状态机；审批流以 `businessKey` 外置到 workflow |
| Backlog 排序并发冲突 | 顺序错乱、唯一键冲突 | LexoRank + 唯一键 + 最多 3 次重试 + 局部重排 |
| 权限点/菜单 ID 冲突 | 种子失败、越权 | 固定预留区间（172–199 / 18–29），迁移脚本幂等并回写 `init.sql` |
| 多租户遗漏 | 跨租户数据泄露 | 全表 `tenant_id` + starter 插件 + 跨租户用例纳入单测与冒烟 |
| 附件/通知耦合过深 | 主事务被外部失败拖垮 | 附件只存引用；通知 `afterCommit` 异步、失败仅 warn |
| 报表性能 | 大项目查询慢 | 单项目 1 年上限、组合索引、Redis 5 分钟缓存 |
| 本机无 Docker / 中间件受限 | 端到端验证不完整 | 沿用本机 MySQL/Redis 原生进程；MQ、镜像构建标记为环境受限 |
| 前端拖拽自动化不可复现 | 回归覆盖不足 | 拖拽手势列入人工复核清单，接口层用 rank/transition 端点做自动化 |

---

## 七、待确认事项（评审时拍板）

| # | 决策点 | 推荐默认值 | 备选 |
|---|---|---|---|
| 1 | 服务归属 | `whale-apps/whale-pm` | `whale-services/whale-pm`（若视为平台核心能力） |
| 2 | 首版是否含工时/版本 | 不含（表预留、入口关闭） | 首版即做 |
| 3 | 需求评审是否走 `whale-workflow` | M5 可选、默认关闭 | 首版即接 |
| 4 | 前端形态 | qiankun 子应用 `/pm`（8108） | 独立 SPA（8005） |
| 5 | 分层 | DDD-lite 四层 | 沿用 `controller/service/mapper/entity` 三层 |

---

## 八、下一步

1. 评审本规划并确认 §七 五项决策。
2. 执行 M0：登记 REQ、更新 service-catalog / naming-conventions / roadmap。
3. 进入 M1：复制 `whale-oa` 骨架搭建 `whale-pm`，落库与权限种子。
4. 每个里程碑结束产出检查报告与 devlog，并按既定规则提交推送。
