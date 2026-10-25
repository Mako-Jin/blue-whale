# Wave 5.54 多租户现状考察与完善方案（评估稿）

> 生成日期：2026-10-25 ｜ 范围：`backend` 全模块 + `frontend` + 现有数据库脚本
> 结论：当前项目处于「多租户预留态」，**不是可用的多租户实现**；存在 1 个可直接利用的 P0 跨租户越权风险。
> 执行原则：先落文档 → 冻结决策 → 分阶段编码 → 代码检查 → 构建验证 → 提交。

---

## 一、总体结论

| 维度 | 现状判断 | 成熟度 |
|---|---|---|
| 租户实体模型 | 无 `sys_tenant`，无租户生命周期/配额 | L0 |
| 用户与租户归属 | 用户表无 `tenant_id`，账号全局唯一 | L0 |
| 令牌与上下文 | JWT 无 `tenantId` claim，服务内无 `TenantContext` | L0 |
| 网关租户注入 | 不注入、也不清除客户端 `X-Tenant-Id` | L0（且有风险） |
| 数据行级隔离 | 仅表单数据 + 审计表带 `tenant_id`，其余业务表无 | L1 |
| 隔离强制方式 | 表单靠业务代码手工 `eq(tenant_id)`；其余域无 | L1 |
| 异步/服务间传播 | 无租户传播机制 | L0 |
| 前端 | 无租户登录、无租户切换、无租户请求头 | L0 |
| 审计可追溯 | `audit_log.tenant_id` 列存在但写入恒为 `null` | L1 |

**一句话结论**：目前只有「表单数据」这一条业务线具备“行级租户字段 + 手工过滤”，其余全部是单租户假设；而表单的租户来源是客户端可伪造的请求头，等于隔离形同虚设。

---

## 二、现状盘点

### 2.1 身份与令牌链路

- 登录请求只有 `username` / `password`，无租户标识：`LoginRequest.java`。
- JWT 只签发 `sub=username`、`userId`、`roles`、`tokenType`、`jti`、`familyId`，**无 `tenantId`**：`JwtTokenProvider#generateAccessToken`。
- 网关 `JwtAuthGlobalFilter` 认证后只注入 `X-User-Id` / `X-Username` / `X-User-Roles`，并清除这三个外部伪造头；**没有处理租户头**。
- `X-Tenant-Id` 在 `WebHeaders` 中定义为「客户端可选传递，网关不注入」；表单服务 `TenantResolver` 直接读取该头，缺失回落 `1`。
- 结论：任何已登录且持有表单权限点的用户，都可以在请求中自带 `X-Tenant-Id: 2`，读写租户 2 的表单数据。这不是“预留”，而是**可用的越权通道**。

### 2.2 租户模型缺失

`whale-auth` 的 8 张 RBAC 表均无 `tenant_id`：

| 表 | 唯一约束 | 多租户影响 |
|---|---|---|
| `sys_user` | `uk_username(username)` | 不同租户不能出现同名账号 |
| `sys_role` | `uk_role_code(role_code)` | 租户不能自定义同名角色 |
| `sys_permission` | `uk_perm_code(perm_code)` | 权限点只能全平台共享 |
| `sys_menu` | 无唯一键，但无 `tenant_id` | 菜单只能全平台共享 |
| `sys_dept` | 无 `tenant_id` | 部门树只有一棵 |
| `sys_user_role` / `sys_role_permission` / `sys_user_permission` | 仅 ID 组合唯一 | 授权关系无法按租户收口 |

没有 `sys_tenant` 表，因此没有：租户编码、租户名称、状态（启用/停用）、套餐/配额、过期时间、创建者、数据隔离模式等基础字段。

### 2.3 数据落库现状

已带 `tenant_id` 的表（仅 3 类）：

| 域 | 表 | 说明 |
|---|---|---|
| 表单 | `form_data`、`form_data_{code}_v{version}` 动态物理表 | `tenant_id NOT NULL DEFAULT 1` + `idx_tenant_id` |
| 审计 | `audit_log`、`api_log`、`outbound_log`、`frontend_log`、`behavior_log` | `tenant_id DEFAULT NULL`，定位为「扩展预留」 |
| 档案 | `archive_item` 内的 `audit_log` 副本（各服务库同构） | 同上 |

未带 `tenant_id` 的业务表（全部为单租户假设）：

- 表单元数据：`form_definition`、`form_table_definition`、`form_column_meta`、`form_field_config`、`form_field_option`、`form_table_registry`、`form_definition_version`
- 文件：`file_info`（`bucket + object_key` 无租户前缀）
- 即时通讯：`im_conversation`、`im_conversation_member`、`im_message`、`im_agent_binding`、`im_agent_run`
- 工作流：`process_definition`、`process_definition_version`、`process_instance`、`process_task`
- Agent：`agent_fact`、`agent_conversation`、`agent_message`、`agent_feedback`、`agent_operation`、`agent_index_meta`、`agent_knowledge_file`
- 档案：`archive_category`、`archive_rule`、`archive_item`、`archive_file`、`archive_source_record`、`archive_sequence`、`archive_sync_state`
- 任务/支付/告警：`job_info`、`job_log`、`job_dependency`、`job_alert`、`pay_order`、`alert_rule`、`alert_record`
- 国际化：`sys_i18n_message`（平台级共享可接受，但需明确列为“全局表”）

### 2.4 上下文传播缺失

- 全仓库无 `TenantContext` / 租户 `ThreadLocal`；仅有 `UserContext`（`whale-auth-sdk`）与 `KnowledgePermissionContext`（agent）两个 ThreadLocal。
- 异步与后台链路均无租户维度：
  - `AuditLogAspect` 走 `afterCommit + 线程池`，无租户；
  - `FileKnowledgeSyncService` 定时/启动同步调用 `/file/internal/changes`，**不带租户**，把全平台文件索引进同一个知识库；
  - 工作流、任务、档案同步均为定时任务，无租户游标。
- 服务间调用：`FormArchiveClient` 的租户来自部署配置 `whale.archive.integration.tenant-id=1`，是**固定值**，不是请求方租户；其余内部调用（`/user/internal/exists`、`/file/internal/**`、`/workflow/internal/**`）均无租户头。

### 2.5 缓存、锁、唯一键、存储

- 权限缓存 Key：`whale:cache:auth:user:perm:{userId}`。用户 ID 全局唯一时尚可，但一旦支持“一人多租户”，同一 userId 在不同租户的角色不同，Key 必须带租户。
- 分布式锁 Key：`lock:auth:role:{roleId}`、`lock:cache:auth:user:perm:{userId}`，未带租户；角色若改为租户内实体，锁粒度需同步调整。
- 业务唯一性：表单 `code` / `table_code` 由服务层前置校验，改为多租户后必须变为 `(tenant_id, code)`；用户名、角色码需复合唯一。
- 对象存储：`objectKey = yyyy/MM/dd/<uuid>.<ext>`，无租户前缀；跨租户可凭 fileId 或对象键访问（下载接口目前只按 ID 查询）。
- 文件配额：`FileQuotaVO` 存在，但为全局配额，不是租户配额。

### 2.6 审计

- `AuditLogRecord` 有 `tenantId` 字段，`AuditCsvWriter` 也会导出该列；
- 但 `AuditLogAspect` 构建记录时从未 `setTenantId`，所有审计记录 `tenant_id` 恒为 `NULL`；
- 后果：无法按租户检索、无法做租户级合规导出，跨租户追责缺少关键维度。

### 2.7 业务域的“伪隔离”

- IM：按会话成员隔离（`im_conversation_member`），成员校验调用全局 `/user/internal/exists`；同租户概念不存在，A 租户可把 B 租户用户拉进群（只要知道 userId）。
- 工作流：按 `assignee` / 候选角色查询，角色码全局；不同租户共用流程定义与角色。
- Agent 知识库：档案知识按「密级 + 权限码」过滤（`KnowledgePermissionContext`），**无租户过滤**；文件知识同步全量入库，跨租户文件可被检索。
- 表单：`form_definition` 与元数据全局共享，租户只能隔离“数据”，不能隔离“表单结构”。

### 2.8 前端

- `frontend` 全仓库无 `tenant` 关键字；
- 登录页只有用户名/密码，无租户码/租户选择；
- 请求层只注入 `Authorization`，无 `X-Tenant-Id`；
- 无租户切换入口、无租户管理页面。

---

## 三、风险清单

