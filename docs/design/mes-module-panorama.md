# MES 功能模块全景：领域模型与架构设计（规划稿）

> 状态：**规划（2026-11-04，零代码）**。本稿只定义边界、模型、契约与分期，不承诺最终实现细节。
> 关联：[Wave 5.79 实施计划](../plans/2026-11-04-wave5.79-mes-panorama.md)、[需求登记 REQ-FUNC-057..063 / REQ-ARCH-021 / REQ-SEC-031](../requirements/register.md)、[服务清单](../service-catalog.md)。
> 输入：用户提供的《MES 功能模块全景》（生产调度与派工 / 生产执行 / 车间质量 / 设备管理 / 物料与追溯 / 数据采集 / 人员与绩效）及 ERP、OA 边界说明。

---

## 一、结论摘要

1. **现状只是占位 CRUD**：`whale-mes` 当前仅有 `mes_work_order` 一张业务表、5 个工单 CRUD 端点、`ENABLED/DISABLED` 两个状态字段；七大模块其余能力均为绿地。
2. **保持单服务、单库、单部署单元**：`whale-mes` **不拆微服务**，继续使用 **9002 / `/mes/**` / `bluewhale_mes` / 包 `com.bluewhale.mes` / 前端 8105**；内部按限界上下文分包，模块间只经应用服务或领域事件协作，禁止跨上下文访问 Mapper 与表。
3. **7 个业务上下文 + 1 个共享车间主数据**：调度派工（Dispatch）、生产执行（Execution）、物料追溯（Trace）、车间质量（Quality）、设备（Equipment）、数据采集（Collect）、人员绩效（Workforce）；`masterdata` 作为车间主数据共享内核，`common` 放共享值对象、错误码与集成发件箱。
4. **模块编号 ≠ 交付顺序**：先做“能接收工单、能派工、能报工、能看进度”的 M0+M1 核心闭环，再依次补物料追溯、质量、设备采集、人员绩效。质量、OEE、绩效都依赖报工数据，不能先于执行层铺开。
5. **与 ERP 的硬边界**：ERP 出“月度计划/生产订单/总库存/质量成本/设备资产”，MES 拆“今天哪个机台干什么活/工序报工/线边仓/现场检验/设备运行维护/实际工时”。MES 不生成财务凭证，只回传完工、消耗与检验结果。
6. **与 PDM/OA 的硬边界**：BOM 与工艺路线归 PDM，MES 只存执行版本快照；审批归 OA/Workflow，MES 只发起与接收审批结果。PDM 未就绪前，M0 支持手工维护工艺路线快照作为过渡。
7. **首切片建议 M0 车间主数据 + M1 调度与执行**：没有车间、产线、工序、班次与真实工单状态，后续质量、设备、追溯都无处落点。

---

## 二、现状基线（2026-11-04 实测）

| 维度 | 现状 | 证据 |
|---|---|---|
| 后端代码 | 10 个主代码 Java 文件，单层分包（`controller / service / mapper / entity / dto / vo / converter`），非 DDD 四层 | `backend/whale-apps/whale-mes/src/main/java/com/bluewhale/mes` |
| 端点 | 5 个：`GET /mes/work-order/page`、`GET /{id}`、`POST`、`PUT /{id}`、`DELETE /{id}` | `MesWorkOrderController` |
| 数据表 | 2 张：`mes_work_order` + `api_log`；无车间、产线、工序、报工、物料、质量、设备表 | `db/migration/V1__baseline.sql` |
| 种子数据 | `MES-001`（ENABLED）、`MES-002`（DISABLED） | 同上 |
| 工单模型 | 仅 `code / name / status / remark`；`status` 是启停标记，不是执行生命周期 | `MesWorkOrder` |
| 前端 | `frontend/platform-apps/whale-mes`（8105、base `/mes`）：`App.tsx` + 列表 + 表单弹窗 + API 封装，无路由级模块页面 | 目录清单 |
| 权限 | `sys:mes:workorder:list/add/edit/delete` 207–210 | `migration-5.59-mes.sql` |
| 菜单 | 26 工单管理 | 同上 |
| 角色 | `mes_manager`（20701）、`mes_member`（20702） | `migration-5.65-business-role-grant.sql` |
| 网关 | `/mes/**` → `lb://whale-mes`，不剥离前缀；已配路由 | `whale-gateway/src/main/resources/application.yml` |
| 集成 | 无 `/mes/internal/**`；ERP 生产订单未下发 MES；MES 未回传完工 | ERP M3 检查报告、MES 源码 |

**结论**：全景 7 大模块 35 个子模块中，只有“工单 CRUD”一个占位点；工单状态、车间主数据、派工、报工、物料、质量、设备、采集、绩效全部缺失。现有表可以升级为执行工单主表，但不能承载全景。

---

## 三、全景差距矩阵

图例：✅ 已具备；🟡 部分/仅占位；❌ 缺失。

### 3.1 生产调度与派工

| 子模块 | 能力 | 现状 | 规划落点 |
|---|---|---|---|
| 工单接收 | 从 ERP 自动接收生产工单，拆成车间任务 | ❌ | `dispatch`：`/mes/internal/work-order` + `mes_work_order` 升级 + `mes_dp_work_order_task` |
| 派工管理 | 派到产线/班组/机台，支持手动调整 | ❌ | `dispatch`：`mes_dp_dispatch_order` / `mes_dp_dispatch_line` |
| 排程优化 | 有限产能排程、插单、急单优先 | ❌ | `dispatch`：`mes_dp_schedule_plan` / `mes_dp_schedule_slot`；MVP 按工作中心+班次产能 |
| 进度跟踪 | 工序完成状态、在制品数量 | ❌ | `execution`：`mes_ex_wip_snapshot` + 实时查询 |
| 看板管理 | 今日目标、完成数、异常数、达成率 | ❌ | `dispatch`/`collect`：`mes_cl_board_config` + 只读看板接口 |

