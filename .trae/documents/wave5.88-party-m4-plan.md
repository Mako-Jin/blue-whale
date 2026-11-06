# Wave 5.88 M-P4：看板与收口（统计快照 / 通知 / 脱敏导出 / 试点复盘）实施计划

> 依据：[Wave 5.88 计划 §4 M-P4](../docs/plans/2026-11-06-wave5.88-party-union-service.md)
> 需求：REQ-FUNC-094（党政工会域）、REQ-SEC-036（党员与会员数据安全）
> 前置：M-P0–M-P3 已交付并推送（`bluewhale_party` 17 张业务表 / 84 端点 / 权限 1950–1961、1970–1980 / 菜单 470–476、481–485 / 前端 11 页 / 138 单测）
> 目标：`bluewhale_party` **20 张业务表** / **95 个端点** / 前端 14 页 / 权限 1962–1965、1981–1982 / 菜单 477、478、486
> 覆盖 M-P1 遗留：换届到期提醒（`pty_branch.established_date`）与参会率统计（`pty_activity_signin` 按支部×月份）

## 1. 现状与范围

### 1.1 已有可用的统计源

| 统计项 | 源数据 | 现状 |
|---|---|---|
| 党员结构 | `pty_member.join_party_date`（党龄） | 可算党龄；**缺出生日期与学历列**，本波补 |
| 党费收缴率 | `pty_fee_bill.due_amount/paid_amount/status` + `period_month` | 已有 |
| 三会一课开展率 | `pty_activity.planned_date/held_date/status` | 已有 |
| 参会率（M-P1 遗留） | `pty_activity_signin.present` | 已有明细，缺汇总 |
| 工会会员覆盖率 | `pty_union_member.status` | 分子已有；**分母（在职员工数）缺**，本波接 ERP 员工只读快照 |
| 工会活动参与率 | `pty_union_signup.status/signup_at` | 已有 |
| 提案办结率 | `pty_union_proposal.status` | 已有 |
| 换届到期（M-P1 遗留） | `pty_branch.established_date` | 已有日期，缺到期扫描与提醒 |

### 1.2 本波交付

1. `V6__party_metric.sql`：`pty_metric_snapshot`（统计快照）、`pty_notify_log`（通知留痕）、`pty_employee_snapshot`（ERP 员工只读快照），并对 `pty_member` 追加 `birth_date` / `education` 两列。
2. 新上下文 `analytics`（统计快照与看板）与 `integration`（ERP 员工同步、whale-message 通知、通知留痕）。
3. 权限 1962–1965（党建）/ 1981–1982（工会）、菜单 477 / 478 / 486。
4. 前端 8116 新增 3 页：党务看板、工会看板、通知与提醒。
5. 单测覆盖：指标口径、快照覆盖写、通知幂等、导出脱敏、全链试点。

## 2. 数据模型（`V6__party_metric.sql`，新增 3 张表 + 2 列）

### 2.1 `pty_metric_snapshot`（统计快照）

| 列 | 类型 | 说明 |
|---|---|---|
| `stat_date` | DATE | 统计日；党费/三会一课按该月口径，结构类指标按该日口径 |
| `metric_type` | VARCHAR(40) | 指标编码（见 §3，分布类指标把分桶编码并入 metric_type） |
| `scope_type` | VARCHAR(16) | `ALL` / `BRANCH` |
| `scope_id` | BIGINT | `ALL` 固定 0；`BRANCH` 为支部 ID |
| `scope_name` | VARCHAR(128) | 范围名称快照（支部改名不漂移历史快照） |
| `numerator` | DECIMAL(14,2) | 分子（金额或计数） |
| `denominator` | DECIMAL(14,2) | 分母；纯计数指标固定 0 |
| `computed_at` | DATETIME | 计算时刻 |