| 级别 | 编号 | 风险 | 可利用路径 | 影响 |
|---|---|---|---|---|
| P0 | MT-01 | 客户端伪造 `X-Tenant-Id` 跨租户读写 | 登录租户 A 的用户携带 `X-Tenant-Id: 2` 调用 `/form/data/**`；`TenantResolver` 直接采信 | 数据泄露/篡改，隔离失效 |
| P0 | MT-02 | 令牌不绑定租户 | 网关无法向下游证明“该用户属于哪个租户”，所有下游只能信任请求头 | 无法建立可信租户边界 |
| P0 | MT-03 | 无租户实体与归属 | 无 `sys_tenant`、`sys_user.tenant_id` | 无法开通/停用租户，无法做租户级治理 |
| P1 | MT-04 | 账号/角色/权限全局唯一且共享 | `uk_username`、`uk_role_code` | 多租户无法同名账号、无法租户自定义角色 |
| P1 | MT-05 | 元数据不隔离 | `form_definition`、流程定义全局 | A 租户可改 B 租户也在用的表单/流程 |
| P1 | MT-06 | 审计租户为空 | `AuditLogAspect` 未写 `tenantId` | 无法按租户审计与合规导出 |
| P1 | MT-07 | 知识库跨租户索引 | `FileKnowledgeClient#changes` 无租户 | A 租户文件内容可能被 B 租户检索 |
| P1 | MT-08 | 内部调用租户固定/缺失 | `FormArchiveClient` 固定 `tenant=1` | 非 1 租户的档案归档取错数 |
| P2 | MT-09 | 缓存/锁 Key 无租户 | 权限缓存、角色锁 | 一人多租户时串权限 |
| P2 | MT-10 | 对象键无租户前缀 | `file_info.object_key` | 存储层无法按租户隔离/迁移/清理 |
| P2 | MT-11 | 异步/定时任务无租户上下文 | 审计线程池、知识同步、任务调度 | 后台链路写入数据缺租户或串租户 |
| P2 | MT-12 | 唯一约束未复合 | 表单 `code`、用户 `username` | 租户内唯一性无法保证 |
| P2 | MT-13 | 无租户配额与生命周期 | — | 无法限制单租户存储/用户数，无法停用租户 |

---

## 四、需要支持的多租户场景

| 场景 | 说明 | 关键约束 |
|---|---|---|
| S1 平台运营租户 | 平台方自身作为特殊租户（建议 `tenant_id=1`，`type=PLATFORM`） | 平台租户可见租户清单，但默认不可读业务数据 |
| S2 租户开通 | 创建租户 → 初始化管理员、默认角色、菜单/权限模板、部门根节点 | 幂等、可回滚、初始化失败可重试 |
| S3 租户内账号 | 同一 username 可在不同租户存在；登录需带租户码 | `(tenant_id, username)` 唯一 |
| S4 租户切换 | 一个自然人可属于多个租户（如顾问/集团管理员） | 令牌需带“当前租户”，切换即换令牌 |
| S5 租户管理员自助 | 租户管理员管理本租户用户/角色/菜单/部门 | 角色、菜单、部门均按租户隔离 |
| S6 业务数据隔离 | 表单数据/元数据、文件、IM、工作流、知识库、任务、支付、档案 | 所有查询强制租户条件，跨租户返回 404 |
| S7 租户级审计 | 审计记录带 `tenant_id`，支持按租户检索/导出 | 平台运营跨租户只读审计 |
| S8 租户生命周期 | 停用/恢复/续期/配额（存储、用户数、接口 QPS） | 停用后登录与 API 均拒绝 |
| S9 服务间与异步传播 | 内部调用、MQ、线程池、定时任务携带租户 | 禁止“缺失即默认 1”在后台链路静默生效 |
| S10 跨租户平台运维 | 平台超管按租户维度排障，不默认读业务明文 | 需二次授权 + 审计留痕 |

---

## 五、隔离模式选型

| 方案 | 隔离强度 | 改造成本 | 适配现状 | 结论 |
|---|---|---|---|---|
| A. 共享库 + 行级 `tenant_id` | 中（依赖强制过滤） | 低 | 与表单现有实现一致 | **推荐** |
| B. 共享库 + 按租户独立 schema | 高 | 中高（动态数据源/建库脚本） | 需重做所有表 | 作为 A 的升级路径 |
| C. 每租户独立数据库 | 最高 | 高（运维、连接池、迁移） | 与当前单库脚本冲突 | 仅大客户私有化时启用 |

**推荐 A**，理由：
1. 表单域已经按 A 落地，改造成本最低；
2. 平台当前是中小型 SaaS 形态，租户数量可控；
3. 通过「强制 SQL 条件 + 网关可信注入 + 数据库复合唯一」可以获得足够的工程隔离；
4. 在 `TenantDataSourceRouter` 上预留 B/C 扩展点，未来大客户可平滑升级。

---

## 六、推荐实施路线

### Phase 0：止血（P0，必须先做）——已完成（2026-10-25）

目标：让 `X-Tenant-Id` 从“客户端可伪造”变为“网关可信注入”。

已落地：
- 网关 `JwtAuthGlobalFilter`：所有路径先移除入站 `X-Tenant-Id`；受保护路径校验 JWT `tenantId` claim 缺失即 401，并从 claim 注入；白名单路径只做清除。
- `whale-auth`：新增 `sys_tenant`（默认租户 `id=1 / code=default`）；`sys_user` 增加 `tenant_id` 且唯一键升级为 `(tenant_id, username)`；登录请求支持 `tenantCode`（留空回落 `default`）；`UserDetailsServiceImpl` 按租户+用户名加载；JWT access/refresh 均写入 `tenantId` claim；`/auth/refresh` 校验租户启用；`/auth/me` 返回 `tenantId`。
- 数据库：全新建库用 `init.sql`；存量库执行 `migration-5.54-tenant.sql`（幂等）。
- 表单：`TenantResolver` 缺失租户头改为 400「缺少租户上下文」，不再回落租户 1。
- 审计：`AuditLogAspect` 写入 `tenantId`，缺失/非法统一记 `0`。
- 前端：admin / ops / whale-agent / whale-form-designer / whale-workflow-designer / whale-platform 登录页增加租户编码输入（默认 `default`），随登录请求提交。

尚未覆盖（按决策留待 Phase 1+）：租户管理 API、角色/菜单/部门租户化、表单元数据与其余业务表租户字段、异步与服务间租户传播、对象存储租户前缀、租户配额与生命周期。

原始规划：

1. ~~网关新增 `TenantContextGlobalFilter`~~ → 已由 `JwtAuthGlobalFilter` 统一处理：
   - 无条件移除入站 `X-Tenant-Id`；
   - 从已验签 JWT 读取 `tenantId` claim 并注入；
   - 无 claim 的旧令牌：拒绝（401），不回落默认租户。
2. ~~`whale-auth` 登录租户化~~ → 已完成：`tenantCode` + `(tenant_id, username)` + JWT `tenantId` + `/auth/me`。
3. ~~表单服务 fail-closed~~ → 已完成：缺失即 400。
4. ~~审计写入租户~~ → 已完成：缺失/非法记 `0`。
5. ~~单测~~ → 已完成：新增租户解析、JWT claim、网关伪造头/旧令牌、审计租户用例。

### Phase 1：租户模型与 RBAC 租户化（P1）——已完成（2026-10-25）

原始规划：

1. `sys_tenant`：`id`、`tenant_code`、`tenant_name`、`tenant_type`（PLATFORM/TENANT）、`status`、`expire_time`、`quota_json`、审计字段。
2. `sys_user` 增加 `tenant_id`，唯一键改 `(tenant_id, username)`；`sys_dept`、`sys_role`、`sys_menu` 增加 `tenant_id`；角色码唯一改 `(tenant_id, role_code)`。
3. 权限点保持平台级（`sys_permission` 不加租户），菜单按租户复制模板。
4. 租户开通服务：创建租户 → 初始化管理员 → 复制默认角色/菜单 → 初始化部门根；全程幂等（`tenant_code` 唯一 + 分布式锁）。
5. 租户管理 API：列表/详情/启用停用/续期/配额；权限码 `sys:tenant:*`，仅平台租户可访问。
6. 停用租户：登录拒绝、令牌校验拒绝（网关查租户状态缓存）、异步任务跳过。

已落地：

- **租户模型**：`sys_tenant` 新增 `tenant_type`（id=1 为 `PLATFORM`，新开通为 `TENANT`）、`expire_time`、`quota_json`；`Tenant` 聚合补齐不变量、`enable/disable/renew/rename/changeQuota` 行为与 `isAvailableAt` 判定。
- **RBAC 租户化**：`sys_role`/`sys_menu`/`sys_dept` 增加 `tenant_id`（存量默认 1）；角色编码唯一键升级为 `(tenant_id, role_code)`；`SysRole/SysMenu/SysDept` 实体、`Role/Menu/Dept/User` 聚合、5 个 Assembler、4 个仓储与 4 个 AppService 全部按租户读写；用户名唯一性改为 `(tenant_id, username)`。
- **租户上下文**：新增 `TenantContext`（ThreadLocal）+ `TenantContextFilter`（从网关可信 `X-Tenant-Id` 注入，请求结束清理，缺失即 400 失败关闭），接入 `SecurityConfig`；仓储读路径自动追加租户条件，跨租户详情/分页/删除视为不存在。
- **租户开通**：`TenantProvisioningAppService` 单事务编排「建租户 → 部门根 → 复制平台菜单（保持父子层级）→ 创建默认角色 `tenant_admin`（授予除 `sys:tenant:*` 外全部启用权限）→ 创建管理员并绑定角色」；`@DistributedLock` 按租户编码串行，`tenant_code` 唯一键兜底，失败整体回滚。
- **租户管理 API**：`/tenant` 提供分页/详情/开通/编辑/启停/续期，权限码 `sys:tenant:list|add|edit|status|renew`（160–164）；应用层强制 `requirePlatformTenant()`，非平台租户一律 403；平台租户自身禁止停用。
- **生命周期**：登录与刷新均要求「启用且未过期」；续期必须晚于当前时间。
- **数据库脚本**：全新建库同步 `init.sql`；存量库新增幂等脚本 `migration-5.54-tenant-rbac.sql`（列/索引/权限点先检测再变更）。

已知偏差（留待 Phase 2）：

