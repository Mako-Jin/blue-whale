# ERP 功能模块全景：领域模型与架构设计（规划稿）

> 状态：**规划（2026-11-03，未编码）**。本稿只定义边界、模型、契约与分期，不包含实现细节的最终承诺。
> 关联：[Wave 5.78 实施计划](../plans/2026-11-03-wave5.78-erp-panorama.md)、[需求登记 REQ-FUNC-036..042 / REQ-ARCH-018 / REQ-SEC-028](../requirements/register.md)、[服务清单](../service-catalog.md)。
> 输入：用户提供的《ERP 功能模块全景》（财务管理 / 供应链 / 生产计划 / 轻量人力 / 质量与设备 / 数据分析与报表中心）与 OA、MES 边界说明。

---

## 一、结论摘要

1. **现状覆盖率低**：`whale-erp` 当前只有物料（`erp_item`）与供应商（`erp_supplier`）两张主数据表、10 个 CRUD 端点、4 个前端页面；全景 6 大模块 26 个子模块中，只有「供应商档案」「客户档案之外的基础主数据」等少数点有雏形，其余为绿地。
2. **不拆微服务**：ERP 保持 `whale-erp` **模块化单体**（9001、`/erp/**`、`bluewhale_erp`、包 `com.bluewhale.erp`、前端 8102）。按限界上下文在包与表前缀上分隔，模块间只经应用服务/领域事件协作，**禁止跨模块访问 Mapper 与表**。
3. **7 个限界上下文**：主数据（MDM）、财务（Finance）、供应链（SCM）、生产计划（Production Planning）、人力（HR）、质量与设备（Quality & Equipment）、分析（Analytics）。它们是同一部署单元内的模块，不是独立服务。
4. **主链路**：`采购/销售/生产 → 库存与成本 → 自动凭证 → 总账与报表`；因此**先建财务骨架（科目、期间、凭证、应收应付）**，再横向铺交易单据，避免后续补记账规则时返工。
5. **跨域边界**：OA/Workflow 管审批、CRM 管客户关系、SRM 管供应商准入与绩效、PDM 管 BOM/工艺、MES 管车间执行与质量数据采集、`whale-file` 管附件字节、`whale-pay` 管资金执行、`whale-message` 管通知、`whale-audit` 管审计。
6. **必须先确认的业务规则**：会计准则与科目表、多币种与汇兑、税务口径、期间结账策略、成本核算方法、库存计价、预算控制强度、信用额度控制点、客户主数据归属、薪资合规模板（见 §十）。这些规则未确认前不写死默认值。

---

## 二、现状基线（2026-11-03 实测）

| 维度 | 现状 | 证据 |
|---|---|---|
| 后端代码 | 20 个 Java 文件，单模块分包（`controller / service / mapper / entity / dto / vo`），非 DDD 四层 | `backend/whale-apps/whale-erp/src/main/java/com/bluewhale/erp` |
| 端点 | 10 个：`/erp/item`、`/erp/supplier` 各 5 个（分页、详情、新增、编辑、删除） | `ErpItemController`、`ErpSupplierController` |
| 数据表 | 4 张：`erp_item`、`erp_supplier`、`audit_log`、`api_log`；无分录、无期间、无库存 | `db/migration/V1__baseline.sql` |
| 种子数据 | 物料 `ERP-001/002`、供应商 `SUP-001/002` | 同上 |
| 前端 | `frontend/platform-apps/whale-erp`（8102、base `/erp`）：物料列表/表单、供应商列表/表单 + 4 个测试 | 目录清单 |
| 权限 | `sys:erp:item:*` 203–206、`sys:erp:supplier:*` 235–238 | `migration-5.58-erp.sql`、`migration-5.69-erp-supplier.sql` |
| 菜单 | 25 物料管理、32 供应商管理 | 同上 |
| 角色 | `erp_manager` / `erp_member`（平台 + 业务租户复制，5.58/5.65/5.69 已固化） | 迁移脚本 |
| 网关 | `/erp/**` → `lb://whale-erp`，不剥离前缀 | `whale-gateway/src/main/resources/application.yml` |
| 相邻服务 | MES 仅 `MesWorkOrderController`；OA 仅 `OaLeaveController`；PDM 产品骨架；CRM 门禁骨架；SRM 未建 | 各服务源码 |

**结论**：全景中除「供应商档案」「物料档案」外，财务、供应链交易、库存、计划、人力、质量设备、分析全部缺失；现有两张主数据表也缺少 ERP 记账所需的属性（计价方式、税码、默认仓库、客户信用等）。

---

## 三、全景差距矩阵

图例：✅ 已具备；🟡 部分/仅字段；❌ 缺失。

### 3.1 财务管理（ERP 核心骨架）

| 子模块 | 能力 | 现状 | 落点（规划） |
|---|---|---|---|
| 总账 | 凭证录入、账簿查询、期末结转、结账控制、多币种 | ❌ | `finance.gl`：`erp_voucher`/`erp_voucher_line`/`erp_account_balance` |
| 应收应付 | 客户应收台账、供应商应付台账、账龄、核销、坏账计提 | ❌ | `finance.ar` / `finance.ap`：`erp_receivable*` / `erp_payable*` / `erp_bad_debt` |
| 固定资产 | 卡片、折旧、变动、盘点、报废 | ❌ | `finance.fa`：`erp_fixed_asset*` |
| 成本核算 | 产品成本、物料归集、人工/制造费用分摊、差异 | ❌ | `finance.cost` + `scm.inventory` 成本层 |
| 预算 | 部门预算、控制、执行分析、超预算预警 | ❌ | `finance.budget`：`erp_budget_plan`/`erp_budget_control` |
| 资金 | 资金计划、银行对账、票据、现金流预测 | ❌ | `finance.treasury`；执行侧调 `whale-pay` |
| 财务报表 | 资产负债表、利润表、现金流量表、自定义 | ❌ | `finance.report`：科目余额取数 + 报表定义/快照 |