### 3.2 生产执行

| 子模块 | 能力 | 现状 | 规划落点 |
|---|---|---|---|
| 工单执行 | 开工、暂停、完工、转序、报废 | 🟡 仅启停字段 | `execution`：任务级状态机 + 显式动作端点 |
| 工序报工 | 扫码报工：做了多少、合格多少、不良多少 | ❌ | `execution`：`mes_ex_production_report` / `mes_ex_report_defect` |
| 领料管理 | 领料、退料、补料，与库存联动 | ❌ | `execution`：`mes_ex_material_issue` / `mes_ex_material_return`；联动见 `trace` 线边仓 |
| 在制品管理 | 每工序在制品、卡在哪 | ❌ | `execution`：`mes_ex_wip_snapshot` |
| 异常处理 | 设备故障、物料短缺、质量异常上报流转 | ❌ | `execution`：`mes_ex_exception` / `mes_ex_exception_action`；审批走 OA/Workflow |

### 3.3 车间质量

| 子模块 | 能力 | 现状 | 规划落点 |
|---|---|---|---|
| 首件检验 | 每班/每批次首件合格才批量生产 | ❌ | `quality`：`mes_qc_inspection_plan` / `mes_qc_inspection_task` |
| 过程巡检 | IPQC 按频次巡检并记录 | ❌ | `quality`：巡检任务 + 检验结果 |
| 不良品管理 | 记录、隔离、返工/返修/报废 | ❌ | `quality`：`mes_qc_ncr` / `mes_qc_disposition` / `mes_qc_rework_order` |
| SPC 统计 | 关键工序控制图，实时监控稳定性 | ❌ | `quality`：`mes_qc_spc_sample` / `mes_qc_spc_rule` |
| 质量追溯 | 正反向追溯：批次、人、设备、参数 | ❌ | `quality` + `trace`：`mes_tr_trace_link` + 追溯查询接口 |

### 3.4 设备管理

| 子模块 | 能力 | 现状 | 规划落点 |
|---|---|---|---|
| 设备台账 | 档案、参数、保养周期、维修历史 | ❌ | `equipment`：`mes_eq_equipment` |
| 设备状态监控 | 运行/待机/故障，OEE 计算 | ❌ | `equipment`：`mes_eq_status_event` / `mes_eq_downtime` / `mes_eq_oee_snapshot` |
| 保养计划 | 提醒、记录、验收 | ❌ | `equipment`：`mes_eq_maintenance_plan` / `mes_eq_maintenance_order` |
| 维修管理 | 报修、派工、记录、停机统计 | ❌ | `equipment`：`mes_eq_repair_order` + 停机事件 |
| 备件管理 | 关键备件库存、领用记录 | ❌ | `equipment`：`mes_eq_spare_part` / `mes_eq_spare_part_stock` / `mes_eq_spare_part_movement` |

### 3.5 物料与追溯

| 子模块 | 能力 | 现状 | 规划落点 |
|---|---|---|---|
| 条码/RFID | 条码打印、扫码识别、批次绑定 | ✅ M2（Code128/QR 规则生成 + 作废） | `trace`：`mes_tr_barcode` / `mes_tr_barcode_rule` |
| 批次追溯 | 原材料→半成品→成品正反追溯 | ✅ M2（批次建档/封批 + 谱系建链 + 正反逐层追溯） | `trace`：`mes_tr_batch` / `mes_tr_trace_link` |
| 物料防错 | 扫码校验，上错料报警 | ✅ M2（BLOCK 阻断 / WARN 告警 + 扫码幂等） | `trace`：`mes_tr_scan_rule` + 执行侧扫码校验 |
| 线边仓管理 | 线边小仓、实时扣减、缺料叫料 | ✅ M2（线边出入库 + 流水 + 叫料幂等） | `trace`：`mes_tr_line_side_stock` / `mes_tr_line_side_movement` / `mes_tr_replenishment` |

### 3.6 数据采集

| 子模块 | 能力 | 现状 | 规划落点 |
|---|---|---|---|
| 设备联网 | 对接 PLC、传感器、数控机床 | ❌ | `collect`：`mes_cl_device` / `mes_cl_collection_point`；首期网关推送，MES 不直连 PLC |
| 人工报工终端 | 触摸屏/平板/扫码枪自助报工 | ❌ | `execution` + `masterdata`：工位/终端绑定 + 扫码报工 |
| 数据看板 | 产量、节拍、不良率、OEE 大屏 | ❌ | `collect`：看板配置 + 只读聚合接口 |
| 数据归档 | 采集数据存储、历史查询、趋势 | ❌ | `collect`：`mes_cl_metric_sample` + `mes_cl_archive_policy` |

### 3.7 人员与绩效

| 子模块 | 能力 | 现状 | 规划落点 |
|---|---|---|---|
| 班组管理 | 排班、交接班记录 | ❌ | `workforce`：`mes_wf_team_member` / `mes_wf_shift_assignment` / `mes_wf_handover` |
| 工人绩效 | 个人产量、合格率、效率 | ❌ | `workforce`：`mes_wf_performance_stat` |
| 工时管理 | 实际工时采集、标准工时对比 | ❌ | `workforce`：`mes_wf_work_hour` + 标准工时来自工艺路线快照 |

---

## 四、战略建模

### 4.1 子域分类

