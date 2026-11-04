# PDM 功能模块全景：领域模型与架构设计（规划稿）

> 状态：**规划（2026-11-04，未编码）**。本稿只定义边界、模型、契约与分期，不承诺实现细节。
> 关联计划：[Wave 5.80 实施计划](../plans/2026-11-04-wave5.80-pdm-panorama.md)。
> 关联需求：[REQ-FUNC-050..056 / REQ-ARCH-020 / REQ-SEC-030](../requirements/register.md)、[服务清单](../service-catalog.md)、[命名规范](../naming-conventions.md)。
> 输入：用户《PDM 功能模块全景》（图文档 / 产品结构 / 版本与变更 / 零件库 / 项目任务 / 工艺 / 集成）与 OA、ERP、MES 边界说明。

---

## 一、结论摘要

1. **现状覆盖率极低**：`whale-pdm` 目前只有产品主数据（`pdm_product` 1 张业务表、5 个 CRUD 端点、前端 2 个页面、权限 211–214、菜单 27）。全景 7 大模块 28 个子模块中，除「产品编码档案」外基本为绿地。
2. **不拆微服务**：PDM 保持 `whale-pdm` **模块化单体**（9003、`/pdm/**`、`bluewhale_pdm`、包 `com.bluewhale.pdm`、前端 8106）。内部按限界上下文分包与表前缀隔离，模块间只经应用服务/领域事件协作，禁止跨模块访问 Mapper 与表。
3. **5 个业务上下文 + 1 个集成层**：图文档（Document）、产品结构（PartBom，含零件库）、变更（Change）、工艺（Process）、协同（Collab，交付物与评审）；集成（Integration）是适配层，不是业务上下文。
4. **主链路**：`零件/产品主档 → 图文档与版本 → EBOM/PBOM/MBOM → ECR/ECO/ECN 变更 → 工艺路线/SOP → 发布 ERP/MES → OA 审批回写`。因此先建「图文档 + 版本 + 权限」，再建「零件 + BOM」，最后做变更与工艺，避免版本规则未定时返工。
5. **跨域硬边界**：OA/Workflow 管审批、ERP 管采购/生产物料与库存 BOM、MES 管车间执行与报工、`whale-file` 管文件字节、`whale-pm` 管项目/任务/甘特、`whale-doc` 管通用 Wiki/在线文档、`whale-message` 管通知、`whale-archive` 管归档。
6. **必须先确认的业务规则**：版本号规则、图文档默认可见性与密级、零件与 ERP 物料主数据归属、BOM 发布同步方式、CAD 预览转换方案、PBOM/MBOM 深度、项目归属、MES 下发契约、存储配额（见 §十一 D-PDM-1..10）。未确认前不写死默认值。

---

## 二、现状基线（2026-11-04 实测）

| 维度 | 现状 | 证据 |
|---|---|---|
| 后端代码 | 10 个 Java 文件，单模块分包（`controller / service / mapper / entity / dto / vo / converter`），非 DDD 四层 | `backend/whale-apps/whale-pdm/src/main/java/com/bluewhale/pdm` |
| 端点 | 5 个：`/pdm/product/page`、`/{id}`、`POST`、`PUT /{id}`、`DELETE /{id}` | `PdmProductController` |
| 数据表 | 2 张：`pdm_product`（种子 `PDM-001/002`）、`api_log`；无文件夹、无版本、无权限、无 BOM | `db/migration/V1__baseline.sql` |
| 前端 | `frontend/platform-apps/whale-pdm`（8106、base `/pdm`）：单页 `List` + `Form` 弹窗，无路由 | 目录清单 |
| 权限/菜单 | 权限 `sys:pdm:product:*` 211–214；菜单 27「产品管理」；角色 `pdm_manager`(21101) / `pdm_member`(21102) | `migration-5.60-pdm.sql`、`migration-5.65-business-role-grant.sql` |
| 网关 | `/pdm/**` → `lb://whale-pdm`，不剥离前缀；**`/pdm/internal/**` 尚未加入 internal-deny** | `whale-gateway/src/main/resources/application.yml` |
| 相邻服务 | `whale-erp` 已有 M0–M3 第一阶段；`whale-mes` 仅工单 CRUD；`whale-pm` 已有项目/工作项/迭代/看板/报表；`whale-doc` 已交付 M1–M5；`whale-file` 提供内网内容读取与 Range | 各服务源码 |
| 集成能力 | 无 `whale-file` / `whale-workflow` / `whale-message` / ERP / MES 客户端 | `whale-pdm` 源码 |

**结论**：全景中除「产品档案」外全部缺失；现有 `pdm_product` 也缺少零件类型、材质、规格、计量单位、ERP 物料映射等属性。ERP M3 剩余（MRP）明确依赖 PDM 的 BOM 快照契约，PDM 是 ERP 生产计划的前置依赖。

