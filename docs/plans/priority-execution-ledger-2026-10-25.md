# 优先级执行台账（2026-10-25）

> 用途：把「当前工作空间全部待办」按优先级固化为可执行队列，逐项记录状态、依赖与验收口径，作为后续每轮执行的唯一入口。
> 口径来源：[todo-inventory-2026-10-23.md](todo-inventory-2026-10-23.md)、[next-steps.md](next-steps.md)、[roadmap.md](roadmap.md)、各服务 backlog，以及 2026-10-25 各波次收尾记录。
> 变更控制：任何一项进入编码前必须已在 [需求登记册](../requirements/register.md) 有 REQ 编号（REQ-DOC-002）。本轮新增 REQ-ARCH-006（多租户隔离）、REQ-FUNC-016（一人多租户切换）。
> 状态取值：`待启动` / `进行中` / `待验证` / `已完成` / `已阻塞`。完成即划线（REQ-DOC-003）。

---

## 一、执行队列

| 序号 | 优先级 | 事项 | 状态 | 依赖 / 阻塞 | 验收口径 |
|---|---|---|---|---|---|
| 0 | P0 | 落地优先级执行台账 + 登记多租户切换需求 | **已完成**（2026-10-25） | 无 | 本文件 + register.md 中 REQ-ARCH-006 / REQ-FUNC-016 |
| 1 | P0 | Wave 5.54 部署验证与存量数据/对象迁移 | 待启动 | **需用户提供 MySQL 口令与 mysql CLI 路径**（`MYSQL_PASSWORD` 为空）；Redis 6379 / Nacos 8848 未监听 | 逐库执行 `migration-5.54-tenant-*.sql` 幂等通过；对象键 `tenants/` 前缀迁移；评估稿 §十四 冒烟与 E2E 通过 |
| 2 | P0 | S4 一人多租户（Phase 5.3 / 5.4 剩余） | **进行中**（5.1/5.2/5.3 已完成；5.4 E2E 待联调环境） | 5.4 需 Redis/Nacos/MySQL 与双租户数据 | 5.3 成员列表 + 租户内角色分配 + 平台绑定/解绑（已完成，见 [5.3 检查报告](../reviews/2026-10-25-wave5.54-phase5.3-tenant-members.md)）；5.4 双租户 E2E 与角色隔离回归 |
| 3 | P0 | Wave 5.53 部署脚本执行与密级矩阵复测 | 待启动 | 同上（需 MySQL 口令）；`bluewhale_agent`/`whale-auth` 迁移未执行 | `sys:archive:secret:read` 授权落库；档案快照三级权限矩阵真机复测 |
| 4 | P0 | Wave 5.52 剩余项（Gitee Go AI 阶段、未读汇总帧等） | **已完成**（2026-10-25；实机触发待 Gitee 侧） | 远端触发需 Gitee Go 环境 | 未读汇总帧已于 2026-10-24 完成；`.workflow/ai-ci.yml` 补齐 uv 3.12 + pytest 阶段，与 GitHub `ai-pytest` 对齐 |
| 5 | P1 | 需求登记册状态校准 + REQ-ARCH-002 多数据库设计 | **已完成**（2026-10-25） | 无 | 登记册全量校准（仅 REQ-ARCH-002 需更新，其余与代码事实一致）；产出 [design/multi-database.md](../design/multi-database.md)（现状基线 + 方言选择 + DDL 映射 + T1–T6 + 风险） |
| 6 | P2 | 网关补 5 条路由 + 基座 `/mes` `/pdm` 挂载 | **已完成**（2026-10-25；冒烟待环境） | 路由冒烟需服务与 Nacos 就绪 | 5 条 `lb://` 路由已写入网关 yml；基座挂载 `/mes/*`、`/pdm/*` 并补菜单/图标；`whale-platform` 构建通过 |
| 7 | P2 | portal 三处占位收口 + 基座 3 个 PlaceholderPage | 待启动 | 依赖待办统计/通知/工作流接口 | Dashboard 真实统计；待办「去处理」跳转 `/workflow/task/{id}`；通知中心接真实接口；用户/角色/菜单页收口 |
| 8 | P2 | alert 通知渠道 + monitor Prometheus 接入 | 待启动 | 需邮件/Webhook 配置与 Prometheus 栈 | 至少邮件 + Webhook；monitor 可抓取/存储并供 ops 展示 |
| 9 | P3 | Phase 4 业务应用（OA 请假闭环）+ CI/部署收口 | 待启动 | 依赖 P2 网关路由与前端占位收口 | `whale-apps` OA 域 DDL→六端点→权限种子→路由→前端页面闭环；T-CI-7 镜像扩面 + coverage-gate 接 CI |

---

## 二、执行规则

1. **文档先行**：编码前先更新评估稿/需求登记；每批代码产出 `docs/reviews/` 检查报告。
2. **单步闭环**：一项完成 → 构建/单测/Checkstyle 验证 → 提交并推送 `origin/main` → 更新本台账状态。
3. **阻塞不重试**：需要口令、外部环境或人工操作的项标记 `已阻塞`，先推进不依赖它的下一项。
4. **完成即划线**：已完成项保留删除线与完成结论，不删除历史。

## 三、进度记录

| 时间 | 事项 | 结论 |
|---|---|---|
| 2026-10-25 | 序号 0 | 台账落地；register.md 新增 REQ-ARCH-006 / REQ-FUNC-016 并重排章节号为六/七/八 |
| 2026-10-25 | 序号 2（5.1/5.2） | 后端切换能力 commit `9a6b679`；admin 顶栏切换器 commit `1bef439`；`whale-auth` 330 用例全绿、Checkstyle 0，admin lint/build 通过 |
| 2026-10-25 | 序号 2（5.3） | 成员/角色/绑定/解绑 4 端点 + 权限 165–167 + 菜单 17 + admin 成员页；`whale-auth` 394 用例全绿、Checkstyle 0，admin lint/build 通过；详见 [5.3 检查报告](../reviews/2026-10-25-wave5.54-phase5.3-tenant-members.md) |
| 2026-10-25 | 序号 4 | `.workflow/ai-ci.yml` 补齐 Gitee Go AI 阶段（uv sync --frozen + pytest，Python 3.12），与 GitHub `ai-pytest` 对齐；实机触发待 Gitee 侧 |
| 2026-10-25 | 序号 5 | 登记册校准：仅 REQ-ARCH-002 由「未设计」改为「设计已定稿」；新增 [design/multi-database.md](../design/multi-database.md)（现状统计：InnoDB 31 处、ON UPDATE 29 处、MySQL 幂等语法 21 处；Mapper 无专有函数） |
| 2026-10-25 | 序号 6 | 网关补 `/erp` `/mes` `/pdm` `/oa` `/config` 5 条 `lb://` 路由；基座挂载 `/mes/*` `/pdm/*` + 菜单/图标；service-catalog C-03 关闭；`whale-platform` tsc+vite 通过 |