| 子域 | 类型 | 判据 |
|---|---|---|
| 生产执行（报工、在制品、异常） | **核心域** | 车间现场事实的唯一来源，直接决定进度、成本与追溯 |
| 调度派工（工单拆分、派工、排程） | **核心域** | 把 ERP 计划转成可执行任务，是 MES 的入口 |
| 物料追溯（批次、条码、线边仓、正反追溯） | **核心域** | 质量追溯与物料防错的底座，不可由 ERP 总库存替代 |
| 车间质量（首件、IPQC、不良品、SPC） | **支撑域** | 与 ERP 质量成本分工，MES 只做现场采集与响应 |
| 设备（台账、状态、保养、维修、备件） | **支撑域** | 与 ERP 固定资产分工，MES 只做运行与维护执行 |
| 数据采集（设备接入、指标、看板、归档） | **通用域** | 为执行、质量、设备提供数据通道，不拥有业务事实 |
| 人员绩效（班组、排班、工时、绩效） | **支撑域** | 与 OA/ERP HR 分工，MES 只做实际工时与绩效计算 |
| 车间主数据（车间、产线、工作中心、工位、班组、班次、工序） | **支撑域/共享内核** | 被多上下文共享，本身不产生业务价值 |

### 4.2 限界上下文

| 上下文 | 拥有的事实 | 不拥有 |
|---|---|---|
| **masterdata 车间主数据** | 车间、产线、工作中心、工位、班组、班次与班历、工序、工艺路线快照、缺陷代码、异常/停机原因 | BOM/工艺主数据（PDM）、员工档案（ERP HR/OA）、生产计划（ERP） |
| **dispatch 调度派工** | 工单执行快照、拆分任务、派工单、排程计划与产能占用 | ERP 生产订单主数据、工艺路线定义 |
| **execution 生产执行** | 任务执行状态、报工、领料/退料/补料、在制品快照、异常事件 | 工单计划量（来自 dispatch 快照）、总库存（ERP）、工资（ERP HR） |
| **trace 物料追溯** | 物料批次、条码、批次谱系、线边仓库存与流水、扫码校验规则、叫料请求 | ERP 总库存与采购批次主数据、供应商主数据 |
| **quality 车间质量** | 首件/IPQC 检验计划与任务、检验结果、不良品、处置、返工单、SPC 样本与规则 | IQC/FQC 与质量成本（ERP M5）、审批流（OA/Workflow） |
| **equipment 设备** | 生产设备台账、状态事件、停机、保养计划/工单、维修工单、备件库存与领用、OEE 快照 | 固定资产折旧与资产卡片（ERP）、备件采购（ERP/SRM） |
| **collect 数据采集** | 设备/采集点注册、指标样本、看板配置、归档策略 | 设备控制写操作（PLC/SCADA）、业务事实 |
| **workforce 人员绩效** | 班组成员、排班、交接班、实际工时、绩效统计、技能矩阵 | 员工主档、请假/加班审批（OA/ERP HR）、薪资（ERP HR） |
| **common 共享内核** | 数量/时间/单号值对象、错误码、集成发件箱、幂等键 | 任何业务事实 |

### 4.3 上下文映射

| 上游 | 下游 | 关系类型 | 契约 |
|---|---|---|---|
| PDM | MES（masterdata/dispatch） | 供应商–客户（Conformist） | 发布 BOM/工艺路线版本 → MES 落 `mes_md_routing_snapshot` 只读快照，按版本号幂等；PDM 未就绪时 M0 手工维护 |
| ERP | MES（dispatch） | 客户–供应商（Published Language） | `POST /mes/internal/work-order` 下发生产订单；幂等键 `erpProductionOrderNo` |
| MES | ERP | 事件 + 内网 REST | 完工/报废/领料消耗/检验结果回传；`sourceEventId` 幂等，重复不重复记账 |
| ERP | MES（trace） | 调用 | 线边仓补货走 ERP 库存调拨（`/erp/scm/transfer`），MES 只记线边流水与叫料请求 |
| MES | OA / Workflow | 调用 + 回调 | 异常升级、超领、NCR 让步/报废审批；`businessKey` 幂等，回写审批状态快照 |
| MES | PDM | 事件/只读 | 标准工时、工序参数从工艺路线快照取；不反向写 PDM |
| MES | `whale-file` | 调用 | 检验图片、维修照片、附件字节；MES 只存 `fileId` |
| MES | `whale-message` | 事件 | 缺料、设备故障、质量异常、保养到期通知；同对象同事件去重 |
| MES | auth / audit | 平台能力 | 租户/权限/身份；状态流转、质量处置、维修、追溯查询写 `@AuditLog` |

### 4.4 跨域硬边界（不可协商）

1. MES **不**保存 ERP 生产订单主数据与总库存；只保存执行快照、线边仓与消耗记录。
2. MES **不**保存 BOM/工艺路线主数据；只保存版本快照，BOM 变更必须由 PDM 发布。
3. MES **不**承载审批流引擎；超领、让步、报废、异常升级以 OA/Workflow 结果为准。
4. MES **不**保存员工主档与薪资；只引用 `employeeId`/`userId`，绩效只算车间口径。
5. MES **不**生成财务凭证；完工、消耗、报废回传 ERP，由 ERP 记账。
6. MES **不**直接读写其他服务数据库；跨域只走内网 REST 或事件。
7. MES **不**向 PLC/设备下发控制指令；设备接入只读采集，写控制归 SCADA/设备系统。

---

## 五、战术建模

### 5.1 聚合与不变量