---

## 三、全景差距矩阵

图例：✅ 已具备；🟡 部分/仅字段；❌ 缺失。

### 3.1 图文档管理

| 子模块 | 能力 | 现状 | 落点（规划） |
|---|---|---|---|
| 文档存储 | CAD 图纸、技术文档、说明书、测试报告统一存储 | ❌ | `document`：`pdm_doc_folder` / `pdm_doc_document` / `pdm_doc_version` |
| 分类管理 | 按产品/项目/文档类型多级文件夹 | ❌ | `pdm_doc_folder`（树 + path） |
| 在线预览 | 不打开 CAD，网页预览 2D/3D | ❌ | `pdm_doc_preview_job` + 转换器（§八） |
| 权限控制 | 文件夹/文件级查看、编辑、下载 | ❌ | `pdm_doc_acl`（继承 + DENY 优先） |
| 版本管理 | 自动存版本、对比、回滚 | 🟡（仅行级更新） | `pdm_doc_version` + 检出/检入锁 |
| 搜索 | 零件号、名称、设计师、日期多维检索 | 🟡（仅编码/名称） | `pdm_doc_document` 索引字段 + 查询条件 |

### 3.2 产品结构管理（BOM）

| 子模块 | 能力 | 现状 | 落点 |
|---|---|---|---|
| BOM 搭建 | 成品 → 部件 → 零件 → 原材料结构树 | ❌ | `pdm_bom` / `pdm_bom_line` |
| 多视图 BOM | EBOM → PBOM → MBOM | ❌ | `pdm_bom.view` + 派生复制 |
| BOM 对比 | 两版本差异（增/删/改） | ❌ | 服务端递归比对 + 异步任务 |
| BOM 导出 | Excel / 同步 ERP | ❌ | 导出端点 + `pdm_bom_release` 快照契约 |
| 零件关联 | 图纸、规格、供应商、库存 | ❌ | `pdm_doc_link` + ERP 只读端口 |

### 3.3 版本与变更管理

| 子模块 | 能力 | 现状 | 落点 |
|---|---|---|---|
| 版本规则 | 正式版/草稿版/修订版编号 | ❌ | `PdmVersionNo` 值对象 + 发布策略 |
| 变更申请（ECR） | 为什么改、改哪里、影响什么 | ❌ | `pdm_chg_request` |
| 变更任务（ECO） | 影响评估、审批流 | ❌ | `pdm_chg_order` + OA/Workflow |
| 变更通知（ECN） | 生效后通知采购/生产/质量 | ❌ | `pdm_chg_notice` + `whale-message` |
| 变更追溯 | 改了几次、每次改了什么 | ❌ | `pdm_chg_trace`（只追加） |
| 生效日期 | 下批生效、非即时 | ❌ | `effective_from` / 定时生效任务 |

### 3.4 零件库与标准件管理

| 子模块 | 能力 | 现状 | 落点 |
|---|---|---|---|
| 标准件库 | 螺栓/螺母/轴承统一管理、直接调用 | ❌ | `pdm_product.part_type=STANDARD` + `pdm_part_category` |
| 通用件库 | 公司内部重复使用零件 | ❌ | `part_type=COMMON` |
| 物料分类 | 材质/规格/用途分类检索 | ❌ | `pdm_part_category` + 属性索引 |
| 零件去重 | 自动识别重复零件 | ❌ | 归一键（名称/材质/规格/尺寸）+ 候选清单 |
| 供应商数据 | 推荐供应商、参考价、采购周期 | ❌ | ERP 只读回传快照字段 |

### 3.5 项目与任务管理

| 子模块 | 能力 | 现状 | 落点 |
|---|---|---|---|
| 研发项目 | 立项、计划、里程碑 | 🟡（`whale-pm` 已有项目） | **归 `whale-pm`**，PDM 只存 `pmProjectId` |
| 任务分配 | 派工、截止时间 | 🟡（`whale-pm` 工作项） | **归 `whale-pm`**，PDM 只存 `pmWorkItemId` |
| 项目看板 | 甘特/进度 | 🟡（`whale-pm` 看板/报表） | **归 `whale-pm`**，PDM 不做甘特 |
| 交付物管理 | 图纸/文档自动归档到 PDM | ❌ | `pdm_clb_deliverable`（引用已发布版本） |
| 评审管理 | 设计/样机/转产评审记录与结论 | ❌ | `pdm_clb_review` + 审批实例快照 |

### 3.6 工艺管理（轻量级）

| 子模块 | 能力 | 现状 | 落点 |
|---|---|---|---|
| 工艺路线 | 零件经过哪些工序 | ❌ | `pdm_prc_route` / `pdm_prc_step` |
| 工时定额 | 每道工序标准工时 | ❌ | `pdm_prc_step.standard_hours` |
| 工装夹具 | 刀具、夹具、模具 | ❌ | `pdm_prc_tooling` |
| 作业指导书 | 每道工序 SOP，下发 MES | ❌ | `pdm_prc_sop` + MES 下发契约 |

