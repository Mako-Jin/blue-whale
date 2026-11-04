# 蓝鲸系统 - 搭建填充路线图

> 所有阶段性规划保存在本目录，完成一项在 `docs/devlog/` 中记录一项。

## Phase 1：基础设施就绪

> **状态（2026-10-23）**：Docker Desktop 已启动（daemon 29.9.2），Phase 1 四项已用镜像站完成实机验证（详见 [devlog §十六](../devlog/2026-10-23.md)）。注意 Docker Hub 直连仍不可用，需在 `.env` 用镜像站变量覆盖基础镜像名。

- [x] ~~1.1 配置环境变量 — 复制 `.env.example` → `infrastructure/docker/.env`，填入 MySQL/Redis/Nacos 密码~~（`.env.example` 补齐端口/镜像/Nacos Token/TLS 变量与 `bluewhale_*` 连接串）
- [x] ~~1.2 启动 Docker Compose — `cd infrastructure/docker && docker compose up -d`~~（实机拉起 mysql/redis/nacos/rabbitmq/nginx 五服务，全部 healthy；默认端口与本机中间件冲突时可用 `*_HOST_PORT` 错位）
- [x] ~~1.3 验证中间件 — Nacos (http://localhost:8848/nacos)、MySQL 11 个业务库自动建库、Redis 连通~~（MySQL 实建 16 个 `bluewhale_*` 库 + `whale_user` 通配授权；Redis 密码生效 `NOAUTH→PONG`、AOF 开启；Nacos 认证模式 `/nacos=302`、health=200；RabbitMQ 4 管理 API=200）
- [x] ~~1.4 验证 Nginx 配置 — 静态资源 / API 网关 / 微前端三套 conf.d 联调~~（`nginx -t` 通过；HTTP 301→HTTPS、HSTS/X-CTO/XFO/Referrer 安全头、TLS1.3+ALPN h2、`/api/**` 经网关登录 `code=0` 端到端打通；自签证书脚本 `infrastructure/scripts/gen-dev-cert.ps1`）

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
- [x] ~~国际化下沉为 SDK 自治（取消集中服务）~~（Wave 5.50：SDK 启动时在**各服务自己的库**幂等建 `sys_i18n_message`，扫描 `classpath*:i18n/**/messages.properties` 自动注册 basename，新增按服务默认关闭的本地文案 CRUD〔`whale.i18n.admin`〕；试点 `whale-auth`（开启，`/auth/i18n/message`）与 `whale-file`（关闭）；**删除** `whale-i18n` 模块/路由/`bluewhale_i18n` 库/前端 `/i18n-demo`；SDK **31 用例全绿**，auth/file 启动自动建表 + 网关 CRUD 冒烟通过；见 [devlog §二十](../devlog/2026-10-23.md) 与 [wave5.50](../plans/wave5.50-i18n-starter/spec.md)）

### 2.5 其余服务骨架填充

- [x] ~~whale-job（定时任务）骨架填充~~（Wave 5.19，T5.24：依赖/配置 + `JobInfo`/`JobLog` + `JobHandler` SPI + `JobScheduler`（`ThreadPoolTaskScheduler`+`CronTrigger`）+ `/job/**` REST + 6 权限点种子 + `bluewhale_job` 建库；8082 经网关冒烟 `code=0`）
- [x] ~~whale-job 服务「继续完成」~~（Wave 5.20：调度健壮性〔实例内互斥 `JobExecutionGuard` + Redisson 分布式锁 `lock:job:execute:{jobId}` + `misfire_policy` 落地 + `JobHandlerRegistry` 存在性校验〕+ 可观测〔`JobInfoVO.nextFireTime`、`runOnce` 异步〕+ 日志清理〔`JobLogCleanupHandler` + `whale.job.log-retention-days` + 内置种子任务〕+ 单测基线 42 用例；29 模块构建 SUCCESS，8082 冒烟含分布式锁路径全绿；**归档导出至对象存储仍阻塞于 whale-file**）
- [x] ~~whale-file（文件）最小可用~~（Wave 5.21，T5.25：存储抽象 `FileStorage`+`LocalFileStorage` + `POST /file/upload`、`POST /file/internal/upload`、`GET /file/{id}/download`、`GET /file/page`、`DELETE /file/{id}` + `sys:file:*` 4 权限点种子 + 8 单测；当时**未接对象存储**、落地本地文件系统，**Wave 5.49 已补齐插件化对象存储**）
- [x] ~~whale-job 归档导出~~（Wave 5.21，T5.25：`JobLogArchiveHandler` + `jobLogArchiveClient` 导出 CSV 上传 whale-file 成功后再逻辑删除，内置种子任务 id=2；失败不删语义；**对象存储诉求已于 Wave 5.49 解冻**）
- [x] ~~whale-audit 导出与归档~~（Wave 5.21，T5.25：`POST /audit/log/export`〔`EXPORT` 元审计〕+ `AuditLogArchiver` 归档；**不删除 `audit_log`**，append-only 不变）
- [x] ~~Wave 5.49 whale-file 存储插件化 + 对象存储接入~~（参考 `sts-components-file` 的 core/plugins 结构，先以 `whale-file-storage` 独立模块承载 `StoragePlugin` SPI 与 `plugin-local`/`plugin-minio` 两个插件，后按用户要求**合并回 `whale-file` 单模块**〔源码仍在 `storage/api` 与 `storage/plugin/{local,minio}` 包内，自动配置合并为一份 imports，pom 回迁 MinIO 与 kotlin 锁定〕；服务侧 `StoragePluginRegistry` 实现既有 `FileStorage` 端口，按 `whale.file.storage.type` 选路，业务与 `/file/**` 契约零改动；MinIO 插件写入前自动建桶（`auto-create-bucket=true`），**解锁 job/audit 归档独立桶**（无需人工 `mc mb`）；新增 `GET /file/storage/plugins` 插件清单端点；删除旧 `LocalFileStorage`/`MinioFileStorage`/`MinioConfig`/`MinioProperties`；合并后 `mvn -o -pl whale-services/whale-file -am test` BUILD SUCCESS / **51 用例全绿** / Checkstyle 0 违规 + 本机 MinIO 实机读写/预签名/自动建桶取证；见 [devlog §十九](../devlog/2026-10-23.md)）
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
- [x] ~~Wave 5.41 工作流剩余任务 ⑥：人工复核清单收口~~（无代码变更：转办弹窗空表单必填拦截、原生 `input` 事件提交 `PUT /workflow/task/{id}/transfer` → 200、DB `assignee=alice`/`owner_id=admin` 核对通过（复核后 SQL 还原 admin）；dnd-kit 真实指针拖拽 6 条人工清单（调色板拖入/网格吸附/参考线对齐/缩放后落点/空格与中键平移/撤销重做）归档 [devlog 2026-10-21 §十三](../devlog/2026-10-21.md)；可自动化部分全部通过）
- [x] ~~Wave 5.42 工作流剩余任务 ⑦：定义版本管理前端 UI~~（纯前端 `whale-workflow-designer`：契约层 `ProcessDefinitionVersion` / `DefinitionPublishRequest` / `DefinitionMigrationRequest` / `DefinitionMigrationResult` + 3 个 API；设计器顶栏「版本」按钮 → Drawer 版本表（版本号/发布状态/创建时间/发布、迁移）；发布后刷新列表并重载画布，迁移 Modal 按字符串提交实例 ID 并展示 `migrated/skipped/failed` 明细；`pnpm build` 通过 + 网关 `/versions` 冒烟 `code=0` + 浏览器抽屉/空态验证通过）
- [x] ~~Wave 5.43 工作流剩余任务 ⑧：画布分组 / 泳道容器~~（backlog 5b 收口：规范化 JSON 可选 `groups:[{key,name}]` + 节点 `groupKey`，容器坐标由成员包围盒派生；后端 `ProcessModelGroup`/解析/4 校验码/`toJson` 输出 + BPMN `laneSet/lane/flowNodeRef` 与 DI Bounds 双向；前端画布虚线容器、属性面板「所属分组」、分组新建/重命名/删除；workflow **147/147** 单测 + Checkstyle 0 违规 + `pnpm build` 通过 + 浏览器容器渲染/删除验证通过；见 [devlog 2026-10-21 §十五](../devlog/2026-10-21.md)）
- [x] ~~Wave 5.44 全平台统一主题~~（移植 `platform-terminator/common-theme` 到 `@bluewhale/whale-common-theme`：6 套配色 + 明暗两套、`themeCore` 持久化/跨标签同步、`ThemeProvider`/`useTheme`/`ThemeSwitcher`、antd token 动态派生、`--color-*`/`--bw-*` 双变量；admin、ops 与 9 个 platform-apps 全部接入，admin/平台顶栏提供切换入口；主题包与 11 个应用构建通过 + 浏览器持久化/暗色验证通过；见 [devlog 2026-10-21 §十六](../devlog/2026-10-21.md)）
- [x] ~~Wave 5.45 主题列表补回「经典蓝」~~（`whale-common-theme` 新增 `CLASSIC_BLUE`：亮色主色 `#1890FF`/背景 `#F5F5F5`/侧栏 `#001529`（取自迁移前 tokens），暗色 `#177DDC`/`#141414`；列表名「经典蓝」置首，默认主题不变；主题包构建通过 + 浏览器亮/暗变量验证通过；见 [devlog 2026-10-21 §十七](../devlog/2026-10-21.md)）
- [x] ~~whale-pay（支付）~~（Wave 5.33 ⑨：建库 `bluewhale_pay` + `pay_order` 表 + `/pay/order`〔page/详情/create/`{id}/pay`/`{id}/cancel〕5 接口 + `sys:pay:*` 权限种子 id 70–73；状态单向 `CREATED→PAID/CANCELLED`；9 单测 + Checkstyle 0 违规）
- [x] ~~whale-audit（审计日志，**四通道查询 + 元审计 + 出站/接口日志**已落地，Wave 5.19 建 `bluewhale_audit` 库并运行时验证）、whale-monitor（监控）、whale-alert（告警）~~（Wave 5.33 ③④⑤：monitor 补 `GET /monitor/service/list` + `/monitor/service/{name}/instances`〔`DiscoveryClient` 服务与实例健康聚合，`sys:monitor:list` id 64〕；alert 建库 `bluewhale_alert` + `alert_rule`/`alert_record` + `/alert/rule`〔page/详情/create/update/delete/启停〕+ `/alert/record/page` + 权限种子 id 65–69 + 网关 `/alert/**` 路由；audit **仅文档勾选同步**；~~MQ 汇聚~~ **已于 2026-10-23 AUTH-10 落地**〔RabbitMQ 生产者 starter + whale-audit 消费者/幂等表 `audit_mq_message`/DLQ，三级降级 MQ→HTTP→本地，whale-auth 试点实机验收〕，异步派发因 `shouldNotFilterAsyncDispatch` 问题已在 P3 第 2 项修复）

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

> **执行计划（2026-10-21 制定）**：[wave5.46-phase4-business-apps.md](wave5.46-phase4-business-apps.md)（四应用同构主线 + 底座/网关/基座/门户收口 + 验收口径）

- [x] ~~whale-erp / whale-mes / whale-pdm / whale-oa 各自列表页 + 表单页骨架~~（Wave 5.33 ⑧：erp(8102)/oa(8103) 补列表页 + 表单页 + API 层；**新建** mes(8105)/pdm(8106) 子应用并注册进基座 `whale-platform/src/qiankun/apps.ts`；四应用 `pnpm build` 全绿）
- [x] ~~业务应用后端填充（`whale-apps/*`〔erp/oa/mes/pdm，9001–9004〕：DDL + 六端点 + 权限种子 + 网关路由）~~ → [wave5.46](wave5.46-phase4-business-apps.md) 轨 A–F（**2026-10-25**：`whale-oa` 请假域 DDL + 6 端点 + 权限 168–171 + 网关 `/oa/**`，见 [检查报告](../reviews/2026-10-25-p3-oa-leave-ci.md)；**2026-10-26**：erp/mes/pdm 三应用全部闭环——`erp_item`/`mes_work_order`/`pdm_product` DDL + 各 5 接口 + 权限 203–214 + 菜单 25–27 + `lb://` 路由，见 [ERP](../reviews/2026-10-26-p3-erp-item.md) / [MES](../reviews/2026-10-26-p3-mes-work-order.md) / [PDM](../reviews/2026-10-26-p3-pdm-product.md) 检查报告）
- [x] **Wave 5.55 项目管理服务 `whale-pm`**（对标 Jira/禅道）：9005 / `/pm/**` / `bluewhale_pm` / 前端 8108 → [wave5.55](wave5.55-project-management.md)（**2026-10-25 M1–M5 全部完成**：项目域 CRUD + 成员 + 状态/流转配置 + 权限 172–186 + 菜单 18–21 + 网关路由；M2 工作项；M3 迭代看板；M4 报表与前端；M5 IM 站内通知与 workflow 审批（默认关闭））
- [x] ~~前后端 API 联调（前端占位数据源替换 + 基座 proxy + 真实登录）~~ → [wave5.46](wave5.46-phase4-business-apps.md) 轨 B–G（**2026-10-25**：`whale-oa` 请假列表/表单已接真实接口并恢复基座鉴权；**2026-10-26**：`api/erp.ts`、`api/mes.ts`、`api/pdm.ts` 全部去 mock 改接 `/erp|/mes|/pdm/**` 经网关 `lb://` 真实接口，三应用 `tsc` + `vite build` 通过，网关 CRUD 冒烟全 `code=0`，见 [ERP](../reviews/2026-10-26-p3-erp-item.md) / [MES](../reviews/2026-10-26-p3-mes-work-order.md) / [PDM](../reviews/2026-10-26-p3-pdm-product.md) 检查报告）
- [x] ~~基座 / 门户占位收口（4 个 `PlaceholderPage` + 门户 Dashboard/通知/待办）~~（2026-10-25：基座用户/角色/菜单改为真实只读页并删除 `PlaceholderPage`；门户 Dashboard 四项统计接真实接口、待办审批抽屉、通知接告警 + IM 双源；详见 [检查报告](../reviews/2026-10-25-p2-portal-platform-placeholders.md)）→ [wave5.46](wave5.46-phase4-business-apps.md) 轨 G
- [x] ~~ops 运维平台：服务监控面板、审计日志查询、告警列表（对接 Prometheus）~~（Wave 5.33 ⑥：`frontend/ops` 独立 SPA〔端口 8104 + 代理 `/auth|/monitor|/alert|/audit` → 网关 8080，token 前缀 `whale_ops_`〕三页已接 `/monitor/service/list`、`/audit/log/page`、`/alert/rule/page` + `/alert/record/page`（双 Tab）；浏览器实机冒烟通过〔Monitor 6 服务 / Audit 14 条 / Alert 3 条规则〕；2026-10-25：ops Monitor 页补 Prometheus/Grafana 外链，Alertmanager→whale-alert 入站与邮件/Webhook 通知打通〔REQ-ARCH-007〕，详见 [检查报告](../reviews/2026-10-25-p2-alert-notify-prometheus.md)）

- [x] **Wave 5.61 合同管理 M1**（`whale-contract` 9006 / `/contract/**` / `bluewhale_contract` / 前端 8109）：4 表 + 11 接口 + 状态机 + 工作流提交与审批结果同步 + 权限 215–222 + 菜单 28 + 网关路由；构建成功、9006 UP、经网关冒烟 18/18、浏览器列表/详情正常 → [设计稿](wave5.61-contract-esign.md) / [检查报告](../reviews/2026-10-26-p3-wave5.61-contract-m1.md)
- [x] **Wave 5.61 M2 电子签章**（2026-10-26）：`ct_seal` / `ct_seal_grant` / `ct_sign_task` / `ct_sign_record` + `audit_log` + 用章授权校验（403 审计）+ PDFBox 视觉盖章（含骑缝 2 页）+ BouncyCastle PKCS#7 签名与验签 + 链式哈希证据链 + 拒签/作废 + 权限 223–231 + 菜单 29/30 + 前端印章管理与签署中心；冒烟 27/27、前端 `tsc`/`build`/`lint` 通过 → [检查报告](../reviews/2026-10-26-p3-wave5.61-contract-m2.md)
- [x] **Wave 5.61 M3 第三方签章**（2026-10-26）：`EsignProvider` 适配层（local/e签宝/法大大/上上签）+ `ct_esign_callback` 幂等表 + HMAC 验签与 ±300s 时间窗 + 三方证据落库/链式哈希 + `/workflow/internal/task/{id}/complete`（`SYSTEM:ESIGN_CALLBACK` 留痕）+ 签署发起对接 provider + 生效接口（权限 232）+ 菜单 31 证据中心 + 网关回调白名单 + 前端证据中心页；回调重放 10 次仅推进一次、坏签名/超窗 403 落审计、链篡改可检出、provider 切换仅改配置、签署后生效归档闭环；冒烟 00–27 全通过、前端 build/lint 通过 → Wave 5.61 三阶段全部完成 → [检查报告](../reviews/2026-10-26-p3-wave5.61-contract-m3.md)
- [x] **Wave 5.61 M3.5 回调乱序补偿**（2026-10-26）：`EsignCallbackSigner` 统一验签/补偿签名口径 + `SignTaskReconcileJob` 定时轮询超时 `SIGNING` 非 local 任务，终态时以 `RECONCILE-<taskId>-<action>` 自签内部回调复用既有幂等/证据/流程链路；配置五项支持环境变量覆盖；单测 4/4、实机三类任务筛选与不误推进验证通过 → [检查报告](../reviews/2026-10-26-p3-esign-callback-reconcile.md)
- [x] **Wave 5.61 M3.6 归档快照衔接**（2026-10-26）：archive 新增 `POST /archive/internal/items`（复用 `ArchiveItemStore` + 同步台账，快照上传 + SHA-256 + 「待整理」条目），contract 归档推送合同快照并回填 `archive_item_id`；M3 冒烟 00–27 全通过、条目/文件/台账/幂等/回读摘要校验齐备 → [检查报告](../reviews/2026-10-26-p3-contract-archive-snapshot.md)
- [x] **Wave 5.75 音乐平台 `whale-music`（M0–M5 已完成并收口，可上线）**（8096 / `/music/**` / `bluewhale_music` / 前端 8111）：曲库、播放（票据 + Range 流式）、歌单、发现、评论与审核、版权申诉、7 天榜；M5 收口含网关四类限流 429 实测、音乐专用备份演练 `RESULT: PASSED`、PS 5.1 兼容、admin 口令重置与网关真实登录链路实测；单测 117/117、Checkstyle 0、ArchUnit 5/5 → [实施计划](wave5.75-music-platform.md)（关联 REQ-FUNC-029/030、REQ-ARCH-013、REQ-SEC-019）｜ M0 检查报告：[2026-10-31](reviews/2026-10-31-wave5.75-music-m0.md)｜ M1 检查报告（后端 + 前端 + G1）：[2026-10-31](reviews/2026-10-31-wave5.75-music-m1.md)｜ M2 检查报告（播放与音频流）：[2026-10-31](reviews/2026-10-31-wave5.75-music-m2.md)｜ M5 检查报告：[2026-10-31](reviews/2026-10-31-wave5.75-music-m5.md)（**剩余**：浏览器逐页点检 B-09、通知链路实机验证 B-10、`comment-pre-audit=true` 先审后发切换）
- [x] **Wave 5.77 视频平台 `whale-video`（M0–M5 已完成，代码 / 脚本 / 文档收口，实机验收待环境）**（8098 / `/video/**` / `bluewhale_video` / 前端 8114）：短视频 / 视频 / 系列 / 电影三条内容线共享 `vd_content` 注册、播放票据 + Range 流式、互动、审核与分类标签底座；视频字节归 `whale-file`，前置依赖 **G1 按权限码大文件上传策略**（`sys:video:upload` 2GB/8GB、`sys:video:movie:manage` 4GB/不限、默认 50MB/1GB）；权限 305–322、菜单 46–52、角色 25105/25106 → [实施计划](wave5.77-video-platform.md)（关联 REQ-FUNC-031/032/033、REQ-ARCH-014、REQ-SEC-025）｜ M0 检查报告：[2026-11-01](../reviews/2026-11-01-wave5.77-video-m0.md) ｜ M1 检查报告：[2026-11-01](../reviews/2026-11-01-wave5.77-video-m1.md)（**M0 交付**：服务骨架 8098、Flyway 14 表、DDD 四层 + ArchUnit 5/5、网关路由与限流、权限/菜单/角色种子、前端子应用 8114、G1 大文件策略 47 单测、契约冻结与 7 个冒烟骨架、CI/镜像/Nginx/Prometheus 矩阵；**M1 交付**：内容聚合与发布状态机、上传编排与 magic bytes 校验、票据 + Range 流式 200/206/416、互动与进度、评论与审核、首页聚合与缓存、运营发布/下架/版权下架、单测 64/64 + Checkstyle 0 + 冒烟强断言化；**M2 交付**：短视频作品管理（编辑回填 `ownerView` + `OwnerContentView` 状态）、feed 键集游标（`LATEST / HOT / 分类`）与首屏缓存分区、前端滑播 / 详情 / 上传中心 / 我的作品 / 票据刷新播放器、网关票据与互动限流、`bw-video-m2-smoke.ps1` 强断言，单测 73/73 + Checkstyle 0 + 前端 build/lint 通过；**M3 交付**：`vd_series` 系列聚合与集数唯一（应用层 `existsSeriesEpisode`）、视频上传 / 编辑 / 删除 / 我的视频与系列集数、视频列表 / 详情 / 系列页 / 搜索（新增 `GET /video/series/page` 仅已上架）、播放器断点续播 + 全屏 + 快捷键、admin 视频与系列管理、系列排序 `sort_no asc, id desc` 与加集幂等、`bw-video-m3-smoke.ps1` 全段强断言，单测 96/96 + Checkstyle 0 + `whale-video` / `admin` build/lint 通过；**M4 交付**：`vd_movie` 电影元数据复用 `vd_content`（仅运营入库）、授权来源 / 编号 / 到期校验与 `MovieLicenseExpiryScheduler`（每日 03:10 自动下线）、电影列表 / 详情、`vd_collection` / `vd_collection_item` 片单（加片幂等、单片单 ≤200）、`GET /video/rankings` 热门 / 新片榜、跨类型搜索、评论审核 / 统计概览 / 创作者管理、admin 电影与片单 / 创作者页面（`video/movie-list`、`video/catalog` Tabs、`video/comment-audit`、`video/stat`）、`bw-video-m4-smoke.ps1` 全段强断言，单测 146/146 + Checkstyle 0 + `whale-video` / `admin` 的 `tsc -b` + `vite build` + `eslint --max-warnings 0` 通过（M4-8 以统计页临期提醒 + 每日自动下线兜底，本期不接消息中心）；**M5 交付**：越权口径固定「不可见 40401 / 可见非本人 40301」、观看历史清理任务（每日 04:40、500/批 × ≤20 批）+ `V4__watch_history_cleanup_index.sql`、`bw-video-m5-smoke.ps1` 强断言（上传安全 / 票据 300+240 / Range 206+416 / 越权矩阵 / 直方图）、`bw-video-m5-load.ps1`（50 并发 × 3 轮）、`bw-video-gateway-rate-limit-smoke.ps1`（429）、`video-backup-restore.ps1`（19 表校验）、SLO-9/10/11 + Grafana `bluewhale-video`（部署侧 + Docker 侧）、`docs/ops/video-service-ops.md`，单测 151/151 + Checkstyle 0 + ArchUnit 5/5（举报入口与孤儿文件清理为可选项，本期不做）；**剩余**：建库与迁移实跑、实机起服与 M1–M5 冒烟、429 / 授权到期任务 / 备份恢复实测、浏览器联调、海报 / 封面与流式网关鉴权方案）｜ M2 检查报告：[2026-11-01](../reviews/2026-11-01-wave5.77-video-m2.md) ｜ M3 检查报告：[2026-11-01](../reviews/2026-11-01-wave5.77-video-m3.md) ｜ M4 检查报告：[2026-11-01](../reviews/2026-11-01-wave5.77-video-m4.md) ｜ M5 检查报告：[2026-11-01](../reviews/2026-11-01-wave5.77-video-m5.md)

## Phase 5：部署与 CI/CD

> **状态（2026-10-23）**：Docker Desktop 已启动。Nginx HTTPS、生产 compose 覆盖层与中间件编排已在 Phase 1 实机验证（[devlog §十六](../devlog/2026-10-23.md)）；后端 3 个 Dockerfile 模板 + 前端镜像的多阶段构建已实机构建并运行验证（[devlog §十七](../devlog/2026-10-23.md)）。

- [x] ~~Dockerfile 多阶段构建验证（后端 3 个模板 + 前端）~~（2026-10-23：模板对齐真实布局——后端以仓库根为上下文、`-pl <module> -am` 多模块构建，新增 `whale-auth` 模板与参数化通用模板 `sample`（`MODULE_PATH`/`SERVICE_PORT`），前端补 `APP_NAME`/`APP_DIR` 双参数；实机构建 4 个镜像全部成功：gateway 613MB / auth 712MB / 通用模板(whale-file) 599MB / web 95.6MB，均非 root，网关容器实测引导成功、前端 `healthz`/SPA 回退/assets 全 200；新增根 `.dockerignore`）
- [x] ~~T-CI-7 镜像扩面 + coverage-gate 接 CI~~（2026-10-25：`docker-deploy.yml` 改为 18 个后端服务矩阵并统一 `deploy/backend/sample/Dockerfile`；Backend CI 与 Gitee Go 备用流水线改为 `-Pcoverage-gate verify` 并上传 JaCoCo 报告；全 reactor 本地 `BUILD SUCCESS`，详见 [检查报告](../reviews/2026-10-25-p3-oa-leave-ci.md)）
- [x] ~~GitHub Actions CI 跑通~~（2026-10-22 平台评审 P0-1：GitHub 镜像仓 `Mako-Jin/blue-whale-platform`（remote `ci`）触发 Backend CI / Frontend CI / Code Quality 全绿；故意引入未使用变量 + 缺 Javadoc 后三条全部变红，revert 后恢复全绿；Checkstyle 绑定 `validate` 阶段阻断）
- [x] ~~构建可复现~~（2026-10-22 平台评审 P0-2：仓库根入库 Maven Wrapper 3.9.9（only-script）+ `.mvn/jvm.config`；`check-env.ps1` 增加 JDK 21 断言；启动脚本统一 `WHALE_JAVA_HOME`，Nacos 用 `WHALE_NACOS_JAVA_HOME`）
- [x] ~~E2E 冒烟（两批）~~（2026-10-22 平台评审 P0-3：新建 `frontend/e2e`（`@bluewhale/e2e`）+ Playwright `webServer`（8001/8002/8003）；用例 A 登录/菜单按权限渲染、用例 B 建表→加列→发布→填报→列表可见、用例 C 发起流程→首节点审批→会签（ALL 两人）→通过；`pnpm e2e` **3 passed**，两批均做反向验证失败闭环）
- [x] ~~文档唯一真相~~（2026-10-22 平台评审 P0-4：README 版本/端口表改为指向 `whale-parent/pom.xml` 与 `service-catalog.md`；`architecture.md` Sentinel 表述修正为「Redis 限流 + resilience4j 熔断，Sentinel 未引入」；新增 `scripts/check-doc-consistency.ps1` 以 POM 为唯一版本源，现行事实文档 0 过期标记，注入 `4.0.0-M3` 时退出码 1；`docs/*/originals/` 经用户确认保留现状，不清理）
- [x] ~~仓库卫生~~（2026-10-22 平台评审 P1-4：`.gitignore` 补 `/work/`、`/.workbuddy/`、`derby.log`（`data/` 覆盖 `data/agent-index/`）；`git ls-files` 审计 `target/work/logs/*.jar/*.log/node_modules/dist` 均 0 命中；补入平台评估报告修复断链；`.trae/specs/` 保留入库；`git status` 干净）
- [x] ~~安全默认值与 CORS~~（2026-10-22 平台评审 P1-2：10 个服务 yml 移除 `${MYSQL_PASSWORD:root}`，新增 starter `DataSourcePasswordGuard` 空口令拒绝启动并给出 `MYSQL_PASSWORD` 指引；网关新增 `whale.gateway.cors.allowed-origins` + `CorsWebFilter`，预检允许来源 200、`http://evil.example` 403）
- [x] ~~审计日志与脱敏~~（2026-10-22 平台评审 P1-3 用户口径：`SensitiveDataMasker#maskQueryString` 覆盖 `api_log.query_string` 与 `outbound_log.target_uri`（secret 11 键 / token / 手机号 / 证件 / 银行卡 / 邮箱）；starter 新增 `AuditLogForwarder` + `whale.audit.forward.*`，whale-auth 试点转发到 whale-audit `POST /audit/log/ingest`，失败回退本地；网关 `internal-deny` 403 屏蔽摄入；starter 77 项单测全绿、实机正向 + 两项反向验证通过）
- [x] ~~前端统一请求层~~（2026-10-22 平台评审 P1-1：新建 `frontend/shared-packages/http`（`@bluewhale/http`），统一信封 / Bearer / HTTP 401 单飞刷新重放 / `ApiError` / `X-Trace-Id` / `downloadBlob` / `streamSse`；10 个应用（admin、ops、portal、form-designer、workflow-designer、agent、erp、mes、oa、pdm）改为薄适配 `api/client.ts` 并删除各自 `api/http.ts`；共享包 smoke 全绿、10 应用 build+lint 全绿、Playwright E2E 3 passed）
- [x] ~~可观测性最小闭环（Prometheus）~~（2026-10-22 平台评审 P1-3b：`whale-parent` 显式管理 `micrometer-registry-prometheus` 1.16.7；12 个服务暴露 `/actuator/prometheus` 并统一 `application` 标签；auth/workflow 开 HTTP 耗时直方图；新增 [ops/slo.md](../ops/slo.md) 3 条 SLO + PromQL；8081/8083/8080/9101 实测 200 且 `_bucket` 存在）
- [x] ~~巨型组件拆分~~（2026-10-22 平台评审 P2-1：`WorkflowDesigner` 1049→146、`Canvas` 663→192、`TaskCenter` 646→39、`DefinitionList` 592→20，拆入 `store/`、`panels/`、`canvas/`、`task/`、`definition/`；`src` 无 >400 行文件；build + ESLint + 浏览器回归 + E2E 3 passed）
- [x] ~~God Service 拆分~~（2026-10-22 平台评审 P2-2：`ProcessTaskServiceImpl` 651→67 行纯委派，新增 `service/impl/task/` 8 个命令 + 3 个支撑类，写命令独立事务；147 单测全绿，新 jar 重启后 E2E 3 passed）
- [x] ~~API 契约自动化~~（2026-10-22 平台评审 P2-3：whale-auth 接入 springdoc 3.1.1，`/v3/api-docs` 200（24 paths）；连带将 `commons-lang3` 抬至 3.20.0 修复 `Strings` 缺失；admin `openapi:types` 生成 41KB 类型且 build/lint 通过）
- [x] ~~分支与评审策略~~（2026-10-22 平台评审 P2-4：本轮 P2 变更走 `wave5.48-p2-refactor` 分支后合入 `main`；Gitee PR 需网页手工补建，PR 环节待下一次 Wave 验证）
- [x] ~~生产环境 `docker-compose.prod.yml` 验证~~（2026-10-26 P3-10：prod 覆盖层经 `docker compose config` 在引擎运行态合并校验通过（资源限制 / `no-new-privileges` / `read_only`+`tmpfs` / Let's Encrypt 证书卷均正确解析）；宿主机无 Let's Encrypt 证书，覆盖层运行时未实跑，开发编排容器化 Nginx HTTPS 已验证；详见 [检查报告](../reviews/2026-10-26-p3-docker-prod-nginx-https.md)）
- [x] ~~Nginx 生产配置 + HTTPS~~（2026-10-26 P3-10：`nginx -t` 全配置语法通过；自签证书 CN=bluewhale.local / SAN 覆盖 api/app/localhost / 有效期至 2029-01-25 且公私钥匹配；容器化实测 HTTP→HTTPS 301、`/api/**` 经 `host.docker.internal:8080` 网关登录 `code=0`）

## Phase 6：平台与项目知识 Agent

> **执行计划（2026-10-21 制定，待评审）**：[wave5.47-project-agent.md](wave5.47-project-agent.md)（Java 21 + 内嵌 Lucene 双通道 RAG；代码/文档/DDL/版本/Git/运行时六类知识源；Web 工作台；MCP 二期）

- [x] 轨 A 底座与索引（Lucene 依赖验证 / 采集器 / 五类切分 / BM25 检索）—— 2026-10-21 完成 A1–A5，全仓 1248 文件 / 7666 块，建索引 6.3s；向量与 RRF 融合并入轨 C
- [x] 轨 B 结构化事实层（服务/路由/端口、DDL 表字段、依赖版本、Git、运行时）—— 2026-10-21 完成 B1–B5，9 类事实 894 条，重建 3.5s；`GET /agent/facts` 经网关验收通过
- [x] 轨 C 问答编排（OpenAI 兼容 API + SSE 流式 + 引用校验 + 逐级降级）—— 2026-10-22 完成 C1–C5：`POST /agent/chat`（meta/delta/done/error）、意图路由、事实+BM25 召回、提示词引用编号、引用校验、会话/消息/评价接口；未配置 Key 时降级检索摘要，经网关验收通过；~~向量 / RRF / embeddings~~ 已于 2026-10-23 P3 第 6 项完成（哈希/OpenAI 兼容双 embedding + KNN 向量 + RRF 融合，全量重建 1524 文件 / 9307 块 / 维度 256）
- [x] 轨 D 前端工作台 `whale-agent`（8004 /agent，会话 + 证据面板 + 范围开关）—— 2026-10-22 完成：独立应用 `frontend/platform-apps/whale-agent`（8004，真实认证直连网关，storage 前缀 `whale_agent_`，proxy `^/(auth|agent)`），三栏工作台（会话/范围、SSE 打字机消息流、`[Sx]` 证据面板）+ 索引徽标/重建 + 有用/无用评价；`tsc -b && vite build` 通过，浏览器实机 9 步验收全绿
- [x] ~~轨 E 治理与评测（权限点 / 脱敏 / 审计 / 40 题黄金集）~~ —— 2026-10-22 完成 E1–E4：`RUNTIME_SERVICE` 按 `sys:agent:runtime:read` 隔离（越权 403、默认剔除）、三查询接口 `@AuditLog(QUERY)` + `audit_log` 落库、脱敏器空值 NPE 修复、auth 菜单种子 id 9/10；40 题黄金集 + `scripts/agent-eval.ps1` 实机评测**总体 40/40（100%）、结构化 20/20（100%）、引用有效 298/298（100%）** 三项阈值全 PASS，报告 [reviews/2026-10-22-wave5.47-agent-eval.md](../reviews/2026-10-22-wave5.47-agent-eval.md)；运维手册 [ops/agent-index-ops.md](../ops/agent-index-ops.md)
- [x] ~~轨 F（可选，二期）MCP / IDE 接入~~ —— 2026-10-22 完成：`POST /agent/mcp`（JSON-RPC 2.0，protocolVersion `2024-11-05`）暴露 `search_knowledge` / `get_fact` / `read_file` / `list_services` 四个只读工具；本地 Token（`X-MCP-Token` 或 `Authorization: Bearer`，常量时间比较）鉴权，未配置 Token 返回 503；路径穿越/符号链接越界/敏感文件/构建产物/`RUNTIME_*` 事实全部拒绝；直连 8090 不经网关；13 项 MCP 单测（模块 29 项全绿）+ 实机 10 项正反向验收通过，手册 [ops/agent-mcp.md](../ops/agent-mcp.md)

## Phase 7：Agent 自主编排（tool-calling / ReAct）

> 背景：Phase 6 的问答是「一次召回 + 一次生成」的 RAG，模型不决定检索路径；本阶段把 MCP 四工具升级为模型可调用的 function，由模型自主多轮取证。
> 依赖：需要可用的 OpenAI 兼容模型（`whale.agent.llm.api-key`）；未配置时自动走原单轮 RAG，行为不变。

- [x] 轨 A LLM 层扩展：`ChatMessage` 支持 `tool` 角色与 `tool_calls`，新增 `LlmToolCall` / `LlmToolSpec` / `LlmToolTurn`，`LlmClient.completeWithTools` 默认返回 `null`（旧实现零破坏），`OpenAiClient` 实现 `tools` + `tool_choice:"auto"` 请求与 `tool_calls` 解析
- [x] 轨 B 工具注册表：`KnowledgeToolRegistry` 把 MCP 四工具适配为 function 定义（JSON Schema + 输出压缩），复用 `McpToolService` 的只读与安全校验
- [x] 轨 C 编排循环：`ToolCallingAgent` 实现 ReAct 循环（`max-tool-steps`，硬上限 12）；未知工具/参数非法/内部异常作为文本回填不中断；证据按登记顺序编 `S1..Sn` 并回填「可用引用编号」；模型失败回落已取证摘要（`degraded=true`）
- [x] 轨 D 服务接入：`ChatService` 双路径（`agentic-enabled && 模型可用` → 自主编排，否则原单轮 RAG）；新增 SSE `tool` 事件（前端 `default` 分支忽略，后续可接）；回答经 `CitationValidator` 校验后落库
- [x] 轨 E 验证与文档：新增 `ToolCallingAgentTest` 6 项 + `OpenAiClientToolTest` 3 项，模块 **38 项测试全绿**；配置项 `whale.agent.qa.agentic-enabled` / `max-tool-steps` 写入 [ops/agent-index-ops.md](../ops/agent-index-ops.md)；实机需模型 Key，暂不做无 Key 实跑
- [x] 轨 F 实机验收（2026-10-23：本地 Ollama `qwen2.5:3b` 实机验收通过——多轮工具调用、SSE `tool` 事件、引用 S1..Sn 与事实来源；工具轨迹已在 `whale-agent` 工作台展示（`ToolTracePanel`，仅当轮可见））

## Phase 8：城池场景与客户关系（规划，2026-11-03）

> 执行计划：[2026-11-03-wave5.77-scene-and-crm.md](2026-11-03-wave5.77-scene-and-crm.md)
> 结论：上传文档的模块地图中仅「城池场景导航」缺服务；CRM 缺位但受 REQ-ARCH-011 需求门禁约束。两服务已登记，**未编码**。

- [ ] 8.1 文档治理与借鉴动作（DOC-01–DOC-10）
- [ ] 8.2 `whale-scene` M0 设计定稿（ADR-002、设计稿、ER/API、权限 323–336、菜单 53–56）
- [ ] 8.3 `whale-scene` M1 骨架（8097、`/scene/**`、`bluewhale_scene`、前端 8112）
- [ ] 8.4 `whale-scene` M2 2D MVP（场景/节点/绑定/可见性/发布/3 个入口）
- [ ] 8.5 `whale-scene` M3 3D 可选增强（需满足 F-09 解冻四条件）
- [ ] 8.6 `whale-crm` 需求门禁评审（未通过前不编码）
- [ ] 8.7 `whale-crm` M4 客户/联系人（9007、前端 8113）
- [ ] 8.8 `whale-crm` M5 商机/漏斗/合同联动

---

## Phase 9：MES 功能模块全景（规划，2026-11-04）

> 执行计划：[2026-11-04-wave5.79-mes-panorama.md](2026-11-04-wave5.79-mes-panorama.md)
> 设计稿：[../design/mes-module-panorama.md](../design/mes-module-panorama.md)
> 结论：`whale-mes` 现仅生产工单占位 CRUD；保持 **9002 / `/mes/**` / `bluewhale_mes` / 前端 8105** 单服务，按 7 业务上下文 + 车间主数据共享内核分包；首切片 **M0 车间主数据 + M1 调度与执行**，不拆微服务。

- [ ] 9.1 M0 车间主数据 + 骨架重构 + 工单模型升级（权限 1100–1129 / 菜单 240–244）
- [ ] 9.2 M1 工单接收/拆分/派工/有限产能排程 + 执行/报工/领料/WIP/异常/生产看板（权限 1130–1219 / 菜单 245–259）
- [ ] 9.3 M2 条码/批次/谱系/扫码防错/线边仓/正反追溯（权限 1220–1259 / 菜单 260–266）
- [ ] 9.4 M3 首件/IPQC/不良品处置/返工/SPC（权限 1260–1299 / 菜单 267–273）
- [ ] 9.5 M4 设备台账/状态/OEE/保养/维修/备件 + 设备采集/看板/归档（权限 1300–1349 / 菜单 274–281）
- [ ] 9.6 M5 班组/排班/交接班/工时/绩效 + 综合看板与运维收口（权限 1350–1399 / 菜单 282–299）

---

## Phase 10：PDM 功能模块全景（规划，2026-11-04）

> 执行计划：[2026-11-04-wave5.80-pdm-panorama.md](2026-11-04-wave5.80-pdm-panorama.md)
> 设计稿：[../design/pdm-module-panorama.md](../design/pdm-module-panorama.md)
> 结论：`whale-pdm` 现仅产品档案占位 CRUD；保持 **9003 / `/pdm/**` / `bluewhale_pdm` / 前端 8106** 单服务，按 Document/PartBom/Change/Process/Collab 五上下文 + Integration 适配层分包；首切片 **M0 图文档中心 + M1A 零件库/EBOM 发布契约**（解除 ERP MRP 的 BOM 快照阻塞），不拆微服务。

- [x] 10.1 M0-A 图文档中心：目录/文档/版本上传·检出检入·发布回滚·下载 + 多维检索 + 多路由前端（权限 900–909 / 菜单 180）
- [ ] 10.1b M0-B 文档 ACL（文件夹继承/DENY 优先）+ 预览任务 + 导出（权限 910–929 / 菜单 181–184）
- [ ] 10.2 M1A 零件主档/分类 + EBOM + BOM 发布快照契约（权限 930–949 / 菜单 185–189）
- [ ] 10.3 M1B PBOM/MBOM 派生 + BOM 对比/导出 + 零件去重/供应商只读
- [ ] 10.4 M2 版本规则 + ECR/ECO/ECN + 追溯 + 生效任务（权限 950–969 / 菜单 190–194）
- [ ] 10.5 M3 工艺路线/工序/工时/工装/SOP + MES 下发（权限 970–979 / 菜单 195–197）
- [ ] 10.6 M4 项目交付物/评审 + 2D/3D 预览与检索增强（权限 980–989 / 菜单 198–201）
- [ ] 10.7 M5 CAD/ERP/OA 集成收口 + 同步日志/重试/对账（权限 990–999 / 菜单 202–203）

---

## Phase 11：OA 功能模块全景（规划，2026-11-04）

> 执行计划：[2026-11-04-wave5.82-oa-panorama.md](2026-11-04-wave5.82-oa-panorama.md)
> 结论：`whale-oa` 保持 **9004 / `/oa/**` / `bluewhale_oa` / 前端 8103** 单服务；审批中心复用 `whale-workflow` 并补动态审批人/抄送/代理/催办/查询；关键业务表单 OA 领域化、低频通用表单用 `whale-form`；权限 1600–1799 / 菜单 360–409 / 角色 25123–25127；首切片 **M0 审批底座 + M1A 审批中心与请假闭环**。

- [ ] 11.1 M0 审批底座 + Workflow/Auth 缺口 + 骨架（权限 1600–1629 / 菜单 360–364）
- [ ] 11.2 M1A 审批中心 + 请假闭环 + 员工档案 + 额度台账（权限 1630–1659 / 菜单 365–369）
- [ ] 11.3 M1B 加班 + 出差 + 调休/预支（权限 1660–1679 / 菜单 370–372）
- [ ] 11.4 M2 考勤 + 报销 + 用车 + 用章 + 采购申请（权限 1680–1729 / 菜单 373–382）
- [ ] 11.5 M3 协同办公：会议/日程/任务/公告/网盘/通讯录/工作台（权限 1730–1759 / 菜单 383–390）
- [ ] 11.6 M4 公文与档案（权限 1760–1779 / 菜单 391–394）
- [ ] 11.7 M5 文化/投票/建议 + 人员异动 + 组织增强（权限 1780–1789 / 菜单 395–397）
- [ ] 11.8 M6 报表 + 安全/性能 + 试点（权限 1790–1799 / 菜单 398–409）

---

## Phase 12：CRM 功能模块全景（规划，2026-11-04）

> 执行计划：[2026-11-04-wave5.83-crm-panorama.md](2026-11-04-wave5.83-crm-panorama.md)
> 设计稿：[../design/crm-module-panorama.md](../design/crm-module-panorama.md)
> 结论：`whale-crm` 保持 **9007 / `/crm/**` / `bluewhale_crm` / 前端 8113** 单服务；6 上下文 + integration/common 分包；权限 1800–1899 / 菜单 410–449 / 角色 25128–25132；需求 080–086 / ARCH-024 / SEC-034；首切片 M0 底座 + M1 客户主数据。

- [ ] 12.1 M0 底座 + 客户主数据（以 CRM 计划为准）
- [ ] 12.2–12.7 M1–M6 按 CRM 计划推进（销售/商务/售后/营销/分析）

---

## 里程碑顺序

```
Phase 1 ──► 2.1 auth ──► 2.2 gateway ──► 2.3 common ──► 3.1 基座 ──► 3.3 admin
                                    └──► 2.4 i18n ──► 2.5 其他服务 ──► 3.2 UI ──► 3.4 portal ──► Phase 4 ──► Phase 5
```