| 上下文 | 聚合根 | 关键不变量 | 主要命令 | 领域事件 |
|---|---|---|---|---|
| masterdata | `Workshop`/`ProductionLine`/`WorkCenter` | 编码租户内唯一；产线必须挂车间；停用后不可派工 | 建档、启停、调整 | `WorkCenterChanged` |
| masterdata | `RoutingSnapshot` | 版本号唯一；工序顺序不重复；发布后不可改，只能新版本 | 发布、快照、停用 | `RoutingPublished` |
| masterdata | `ShiftCalendar` | 班次时间不重叠；工作日/停机日明确 | 维护班历、停线 | `ShiftCalendarChanged` |
| dispatch | `WorkOrder` | `erpProductionOrderNo` 租户内唯一；已派工不可改计划量；已完工不可取消 | 接收、拆分、派工、取消 | `WorkOrderReceived`、`WorkOrderDispatched` |
| dispatch | `WorkOrderTask` | 拆分数量之和 = 工单计划量；派工量 ≤ 任务量；状态不可跳转 | 拆分、调整、派工、转序 | `TaskDispatched` |
| dispatch | `SchedulePlan` | 同一工作中心同一时段不超产能（MVP 软约束可人工覆盖并留痕） | 排程、插单、锁定、重排 | `SchedulePlanned` |
| execution | `OperationExecution` | 合格数 + 不良数 = 报工数；累计报工 ≤ 派工量；暂停必须成对恢复 | 开工、暂停、恢复、报工、完工、报废 | `TaskStarted`、`ReportSubmitted` |
| execution | `MaterialIssue` | 领料量 ≤ 定额（超领需审批）；退料 ≤ 已领；补料需原因 | 领料、退料、补料 | `MaterialIssued` |
| execution | `WipSnapshot` | 在制品 = 已派工 − 已完工 − 已报废；不得为负 | 快照、查询 | `WipChanged` |
| execution | `ExceptionEvent` | 必须绑定工单/任务/设备/物料之一；关闭需处理结论 | 上报、响应、升级、关闭 | `ExceptionRaised`、`ExceptionResolved` |
| trace | `MaterialBatch` | 批次号租户内唯一；批次谱系无环 | 建档、绑定、封批 | `BatchCreated` |
| trace | `Barcode` | 条码值唯一；一个条码只绑定一个批次/对象 | 生成、打印、作废、扫描 | `BarcodeScanned` |
| trace | `LineSideStock` | 线边库存 ≥ 0；流水只追加；补货请求幂等 | 入库、扣减、退料、叫料 | `LineSideReplenished` |
| trace | `TraceLink` | 正反向可还原；消耗量 ≤ 投入量；链接不可改只能追加冲销 | 建链、冲销、追溯 | `TraceLinked` |
| quality | `InspectionTask` | 检验项未完成不可判定；首件未通过阻断批量释放 | 建任务、录入、判定 | `InspectionPassed`、`InspectionFailed` |
| quality | `NonconformingRecord` | `FAILED` 必生成 NCR；处置数量 = NCR 数量；关闭需结论 | 建 NCR、处置、返工、关闭 | `NcrCreated`、`NcrDisposed` |
| quality | `SpcChart` | 样本必须落在有效检验项；规则命中必须留痕 | 采样、判定、告警 | `SpcRuleTriggered` |
| equipment | `Equipment` | 编码唯一；故障状态不可开工；保养到期提醒 | 建档、启停、状态变更 | `EquipmentStatusChanged` |
| equipment | `MaintenanceOrder` | 保养项必须全部有结论才可验收；到期未保养告警 | 计划、执行、验收 | `MaintenanceCompleted` |
| equipment | `RepairOrder` | 报修必须关联设备；维修完成需验证；停机时长可计算 | 报修、派工、维修、验收 | `RepairClosed` |
| equipment | `SparePartStock` | 库存 ≥ 0；领用 ≤ 库存；流水只追加 | 入库、领用、退回、盘点 | `SparePartIssued` |
| collect | `CollectionPoint` | 采集点编码唯一；必须绑定设备/工序；样本时间可排序 | 注册、启停、采样 | `MetricSampled` |
| workforce | `ShiftAssignment` | 同工位同班次仅一名主操作员；跨班交接必须留记录 | 排班、调班、交接 | `ShiftHandedOver` |
| workforce | `PerformanceStat` | 只从报工/工时汇总；月度锁定后不可改 | 汇总、锁定、重算（受控） | `PerformanceLocked` |

### 5.2 值对象（共享内核 `common`）

`Quantity(value, uom)`、`TimeRange(start, end)`、`WorkCenterRef(code, name)`、`ShiftCode`、`RoutingVersion`、`BatchNo`、`BarcodeValue`、`DefectCode`、`ReasonCode`、`InspectionResult`、`OeeTriple(availability, performance, quality)`、`DocumentNo`、`SourceEventId`。

约束：数量一律 `DECIMAL(18,4)`；比率 `DECIMAL(9,6)`；禁止 `double`；单号统一由 `DocumentNo` 生成；时间统一 `Asia/Shanghai`，存储 `DATETIME`。

### 5.3 状态机

| 对象 | 状态 |
|---|---|
| 执行工单 | `RECEIVED → DISPATCHED → IN_PROGRESS → PAUSED → COMPLETED → CLOSED`；旁支 `CANCELLED`（仅完工前） |
| 工序任务 | `PLANNED → DISPATCHED → IN_PROGRESS ⇄ PAUSED → COMPLETED`；旁支 `SCRAPPED`（M1 实际口径，取代草案 `PENDING/READY/CLOSED`） |
| 报工记录 | `SUBMITTED → CONFIRMED / REJECTED`；确认后只可追加冲销，不可改历史 |
| 异常事件 | `OPEN → ACKNOWLEDGED → RESOLVED → CLOSED`（M1 最小集；`PROCESSING`/`ESCALATED` 留待 M2 起增强） |
| 检验任务 | `PENDING → IN_PROGRESS → PASSED / FAILED → DISPOSED` |
| 不良品 NCR | `OPEN → DISPOSITION → REWORK / REPAIR / SCRAP / CONCESSION → CLOSED` |
| 设备状态 | `RUNNING / IDLE / FAULT / MAINTENANCE / OFFLINE`（事件流，不覆盖历史） |
| 保养工单 | `PLANNED → DUE → IN_PROGRESS → COMPLETED → ACCEPTED`；旁支 `OVERDUE` |
| 维修工单 | `REPORTED → ASSIGNED → REPAIRING → VERIFIED → CLOSED` |
| 线边库存 | 无状态字段；由追加流水汇总，余额 ≥ 0 |
| 物料批次（M2） | `ACTIVE → CLOSED`；仅 `ACTIVE` 父批次可继续被消耗 |
| 物料条码（M2） | `ACTIVE → VOID`；一码绑定一个批次，`VOID` 后扫码阻断 |
| 谱系链接（M2） | `ACTIVE → REVERSED`；只追加建链，M2 仅开放 `ACTIVE` |
| 补货请求（M2） | `REQUESTED → CONFIRMED / CANCELLED`；按 `request_no` 幂等 |