- 唯一键 `uk_pty_metric_snapshot (tenant_id, stat_date, metric_type, scope_type, scope_id)`；**覆盖写不追加**：同一统计日重刷只更新分子分母，历史曲线按 `stat_date` 天然分段。
- 索引 `idx_pty_metric_lookup (tenant_id, stat_date, scope_type, scope_id)`。
- **只存分子分母、不存比率**：比率是展示口径（保留位数、分母为 0 的展示规则）会随时间调整，落库比率等于把当时的展示规则固化进历史。

### 2.2 `pty_notify_log`（通知投递留痕）

结构与 LMS `lr_notify_log` 对齐，避免同一套「本地留痕 + 下游去重」口径出现两种写法：

| 列 | 说明 |
|---|---|
| `scene` | `PROBATION_REVIEW` / `FEE_URGE` / `ACTIVITY_NOTICE` / `PROPOSAL_FEEDBACK` / `BRANCH_TERM` |
| `idempotency_key` | 场景内幂等键（见 §7） |
| `biz_type` / `biz_id` | 冗余检索 |
| `user_id` | 接收人用户 ID |
| `template_code` / `title` / `channel` | 模板与标题；渠道固定 `INAPP` |
| `send_status` / `fail_reason` / `sent_at` | `SENT` / `FAILED`，失败可被下一轮重试覆盖为 `SENT` |

唯一键 `uk_pty_notify_log (tenant_id, idempotency_key, user_id)`；索引 `(tenant_id, scene, sent_at)`、`(tenant_id, user_id, sent_at)`。

### 2.3 `pty_employee_snapshot`（ERP 员工只读快照）

**为什么必须建**：工会会员覆盖率的分母是「在职员工数」，`pty_union_member` 只有入会的人，没有全体员工。按计划 §6 只读引用 ERP `GET /erp/internal/employee/directory`，单向同步，**不建外键、不回写**。

| 列 | 说明 |
|---|---|
| `employee_no` / `user_id` / `name` / `org_code` / `position` | ERP 目录字段窄投影 |
| `status` | 沿用 ERP 的 `ACTIVE` / `LEFT`，不做二次翻译 |
| `hire_date` / `leave_date` | ERP 原值 |
| `last_synced_at` | 最近同步时间；判断快照新鲜度，不与 `update_time` 混用 |

唯一键 `uk_pty_employee_snapshot (tenant_id, employee_no)`。同步失败**保留旧快照**并报错，不清表——把网络故障当成空目录会让覆盖率瞬间变成 100%。

### 2.4 `pty_member` 追加两列

```sql
ALTER TABLE pty_member
    ADD COLUMN birth_date DATE DEFAULT NULL COMMENT '出生日期（年龄结构统计，可空）',
    ADD COLUMN education  VARCHAR(32) DEFAULT NULL COMMENT '学历编码：DOCTOR/MASTER/BACHELOR/COLLEGE/SECONDARY/OTHER，可空';
```

- 出生日期与学历是党内统计的标准维度，且 ERP 员工目录契约不含这两项，只能由党员建档时录入。
- 两列均可空：历史档案不回填、不阻断；为空计入「未知」桶，看板明确显示未知数，而不是悄悄归入某一档。
- `ALTER TABLE` 为版本化迁移的一次性语句（MySQL 不支持 `ADD COLUMN IF NOT EXISTS`），与建表语句的 `IF NOT EXISTS` 约束不冲突。

## 3. 指标口径（编码即契约）

### 3.1 党建指标（`scope_type = ALL / BRANCH`）