### 3.2 供应链管理

| 子模块 | 能力 | 现状 | 落点 |
|---|---|---|---|
| 采购 | 申请、订单、比价、到货验收、退货、价格分析 | ❌ | `scm.purchase`：`erp_purchase_*`、`erp_goods_receipt*` |
| 销售 | 报价、订单、发货、退货、价格、信用额度 | ❌ | `scm.sales`：`erp_sales_*`、`erp_shipment_notice`、`erp_credit_limit` |
| 库存 | 出入库/调拨、盘点、安全库存、呆滞、批次 | ❌ | `scm.inventory`：`erp_stock_movement`/`erp_stock_balance`/`erp_batch` |
| 供应商管理 | 档案、评级、绩效、准入退出 | 🟡 | 档案+评级已在 `erp_supplier`；准入/绩效归 SRM，ERP 只读评级 |
| 客户管理 | 档案、分级、信用、流失分析 | ❌ | 业务主数据 `erp_customer`（关系主数据归 CRM，见 D9）；流失分析归 CRM |

### 3.3 生产计划

| 子模块 | 能力 | 现状 | 落点 |
|---|---|---|---|
| MPS | 销售订单/预测 → 月/周计划 | ❌ | `manufacturing.mps`：`erp_mps` |
| MRP | BOM 展开、物料/数量/时间 | ❌ | `manufacturing.mrp`：`erp_mrp_run`/`erp_mrp_result`；BOM 来自 PDM |
| CRP | 产能负荷核算 | ❌ | `manufacturing.crp`：`erp_crp_run` |
| 生产订单 | 工单下达、领料、完工、结案 | ❌ | `manufacturing.order`：`erp_production_order*`；下发 MES |
| 预测 | 销售/需求预测、准确率 | ❌ | `manufacturing.forecast`：`erp_forecast` |

### 3.4 人力资源（轻量）

| 子模块 | 能力 | 现状 | 落点 |
|---|---|---|---|
| 人事档案 | 员工、组织、入转调离 | ❌ | `hr.employee`：`erp_employee`/`erp_org_unit`/`erp_employment_change` |
| 薪酬 | 薪资计算、社保公积金、工资条、个税 | ❌ | `hr.payroll`：`erp_payroll_run`/`erp_payslip` |
| 考勤 | 数据对接、请假/加班统计、异常 | ❌ | `hr.attendance`：`erp_attendance_summary`（审批来自 OA） |
| 绩效 | 指标、流程、结果 | ❌ | `hr.performance`：`erp_performance_cycle`/`erp_kpi`/`erp_appraisal` |

### 3.5 质量与设备

| 子模块 | 能力 | 现状 | 落点 |
|---|---|---|---|
| 质量 | IQC / IPQC / FQC、不合格品 | ❌ | `quality.inspection`：`erp_inspection_order*`、`erp_ncr` |
| 设备 | 档案、保养、维修、OEE | ❌ | `quality.equipment`：`erp_equipment*`、`erp_oee_record` |
| 量具 | 台账、校准计划/记录 | ❌ | `quality.metrology`：`erp_measuring_tool`/`erp_calibration_*` |

### 3.6 数据分析与报表中心

| 子模块 | 能力 | 现状 | 落点 |
|---|---|---|---|
| 驾驶舱 | 收入/成本/利润/订单实时看板 | ❌ | `analytics.cockpit`：`erp_cockpit_snapshot` |
| 多维分析 | 部门/产品/客户/区域钻取 | ❌ | `analytics.cube`：事实视图 + 维度表（只读投影） |
| 报表中心 | 自定义、定时推送、导出 | ❌ | `analytics.report`：`erp_report_def`/`erp_report_schedule`；推送调 `whale-message` |
| 预警中心 | 库存/超期应收/超预算/质量异常，出计划 | ❌ | `analytics.alert`：`erp_alert_rule`/`erp_alert_event`；出计划落 `erp_mrp_result` 或 `erp_plan_suggestion` |

---

## 四、战略建模

### 4.1 业务能力与子域分类

| 子域 | 类型 | 判据 |
|---|---|---|
| 财务核算（总账、应收应付、资产、成本） | **核心域** | 唯一产生法定账簿与对外报表，规则最复杂、不可替代 |
| 供应链交易（采购、销售、库存） | **核心域** | 业务单据与库存量是 ERP 的主数据流 |
| 生产计划（MPS/MRP/CRP） | **支撑域** | 依赖 BOM/产能，向 MES 输出计划 |
| 主数据（物料、供应商、客户、仓库、科目、期间） | **支撑域** | 被多上下文共享，但本身不产生业务价值 |
| 人力（轻量） | **支撑域** | 与 OA 分工，ERP 只做核算与成本归集 |
| 质量与设备 | **支撑域** | 与 MES 分工，ERP 管标准、成本与台账 |
| 数据分析与报表 | **通用域** | 只消费各上下文数据，不拥有交易事实 |

### 4.2 限界上下文

