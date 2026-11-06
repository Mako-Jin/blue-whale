# Wave 5.88 M-P3：工会（会员 / 慰问帮扶 / 福利 / 活动 / 提案）实施计划

> 依据：[Wave 5.88 计划 §4 M-P3](../docs/plans/2026-11-06-wave5.88-party-union-service.md)
> 需求：REQ-FUNC-092（工会）、REQ-SEC-036（未审/敏感数据安全，工会侧收紧）
> 前置：M-P0 / M-P1 / M-P2 已交付（`bluewhale_party` 10 张业务表 / 41 端点 / 权限 1950–1961 / 菜单 470–476)
> 目标：`bluewhale_party` **17 张业务表** / **77 个端点** / 前端 11 页 / 权限 1970–1980 / 菜单 481–485

## 1. 范围与验收（对齐计划 §4）

| 能力 | 验收 |
|---|---|
| 会员台账（入会 / 退会 / 会费） | 一人同时只能有一份在册会籍；退会为终态但保留历史；再入会新建档案而不是复活旧档 |
| 慰问与困难帮扶（workflow `UNION_RELIEF`） | 同一人同年同类：已批次数与已批金额双上限；审批不可达时申请**保存为草稿**并明确提示，不丢数据 |
| 节日福利发放（标准 / 名单 / 签收） | 名单生成幂等；签收按人幂等；发放数与签收数可对账 |
| 工会活动（报名 / 名额） | 仅已发布活动可报名；不超名额、不重复；取消后可重新报名且不留重复行 |
| 职代会提案（提交→立案→办理→反馈→评价） | 状态单向，评价后终态不可回退；非提交人不能评价 |

## 2. 数据模型（`V5__party_union.sql`，7 张表）

| 表 | 关键列 | 唯一键 / 索引 | 口径 |
|---|---|---|---|
| `pty_union_member` | `member_no`(UN{yyyy}{4 位}，取号)、`employee_no`、`user_id`、`name`、`dept_name`、`join_date`、`status`(ACTIVE/WITHDRAWN)、`withdraw_date`、`dues_base`、`dues_paid_to`、`remark` | `uk_pty_union_member_no(tenant_id,member_no)`；**不对 `employee_no` 建唯一键**；`idx_union_member_employee(tenant_id,employee_no,status)`；`idx_union_member_user(tenant_id,user_id)` | 退会后可再入会，因此不能靠唯一键禁止「同一员工两条记录」；改由应用层校验「该员工没有 ACTIVE 会籍」——唯一键会把正常再入会直接拒掉 |
| `pty_union_relief` | `relief_no`(RF 取号)、`union_member_id`、`applicant_user_id`、`relief_type`(DIFFICULTY/CONDOLENCE/MEDICAL/EDUCATION/OTHER)、`year`、`amount`、`reason`、`status`(DRAFT/SUBMITTED/APPROVED/REJECTED/PAID)、`approval_instance_id`、`approval_status`、`submit_time`、`approved_at`、`paid_at`、`approval_hint` | `uk_pty_union_relief_no`；`idx_union_relief_cap(tenant_id,union_member_id,relief_type,year,status)` | `year` 独立落库（按申请年度计上限），不用 `apply_date` 现算；`approval_hint` 保存降级提示，便于列表直接展示「审批服务不可用，已存草稿」 |
| `pty_union_welfare` | `welfare_no`(WF 取号)、`title`、`year`、`standard_amount`、`status`(DRAFT/ISSUED/CLOSED)、`issued_at`、`closed_at`、`remark` | `uk_pty_union_welfare_no`；`idx_union_welfare_year(tenant_id,year,status)` | 标准金额在发放时快照到明细；后续调标不改历史 |
| `pty_union_welfare_item` | `welfare_id`、`union_member_id`、`employee_no`、`member_name`、`amount`、`sign_status`(UNSIGNED/SIGNED)、`signed_at`、`signed_by` | `uk_pty_union_welfare_item(tenant_id,welfare_id,union_member_id)` | 发放名单重复生成时幂等跳过，不追加 |
| `pty_union_activity` | `activity_no`(UA 取号)、`title`、`activity_type`、`start_time`、`end_time`、`location`、`quota`、`status`(DRAFT/PUBLISHED/CLOSED/CANCELLED)、`remark` | `uk_pty_union_activity_no`；`idx_union_activity_time(tenant_id,start_time,status)` | 名额为 0 表示不限；发布后时间地点冻结 |
| `pty_union_signup` | `activity_id`、`union_member_id`、`user_id`、`status`(SIGNED/CANCELLED)、`signup_at`、`cancelled_at` | `uk_pty_union_signup(tenant_id,activity_id,union_member_id)` | 取消后再报名只把状态改回 `SIGNED`，不新增行——否则「取消两次再报名」会在名单里留下多条 |
| `pty_union_proposal` | `proposal_no`(UP 取号)、`title`、`content`、`submitter_user_id`、`submitter_name`、`status`(SUBMITTED/FILED/HANDLING/FEEDBACK/RATED)、`handler`、`filed_at`、`handled_at`、`feedback`、`feedback_at`、`rating`(1–5)、`rated_at` | `uk_pty_union_proposal_no`；`idx_union_proposal_status(tenant_id,status,create_time)` | 每次流转只写当前节点字段，不覆盖前序；无「审核轨迹表」，M-P3 只保留节点时间与内容 |

