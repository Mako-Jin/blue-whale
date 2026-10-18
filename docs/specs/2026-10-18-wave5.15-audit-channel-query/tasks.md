# Wave 5.15 · 任务清单（①② 通道查询接口补齐）

> 状态：✅ 已完成（Q8 待提交后勾选） ｜ 对应 [spec.md](./spec.md)

| 编号 | 任务 | 依赖 | 优先级 | 验收口径 | 状态 |
|---|---|---|---|---|---|
| Q1 | `dto/ApiLogQueryRequest` + `dto/OutboundLogQueryRequest`（分页 + 多维过滤，时间范围 `@DateTimeFormat`） | 无 | 高 | 继承 `PageQuery`；条件命中既有索引 | ✅ |
| Q2 | `vo/ApiLogVO` + `vo/OutboundLogVO`（字段与实体一一对应，`Serializable`） | 无 | 高 | 字段覆盖实体全部查询可见列 | ✅ |
| Q3 | `service/ApiLogService` / `OutboundLogService` 接口 + `impl` 实现（分页、`size` 收敛 `[1,200]`、`create_time` 倒序、条件开关） | Q1、Q2 | 高 | 单测可断言列缓存与排序 | ✅ |
| Q4 | `LogIngestController` 新增 `GET /audit/log/api/page`、`GET /audit/log/outbound/page`（`@RequirePermission("sys:audit:log:list")`）+ 类注释修订 | Q3 | 高 | 路径/权限/返回结构与 ③④ 一致 | ✅ |
| Q5 | 单测 `ApiLogServiceImplTest` / `OutboundLogServiceImplTest`（分页映射 / `size` 上下界 / 条件拼装 + 倒序 / 空结果） | Q3 | 高 | 全绿 | ✅ |
| Q6 | 门禁：`mvn -f backend/pom.xml -pl :whale-audit -am test` + 全 reactor `checkstyle:check` | Q4、Q5 | 高 | 模块 test 通过（含不回归）且 0 违规 | ✅ |
| Q7 | 留痕收口：检查报告 `docs/reviews/2026-10-18-wave5.15-audit-channel-query.md` + devlog 追加 + 台账销账（`register.md` / `next-steps.md` / `design/audit-log.md`） | Q6 | 中 | 四类信息齐备 | ✅ |
| Q8 | 自动 commit + push（Gitee origin/main） | Q7 | 中 | 工作区干净且 `main == origin/main` | ⏳ |