- 停用租户的**存量 accessToken** 在过期前仍可通过网关访问其他服务：网关当前不查询租户状态。登录/刷新已拒绝，网关侧租户状态校验需随「租户状态缓存 + 网关读取」在 Phase 2 收口。
- `TenantContext` 暂位于 `whale-auth`；Phase 2 上移到 `whale-spring-boot-starter` 并配合 MyBatis-Plus 租户行插件统一覆盖全模块。
- 一人多租户（S4）仍未支持：`sys_user.tenant_id` 为单值，令牌中的租户即账号归属租户。

### Phase 2：横切能力（P1）——已完成（2026-10-25）

1. `whale-spring-boot-starter` 新增 `TenantContext`（ThreadLocal）+ `TenantContextFilter`（Servlet 最高优先级自动注册）+ `TenantContextTaskDecorator`；请求结束 `clear`，auth 本地实现删除并统一引用 starter。
2. MyBatis-Plus `TenantLineInnerInterceptor`：采用**显式表清单**（`whale.tenant.sql.tables`）自动追加 `tenant_id` 条件，默认覆盖 RBAC 4 表与 5 张日志表；无租户上下文的线程与未列入表忽略。Phase 3 业务表铺开后再切换为「全表 + 忽略白名单」。
3. 传播：
   - 审计线程池与日志线程池接入 `TenantContextTaskDecorator`；
   - 审计 MQ 消息头携带 `tenantId`；
   - 档案定时同步按配置租户列表逐租户绑定上下文（暂为配置驱动，Phase 3 接入租户目录）；
   - `FormArchiveClient` 从 `TenantContext` 取租户注入 `X-Tenant-Id`，删除 `whale.archive.integration.tenant-id` 固定配置。
4. 缓存与锁：Key 统一 `whale:{tenantId}:...`；权限缓存改 `whale:{tenantId}:cache:auth:user:perm:{userId}`（网关读取口径同步）。
5. 对象存储：`objectKey = tenants/{tenantId}/yyyy/MM/dd/<uuid>.<ext>`；按 `fileId` 下载/预览/删除时校验对象键租户前缀，跨租户按 404 处理。
6. 审计：`api_log`/`outbound_log`/`frontend_log`/`behavior_log` 均补 `tenant_id` 写入与 `(tenant_id, create_time)` 索引。
7. 网关：新增租户状态缓存校验，auth 启停租户即写 `whale:{tenantId}:cache:auth:tenant:status`，网关在验签与黑名单校验后读取，命中 `0` 直接 401；Redis 不可用时按启用降级放行并告警。

### Phase 3：业务域铺开（P2）——已完成（2026-10-25）

按“风险 × 改动量”排序：

1. 表单元数据（`form_definition` 等 7 表）加 `tenant_id`，`(tenant_id, code)` 唯一；发布/设计器按租户。
2. 文件（`file_info`）加 `tenant_id`，列表/下载/配额按租户；内部变更流按租户过滤。
3. Agent 知识库（`agent_knowledge_file`、索引）加 `tenant_id`，索引按租户分区，检索强制过滤；文件同步按租户游标。
4. 档案（7 表）加 `tenant_id`；`FormArchiveClient` 透传请求租户。
5. IM（5 表）加 `tenant_id`；群成员校验限定同租户。
6. 工作流（4 表）加 `tenant_id`；流程定义按租户发布，任务查询加租户。
7. 任务/支付/告警（`job_*`、`pay_order`、`alert_*`）加 `tenant_id`。

已落地要点：

- **数据库**：10 个库的 `init.sql` 已同步；新增 `migration-5.54-tenant-business.sql`（每服务一份、幂等），列默认 1 自动回填存量数据；业务唯一键统一升级为租户复合键（表单 code/table_code、IM pair_key、档案档号/来源/流水/游标、支付单号、告警规则码）。
- **MyBatis**：插件切换为「全表默认 + 忽略白名单」，白名单为 `sys_tenant`、`sys_permission`、`sys_i18n_message`、RBAC 关系表、审计归档水位/MQ 去重表、`agent_fact`/`agent_index_meta`。
- **表单**：非默认租户物理表名 `form_data_t{tenantId}_{code}_v{version}`，默认租户沿用历史表名，存量物理表无需重命名。
- **Agent**：Lucene 文档写入 `tenant_id`（平台知识为 0），检索强制过滤「当前租户 + 平台知识」，升级前无租户字段的文档按平台知识兼容；文件知识同步按租户目录逐租户执行。
- **任务/档案**：任务调度执行时以 `job_info.tenant_id` 绑定上下文；档案同步接入 auth 内网租户目录 `/tenant/internal/enabled-ids`，目录不可用回退默认租户。
- **内部调用**：档案→文件/工作流、Agent→文件均透传 `X-Tenant-Id`，缺失上下文失败关闭。

### Phase 4：前端与运营（P2）

1. 登录页支持租户码（或按子域名自动解析）；登录返回租户信息。
2. 顶部租户切换器（一人多租户时）；切换后刷新令牌与菜单。
3. 租户管理页（平台租户可见）：开通、停用、配额、审计导出。
4. 请求层统一注入租户头（仅用于展示/兼容，服务端仍以 JWT 为准）。
5. E2E：租户 A/B 双账号并行，验证菜单、数据、文件、知识库互不可见。

**Phase 4 执行计划（2026-10-25，编码完成；构建验证待正常环境补跑）**

- 范围与取舍：
  - 登录租户码已由 Phase 0 完成（6 个前端应用登录页均带 `tenantCode`），本期补「登录/`/auth/me` 的 `tenantId` 落地 + 登录输入的 `tenantCode` 持久化 + 顶栏展示当前租户」。
  - 一人多租户切换器本期不实现：`sys_user.tenant_id` 为单值，账号归属租户唯一（D3 后续项），切换需先补齐「用户-租户多对多 + 换令牌」后端能力。
  - 审计导出不在本期：后端暂无租户级审计导出接口，避免前端空壳按钮。
- 文件与改动：
  - `frontend/shared-packages/http/src/client.ts`：`HttpClientConfig` 增加可选 `getTenantId`，在普通请求/文件下载/SSE 三类发送前统一注入 `X-Tenant-Id`（不传则保持原行为；服务端仍以 JWT claim 为准，网关会清除入站并重新注入）。
  - `frontend/admin/src/api/token.ts`：`LoginResponse`/`AuthMeResponse`/`UserInfo` 补 `tenantId`，`UserInfo` 补展示用 `tenantCode`；`saveLogin(data, tenantCode?)` 合并保存，保留既有权限/菜单。
  - `frontend/admin/src/api/auth.ts`：登录成功时把表单租户码一并落盘；`fetchMe` 回写 `tenantId`。
  - `frontend/admin/src/api/client.ts`：接入 `getTenantId`（读取当前登录用户 `tenantId`）。
  - `frontend/admin/src/api/tenant.ts`（新增）：`/tenant` 分页/详情/开通/更新/启停/续期，类型对齐 `TenantVO` 与后端 DTO。
  - `frontend/admin/src/pages/tenant/TenantList.tsx`（新增）：`ProTable + SearchForm + FormModal + AuthButton` 风格，列表展示编码/名称/类型/状态/到期/配额；开通（编码、名称、管理员、到期、配额 JSON）、编辑（名称/配额）、启停、续期；平台租户行禁止停用。
  - `frontend/admin/src/App.tsx`、`router/dynamicRoutes.tsx`、`layouts/AdminLayout.tsx`：注册 `/tenants` 静态路由与 `tenant` 组件契约键；导航在动态菜单未包含 `/tenants` 且具备 `sys:tenant:list` 时补充入口；顶栏展示当前租户编码。
- 验收：
  - 平台租户账号可见「租户管理」入口；非平台租户即使直接访问 `/tenants` 也因后端 `requirePlatformTenant()` 返回 403。
  - 开通/编辑/启停/续期均按权限码 `sys:tenant:add|edit|status|renew` 控制按钮，成功后列表刷新。
  - 所有 admin 请求携带 `X-Tenant-Id`（与 JWT 一致），伪造该头不影响服务端隔离结论。
  - admin `tsc -b && vite build` 通过；不改变未接入应用的既有行为。

---

## 七、关键决策（需确认）

| 编号 | 决策 | 选项 | 建议 |
|---|---|---|---|
| D1 | 隔离模式 | A 共享库行级 / B schema / C 独立库 | A |
| D2 | 租户入口 | 登录填租户码 / 子域名 / 独立登录域 | 登录填租户码（先做），子域名后置 |
| D3 | 一人多租户 | 支持 / 不支持 | 支持，令牌携带“当前租户”，切换即换令牌 |
| D4 | 平台超管读业务数据 | 默认禁止 / 二次授权 | 默认禁止，二次授权 + 审计 |
| D5 | 默认租户回落 | 拒绝 / 回落 1 | **拒绝**（P0 止血口径） |
| D6 | 本期范围 | 仅 P0 / P0+Phase 1 / 全阶段 | P0 + Phase 1（均已完成）；Phase 2 起按阶段推进 |

---

## 八、验收与测试清单

**网关与令牌**
- 伪造 `X-Tenant-Id: 2` 访问 `/form/data` → 实际按 JWT 租户处理（不串租）。
- 无 `tenantId` claim 的旧令牌 → 401。
- 停用租户的令牌 → 401/403。

