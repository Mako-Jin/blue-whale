# Wave 5.82 OA M1B-2b 规格：出差报告未交提醒

> 子计划：Wave 5.82 OA 全景 §7.1 M1B-2 剩余项
> 上游：M1B-2 出差申请/行程/预支（2026-11-06 已完成，提交 79563a87）
> 日期：2026-11-06

## 一、背景

M1B-2 已交付出差申请、行程、预支与报告提交，但「审批通过且出差结束后未提交报告」没有提醒机制，
员工容易遗漏，行政/HR 也无法主动发现。本轮补齐该剩余项。

## 二、需求项

| 编号 | 需求 | 验收 |
|---|---|---|
| R1 | 扫描已通过且报告待提交、出差结束日期已超过宽限期的出差单 | 仅 `status=APPROVED`、`report_status=PENDING`、`end_date <= 今天-宽限天数` |
| R2 | 向申请人发送站内信提醒 | 走 `whale-message` 内网端点，模板 `OA_TRIP_REPORT_REMINDER` |
| R3 | 提醒按天幂等，不重复打扰 | 幂等键 `OA_TRIP_REPORT:{tripId}:{yyyyMMdd}` |
| R4 | 提醒失败不影响任务整体 | 单条异常仅 warn，继续处理下一条 |
| R5 | 开关、cron、宽限天数、批量可配置 | `whale.oa.trip.report-reminder.*` |
| R6 | 申请人无用户ID时按账号解析 | 复用 `UserDirectoryGateway` 精确匹配，失败跳过并 warn |

## 三、非目标

- 不做邮件/短信渠道编排（沿用消息服务渠道配置）。
- 不做逾期升级、主管通报或扣分处理。
- 不做报告内容质量校验。
- 不解决「预算超限条件分支」——该验收项依赖 Workflow 运行期条件表达式，另立切片。

## 四、关键决策

| 决策 | 说明 |
|---|---|
| 提醒范围 | 仅审批通过的出差单；驳回/撤销/待审批不提醒 |
| 宽限期 | 默认 3 天（`days-after=3`），给员工留出返程与整理时间 |
| 幂等 | 消息服务按 `(tenantId, idempotencyKey)` 去重；键含出差单ID与自然日 |
| 接收人 | 优先 `applicant_id`；为空时用申请人账号解析用户ID |
| 依赖方向 | Job（interfaces）→ 应用服务（application）→ 领域仓储/网关（domain），不直接依赖 infrastructure |

## 五、配置

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `whale.oa.trip.report-reminder.enabled` | `true` | 总开关 |
| `whale.oa.trip.report-reminder.cron` | `0 0 9 * * *` | 每天 09:00 执行 |
| `whale.oa.trip.report-reminder.days-after` | `3` | 出差结束后第几天开始提醒 |
| `whale.oa.trip.report-reminder.batch-size` | `100` | 单次最多扫描条数 |

## 六、模板

`whale-message` 新增 `migration-5.86-oa-m1b-trip-reminder.sql`：

- `template_code`：`OA_TRIP_REPORT_REMINDER`
- 标题：`${destination}出差报告提交提醒`
- 正文：出差目的地 / 结束日期 / 报告状态 / 提交入口

## 七、待做项（本轮不做）

1. 预算超限触发审批条件分支（依赖 Workflow 条件表达式运行期求值）。
2. 报销单关联 `trip_id`（依赖 M2 报销域）。
3. 加班费月汇总导出/推送 ERP（依赖 M2 薪资契约）。
