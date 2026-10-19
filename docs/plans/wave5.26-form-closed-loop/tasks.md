# Wave 5.26 任务分解（并行执行）

> 规格：[spec.md](spec.md)。执行原则：**先冻结契约（spec §三）→ 轨内并行 → 模块级自检 → 收尾串行全量回归**。
> 文件所有权：全部落在 `whale-form` 单一模块（同模块内由同一执行单元串行完成）；仅种子追加跨到 `whale-auth` db（纯追加，无冲突）。

## 并行轨与任务

| 编号 | 任务 | 落点 | 依赖 | 产物 | 自检 |
|---|---|---|---|---|---|
| **A-a** | 表单数据实体与映射：`FormData`（继承 `BaseEntity`）+ `FormDataMapper` | `whale-form` | 无 | 2 文件 | 编译 |
| **A-b** | 请求/视图：`dto/{FormDataSubmitRequest,FormDataQueryRequest}` + `vo/FormDataVO` | 同上 | 无 | 3 文件 | 编译 |
| **B** | 服务与接口：`FormDataService(+Impl)`（`submit`/`getDetail`/`page`/`delete`）+ `FormDataController`（4 接口） | 同上 | A-a/A-b | 3 文件 | 编译 |
| **C-a** | DDL：`whale-form/db/init.sql` 追加 `form_data` 表 | 同上 | 无 | 1 文件（追加） | 人工核对 |
| **C-b** | 权限种子：whale-auth `db/init.sql` 追加 `sys:form:data:*`（49–51） | `whale-auth` | 无 | 1 文件（追加） | 人工核对 |
| **C-c** | 单测：`FormDataServiceImplTest`（提交成功/定义不存在/数据超长/详情/详情不存在/删除/删除不存在/分页）+ `checkstyle:check` | `whale-form` | B | 1 测试类 | `mvn -o -pl :whale-form -am test checkstyle:check` |
| **E2E** | 建表与种子落库 + 经网关 `/form/**` 闭环冒烟（提交→分页/详情→删除后 404；负向 400/404） | 运行时 | B/C-a/C-b | 冒烟证据 | 运行时 |
| **E** | 收尾：全量 `mvn clean install -T 1C` → 检查报告 → devlog → roadmap/task-breakdown 勾选 → commit + push origin | 全仓 | 全部 | 报告 + 日志 + 提交 | 全量 SUCCESS + 冒烟证据 |

> **A-a/A-b/B/C-c 同属 `whale-form` 模块**（同一执行单元串行完成，先 A 后 B 再 C-c）。
> **构建注意**：全量 `clean install` 前须停运行中的 7 个服务（gateway/auth/job/workflow/form/file/audit）以释放 `target/*.jar` 锁，构建后再以 `Start-Process` 拉起并核验健康。

## 执行波次

```
Wave 5.26-a（模块内串行）：A-a → A-b → B → C-a → C-b → C-c（whale-form 数据闭环 + DDL + 种子 + 单测）
Wave 5.26-b（串行）：E2E（建表 + 种子落库 + /form 闭环冒烟）
Wave 5.26-c（串行）：E 收尾
```

## 完成定义（DoD）

- `whale-form` 模块 `test` + `checkstyle:check` 全绿（0 违规）；全量 `mvn clean install -T 1C` BUILD SUCCESS。
- 运行时证据：经网关 `/form/**` 闭环（提交→分页/详情→删除后 404 / 负向 400·404）全绿。
- `docs/reviews/` 检查报告 + `docs/devlog/2026-10-19.md` 追加 + 台账勾选 + commit & push origin（Gitee）。
