# 待办列表执行状态报告（2026-10-25）

> 数据来源：[priority-execution-ledger-2026-10-25.md](../plans/priority-execution-ledger-2026-10-25.md)
> 汇总时点：2026-10-25 晚（commit `465c954` 之后）
> 结论：10 项待办中 **7 项已完成**、**3 项部分完成**（序号 1/2/3）；无「未开始」项。剩余缺口全部依赖外部环境（前端三应用、档案/Agent 服务、维护窗口），非代码缺口。

---

## 一、总览

| 序号 | 优先级 | 事项 | 状态 | 剩余缺口 |
|---|---|---|---|---|
| 0 | P0 | 落地优先级执行台账 + 多租户切换需求登记 | **已完成** | — |
| 1 | P0 | Wave 5.54 部署验证与存量数据/对象迁移 | **部分完成** | §14.5 E2E；Agent 一次性重建索引 |
| 2 | P0 | S4 一人多租户（Phase 5.3 / 5.4） | **部分完成** | 浏览器 E2E（`tenant-switch.spec.ts`） |
| 3 | P0 | Wave 5.53 部署脚本与密级矩阵复测 | **部分完成** | 三级权限矩阵真机复测 |
| 4 | P0 | Wave 5.52 剩余项（Gitee Go AI 阶段） | **已完成** | 远端触发待 Gitee 侧 |
| 5 | P1 | 需求登记册校准 + REQ-ARCH-002 多数据库设计 | **已完成** | — |
| 6 | P2 | 网关 5 条路由 + 基座 `/mes` `/pdm` 挂载 | **已完成** | 路由冒烟已随后续批次覆盖 |
| 7 | P2 | portal 三处占位 + 基座 PlaceholderPage | **已完成** | 浏览器 E2E 待前端 |
| 8 | P2 | alert 通知渠道 + monitor Prometheus 接入 | **已完成** | Alertmanager/邮件实机联调待栈 |
| 9 | P3 | Phase 4 业务应用（OA 请假闭环）+ CI 收口 | **已完成** | 镜像实机构建待 Docker |

完成率：**70% 已完成，30% 部分完成，0% 未开始**。

---

## 二、已完成项与证据

| 序号 | 关键产出 | 证据 |
|---|---|---|
| 0 | 台账 10 项固化；`register.md` 新增 REQ-ARCH-006 / REQ-FUNC-016 | [台账](../plans/priority-execution-ledger-2026-10-25.md) |
| 4 | `.workflow/ai-ci.yml` 补齐 Gitee Go AI 阶段（uv 3.12 + pytest），与 GitHub `ai-pytest` 对齐 | 台账进度记录（2026-10-25） |
| 5 | 登记册全量校准；新增 [design/multi-database.md](../design/multi-database.md)：InnoDB 31 处、`ON UPDATE` 29 处、MySQL 幂等语法 21 处 | 同上 |
| 6 | 网关补 `/erp` `/mes` `/pdm` `/oa` `/config` 路由；基座挂载 `/mes/*` `/pdm/*` | commit `722e3ed` |
| 7 | portal token 键修正、Dashboard 真实统计、待办审批抽屉、通知双源；基座 3 个占位页删除 | commit `20c15dc`；[检查报告](../reviews/2026-10-25-p2-portal-platform-placeholders.md) |
| 8 | 邮件/Webhook 通知、Alertmanager 入站端点 + 双令牌头、网关内网屏蔽、Prometheus/Grafana 外链 | commit `fa495ff`；[检查报告](../reviews/2026-10-25-p2-alert-notify-prometheus.md) |
| 9 | OA 请假 `oa_leave` DDL + 6 端点 + 权限 168–171 + 前端列表/表单；CI 改 `-Pcoverage-gate verify`；镜像矩阵 18 服务 | commit `ea02731`；[检查报告](../reviews/2026-10-25-p3-oa-leave-ci.md) |

---

## 三、部分完成项：已做 / 待做

### 3.1 序号 1（Wave 5.54 部署验证）

**已做**

- 新建 `bluewhale_job` / `bluewhale_pay` / `bluewhale_alert` 三库并执行 `init.sql`。
- 14 个 5.54 迁移脚本两轮执行，第二轮 **14/14 退出码 0**（幂等成立）。
- 12 个库 `tenant_id` 列覆盖校验通过；补齐 agent 知识文件表与 audit/auth 四类日志表。
- `file_info` 对象键 **7/7** 迁移为 `tenants/<tenantId>/` 前缀。
- 修复租户开通 500（菜单父子乱序导致三元自动拆箱 NPE），补回归用例；网关补 `/tenant/**` 路由。
- §14.4 API 冒烟经网关 **9 步全通过**：平台登录、租户分页、伪造租户头失效、业务租户 403、开通 `acme`（菜单 11、部门根 1、权限 24）、重复编码 400、新管理员登录、停用 401、恢复登录。

