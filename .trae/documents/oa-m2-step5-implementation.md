# Step 5：OA M2 首批实施方案（考勤 + 报销 + 用车/用章/采购申请 + M1B 预算超限分支）

> 状态：待审批　日期：2026-11-07　上级计划：[五服务分步计划 §二](../docs/plans/2026-11-06-five-services-backlog-stepwise.md)、[OA 全景 §五/§7.2](../docs/plans/2026-11-04-wave5.82-oa-panorama.md)
> 本文为过程稿，不提交。

## 一、代码对账结论（2026-11-07）

| 项 | 现状 | 结论 |
|---|---|---|
| OA `approval` | 审批配置/审批人规则/审批实例/审批人快照/业务详情 Provider/审批中心（我发起/待我审批/我已审批/抄送）、代理规则、催办、监控、对账 Job 已齐备；`ApprovalInstanceAppServiceImpl` 提供 register（幂等）/attachWorkflow/syncResult；`ApprovalResultSyncedEvent` 支持业务结果回写 | 可复用，M2 各业务只需实现 `ApprovalBusinessDetailProvider` + 结果监听 |
| OA `hr` | 请假/加班/出差全闭环（创建→状态机→审批结果同步→额度/调休副作用），`OvertimeAppServiceImpl` 是可照抄的模板 | 新业务按同一分层复制 |
| OA `integration` | `OutboxAppService`（append 幂等 / listRetryable / markSent / markFailed）已实现 | 报销/采购推送直接复用 |
| OA `attendance` / `expense` / `administrative` | 仅 `package-info.java` | 本批全新建 |
| OA 迁移 | V1–V6；下一个版本 V7、V8 | 按计划 V7 考勤、V8 报销/行政 |
| 权限/菜单 | OA 已用 1600–1679（请假 168–171、审批中心 1642–1646、加班 1660–1663、出差 1664–1667）；菜单 360–372 | **M2 用 1680–1729 / 373–382**，实施前仍需 grep 全仓确认未占用 |
| Workflow 条件分支 | `ConditionGuard` 用 SpEL 受限求值；`ProcessInstanceServiceImpl.start` 接收 `variables`；`ApproveCommand` 合并实例变量并落 `variables_json`；`StateMachine.pickTransition` 运行期求值 | **M1B 预算超限分支可落地**：OA 发起时传 `amount`，流程定义网关条件 `amount > 阈值`，OA 不新增引擎能力 |
| ERP 应付/采购 | `ArApController` 有手工建应付（sourceType/sourceId 幂等）；`PurchaseRequisitionController` 有采购申请建单/审核/转单，但**无 `/erp/internal/**` 供 OA 调用的端点** | 需新增 ERP 内网端点：报销→应付单、采购申请→采购订单草稿（或采购申请） |
| Contract 印章 | `ContractSealController` 有印章列表/授权（`sealService.grants`），但均为公网权限 `sys:contract:seal:*`，**无内网授权校验端点** | 需新增 `/contract/internal/seal/{id}/grant-check`（只读） |
| 前端 OA | 已有审批中心/人事 6 页 + `api/client.ts` 封装；8083 骨架可扩展 | 每业务新增 `api/*.ts` + `pages/*` + App 菜单/路由 |

## 二、分批（按依赖，逐批提交）

### 5a 考勤（V7__attendance.sql）

**表**（计划 §7.2 口径，逻辑外键、不加物理 FK）：

- `oa_att_group`（考勤组：编码/名称/班次/规则/地点/设备绑定）
- `oa_att_group_member`（组成员，`UK(tenant_id, group_id, user_id)`）
- `oa_att_shift`（班次：上下班时间、弹性分钟、休息时长）
- `oa_att_rule`（规则：迟到/早退/缺卡阈值、外勤允许、补卡次数/月）
- `oa_att_location`（打卡地点：名称/经纬度/半径）
- `oa_att_device`（打卡设备：设备码/类型/启停）
- `oa_att_record`（原始打卡：用户、时间、类型 IN/OUT、来源 APP/DEVICE/WEB、经纬度、地址、设备码；`UK(tenant_id, user_id, punch_time, punch_type)` 幂等）
- `oa_att_daily`（日汇总：日期、班次、应出勤/实出勤、迟到/早退/缺卡分钟、状态 NORMAL/LATE/EARLY/ABSENT/LEAVE；`UK(tenant_id, user_id, work_date)`）
- `oa_att_exception`（异常：类型、日期、状态 PENDING/CONFIRMED/WAIVED、处理人）
- `oa_att_correction`（补卡申请：日期、时间、原因、状态机、审批实例ID）