| 上下文 | 拥有的事实 | 不拥有 |
|---|---|---|
| **MDM 主数据** | 物料、供应商、客户业务属性、仓库、计量单位、币种/汇率、会计期间、科目表、税码 | 客户关系与商机（CRM）、供应商准入与绩效（SRM）、BOM/工艺（PDM） |
| **Finance 财务** | 凭证、余额、应收应付、核销、资产、成本、预算、资金、报表 | 交易单据本身（由 SCM/MFG 拥有）、支付渠道执行（`whale-pay`） |
| **SCM 供应链** | 采购/销售单据、库存移动与余额、批次、信用占用 | 客户关系、供应商准入、物料主数据 |
| **Production Planning 生产计划** | 预测、MPS、MRP 结果、CRP、生产订单 | BOM/工艺主数据（PDM）、工序执行与报工（MES） |
| **HR 人力** | 员工与组织、考勤汇总、薪资批次与工资条、绩效 | 请假/报销审批（OA/Workflow）、身份认证（auth） |
| **Quality & Equipment 质量设备** | 检验单、不合格品、质量成本、设备台账、保养维修、OEE 记录、量具校准 | 现场检验执行与数据采集（MES） |
| **Analytics 分析** | 指标定义、驾驶舱快照、报表定义/计划、预警规则与事件 | 任何交易事实与账簿 |

### 4.3 上下文映射

| 上游 | 下游 | 关系类型 | 契约 |
|---|---|---|---|
| PDM | ERP（生产计划、成本） | 供应商–客户（Conformist） | 发布 BOM/工艺版本 → ERP 落 `erp_bom_snapshot` 只读快照，按版本号幂等 |
| ERP | MES | 客户–供应商（Published Language） | `POST /mes/internal/work-order` 下发；幂等键 `erpProductionOrderNo` |
| MES | ERP | 事件 + 内网 REST | 完工报工/领料消耗/检验结果回传；`sourceEventId` 幂等，重复不重复记账 |
| OA / Workflow | ERP（财务、人力） | 事件（Customer–Supplier） | 审批通过事件携带 `approvalInstanceId`；ERP 生成凭证/考勤汇总时以其为幂等键 |
| ERP | OA / Workflow | 调用 | 采购申请、订单、付款等审批发起；ERP 只存审批实例号与状态快照 |
| CRM | ERP | 共享主数据（需决策，D9） | 客户关系主数据归 CRM；ERP 需要开票/信用/AR 主体字段，建议 CRM 发布 `crmCustomerId` + 最小字段，ERP 保留业务扩展字段 |
| SRM | ERP | 上游发布 | 供应商准入状态与绩效评级 → ERP 只读同步，不反向写 |
| Contract | ERP | 引用快照 | 销售/采购引用 `contractNo` + 相对方快照，不复制合同正文 |
| ERP | `whale-pay` | 调用 | 付款/收款指令与结果回执；ERP 不存渠道流水 |
| ERP | `whale-file` | 调用 | 发票、合同、盘点附件字节 |
| ERP | `whale-message` | 事件 | 超预算、超期应收、工资条通知、报表定时推送 |
| ERP | auth / audit | 平台能力 | 租户/权限/身份；写操作 `@AuditLog` |

### 4.4 跨域硬边界（不可协商）

1. ERP **不**保存 BOM/工艺主数据，只存版本快照；BOM 变更必须由 PDM 发布。
2. ERP **不**执行车间工序、不采集设备实时数据；MES 回传后才更新工单与成本。
3. ERP **不**承载审批流引擎；审批状态以 OA/Workflow 为准。
4. ERP **不**保存客户关系过程数据（商机、跟进）；只保存交易所需的客户业务属性。
5. ERP **不**保存支付渠道流水与发票影像字节。
6. ERP **不**直接读其他服务的库；跨域只走内网 REST 或事件。

---

## 五、战术建模

### 5.1 聚合与不变量