**待做**

| 项 | 阻塞 | 解除方式 |
|---|---|---|
| §14.5 Playwright E2E | 需 admin / 表单设计器 / 工作流设计器三前端 + form/workflow 服务 | 拉起三前端与后端服务后执行 `pnpm --filter @bluewhale/e2e test` |
| Agent 一次性重建索引 | 需维护窗口（全量重索引影响在线检索） | 窗口内调用 Agent 重建接口 / 脚本 |

### 3.2 序号 2（S4 一人多租户）

**已做**

- 5.1/5.2 后端切换能力 + admin 顶栏切换器（commit `9a6b679`、`1bef439`）。
- 5.3 成员列表、租户内角色分配、平台绑定/解绑 4 端点 + 权限 165–167 + admin 成员页。
- 5.4 API 级实测通过：`/auth/tenants` 2 条且 `current` 标记正确；`/auth/tenant/switch` 后 `userId=1` 不变、`tenantId` 切换、角色按目标租户重算为 `tenant_admin`、旧令牌 401。

**待做**

| 项 | 阻塞 | 解除方式 |
|---|---|---|
| 浏览器双租户 E2E（`tenant-switch.spec.ts`） | admin 前端未启动 | 拉起 admin 后纳入 Playwright 套件 |

### 3.3 序号 3（Wave 5.53 密级矩阵）

**已做**

- `whale-auth` / `whale-agent` 的 `migration-5.53.sql` 执行通过；`agent_knowledge_file` 已创建。
- `sys:archive:secret:read`（151）授予 `super_admin` 并验证落库；权限 140–151、档案菜单 11–16 校验通过。

**待做**

| 项 | 阻塞 | 解除方式 |
|---|---|---|
| 三级权限矩阵真机复测（匿名 / 条目级 / 秘密级） | 需 `whale-archive` + `whale-agent` 运行且存在已入索引的秘密级档案快照 | 造一条秘密级档案并完成索引后复测 |

---

## 四、环境现状

| 依赖 | 状态 | 说明 |
|---|---|---|
| MySQL 8 | 可用 | `127.0.0.1:3306`，12 库已迁移 |
| Redis 5.0.14.1 | 可用 | `tools` 原生进程，测试时与命令同批启动 |
| Nacos 3.2.4 | 可用 | `tools` 内 standalone，`lb://` 路由已验证 |
| 前端 admin/form/workflow | 未启动 | E2E 前置 |
| whale-archive / whale-agent | 未启动 | 密级矩阵前置 |
| Docker / WSL | 不可用 | 无管理员权限；镜像构建验证待独立环境 |

> 进程生命周期注意：`Start-Process` / 后台进程会随终端命令结束被回收，多服务验证须写成「同一条命令内启动→测试」的批次脚本。

---

## 五、下一步建议

1. **前端 E2E 批次**：一次拉起 admin + 表单设计器 + 工作流设计器 + form/workflow 服务，执行 §14.5 与 `tenant-switch.spec.ts`，可同时关闭序号 1、2 的浏览器验证缺口。
2. **密级矩阵批次**：启动 archive + agent，造秘密级档案快照并索引，复测三档可见性，关闭序号 3。
3. **维护窗口**：执行 Agent 一次性重建索引，关闭序号 1 最后一项。
4. **独立环境**：Docker 可用后跑 18 服务镜像矩阵与 OA E2E。

---

## 六、提交记录（本轮相关）

| Commit | 内容 |
|---|---|
| `9a6b679` | S4 后端租户切换能力 |
| `1bef439` | admin 顶栏租户切换器 |
| `722e3ed` | 网关业务路由 + 基座 mes/pdm 挂载 |
| `20c15dc` | portal / 基座占位收口 |
| `fa495ff` | 告警通知渠道 + Alertmanager 入站 |
| `ea02731` | OA 请假闭环 + CI coverage gate |
| `1005b0d` | 5.54/5.53 数据库迁移执行记录 |
| `465c954` | 租户开通 NPE 修复 + §14.4/S4 冒烟 |