### 3.7 集成与接口

| 子模块 | 能力 | 现状 | 落点 |
|---|---|---|---|
| CAD 集成 | SolidWorks / AutoCAD / Pro/E 对接 | ❌ | 检入/检出 + 属性/装配 BOM 导入契约 |
| ERP 同步 | BOM、物料主数据同步 | ❌ | `pdm_bom_release` 快照 + 零件发布 |
| MES 下发 | 工艺路线、SOP 下发 | ❌ | `pdm_prc_publish` + MES 内网接口 |
| OA 对接 | 审批状态双向同步 | ❌ | Workflow 发起 + 内部回调快照 |

---

## 四、战略建模

### 4.1 业务能力与子域分类

| 子域 | 类型 | 判据 |
|---|---|---|
| 图文档与版本（含文件级权限） | **核心域** | 图纸/文档与版本是所有产品数据的载体，规则最复杂、不可替代 |
| 产品结构与 BOM | **核心域** | EBOM 是设计输出的主结构，向 ERP/MES 提供制造依据 |
| 版本与变更（ECR/ECO/ECN） | **核心域** | 变更追溯与生效控制是 PDM 的法定义务与审计重点 |
| 零件库与标准件 | **支撑域** | 复用与标准化，降低物料种类 |
| 工艺管理（轻量） | **支撑域** | 向 MES 输出路线/SOP，复杂排程不在 PDM |
| 项目交付物与评审 | **支撑域** | 项目/任务归 `whale-pm`，PDM 只做交付物与评审记录 |
| CAD/ERP/MES/OA 集成 | **通用域** | 适配与契约，不拥有业务事实 |

### 4.2 限界上下文

| 上下文 | 拥有的事实 | 不拥有 |
|---|---|---|
| **Document 图文档** | 文件夹、文档主档、文档版本、检出/检入锁、文档 ACL、文档与零件/BOM/项目/变更的关联、预览任务 | 文件字节（`whale-file`）、通用 Wiki/在线文档（`whale-doc`） |
| **PartBom 产品结构** | 零件/产品主档（含标准件/通用件）、物料分类、BOM 头与行、EBOM/PBOM/MBOM 视图、BOM 发布快照 | 采购/生产物料主数据与库存（ERP）、车间装配执行（MES） |
| **Change 变更** | ECR、ECO、ECN、影响对象、变更任务、变更追溯、生效计划 | 审批引擎（OA/Workflow）、通知投递（`whale-message`） |
| **Process 工艺** | 工艺路线、工序、工时定额、工装夹具、SOP 文档引用、下发记录 | 工序执行与报工（MES）、工装实物台账（ERP/MES） |
| **Collab 协同** | 项目交付物关联、设计/样机/转产评审记录与结论 | 项目/任务/迭代/看板/甘特（`whale-pm`）、审批（Workflow） |
| **Integration 集成层** | 同步记录、幂等键、重试与对账状态 | 任何业务主数据与业务状态 |

### 4.3 上下文映射

| 上游 | 下游 | 关系类型 | 契约 |
|---|---|---|---|
| PDM Document | `whale-file` | 客户–供应商 | 上传/下载/内网内容读取；PDM 只存 `fileId` + 校验和，不存字节 |
| PDM PartBom | ERP（主数据/生产计划） | 发布语言（Published Language） | 零件发布 → ERP 物料；BOM 发布 → `GET /pdm/internal/bom/{bomNo}/versions/{versionNo}` 快照，按 `bomNo + versionNo` 幂等 |
| ERP | PDM PartBom | 只读回传 | 参考价、采购周期、库存、物料状态；PDM 不落 ERP 交易表 |
| PDM Process | MES | 发布语言 | `POST /mes/internal/process-route` 下发路线/工序/SOP 版本，幂等键 `pdmRouteNo + version` |
| OA / Workflow | PDM Change/Collab | 事件 + 内网 REST | 审批通过/驳回回调，`approvalInstanceId` 幂等；PDM 只存实例号与状态快照 |
| PDM | OA / Workflow | 调用 | ECR/ECO/评审发起审批；PDM 不实现审批引擎 |
| PDM Collab | `whale-pm` | 引用 | 只存 `pmProjectId` / `pmWorkItemId` 与交付物引用，不复制项目/任务 |
| PDM | `whale-message` | 事件 | ECN 生效、变更待办、评审通知；不落投递记录 |
| PDM | `whale-archive` | 调用 | 历史版本/已归档项目资料归档，PDM 保留索引与摘要 |
| PDM | auth / audit | 平台能力 | 租户/权限/身份；写操作 `@AuditLog` |

### 4.4 跨域硬边界（不可协商）