| 上下文 | 聚合根 | 关键不变量 | 主要命令 | 领域事件 |
|---|---|---|---|---|
| MDM | `Item` | 编码租户内唯一；启用后方可被单据引用；计量单位必填 | 建档、改属性、启停 | `ItemChanged` |
| MDM | `Supplier` | 编码唯一；停用后不可下新订单 | 建档、评级更新、启停 | `SupplierChanged` |
| MDM | `Customer` | 编码唯一；信用额度 ≥ 0；停用后不可新开销售订单 | 建档、信用调整、启停 | `CustomerCreditChanged` |
| MDM | `AccountingPeriod` | 期间不重叠；`CLOSED/LOCKED` 不可再记账 | 开启、关账、锁定、反结账（受控） | `PeriodClosed` |
| MDM | `Account` | 科目编码唯一；末级科目才可记账；借贷方向固定 | 建科目、停用 | `AccountChanged` |
| Finance | `Voucher` | 借贷必平衡（按币种）；会计日期必须落在 `OPEN` 期间；已记账不可改，只能红冲；凭证号租户+期间+类型唯一 | 录入、提交、记账、红冲 | `VoucherPosted`、`VoucherReversed` |
| Finance | `Receivable` | 未核销金额 ≥ 0；核销总额 ≤ 原币金额；跨租户不可见 | 生成、核销、计提坏账、关闭 | `ReceivableSettled`、`BadDebtProvisioned` |
| Finance | `Payable` | 同上（应付侧） | 生成、核销、关闭 | `PayableSettled` |
| Finance | `FixedAsset` | 累计折旧 ≤ 原值−残值；已处置不可再计提 | 建卡、变动、计提、盘点、处置 | `AssetDepreciated`、`AssetDisposed` |
| Finance | `BudgetPlan` | 预算版本按「部门+期间+科目」唯一；执行额 ≤ 预算额（`BLOCK` 模式） | 编制、下达、调整 | `BudgetExceeded` |
| SCM | `PurchaseOrder` | 已收货数量 ≤ 订单数量；关闭后不可收货；供应商必须启用 | 创建、审批、下达、收货、退货、关闭 | `PurchaseOrderReceived` |
| SCM | `SalesOrder` | 已发货 ≤ 订单数量；信用占用 ≤ 可用额度（若启用）；价格不得低于底价（可配置） | 创建、审批、发货、退货、关闭 | `SalesOrderShipped` |
| SCM | `StockMovement` | **只追加**，不可修改删除；批次/仓库/物料必填；负库存默认禁止 | 入库、出库、调拨、盘盈亏 | `StockMoved` |
| SCM | `StockBalance` | 由移动流水汇总，不允许独立修改（只能由盘点调整生成流水） | 重建、快照 | — |
| SCM | `StockTake` | 盘点差异必须生成调整流水；已审核不可改 | 创建、录入、审核、调整 | `StockTakeAdjusted` |
| MFG | `Forecast` | 版本+期间+物料唯一；预测准确率可回算 | 编制、发布、回算 | `ForecastPublished` |
| MFG | `MpsPlan` | 计划量 ≥ 已下达量 | 生成、调整、下达 | `MpsReleased` |
| MFG | `MrpRun` | 一次运行结果可重放（同输入同输出）；计划订单必须可追溯至需求来源 | 运行、确认、转生产/采购 | `MrpCompleted` |
| MFG | `ProductionOrder` | 已完工 ≤ 计划量；已领料 ≤ 定额（超领需审批）；结案后不可回写 | 下达、领料、报工、完工、结案 | `ProductionOrderReleased`、`ProductionOrderCompleted` |
| HR | `PayrollRun` | 期间唯一；`APPROVED` 后明细不可改；工资条一经发放不可重发同版本 | 计算、审批、发放、作废 | `PayrollApproved` |
| HR | `Employee` | 工号唯一；入职/离职日期合法；离职后不可发薪 | 建档、异动、离职 | `EmployeeChanged` |
| Quality | `InspectionOrder` | 检验项必须全部有结论才可判定；`FAILED` 必须生成 NCR | 创建、录入、判定、处置 | `InspectionFailed` |
| Quality | `Nonconformity` | 处置方式（返工/让步/报废/退货）必填；关闭需结论 | 创建、处置、关闭 | `NcrClosed` |
| Quality | `Equipment` | 设备编码唯一；保养计划到期提醒；校准过期量具不可用于检验 | 建档、保养、维修、停用 | `EquipmentMaintenanceDue` |
| Analytics | `AlertRule` | 规则表达式可解析；同一规则+对象+周期不重复触发 | 创建、启停、触发 | `AlertTriggered` |

### 5.2 值对象（共享内核 `common`）

`Money(amount, currency)`、`Quantity(value, uom)`、`ExchangeRate(from, to, rate, effectiveDate)`、`AccountingPeriodCode(yyyyMM)`、`VoucherNo`、`DocumentNo`、`TaxAmount`、`CreditLimit`、`Percentage`、`DateRange`。

约束：金额一律 `DECIMAL(18,4)`、汇率 `DECIMAL(18,8)`，禁止 `double`；比较与舍入统一走 `Money` 值对象，禁止各处自写 `BigDecimal.setScale`。

### 5.3 状态机

| 对象 | 状态 |
|---|---|
| 会计期间 | `FUTURE → OPEN → CLOSING → CLOSED → LOCKED`（反结账仅 `CLOSED → OPEN`，需权限与审计） |
| 凭证 | `DRAFT → SUBMITTED → POSTED → REVERSED`（`POSTED` 起不可改） |
| 采购订单 | `DRAFT → APPROVING → APPROVED → PARTIALLY_RECEIVED → RECEIVED → CLOSED`；旁支 `CANCELLED` |
| 销售订单 | `DRAFT → APPROVING → APPROVED → PARTIALLY_SHIPPED → SHIPPED → CLOSED`；旁支 `CANCELLED` |
| 生产订单 | `DRAFT → RELEASED → IN_PROGRESS → COMPLETED → CLOSED`；旁支 `CANCELLED` |
| 检验单 | `PENDING → IN_PROGRESS → PASSED / FAILED → DISPOSED` |
| 薪资批次 | `DRAFT → CALCULATED → APPROVED → PAID → CLOSED` |
| 预算 | `DRAFT → SUBMITTED → APPROVED → EFFECTIVE → CLOSED` |
| 应收/应付 | `OPEN → PARTIALLY_SETTLED → SETTLED → CLOSED`；坏账旁支 `PROVISIONED / WRITTEN_OFF` |

### 5.4 异常与错误码

沿用「模块段 + 序号」：`ERP-1xxx` 主数据、`ERP-2xxx` 财务、`ERP-3xxx` 供应链、`ERP-4xxx` 生产计划、`ERP-5xxx` 人力、`ERP-6xxx` 质量设备、`ERP-7xxx` 分析。业务错误统一 `BizException(code, message)`，禁止把校验失败包成 500。
典型：`PERIOD_CLOSED`、`VOUCHER_UNBALANCED`、`CREDIT_LIMIT_EXCEEDED`、`NEGATIVE_STOCK_FORBIDDEN`、`DOCUMENT_ALREADY_POSTED`、`INSPECTION_INCOMPLETE`、`CALIBRATION_EXPIRED`。

---

## 六、数据模型规划

### 6.1 命名与库