### 5.4 异常与错误码

沿用“上下文段 + 序号”：`MES-1xxx` 主数据、`MES-2xxx` 调度、`MES-3xxx` 执行、`MES-4xxx` 物料追溯、`MES-5xxx` 质量、`MES-6xxx` 设备、`MES-7xxx` 采集、`MES-8xxx` 人员绩效。业务错误统一 `BizException(code, message)`，禁止把校验失败包成 500。

典型错误：`WORK_ORDER_ALREADY_DISPATCHED`、`TASK_QUANTITY_EXCEEDED`、`REPORT_QUANTITY_EXCEEDED`、`MATERIAL_ISSUE_EXCEEDED`、`WIP_NEGATIVE_FORBIDDEN`、`SCAN_MISMATCH`、`BATCH_TRACE_BROKEN`、`FIRST_ARTICLE_NOT_PASSED`、`NCR_DISPOSITION_REQUIRED`、`EQUIPMENT_FAULT_LOCKED`、`SPARE_PART_STOCK_SHORT`、`METRIC_POINT_DUPLICATED`。

---

## 六、数据模型规划

### 6.1 命名与库

- 库：`bluewhale_mes`（不变）；表前缀 `mes_`；新表按上下文二级前缀：`mes_md_*`、`mes_dp_*`、`mes_ex_*`、`mes_tr_*`、`mes_qc_*`、`mes_eq_*`、`mes_cl_*`、`mes_wf_*`。
- **存量例外**：`mes_work_order` 不改名，升级为执行工单主表；`api_log` 保持。
- 公共字段：`id`（雪花）、`tenant_id`、`create_time`、`update_time`、`deleted`；业务单据另加 `doc_no`、`doc_date`、`status`、`version`（乐观锁）。
- 逻辑外键，不建物理外键；跨上下文禁止 JOIN 写 SQL，看板只读聚合例外。

### 6.2 分期表清单（草案）

| 阶段 | 迁移 | 关键新增表 |
|---|---|---|
| M0 车间主数据 | `V2__mes_foundation.sql`、`V3__mes_shift_calendar.sql` | `mes_md_workshop`、`mes_md_line`、`mes_md_work_center`、`mes_md_station`、`mes_md_team`、`mes_md_shift`、`mes_md_shift_calendar`、`mes_md_operation`、`mes_md_routing_snapshot`、`mes_md_routing_operation`、`mes_md_defect_code`、`mes_md_reason_code`、`mes_common_outbox`；`ALTER mes_work_order` 补 `erp_order_no`、`item_code`、`planned_quantity`、`routing_version`、`priority`、`planned_start/end`、`version`（`shift_calendar` 独立放在 V3，避免已执行的 V2 触发 Flyway 校验和变更） |
| M1 调度与执行 | `V4__mes_dispatch_execution.sql` | `mes_dp_work_order_task`、`mes_dp_dispatch_order`、`mes_dp_dispatch_line`、`mes_dp_schedule_plan`、`mes_dp_schedule_slot`、`mes_ex_production_report`、`mes_ex_report_defect`、`mes_ex_material_issue`、`mes_ex_material_issue_line`、`mes_ex_material_return`、`mes_ex_wip_snapshot`、`mes_ex_exception`、`mes_ex_exception_action` |
| M2 物料与追溯 | `V5__mes_trace.sql` | `mes_tr_batch`、`mes_tr_barcode`、`mes_tr_barcode_rule`、`mes_tr_trace_link`、`mes_tr_line_side_stock`、`mes_tr_line_side_movement`、`mes_tr_scan_rule`、`mes_tr_replenishment` |
| M3 车间质量 | `V6__mes_quality.sql` | `mes_qc_inspection_plan`、`mes_qc_inspection_plan_item`、`mes_qc_inspection_task`、`mes_qc_inspection_result`、`mes_qc_inspection_defect`、`mes_qc_ncr`、`mes_qc_disposition`、`mes_qc_rework_order`、`mes_qc_spc_sample`、`mes_qc_spc_rule` |
| M4 设备与采集 | `V7__mes_equipment_collect.sql` | `mes_eq_equipment`、`mes_eq_status_event`、`mes_eq_downtime`、`mes_eq_maintenance_plan`、`mes_eq_maintenance_order`、`mes_eq_repair_order`、`mes_eq_spare_part`、`mes_eq_spare_part_stock`、`mes_eq_spare_part_movement`、`mes_eq_oee_snapshot`、`mes_cl_device`、`mes_cl_collection_point`、`mes_cl_metric_sample`、`mes_cl_board_config`、`mes_cl_archive_policy` |
| M5 人员绩效与收口 | `V8__mes_workforce.sql` | `mes_wf_team_member`、`mes_wf_shift_assignment`、`mes_wf_handover`、`mes_wf_work_hour`、`mes_wf_performance_stat`、`mes_wf_skill_matrix` |