1. PDM **不**保存文件字节，**不**实现通用 Wiki/在线文档协作；文件字节归 `whale-file`，通用文档归 `whale-doc`。
2. PDM **不**承载审批流引擎；ECR/ECO/评审审批以 OA/Workflow 为准，PDM 只存实例号与状态快照。
3. PDM **不**拥有采购/生产物料主数据与库存；ERP 拥有 `erp_item` 与库存，PDM 只发布设计零件并回读价格/周期/库存。
4. PDM **不**执行车间工序、**不**采集报工与设备数据；MES 执行并回传，PDM 只下发路线与 SOP 版本。
5. PDM **不**重建项目/任务/迭代/甘特；项目域归 `whale-pm`，PDM 只维护交付物关联与评审记录。
6. PDM **不**直接读其他服务数据库；跨域只走内网 REST，`/pdm/internal/**` 对外一律 403。
7. PDM **不**拆分为独立微服务；文档版本、BOM 发布、变更生效需要同库本地事务。

---

## 五、战术建模

### 5.1 聚合与不变量

| 上下文 | 聚合根 | 关键不变量 | 主要命令 | 领域事件 |
|---|---|---|---|---|
| Document | `Folder` | 同级名称唯一；父子无环；移动不得移入自身子树；path 与 parent 一致 | 建目录、重命名、移动、删除（空目录） | `FolderMoved` |
| Document | `PdmDocument` | `docNo` 租户内唯一；有效版本唯一；检出期间禁止他人检入；已发布版本不可改 | 建档、挂接文件、检出、检入、发布、作废、授权 | `DocumentReleased`、`DocumentObsoleted` |
| Document | `DocumentVersion` | `(documentId, versionNo)` 唯一；`RELEASED` 后文件/校验和不可变；回滚以旧版本为基线新建草稿 | 创建草稿、上传文件、发布、作废 | `DocumentVersionCreated` |
| Document | `DocumentAcl` | 同一主体同一动作唯一；DENY 优先于 ALLOW；跨租户不可见 | 授权、撤权、继承开关 | `DocumentAclChanged` |
| PartBom | `Part` | `partNo` 租户内唯一；`partType` 与父级约束（装配可含子件，零件不可含子件）；停用不可入新 BOM | 建档、改属性、启停、合并去重 | `PartChanged` |
| PartBom | `Bom` | `(productPartId, view, versionNo)` 唯一；父子无环；用量 > 0；发布后不可改；视图派生不得篡改 EBOM | 建 BOM、编辑行、派生视图、发布、作废 | `BomReleased` |
| PartBom | `BomRelease` | 发布快照不可变；`bomNo + versionNo + checksum` 唯一；重复发布返回既有记录 | 发布、撤销发布（未同步前） | `BomSnapshotPublished` |
| Change | `ChangeRequest` (ECR) | 变更原因/范围/影响必填；仅 `DRAFT` 可编辑；`APPROVED` 才能生成 ECO | 提出、提交、审批回写、关闭 | `ChangeRequested` |
| Change | `ChangeOrder` (ECO) | 影响对象必须至少一项；审批通过前不可执行；执行只允许生成新版本，不覆盖已发布版本 | 评估、提交、审批回写、执行、完成 | `ChangeApproved`、`ChangeExecuted` |
| Change | `ChangeNotice` (ECN) | 必须关联 ECO；生效日期不早于审批通过日；生效后旧版本置 `OBSOLETE` | 生成、通知、生效、关闭 | `ChangeEffective` |
| Process | `ProcessRoute` | 工序序号唯一且连续；标准工时 ≥ 0；SOP 必须引用已发布文档版本；发布后不可改 | 建路线、编辑工序、发布、作废 | `ProcessRouteReleased` |
| Process | `ProcessPublish` | 幂等键 `routeNo + version + target` 唯一；失败可重试；已发布版本再发布生成新记录 | 下发、重试、撤回 | `ProcessPublished` |
| Collab | `ReviewRecord` | 评审结论必填；已关闭不可改；审批实例号幂等 | 建评审、提交、回写、关闭 | `ReviewClosed` |
| Collab | `DeliverableLink` | 只允许关联已发布文档版本/BOM 版本；同一项目同一对象唯一 | 归档、解除 | `DeliverableArchived` |

### 5.2 值对象（共享内核 `common`）

`PartNo`、`DocNo`、`VersionNo(major, minor, stage)`、`Revision`、`BomPath`、`Quantity(value, uom)`、`FileRef(fileId, fileName, size, checksum, contentType)`、`EffectivePeriod(from, to)`、`AclSubject(type, id)`、`Percentage`、`StandardHours`。

约束：版本号解析/比较只走 `VersionNo`，禁止字符串比较；数量统一 `DECIMAL(18,4)`；文件校验和统一 SHA-256；`fileId` 只在服务内部与授权下载票据中出现，不进入公开列表 VO。

