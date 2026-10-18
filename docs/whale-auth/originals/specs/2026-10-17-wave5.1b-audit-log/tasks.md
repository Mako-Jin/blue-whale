# Tasks：Wave 5.1b 审计日志落地

> 关联 spec：[spec.md](./spec.md) ｜ 关联需求：REQ-SEC-001
> 执行顺序即下表顺序；每项完成后按 REQ-QA-002 留痕，未通过构建不得提交。

| # | 任务 | 依赖 | 优先级 | 验收 | 状态 |
|---|---|---|---|---|---|
| [x] B1 | 文档先行：spec/tasks 落地 + `audit-log.md` 落点修正 + `register.md` REQ-SEC-001 推进「实现中」 | 无 | 高 | 文档一致可追溯 | ✅ 已完成 |
| [x] B2 | starter：`OperateType` + `@AuditLog` 注解 | B1 | 高 | 编译通过，注解可被 AOP 绑定 | ✅ 已完成 |
| [x] B3 | starter：`AuditLogRecord` 实体 + `AuditLogMapper` | B2 | 高 | 字段与 init.sql 21 列一一对应 | ✅ 已完成 |
| [x] B4 | starter：`SensitiveDataMasker`（递归脱敏 + 4KB 截断） | B3 | 高 | 6 类规则 + 嵌套/数组/深度用例通过 | ✅ 已完成 |
| [x] B5 | starter：`AuditLogAspect`（计时/上下文采集/SpEL targetId/afterCommit 异步落库） | B3、B4 | 高 | 成功/异常两路径单测通过，主流程零阻塞 | ✅ 已完成 |
| [x] B6 | starter：`AuditLogAutoConfiguration` + `AutoConfiguration.imports` + pom(test 依赖) | B5 | 高 | 有 DataSource 才生效；无 mapper 不生效 | ✅ 已完成 |
| [x] B7 | starter 单测：脱敏器 + 切面（mock Mapper / MockHttpServletRequest） | B4、B5 | 高 | 新增用例全绿 | ✅ 已完成（15/15） |
| [x] B8 | whale-audit：Query DTO + VO + Service(+Impl) + Controller + pom + datasource 配置 | B3 | 高 | 分页条件查询返回 `Result<PageResult>` | ✅ 已完成 |
| [x] B9 | whale-audit 单测：分页映射 / 条件构造 / size 上限 | B8 | 中 | 用例全绿 | ✅ 已完成（6/6） |
| [x] B10 | whale-auth：login/logout/创建用户/分配角色/分配权限 埋点 | B6 | 高 | 注解落位；既有 19 用例不回归 | ✅ 已完成 |
| [x] B11 | 构建验证：全量编译 + 3 模块 test + Checkstyle | B7、B9、B10 | 高 | 编译 SUCCESS / 单测全绿 / 0 违规 | ✅ 已完成 |
| [x] B12 | 留痕收口：`docs/reviews/2026-10-17-wave5.1b-audit-log.md` + devlog §14 + register/task-breakdown/next-steps 更新 | B11 | 中 | 四类信息齐备 | ✅ 已完成 |
| [x] B13 | 自动 commit + push（Gitee origin/main，多 `-m`） | B12 | 中 | 工作区干净且 `main == origin/main` | ✅ 已完成 |
| [x] 附 | `checklist.md` 验收清单落地并逐项核验 | B1 | 中 | 清单项全部通过 | ✅ 已完成 |

> 对照验收清单见 [checklist.md](./checklist.md)。实现与 spec 的偏差（包名 / `maskArguments` vs `maskParams` / `getDetail` vs `getById` / `targetId="#id"`）已在 [检查报告](../../reviews/2026-10-17-wave5.1b-audit-log.md) 第三节登记，以代码事实为准。

## 待做项（本次明确不做，登记备查）
- T-AUD-8 保留与归档定时任务
- T-AUD-9 阶段二 MQ 汇聚（RabbitMQ 消费落库）
- `POST /audit/log/export` 导出（依赖 whale-file 异步任务）
- `@Sensitive` 注解兜底 + `whale.audit.sensitive.keys` 规则配置化
- whale-audit 侧 `@RequirePermission("sys:audit:log:list")`（需引入 whale-auth-sdk + 权限拦截器）
- 运行时/端到端验证（依赖 Docker 中间件，当前阻塞）