> 表中为草案，实际 DDL 在对应里程碑定稿后随 Flyway 落地；**禁止修改 V1 基线**，已发布脚本不可再改。合计约 **65 张新增表 + 1 张存量表升级**，以里程碑实际为准。
>
> M0 已落地 15 张表（含 `mes_work_order` 升级）；M1 已落地 13 张表；M2 已落地 8 张 `mes_tr_*` 表，空库累计 **36 张表**（不含 `flyway_schema_history`）。M1 的 `mes_dp_schedule_plan`、`mes_dp_schedule_slot` 仅建表，排程服务与排程页面顺延 M1-B。

### 6.3 关键索引与数据量假设

- 主数据：`(tenant_id, code)` 唯一；`(tenant_id, line_id, status)` 支撑派工选择。
- 工单/任务：`(tenant_id, erp_order_no)` 唯一；`(tenant_id, task_no)` 唯一；`(tenant_id, work_center_id, planned_start)` 支撑排程。
- 报工：`(tenant_id, report_no)` 唯一；`(tenant_id, task_id, reported_at)` 支撑进度与绩效汇总。
- 条码/批次：`(tenant_id, barcode)` 唯一；`(tenant_id, batch_no)` 唯一；`(tenant_id, parent_batch_id, child_batch_id)` 支撑谱系。
- 设备/采集：`(tenant_id, equipment_id, occurred_at)`；`(tenant_id, point_id, sampled_at)`；样本表按月归档/分区。
- 绩效：`(tenant_id, employee_id, work_date)` 唯一；月度统计按 `(tenant_id, period_code, team_id)` 汇总。
- 发件箱：`(status, next_retry_at)`；`(tenant_id, source_event_id)` 唯一。
- 量级假设（用于设计，不作为验收）：工单 5 万/年、任务 20 万/年、报工 200 万/年、条码 1000 万/年、采集点 100 个 × 1 分钟粒度 ≈ 14 万样本/日、谱系链接 1000 万/年。

---

## 七、接口规划

| 分组 | 路径 | 说明 |
|---|---|---|
| 车间主数据 | `/mes/master-data/workshop`、`/line`、`/work-center`、`/station`、`/team`、`/shift`、`/calendar`、`/operation`、`/routing`、`/defect-code`、`/reason-code` | 工艺路线快照只读发布；手工维护仅 M0 过渡 |
| 调度派工 | `/mes/dispatch/work-order`、`/task`、`/dispatch`、`/schedule`、`/board` | 工单接收后拆任务、派工、插单、锁定与看板；动作端点显式 |
| 生产执行 | `/mes/execution/task/{id}/start`、`/pause`、`/resume`、`/complete`、`/scrap`；`/report`、`/material-issue`、`/material-return`、`/wip`、`/exception` | 报工追加式；修正走冲销/补录，不直接改历史 |
| 物料追溯 | `/mes/trace/barcode`、`/batch`、`/line-side/stock`、`/line-side/replenish`、`/scan/validate`、`/trace/forward`、`/trace/backward` | 条码生成/打印返回打印载荷；打印硬件由现场集成 |
| 车间质量 | `/mes/quality/inspection-plan`、`/inspection/first-article`、`/inspection/ipqc`、`/ncr`、`/disposition`、`/rework`、`/spc` | 首件未通过阻断批量释放；NCR 处置数量强校验 |
| 设备 | `/mes/equipment`、`/status`、`/maintenance`、`/repair`、`/spare-part`、`/oee` | 状态事件只追加；OEE 按班次/设备快照 |
| 数据采集 | `/mes/collect/device`、`/point`、`/sample`、`/board`、`/archive` | 看板只读；样本入口支持网关批量上报 |
| 人员绩效 | `/mes/workforce/team-member`、`/shift-assignment`、`/handover`、`/work-hour`、`/performance` | 绩效只读汇总为主；月度锁定动作显式 |
| 兼容入口 | `/mes/work-order/**` | 存量 5 端点保留路径与响应信封，M0 起映射到新工单模型；前端同步迁移到 `/mes/dispatch/work-order` |
| 内网 | `/mes/internal/work-order`、`/mes/internal/collect/{pointCode}` | 仅服务间；网关 `internal-deny` 403；ERP 下发与网关采集 |

通用契约：

- 响应 `Result<T>` / `PageResult<T>`，分页沿用 `PageQuery`；写操作 `@RequirePermission` + `@AuditLog`。
- 幂等：`@Idempotent` 用于 ERP 工单接收、报工提交、领料扣减、线边补货、采集样本、完工回传。
- 跨域回调校验内网标记/签名，`sourceEventId` 唯一索引防重；重复请求返回已处理结果。
- 状态变更一律显式动作端点，禁止通用 `PUT` 直接改 `status`。

---

## 八、架构决策

### 8.1 为什么保持单服务

| 选项 | 结论 | 理由 |
|---|---|---|
| 拆成 dispatch/execution/quality/equipment/trace/collect/workforce 七个服务 | ❌ | 报工→线边扣减→谱系→质量→OEE 在一个现场事务内完成，拆服务会引入分布式事务与对账 |
| 保持一个 `whale-mes`，内部按上下文分包 | ✅ | 单库本地事务保证现场闭环；包与表前缀隔离，ArchUnit 固化边界 |
| 继续扁平 `controller/service/mapper` | ❌ | 35 个子模块扁平化后无法阻止跨模块耦合，后续无法维护 |

### 8.2 包结构（目标态）

