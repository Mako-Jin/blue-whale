# Wave 5.24 任务分解（并行执行）

> 规格：[spec.md](spec.md)。执行原则：**先冻结契约（spec §三）→ 轨内并行 → 模块级自检 → 收尾串行全量回归**。
> 文件所有权：T1~T4 同属 `whale-workflow` 模块（同一执行单元串行完成）；T3 的种子追加落在 `whale-auth` db（跨模块仅追加，无冲突）。

## 并行轨与任务

| 编号 | 任务 | 落点模块 | 依赖 | 产物 | 自检 |
|---|---|---|---|---|---|
| **T1** | whale-workflow 骨架接线：`pom.xml` 增 `whale-spring-boot-starter`；`application.yml` 修端口自引用 `${server.port:8083}` → `${SERVER_PORT:8083}`，补 datasource(`bluewhale_workflow`)/mybatis-plus/`whale.permission.header.enabled=true` | `whale-services/whale-workflow` | 无 | 2 文件 | `mvn -o -pl ... test` |
| **T2** | whale-workflow 业务：`ProcessDefinition`(继承 `BaseEntity`) + `ProcessDefinitionMapper` + `ProcessQueryRequest`(继承 `PageQuery`) + `ProcessDefinitionSaveRequest` + `ProcessDefinitionVO` + `ProcessDefinitionService(+Impl)` + `WorkflowController`（5 接口 + `@RequirePermission`） | 同上 | T1 | 8 文件 | 同上 |
| **T3** | DDL 与种子：`whale-workflow/db/init.sql`（`process_definition` 表 + 建库注释）；whale-auth `db/init.sql` 追加 `sys:workflow:add/edit/delete/list`（id 40–43） | `whale-workflow`、`whale-services/whale-auth` | 无 | 1 新建 + 1 追加 | 人工核对 |
| **T4** | 单测：`ProcessDefinitionServiceImplTest`（新增/更新/删除/详情/分页 + code 查重 + definition 超限），Mockito `@InjectMocks` 风格 | `whale-services/whale-workflow` | T2 | 1 测试类 | `mvn -o -pl ... test` + `checkstyle:check` |
| **E2E** | 建库与冒烟（串行，收尾段）：建 `bluewhale_workflow` + 执行 `process_definition` DDL；`bluewhale_auth.sys_permission` 插入 id 40–43；起 8083 经网关 `/workflow/**` 冒烟 | 运行时 | T2/T3 | 冒烟证据 | 运行时 |
| **E** | 收尾（串行，主执行）：全量 `mvn clean install -T 1C` → 检查报告 `docs/reviews/2026-10-19-wave5.24-workflow.md` → devlog 追加 → roadmap/task-breakdown 勾选 → commit + push origin | 全仓 | T1–E2E | 报告 + 日志 + 提交 | 全量 SUCCESS + 冒烟证据 |

> **T1~T4 同属 `whale-workflow` 模块**（由同一执行单元串行完成）。
> **T3 的 whale-auth 种子追加**为跨模块追加写，与 whale-form 种子（id 36–39）同文件不同行，手工核对无冲突。

## 执行波次

```
Wave 5.24-a（并行）：T1~T4（whale-workflow 服务 + DDL + 种子 + 单测）
Wave 5.24-b（串行）：E2E（建库 + /workflow 冒烟）
Wave 5.24-c（串行）：E 收尾
```

## 完成定义（DoD）

- `whale-workflow` 模块 `test` + `checkstyle:check` 全绿（0 违规）；全量 `mvn clean install -T 1C` BUILD SUCCESS。
- 运行时证据：whale-workflow 经网关 `/workflow/**` 全链路 `code=0`（重复 code 400 / 删后 404）。
- `docs/reviews/` 检查报告 + `docs/devlog/2026-10-19.md` 追加 + 台账勾选 + commit & push origin（Gitee）。