| 编码 | 分子 | 分母 | 说明 |
|---|---|---|---|
| `MEMBER_TOTAL` | 在册党员数（`status=ACTIVE`） | 0 | 纯计数 |
| `MEMBER_AGE_UNDER_30` / `_30_39` / `_40_49` / `_50_59` / `_60_PLUS` / `_UNKNOWN` | 各年龄段人数（按 `stat_date` 计算周岁） | 0 | 出生日期为空入 `_UNKNOWN` |
| `PARTY_AGE_UNDER_5` / `_5_10` / `_11_20` / `_21_30` / `_31_PLUS` / `_UNKNOWN` | 各党龄段人数（入党日期起算，含预备期） | 0 | 与 `PartyMember.partyAgeYears` 同一口径 |
| `EDUCATION_DOCTOR` / `_MASTER` / `_BACHELOR` / `_COLLEGE` / `_SECONDARY` / `_OTHER` / `_UNKNOWN` | 各学历人数 | 0 | `education` 为空入 `_UNKNOWN` |
| `FEE_COLLECTION_AMOUNT` | `SUM(paid_amount)`（排除 `WAIVED`） | `SUM(due_amount)`（排除 `WAIVED`） | 金额口径收缴率 |
| `FEE_COLLECTION_BILL` | 已结清账单数（`PAID`） | 应缴账单数（排除 `WAIVED`） | 人数口径收缴率 |
| `ACTIVITY_HOLD` | 当月已完成/已归档场次（`held_date` 在当月） | 当月计划场次（`planned_date` 在当月） | 三会一课开展率，不按类型拆分 |
| `ACTIVITY_ATTENDANCE` | 当月已召开场次中 `present=1` 的签到数 | 当月已召开场次的签到记录总数 | M-P1 遗留参会率 |
| `PROBATION_DUE` | 预备党员距满一年 ≤ `probationReminderDays` 的人数 | 0 | 转正提醒的依据 |
| `BRANCH_TERM_DUE` | 距换届日 ≤ `branchTermReminderDays` 的在册支部数 | 在册支部数 | M-P1 遗留换届提醒 |

### 3.2 工会指标（`scope_type = ALL`）

| 编码 | 分子 | 分母 | 说明 |
|---|---|---|---|
| `UNION_COVERAGE` | 在册工会会员数 | 员工快照中 `status=ACTIVE` 人数 | 快照未同步时分母 0、看板提示「未同步」 |
| `UNION_ACTIVITY_PARTICIPATION` | 年年初至 `stat_date` 内 `SIGNED` 报名的去重会员数 | 在册工会会员数 | 年度累计口径，避免单月分母把参与率压到个位数 |
| `PROPOSAL_CLOSED` | 年年初至 `stat_date` 内状态为 `FEEDBACK` / `RATED` 的提案数 | 同期新建提案总数 | 办结率 |

### 3.3 比率展示

`MetricSnapshot.rate()`：分母为 0 返回 `0.00`；否则 `分子×100/分母` 保留 2 位（`HALF_UP`）。**看板同时展示分子分母**，避免只给一个百分比导致无法核对。

## 4. 接口清单（新增 11 个，累计 95）

**党务看板 / 快照 / 导出（4）**
- `GET /party/metric/overview?statDate=&branchId=`：结构、收缴、三会一课、参会率、待转正、换届到期 + 近 30 日趋势；缺快照时实时计算但不落库（`live=true`）。
- `POST /party/metric/refresh?statDate=`：计算 `ALL` + 全部在册支部并覆盖写快照。
- `GET /party/metric/export?statDate=`：CSV，仅聚合数与范围名，**不含任何个人字段**。
- `GET /party/branch/term-due?withinDays=`：换届到期支部列表（支部名、成立日期、换届日期、剩余天数、书记），供提醒与人工核对。

**工会看板 / 快照 / 导出（3）**
- `GET /party/union/metric/overview?statDate=`
- `POST /party/union/metric/refresh?statDate=`
- `GET /party/union/metric/export?statDate=`

**通知（3）**
- `GET /party/notice/page`：通知留痕分页（场景 / 接收人 / 状态 / 失败原因）。
- `POST /party/notice/scan?scene=`：手动触发党务提醒扫描（`PROBATION_REVIEW` / `FEE_URGE` / `ACTIVITY_NOTICE` / `BRANCH_TERM`）。
- `POST /party/union/notice/scan`：触发工会活动通知扫描（仅 `ACTIVITY_NOTICE`）。