> 附件字节归 `whale-file`，表内只存 `fileId`；员工主数据只读引用 ERP（`employee_no` / `dept_name` 快照）。

## 3. 领域状态机

- **UnionMember**：`ACTIVE → WITHDRAWN`（终态）。入会校验员工编号必填、`join_date` 不晚于今天；退会写 `withdraw_date`；存在 ACTIVE 会籍时同员工再入会拒绝（`UNION_MEMBER_DUPLICATED`）。
- **UnionRelief**：`DRAFT → SUBMITTED → APPROVED / REJECTED`；`APPROVED → PAID`（终态），`REJECTED` 终态（如需再申请另建单）。提交前校验：金额 > 0、类型合法、同人同年同类型 **已批（APPROVED/PAID）次数 ≤ `reliefCountLimit`** 且 **金额合计 + 本次 ≤ `reliefAmountLimit`**。仅 SUBMITTED 可同步审批结果；仅 APPROVED 可登记发放。
- **UnionWelfare**：`DRAFT → ISSUED → CLOSED`（终态）。发放时按入参名单（不传则全部 ACTIVE 会员）生成明细并快照金额；`ISSUED` 才可签收；关闭后不可再签收。
- **UnionActivity**：`DRAFT → PUBLISHED → CLOSED`（终态）；`DRAFT/PUBLISHED → CANCELLED`（终态）。报名仅 `PUBLISHED` 且未开始前；取消报名仅 `PUBLISHED`；名额 `quota > 0` 时按当前 `SIGNED` 数校验。
- **UnionProposal**：`SUBMITTED → FILED → HANDLING → FEEDBACK → RATED`（终态，单向，不可跳步/回退）。立案与办理为工会管理员动作；反馈由办理人填写；评价仅提交人本人且必须处于 `FEEDBACK`。

## 4. 权限 / 菜单 / 角色（`migration-5.88-party-m3.sql`，幂等）