- 库：`bluewhale_erp`（不变）；表前缀 `erp_`；新表按模块二级前缀：`erp_fin_*`、`erp_scm_*`、`erp_mfg_*`、`erp_hr_*`、`erp_qc_*`、`erp_bi_*`。
- **存量例外**：`erp_item`、`erp_supplier` 不改名（避免数据迁移与 API 破坏），在文档中登记为主数据表。
- 公共字段：`id`（雪花）、`tenant_id`、`create_time`、`update_time`、`deleted`；业务单据另加 `doc_no`、`doc_date`、`status`、`version`（乐观锁）。
- 逻辑外键，不建物理外键；跨模块禁止 JOIN 写 SQL（分析模块只读视图除外）。

### 6.2 分期表清单

| 阶段 | 新增表（草案） |
|---|---|
| M0 主数据 | `erp_warehouse`、`erp_uom`、`erp_currency`、`erp_exchange_rate`、`erp_accounting_period`、`erp_account`、`erp_tax_code`、`erp_customer`（若 D9 决定 ERP 持有业务主数据） |
| M1 财务核心 | `erp_fin_voucher`、`erp_fin_voucher_line`、`erp_fin_account_balance`、`erp_fin_receivable`、`erp_fin_receivable_settlement`、`erp_fin_payable`、`erp_fin_payable_settlement`、`erp_fin_bad_debt` |
| M1 扩展 | `erp_fin_fixed_asset`、`erp_fin_depreciation_run`、`erp_fin_cost_object`、`erp_fin_budget_plan`、`erp_fin_budget_control`、`erp_fin_fund_plan`、`erp_fin_bank_statement`、`erp_fin_bill`、`erp_fin_report_def`、`erp_fin_report_snapshot` |
| M2 供应链 | `erp_scm_purchase_requisition`、`erp_scm_purchase_order`、`erp_scm_purchase_order_line`、`erp_scm_supplier_quote`、`erp_scm_goods_receipt`、`erp_scm_goods_receipt_line`、`erp_scm_purchase_return`、`erp_scm_sales_quote`、`erp_scm_sales_order`、`erp_scm_sales_order_line`、`erp_scm_shipment_notice`、`erp_scm_sales_return`、`erp_scm_price_policy`、`erp_scm_credit_limit`、`erp_scm_stock_movement`、`erp_scm_stock_balance`、`erp_scm_stock_transfer`、`erp_scm_stock_take`、`erp_scm_batch`、`erp_scm_safety_stock` |
| M3 生产计划 | `erp_mfg_forecast`、`erp_mfg_mps`、`erp_mfg_mrp_run`、`erp_mfg_mrp_result`、`erp_mfg_crp_run`、`erp_mfg_production_order`、`erp_mfg_production_order_line`、`erp_mfg_bom_snapshot` |
| M4 人力 | `erp_hr_employee`、`erp_hr_org_unit`、`erp_hr_employment_change`、`erp_hr_attendance_summary`、`erp_hr_payroll_run`、`erp_hr_payslip`、`erp_hr_performance_cycle`、`erp_hr_kpi`、`erp_hr_appraisal` |
| M5 质量设备 | `erp_qc_inspection_order`、`erp_qc_inspection_item`、`erp_qc_ncr`、`erp_qc_quality_cost`、`erp_qc_equipment`、`erp_qc_maintenance_plan`、`erp_qc_maintenance_order`、`erp_qc_oee_record`、`erp_qc_measuring_tool`、`erp_qc_calibration_plan`、`erp_qc_calibration_record` |
| M6 分析 | `erp_bi_cockpit_snapshot`、`erp_bi_report_def`、`erp_bi_report_schedule`、`erp_bi_alert_rule`、`erp_bi_alert_event` |

> 表中为草案，实际 DDL 在对应里程碑定稿后随 Flyway `V{n}__*.sql` 落地；禁止在基线脚本上直接追加（基线保持可重放）。
>
> **实施偏差（2026-11-04）**：M0 + M1 合并为 `V2__masterdata_finance.sql`，实际建 **13 张表**：
> `erp_account`、`erp_accounting_period`、`erp_customer`、`erp_warehouse`、`erp_uom`、`erp_currency`、
> `erp_tax_code`、`erp_fin_voucher`、`erp_fin_voucher_line`、`erp_fin_receivable`、
> `erp_fin_receivable_settlement`、`erp_fin_payable`、`erp_fin_payable_settlement`。
> `erp_exchange_rate` 未建（凭证分录保留 `currency + exchange_rate` 字段，汇率表随 M2 多币种结算补）；
> `erp_fin_account_balance` 未建（余额由已记账分录实时汇总）；`erp_fin_bad_debt` 未建（坏账金额落在应收单 `bad_debt_amount`）。

### 6.3 关键索引与数据量假设

- 凭证：按 `(tenant_id, period_code, voucher_no)` 唯一；`(tenant_id, account_id, voucher_date)` 支撑账簿查询。
- 库存流水：`(tenant_id, item_id, warehouse_id, occurred_at)`；余额表按 `(tenant_id, item_id, warehouse_id, batch_no)` 唯一。
- 单据：`(tenant_id, doc_no)` 唯一；状态索引支撑列表。
- 量级假设（用于设计，不作为验收）：物料 1 万、BOM 5 层、单据 10 万/年、凭证行 20 万/年、库存流水 100 万/年。

---

## 七、接口规划