**集成（1）**
- `POST /party/integration/employee/sync`：ERP 员工只读快照同步（分页拉取、按 `employee_no` upsert、失败保留旧数据）。

写接口统一 `@RequirePermission` + `@AuditLog`；分页复用 `PageParams`，上限取 `PartyProperties.notifyPageLimit`。

## 5. 权限 / 菜单 / 角色（`migration-5.88-party-m4.sql`，幂等）

### 5.1 权限

| 编号 | 权限码 | 说明 | 授权 |
|---|---|---|---|
| 1962 | `sys:party:metric:list` | 党务看板与换届到期查看 | `party_admin`、`tenant_admin`、`branch_secretary`（书记只看本支部） |
| 1963 | `sys:party:metric:manage` | 刷新党务快照、触发党务提醒扫描、ERP 员工同步 | `party_admin`、`tenant_admin` |
| 1964 | `sys:party:metric:export` | 党务聚合数据脱敏导出 | `party_admin`、`tenant_admin` |
| 1965 | `sys:party:notice:list` | 通知留痕查看 | `party_admin`、`tenant_admin`、`union_admin` |
| 1981 | `sys:union:metric:list` | 工会看板查看与导出 | `union_admin`、`tenant_admin` |
| 1982 | `sys:union:metric:manage` | 刷新工会快照、触发工会活动通知 | `union_admin`、`tenant_admin` |

号段：1962–1965 属党建段（1950–1969）的空闲号；1981–1982 属工会段（1970–1999）的空闲号。**不越段占用**。

### 5.2 菜单

| 编号 | 名称 | 路径 | component | 权限 |
|---|---|---|---|---|
| 477 | 数据看板 | `/party/dashboard` | `party/dashboard` | `sys:party:metric:list` |
| 478 | 通知与提醒 | `/party/notice` | `party/notice` | `sys:party:notice:list` |
| 486 | 工会看板 | `/party/union/dashboard` | `party/union-dashboard` | `sys:union:metric:list` |

均挂 470 工作台之下。`branch_secretary` 进 477 时接口自动把范围收敛到其本人支部；`union_admin` 只看到 486。

## 6. 上下文与分层

```
com.bluewhale.party
├── analytics   统计快照 / 党务看板 / 工会看板 / 聚合导出
├── integration ERP 员工只读快照 / whale-message 通知 / 通知留痕
├── …（branch / member / activity / fee / content / union 不变）
```

- `PartyArchitectureTest` 的 `CONTEXTS` 追加 `analytics`（`integration` 已在门禁内，本波补实现）。
- 看板**不直接读别人的表**：沿用 LMS 做法，各上下文暴露 `metrics()` 给 analytics 调用。理由：直接写 SQL 查别人的表，任何一方改表结构都会在看板上炸出编译期发现不了的错误。
- 跨服务只通过 `integration` 的端口：`ErpEmployeeGateway`、`NotifyGateway`。

## 7. 通知与导出

### 7.1 场景、接收人、幂等键

| 场景 | 触发方式 | 接收人 | 幂等键 |
|---|---|---|---|
| `PROBATION_REVIEW` 转正提醒 | 扫描：预备党员距入党满一年 ≤ 30 天 | 党员本人（`user_id`）+ 支部书记（`secretary_user_id`） | `PARTY_PROBATION:{memberId}:{yyyyMM}` |
| `FEE_URGE` 党费催缴 | 扫描：当月 `UNPAID` / `PARTIAL` 账单 | 党员本人 | `PARTY_FEE:{billId}`（一人一月唯一，键天然唯一） |
| `ACTIVITY_NOTICE` 活动通知 | 扫描：7 日内将开始的党务计划活动 / 已发布工会活动 | 支部在册党员 / 在册会员中有 `user_id` 者 | `PARTY_ACTIVITY:{activityId}` / `UNION_ACTIVITY:{activityId}` |
| `PROPOSAL_FEEDBACK` 提案反馈 | 提案 `feedback()` 动作内即时触发（`sendQuietly`，失败不回滚提案） | 提交人 `submitter_user_id` | `UNION_PROPOSAL_FEEDBACK:{proposalId}` |
| `BRANCH_TERM` 换届提醒 | 扫描：距换届日 ≤ 90 天 | 支部书记 `secretary_user_id`（空则跳过并计入看板待办） | `PARTY_BRANCH_TERM:{branchId}:{deadlineYear}` |

