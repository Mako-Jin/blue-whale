# P2 PM 审批开启态联调（2026-10-26）

> 来源：[2026-10-25 Wave 5.55 部署验证](2026-10-25-wave5.55-deployment-verification.md) 遗留第 1 条：「审批真机联调需 whale-workflow 与流程定义 ID；本期仅验证关闭态」。

## 一、范围

在 whale-pm 审批**开启态**（`PM_APPROVAL_ENABLED=true` + `PM_APPROVAL_DEFINITION_ID`）下，经网关完成「建项目 → 建工作项 → 发起审批 → 动态记录 → workflow 实例对账 → 审批通过完结」全链路。

## 二、环境补齐

- 10-26 环境重建仅建 4 库，缺 `bluewhale_pm`：本次补建并执行 `init.sql` + `migration-5.55-m2-workitem.sql` + `migration-5.55-m3-board.sql`（12 表）。
- 创建专用流程定义 `pm-workitem-approval-110330`（id=2114553480332849153，单节点「负责人审批/admin」，legacy nodes JSON 经 `/workflow/definition` 创建）。
- whale-pm 以审批开启态启动（9005）：`PM_APPROVAL_ENABLED=true`、`PM_APPROVAL_DEFINITION_ID=2114553480332849153`、`PM_NOTIFY_ENABLED=false`（隔离通知通道）、`SPRING_DATA_REDIS_PASSWORD` + Nacos 认证账号注入。

## 三、验证记录（全部经网关 8080）

| # | 步骤 | 结果 |
|---|---|---|
| 1 | 创建流程定义 | code=0，id=2114553480332849153 |
| 2 | 建项目 / 工作项 | code=0，itemId=2114554850939228161 |
| 3 | `POST /pm/work-item/{id}/approval` | code=0，返回 instanceId=2114554851396300802 |
| 4 | 工作项动态 | 记录 `field=approval`、`newValue=instanceId`、`oldValue=null` |
| 5 | `GET /workflow/instance/{id}` | definitionId 对账一致；`businessKey`=工作项ID；title=`[AP110856-1] 审批联调工作项`；status=0 运行中 |
| 6 | 待办任务 | 节点「负责人审批」、assignee=admin、status=0 |
| 7 | `POST /workflow/task/{id}/approve` | code=0；实例 status=1 完结 |
| 8 | 清理 | 工作项 / 项目删除均 code=0 |

## 四、结论

- 审批开启态链路**闭环通过**：PM 侧身份头（X-User-Id/X-Username/X-Permission-Codes/X-Tenant-Id）透传 → whale-workflow 内网直连发起 → 实例/任务/审批动作全部正常。
- 关闭态负向（`审批功能未开启`）已于 5.55 验证，开/关两态均覆盖。
- 固化回归脚本：[bw-pm-approval-smoke.ps1](../../scripts/smoke/bw-pm-approval-smoke.ps1)（自建流程定义，8 步断言 + 清理）。

## 五、残留

- 无代码变更；`whale-pm` 审批默认仍关闭（配置驱动），生产开启只需注入两个环境变量。
- whale-pm 实例（9005）当前以审批开启态在本机运行，供后续 P2-6 角色授权验证复用。