| 分组 | 路径 | 说明 |
|---|---|---|
| 主数据 | `/erp/master-data/item`、`/supplier`、`/customer`、`/warehouse`、`/uom`、`/currency`、`/period`、`/account`、`/tax-code` | 存量 `/erp/item`、`/erp/supplier` **保留不破坏**，新页面走新前缀 |
| 财务 | `/erp/finance/voucher`、`/ledger`、`/period`、`/receivable`、`/payable`、`/aging`、`/asset`、`/cost`、`/budget`、`/treasury`、`/report` | 记账/红冲/结账为显式动作端点，不用通用 PUT 改状态 |
| 供应链 | `/erp/scm/purchase-requisition`、`/purchase-order`、`/goods-receipt`、`/purchase-return`、`/sales-quote`、`/sales-order`、`/shipment`、`/sales-return`、`/stock`、`/stock-take`、`/transfer` | 单据动作端点：`/submit`、`/approve`、`/receive`、`/ship`、`/close` |
| 生产计划 | `/erp/mfg/forecast`、`/mps`、`/mrp-run`、`/crp-run`、`/production-order` | MRP/CRP 为异步任务：`POST /mrp-run` 返回 `runId`，`GET /mrp-run/{id}` 查进度 |
| 人力 | `/erp/hr/employee`、`/attendance`、`/payroll`、`/payslip`、`/performance` | 工资条只读本人；HR 管理员按权限 |
| 质量设备 | `/erp/qc/inspection`、`/ncr`、`/equipment`、`/maintenance`、`/oee`、`/calibration` | OEE 数据由 MES 推送 |
| 分析 | `/erp/bi/cockpit`、`/cube`、`/report`、`/alert` | 只读为主 |
| 内网 | `/erp/internal/**` | 仅服务间；网关注入 `internal-deny` 403 |

通用契约：
- 响应 `Result<T>` / `PageResult<T>`；分页参数沿用 `PageQuery`。
- 写操作 `@RequirePermission` + `@AuditLog`；金额/利率类字段脱敏与精度统一。
- 幂等：`@Idempotent` 用于记账、核销、收货、MES 回调、支付回执。
- 跨域回调必须校验来源与签名/内网标记，`sourceEventId` 唯一索引防重。

---

## 八、架构决策

### 8.1 为什么保持模块化单体

| 选项 | 结论 | 理由 |
|---|---|---|
| 拆成 finance/scm/mfg/hr/qc/bi 六个服务 | ❌ | 事务边界（收货→库存→应付凭证）会跨服务，需分布式事务/对账；团队规模与运维成本不匹配 |
| 保持一个 `whale-erp` | ✅ | 单库本地事务保证「单据→库存→凭证」一致；模块内分区通过包与表前缀隔离 |
| 单模块继续扁平分包 | ❌ | 26 个子模块扁平化后无法阻止跨模块耦合，ArchUnit 也守不住 |

### 8.2 包结构（目标态）

```
com.bluewhale.erp
├── common/                 共享内核：Money、Quantity、DocNo、ERP 错误码、分页基类
├── masterdata/
│   ├── domain/{model,event,port}
│   ├── application/{service,command,query}
│   ├── infrastructure/{persistence,mapper,repository,client}
│   └── interfaces/{controller,dto,vo}
├── finance/
│   ├── gl/  ar/  ap/  fa/  cost/  budget/  treasury/  report/   （各自 domain/application/infrastructure）
│   └── interfaces/
├── scm/ (purchase, sales, inventory)
├── manufacturing/ (forecast, mps, mrp, crp, order)
├── hr/ (employee, attendance, payroll, performance)
├── quality/ (inspection, ncr, equipment, metrology)
├── analytics/ (cockpit, cube, report, alert)
└── infrastructure/         跨模块技术设施（Flyway、Feign 客户端装配、定时任务注册）
```

存量 `item`/`supplier` 在 M0 迁入 `masterdata` 四层包，**API 路径与响应不变**。

> **实施偏差（2026-11-04）**：M0/M1 只新建 `masterdata` 与 `finance` 两个上下文；存量 `item`/`supplier`
> 仍保留原 `controller/service/mapper` 包（未迁移），通过 ArchUnit 新旧规则并存保证不继续劣化。
> 财务暂按单一 `finance` 上下文分包（`domain / application / infrastructure / interfaces`），
> 未按 `gl/ar/ap/fa/cost/budget/treasury/report` 二级拆分；M2 起若财务子域继续膨胀再拆。

### 8.3 模块协作规则（ArchUnit 固化）

1. 模块 `domain` 不得依赖 Spring/MyBatis/Feign/Redis（与既有服务一致）。
2. 模块之间**只能**依赖对方的 `application.service` 接口或 `domain.event`；禁止依赖对方 `infrastructure`/`mapper`/`entity`。
3. 控制器不得跨模块直接调用另一个模块的 Mapper；跨模块查询必须经应用服务。
4. 禁止跨模块 SQL JOIN；分析模块例外：只允许对只读视图/物化快照查询。
5. 单据→凭证的记账通过**领域事件 + 同库事务内同步处理器**完成，不引入消息队列（保证一致；后续如需解耦再升级）。
6. 所有列表查询必须带 `tenant_id` 条件（租户行插件兜底，但手写 SQL 需显式）。

### 8.4 事务与一致性

- 本地事务：单据过账（收货/发货/完工）在同一事务内写库存流水、余额、凭证、事件。
- MRP/CRP/折旧/结账为**批处理任务**：任务表 + 幂等键 + 进度查询 + 失败可重跑。
- 外部回调（MES/OA/支付）落 `sourceEventId` 唯一索引，重复回调返回已处理结果。

### 8.5 前端

- 复用现有 `frontend/platform-apps/whale-erp`（8102、base `/erp`），按模块新增路由：`/erp/master-data/*`、`/erp/finance/*`、`/erp/scm/*`、`/erp/mfg/*`、`/erp/hr/*`、`/erp/qc/*`、`/erp/bi/*`。
- **不新增 qiankun 子应用**，避免 12 个已落地子应用继续膨胀；ERP 前端保持单应用多路由。
- 单据类页面统一「列表 + 详情 + 动作条（提交/审批/过账/关闭）」模式；金额输入统一精度组件。