```
com.bluewhale.mes
├── common/                 共享内核：Quantity、TimeRange、DocumentNo、MES 错误码、Outbox、幂等键
├── masterdata/             车间/产线/工作中心/工位/班组/班次/工序/工艺路线快照
├── dispatch/               工单接收、拆分、派工、排程、看板
├── execution/              任务执行、报工、领料/退料、在制品、异常
├── trace/                  批次、条码、谱系、线边仓、扫码校验、叫料
├── quality/                首件/IPQC、不良品、处置、返工、SPC
├── equipment/              设备台账、状态、保养、维修、备件、OEE
├── collect/                设备/采集点、指标样本、看板、归档
├── workforce/              班组、排班、交接班、工时、绩效
└── infrastructure/         跨上下文技术设施（Flyway、客户端装配、定时任务注册）
```

每个上下文内部统一 `domain / application / infrastructure / interfaces` 四层；存量 `controller/service/mapper/entity` 在 M0 迁入 `dispatch` 的 `interfaces/infrastructure`，**旧 API 路径保留**。

### 8.3 模块协作规则（ArchUnit 固化）

1. 上下文 `domain` 不得依赖 Spring/MyBatis/Feign/Redis。
2. 上下文之间只能依赖对方 `application.service` 接口或 `domain.event`；禁止依赖对方 `infrastructure`/`mapper`/`entity`。
3. 控制器不得跨上下文直接调用另一个上下文的 Mapper；跨上下文查询必须经应用服务。
4. 禁止跨上下文 SQL JOIN；看板/追溯聚合只允许查询只读视图或应用层汇总。
5. 报工→在制品→线边扣减→谱系建链在同一本地事务；ERP 回传走 Outbox，不引入消息队列。
6. 所有列表查询必须带 `tenant_id`；手写 SQL 显式带租户条件。
7. `interfaces` 不依赖 `infrastructure`；`application` 不依赖 `infrastructure`（依赖倒置，端口在 domain/application）。

### 8.4 事务与一致性

- 本地事务：报工提交（报工 + 不良明细 + 在制品 + 线边扣减 + 谱系）同事务。
- 外部集成：MES→ERP 完工/消耗写 `mes_common_outbox`，定时任务重试；失败不阻断现场报工。
- ERP→MES 工单接收：`erp_order_no` 唯一索引 + 应用层先查后建；重复下发返回既有工单。
- 设备采集：样本按 `(point_id, sampled_at, seq)` 幂等；重复上报丢弃或覆盖同键。
- 批量任务（OEE 汇总、绩效汇总、归档）需分布式锁（Redisson）保证单实例。

### 8.5 前端

- 复用 `frontend/platform-apps/whale-mes`（8105、base `/mes`），**不新增 qiankun 子应用**。
- 按模块新增路由：`/mes/master-data/*`、`/mes/dispatch/*`、`/mes/execution/*`、`/mes/trace/*`、`/mes/quality/*`、`/mes/equipment/*`、`/mes/collect/*`、`/mes/workforce/*`。
- 页面模式：主数据列表/表单；单据“列表 + 详情 + 动作条”；现场终端“扫码 + 大按钮 + 最少输入”；看板“大屏只读 + 自动刷新”。
- 现场终端需适配触摸屏/平板：最小点击区域 44px、扫码枪回车提交、断网重试提示。

---

## 九、权限、菜单与角色预留

现状权限已用至 455，ERP 预留 355–599，PDM 规划预留 900–1099 / 菜单 180–219。MES 从 **1100** 起编，菜单从 **240** 起编，实施时按里程碑逐段启用，**禁止跳段占用**。

| 模块 | 权限号段 | 菜单号段 | 说明 |
|---|---|---|---|
| 车间主数据 | 1100–1129 | 240–244 | 车间、产线、工作中心、工位、班组、班次、工序、工艺路线、缺陷/原因 |
| 调度派工 | 1130–1179 | 245–253 | 工单接收、拆分、派工、排程、插单、进度、生产看板 |
| 生产执行 | 1180–1219 | 254–259 | 开工/暂停/完工/报废、报工、领料/退料/补料、在制品、异常 |
| 物料与追溯 | 1220–1259 | 260–266 | 条码、批次、谱系、线边仓、补货、扫码防错、正反追溯 |
| 车间质量 | 1260–1299 | 267–273 | 首件、IPQC、不良品、处置、返工、SPC、质量看板 |
| 设备与采集 | 1300–1349 | 274–281 | 台账、状态、保养、维修、备件、OEE、设备接入、采集点、看板、归档 |
| 人员与绩效 | 1350–1379 | 282–286 | 班组、排班、交接班、工时、绩效、技能 |
| 看板/内网/预留 | 1380–1399 | 287–299 | 综合看板、内网接口、导出、后续增强 |

角色：

| 角色 | ID | 说明 |
|---|---|---|
| `mes_manager` | 20701（存量） | MES 全量 |
| `mes_member` | 20702（存量） | 查询 + 执行子集 |
| `mes_planner` | 20703（新增） | 主数据只读 + 调度派工/排程 |
| `mes_operator` | 20704（新增） | 现场执行、报工、领料、异常上报 |
| `mes_quality` | 20705（新增） | 检验、不良品、处置、SPC |
| `mes_equipment` | 20706（新增） | 设备、保养、维修、备件 |
| `mes_team_lead` | 20707（新增） | 班组、排班、交接班、绩效查看 |

> 权限/菜单脚本沿用 `migration-5.59-mes.sql` 的幂等写法；业务租户角色复制与授权参照 `migration-5.65` 与 `migration-5.78-*` 的租户复制模式。实施前先查已用最大值，避免与 ERP 预留段冲突。

---

## 十、非功能与安全