### 5.3 状态机

| 对象 | 状态 |
|---|---|
| 文档版本 | `DRAFT → RELEASED → OBSOLETE`；草稿可删除，已发布不可删 |
| 文档检出 | `AVAILABLE → CHECKED_OUT → AVAILABLE`（检入生成新草稿，不直接改已发布版本） |
| BOM | `DRAFT → RELEASED → OBSOLETE`；旁支 `CANCELLED` |
| BOM 发布 | `PENDING → PUBLISHED / FAILED`；失败可重试，不改快照 |
| ECR | `DRAFT → SUBMITTED → APPROVED / REJECTED → CLOSED` |
| ECO | `DRAFT → ASSESSING → APPROVING → APPROVED → EXECUTING → COMPLETED → CLOSED`；旁支 `REJECTED / CANCELLED` |
| ECN | `DRAFT → NOTIFIED → EFFECTIVE → CLOSED`；旁支 `CANCELLED` |
| 工艺路线 | `DRAFT → RELEASED → OBSOLETE`；旁支 `CANCELLED` |
| 工艺下发 | `PENDING → PUBLISHED / FAILED`；支持重试 |
| 评审 | `DRAFT → IN_REVIEW → PASSED / CONDITIONAL / REJECTED → CLOSED` |

### 5.4 异常与错误码

沿用平台 `BizException(code, message)` 与既有段位约定：`400xx` 参数/业务校验、`404xx` 不存在（跨租户一律按不存在处理）、`409xx` 状态冲突。PDM 新增 `PdmErrorCode`：

- 文档：`DOC_NO_CONFLICT`、`DOC_VERSION_CONFLICT`、`DOC_CHECKED_OUT`、`DOC_VERSION_IMMUTABLE`、`DOC_ACL_DENIED`
- 零件/BOM：`PART_NO_CONFLICT`、`PART_IN_USE`、`BOM_CYCLE_DETECTED`、`BOM_LINE_INVALID`、`BOM_RELEASED_IMMUTABLE`
- 变更：`CHANGE_STATE_CONFLICT`、`CHANGE_IMPACT_REQUIRED`、`CHANGE_NOT_APPROVED`、`CHANGE_EFFECTIVE_INVALID`
- 工艺：`PROCESS_STEP_INVALID`、`PROCESS_SOP_NOT_RELEASED`、`PROCESS_PUBLISH_CONFLICT`
- 集成：`SYNC_IDEMPOTENT_CONFLICT`、`SYNC_TARGET_UNAVAILABLE`

禁止把校验失败包成 500；OC 客户端收到业务错误码后应展示可读文案。

---

## 六、数据模型规划

### 6.1 命名与库

- 库：`bluewhale_pdm`（不变）；表前缀 `pdm_`；新表按模块二级前缀：`pdm_doc_*`、`pdm_part_*`、`pdm_bom_*`、`pdm_chg_*`、`pdm_prc_*`、`pdm_clb_*`、`pdm_int_*`。
- **存量例外**：`pdm_product` 不改名（避免破坏 API 与数据），M1 通过 `ALTER TABLE` 扩展属性；`api_log` 保持。
- 公共字段：`id`（雪花）、`tenant_id`、`create_time`、`update_time`、`deleted`；单据另加 `doc_no`、`status`、`version`（乐观锁）。
- 逻辑外键，不建物理外键；跨模块禁止 JOIN 写 SQL。

### 6.2 分期表清单

| 阶段 | 新增/变更表（草案） |
|---|---|
| M0 图文档 | **M0-A/M0-B 已落地**：`pdm_doc_folder`、`pdm_doc_document`、`pdm_doc_version`；`pdm_doc_acl` 已启用（目录继承 + 显式 DENY 优先，`V3` 将 `subject_id` 扩为 `VARCHAR(64)` 以承载角色码）；`pdm_doc_link` 顺延 M1A；扩展现有 `pdm_product`（文档关联字段）亦顺延 M1A |
| M1 零件/BOM | `pdm_part_category`；`ALTER pdm_product`（part_type/material/spec/uom/erp_item_code 等）；`pdm_bom`、`pdm_bom_line`、`pdm_bom_release` |
| M2 变更 | `pdm_chg_request`、`pdm_chg_order`、`pdm_chg_notice`、`pdm_chg_impact`、`pdm_chg_task`、`pdm_chg_trace` |
| M3 工艺 | `pdm_prc_route`、`pdm_prc_step`、`pdm_prc_tooling`、`pdm_prc_sop`、`pdm_prc_publish` |
| M4 协同/预览 | `pdm_clb_review`、`pdm_clb_deliverable`、`pdm_doc_preview_job` |
| M5 集成 | `pdm_int_sync_log`、`pdm_int_mapping`（零件↔ERP 物料、路线↔MES 等） |