**数据隔离**
- 租户 A 详情/分页/导出/删除访问租户 B 数据 → 404。
- 租户 A 与租户 B 使用相同表单 `code`、相同 username → 均成功创建。
- 跨租户内部调用 `/form/internal/data/{id}` → 404。

**横切**
- 异步线程池/MQ/定时任务中 `TenantContext` 正确传递且结束后清理。
- 缓存 Key 含租户，切换租户不串权限。
- 对象键含租户前缀；跨租户下载 403/404。
- 审计四通道 `tenant_id` 非空。

**回归**
- 单租户（仅默认租户 1）现有全部单测、冒烟脚本保持通过。
- 全模块 `mvn install` BUILD SUCCESS；前端 `tsc -b && vite build` 通过。

---

## 九、执行记录（本次评估）

| 类别 | 内容 |
|---|---|
| 需求项 | ① 考察现有项目多租户实现；② 识别隔离缺口与风险；③ 给出可落地的完善场景与分阶段方案；④ 按确认结论（共享库行级 / 仅 Phase 0 / 登录填租户码）实施止血 |
| 完成项 | ① 盘点 auth 8 表、10 个业务域 40+ 表的租户字段；② 梳理 JWT/网关/服务内上下文链路；③ 定位 P0 伪造租户头越权路径；④ 输出 S1–S10 场景与 Phase 0–4 路线；⑤ 给出 D1–D6 决策；⑥ Phase 0 代码落地（网关清头+claim 注入、sys_tenant、sys_user.tenant_id、登录 tenantCode、JWT tenantId、表单 fail-closed、审计租户、6 个前端登录页）；⑦ auth/starter/gateway/form 模块单测通过；⑧ 后端全模块 `mvn install` 通过（含 checkstyle 与全部单测） |
| 待做项 | ① Phase 2 `TenantContext` 上移 starter + MyBatis 租户插件 + 异步/MQ/服务间传播 + 缓存/锁/存储前缀 + 网关租户状态校验；② Phase 3 表单元数据、文件、IM、工作流、Agent、档案、任务、支付、告警铺开；③ Phase 4 租户切换与运营台；④ 在联调环境执行 `migration-5.54-tenant-rbac.sql` 并回归登录/租户开通/RBAC 链路；⑤ 前端构建验证：本机执行 node/pnpm 时命令沙箱后端异常（`SandboxBackendFailed`），本次仅完成人工代码审查，需在正常环境补跑 `pnpm build` |
| 已修正问题项 | ① 旧令牌无租户 claim 的越权兜底：改为 401，不回落默认租户；② 白名单路径伪造租户头：同样清除；③ Mockito 严格桩在“租户解析前失败”用例误报：改用 lenient；④ 新增领域类缺少方法 Javadoc：按 checkstyle 补齐；⑤ `JwtUtilsTest` 篡改令牌用例偶发失败：改为确定性篡改签名段首字符 |

---

## 十、Phase 1 执行记录（2026-10-25）

| 类别 | 内容 |
|---|---|
| 需求项 | ① `sys_tenant` 生命周期/配额字段与租户管理 API；② 角色/菜单/部门租户化与租户内唯一；③ 租户开通（管理员、默认角色、菜单模板、部门根）；④ 启停/续期；⑤ 幂等迁移脚本与文档 |
| 完成项 | ① `sys_tenant` 增加 `tenant_type/expire_time/quota_json`，id=1 标记 PLATFORM；② `sys_role/sys_menu/sys_dept` 增加 `tenant_id`，角色唯一键改 `(tenant_id, role_code)`；③ 5 个实体/聚合/Assembler、4 个仓储、4 个 AppService 全量租户化；④ `TenantContext` + `TenantContextFilter` 失败关闭；⑤ `TenantProvisioningAppService` 事务化开通；⑥ `/tenant` 六个管理接口 + 平台租户准入；⑦ 登录/刷新校验过期；⑧ `init.sql` 同步、`migration-5.54-tenant-rbac.sql` 幂等脚本；⑨ auth 单测 322 个全通过；⑩ 后端全模块 `mvn install -DskipTests` BUILD SUCCESS |
| 待做项 | ① 网关租户状态缓存校验（停用租户存量令牌）；② `TenantContext` 上移 starter + MyBatis 租户插件；③ 前端租户管理页与 E2E；④ 联调环境执行 RBAC 迁移脚本 |
| 已修正问题项 | ① 仓储改造后登录租户解析误改 400：恢复为 401，与刷新链路一致；② `save` 改为返回回填主键的聚合，移除“保存后按用户名回查”的竞态；③ 角色/菜单/部门写路径统一从 `TenantContext` 取租户，禁止默认租户回落 |

---

## 十一、Phase 2 执行记录（2026-10-25）

| 类别 | 内容 |
|---|---|
| 需求项 | ① 租户上下文上移 starter 并全服务生效；② MyBatis 行级租户插件；③ 异步/线程池/MQ/定时任务/内部调用租户传播；④ 缓存与锁 Key 带租户；⑤ 对象存储租户前缀与访问校验；⑥ 四类日志补 `tenant_id` 与索引；⑦ 网关租户状态校验收口停用租户存量令牌 |
| 完成项 | ① 新增 `com.bluewhale.common.tenant.TenantContext/TenantContextFilter/TenantContextTaskDecorator/TenantKeys/TenantSqlProperties/TenantContextAutoConfiguration`，auth 删除本地同名实现并全量改引用；② `MybatisPlusConfig` 接入 `TenantLineInnerInterceptor`（显式表清单，租户插件先于分页）；③ 审计/日志线程池接入装饰器，审计 MQ 增加 `tenantId` 头，`FormArchiveClient` 删除固定租户配置并改用上下文，档案定时同步逐租户绑定上下文；④ `CacheConsistencyTemplate` 与 `DistributedLockAspect` Key 统一 `whale:{tenantId}:...`，网关权限缓存 Key 同步；⑤ 文件对象键增加 `tenants/{tenantId}/` 前缀，按 ID 访问校验租户前缀；⑥ 四类日志实体与写入补 `tenantId`，`init.sql` 双库同步，新增 `migration-5.54-tenant-logs.sql`；⑦ 新增 `TenantStatusKeys`（whale-security）、`TenantStatusChecker`（gateway）与 `TenantStatusCache`（auth，含启动预热与启停即时写入）；⑧ starter/auth/gateway/file/archive/audit 单测通过，后端全模块 `mvn install` BUILD SUCCESS |
| 待做项 | ① MyBatis 插件在 Phase 3 切换为「全表默认 + 白名单忽略」，当前为显式表清单（未铺开 `tenant_id` 的业务表若强制追加会 SQL 失败）；② 定时任务租户清单暂由 `whale.archive.sync.tenant-ids` 配置提供，Phase 3 接入 `sys_tenant` 启用租户目录后改为动态查询；③ Agent 文件知识同步按租户游标分区依赖 `agent_knowledge_file` 铺开 `tenant_id`，留待 Phase 3；④ 存量对象键无 `tenants/` 前缀，需在联调环境执行一次对象迁移或保留只读兼容窗口；⑤ 联调环境需在 `bluewhale_auth` 与 `bluewhale_audit` 两库分别执行 `migration-5.54-tenant-logs.sql` |
| 已修正问题项 | ① `CacheProperties.keyPrefix` 删除后测试仍按旧 Key 断言：统一改为 `whale:{tenantId}:cache:{name}:{id}` 并显式绑定租户；② 网关新增状态校验后测试未桩化导致 `UnnecessaryStubbingException`：改为按用例精确桩化；③ `TenantContext` 上移后 auth 测试仍使用旧包名：测试迁移到 starter 的 `com.bluewhale.common.tenant` 包；④ `FormArchiveClient` 删除固定租户后定时同步无上下文：调度器改为逐租户设置/清理 `TenantContext`；⑤ 停用租户的存量令牌：网关验签后增加 Redis 状态校验并补充 401 单测 |

---

## 十二、Phase 3 执行记录（2026-10-25）

| 类别 | 内容 |
|---|---|
| 需求项 | ① 表单元数据/文件/IM/工作流/Agent/档案/任务/支付/告警铺开 `tenant_id`；② MyBatis 插件切「全表默认 + 白名单忽略」；③ 表单物理表按租户命名；④ Agent 知识索引租户分区与按租户同步；⑤ 任务/档案定时任务按租户目录执行；⑥ 存量数据回填默认租户；⑦ 幂等迁移脚本与文档 |
| 完成项 | ① 10 个库 `init.sql` 同步新增 `tenant_id` 与索引，业务唯一键升级为租户复合键；② 新增 10 份 `migration-5.54-tenant-business.sql`（列默认 1，存量自动归属默认租户）；③ `TenantSqlProperties` 改为 `ignoreTables` 白名单，`MybatisPlusConfig` 全表默认隔离；④ 非默认租户表单物理表名带 `t{tenantId}_` 段；⑤ Lucene 文档写入 `tenant_id`（平台知识 0），BM25/KNN 检索强制租户过滤并兼容历史无字段文档；⑥ 文件知识同步与档案同步接入租户目录（auth `/tenant/internal/enabled-ids`），档案→文件/工作流、Agent→文件内部调用透传 `X-Tenant-Id`；⑦ 任务调度按 `job_info.tenant_id` 绑定上下文；⑧ Agent/档案/任务相关模块单测与后端全量构建通过 |
| 待做项 | ① **已完成（2026-10-25）**：新建 `bluewhale_job/pay/alert` 三库，11 个业务库 + auth 日志迁移两轮幂等通过，12 库 `tenant_id` 覆盖校验通过（详见 [执行报告](../../reviews/2026-10-25-wave5.54-5.53-deploy-execution.md)）；② **已完成（元数据层，2026-10-25）**：`file_info` 7/7 对象键迁移为 `tenants/1/` 前缀；本地对象桶为空，无物理文件搬移，远端对象存储环境仍需同步搬移；③ Agent 一次性重建索引（清掉无租户字段的历史文档）建议在维护窗口执行；④ 一人多租户（S4）5.4 双租户 E2E 与 Phase 4 前端租户切换/运营台 |
| 已知限制 | ① 平台级知识（代码/文档/`agent_fact`）对所有租户可见，按设计保留；② RBAC 关系表沿用全局唯一 ID 关联，不加租户列（写入白名单）；③ 表单动态物理表租户隔离依赖已发布物理表固定名，租户编码变更不支持，仅支持编码级隔离 |

