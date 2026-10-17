# Checklist：Wave 5.1b 审计日志落地

> 关联 spec：[spec.md](./spec.md) ｜ 关联 tasks：[tasks.md](./tasks.md) ｜ 关联需求：REQ-SEC-001
> 验证方式：无 Docker 前提下的静态验证（编译 + 单测 + Checkstyle + 文档留痕）。

- [x] B1 文档先行：`spec.md`/`tasks.md` 已落地；`docs/design/audit-log.md` §5.1 落点已按 D1 修正为 `whale-spring-boot-starter`；`register.md` REQ-SEC-001 状态已推进
- [x] B2 `whale-spring-boot-starter` 新增 `OperateType` 枚举与 `@AuditLog` 注解（`module/operation/type/targetType/targetId(SpEL)/recordParams/recordResult`），可被 `@Around("@annotation(...)")` 绑定
- [x] B3 `AuditLogRecord` 21 字段与 `audit_log` 建表列一一对应，`@TableId(ASSIGN_ID)`，**不继承 `BaseEntity`**；`AuditLogMapper extends BaseMapper`
- [x] B4 `SensitiveDataMasker` 覆盖密码/令牌/手机号/身份证/银行卡/邮箱 6 类规则 + 嵌套/数组/Bean 递归 + 深度上限 8 + 参数 JSON 4096 截断
- [x] B5 `AuditLogAspect` 成功与异常两路径均可落库（`biz_status` 1/0 + `error_msg`），事务内注册 `afterCommit`、无事务直提，写入失败仅告警且异常原样抛出
- [x] B6 `AuditLogAutoConfiguration` 条件装配（Web Servlet + Mapper Bean + 必要类），并已登记到 `AutoConfiguration.imports`
- [x] B7 `whale-spring-boot-starter` 单测全绿（`SensitiveDataMaskerTest` 9 + `AuditLogAspectTest` 6 = 15）
- [x] B8 `whale-audit` 交付 `AuditLogQueryRequest`/`AuditLogVO`/`AuditLogService(+Impl)`/`AuditLogController` + `pom` 依赖 + `spring.datasource`，接口返回 `Result<PageResult>`
- [x] B9 `whale-audit` 单测全绿（`AuditLogServiceImplTest` 6：分页映射 / size 上限 200 / NOT_FOUND / traceId 聚合）
- [x] B10 `whale-auth` 5 处埋点落位（login/logout/create/assignRoles/assignPermissions），且在 auth 的 `db/init.sql` 补 `audit_log` 建表以支持同库写入
- [x] B11 构建验证：全量编译 **BUILD SUCCESS**；三模块测试 **40 用例全绿**（starter 15 + whale-audit 6 + whale-auth 19 不回归）；Checkstyle **0 违规**
- [x] B12 留痕收口：`docs/reviews/2026-10-17-wave5.1b-audit-log.md`（57 项逐条）、`devlog §14`（需求/完成/待做/已修正四类）、`design/audit-log.md`、`register.md`、`task-breakdown.md`、`next-steps.md` 均已更新
- [x] B13 提交推送：工作区干净且 `main == origin/main`（Gitee）

## 未纳入本批（与原 spec 一致，登记待做）
- [ ] 审计查询接口鉴权 `@RequirePermission("sys:audit:log:list")`
- [ ] `POST /audit/log/export` 导出、T-AUD-8 保留归档、T-AUD-9 阶段二 MQ 汇聚
- [ ] `@Sensitive` 注解兜底 + 脱敏规则配置化（`whale.audit.sensitive.keys`）
- [ ] 运行时/端到端验证（依赖 Docker 中间件，本机阻塞）