---

## 九、权限与菜单预留

权限号已用至 354（CRM），菜单已用至 56（CRM 预留 57–62 未启用）。ERP 本次预留如下（**实施时按里程碑逐段启用，禁止跳段占用**）：

| 模块 | 权限号段 | 菜单号段 | 说明 |
|---|---|---|---|
| 主数据 | 355–374 | 63–69 | 仓库、计量单位、币种、期间、科目、税码、客户业务主数据 |
| 财务 | 375–419 | 70–78 | 总账、应收、应付、资产、成本、预算、资金、报表 |
| 供应链 | 420–469 | 79–84 | 采购、销售、库存、价格、信用 |
| 生产计划 | 470–499 | 85–90 | 预测、MPS、MRP、CRP、生产订单 |
| 人力 | 500–519 | 91–95 | 档案、考勤、薪酬、绩效 |
| 质量设备 | 520–549 | 96–100 | 检验、不合格品、设备、量具 |
| 数据分析 | 550–569 | 101–105 | 驾驶舱、多维、报表、预警 |
| 预留 | 570–599 | 106–119 | 后续增强 |

角色：沿用 `erp_manager` / `erp_member`，按模块新增 `erp_finance`、`erp_scm`、`erp_planner`、`erp_hr`、`erp_quality` 业务角色模板；角色 ID 沿用 251xx 段（实施前先核对已用最大值）。

> **实施进度（2026-11-04）**：M0/M1 已启用权限 **355–367**（主数据 13 项）与 **375–387**（财务 13 项），
> 368–374 保留；菜单已启用 **63–66**（ERP 主数据 / 会计凭证 / 应收应付 / 财务报表）；
> 新增角色 `erp_finance`（25110）与 `erp_masterdata`（25111），
> 脚本见 `backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/migration-5.78-erp-finance.sql`。

---

## 十、非功能与安全

| 主题 | 要求 |
|---|---|
| 性能 | 单据列表 P95 < 300ms；凭证记账 < 100ms；驾驶舱首屏 < 1s（快照 + 缓存 60s）；MRP 1 万物料/5 层 BOM < 10min（异步） |
| 并发 | 库存扣减按仓库+物料行锁或乐观锁；凭证号按期间序列原子分配；防止重复过账 |
| 安全 | 租户隔离 + 功能权限 + 数据范围（本人/部门/全公司）；金额与银行账号在日志/审计中脱敏；导出受权限码控制 |
| 不可篡改 | `POSTED` 凭证、已审核盘点、已发放工资条禁止更新；数据库层不提供删除接口，仅红冲/作废留痕 |
| 审计 | 记账、核销、结账/反结账、信用调整、预算调整、薪资发放、处置资产、校准判定全部 `@AuditLog` |
| 批处理 | 折旧、结账、MRP、报表定时任务需分布式锁（Redisson）保证单实例执行 |
| 数据归档 | 历史年度凭证/报表按 `whale-archive` 归档；ERP 保留索引与摘要 |

---

## 十一、开放问题（编码前必须确认）

| 编号 | 问题 | 影响 | 建议默认（待确认） |
|---|---|---|---|
| D1 | 采用《企业会计准则》还是《小企业会计准则》？科目表模板来源 | 科目体系、报表格式 | 企业会计准则 + 内置标准科目表模板，可导入调整 |
| D2 | 记账本位币与多币种：汇率类型（即期/月初/月末）、汇兑损益是否自动结转 | 凭证与报表 | 本位币 CNY；支持即期+月初汇率；汇兑损益期末单独凭证 |
| D3 | 税务口径：增值税率、价内/价外税、是否管理发票 | 单据金额与凭证 | 价外税；税率表可配置；发票影像走 `whale-file`，不在 ERP 做发票开具 |
| D4 | 结账策略：月结/年结、关账后调整是否允许红冲、反结账权限 | 期间与凭证 | 月结；`CLOSED` 后仅红冲；反结账需 `sys:erp:finance:period:reopen` |
| D5 | 成本核算方法：品种法/分批法/分步法；标准成本 vs 实际成本；差异分摊规则 | 成本模块复杂度 | 分步法 + 实际成本起步，标准成本与差异后置 |
| D6 | 库存计价：FIFO / 移动加权平均 / 标准成本；是否允许负库存 | 库存与成本 | 移动加权平均；默认禁止负库存 |
| D7 | 预算控制强度：预警 / 阻断；占用时点是申请、订单还是付款 | 预算与采购流程 | 默认预警，可配阻断；占用时点=采购申请与付款 |
| D8 | 信用额度：控制时点（订单/发货）、共享范围、超限审批 | 销售流程 | 订单确认时校验；超限需审批 |
| D9 | 客户主数据归属：CRM（推荐，门禁通过后）vs ERP 业务主数据 | 与 CRM 的耦合方式 | CRM 为关系主数据上游；ERP 保留开票/信用/AR 扩展字段 |
| D10 | 供应商绩效与准入是否等 SRM，还是 ERP 先做轻量评级 | 供应商管理范围 | ERP 只维护档案与评级字段；准入/绩效等 SRM |
| D11 | MES 集成方式：REST 回调 vs 消息队列；回传粒度（工单/工序） | 生产订单闭环 | 先 REST + 内网签名，幂等键 `sourceEventId` |
| D12 | OA/Workflow 审批通过事件来源与幂等键 | 报销→凭证、请假→考勤 | 以 `approvalInstanceId` 为幂等键，事件由 workflow 发布 |
| D13 | 薪资合规：个税/社保公积金模板、工资条渠道 | HR 模块 | 参数化模板 + 月度导入；工资条经 `whale-message` 站内信 |
| D14 | 是否引入独立报表引擎（如 BI 工具） | 分析模块 | 先自建快照 + 导出，暂不引入外部 BI |