---

## 十三、Phase 4 执行记录（2026-10-25）

| 类别 | 内容 |
|---|---|
| 需求项 | ① 登录返回的租户信息在前端落地并展示；② 共享请求层统一注入 `X-Tenant-Id`；③ 平台租户管理页（分页/开通/编辑配额/启停/续期）；④ 租户管理路由与权限可见导航；⑤ 前端构建验证与文档 |
| 完成项 | ① `@bluewhale/http` 的 `HttpClientConfig` 新增可选 `getTenantId`，在 `newHeaders()` 统一注入 `X-Tenant-Id`（普通请求/下载/SSE/续期全部覆盖，未配置时保持原行为）；② admin `LoginResponse`/`AuthMeResponse`/`UserInfo` 补 `tenantId`，`UserInfo` 补展示用 `tenantCode`，`saveLogin(data, tenantCode?)` 合并落盘，`fetchMe` 回写租户；③ admin 顶栏展示「租户 {tenantCode 或 tenantId}」标签；④ 新增 `api/tenant.ts`（`/tenant` 分页/详情/开通/更新/启停/续期，字段对齐 `TenantVO` 与后端 DTO）；⑤ 新增 `pages/tenant/TenantList.tsx`（`ProTable + SearchForm + FormModal + AuthButton`，平台租户行禁止停用，按钮按 `sys:tenant:*` 权限码隐藏）；⑥ `App.tsx` 注册 `/tenants` 静态路由，`dynamicRoutes` 增加 `tenant` 组件契约键，`AdminLayout` 在动态菜单缺失且持有 `sys:tenant:list` 时补充入口；⑦ admin `vite.config.ts` 开发代理增加 `tenant` 前缀 |
| 待做项 | ① 一人多租户切换器（D3，需先补「用户-租户多对多 + 换令牌」后端能力）；② 租户 A/B 双账号 E2E（依赖双租户联调环境）；③ 租户级审计导出（后端暂无接口）；④ 联调环境执行各库 `migration-5.54-tenant-*.sql`；⑤ 在正常环境补跑 `pnpm --filter @bluewhale/http build`、`pnpm --filter whale-admin build`（含 `tsc -b`）与 `pnpm --filter whale-admin lint` |
| 已知限制 | ① 本次命令执行环境返回 `sandbox backend failed`（含 `node -v`），前端构建与 lint 未能在本机执行，仅完成人工类型审查；② `dist/` 为构建产物且被 git 忽略，需先构建 `@bluewhale/http` 再构建 admin；③ 请求头租户仅兼容展示，服务端仍以 JWT `tenantId` claim 为准，伪造请求头不改变隔离结论；④ 业务租户即使直接访问 `/tenants`，后端 `requirePlatformTenant()` 返回 403 |

---

## 十四、验证清单（正常环境执行）

> 本机 Trae 执行器对 `node` / `pnpm` / `where.exe` 返回 `sandbox backend failed`（`git` 正常），以下命令需在普通 PowerShell 5 终端执行。
> 执行顺序：依赖 → 后端构建 → 数据库迁移 → 前端构建 → 启动服务 → API 冒烟 → E2E → 手工验收。
> 每项通过后在“结果”列记录命令输出摘要与时间。

### 14.1 依赖与后端构建

| # | 命令 | 通过标准 |
|---|---|---|
| 1 | `cd f:\blue-whale\frontend; pnpm install --frozen-lockfile` | 退出码 0，无 lockfile 变更 |
| 2 | `mvn -f f:\blue-whale\backend\pom.xml install -DskipTests=false` | `BUILD SUCCESS`；checkstyle、单测全部通过 |
| 3 | `mvn -f f:\blue-whale\backend\pom.xml -pl whale-services/whale-auth -am test -DskipTests=false` | auth 模块单测通过（含租户解析、JWT claim、审计租户用例） |

### 14.2 数据库迁移（逐库执行，脚本均幂等）

| 库 | 脚本（`{module}` 内） | 关键验证 SQL |
|---|---|---|
| `bluewhale_auth` | `backend/whale-services/whale-auth/src/main/resources/db/migration-5.54-tenant.sql`、`migration-5.54-tenant-rbac.sql`、`migration-5.54-tenant-business.sql` | `SELECT tenant_code, tenant_type, status FROM sys_tenant;` 期望 `default / PLATFORM / 1`；`SELECT perm_code FROM sys_permission WHERE perm_code LIKE 'sys:tenant:%';` 期望 5 行 |
| `bluewhale_audit` | `backend/whale-ops/whale-audit/src/main/resources/db/migration-5.54-tenant-logs.sql` | `audit_log.tenant_id` 已回填为 `1`，无 `NULL` 历史行 |
| `bluewhale_form` | `whale-form/.../migration-5.54-tenant-business.sql` | `form_definition.tenant_id` 存在且历史行=1 |
| `bluewhale_file` | `whale-file/.../migration-5.54-tenant-business.sql` | `file_info.tenant_id` 存在且历史行=1 |
| `bluewhale_im` | `whale-im/.../migration-5.54-tenant-business.sql` | `im_conversation.tenant_id` 存在 |
| `bluewhale_workflow` | `whale-workflow/.../migration-5.54-tenant-business.sql` | `process_definition.tenant_id` 存在 |
| `bluewhale_agent` | `whale-agent/.../migration-5.54-tenant-business.sql` | `agent_conversation.tenant_id` 存在 |
| `bluewhale_archive` | `whale-archive/.../migration-5.54-tenant-business.sql` | `archive_item.tenant_id` 存在 |
| `bluewhale_job` | `whale-job/.../migration-5.54-tenant-business.sql` | `job_info.tenant_id` 存在 |
| `bluewhale_pay` | `whale-pay/.../migration-5.54-tenant-business.sql` | `pay_order.tenant_id` 存在 |
| `bluewhale_alert` | `backend/whale-ops/whale-alert/.../migration-5.54-tenant-business.sql` | `alert_rule.tenant_id` 存在 |

**执行结果（2026-10-25）**：上表 11 个业务库 + `bluewhale_auth` 核心/日志共 14 个脚本已全部执行，并以第二轮复跑验证幂等（14/14 退出码 0）；12 库 `tenant_id` 列覆盖校验全部通过。执行中补齐了缺失库（job/pay/alert）与缺失表（agent 知识文件、audit/auth 四类日志表），未删除既有数据。详见 [执行报告](../../reviews/2026-10-25-wave5.54-5.53-deploy-execution.md)。14.4 API 冒烟与 14.5 E2E 仍因 Redis/Nacos 不可用而阻塞。

执行方式（PowerShell 不支持 `<` 输入重定向，需借 `cmd /c`）：

```powershell
cmd /c "mysql -h 127.0.0.1 -P 3306 -u root -p bluewhale_auth < f:\blue-whale\backend\whale-services\whale-auth\src\main\resources\db\migration-5.54-tenant.sql"
```

列存在性通用检查：

```sql
SELECT COUNT(*) FROM information_schema.columns
WHERE table_schema = 'bluewhale_form' AND table_name = 'form_definition' AND column_name = 'tenant_id';
-- 期望 1
```

### 14.3 前端构建与 lint

| # | 命令（cwd = `f:\blue-whale\frontend`） | 通过标准 |
|---|---|---|
| 1 | `pnpm --filter @bluewhale/http build` | 生成 `shared-packages/http/dist`（`dist/` 被 git 忽略） |
| 2 | `pnpm --filter whale-admin lint` | `--max-warnings 0` 通过 |
| 3 | `pnpm --filter whale-admin build` | `tsc -b` 与 `vite build` 均成功 |
| 4 | `pnpm --filter @bluewhale/whale-ui build`（如改共享组件） | 成功 |

### 14.4 API 冒烟（PowerShell）

前置：网关（默认 `:8080`）、auth、form 已启动；平台管理员密码以实际环境为准。

```powershell
# 1) 平台租户登录：tenantId 必须为 1
$p = Invoke-RestMethod -Method Post -Uri http://localhost:8080/auth/login -ContentType 'application/json' `
  -Body (@{ username = 'admin'; password = '实际密码'; tenantCode = 'default' } | ConvertTo-Json)
