# Wave 5.82 OA M1B-2b 任务清单：出差报告未交提醒

> 规格：[spec.md](spec.md)
> 日期：2026-11-06

## 一、实现任务

| 编号 | 任务 | 状态 | 产物 |
|---|---|---|---|
| T1 | 出差仓储新增“报告待提交且已结束”查询 | 完成 | `TripRepository` / `TripRepositoryImpl` |
| T2 | 新增出差报告提醒应用服务 | 完成 | `TripReportReminderAppService` + Impl |
| T3 | 新增每日提醒定时任务 | 完成 | `TripReportReminderJob` |
| T4 | 新增消息模板种子 | 完成 | `migration-5.86-oa-m1b-trip-reminder.sql` |
| T5 | 配置项落地 | 完成 | `application.yml` |
| T6 | 单元测试（应用服务 / 定时任务 / 迁移种子） | 完成 | 3 个测试类 |
| T7 | 构建、Checkstyle、ArchUnit 验证 | 完成 | [检查报告](../../reviews/2026-11-06-wave5.82-oa-m1b-trip-reminder.md) |
| T8 | 回填计划、路线图、devlog | 完成 | 文档更新 |

## 二、验收核对

- [x] 仅审批通过且报告待提交的出差单被扫描
- [x] 未超过宽限期的出差单不提醒
- [x] 同一出差单同一天只提醒一次（幂等键）
- [x] 单条投递失败不中断批处理
- [x] 申请人为空且账号解析失败时跳过并 warn
- [x] 开关关闭时不查询、不发送

## 三、已修正问题项

| 编号 | 问题 | 修正 |
|---|---|---|
| F1 | 规划期口径称“出差报告未交提醒依赖 M1A-4 通知能力”，但 M1B-2 交付时未落地 | 本切片复用既有 `ApprovalNotifyGateway` 补齐 |
| F2 | 全景计划验收项把“报告提醒”与“预算条件分支”混在同一验收行 | 拆分：提醒本轮完成，条件分支单独登记待做 |