---

## 十二、M2 供应链核心实施偏差（2026-11-04）

| 设计项 | 设计稿原方案 | 实际落地 | 原因 |
|---|---|---|---|
| 表清单 | `V4__scm.sql`：采购申请/订单/收货/退货、销售报价/订单/发货/退货、库存/调拨/盘点/批次 | `V3__scm.sql`：采购订单头行、销售订单头行、库存流水、库存余额共 **6 表** | M0+M1 已合并为 `V2`，版本顺延；先做正向外贸主链路，减少一次性铺开风险 |
| 收货/发货单据 | 独立 `goods-receipt` / `shipment` 头行表 | 记在订单行 `received_quantity` / `shipped_quantity` + 库存流水单据号（`GR-/DN-`） | 首阶段只服务「收货→应付→凭证」闭环，到货批次追溯随后补 |
| 库存计价 | D6 待定（FIFO / 移动加权 / 标准成本） | **移动加权平均**，出清归零 | 不依赖批次层，先保证仓库级成本可算；D6 定稿后可切换 |
| 自动记账 | 科目映射表 + 按业务类型/仓库配置 | `ErpFinanceAccountProperties`（`erp.finance.account.*`，默认 1405/2202/1122/6001/6401） | D5 未定稿，先配置化，避免硬编码 |
| 幂等 | 业务事件 ID | `sourceType + sourceId`（`GOODS_RECEIPT`/`SHIPMENT`/`STOCK_ADJUST` + 单据号），三张表唯一索引 | 无需事件总线；MySQL 唯一索引兜底并发 |
| 信用额度 | D8 控制点与占用时点待定 | 审核与发货两个时点比对「未结应收 + 本单金额」，额度 0 视为不限 | 无预占表，并发场景留待 D8 定稿 |
| 主数据访问 | 上下文映射：SCM 只读引用 MDM | `ItemCatalog` / `SupplierCatalog` 只读端口 + 快照 DTO | ArchUnit 固化 SCM 不直接依赖 `mapper`/`entity`/存量 `service` |
| 包结构 | `scm.purchase` / `scm.sales` / `scm.inventory` 子包 | 单 `scm` 上下文内按四层分包（`domain` / `application` / `infrastructure` / `interfaces`） | 首阶段 6 表规模不需要再分子上下文，聚合仍按订单/库存分离 |

### 12.1 库存调拨与库存盘点（M2 剩余 A）

| 设计项 | 设计稿原方案 | 实际落地 | 原因 |
|---|---|---|---|
| 表清单 | 库存/调拨/盘点/批次全在 `V4__scm.sql` | `V4__scm_stock_ops.sql`：`erp_scm_transfer` / `erp_scm_transfer_line` / `erp_scm_stocktake` / `erp_scm_stocktake_line` 共 **4 表** + 科目 1901 种子 | 批次、安全库存依赖 D6/D7，先补齐调拨与盘点闭环 |
| 调拨 | 单据 + 在途仓 + 调拨价 | 双仓单步过账，调出仓出库成本即调入仓入库成本（4 位 HALF_UP） | 无跨组织交易诉求前不建在途仓，保证不产生调拨损益 |
| 盘点 | 盘点计划 + 循环盘点 + 复盘阈值 | 按仓库一次性盘点：建单快照账面/均价，过账按差异生成流水 | 循环盘点与阈值属运营增强，待 M2 剩余 B |
| 盘盈盘亏记账 | 记「待处理财产损溢」并二次结转 | 过账按净差异一张凭证：盘盈借存货/贷待处理（1901），盘亏反向；净额为零不生成 | 二次结转（费用/营业外）依赖 D5 科目映射表，暂不做 |
| 状态机 | 独立状态枚举 | 复用 `ScmStatus.POSTED` / `CANCELLED` | 与采购/销售取消语义一致，减少枚举分叉 |

> 检查报告：[2026-11-03-wave5.78-erp-m2.md](../reviews/2026-11-03-wave5.78-erp-m2.md)、[2026-11-03-wave5.78-erp-m2-stock-ops.md](../reviews/2026-11-03-wave5.78-erp-m2-stock-ops.md)。

---

## 十三、变更记录

| 日期 | 版本 | 变更 |
|---|---|---|
| 2026-11-03 | v0.1 | 首版：现状差距矩阵、7 个限界上下文、聚合与不变量、上下文映射、数据/接口/权限规划、开放问题 D1–D14 |
| 2026-11-04 | v0.2 | 回填 M0 + M1 实施偏差：13 张表、两个上下文、权限 355–367/375–387、菜单 63–66；检查报告见 `docs/reviews/2026-11-03-wave5.78-erp-m0-m1.md` |
| 2026-11-04 | v0.3 | 回填 M2 第一阶段偏差：`V3__scm.sql` 6 表、无独立收货/发货单、移动加权平均、科目映射配置化、信用时点校验；检查报告见 `docs/reviews/2026-11-03-wave5.78-erp-m2.md` |
| 2026-11-04 | v0.4 | 回填 M2 剩余 A 偏差：`V4__scm_stock_ops.sql` 4 表、调拨单步过账、盘点快照与净额凭证、状态机复用；检查报告见 `docs/reviews/2026-11-03-wave5.78-erp-m2-stock-ops.md` |