| 主题 | 要求 |
|---|---|
| 性能 | 工单/任务列表 P95 < 500ms；报工提交 < 200ms；看板首屏 < 1s（快照 + 缓存 5–10s）；正反追溯 3 层 P95 < 3s |
| 并发 | 任务报工用乐观锁 `version`；线边库存按“物料+线边仓+批次”行锁或乐观锁；扫码重复提交按 `(tenant, barcode, report_no)` 幂等 |
| 可用性 | ERP 不可用时 MES 仍可执行（工单本地快照）；回传失败进 Outbox 重试；看板降级读最近快照 |
| 安全 | 租户隔离 + 功能权限 + 数据范围（车间/产线/班组）；现场终端必须绑定工位与登录人；跨租户一律 404 |
| 不可篡改 | 已确认报工、已处置 NCR、已验收保养、已锁定绩效不可改；只能冲销/追加留痕 |
| 审计 | 工单接收/拆分/派工、状态流转、报工修正、领料/退料、质量处置、设备维修、保养验收、追溯查询、导出全部 `@AuditLog` |
| 脱敏 | 员工绩效、工号、联系方式在日志/审计中脱敏；检验图片走 `whale-file`，MES 只存 `fileId` |
| 数据保留 | 指标样本热存 90 天、归档 1–3 年；报工/谱系至少保留 2 年；归档策略可配置 |
| 设备接入 | 首期由边缘网关推送 REST/MQTT 到 `/mes/internal/collect/**`；MES 不直连 PLC、不下发控制指令；直连适配器作为可选插件 |
| 可观测 | 报工成功率、Outbox 积压、采集延迟、看板刷新耗时、追溯查询耗时接入 Prometheus；关键告警走 `whale-alert`/`whale-message` |

---

## 十一、开放问题（编码前必须确认）

| 编号 | 问题 | 影响 | 建议默认（待确认） |
|---|---|---|---|
| D1 | 车间主数据归属：车间/产线/工作中心/工位/班组/班次由 MES 维护，还是 ERP/PDM 下发？ | 主数据边界与同步方式 | MES 维护并发布只读快照；物料/BOM/工艺归 PDM/ERP |
| D2 | 工单拆分粒度：按工序、按批次还是按产线？ | 任务模型与报工粒度 | 默认按工艺路线工序拆分；批次拆分作为可选 |
| D3 | 派工粒度：产线、班组还是机台？是否允许一机多任务并行？ | 派工与排程模型 | 默认机台/工作中心为派工对象，班组为属性；一机多任务需排程锁 |
| D4 | 有限产能排程做到什么程度？是否引入 APS？ | 排程复杂度与工期 | MVP 按工作中心+班次产能做有限排程与插单；不引入 APS |
| D5 | 报工粒度：工序级、批次级还是单件级？扫码频次？ | 现场操作与数据量 | 默认工序+批次；关键件可单件序列号 |
| D6 | 领料模式：推动领料、倒冲领料还是混合？超领是否审批？ | 领料与线边仓 | 默认扫码领料；倒冲按物料配置；超领走 OA 审批 |
| D7 | 线边仓是否计入财务库存与金额？ | 与 ERP 库存边界 | MES 只记数量与批次；金额与总账归 ERP；补货走 ERP 调拨 |
| D8 | 条码标准与标签：Code128/QR、批次码/序列号、标签模板与打印机 | 条码与硬件集成 | 默认 Code128 + QR 双码；关键件序列号；打印由现场标签系统 |
| D9 | 质量抽样标准：AQL/GB2828 还是固定频次？ | 检验计划模型 | 默认可配置抽样方案；不内置 AQL 判定 |
| D10 | SPC 图型与控制规则：Xbar-R / P / C，Nelson / Western Electric？ | SPC 复杂度 | 默认 Xbar-R + 8 条规则可配置；P/C 图后续 |
| D11 | 设备接入方式：边缘网关推送、直连 OPC UA/Modbus/S7？采集频率？ | 设备模块工期与网络 | 默认网关推送 REST/MQTT；MES 只读；直连作为可选插件 |
| D12 | OEE 口径：可用率/性能/质量的公式、计划停机与换型是否计入？ | OEE 结果可信度 | 默认按班次计算；计划停机单列；公式可配置 |
| D13 | 实际工时口径：首件开工到最后完工，是否扣除暂停/换型？标准工时来源？ | 绩效与成本 | 默认扣除暂停；标准工时取工艺路线快照；月度锁定 |
| D14 | 数据保留与看板刷新：样本热存/归档年限、看板刷新频率？ | 存储与性能 | 默认样本热存 90 天、归档 1 年；看板 5–10s |
| D15 | 内网调用鉴权：共享 Token、HMAC 还是 mTLS？ | ERP/网关集成安全 | 默认内网 Token + HMAC + 网关 `internal-deny`；生产按网络边界升级 |
| D16 | 现场断网是否要求离线报工？ | 终端架构与冲突处理 | 默认在线 + 幂等重试；离线队列作为后续增强 |

---

## 十二、变更记录

| 日期 | 版本 | 变更 |
|---|---|---|
| 2026-11-04 | v0.1 | 首版：现状基线、七大模块差距矩阵、上下文与边界、聚合/状态机、表与接口规划、权限号段、非功能安全与 16 项开放问题 |
| 2026-11-04 | v0.2 | M0/M1 落地回填：工序任务与异常状态机改为实现口径（`PLANNED/DISPATCHED/IN_PROGRESS/PAUSED/COMPLETED/SCRAPPED`、`OPEN→ACKNOWLEDGED→RESOLVED→CLOSED`）；表清单标注 M1 已落地 13 表（空库 28 表）与排程表仅建表；接口路径补充 `/mes/dispatch/task/page`、`/split`、`/dispatch` |
| 2026-11-04 | v0.3 | M2 落地回填：物料与追溯四条子模块标记已落地；新增批次/条码/谱系/补货状态口径与“线边库存无状态、由流水汇总”的约束；表清单标注 V5 已落地 8 表（空库累计 36 表）；条码唯一、谱系无环、消耗量 ≤ 投入量、线边余额 ≥ 0 为实现不变量 |