- 接收人为空一律跳过，不报错：不是每个党员/会员都开通了平台账号，把「没账号」当失败会让整批扫描天天告警。
- **双层幂等**：本地 `pty_notify_log` 保证「不重发」，`whale-message` 的 `idempotencyKey` 保证「网络重试也只投一次」。
- 定时：`WhalePartyApplication` 追加 `@EnableScheduling`，新增 `PartyNoticeJob`，`cron = ${whale.party.notice.cron:0 0 9 * * *}`，逐场景调用扫描；单场景异常只记日志，不中断其余场景。

### 7.2 脱敏导出

- CSV 表头固定：`统计日,统计范围,指标编码,指标名称,分子,分母,比率(%)`。
- 导出数据源是 `pty_metric_snapshot`（**本身就是聚合表**），不含姓名、工号、手机号、身份证号、支部内个人明细。脱敏不是「导出时打码」，而是「一开始就不把个人明细带出来」。
- 单测断言：导出内容不含 11 位手机号、18 位身份证号、党员姓名；导出天数上限 `exportMaxDays=366`。

## 8. 验收（对齐计划 §4）

| 验收 | 落地方式 |
|---|---|
| ① 看板与源数据抽样一致 | 单测用固定源数据（3 名党员、2 场活动、3 张账单、2 名会员）计算快照，断言分子分母与仓储返回的原始聚合逐一相等；看板视图直接读快照，不再二次换算 |
| ② 导出脱敏 | 单测断言 CSV 表头与内容不含手机号/身份证/姓名（正则扫描） |
| ③ 通知幂等重发不重复 | 单测：同一幂等键连续投递两次，第二次返回 0，`pty_notify_log` 仅 1 行；失败后重试从 `FAILED` 覆盖为 `SENT` |
| ④ 1 个支部走通全链 | `PartyPilotChainTest`（无 Spring、内存假仓储）：建支部 → 建档党员 → 建组织生活并完成+签到 → 生成当月党费并缴费 → 刷新快照 → 断言 `MEMBER_TOTAL` / `FEE_COLLECTION_AMOUNT` / `ACTIVITY_HOLD` / `ACTIVITY_ATTENDANCE` 与操作数据一致 |
| ⑤ 快照覆盖写 | 同一 `stat_date` 刷新两次，`pty_metric_snapshot` 行数不增、分子分母取最新值 |
| ⑥ 换届与参会率（M-P1 遗留） | `GET /party/branch/term-due` 返回正确剩余天数；`ACTIVITY_ATTENDANCE` 与签到明细一致 |

## 9. 实施步骤

