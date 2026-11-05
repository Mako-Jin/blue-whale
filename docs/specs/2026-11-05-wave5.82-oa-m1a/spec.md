# Wave 5.82 OA M1A（人事与请假闭环切片）规格

> 状态：**M1A-2 / M1A-3 / M1A-5 / M1A-6（人事部分）/ M1A-7 已实现（2026-11-05）**
> 上级计划：[Wave 5.82 OA 全景计划](../../plans/2026-11-04-wave5.82-oa-panorama.md) §6.2–§6.4
> 关联需求：REQ-FUNC-073（请假）、REQ-FUNC-074（员工档案）、REQ-ARCH-023（OA 模块化单体）、REQ-SEC-033（OA 权限与数据范围）

---

## 一、目标

在不新建服务、不新建数据库的前提下，为 `whale-oa`（9004 / `/oa/**` / `bluewhale_oa` / 前端 8103）补齐「请假闭环 + 年度额度 + 员工扩展档案 + 假期类型 + 工作日历」的人事底座，并把审批结果回写与额度副作用打通。

## 二、约束

- C-1 表结构只新增，`oa_leave` 仅幂等加列；禁止 `DROP/TRUNCATE/DELETE` 行首语句与物理外键。
- C-2 姓名、部门、手机号仍归 Auth，`oa_employee_profile` 只存 HR 专属字段，不复制通讯录。
- C-3 额度语义固定：提交冻结 → 通过释放冻结并扣减已用 → 驳回/撤销释放冻结；所有变动写流水。
- C-4 默认 `used + frozen ≤ total`；`oa_leave_type.allow_negative = 1` 时放行并允许负数可用。
- C-5 天数按工作日历计算：整天 1、AM/PM 各 0.5、非工作日 0；无日历记录时按周一至周五兜底。
- C-6 同一申请人同类型时间区间重叠拒绝提交；状态为 `PENDING/APPROVED` 视为占用。
- C-7 审批结果同步幂等：重复同步同一终态不重复扣减；终态不可再改。
- C-8 部门主体 ACL 与附件强校验延后到 M1A-4 / M1B；本期不暴露裸 `fileId`。

## 三、数据模型（V4__oa_m1a_leave.sql）

| 表 | 关键字段与约束 |
|---|---|
| `oa_employee_profile` | `tenant_id + user_id` 唯一；工号、入职/转正/离职日期、生日、在职状态、文化墙展示 |
| `oa_leave_type` | `tenant_id + code` 唯一；单位 DAY/HOUR、年度默认额度、最小单位、需附件、允许负额度、启用、排序 |
| `oa_leave_balance` | `tenant_id + user_id + leave_type_id + balance_year` 唯一；总额度、已用、冻结 |
| `oa_leave_balance_log` | 只追加；action（INIT/ADJUST/FREEZE/RELEASE/DEDUCT）、天数、前后快照、业务类型与单据 |
| `oa_work_calendar` | `tenant_id + calendar_date` 唯一；是否工作日、标准工时、节假日名称 |
| `oa_leave`（扩展） | 新增 `applicant_id`、`leave_type_id`、`start_period`、`end_period`、`handover_user_id`、`approval_instance_id` |

种子：`ANNUAL`（年假 5 天）、`SICK`（病假 10 天、需附件、可负额度）、`PERSONAL`（事假 0 天、可负额度）。

## 四、接口

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/oa/hr/profile/page`、`/oa/hr/profile/{userId}` | `sys:oa:hr:profile:list` | 员工档案分页与详情 |
| POST / DELETE | `/oa/hr/profile`、`/oa/hr/profile/{userId}` | `sys:oa:hr:profile:edit` | 按 userId 幂等保存与逻辑删除 |
| GET | `/oa/hr/leave-type/page`、`/oa/hr/leave-type/enabled` | `sys:oa:hr:leave-type:list` | 假期类型分页与启用列表 |
| POST / PUT | `/oa/hr/leave-type`、`/{id}`、`/{id}/toggle` | `sys:oa:hr:leave-type:edit` | 新增、修改、启停 |
| GET | `/oa/hr/leave-balance/page`、`/my`、`/{id}/logs` | `sys:oa:hr:balance:list` | 额度分页、本人额度、流水 |
| POST / PUT | `/oa/hr/leave-balance/init`、`/{id}` | `sys:oa:hr:balance:edit` | 按启用类型初始化年度额度、调整总额 |
| GET | `/oa/hr/calendar/page` | `sys:oa:hr:calendar:list` | 日历区间/月份查询 |
| PUT | `/oa/hr/calendar/{date}` | `sys:oa:hr:calendar:edit` | 按日期幂等 upsert |
| POST / PUT / DELETE | `/oa/leave/**`（沿用） | `sys:oa:leave:*` | 创建时计算天数、查重叠、冻结额度 |

## 五、审批联动

`ApprovalInstanceAppServiceImpl.syncResult` 在终态同步成功后发布 `ApprovalResultSyncedEvent`；`hr` 侧 `LeaveApprovalResultListener` 监听 `businessType = LEAVE` 并调用 `LeaveAppService.syncApprovalResult`，映射为 `APPROVED` / `REJECTED` / `CANCELLED` 后走幂等状态迁移与额度副作用。方向保持 `hr → approval`，不违反 ArchUnit 上下文依赖规则。

## 六、验收

1. 提交 2 天年假 → 冻结 2 天；通过 → 释放冻结、已用 +2；重复同步通过不再扣减。
2. 驳回/撤销/删除 → 冻结释放；修改日期或类型 → 先释放旧冻结再冻结新额度。
3. 半天、跨天、跨周末按工作日历计算；区间内无工作日拒绝。
4. 同类型时间重叠拒绝；额度不足拒绝（不允许负额度的类型）。
5. 普通员工访问档案/类型/额度维护 → 403；跨租户按 ID 查询返回 404。
6. 后端测试全绿、前端 lint/build 通过。

## 七、不在本期范围

- M1A-1 审批中心四类列表与 Workflow 动作代理、抄送、催办、代理设置。
- M1A-4 通知模板、自动催办、审批状态对账 Job、附件强制校验。
- 移动端专项适配与冒烟脚本。
