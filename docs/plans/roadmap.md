# 蓝鲸系统 - 搭建填充路线图

> 所有阶段性规划保存在本目录，完成一项在 `docs/devlog/` 中记录一项。

## Phase 1：基础设施就绪

> **环境受限待办（本机无 Docker）**：1.1–1.4 依赖 Docker Compose / Nginx 容器，未在本机验证，转入后续具备 Docker 的环境补齐（Wave 5.33 已按用户口径跳过）。

- [ ] 1.1 配置环境变量 — 复制 `.env.example` → `infrastructure/docker/.env`，填入 MySQL/Redis/Nacos 密码
- [ ] 1.2 启动 Docker Compose — `cd infrastructure/docker && docker compose up -d`
- [ ] 1.3 验证中间件 — Nacos (http://localhost:8848/nacos)、MySQL 11 个业务库自动建库、Redis 连通
- [ ] 1.4 验证 Nginx 配置 — 静态资源 / API 网关 / 微前端三套 conf.d 联调

## Phase 2：后端核心服务填充（按优先级）

### 2.1 whale-auth 权限中心（最高优先级）

- [x] ~~数据库表设计：sys_user / sys_role / sys_permission / sys_menu / sys_dept / sys_user_role / sys_role_permission（+sys_user_permission ABAC 属性表）~~
- [x] ~~RBAC + ABAC 六层级模型：系统 → 菜单 → 接口 → 按钮 → 数据 → 属性~~
- [x] ~~JWT 登录 / 刷新 / 注销接口~~
- [x] ~~Spring Security + `@RequirePermission` 注解拦截~~
- [x] ~~接口权限注解全覆盖（31 处 `@RequirePermission`，3 → 31）+ 22 条权限点种子 + 2 菜单项（Wave 5.2，AUTH-04）~~
- [x] ~~当前用户菜单/权限接口 `GET /auth/me`（roles/permissions/menus，Wave 5.2，AUTH-21）~~
- [x] ~~对接 admin 前端登录（Wave 5.17，2026-10-19：登录/登出经网关联调通过，登出后旧 accessToken 实测 HTTP 401，黑名单端到端达成）~~

### 2.2 whale-gateway 网关

- [x] ~~Nacos 服务发现动态路由~~（**Wave 5.21 升级**：`spring.config.import=NacosRouteDefinitionRepository` 从 Nacos 配置中心动态加载路由，变更可热刷新）
- [x] ~~JWT 全局鉴权过滤器~~
- [x] ~~网关限流（Redis 计数器 + 429 `Result` 体，Wave 5.6，T5.4；Sentinel 接入转 backlog）~~
- [x] ~~网关熔断（Wave 5.21，T5.25：自研轻量熔断 `CircuitBreakerGlobalFilter`〔滑动窗口失败率 + 超时 + 半开探测〕，503 `Result` 体降级；**未引入 resilience4j**，Sentinel 接入转 backlog）~~
- [x] ~~内网接口屏蔽（Wave 5.21，T5.25：`InternalApiDenyGlobalFilter` 对 `/file/internal/**` 返回 403）~~
- [x] ~~请求日志 / TraceId 链路追踪~~
- [x] ~~网关治理（Wave 5.35：**G3** `RouteDefinitionValidator` 白名单校验 + 非法配置整体拒绝 + current/previous 双快照 + `gateway.routes.rejected{dataId}` + `GET/POST /actuator/routeGovernance` 回滚，**回滚固定标记**避免被 Nacos 刷新覆盖；**G2** resilience4j 2.3.0 替换自研熔断，保留 `apply-to-routes` / 503 `Result` / `gateway.circuit.*`；**G1** Sentinel 评估结案（不引入）。网关单测 **76/76**，运行时治理冒烟 `GW-GOVERNANCE-PASS`；报告 [reviews/2026-10-21-wave5.35-workflow-gateway.md](../reviews/2026-10-21-wave5.35-workflow-gateway.md)、[Sentinel 评估](../reviews/2026-10-21-wave5.35-sentinel-evaluation.md)）~~

### 2.3 whale-common / whale-tools 公共组件

- [x] ~~统一返回体 `Result<T>` / `PageResult<T>`~~
- [x] ~~全局异常处理 `GlobalExceptionHandler`~~
- [x] ~~MyBatis-Plus 分页插件、字段自动填充~~

### 2.4 whale-i18n-sdk 国际化

- [x] ~~消息表 `sys_i18n_message` 设计~~
- [x] ~~三级优先级实现：数据库 > 属性文件 > 默认值~~
- [x] ~~Redis 缓存（+SPI 数据源实现，14 个单测全绿）~~
- [x] ~~消息 CRUD 接口~~（Wave 5.33 ①：新建服务 `whale-i18n`（8087，`/i18n/**`，`bluewhale_i18n`），复用 SDK 实体/Mapper/缓存清理；`/i18n/message` 5 端点 + 权限种子 id 60–63 + 网关路由；9 单测 + 全量回归 271 测试全绿；直连冒烟 10/10 + 网关冒烟 3/3 PASS）

### 2.5 其余服务骨架填充

- [x] ~~whale-job（定时任务）骨架填充~~（Wave 5.19，T5.24：依赖/配置 + `JobInfo`/`JobLog` + `JobHandler` SPI + `JobScheduler`（`ThreadPoolTaskScheduler`+`CronTrigger`）+ `/job/**` REST + 6 权限点种子 + `bluewhale_job` 建库；8082 经网关冒烟 `code=0`）
- [x] ~~whale-job 服务「继续完成」~~（Wave 5.20：调度健壮性〔实例内互斥 `JobExecutionGuard` + Redisson 分布式锁 `lock:job:execute:{jobId}` + `misfire_policy` 落地 + `JobHandlerRegistry` 存在性校验〕+ 可观测〔`JobInfoVO.nextFireTime`、`runOnce` 异步〕+ 日志清理〔`JobLogCleanupHandler` + `whale.job.log-retention-days` + 内置种子任务〕+ 单测基线 42 用例；29 模块构建 SUCCESS，8082 冒烟含分布式锁路径全绿；**归档导出至对象存储仍阻塞于 whale-file**）
- [x] ~~whale-file（文件）最小可用~~（Wave 5.21，T5.25：存储抽象 `FileStorage`+`LocalFileStorage` + `POST /file/upload`、`POST /file/internal/upload`、`GET /file/{id}/download`、`GET /file/page`、`DELETE /file/{id}` + `sys:file:*` 4 权限点种子 + 8 单测；**未接对象存储**，落地本地文件系统）
- [x] ~~whale-job 归档导出~~（Wave 5.21，T5.25：`JobLogArchiveHandler` + `jobLogArchiveClient` 导出 CSV 上传 whale-file 成功后再逻辑删除，内置种子任务 id=2；失败不删语义）
- [x] ~~whale-audit 导出与归档~~（Wave 5.21，T5.25：`POST /audit/log/export`〔`EXPORT` 元审计〕+ `AuditLogArchiver` 归档；**不删除 `audit_log`**，append-only 不变）
- [x] ~~whale-form（表单）最小可用~~（Wave 5.23：`bluewhale_form` 建库 + `form_definition` 表〔JSON schema ≤64KB〕+ `/form/definition` 5 接口〔新增/更新/删除/详情/分页，`sys:form:add/edit/delete/list` 权限点种子 id 36–39〕+ 8 单测；8084 经网关 `/form/**` 增删改查分页冒烟 `code=0`〔重复 code 400 / 删后 404〕；**仅「定义」侧**，表单数据 `form_data` 与前端设计器未做）
- [x] ~~whale-form 全闭环（表单数据）~~（Wave 5.26：`form_data` 表 + `/form/data`〔提交/详情/分页/逻辑删除〕4 接口 + `sys:form:data:*` 权限点种子 id 49–51 + 8 单测；**闭环**：提交（校验定义存在 + 数据 JSON ≤64KB，回填定义冗余 code/version）→ 分页/详情可见 → 逻辑删除后详情 404；8084 经网关 `/form/**` 9 步冒烟全绿〔定义不存在 404 / 数据为空 400〕；表单↔工作流联动、前端填报页未做）
- [x] ~~whale-workflow（工作流）最小可用~~（Wave 5.24：`bluewhale_workflow` 建库 + `process_definition` 表〔流程 JSON 定义 ≤64KB〕+ `/workflow/definition` 5 接口〔新增/更新/删除/详情/分页，`sys:workflow:add/edit/delete/list` 权限点种子 id 40–43〕+ 8 单测；**修复骨架端口自引用 `${server.port:8083}` → `${SERVER_PORT:8083}`**；8083 经网关 `/workflow/**` 冒烟 `code=0`〔重复 code 400 / 删后 404〕；**仅「定义」侧**，流程实例/待办任务/审批动作未做）
- [x] ~~whale-workflow 全闭环（流程实例 + 待办任务 + 审批动作）~~（Wave 5.25：`process_instance`/`process_task` 两表 + `/workflow/instance`〔发起/详情含 tasks/分页/撤销〕、`/workflow/task`〔分页/同意推进/驳回〕7 接口 + `sys:workflow:instance:*`、`sys:workflow:task:*` 权限点种子 id 44–48 + 14 单测；**状态流转**：发起 current_index=0 生成首节点待办 → 同意推进（末节点同意 → 实例「已通过」）→ 驳回实例「已驳回」/撤销实例「已撤销」并取消其余待办；8083 经网关 `/workflow/**` 14 步全闭环冒烟全绿〔重复处理 400 / 实例不存在 404〕；顺序单待办模型，会签/并行/转办等未做）
- [x] ~~whale-form 动态表引擎（主从分离 · 版本化物理表 · 多租户）~~（Wave 5.27：解答「单表 JSON 会炸」的架构质疑 —— 选定**主从分离 + 版本化物理表**。`form_table_registry` 登记「定义 code+version ↔ 物理表」+ schema 快照；`POST /form/definition/{id}/publish`（`sys:form:publish` id 52）解析 schema **动态建物理表** `form_data_{code}_v{version}`（动态字段=真实列：text/textarea/number/date/datetime/select/bool 类型映射）；`form_data` 加行级 `tenant_id`（`X-Tenant-Id` 头，缺省 1）；提交/详情/删除走**主表+物理表双写/双删**，`data_json` 保留过渡；注入防护三件套（表名白名单 + 列名白名单 + 值参数化）；36 单测全绿 + Checkstyle 0 违规；8084 经网关 `/form/**` 14 步冒烟（发布建表→提交→详情 fields→分页→负向→双删→删后 404）全绿；**前端设计器/动态渲染、字段级索引自动优化、schema 在线迁移未做**）
- [x] ~~whale-form 动态表单元数据模型（三层分离 · 表:表单 1:N）~~（Wave 5.28：解答「用户要的是『列详情表 + 表单属性详情表』」的模型优化 —— 拆为**三层模型**：① 表定义层 `form_table_definition` 1—N `form_column_meta`（列编码/存储类型/长度/精度/可空/必填/默认值/备注/排序）；② 表单视图层 `form_definition`〔+`table_definition_id`〕1—N `form_field_config`（显示名/控件类型/列表与表单显示/可编辑/排序/占位/默认值/校验/关联列，`column_meta_id=NULL` 为纯布局字段）；③ 选项层 `form_field_config` 1—N `form_field_option`。关键修正：**存储类型 `data_type`（DDL）与控件类型 `widget_type`（前端）解耦**（`VARCHAR` 列可分别渲染 text/select/radio/checkbox），**选项独立成表**；`schema_json` 保留为**发布快照**；**表:表单 = 1:N**（一张物理表被多张表单视图共享，可见性/可编辑性/控件各异）。新增 `/form/table-definition` CRUD + publish + tables（`sys:form:table:*` id 53–57）、`migrateLegacy()` 幂等迁移（`schema_json` → 元数据）；新增非破坏性增量脚本 `db/migration-5.28.sql`；**迁移同构性**（`table_code==form.code`、`table_version==form.version` → 物理表名与 5.27 完全一致，存量零改动）；44 单测全绿 + Checkstyle 0 违规；8084 经网关 `/form/**` 冒烟（表定义→发布→**双视图 1:N**→提交→详情→负向）全绿；**前端设计器/动态渲染、列删除/改类型在线迁移未做**）
- [x] ~~Wave 5.29 拖拽式表单设计器（前后端贯通）：后端 whale-form 最小补强（`form_field_config.col_span` 栅格宽度 + `FormFieldConfigVO` 回填 `columnCode/dataType/required/maxLength`，`FormDefinitionServiceImpl.enrichColumnInfo` 一次查列建 Map 无 N+1）+ 新建前端独立应用 `frontend/platform-apps/whale-form-designer`（端口 8002，真实认证直连网关 8080，非 qiankun 子应用）：`api/form.ts` 冻结契约 + 表定义管理页（含列编辑器/发布）+ 表单视图列表页 + `@dnd-kit` 三栏拖拽设计器（Palette/Canvas/PropertyPanel + 保存/发布/预览/回填）+ `FormRenderer`/`FormRender` 动态渲染填报 + 数据列表页；端到端闭环 `建表→发布→拖拽设计→保存→发布→填报→数据列表`~~（2026-10-20 完成：后端 `form_field_config.col_span` + `FormFieldConfigVO` 回填 `columnCode/dataType/required/maxLength`（`enrichColumnInfo` 一次查列建 Map 无 N+1）；前端独立应用 `whale-form-designer`（8002，真实认证直连网关 8080）五路由贯通；API 级 7 步正向 + 2 条负向全绿、浏览器实机 E2E 全绿；报告 [reviews/2026-10-20-wave5.29-form-designer.md](../reviews/2026-10-20-wave5.29-form-designer.md)）；规格：[wave5.29-form-designer/spec.md](wave5.29-form-designer/spec.md)
- [x] ~~Wave 5.30 表单剩余任务 · 问题修复 · 收尾：补齐 Wave 5.29 遗留三项 —— ①后端 whale-form 列变更**在线迁移**（`FormDataEngine.describeColumns` 描述物理列 + `FormTableDdlGenerator` 增 `columnDef/alterAdd/alterModify/alterDrop/normalizeType/reservedColumns`；`FormPublishServiceImpl.ensurePublished` 统一「无表→CREATE，有表→diff+ALTER」，`publishTableDefinition`/`publishMetadata` 同走该路；DROP 前护栏：元数据仍在（含 `status=0` 停用列）不下发 DROP，被表单视图引用列 `PARAM_ERROR` 前置拦截）；**P1 修复**：`FormTableDefinitionServiceImpl.update` 由「整体逻辑删列+重建」改 `mergeColumns` 按 `columnCode` 就地合并**保列主键**，消除既有表单视图 `column_meta_id` 悬空；②数据导出 `GET /form/data/export`（`format=csv|xlsx`，纯 JDK UTF-8 BOM CSV + 最小 OpenXML XLSX，`EXPORT_MAX_ROWS=5000`，复用 `sys:form:data:list`，**不新增 Maven 依赖**；前端 `api/http.ts downloadBlob` + 数据列表「导出」下拉）；③设计器**撤销/重做**（`past/future` 历史栈 + `commit()` 统一入口 + 按钮 + `Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z`，输入控件内不劫持）~~（2026-10-20 完成：后端 8 改 + 2 新 main + 3 测试、前端 5 改；`mvn -o -f backend/pom.xml clean install` 28/28 模块 **BUILD SUCCESS**（Checkstyle `severity=error` + 全量单测）、`pnpm … whale-form-designer build` 通过（1461 modules）；API 级 E2E E1–E4/E7 全 PASS（编辑只改元数据、发布 ADD/MODIFY/DROP、导出 CSV/XLSX 可打开、负向护栏）＋浏览器实机 E5 撤销/重做 PASS；遗留 E6 真实指针拖拽入画布自动化不可复现，**需人工复核**（本轮未改 DnD 代码，判定无回归）；报告 [reviews/2026-10-20-wave5.30-form-residual.md](../reviews/2026-10-20-wave5.30-form-residual.md)）；规格：[wave5.30-form-residual/spec.md](wave5.30-form-residual/spec.md)
- [x] ~~Wave 5.31 whale-workflow 引擎升级（状态机 + BPMN 2.0 双向 + JSON 事实源）：扩展现有 `whale-workflow`（不新建模块、不推倒重来），**本轮仅后端** —— ① **通用状态机内核** `support/machine/`（`StateMachine.pickFlow` 统一选路 + `Guard` SPI〔`AlwaysGuard`/`DefaultFlowGuard`，条件本轮不参与选路〕+ 网关透传 + `MAX_GATEWAY_HOPS=16`），`ProcessInstanceServiceImpl#start` / `ProcessTaskServiceImpl#approve` 改由内核驱动（新增 `current_node_key` 定位，为空回落 `currentIndex`）；② **规范化 JSON 为唯一事实源** `{type,nodes[],flows[]}`（`ProcessModelParser` 模式判定：`stateMachine`/legacy/无 type 有 flows；`ProcessModelValidator` 22 错误码 + `CONDITION_IGNORED` 警告；上限 200 节点/400 流转），**向后兼容**旧 `{"nodes":[…]}`（legacy 归一化委托既有 `ProcessNodeParser`，文案逐字节保留）；③ **BPMN 2.0 轻量自研双向**（`BpmnXmlSupport` JDK DOM 加固〔禁 DOCTYPE/外部实体/XInclude + `FEATURE_SECURE_PROCESSING` + 空 `EntityResolver` + 体积卡口 256KB，**不引入 Flowable/Camunda**〕、`BpmnXmlParser` XML→模型〔任意前缀 `assignee`、网关 `default`→`isDefault`、不支持元素点名报错〕、`BpmnXmlWriter` 模型→XML）；④ **3 个新端点** `/workflow/definition/validate`（权限种子 58）、`/workflow/definition/bpmn`（59）、`GET /workflow/definition/{id}/bpmn`（复用 `sys:workflow:list`）；⑤ 库表 `process_instance.current_node_key VARCHAR(64) NULL` + `db/migration-5.31.sql`；**验证**：模块自检 102 用例全绿 + Checkstyle 0 违规；全量回归 `mvn -o clean install -T 1C` **28/28 模块 BUILD SUCCESS**；运行时冒烟（经网关 8080）**28/28 PASS**（G2 legacy `n1→n2→已通过`｜G3 校验 18/18〔LEGACY + `CONDITION_IGNORED` + 16 负向码含 2 个 XML〕｜G4 BPMN 导入→导出→再校验往返｜G5 `current_node_key=NULL` 回落同意成功 + 信封 404/400/401/403）；报告 [reviews/2026-10-20-wave5.31-workflow-engine.md](../reviews/2026-10-20-wave5.31-workflow-engine.md)；规格：[wave5.31-workflow-engine/spec.md](wave5.31-workflow-engine/spec.md)；**前端设计器/BPMN 可视化、条件表达式求值、并行网关/会签未做**~~
- [x] ~~Wave 5.32 whale-workflow-designer 工作流前端（管理 + 绘制 + 表单绑定）：新建独立前端应用 `frontend/platform-apps/whale-workflow-designer`（端口 8003，proxy 正则 `^/(auth|workflow|form)(/|$)`，storage 前缀 `whale_wf_`，`Id = string` 全链路禁 `Number(id)`）覆盖**四域** —— ① **流程定义管理**（分页/新建/编辑/删除/校验/跳设计器/发起入口/BPMN 导入导出）；② **自由画布设计器**（SVG 自绘 + 复用 `@dnd-kit`，不引入 React Flow：Palette 4 类节点拖入、节点拖动〔`PointerSensor` `activationConstraint:{distance:4}` 区分点击选中/拖拽移动 + 10px 网格吸附〕、连线选中、属性面板 `key`/`name`/`type`/`assignee`/`formId`+`formCode`、`FormPicker` 拉 `GET /form/definition/page` **选表单管理器的表单**、画布 ↔ 规范化 JSON、`/validate` 按 `refKey` 高亮、BPMN 导入导出）；③ **流程发起 + 填表单**（入口 `USER_TASK` 节点 `formId` → `<FormRenderer>` 填报 → `POST /form/data` 取数据 ID → `POST /workflow/instance`，`businessKey` = 数据 ID）；④ **我的待办 + 审批 + 实例监控**（审批抽屉经实例 `businessKey` → `GET /form/data/{id}` → `<FormRenderer disabled>` **只读回显**，同意/驳回；实例详情抽屉含 `currentNodeKey`/`status`/轨迹）。**后端最小可加性补强**（Wave 5.31 事实源上加字段，legacy 零回归）：节点 `formId`/`formCode`/`x`/`y` 显式入 JSON 往返、`USER_TASK_FORM_CODE_INVALID`/`USER_TASK_FORM_INCOMPLETE` 两校验码、BPMN 任意前缀 `formKey`/`formId` 对称往返（坐标不进 XML）、`process_task` 加 `node_key`/`form_id`/`form_code`（幂等 `db/migration-5.32.sql`）、`FormRenderer` 抽进 `elemental/whale-ui` 供 form-designer 与 workflow-designer 共用；**验证**：模块自检 **120 用例全绿**（5.31 基线 102 + 18）+ Checkstyle 0 违规；全量回归 `mvn -o clean install -T 1C` **28/28 模块 BUILD SUCCESS**；前端 `pnpm build` **14/14 任务**；DDL 落库（`process_task` 三列 `varchar(64) YES`）；运行时冒烟 **15/15 PASS**（新字段往返、两新码、待办三列、BPMN `formKey` 往返、信封 401/404）；浏览器实机 E2E **A–G 全链路 PASS**（登录→定义列表→设计器 3 节点 2 连线→节点属性含绑定表单→连线属性→校验 `nodeCount=3 flowCount=2 mode=NORMALIZED`→保存持久化→发起填表单「流程发起成功」→待办只读回显→同意→实例「已通过」轨迹正确）；**遗留**：C4 拖拽手势自动化不可复现（dnd-kit 拖拽源接线已确认正确，`role="button"`+`aria-roledescription="draggable"`，需人工复核）；审批表单仅只读回显；条件表达式仍未求值）；报告 [reviews/2026-10-20-wave5.32-workflow-designer.md](../reviews/2026-10-20-wave5.32-workflow-designer.md)；规格：[wave5.32-workflow-designer/spec.md](wave5.32-workflow-designer/spec.md)~~
- [x] ~~Wave 5.35 工作流能力补全（条件表达式 / 并行会签 / 转办委派 / 回退任意节点 / 审批改表单）：W1 `ConditionGuard` SpEL 受限求值 + 实例 `variables_json`；W2 `PARALLEL_GATEWAY`/`COUNTER_SIGN`（ALL/ANY/RATIO）+ `active_node_keys` fork/join；W3 `transfer`/`delegate`/`resolve`；W4 `rollback(targetNodeKey)`；W5 `PUT /form/data/{id}` + 前端审批抽屉可编辑保存；DDL 7 列 + 权限种子 74–77；workflow **120** / form **63** 单测；运行时 `SMOKE-ALL-PASS`；报告 [reviews/2026-10-21-wave5.35-workflow-gateway.md](../reviews/2026-10-21-wave5.35-workflow-gateway.md)~~
- [x] ~~Wave 5.36 工作流剩余任务 ①：加签（ADD_SIGN）~~（`POST /workflow/task/{id}/add-sign` + 权限种子 78 + `migration-5.36.sql`；复用 `sign_type`/`owner_id` 无 DDL；同节点追加待办全部完成后才推进，ANY/RATIO 会签拒绝加签；前端审批抽屉「加签」弹窗；workflow **124/124** 单测 + Checkstyle 0 违规 + 前端构建通过 + 运行时 `SMOKE-ALL-PASS`；剩余项见 [whale-workflow-backlog.md](whale-workflow-backlog.md)）
- [x] ~~Wave 5.37 工作流剩余任务 ②：设计器多分支真实选路模拟~~（`POST /workflow/definition/simulate`，复用 `sys:workflow:definition:validate` 权限；`StateMachine.pickTransition` + `ConditionGuard` 真实求值，返回逐步路径与入边条件/默认流标记，不建实例不落库；并行网关分支合并、节点重入跳过、128 步上限；workflow **129/129** 单测 + Checkstyle 0 违规 + 前端「模拟」弹窗构建通过 + `wave5.37-simulate.ps1` **SMOKE-ALL-PASS**）
- [x] ~~Wave 5.38 工作流剩余任务 ③：定义版本快照 / 发布 / 实例迁移~~（新表 `process_definition_version`（快照 + 发布态）+ 幂等 `migration-5.37.sql`；`GET /definition/{id}/versions` / `POST .../publish` / `POST .../migrate` 复用 list/edit 权限；定义 JSON 变更版本自增写草稿、发布切换新实例版本、运行中实例按节点键迁移；发起优先已发布快照，审批/回退按实例锁定版本解析；workflow **138/138** 单测 + Checkstyle 0 违规 + `wave5.38-version.ps1` **SMOKE-ALL-PASS**）
- [x] ~~Wave 5.39 工作流剩余任务 ④：BPMN-DI 坐标回写 / 图形保真~~（导出：节点 `x/y` 非空时追加 `bpmndi:BPMNDiagram` + `BPMNPlane` + 每节点 `BPMNShape/dc:Bounds`（事件 36、网关 40、审批 120×60），无坐标不输出图形；导入：`BPMNShape.bpmnElement` + `dc:Bounds` 回读 `x/y`，缺失/未知节点/非数字静默跳过；`x/y` 经 `ProcessModelParser`/`toJson` 全链路往返；workflow **142/142** 单测 + Checkstyle 0 违规 + `wave5.39-bpmndi.ps1` **SMOKE-ALL-PASS**）
- [x] ~~Wave 5.40 工作流剩余任务 ⑤：画布高级能力（撤销重做 / 缩放平移 / 对齐吸附）~~（纯前端 `whale-workflow-designer`：`useReducer` 历史栈 `{model,past,future}`（上限 50）+ `commit()` 统一入口 + 顶栏撤销/重做与 `Ctrl+Z` / `Ctrl+Y` / `Ctrl+Shift+Z`（输入控件内不劫持）；缩放 0.4x~2x（工具栏「−/百分比/+/适应/重置」+ `Ctrl+滚轮` 指针定位）、空格/中键平移（`setPointerCapture`）；6px 阈值十向对齐 + 虚线参考线，命中轴按对齐值、未命中轴 10px 网格吸附；拖拽位移与 Palette 落点按 zoom 换算；`pnpm build` 通过 + 浏览器实机验证缩放/撤销重做；分组/泳道拆为 backlog 5b）
- [x] ~~whale-pay（支付）~~（Wave 5.33 ⑨：建库 `bluewhale_pay` + `pay_order` 表 + `/pay/order`〔page/详情/create/`{id}/pay`/`{id}/cancel〕5 接口 + `sys:pay:*` 权限种子 id 70–73；状态单向 `CREATED→PAID/CANCELLED`；9 单测 + Checkstyle 0 违规）
- [x] ~~whale-audit（审计日志，**四通道查询 + 元审计 + 出站/接口日志**已落地，Wave 5.19 建 `bluewhale_audit` 库并运行时验证）、whale-monitor（监控）、whale-alert（告警）~~（Wave 5.33 ③④⑤：monitor 补 `GET /monitor/service/list` + `/monitor/service/{name}/instances`〔`DiscoveryClient` 服务与实例健康聚合，`sys:monitor:list` id 64〕；alert 建库 `bluewhale_alert` + `alert_rule`/`alert_record` + `/alert/rule`〔page/详情/create/update/delete/启停〕+ `/alert/record/page` + 权限种子 id 65–69 + 网关 `/alert/**` 路由；audit **仅文档勾选同步**，**MQ 汇聚 / 异步派发标注「环境受限（无 RabbitMQ）backlog」**）

### 2.6 工程规范与分布式基础（2026-10-17 新增）

**工程规范（REQ-DOC-001/002、REQ-QA-001/002）**

- [x] ~~需求登记册：REQ 编号体系 + 变更控制七步流程 + 四条门禁规则（[register.md](../requirements/register.md)）~~
- [x] ~~代码检查清单：协议安全 12 / 代码漏洞 16 / 复杂度 9 / 魔法值 6 / 规范 14 项（[code-review-checklist.md](../checklists/code-review-checklist.md)）~~
- [x] ~~检查报告机制：每次变更产出 `docs/reviews/` 报告（首份已归档）~~
- [x] ~~CI/CD 代码质量门禁：Checkstyle 规则集 + `code-quality.yml`（报告模式）~~

**专项设计定稿（REQ-SEC-001 / REQ-DIST-001~003 / REQ-ARCH-001 / REQ-CICD-001）**

- [x] ~~审计日志方案（[audit-log.md](../design/audit-log.md)）~~
- [x] ~~分布式 ID / 分布式锁 / 其他分布式问题（[distributed.md](../design/distributed.md)）~~
- [x] ~~权限服务 DDD 架构与迁移路线（[ddd-whale-auth.md](../design/ddd-whale-auth.md)）~~
- [x] ~~CI/CD 流水线方案（[cicd.md](../design/cicd.md)）~~

**分布式能力落地（REQ-SEC-002 / REQ-DIST-002）**

- [x] ~~分布式令牌黑名单：登出后 accessToken 立即失效（Redis + SHA-256 摘要键 + 降级）~~
- [x] ~~分布式锁工具（`@DistributedLock` + SpEL + Redisson）~~
- [x] ~~审计日志落地（`@AuditLog` + AOP 异步写库 + 敏感字段脱敏）~~
- [x] ~~refreshToken 失效（`jti` + 黑名单）、网关侧黑名单校验（REQ-SEC-003/004）~~
- [x] ~~分布式 ID 统一与 Long→String 序列化~~
- [x] ~~幂等组件 `@Idempotent`（Redisson SETNX + 结果缓存，Wave 5.6，T5.3）~~
- [x] ~~缓存一致性工具（Cache-Aside + 空值哨兵 + TTL 抖动 + 延迟双删，Wave 5.6，T5.5）~~
- [x] ~~Gitee Go 流水线映射、JaCoCo 覆盖率门禁（Wave 5.6，T5.16/T5.18；Gitee 侧实机触发待验证）~~

## Phase 3：前端平台填充

### 3.1 whale-platform 基座

- [x] ~~登录页 + 路由守卫~~
- [x] ~~MainLayout（Header / Sidebar / 多标签页）~~
- [x] ~~子应用错误兜底（qiankun 加载动画待做）~~
- [x] ~~主题切换（@bluewhale/whale-common-theme，亮/暗）~~

### 3.2 whale-ui 组件库

- [x] ~~基于 antd 二次封装：ProTable / FormModal / SearchForm~~（Wave 5.18，另附 `PermissionProvider` / `AuthButton` 按钮级权限组件）
- [x] ~~主题 CSS 变量注入~~（Wave 5.33 ②：核对确认 `whale-common-theme` 已是权威主题层 → **复用 + 补齐**路线，不造第二套 `--whale-*`；whale-ui 新建 `src/theme/`：`buildThemeVars` 纯函数 + `applyThemeVars`（SSR 安全 + 幂等）覆盖 32 个 `--bw-*` 变量，并统一 re-export `tokens`/`antdThemeConfig`/`getAntdTheme`；`whale-ui build` ESM+CJS+dts 成功）

### 3.3 admin 后台管理

- [x] ~~登录页 → 用户 / 角色 / 菜单管理~~（登录 Wave 5.17 + 三管理页 Wave 5.18）
- [x] ~~对接 whale-auth API~~（Wave 5.18：user/role/menu/dept/permission 五模块客户端，经网关）
- [x] ~~动态路由 + 按钮级权限渲染~~（**均已落地**：按钮级权限渲染 Wave 5.18 `PermissionProvider` + `AuthButton`；**动态路由 Wave 5.22** `frontend/admin` 用 `GET /auth/me` 的 `menus[]` 驱动侧栏与路由，静态兜底保留）
- [x] ~~admin 部门 / 权限 / 定时任务 / 文件四页~~（Wave 5.34：新增 `pages/{dept,permission,job,file}` + `api/{dept,permission,job,file}`；`COMPONENT_MAP` 增 4 契约键，vite proxy 覆盖 `/job`、`/file`；`sys_menu` 补 id 7/8 + 幂等 `migration-5.34.sql`；浏览器实测四页数据加载正常、无「页面未配置」；报告 [reviews/2026-10-21-wave5.34-module-closure.md](../reviews/2026-10-21-wave5.34-module-closure.md)）
- [x] ~~端口占位自引用缺陷（5 处）~~（Wave 5.34：`whale-apps` erp/mes/oa/pdm + `whale-config` 的 `${server.port:…}` → `${SERVER_PORT:…}`，消除带 actuator 服务的 `Circular placeholder reference`）

### 3.4 whale-portal 门户

- [x] ~~工作台 / 待办 / 消息通知~~（Wave 5.33 ⑦：`whale-portal` 工作台 4 张统计卡片 + `GET /workflow/task/page` 我的待办表 + 消息通知占位页 + Layout/菜单/路由）
- [x] ~~ERP / OA / MES 入口卡片~~（Wave 5.33 ⑦：三张入口卡片跳 `/erp` `/oa` `/mes`；`pnpm --filter whale-portal build` 通过）

## Phase 4：业务应用与运维

- [x] ~~whale-erp / whale-mes / whale-pdm / whale-oa 各自列表页 + 表单页骨架~~（Wave 5.33 ⑧：erp(8102)/oa(8103) 补列表页 + 表单页 + API 层；**新建** mes(8105)/pdm(8106) 子应用并注册进基座 `whale-platform/src/qiankun/apps.ts`；四应用 `pnpm build` 全绿）
- [ ] 前后端 API 联调（Wave 5.33 ⑧ 仅铺前端骨架，接**占位数据源 + TODO**；后端 `whale-apps/*`〔erp/oa/mes/pdm，9001–9004〕本轮未填充，列后续 wave）
- [x] ~~ops 运维平台：服务监控面板、审计日志查询、告警列表（对接 Prometheus）~~（Wave 5.33 ⑥：`frontend/ops` 独立 SPA〔端口 8104 + 代理 `/auth|/monitor|/alert|/audit` → 网关 8080，token 前缀 `whale_ops_`〕三页已接 `/monitor/service/list`、`/audit/log/page`、`/alert/rule/page` + `/alert/record/page`（双 Tab）；浏览器实机冒烟通过〔Monitor 6 服务 / Audit 14 条 / Alert 3 条规则〕；**Prometheus/Grafana 对接未做**，留 backlog）

## Phase 5：部署与 CI/CD

> **环境受限待办（本机无 Docker）**：Dockerfile / GitHub Actions / 生产 compose / Nginx HTTPS 均未在本机验证，转入后续具备 Docker 的环境补齐（Wave 5.33 已按用户口径跳过）。

- [ ] Dockerfile 多阶段构建验证（后端 3 个模板 + 前端）
- [ ] GitHub Actions CI 跑通
- [ ] 生产环境 `docker-compose.prod.yml` 验证
- [ ] Nginx 生产配置 + HTTPS

---

## 里程碑顺序

```
Phase 1 ──► 2.1 auth ──► 2.2 gateway ──► 2.3 common ──► 3.1 基座 ──► 3.3 admin
                                    └──► 2.4 i18n ──► 2.5 其他服务 ──► 3.2 UI ──► 3.4 portal ──► Phase 4 ──► Phase 5
```