1. **迁移**：`V6__party_metric.sql`（3 表 + `pty_member` 两列）；`PartyFlywayMigrationScriptTest` 追加 V6 表名、唯一键、`pty_member` 新列断言。
2. **党员档案补字段**：`PartyMember` / `MemberSaveCommand` / `MemberView` / Entity / Mapper XML / `MemberPage.tsx` 增 `birthDate`、`education`（可空，学历下拉，空值显示「未知」）；`PartyMember.validate()` 校验出生日期不晚于今天、学历编码合法。
3. **analytics 上下文**：`MetricSnapshot` / `MetricType` / `MetricScope` / `MetricSnapshotRepository(+Impl/Mapper)`；`AnalyticsAppService`（党务/工会总览、刷新、导出、趋势）。
4. **各上下文 `metrics()`**：member / activity / fee / branch / union-member / union-activity / union-proposal 各增一个指标视图与聚合查询（只读，不改写业务）。
5. **integration 上下文**：`ErpEmployeeGateway(+Impl)`、`EmployeeSnapshot` / Repository / `EmployeeSnapshotAppService`（同步与在职计数）；`NotifyGateway(+Impl)`、`NotifyLog` / Repository / `NotifyAppService`（幂等投递、日志分页、五场景扫描）；`UnionProposalAppServiceImpl.feedback()` 接入 `notifyQuietly`。
6. **控制器与定时任务**：`PartyMetricController`、`PartyUnionMetricController`、`PartyNoticeController`、`PartyIntegrationController`；`PartyNoticeJob` + `@EnableScheduling`。
7. **Auth 迁移**：`migration-5.88-party-m4.sql`（1962–1965、1981–1982、菜单 477/478/486，全部 `NOT EXISTS` 幂等）。
8. **前端 8116**：`DashboardPage.tsx`、`UnionDashboardPage.tsx`、`NoticePage.tsx`；`api/party.ts` 追加 11 个函数与类型；`PartyLayout` 导航 +3；`App.tsx` 路由 +3；`PartyHomePage` 的 `DONE_MILESTONES` 补齐 M-P2/M-P3/M-P4。
9. **测试**：指标口径、快照覆盖写、通知幂等、导出脱敏、ERP 同步失败保留旧快照、全链试点、迁移断言；`PartyProperties` 追加 `metricTrendDays=30` / `exportMaxDays=366` / `branchTermYears=5` / `branchTermReminderDays=90` / `probationReminderDays=30` / `activityNoticeDays=7` / `noticePageLimit=100` / `noticeCron`。
10. **验证**：`mvn -o -pl backend/whale-apps/whale-party test`（目标 ≥ 160 项）+ Checkstyle 0 + ArchUnit 4/4；`frontend` 目录下 `pnpm --filter whale-party lint` / `build`。

## 10. 文档同步（完成后同批提交）

- `docs/plans/2026-11-06-wave5.88-party-union-service.md`：状态行、§2 编号（权限 1962–1965 / 1981–1982、菜单 477/478/486）、§4 M-P4 ✅、§8 步骤 5、§9、§10 变更记录。
- `docs/plans/roadmap.md` 13.9 标 ✅。
- `docs/requirements/register.md` REQ-FUNC-094 转「M-P4 已交付」，REQ-SEC-036 补「看板聚合与导出脱敏」。
- `docs/service-catalog.md`：顶部新段、服务行（20 表 / 95 端点 / 前端 14 页 / 权限 1962–1965、1981–1982 / 菜单 477/478/486）、8116 与 9009 端口行。
- `docs/devlog/2026-11-06.md` 追加 M-P4 章节；新建 `docs/reviews/2026-11-06-wave5.88-party-m4.md`（含**试点复盘**章节：试点支部、全链步骤、抽样核对结果、遗留）。
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-doc-consistency.ps1` 须 PASS；精确 `git add` 本波文件后提交并推送 `origin/main`（不触碰 5.89 邮箱波次未提交内容，不 pop stash）。

## 11. 不做与遗留

- **不做会员/党员个人画像看板**：看板只出聚合数，点开到个人仍是台账页；避免把敏感字段复制进第二套展示链路。
- **不做跨服务强一致**：员工快照、覆盖率允许滞后，看板标注快照时间与 `live` 状态。
- **不做图表库引入**：前端用 Ant Design 的 `Statistic` / `Progress` / `Table` 呈现，不新增 ECharts 依赖。
- **不做党费逐笔会费流水**（工会会费仍为会员档案上的计量口径，与 M-P3 一致）。
- 真实 MySQL 跑 `V1`–`V6` 与 `migration-5.88-party-m4.sql`、定时任务与消息投递实机验证留给部署环境。