**领域规则**：

- 打卡按班次窗口判定：早于上班时间 + 弹性内 → NORMAL；晚于上班时间超过弹性 → LATE；下班同理 EARLY；无卡 → ABSENT（有已通过请假/出差当日记录则记 LEAVE/OFFICIAL）。
- 日汇总可由「重算」端点按日期区间重算（删除当日旧汇总后再插入，或在服务层 upsert）。
- 补卡：仅对过去 7 天且月次数不超规则上限；提交走审批（business_type `ATT_CORRECTION`），通过后写入 `oa_att_record` 并重算当日汇总。
- 考勤管理员可按组管理班次/规则/地点/设备；普通员工只看自己。

**接口/权限**（1680–1689）：

| 路径 | 权限 |
|---|---|
| `GET /oa/attendance/records/page`、`POST /oa/attendance/records`（打卡） | 1680 `sys:oa:attendance:record:list` / 1681 `record:add` |
| `GET /oa/attendance/daily/page`、`POST /oa/attendance/daily/recalculate` | 1682 `daily:list` / 1683 `daily:recalculate` |
| `GET/POST/PUT/DELETE /oa/attendance/groups`、`/shifts`、`/rules`、`/locations`、`/devices` | 1684–1687 分组管理（list/add/edit/delete 合并为 4 个码，path 多值） |
| `GET/POST /oa/attendance/exceptions`、`PUT /{id}/status` | 1688 `exception:handle` |
| `GET/POST /oa/attendance/corrections`、`PUT /{id}/status` | 1689 `correction:edit` |

菜单：373 考勤打卡、374 考勤日报、375 考勤规则（组/班次/地点/设备同页 Tab）、376 补卡与异常。

**测试**：迟到/早退/缺卡/请假抵扣判定、补卡月上限、重复打卡幂等、日汇总重算幂等、权限与架构测试。

### 5b 报销 + M1B 预算超限分支（V8__expense_admin.sql 前半）

**表**：

- `oa_expense`（单号、申请人、部门、费用类型、合计金额、币种、状态 PENDING/APPROVED/REJECTED/CANCELLED、审批实例ID、ERP 推送状态 NONE/PENDING/SENT/FAILED + 应付单号、备注）
- `oa_expense_item`（明细：费用类别、日期、金额、说明、税额；`UK(tenant_id, expense_id, line_no)`）
- `oa_expense_invoice`（发票：代码/号码、金额、税额、开票日期、`fileId`；`UK(tenant_id, invoice_code, invoice_no)` 拦截重复）
- `oa_expense_category`（费用类别主数据：编码/名称/科目/单笔限额/是否需发票）
- `oa_expense_standard`（费用标准：类别 × 职级/城市 → 限额）

**规则**：

- 合计 = 明细金额之和（服务端重算，不接受客户端合计）。
- 每张发票全局唯一（跨报销单重复拦截），发票金额合计不得超过明细合计。
- 提交时按类别标准校验超标准（超标准仅提示/禁止可配置；默认禁止）。
- 审批通过后：写 `oa_expense` 为 APPROVED，同一事务内 append Outbox `OA_EXPENSE_APPROVED`（幂等键 `expense.id`）；Job 推送 ERP 内网建应付（`sourceType=OA_EXPENSE, sourceId=expenseId`），成功回写 `erp_payable_no`+SENT，失败 FAILED 可重试；ERP 不可用不影响审批通过。
- **M1B 预算超限条件分支**：`oa_approval_config` 新业务类型 `EXPENSE` → 流程定义 `OA_EXPENSE`；发起时 `variables = {amount, categoryCode, deptId}`；流程定义网关条件 `amount > 10000` 走「财务总监」分支，否则「部门经理」。OA 只负责传变量与审批人规则（`DEPT_LEADER` / `ROLE:oa_finance`），不解析分支。

**接口/权限**（1690–1699）：报销单 list/add/edit/delete/submit、明细/发票随单、类别与标准管理、ERP 推送重试。

菜单：377 报销申请、378 报销台账、379 费用类别与标准（管理员）。

**测试**：合计重算、发票重复拦截、超标准拦截、审批通过只推一次（Outbox 幂等）、ERP 失败可重试、条件变量传递。

### 5c 用车 / 用章 / 采购申请（V8 后半 + 契约补充）

**用车**：

- `oa_vehicle`（车牌、车型、座位、状态、里程）
- `oa_driver`（姓名、手机、驾照、状态）
- `oa_vehicle_booking`（申请人、用车时间段、起终点、事由、车辆/司机、状态机、审批实例ID）
- `oa_vehicle_usage`（出车/回车里程、油费、备注）
- 规则：同车时间段重叠拒绝；仅 APPROVED 可派车；回车登记写 usage 并回写里程。