> 实际 DDL 在对应里程碑定稿后随 Flyway `V{n}__*.sql` 落地；禁止在 `V1__baseline.sql` 上直接追加（基线保持可重放）。
> 迁移顺序建议：`V2__document_center.sql` → `V3__document_acl_subject.sql` → `V4__part_bom.sql` → `V5__change.sql` → `V6__process.sql` → `V7__collab.sql` → `V8__integration.sql`。

### 6.3 关键索引与数据量假设

- 文档：`(tenant_id, doc_no)` 唯一；`(tenant_id, folder_id, doc_type, status)`；`(tenant_id, owner_id, update_time)`；版本 `(document_id, version_no)` 唯一。
- 权限：`(tenant_id, resource_type, resource_id, subject_type, subject_id, action, effect)` 唯一；目录 ACL 继承按目录 `path` 祖先链查询；`ALLOW` 与 `DENY` 可共存，解析时 `DENY` 优先。
- 零件：`(tenant_id, part_no)` 唯一；`(tenant_id, part_type, category_code, status)`；去重键 `(tenant_id, normalized_name, material, spec)`。
- BOM：`(tenant_id, product_part_id, view, version_no)` 唯一；行 `(bom_id, line_no)` 唯一 + `(bom_id, parent_line_no)`；树查询按 `bom_id`。
- 量级假设（设计参考，不作验收）：零件 5 万、BOM 5 层/单产品 1 万节点、文档 20 万/年、版本 60 万/年、变更单 2 万/年。

---

## 七、接口规划

| 分组 | 路径 | 说明 |
|---|---|---|
| 图文档 | `/pdm/document/page`、`/pdm/document/{id}`、`/pdm/document/{id}/versions`、`/pdm/document/{id}/checkout`、`/checkin`、`/pdm/document/{id}/versions/{versionId}/release`、`/rollback`、`/content`、`/pdm/folder/**`；M0-B 增 `/pdm/acl`（GET/PUT/DELETE，按 `resourceType` 区分 FOLDER/DOCUMENT） | 下载走授权票据/代理，不暴露 `fileId`；版本发布/作废为显式动作 |
| 零件/BOM | `/pdm/part`、`/pdm/part/{id}/where-used`、`/pdm/part/duplicates`、`/pdm/part/category`、`/pdm/bom`、`/pdm/bom/{id}/lines`、`/pdm/bom/{id}/release`、`/pdm/bom/diff`、`/pdm/bom/{id}/export` | BOM 对比/导出为查询型；发布为显式动作，不做通用 PUT 改状态 |
| 变更 | `/pdm/change/ecr`、`/pdm/change/eco`、`/pdm/change/ecn`、`/pdm/change/trace` | 提交/审批回写/执行/生效为动作端点 |
| 工艺 | `/pdm/process/route`、`/pdm/process/route/{id}/release`、`/pdm/process/publish` | 下发/重试/撤回为动作端点 |
| 协同 | `/pdm/review`、`/pdm/deliverable` | 评审结论/关闭；交付物只关联已发布版本 |
| 预览 | `/pdm/preview/jobs`、`/pdm/preview/jobs/{id}` | 异步任务：创建返回 `jobId`，轮询状态 |
| 集成 | `/pdm/sync/log`、`/pdm/sync/retry`、`/pdm/meta` | 同步记录与重试；`/pdm/meta` 返回模块状态与契约版本 |
| 内网 | `/pdm/internal/bom/{bomNo}/versions/{versionNo}`、`/pdm/internal/part/{partNo}`、`/pdm/internal/document/{id}/versions/{versionNo}/content`、`/pdm/internal/change/approval-callback` | 仅服务间；网关 internal-deny 403；系统级调用需内网标记 |

通用契约：
- 响应 `Result<T>` / `PageResult<T>`；分页沿用 `PageQuery`，单页上限 200。
- 写操作 `@RequirePermission` + `@AuditLog`；下载/预览/ACL 变更单独留审计。
- 幂等：版本发布、BOM 发布、审批回调、工艺下发、ERP 同步使用唯一键 + `@Idempotent`。
- 跨域回调校验来源与内网标记；`sourceEventId` / `approvalInstanceId` 唯一索引防重。

---

## 八、架构决策

### 8.1 为什么保持模块化单体

| 选项 | 结论 | 理由 |
|---|---|---|
| 拆成 document/bom/change/process 四个服务 | ❌ | 文档版本、BOM 发布、变更生效存在跨模块事务与一致性要求，拆分会引入分布式事务/对账 |
| 保持一个 `whale-pdm` | ✅ | 单库本地事务保证「文档版本→BOM→变更→发布」一致；模块边界靠包与表前缀隔离 |
| 继续扁平分包 | ❌ | 7 大模块扁平化后无法阻止跨模块耦合，ArchUnit 也守不住 |

### 8.2 包结构（目标态）

