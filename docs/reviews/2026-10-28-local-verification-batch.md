# 本地验证批次：V-01 / V-02 / V-03 / V-04 / V-14（2026-10-28）

## 1. 结论

| 项 | 结果 | 证据 |
|---|---|---|
| V-01 表单↔工作流跨模块 E2E | **关闭** | 新增 `form-workflow-loop.spec.ts`，3 条新 E2E 合跑 `3 passed (51.5s)` |
| V-02 form-designer 拖拽人工复核 | **关闭** | 新增 `form-designer-drag.spec.ts`，真实鼠标拖拽保存成功；截图人工复核通过 |
| V-03 admin 角色/菜单删除与角色分配 UI | **关闭** | 新增 `admin-role-menu-ui.spec.ts`，UI 动作 + API 核验全部通过 |
| V-04 Agent 服务目录重建 <5s | **关闭** | `FactCollectorTest#shouldRebuildServiceCatalogWithinFiveSeconds`，实跑 **57ms** |
| V-14 熔断半开运行态取证 | **关闭** | 新增 `bw-gateway-circuit-breaker-smoke.ps1`，真实故障注入 `RESULT: PASS` |

本批关闭 5 项，待办未闭环数 **20 → 15**。

## 2. 环境与前置

- 代码同步：`git fetch origin --prune` 后 `main...origin/main` 为 `0 0`，已是最新；工作区既有未提交改动保留。
- 本机中间件：MySQL 8.0.26（3306）、Redis、Nacos 8848/9848；RabbitMQ 沿用本机既有节点。
- 后端：gateway/auth/form/workflow；前端由 Playwright `webServer` 启动 admin/form-designer/workflow-designer。
- **发现并修复前置问题**：`whale-gateway.jar` 为 10-26 旧构建，路由仍指向 `lb://whale-auth`，而 auth 已注册为 `whale-service-auth`，导致登录 503。重新执行 `mvn -pl :whale-gateway -am -DskipTests package` 后恢复。该问题属于构建产物陈旧，不是代码回归。

## 3. V-01 表单填报 → 发起流程 → 审批通过

- 用例：[`frontend/e2e/tests/form-workflow-loop.spec.ts`](../../frontend/e2e/tests/form-workflow-loop.spec.ts)
- 准备：API 建表 → 发布 → 建表单 → 建入口 `USER_TASK` 绑定 `formId/formCode` 的流程定义。
- UI：登录工作流设计器 → 流程发起 → 选择定义 → 填业务标题与表单字段 → 发起 → 我的待办 → 审批抽屉 → 同意。
- 核验：
  - 实例 `businessKey` 非空且等于新建表单数据 ID；
  - 表单详情 `fields.e2e_name` 等于 UI 填报值；
  - 审批抽屉只读表单字段值与发起值一致；
  - 审批后实例 `status=1`，任务轨迹 `status=1`，表单数据仍可读。
- 结果：通过。与 V-02/V-03 合跑 `3 passed (51.5s)`。

## 4. V-02 form-designer 真实鼠标拖拽

- 用例：[`frontend/e2e/tests/form-designer-drag.spec.ts`](../../frontend/e2e/tests/form-designer-drag.spec.ts)
- 为画布补 `data-testid="form-designer-canvas"`，避免依赖 Ant Design 内部结构。
- 操作：API 建表/发布/建空表单 → 打开 `/design/{formId}?tableDefinitionId=...` → Playwright `dragTo` 将「姓名」调色板条目拖入画布 → 保存。
- 核验：画布出现「姓名」卡片、调色板显示「已使用」；保存后 API 返回 `fields[0].columnMetaId/fieldLabel/widgetType` 正确。
- 截图：`frontend/e2e/test-results/form-designer-drag-数据字段拖入画布并保存/form-designer-drag.png`，人工复核通过。
- 说明：此前「合成 PointerEvent 不可复现」的结论只适用于 `dispatchEvent`；真实鼠标事件可稳定复现。

## 5. V-03 admin UI 复验

- 用例：[`frontend/e2e/tests/admin-role-menu-ui.spec.ts`](../../frontend/e2e/tests/admin-role-menu-ui.spec.ts)
- 覆盖：
  1. 角色管理 → 分配权限：勾选首个权限 → 保存 → API `/role/{id}/permissions` 包含该权限；
  2. 用户管理 → 分配角色：勾选新建角色 → 保存 → API `/user/{id}/roles` 包含该角色；
  3. 解除用户占用后，角色管理 → 删除 → API 查询不再返回该角色；
  4. 菜单管理 → 删除 → API 菜单树不再返回该菜单。
- 结果：通过。Ant Design 的两个汉字按钮实际可访问名为「查 询 / 删 除」，用例已用正则兼容。

## 6. V-04 服务目录重建性能回归

- 测试：[`backend/whale-services/whale-agent/src/test/java/com/bluewhale/agent/support/facts/FactCollectorTest.java`](../../backend/whale-services/whale-agent/src/test/java/com/bluewhale/agent/support/facts/FactCollectorTest.java)
- 新增 `shouldRebuildServiceCatalogWithinFiveSeconds`：自动向上定位仓库根，直接读取 `docs/service-catalog.md` 与网关配置，断言事实非空且耗时 < 5000ms。
- 实跑：`FactCollectorTest` 4 用例 0 失败，该用例耗时 **0.057s**。
- 命令：`mvn -f backend/pom.xml -pl :whale-agent -Dtest=FactCollectorTest test -q`

## 7. V-14 熔断半开运行态取证

- 脚本：[`scripts/smoke/bw-gateway-circuit-breaker-smoke.ps1`](../../scripts/smoke/bw-gateway-circuit-breaker-smoke.ps1)
- 故障注入：使用 `whale-audit` 路由，测试时该服务无实例，形成真实下游不可用。
- 观测：
  1. 连续 20 次请求返回 503，网关日志出现 `route=whale-audit, CLOSED -> OPEN`；
  2. 打开后请求被短路，返回 503「服务暂时不可用」；
  3. 等待 11s 后首个探测请求触发 `OPEN -> HALF_OPEN`；
  4. 探测仍失败，日志出现 `HALF_OPEN -> OPEN`。
- 结果：`RESULT: PASS`。
- 说明：本项只要求半开运行态取证，未验证 HALF_OPEN→CLOSED 恢复；恢复路径仍由既有单测覆盖。

## 8. 未闭环与风险

- 本批未触碰外部凭据类项：S-04d-2、S-04e-2、F-03b、V-06、V-11 仍阻塞。
- V-07/V-08/V-09/V-10 仍受 Gitee Go、GHCR、Let's Encrypt、独立构建机限制。
- F-11 仍受本机 RabbitMQ 既有节点控制面（cookie/服务账号）限制；Docker compose 已使用 `rabbitmq:4-management-alpine` 且映射 15672，生产容器路径本身已具备管理插件。
- 本机 MySQL 为手工进程，未安装/变更 Windows 服务；验证结束后 MySQL、Redis、Nacos、gateway/auth/form/workflow 均已停止，环境恢复原状。
- 网关重建暴露的「旧 jar 路由指向旧服务名」建议纳入 CI：构建产物必须与当前源码同步，E2E 前先 `package` 目标模块。