| 编号 | 权限码 | 说明 |
|---|---|---|
| 1970 | `sys:union:browse` | 工会园地：活动列表/详情、报名/取消、我的报名、提案提交、我的提案、我的福利与签收 |
| 1971 | `sys:union:member:list` | 会员台账查看 |
| 1972 | `sys:union:member:manage` | 入会 / 编辑 / 退会 / 会费基数 |
| 1973 | `sys:union:relief:list` | 慰问帮扶查看 |
| 1974 | `sys:union:relief:manage` | 申请 / 提交 / 同步 / 登记发放 |
| 1975 | `sys:union:welfare:list` | 福利查看（含名单） |
| 1976 | `sys:union:welfare:manage` | 福利新建 / 编辑 / 发放 / 关闭 |
| 1977 | `sys:union:activity:list` | 工会活动查看（含报名名单） |
| 1978 | `sys:union:activity:manage` | 活动新建 / 编辑 / 发布 / 关闭 / 取消 |
| 1979 | `sys:union:proposal:list` | 提案查看 |
| 1980 | `sys:union:proposal:manage` | 立案 / 办理 / 反馈 |

- 菜单 481 工会会员 / 482 慰问帮扶 / 483 节日福利 / 484 工会活动 / 485 职代会提案（parent 470，component `party/union-*`）。
- 角色授权：`tenant_admin` / `union_admin` 拿 1970–1980 全部；`party_admin` 只拿 1970（可浏览园地，不参与工会资金业务）；普通账号按需由租户管理员授予 1970。
- `union_admin` 已在 `PartyActor` 预留；新增 `canManageUnion()`（`tenant_admin` / `union_admin`），资金类操作（发放、登记发放、关闭）**只认这两个角色**，不认 `party_admin`。

## 5. 接口清单（36 个，累计 77）

**会员（5）** `/party/union/member`
`GET /page`、`GET /{id}`、`POST /`、`PUT /{id}`、`POST /{id}/withdraw`

**慰问帮扶（7）** `/party/union/relief`
`GET /page`、`GET /{id}`、`POST /`、`PUT /{id}`、`POST /{id}/submit`、`POST /{id}/sync`、`POST /{id}/pay`

**节日福利（8）** `/party/union/welfare`
`GET /page`、`GET /{id}`、`POST /`、`PUT /{id}`、`POST /{id}/issue`、`GET /{id}/items`、`POST /item/{itemId}/sign`、`POST /{id}/close`

**工会活动（9）** `/party/union/activity`
`GET /page`、`GET /{id}`、`POST /`、`PUT /{id}`、`POST /{id}/publish`、`POST /{id}/close`、`POST /{id}/cancel`、`POST /{id}/signup`、`GET /{id}/signups`、`POST /{id}/signup/cancel` → 计 10（含取消）

**职代会提案（7）** `/party/union/proposal`
`GET /page`、`GET /{id}`、`POST /`、`POST /{id}/file`、`POST /{id}/handle`、`POST /{id}/feedback`、`POST /{id}/rate`

合计 5 + 7 + 8 + 10 + 7 = **37**，累计 **78**。写接口统一 `@AuditLog` + `@RequirePermission`；分页沿用 `PageParams`（`current/pageNum`、`size/pageSize`），上限取 `PartyProperties.unionPageLimit`。

## 6. 跨服务契约：workflow `UNION_RELIEF`

- 领域端口 `ReliefApprovalGateway`（`union/domain/gateway`），基础设施实现 `WorkflowReliefApprovalGateway`（`union/infrastructure/client`），沿用 PDM 的 `DiscoveryClient + InternalTokenSupport.restClient()` 方式，**不新增公共依赖**。
- 发起：`POST /workflow/internal/instance/start`，`definitionCode` 取 `party.union.relief-definition-code`（默认 `UNION_RELIEF`），`businessKey = UNION_RELIEF:{reliefId}`，variables 带 `reliefId/amount/type/year`。
- 状态：`GET /workflow/internal/instance/status?instanceIds=`，映射 `1→APPROVED`、`2→REJECTED`；`0/3` 保持 `SUBMITTED` 并提示。
- **降级**：发起失败或未配置定义编码 → 不抛事务异常、不回滚已保存数据，`status` 保持 `DRAFT`，`approval_hint` 记「审批服务不可用，已保存为草稿」，接口返回 `approvalStarted=false`。这条是验收⑤：宁可让用户看到「没提交成功」，也不能假装成功。
- 流程定义本体（`UNION_RELIEF` 节点配置）不属本服务迁移，由 workflow 侧按既有流程定义维护；本服务只认编码。