```
com.bluewhale.pdm
├── common/                 共享内核：PdmErrorCode、DocNo、PartNo、VersionNo、Quantity、FileRef
├── document/               domain/{model,event,port} application/{service,command,query} infrastructure/{persistence,mapper,client} interfaces/{controller,dto,vo}
├── partbom/                同上（零件、分类、BOM、发布快照）
├── change/                 同上（ECR/ECO/ECN、影响、追溯、生效任务）
├── process/                同上（路线、工序、工装、SOP、下发）
├── collab/                 同上（评审、交付物）
├── integration/            跨域客户端（file/workflow/message/erp/mes）与同步记录
└── infrastructure/         Flyway、定时任务、跨模块技术设施
```

存量 `product` 的 `controller/service/mapper/entity/dto/vo` 保持原包，M1 扩展时保留 `/pdm/product/**` 路径与响应不变；新代码按四层分包。

### 8.3 模块协作规则（ArchUnit 固化）

1. 模块 `domain` 不得依赖 Spring/MyBatis/Feign/Redis。
2. 模块之间只能依赖对方 `application.service` 接口或 `domain.event`；禁止依赖对方 `infrastructure`/`mapper`/`entity`。
3. 控制器不得跨模块直接调用另一个模块的 Mapper；跨模块查询必须经应用服务。
4. 禁止跨模块 SQL JOIN；集成层不得直接访问其他服务数据库。
5. 文档版本发布、BOM 发布、变更生效在同一本地事务内完成写库与关联状态更新；外部通知/同步事务外执行，失败不回滚业务。
6. 所有列表查询必须带 `tenant_id` 条件（租户行插件兜底，手写 SQL 显式声明）。

### 8.4 事务与一致性

- 本地事务：文档检入（新版本 + 锁释放）、BOM 发布（BOM 状态 + 快照 + 同步记录）、变更生效（ECN + 版本置旧 + BOM/文档状态）。
- 外部调用：审批发起、消息通知、ERP/MES 同步在事务外；失败落 `pdm_int_sync_log`，按指数退避重试，可人工重试。
- 批处理：变更生效、预览转换、同步重试使用本地任务表 + 分布式锁（Redisson）保证单实例执行。
- 回调（Workflow）落 `approvalInstanceId` 唯一索引，重复回调返回已处理结果。

### 8.5 前端

- 复用现有 `frontend/platform-apps/whale-pdm`（8106、base `/pdm`），M0 从「单页 List+Form」重构为多路由：`/pdm/document`、`/pdm/product`（已落地）；`/pdm/part`、`/pdm/bom`、`/pdm/change`、`/pdm/process`、`/pdm/review`、`/pdm/sync` 随里程碑追加。
- 不新增 qiankun 子应用；基座 `/pdm` 菜单已拆为 `/pdm/document`（`sys:pdm:doc:list`，菜单 180）与 `/pdm/product`（`sys:pdm:product:list`，原菜单 27）。
- 统一「树 + 列表 + 详情 + 动作条」模式；BOM 树/差异视图使用虚拟滚动；文档预览使用 iframe/PDF.js/模型查看器，**不引入**冻结的 `whale-common-three`（除非满足 F-09 解冻四条件）。

---

## 九、权限与菜单预留

权限号已用至 **455**（ERP MFG），菜单已用至 **86**；ERP 已预留 355–599 / 63–119，MES（Wave 5.79）已预留 1100–1399 / 240–299。PDM 新功能从 **900** 起编，菜单从 **180** 起编，避免与 ERP/MES 预留段冲突：

| 模块 | 权限号段 | 菜单号段 | 说明 |
|---|---|---|---|
| 图文档 | 900–929 | 180–184 | M0-A 已用 900–909 / 菜单 180；M0-B 已用 910–911（ACL 查询/维护，页内弹窗不新增菜单）；912–929 / 181–184 预留预览与导出 |
| 零件/BOM | 930–949 | 185–189 | 零件、分类、标准件、BOM、对比、导出、发布 |
| 变更 | 950–969 | 190–194 | ECR/ECO/ECN、影响、追溯、生效 |
| 工艺 | 970–979 | 195–197 | 路线、工序、工装、SOP、下发 |
| 协同/预览 | 980–989 | 198–201 | 评审、交付物、预览任务 |
| 集成 | 990–999 | 202–203 | 同步记录、重试、契约管理 |
| 预留 | 1000–1099 | 204–219 | 后续增强 |

角色：存量 `pdm_manager`(21101) / `pdm_member`(21102) 继续覆盖 `/pdm/product/**`；新增 `pdm_doc_manager`（21103）、`pdm_engineer`（21104，零件/BOM）、`pdm_change_manager`（21105）、`pdm_process_engineer`（21106）。实施前核对 211xx 最大值（当前至 21102）及 ERP/MES 角色段（251xx / 207xx）。

