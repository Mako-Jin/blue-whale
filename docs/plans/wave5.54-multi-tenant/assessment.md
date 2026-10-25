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

### Phase 1：租户模型与登录闭环（P1）

1. `sys_tenant`：`id`、`tenant_code`、`tenant_name`、`type`（PLATFORM/TENANT）、`status`、`expire_time`、`quota_json`、审计字段。
2. `sys_user` 增加 `tenant_id`，唯一键改 `(tenant_id, username, deleted)`；`sys_dept`、`sys_role`、`sys_menu` 增加 `tenant_id`；角色码唯一改 `(tenant_id, role_code)`。
3. 权限点保持平台级（`sys_permission` 不加租户），菜单按租户复制模板。
4. 租户开通服务：创建租户 → 初始化管理员 → 复制默认角色/菜单 → 初始化部门根 → 记录开通审计；全程幂等（`tenant_code` 唯一 + 分布式锁）。
5. 租户管理 API：列表/详情/启用停用/配额；权限码 `sys:tenant:*`，仅平台租户可访问。
6. 停用租户：登录拒绝、令牌校验拒绝（网关查租户状态缓存）、异步任务跳过。

### Phase 2：横切能力（P1）

1. `whale-spring-boot-starter` 新增 `TenantContext` + `TenantContextFilter`（Servlet）+ `TenantContextHolder`；请求结束 `clear`。
2. MyBatis-Plus `TenantLineInnerInterceptor`：默认给所有表加 `tenant_id` 条件；白名单表（`sys_tenant`、`sys_i18n_message`、平台字典）显式忽略。
3. 传播：
   - 线程池装饰器（审计、异步索引）传递 `TenantContext`；
   - MQ 消息头携带 `tenantId`；
   - 定时任务按租户遍历（`sys_tenant` 启用列表）；
   - 内部 HTTP 客户端统一注入 `X-Tenant-Id`，删除 `whale.archive.integration.tenant-id` 固定配置。
4. 缓存与锁：Key 统一 `whale:{tenantId}:...`；权限缓存改 `whale:{tenantId}:cache:auth:user:perm:{userId}`。
5. 对象存储：`objectKey = tenants/{tenantId}/yyyy/MM/dd/<uuid>.<ext>`；下载/预签名校验租户。
6. 审计：`api_log`/`outbound_log`/`frontend_log`/`behavior_log` 均补 `tenant_id` 写入与索引。

### Phase 3：业务域铺开（P2）

按“风险 × 改动量”排序：

1. 表单元数据（`form_definition` 等 7 表）加 `tenant_id`，`(tenant_id, code)` 唯一；发布/设计器按租户。
2. 文件（`file_info`）加 `tenant_id`，列表/下载/配额按租户；内部变更流按租户过滤。
3. Agent 知识库（`agent_knowledge_file`、索引）加 `tenant_id`，索引按租户分区，检索强制过滤；文件同步按租户游标。
4. 档案（7 表）加 `tenant_id`；`FormArchiveClient` 透传请求租户。
5. IM（5 表）加 `tenant_id`；群成员校验限定同租户。
6. 工作流（4 表）加 `tenant_id`；流程定义按租户发布，任务查询加租户。
7. 任务/支付/告警（`job_*`、`pay_order`、`alert_*`）加 `tenant_id`。

### Phase 4：前端与运营（P2）

1. 登录页支持租户码（或按子域名自动解析）；登录返回租户信息。
2. 顶部租户切换器（一人多租户时）；切换后刷新令牌与菜单。
3. 租户管理页（平台租户可见）：开通、停用、配额、审计导出。
4. 请求层统一注入租户头（仅用于展示/兼容，服务端仍以 JWT 为准）。
5. E2E：租户 A/B 双账号并行，验证菜单、数据、文件、知识库互不可见。

---

## 七、关键决策（需确认）

| 编号 | 决策 | 选项 | 建议 |
|---|---|---|---|
| D1 | 隔离模式 | A 共享库行级 / B schema / C 独立库 | A |
| D2 | 租户入口 | 登录填租户码 / 子域名 / 独立登录域 | 登录填租户码（先做），子域名后置 |
| D3 | 一人多租户 | 支持 / 不支持 | 支持，令牌携带“当前租户”，切换即换令牌 |
| D4 | 平台超管读业务数据 | 默认禁止 / 二次授权 | 默认禁止，二次授权 + 审计 |
| D5 | 默认租户回落 | 拒绝 / 回落 1 | **拒绝**（P0 止血口径） |
| D6 | 本期范围 | 仅 P0 / P0+P1 / 全阶段 | 仅 P0 + P1 登录闭环 |

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
| 待做项 | ① Phase 1 租户管理 API、角色/菜单/部门租户化、租户开通与停用；② Phase 2 `TenantContext` + MyBatis 租户插件 + 异步/MQ/服务间传播 + 缓存/锁/存储前缀；③ Phase 3 表单元数据、文件、IM、工作流、Agent、档案、任务、支付、告警铺开；④ Phase 4 租户切换与运营台；⑤ 在联调环境执行 `migration-5.54-tenant.sql` 并回归登录/表单链路；⑥ 前端构建验证：本机执行 node/pnpm 时命令沙箱后端异常（`SandboxBackendFailed`），本次仅完成人工代码审查，需在正常环境补跑 `pnpm build` |
| 已修正问题项 | ① 旧令牌无租户 claim 的越权兜底：改为 401，不回落默认租户；② 白名单路径伪造租户头：同样清除；③ Mockito 严格桩在“租户解析前失败”用例误报：改用 lenient；④ 新增领域类缺少方法 Javadoc：按 checkstyle 补齐；⑤ `JwtUtilsTest` 篡改令牌用例偶发失败：改为确定性篡改签名段首字符 |