$ph = @{ Authorization = "Bearer $($p.accessToken)" }
(Invoke-RestMethod -Uri http://localhost:8080/auth/me -Headers $ph).tenantId   # 期望 1

# 2) 租户管理仅平台租户：业务租户令牌必须 403
# 先以业务租户管理员登录得到 $b，再：
Invoke-RestMethod -Uri 'http://localhost:8080/tenant/page?current=1&size=10' -Headers @{ Authorization = "Bearer $($b.accessToken)" }
# 期望 403

# 3) 伪造租户头无效：平台令牌 + X-Tenant-Id=2 查询表单数据，结果仍限租户 1
Invoke-RestMethod -Uri 'http://localhost:8080/form/data/{code}/page?current=1&size=10' `
  -Headers @{ Authorization = "Bearer $($p.accessToken)"; 'X-Tenant-Id' = '2' }

# 4) 停用租户：登录 401；存量 accessToken 经网关 401（Phase 2 租户状态校验）
# 5) 开通租户后：新租户管理员可登录；重复 tenantCode 返回 4xx 且不产生半初始化数据
```

### 14.5 E2E

```powershell
cd f:\blue-whale\frontend
pnpm --filter @bluewhale/e2e test
```

- 前置：网关/auth/form/workflow 就绪；Playwright 自动拉起 admin(:8001)、表单设计器(:8002)、工作流设计器(:8003)。
- 地址可用 `E2E_ADMIN_BASE_URL` / `E2E_FORM_BASE_URL` / `E2E_WORKFLOW_BASE_URL` / `E2E_API_BASE_URL` 覆盖。
- 现有用例：`login-menu.spec.ts`、`form-loop.spec.ts`、`workflow-loop.spec.ts`；租户隔离与切换用例随 Phase 5 增加 `tenant-switch.spec.ts`。

### 14.6 手工验收（admin）

| 场景 | 步骤 | 期望 |
|---|---|---|
| 顶栏租户标识 | `default` 登录 | 顶栏出现「租户 default」标签 |
| 租户管理入口 | 平台管理员登录 | 动态菜单缺失时也出现「租户管理」 |
| 非平台不可见 | 业务租户管理员登录 | 无「租户管理」入口；直接访问 `/tenants` 收到 403 提示 |
| 开通租户 | 填写编码/名称/管理员/到期/配额 | 列表新增；新管理员可登录；重复编码失败 |
| 编辑与配额 | 修改名称与配额 JSON | 列表即时刷新，配额摘要正确展示 |
| 启停 | 切换业务租户状态 | 停用后该租户登录 401；平台租户行开关禁用 |
| 续期 | 选择新到期时间 | 到期时间更新；早于当前时间被拒 |
| 请求头兼容 | DevTools 观察 admin 请求 | 携带 `X-Tenant-Id`，且与 JWT 租户一致 |

---

## 十五、一人多租户（S4）切换器设计（待评审）

### 15.1 目标与不变量

- 目标：一个自然人用同一套登录凭证与同一 `userId` 在多个租户间切换；切换只改变令牌中的租户上下文，角色/权限/菜单/数据全部按目标租户重新解析。
- 不变量：
  1. 任何时刻令牌只绑定一个租户；服务端仍以 JWT `tenantId` 为准。
  2. **切换不换 `userId`、不换 `sub`**：账号只有一条 `sys_user`（归属首页租户），`(tenant_id, username)` 唯一规则不变。
  3. 租户隔离由 `sys_role.tenant_id` + 业务表行级 `tenant_id` 过滤实现；新增关系只回答「该用户可进入哪些租户」，不放宽业务过滤。
  4. 角色解析必须按当前租户过滤 `sys_role`，禁止把其他租户的角色带入权限码计算。
  5. 切换必须显式校验关系，不接受客户端传入的 userId/roles。

### 15.2 现状约束

- `sys_user.tenant_id` 单值，账号归属唯一租户；同名账号可在不同租户各自存在（S3 已支持）。
- 登录按 `tenantCode + username + password` 定位 `(tenant_id, username)` 账号；JWT 写入 `tenantId`、`userId`、`sub`。
- 刷新按 `refreshToken` 的 `tenantId` claim + `sub` 重新加载启用账号。
- 权限缓存 Key 已含租户；`TenantContext` 由网关 `X-Tenant-Id` 注入；菜单、部门、角色均带 `tenant_id`。
- `sys_user_role` / `sys_role_permission` 本身**没有** `tenant_id` 列，租户归属由 `role_id` 关联 `sys_role.tenant_id` 推导。
- 结论：切换只需为同一 `userId` 增加「可进入租户」关系，并在签发令牌时按目标租户重算角色；不需要成员账号，也不换 `userId`。
- 现状缺口（本期一并修复）：`RoleRepositoryImpl#listRoleIdsByUserId` 只查 `sys_user_role`、未按 `sys_role.tenant_id` 过滤；`listPermissionIdsByRoleIds` 直接按角色 ID 取权限。同一用户在其他租户的角色权限会被计入当前租户。新模型下必须以当前租户过滤后的角色 ID 作为权限计算入口。

### 15.3 数据模型：`sys_user_tenant`

```sql
CREATE TABLE IF NOT EXISTS sys_user_tenant (
  id          BIGINT   NOT NULL COMMENT '主键',
  user_id     BIGINT   NOT NULL COMMENT '全局用户ID（sys_user.id，切换前后不变）',
  tenant_id   BIGINT   NOT NULL COMMENT '可进入的租户ID',
  status      TINYINT  NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_tenant (user_id, tenant_id),
  KEY idx_tenant_id (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户可进入的租户关系（跨租户，忽略行级租户过滤）';
```

- 关系表**加入 MyBatis 租户插件忽略白名单**，所有查询显式带 `user_id` 条件。
- 采用物理删除（无 `deleted` 列），避免软删与唯一键冲突。
- 首页关系：每个 `sys_user` 必有一行 `(user_id=self, tenant_id=self.tenant)`；`sys_user.tenant_id` 仅表示首页/登录定位，切换不修改。
- 不再创建“成员账号”：目标租户的角色直接写入 `sys_user_role`（`user_id` 不变、`role_id` 属于目标租户），由 `sys_role.tenant_id` 天然完成租户归属。

### 15.4 认证与令牌流

```
登录（tenantCode + username + password）
  └─ 按 (tenantCode, username) 定位账号 → 签发 tenantId=账号归属租户，userId/sub 取自该账号
       │
       ├─ GET /auth/tenants → 按 userId 列出 sys_user_tenant 启用关系（租户启用且未过期），标记当前项
       │
       └─ POST /auth/tenant/switch { tenantId }
            ├─ 从 SecurityContext 取 userId（不信任客户端）
            ├─ 校验 (userId, tenantId) 关系存在且启用、目标租户启用且未过期、用户启用
            ├─ 置 TenantContext=目标租户，按目标租户重算角色（sys_user_role ⋈ sys_role.tenant_id）
            ├─ 将当前 accessToken + 提交的 refreshToken 写入黑名单（开启轮换时吊销 family）
            └─ 签发新令牌：tenantId=目标租户，userId/sub 不变，新 familyId
```

- 刷新：`userId` 取自 token claim；用户按 ID 加载（`sys_user` 归属首页，需绕过行级租户过滤）；校验 `(userId, tenantId)` 关系；角色按令牌 `tenantId` 解析。关系撤销后 refresh 立即 401。
- `/auth/me`：角色、权限码、菜单均按当前租户解析；权限缓存 Key 已含租户，切换不会串权限。
- 角色解析入口统一改为「先取当前租户下的启用角色，再取权限」；`sys_user_role` 的跨租户行不会泄漏到当前租户。
- 登出、网关、租户状态校验均不变；不新增 JWT claim。
- 旧 accessToken 在切换时被黑名单，避免新旧租户令牌并存。
- 角色变更后的缓存失效按角色所属租户执行，不影响该用户在其他租户的缓存。

### 15.5 接口契约

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/auth/tenants` | 已登录 | 返回 `[{tenantId, tenantCode, tenantName, current}]`，仅启用且未过期租户；`userId` 与登录态一致 |
| POST | `/auth/tenant/switch` | 已登录 | 入参 `{tenantId}`；出参同 `LoginResponse`（新令牌 + tenantId/username/nickname/roles，`userId` 不变） |
| GET | `/tenant/members` | `sys:tenant:member:list` | 当前租户成员：关系表 + 用户基本信息（跨租户按 ID 查）+ 本租户角色 |
| PUT | `/tenant/members/{userId}/roles` | `sys:tenant:member:assign` | 全量覆盖该用户在本租户的角色；只删除/写入 `role_id` 属于本租户的关联行 |
| POST | `/user/{userId}/tenants` | `sys:user:tenant` | 平台租户：为已有用户增加可进入租户关系 |
| DELETE | `/user/{userId}/tenants/{tenantId}` | `sys:user:tenant` | 解除关系；不允许删除首页关系 |

- `switch` 失败语义：非成员 `403`；目标租户停用/过期 `403`（文案区分）；用户停用 `401`。
- 角色分配必须按租户收口：`PUT /tenant/members/{userId}/roles` 只处理当前租户角色的关联，禁止清空该用户在其他租户的 `sys_user_role`。
- 新增权限码 `sys:user:tenant`、`sys:tenant:member:list`、`sys:tenant:member:assign`（建议 165–167），同时更新 `init.sql` 与幂等迁移。
- `sys_user_role` 保持无 `tenant_id` 列，租户归属一律由关联的 `sys_role.tenant_id` 推导；不新增关系表租户列。

### 15.6 服务端改造点

| 文件/层 | 改动 |
|---|---|
| `whale-auth` DB | 新增 `sys_user_tenant` 建表 + 首页关系回填；`init.sql` 同步；`migration-5.54-tenant-membership.sql` 幂等 |
| `SysUserTenant` 实体/Mapper/仓储 | 关系读写；加入租户插件忽略白名单 |
| `TenantMembershipAppService` | 按 userId 列可进入租户、关系校验、绑定/解绑 |
| `RoleRepositoryImpl` | `listRoleIdsByUserId` 改为「`sys_user_role` ⋈ `sys_role` 且 `tenant_id=当前租户` 且启用」；`listPermissionCodesByUserId` 以租户内角色 ID 为入口，修复跨租户权限计入 |
| `UserRepository` / `SysUserMapper` | 新增按 ID 加载（忽略租户行过滤），供切换与刷新使用（`sys_user` 归属首页租户）；`UserDetailsServiceImpl` 增加 `loadUserById` 装配目标租户角色 |
| `UserAppService` / `TenantProvisioningAppService` | 新建账号（含租户初始管理员）时写首页关系 |
| `AuthService` | 新增 `listTenants()`、`switchTenant()`；`refresh()` 改为按 `userId` claim 加载并校验关系 |
| `TenantMemberAppService` / `UserAppService` | 租户成员列表、租户内角色分配、平台绑定/解绑关系 |
| `AuthController` / `UserController` / `TenantController` | 新增 6 个接口；`@AuditLog` 记录切换与关系变更 |
| 网关 | 无需改动 |

### 15.7 前端改造点（admin 优先）

- 新增 `api/auth.ts#listMyTenants / switchTenant`（类型 `MyTenantVO`、`LoginResponse`）。
- 顶栏 `AdminLayout`：当可切换租户数 > 1 时展示 `Select`；切换调用接口成功后 `saveLogin(data, targetTenantCode)`，随后 `window.location.replace('/')` 全量重载，确保权限/菜单/请求头全部按新租户重建。
- 切换失败保持当前会话并提示；单租户用户不显示切换器。
- 其他 5 个前端应用本期不改，后续可抽 `whale-ui` 的 `TenantSwitcher` 复用。

### 15.8 安全与边界

- 关系表跨租户，只由 `TenantMembershipAppService` 访问，禁止在业务查询中 join 绕过租户过滤。
- 切换只信 `SecurityContext`，客户端不能指定 `userId`。
- 角色分配按租户收口：只增删本租户角色的 `sys_user_role` 行，禁止整用户全量删除导致其他租户角色丢失。
- 用户启停是全局的（同一条 `sys_user` 记录），停用后所有租户均拒绝；这是单账号模型的明确取舍。
- 关系撤销后 refresh 失效，但存量 accessToken 最长存活一个 access TTL（与停用租户同级的已知限制）。
- 首页关系禁止删除；停用关系保留并置 `status=0`。
- 审计：`AUTH_TENANT_SWITCH` 记录 `userId/fromTenantId/toTenantId`。

### 15.9 迁移与回填

```sql
-- 幂等建表后回填首页关系
INSERT INTO sys_user_tenant (id, user_id, tenant_id, status)
SELECT s.id, s.id, s.tenant_id, 1
FROM sys_user s
WHERE NOT EXISTS (
  SELECT 1 FROM sys_user_tenant m WHERE m.user_id = s.id
);
```

- 新库由 `init.sql` 直接建表；存量库执行 `migration-5.54-tenant-membership.sql`。
- 回填后可抽查：`SELECT COUNT(*) FROM sys_user s LEFT JOIN sys_user_tenant m ON m.user_id = s.id WHERE m.id IS NULL;` 期望 0。

### 15.10 验收标准

1. 单租户用户：`/auth/tenants` 仅返回首页租户，前端不出现切换器。
2. 双租户用户：切换前后 `userId`/`sub` 不变，令牌 `tenantId` 变为目标租户；`/auth/me` 的角色、权限码、菜单均来自目标租户。
3. 角色隔离：同一用户在 A、B 各有一个角色时，切换到 B 后只能得到 B 角色对应权限，A 的权限码不得出现。
4. 数据隔离：切换后业务查询只返回目标租户数据；旧租户数据不可见。
5. 切换后旧 accessToken/refreshToken 立即失效（401）。
6. 刷新：即使 `sys_user.tenant_id` 仍是首页租户，按 `userId` claim 也能在目标租户下刷新成功；关系撤销后 refresh 401。
7. 非成员切换 403；目标租户停用或过期 403；用户停用 401。
8. 从目标租户再次切回首页租户成功；角色恢复为首页租户角色。
9. 租户内角色分配只影响本租户：在 B 调整该用户角色后，切回 A 的权限不变。
10. 审计可按 `userId/fromTenantId/toTenantId` 检索到切换记录。

### 15.11 分阶段实施

| 阶段 | 内容 | 交付 |
|---|---|---|
| 5.1 后端模型与切换 | `sys_user_tenant` 建表/迁移/回填、首页关系写入、**角色解析按租户过滤修复**、`/auth/tenants`、`/auth/tenant/switch`、refresh 按 `userId` 加载 + 关系校验、单测 | 同 `userId` 可切换的后端能力 |
| 5.2 admin 切换器 | 顶栏 Select、切换后重载、失败提示 | 一人多租户可用 |
| 5.3 成员与角色管理 | `/tenant/members` 列表 + 本租户角色分配、平台绑定/解绑关系、页面与权限码 | 运营可维护关系与租户内角色 |
| 5.4 回归与文档 | 双租户 E2E（含角色隔离）、隔离回归、更新本评估稿 | 验收闭环 |

### 15.12 Phase 5.1 执行计划（后端模型与切换）

**目标**：同一 `userId` 可在多个租户间切换；切换只换令牌 `tenantId` 与角色上下文，不换 `sub`；同时修复既有跨租户角色/权限泄漏缺口。

#### 交付文件

| 类别 | 文件 | 改动 |
|---|---|---|
| 文档 | `docs/plans/wave5.54-multi-tenant/assessment.md` | 本节执行计划 |
| 建库 | `whale-auth/src/main/resources/db/init.sql` | 新增 `sys_user_tenant` 建表与 admin 首页关系种子 |
| 迁移 | `whale-auth/src/main/resources/db/migration-5.54-tenant-membership.sql` | 幂等建表 + 首页关系回填（`NOT EXISTS`） |
| 实体 | `auth/entity/SysUserTenant.java`、`auth/mapper/SysUserTenantMapper.java` | 关系表 PO 与 Mapper |
| 领域端口 | `auth/domain/user/repository/UserTenantRepository.java` | 启用关系查询、关系存在性、首页关系幂等写入 |
| 仓储实现 | `auth/infrastructure/persistence/repository/UserTenantRepositoryImpl.java` | MyBatis-Plus 实现（关系表忽略租户行过滤，显式带 `user_id`） |
| 应用服务 | `auth/application/tenant/TenantMembershipAppService.java` | 列出可进入租户、切换关系校验（403 语义）、刷新关系校验（401 语义）、首页关系写入 |
| VO/DTO | `auth/vo/MyTenantVO.java`、`auth/dto/SwitchTenantRequest.java` | 租户列表项、切换入参（`tenantId` + 可选 `refreshToken`） |
| 认证 | `AuthService`、`AuthController` | `listTenants()`、`switchTenant()`；`refresh()` 改按 `userId` claim 加载并校验关系；新增 `GET /auth/tenants`、`POST /auth/tenant/switch` |
| 用户装配 | `UserDetailsServiceImpl`、`JwtAuthenticationFilter` | 新增 `loadUserById(userId, tenantId)`；过滤器与刷新统一按 `userId` 加载，避免令牌租户≠账号首页租户时认证失败 |
| 仓储修复 | `RoleRepositoryImpl` | `listRoleIdsByUserId` 加入 `sys_role.tenant_id + status=1 + deleted=0` 过滤；`listPermissionIdsByRoleIds` 增加租户内角色防御过滤 |
| 用户仓储 | `UserRepository` / `UserRepositoryImpl` | 新增 `findByIdAcrossTenants`（临时清空/恢复 `TenantContext`，用于首页账号加载）；`replaceRoles` / `listRoleIds` 只处理当前租户角色关联，避免跨租户误删 |
| 首页关系 | `UserAppService`、`TenantProvisioningAppService` | 新建账号（含租户初始管理员）后写首页关系 |
| 横切 | `TenantSqlProperties` | 忽略白名单加入 `sys_user_tenant` |
| 单测 | `whale-auth/src/test/java/...` | 关系校验、切换令牌、角色按租户隔离 |

#### 实施顺序

1. 落本节执行计划。
2. 数据库：`init.sql` 建表 + 种子；新增幂等迁移并回填，回填后缺失数必须为 0。
3. 模型与仓储：实体/Mapper/领域端口/实现/白名单。
4. 角色解析修复（先修复泄漏，再开放切换）。
5. 用户按 ID 跨租户加载 + JWT 过滤器改造。
6. 成员服务 + `AuthService` 切换/列表/刷新 + 控制器端点。
7. 新账号写首页关系。
8. 单测。
9. 人工代码检查 → 提交 → 推送 `origin/main`。

#### 验收命令

```bash
# 1) 建表与回填
mysql -u<user> -p bluewhale_auth < backend/whale-services/whale-auth/src/main/resources/db/migration-5.54-tenant-membership.sql
mysql -u<user> -p bluewhale_auth -e "SHOW CREATE TABLE sys_user_tenant"
mysql -u<user> -p bluewhale_auth -e "SELECT COUNT(*) FROM sys_user s LEFT JOIN sys_user_tenant m ON m.user_id = s.id WHERE m.id IS NULL"
# 期望：表存在；缺失关系数为 0

# 2) 构建与单测
mvn -pl backend/whale-services/whale-auth -am test

# 3) 接口：登录 → 租户列表 → 切换 → 旧令牌失效
curl -s -X POST http://localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"tenantCode":"default","username":"admin","password":"123456"}'
curl -s http://localhost:8080/auth/tenants -H "Authorization: Bearer <accessToken>"
curl -s -X POST http://localhost:8080/auth/tenant/switch -H 'Content-Type: application/json' \
  -H "Authorization: Bearer <accessToken>" \
  -d '{"tenantId":<targetTenantId>,"refreshToken":"<refreshToken>"}'
curl -s http://localhost:8080/auth/me -H "Authorization: Bearer <oldAccessToken>"   # 期望 401
curl -s http://localhost:8080/auth/me -H "Authorization: Bearer <newAccessToken>"   # 期望 tenantId=目标，角色/菜单来自目标租户
curl -s -X POST http://localhost:8080/auth/refresh -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<oldRefreshToken>"}'                                         # 期望 401
curl -s -X POST http://localhost:8080/auth/refresh -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<newRefreshToken>"}'                                         # 期望在目标租户成功
```

**已落地（2026-10-25）**：

- 建表与回填：`init.sql` 新增 `sys_user_tenant`（唯一 `(user_id, tenant_id)`）与 admin 首页关系种子；新增幂等脚本 `migration-5.54-tenant-membership.sql`（`CREATE TABLE IF NOT EXISTS` + `NOT EXISTS` 回填）。
- 模型与仓储：`SysUserTenant` / `SysUserTenantMapper` / `UserTenantRepository` / `UserTenantRepositoryImpl` / `TenantMembershipAppService`；`sys_user_tenant` 加入租户行过滤忽略白名单。
- 角色隔离修复：`RoleRepositoryImpl#listRoleIdsByUserId` 通过 `sys_role` 子查询收口租户 + 启用 + 未删除；`listPermissionIdsByRoleIds` 增加租户内角色防御过滤；`UserRepository#replaceRoles` / `listRoleIds` 只处理当前租户角色关联。
- 令牌链路：`UserDetailsServiceImpl#loadUserById`（按全局 `userId` 跨租户加载、按令牌租户解析角色）；`JwtAuthenticationFilter`、`AuthService#refresh`、切换流程统一按 `userId` claim，不再依赖用户名或账号首页租户。
- 切换接口：`GET /auth/tenants`、`POST /auth/tenant/switch`（出参同登录，旧 access/refresh 写黑名单，轮换开启时吊销 family，新会话新 familyId）。
- 首页关系写入：新建账号与租户开通的初始管理员均写入首页关系。
- 验证：`mvn -pl backend/whale-services/whale-auth -am test` → 330 个用例全部通过，Checkstyle 0 违规。

**已知限制（沿用 15.8）**：关系撤销后存量 accessToken 仍存活至多一个 access TTL；成员列表/租户内角色分配/平台绑定关系在 5.3 交付。

### 15.13 Phase 5.2 执行计划与记录（admin 切换器）

| 类别 | 内容 |
|---|---|
| 需求项 | ① 顶栏展示「我的租户」切换器，仅当可进入租户数 > 1 时出现；② 切换只调用 `POST /auth/tenant/switch`，成功后落盘新令牌与目标租户编码并全量重载；③ 失败保持当前会话并给出可读提示；④ 单租户用户不出现切换器 |
| 交付 | `frontend/admin/src/api/auth.ts`（`MyTenantVO` + `listMyTenants` + `switchTenant`）、`frontend/admin/src/layouts/AdminLayout.tsx`（`Select` + 切换处理 + 失败提示） |
| 验收 | ① `pnpm --filter whale-admin lint` 与 `build`（含 `tsc -b`）EXIT=0；② 单租户账号顶栏仅显示原租户标签；③ 双租户账号切换后页面重载、`/auth/me` 的 `tenantId`/角色/菜单均为目标租户；④ 切换失败（403/401/网络）保留当前令牌并提示 |
| 待做项 | 5.3 成员列表与租户内角色分配、平台绑定/解绑关系；5.4 双租户 E2E |

**实施记录（2026-10-25）**：

- `api/auth.ts` 新增 `listMyTenants()`（`GET /auth/tenants`）与 `switchTenant(tenantId, tenantCode)`（`POST /auth/tenant/switch`，携带当前 `refreshToken`；成功即 `saveLogin`，失败不落盘）。
- `AdminLayout` 挂载时拉取租户列表；数量 > 1 时以 `Select` 替换原静态租户标签，切换成功 `window.location.replace('/')` 全量重建权限/菜单/请求头；加载失败与非多租户场景保持原标签，不影响登录态。

### 15.14 Phase 5.3 执行计划（成员与角色管理，2026-10-25）

**目标**：让租户管理员维护「本租户成员与租户内角色」，并让平台租户维护「用户可进入哪些租户」；所有写操作按租户收口，不得触及其他租户的角色关联。

#### 交付文件

| 类别 | 文件 | 改动 |
|---|---|---|
| 文档 | 本评估稿 + `docs/requirements/register.md` | 本节执行计划；REQ-ARCH-006 / REQ-FUNC-016 状态 |
| 领域端口 | `UserTenantRepository` | 新增 `listEnabledUserIds(tenantId)`、`exists(userId, tenantId)`、`disable(userId, tenantId)` |
| 仓储实现 | `UserTenantRepositoryImpl` | 按租户列启用成员、关系存在性、软停用（`status=0`，不物理删除） |
| 应用服务 | `TenantMembershipAppService` | 成员列表、租户内角色分配、平台绑定/解绑关系；发布 `UserDomainEvent` 失效权限缓存 |
| 权限码 | `TenantProvisioningAppService` | 平台专属码集合化：`sys:tenant:list/add/edit/status/renew` 与 `sys:user:tenant` 不下发；成员码 165/166 正常下发 |
| VO/DTO | `TenantMemberVO`、`BindUserTenantRequest` | 成员视图（用户信息 + 本租户角色）与绑定入参 |
| 控制器 | `TenantController`、`UserController` | `GET /tenant/members`、`PUT /tenant/members/{userId}/roles`、`POST /user/{userId}/tenants`、`DELETE /user/{userId}/tenants/{tenantId}` |
| 数据库 | `init.sql`、`migration-5.54-tenant-membership.sql` | 权限 165–167；`sys_menu` 新增「租户成员」；为既有 `tenant_admin` 角色授权 165/166 |
| 前端 | `api/tenant.ts`、`pages/tenant/TenantMemberList.tsx`、`router/dynamicRoutes.tsx` | 成员列表 + 角色分配 + 绑定/解绑；组件契约键 `tenant/member` |
| 单测 | `whale-auth/src/test/java/...` | 成员列表、跨租户角色拒绝、首页关系禁删、绑定/解绑幂等 |

#### 接口与语义

| 方法 | 路径 | 权限 | 语义 |
|---|---|---|---|
| GET | `/tenant/members` | `sys:tenant:member:list`（165） | 当前租户启用关系成员 + 用户基本信息（按 ID 跨租户加载）+ 本租户角色 |
| PUT | `/tenant/members/{userId}/roles` | `sys:tenant:member:assign`（166） | 全量覆盖该用户在本租户的角色；非成员 404；角色必须属于本租户（否则 400） |
| POST | `/user/{userId}/tenants` | `sys:user:tenant`（167，仅平台租户） | 增加可进入租户关系；用户不存在 404、目标租户不可用 400、已存在幂等 |
| DELETE | `/user/{userId}/tenants/{tenantId}` | `sys:user:tenant`（167，仅平台租户） | 软停用关系；首页租户关系 400 禁删；关系不存在 404 |

- `sys_user_role` 仍无 `tenant_id`：租户归属由 `sys_role.tenant_id` 推导，`replaceRoles` 只清理/重建本租户角色行。
- 角色分配与关系变更发布 `UserDomainEvent`（`ROLES_ASSIGNED`），由既有监听器失效权限缓存。
- 审计：角色分配、绑定、解绑均 `@AuditLog`；不记录敏感参数。

#### 实施顺序

1. 落本节执行计划（文档先行）。
2. 领域端口 + 仓储实现 + 成员服务（列表/分配/绑定/解绑）。
3. 权限码与菜单迁移（`init.sql` + 幂等脚本，含既有 `tenant_admin` 授权）。
4. 控制器端点 + 审计注解。
5. 前端 API + 成员页 + 路由契约键。
6. 单测 → `whale-auth` 构建 + Checkstyle → admin `tsc/vite/lint`。
7. 检查报告 → 提交推送。

#### 验收

1. 成员列表只返回本租户启用关系；不出现其他租户成员。
2. 给成员分配本租户角色成功，切回另一租户时权限不变；传入其他租户角色 ID 被拒。
3. 非成员分配角色 404；平台租户为任意用户绑定/解绑关系成功且幂等。
4. 删除首页关系 400；解绑后该用户 refresh 401、`/auth/tenants` 不再返回该租户。
5. `whale-auth` 单测全绿 + Checkstyle 0；admin lint/build 通过。