## 7. 前端（8116，新增 5 页）

- `UnionMemberPage.tsx`：会员列表（关键字/状态）、入会/编辑 Drawer、退会、会费基数与缴至月份。
- `UnionReliefPage.tsx`：申请列表、申请 Drawer（类型/金额/事由）、提交（降级时提示草稿）、同步审批、登记发放。
- `UnionWelfarePage.tsx`：福利批次列表、新建/编辑、发放（可选名单或全量）、明细 Drawer（签收/未签收对账、按人签收）、关闭。
- `UnionActivityPage.tsx`：活动列表/详情、新建/编辑、发布/关闭/取消、报名名单、本人报名与取消。
- `UnionProposalPage.tsx`：提案列表、提交、立案/办理/反馈、我的提案、评价。
- `PartyLayout.tsx` 导航追加 5 项；`App.tsx` 追加 5 条路由（`union/member`、`union/relief`、`union/welfare`、`union/activity`、`union/proposal`）；`api/party.ts` 追加类型与 37 个函数。

## 8. 测试

- 领域规则（`UnionDomainRuleTest`）：会籍状态机与再入会、慰问上限（次数/金额边界）、福利发放幂等与快照、活动名额与重复报名、提案单向流转与评价归属。
- 应用服务（`UnionAppServiceImplTest`）：降级草稿、审批状态同步映射、名单生成跳过已存在、取消后重报不新增、越权（非提交人评价、非工会管理员发放）。
- 迁移断言（`PartyFlywayMigrationScriptTest`）：7 张表、各唯一键、`pty_union_member` 无 `employee_no` 唯一键。
- 回归：`mvn -o -pl backend/whale-apps/whale-party test`（目标 ≥ 115 项）+ Checkstyle 0 + ArchUnit 4/4；前端 `pnpm --filter whale-party lint` / `build`。

## 9. 文档同步（完成后同批提交）

- `docs/plans/2026-11-06-wave5.88-party-union-service.md`：状态行、§2 编号（权限 1970–1980 / 菜单 481–485）、§4 M-P3 ✅、§8 步骤 4、§9、§10 变更记录。
- `docs/plans/roadmap.md` 13.8 标 ✅。
- `docs/requirements/register.md` REQ-FUNC-092 转「M-P3 已交付」，REQ-SEC-036 补「工会金额与发放权限已落地」。
- `docs/service-catalog.md`：顶部新段、服务行（17 表 / 78 端点 / 权限 1970–1980 / 菜单 481–485）、前端行（累计 11 页）、8116 / 9009 端口行。
- `docs/devlog/2026-11-06.md` 追加 M-P3 章节；新建 `docs/reviews/2026-11-06-wave5.88-party-m3.md`。
- `powershell -File scripts/check-doc-consistency.ps1` 须 PASS；提交并推送 `origin/main`。

## 10. 不做与遗留

- **工会会费不建流水表**：计划只给 7 张表，M-P3 在会员档案上记 `dues_base` 与 `dues_paid_to`（缴至月份），逐笔收缴流水、欠缴清单与收缴率顺延 M-P4 看板（若确需流水，届时另立 M-P4 迁移）。
- 节日福利资格规则（工龄/在岗/部门）不做自动匹配，发放名单由管理员选择或取全量在册会员。
- workflow 定义编码未在真实库预置时，本波只验证「降级成草稿」，不代 workflow 建流程定义。
- 通知（发布/退回/活动提醒/提案反馈）统一随 M-P4 接 `whale-message`。
- 真实 MySQL 跑 `V1`–`V5` 与 `migration-5.88-party-m3.sql`、起服冒烟留给部署环境。