> 编号纪律：按里程碑逐段启用，**禁止跳段占用**；迁移脚本幂等；已发布脚本不得修改。M0-A 实际使用权限 900–909、菜单 180、角色 21103，对应迁移 `migration-5.80-pdm-doc.sql`；M0-B 使用权限 910–911，对应迁移 `migration-5.80-pdm-acl.sql` 与 Flyway `V3__document_acl_subject.sql`。

---

## 十、非功能与安全

| 主题 | 要求 |
|---|---|
| 性能 | 文档/零件列表 P95 < 300ms；BOM 树 1 万节点首屏 < 1s；BOM 对比 > 2000 行转异步；大文件上传/下载走流式与分片（`whale-file`）；预览任务异步 |
| 并发 | 检出/检入按文档行锁或条件更新；版本号/发布快照唯一索引；变更生效任务单实例锁；BOM 发布与同步记录同事务 |
| 安全 | 租户隔离 + 功能权限 + 文档 ACL（DENY 优先）；下载票据短时有效并绑定用户/文档/版本；公开 VO 不暴露 `fileId` 与存储路径；跨租户 404 |
| 审计 | 上传/下载/预览、检出/检入、版本发布/作废、ACL 变更、BOM 发布、变更提交流转、工艺下发、同步失败全部 `@AuditLog`；审计不记录文件正文与图纸内容 |
| 不可篡改 | 已发布文档版本、已发布 BOM、已生效 ECN、已通过评审禁止更新；只允许新建版本/作废/红冲式留痕 |
| 数据保留 | 历史版本默认保留；归档走 `whale-archive`；删除仅逻辑删除且已发布版本禁止删除 |
| 容量 | 单文件上限与租户配额可配置（D-PDM-10）；预览产物按 TTL 清理；同步日志按保留期清理 |

---

## 十一、开放问题（编码前必须确认）

| 编号 | 问题 | 影响 | 建议默认（待确认） |
|---|---|---|---|
| D-PDM-1 | 研发项目/任务/甘特归 `whale-pm` 还是 PDM 内置 | 是否重复建设、工作量 | **归 `whale-pm`**；PDM 只做交付物关联与评审记录 |
| D-PDM-2 | 零件主数据与 ERP `erp_item` 的归属与同步方向 | 主数据双头、BOM/MRP 口径 | PDM 为设计零件主档；发布后由 ERP 建/更新物料；价格/库存/采购周期由 ERP 只读回传 |
| D-PDM-3 | CAD 在线预览方案与授权 | 预览范围、成本、合规 | 先支持 PDF/图片/Office→PDF 与 STEP→glTF；DWG 默认仅下载，需商业转换器再启用 |
| D-PDM-4 | 版本号规则与发布审批强度 | 版本表结构、变更流程 | 草稿 `V0.n`；首发 `V1.0`；小改 `V1.1`；结构/大改 `V2.0`；发布必须经 ECO 审批（可配置降级） |
| D-PDM-5 | 图文档默认可见性、密级与保留期限 | ACL 默认值、合规 | 根目录默认全员可见；敏感文档显式授权；密级字段预留；历史版本默认保留 |
| D-PDM-6 | BOM 发布与 ERP 同步方式 | 一致性与实现复杂度 | PDM 生成不可变快照；ERP 在 MRP 前按 `bomNo + versionNo` 拉取；同步记录留痕，失败可重试 |
| D-PDM-7 | PBOM/MBOM 是否在 PDM 内维护 | 范围与复杂度 | PDM 维护 EBOM；PBOM/MBOM 为派生只读视图，人工调整需走变更 |
| D-PDM-8 | 工艺路线/SOP 下发 MES 的粒度与接收端 | M3 依赖 | 以「路线版本 + 工序 + SOP 文档版本」整包下发；MES 需新增内网接收与状态查询接口 |
| D-PDM-9 | OA/Workflow 审批定义与回调幂等键 | 变更/评审闭环 | 以 `approvalInstanceId` 为幂等键；审批关闭时降级为人工审批（需权限码） |
| D-PDM-10 | 单文件上限、租户配额、归档策略 | 存储成本与运维 | 单文件 ≤ 2GB；租户配额可配置；历史版本归档到 `whale-archive`，PDM 保留索引 |

> 未确认前，编码只实现不依赖上述决策的硬规则：版本唯一、发布不可变、BOM 无环、ACL 拒绝优先、跨租户 404、审批回调幂等。版本号规则、预览转换器、同步方向一律走配置或日报决策，不写死。

---

## 十二、变更记录

| 日期 | 版本 | 变更 |
|---|---|---|
| 2026-11-04 | v0.1 | 首版：现状基线、7 模块差距矩阵、5 上下文 + 集成层、聚合/状态机/错误码、分期表清单、接口与权限预留、架构决策、开放问题 D-PDM-1..10 |
