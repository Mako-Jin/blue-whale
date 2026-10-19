# Wave 5.25 任务分解（并行执行）

> 规格：[spec.md](spec.md)。执行原则：**先冻结契约（spec §三）→ 轨内并行 → 模块级自检 → 收尾串行全量回归**。
> 文件所有权：全部落在 `whale-workflow` 单一模块（同模块内由同一执行单元串行完成）；仅种子追加跨到 `whale-auth` db（纯追加，无冲突）。

## 并行轨与任务

| 编号 | 任务 | 落点 | 依赖 | 产物 | 自检 |
|---|---|---|---|---|---|
| **A-a** | 实例与任务实体与映射：`ProcessInstance`/`ProcessTask`（继承 `BaseEntity`）+ `ProcessInstanceMapper`/`ProcessTaskMapper` | `whale-workflow` | 无 | 4 文件 | 编译 |
| **A-b** | 节点解析：`support/ProcessNodeParser`（`definition_json` → 有序节点列表；缺失/非法抛 `PARAM_ERROR`）+ `dto/{ProcessInstanceStartRequest,ProcessInstanceQueryRequest,ProcessTaskQueryRequest,ApprovalRequest}` + `vo/{ProcessInstanceVO,ProcessTaskVO}` | 同上 | 无 | 7 文件 | 编译 |
| **B-a** | 实例服务：`ProcessInstanceService(+Impl)`（`start`/`getDetail`(含 tasks)/`page`/`cancel`）+ `ProcessInstanceController`（4 接口） | 同上 | A-a/A-b | 3 文件 | 编译 |
| **B-b** | 待办服务：`ProcessTaskService(+Impl)`（`page`/`approve`（推进）/`reject`（驳回并取消其余））+ `ProcessTaskController`（2 接口） | 同上 | A-a/A-b | 3 文件 | 编译 |
| **C-a** | DDL：`whale-workflow/db/init.sql` 追加 `process_instance`、`process_task` 两表 | 同上 | 无 | 1 文件（追加） | 人工核对 |
| **C-b** | 权限种子：whale-auth `db/init.sql` 追加 `sys:workflow:instance:*`（44–46）、`sys:workflow:task:*`（47–48） | `whale-auth` | 无 | 1 文件（追加） | 人工核对 |
| **C-c** | 单测：`ProcessInstanceServiceImplTest`（发起成功/定义不存在/无节点/撤销/非运行中/详情/分页）+ `ProcessTaskServiceImplTest`（同意推进/末节点通过/重复处理/实例非运行/驳回取消其余/分页） + `checkstyle:check` | `whale-workflow` | B-a/B-b | 2 测试类 | `mvn -o -pl :whale-workflow -am test checkstyle:check` |
| **E2E** | 建表与种子落库 + 经网关 `/workflow/**` 全闭环冒烟（发起→同意推进→末节点通过；驳回；撤销；负向 400/404） | 运行时 | B-a/B-b/C-a/C-b | 冒烟证据 | 运行时 |
| **E** | 收尾：全量 `mvn clean install -T 1C` → 检查报告 → devlog → roadmap/task-breakdown 勾选 → commit + push origin | 全仓 | 全部 | 报告 + 日志 + 提交 | 全量 SUCCESS + 冒烟证据 |

> **A-a/A-b/B-a/B-b/C-c 同属 `whale-workflow` 模块**（同一执行单元串行完成，先 A 后 B 再 C-c）。
> **构建注意**：全量 `clean install` 前须停运行中的 7 个服务（gateway/auth/job/workflow/form/file/audit）以释放 `target/*.jar` 锁，构建后再以 `Start-Process` 拉起并核验健康。

## 执行波次

```
Wave 5.25-a（模块内串行）：A-a → A-b → B-a → B-b → C-a → C-b → C-c（whale-workflow 全闭环 + DDL + 种子 + 单测）
Wave 5.25-b（串行）：E2E（建表 + 种子落库 + /workflow 全闭环冒烟）
Wave 5.25-c（串行）：E 收尾
```

## 完成定义（DoD）

- `whale-workflow` 模块 `test` + `checkstyle:check` 全绿（0 违规）；全量 `mvn clean install -T 1C` BUILD SUCCESS。
- 运行时证据：经网关 `/workflow/**` 全闭环（发起→同意推进→末节点通过 / 驳回 / 撤销 / 负向 400·404）全绿。
- `docs/reviews/` 检查报告 + `docs/devlog/2026-10-19.md` 追加 + 台账勾选 + commit & push origin（Gitee）。