**用章**：

- `oa_seal_use`（申请人、印章ID（Contract 主数据）、用章事由、文件数、用印时间、状态机、登记时间/登记人、审批实例ID）
- `oa_seal_use_file`（`fileId`、文件名）
- 规则：印章必须来自 Contract 且申请人对该印章有授权（内网 `grant-check`）；无授权不可选；审批通过后「登记」幂等（同一用章单只能登记一次，写用印时间/登记人）。

**采购申请**：

- `oa_purchase_request`（单号、申请人、部门、事由、期望到货日、合计金额、状态机、审批实例ID、ERP 推送状态/采购订单号）
- `oa_purchase_item`（物料编码/名称、数量、单价、金额、供应商建议）
- 规则：审批通过 → Outbox `OA_PURCHASE_APPROVED` → ERP 内网创建采购申请/订单草稿（`sourceType=OA_PURCHASE, sourceId` 幂等），回写单号；失败可重试。

**契约补充**：

- ERP 新增 `ErpInternalOaController`（`/erp/internal/oa/**`）：`POST /payable`（sourceType/sourceId 幂等）、`POST /purchase-order/draft`（幂等）；网关 internal-deny 已覆盖 `/erp/internal/**`。
- Contract 新增 `ContractInternalSealController`（`/contract/internal/seal`）：`GET /granted?userId=`（当前用户可用印章列表）、`GET /{sealId}/grant-check?userId=`；网关需把 `/contract/internal/**` 加入 internal-deny（若尚未加入）。

**接口/权限**（1700–1729）：用车 list/add/edit/delete + 派车/回车；用章 list/add/register；采购 list/add/edit/delete + 推送重试。

菜单：380 用车管理、381 用章申请、382 采购申请。

### 5d 前端与文档

- `frontend/platform-apps/whale-oa/src/api/attendance.ts|expense.ts|administrative.ts`（类型 + 函数，沿用 `client.ts`）。
- 页面：`pages/attendance/{punch,daily,rules,correction}.tsx`、`pages/expense/{apply,ledger,category}.tsx`、`pages/admin/{vehicle,seal,purchase}.tsx`；`App.tsx` 注册菜单与路由。
- 文档回填：五服务计划 Step 5 状态、OA 全景 M2 进度、`service-catalog.md` V7/V8、`docs/devlog/2026-11-07.md`；`scripts/check-doc-consistency.ps1` → PASS。
- 权限脚本：`migration-5.82-oa-m2-attendance.sql`、`migration-5.82-oa-m2-expense-admin.sql`（幂等 `NOT EXISTS`）。

## 三、通用实现约定（照抄 M1B）

- 分层：`interfaces(controller/dto)` → `application(service/command/view/listener)` → `domain(model/repository)` → `infrastructure(persistence/client)`；错误码统一进 `OaErrorCode`（40013+ / 40416+ / 40917+）。
- 审批接入：业务提交后调用 `ApprovalInstanceAppService.register`（幂等），负责解析审批人（`DEPT_LEADER`/`ROLE`）；结果通过 `ApprovalResultSyncedEvent` 监听回写状态并做副作用。
- 无外部调用入本地事务：Outbox append 与业务落库同事务；Job 推送在事务提交后；推送幂等键 `(tenant_id, event_key)`。
- 单号：复用 `SequenceAppService`，前缀 `ATT/EXP/VEH/SEAL/PUR`。

## 四、验收

- 后端：`mvn -o -pl backend/whale-apps/whale-oa test` 全绿；ERP/Contract 变更模块各自测试全绿。
- 前端：`pnpm --filter whale-oa lint` / `build`。
- 迁移：`OaFlywayMigrationScriptTest` 覆盖 V7/V8 幂等与禁用 DROP/DELETE。
- 文档一致性：`powershell scripts/check-doc-consistency.ps1` → PASS。
- 红线：不 `git add` 商城 5.94/5.95、`whale-pay`、`.trae/documents/**`；每批精确暂存并推送 `origin/main`。

## 五、风险与裁剪

- 考勤设备真实对接、地图选点、移动端适配不在本批（只做数据模型与录入）。
- 报销发票 OCR/查验不接外部服务，只做录入与查重。
- 用章签章执行仍归 Contract；OA 只登记「已用印」事实。
- 若 ERP 内网端点来不及，5c 可先落 Outbox + 手工重试页，ERP 端点作为同批后端补充（不拆第 4 波）。
