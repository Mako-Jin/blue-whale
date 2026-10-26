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
| 1 | P0 | Wave 5.54 部署验证与存量数据/对象迁移 | **已完成**（2026-10-26；库迁移、对象键、§14.4 API 冒烟、§14.5 E2E、Agent 索引重建全部闭环） | 无 | 14 个 5.54 脚本两轮幂等通过；12 库 `tenant_id` 覆盖校验通过；`file_info` 7/7 对象键带 `tenants/1/` 前缀；§14.4 经网关 9 步全通过；§14.5 E2E 3/3 通过；Agent 索引 READY（2107 文件/13438 块）；详见 [10-25 检查报告](../reviews/2026-10-25-wave5.54-5.53-deploy-execution.md) 与 [10-26 检查报告](../reviews/2026-10-26-wave5.54-e2e-agent-index.md) |
| 2 | P0 | S4 一人多租户（Phase 5.3 / 5.4 剩余） | **已完成**（2026-10-26；5.1–5.4 全部闭环，浏览器 E2E 通过） | 无 | 5.3 成员列表 + 租户内角色分配 + 平台绑定/解绑（见 [5.3 检查报告](../reviews/2026-10-25-wave5.54-phase5.3-tenant-members.md)）；5.4 后端切换实测通过 + 浏览器 E2E `tenant-switch.spec.ts` 通过（切换 acme→租户上下文生效→切回默认）；全量 E2E 4/4 绿 |
| 3 | P0 | Wave 5.53 部署脚本执行与密级矩阵复测 | **已完成**（2026-10-26；脚本与授权已落库，三级矩阵真机复测 PASS，同步修复 4 个检索链路缺陷） | 无 | `whale-auth`/`whale-agent` `migration-5.53.sql` 执行通过；`sys:archive:secret:read`（151）已授予 super_admin；权限 140–151 / 档案菜单 11–16 校验通过；三级矩阵经网关复测：e2e_none 0/0、e2e_item 密级 2 可见/密级 3 不可见、e2e_secret 均可见；详见 [检查报告](../reviews/2026-10-26-wave5.53-security-matrix.md) |
| 4 | P0 | Wave 5.52 剩余项（Gitee Go AI 阶段、未读汇总帧等） | **已完成**（2026-10-25；实机触发待 Gitee 侧） | 远端触发需 Gitee Go 环境 | 未读汇总帧已于 2026-10-24 完成；`.workflow/ai-ci.yml` 补齐 uv 3.12 + pytest 阶段，与 GitHub `ai-pytest` 对齐 |
| 5 | P1 | 需求登记册状态校准 + REQ-ARCH-002 多数据库设计 | **已完成**（2026-10-25） | 无 | 登记册全量校准（仅 REQ-ARCH-002 需更新，其余与代码事实一致）；产出 [design/multi-database.md](../design/multi-database.md)（现状基线 + 方言选择 + DDL 映射 + T1–T6 + 风险） |
| 6 | P2 | 网关补 5 条路由 + 基座 `/mes` `/pdm` 挂载 | **已完成**（2026-10-25；冒烟待环境） | 路由冒烟需服务与 Nacos 就绪 | 5 条 `lb://` 路由已写入网关 yml；基座挂载 `/mes/*`、`/pdm/*` 并补菜单/图标；`whale-platform` 构建通过 |
| 7 | P2 | portal 三处占位收口 + 基座 3 个 PlaceholderPage | **已完成**（2026-10-25；实机 E2E 待环境） | 浏览器 E2E 需 MySQL/Redis/Nacos 与全部服务就绪 | Dashboard 四项统计接真实接口；待办「去处理」改为门户内审批抽屉（同意/驳回）；通知中心接告警记录 + IM 会话双源；用户/角色/菜单改为真实只读页；详见 [检查报告](../reviews/2026-10-25-p2-portal-platform-placeholders.md) |
| 8 | P2 | alert 通知渠道 + monitor Prometheus 接入 | **已完成**（2026-10-25；实机联调待环境） | Alertmanager/邮件实机联调需 Prometheus 栈 + 邮件服务器 + MySQL | 邮件 + Webhook 通道已实现；`/alert/internal/prometheus` 入站落库并按规则通知；网关屏蔽内网前缀；版本化 `prometheus.yml` + `alertmanager.yml`；ops Monitor 页可跳转 Prometheus/Grafana；详见 [检查报告](../reviews/2026-10-25-p2-alert-notify-prometheus.md) |
| 9 | P3 | Phase 4 业务应用（OA 请假闭环）+ CI/部署收口 | **已完成**（2026-10-25；E2E/镜像实机构建待环境） | 浏览器 E2E 需 MySQL/Redis/Nacos 与全部服务；镜像实机构建需 Docker 与 registry 凭据 | `whale-apps` OA 域 DDL（`oa_leave`）→六端点→权限 168–171→网关 `/oa/**`→前端列表/表单闭环；T-CI-7 镜像矩阵覆盖 18 个后端服务；`coverage-gate` 接入 GitHub Actions + Gitee Go（本地全 reactor 通过）；详见 [检查报告](../reviews/2026-10-25-p3-oa-leave-ci.md) |
| 10 | P2 | `ImBotService#openDefault` 并发首建失败修复（5.55 部署验证 P2-1/B16） | **已完成**（2026-10-26） | 无 | 恢复读改 `REQUIRES_NEW + READ_COMMITTED` 独立事务跳出 REPEATABLE READ 快照；新增事务属性断言与双线程并发用例；whale-im 139 用例全绿、Checkstyle 0；详见 [检查报告](../reviews/2026-10-26-p2-imbot-concurrent-create.md) |
| 11 | P2 | PM 审批开启态联调（5.55 遗留） | **已完成**（2026-10-26） | 无 | 补建 `bluewhale_pm` 库；创建流程定义 2114553480332849153；whale-pm 开启态发起审批→动态记录→workflow 实例对账→审批完结 8/8 通过；固化 `bw-pm-approval-smoke.ps1`；详见 [检查报告](../reviews/2026-10-26-p2-pm-approval-enabled.md) |
| 12 | P2 | PM 权限点 172–202 真实业务角色补授（5.55 遗留） | **已完成**（2026-10-26） | 无 | `migration-5.57-pm-role-grant.sql` 建 pm_manager(31 全量)/pm_member(16 子集) 并授权 + 补 workflow `instance:start`(44)；pm_mgr/pm_mem 网关级 12 项正负向验证全过；详见 [检查报告](../reviews/2026-10-26-p2-pm-role-grant.md) |
| 13 | P2 | `api_log` 建表纳入各服务 init.sql（5.55 遗留） | **已完成**（2026-10-26） | 无 | 标准 DDL（含 tenant_id）补入 11 个服务 init.sql；存量 9 库在线补建 + 11 库复核；实机验证 whale-pm 请求落 `bluewhale_pm.api_log`；详见 [检查报告](../reviews/2026-10-26-p2-api-log-init-sql.md) |
| 14 | P2 | 发布前用最新配置重新打包网关 jar | **已完成**（2026-10-26） | 无 | 从已提交 main 重打包（含 2c6a663 修复 D），84 用例全绿；重启 8080 健康 UP；经网关冒烟 3/3（登录 / `/auth/me` 权限 142 / `lb://whale-pm`）；详见 [检查报告](../reviews/2026-10-26-p2-gateway-repackage.md) |
| 15 | P3 | P3-9a ERP 物料业务闭环（后端填充 + 前端联调） | **已完成**（2026-10-26） | 无 | `whale-erp` 补数据源/mybatis-plus/prometheus 与 `erp_item` DDL；5 接口接权限点 203–206 + 审计；前端 `api/erp.ts` 去 mock 接网关；构建成功、9001 UP、网关 CRUD 4/4、前端 tsc+build 通过；详见 [检查报告](../reviews/2026-10-26-p3-erp-item.md) |
| 16 | P3 | P3-9b MES 工单业务闭环 | 待执行 | 无 | 与 ERP 同范式：`mes_work_order` DDL、5 接口、权限点 207–210、前端 `api/mes.ts` 去 mock |
| 17 | P3 | P3-9c PDM 产品业务闭环 | 待执行 | 无 | 与 ERP 同范式：`pdm_product` DDL、5 接口、权限点 211–214、前端 `api/pdm.ts` 去 mock |

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
| 2026-10-25 | 序号 7 | portal 修复 token 键错配（`whale_token` → `whale_platform_access_token`），Dashboard 四项统计接真实接口，待办审批抽屉（同意/驳回），通知接告警记录 + IM 双源；基座用户/角色/菜单改为真实只读页并删除 `PlaceholderPage`；两应用 tsc+vite+eslint 全绿；详见 [检查报告](../reviews/2026-10-25-p2-portal-platform-placeholders.md) |
| 2026-10-25 | 序号 8 | 新增 `whale.alert.notify` 配置与邮件/Webhook 通知服务、Alertmanager 入站端点（双令牌头 + 网关内网屏蔽）、版本化 `prometheus.yml`/`alertmanager.yml`、ops Prometheus/Grafana 外链；whale-alert 20 用例全绿、Checkstyle 0，gateway/ops 构建通过；详见 [检查报告](../reviews/2026-10-25-p2-alert-notify-prometheus.md) |
| 2026-10-25 | 序号 9 | OA 请假闭环：`oa_leave` DDL + 6 端点 + 权限 168–171 + 前端列表/表单；CI：Backend CI 改 `-Pcoverage-gate verify`（全 reactor 通过）、镜像矩阵覆盖 18 服务；whale-oa 6 用例全绿、Checkstyle 0，OA 前端 build+lint 通过；详见 [检查报告](../reviews/2026-10-25-p3-oa-leave-ci.md) |
| 2026-10-25 | 序号 1 | MySQL 口令到位后：新建 `bluewhale_job/pay/alert` 三库；补齐 agent/audit/auth 缺失表；14 个 5.54 脚本两轮幂等通过；12 库 `tenant_id` 覆盖校验通过；`file_info` 对象键 7/7 迁移为 `tenants/1/`；API 冒烟与 E2E 因 Redis/Nacos 不可用阻塞；详见 [检查报告](../reviews/2026-10-25-wave5.54-5.53-deploy-execution.md) |
| 2026-10-25 | 序号 3 | `whale-auth` 与 `whale-agent` 的 `migration-5.53.sql` 执行通过；`sys:archive:secret:read`（151）授予 super_admin 并验证落库；权限 140–151、档案菜单 11–16 校验通过；三级权限矩阵真机复测待档案/Agent 服务；详见 [检查报告](../reviews/2026-10-25-wave5.54-5.53-deploy-execution.md) |
| 2026-10-25 | 序号 1（环境与修复） | Redis 5.0.14.1 / Nacos 3.2.4 改用 `tools` 原生进程启动；修复租户开通 NPE（菜单父子乱序导致 `Long` 三元自动拆箱），补回归用例，`whale-auth` 相关 4 用例全绿；网关补 `/tenant/**` 路由 |
| 2026-10-25 | 序号 1（§14.4 冒烟） | 经网关 8080 完成 9 步：平台登录/租户分页/伪造租户头失效/业务租户 403/开通 acme（菜单 11、部门根 1、权限 24）/重复编码 400/新管理员登录/停用 401/恢复登录；`sys_tenant` 现有 2 租户 |
| 2026-10-25 | 序号 2（5.4 切换） | `admin` 加入 acme（`sys_user_tenant` + `tenant_admin`）；`/auth/tenants` 2 条且 current 标记正确；`/auth/tenant/switch` 后 userId=1 不变、tenantId 切换、角色重算、旧令牌 401；浏览器 E2E 待前端 |
| 2026-10-25 | 汇总报告 | 生成 [待办列表执行状态报告](../reviews/2026-10-25-priority-backlog-status-report.md)：10 项中 7 项已完成、3 项部分完成（序号 1/2/3），剩余缺口均为环境依赖 |
| 2026-10-26 | 序号 1（收尾） | 本机拉起全环境（5 服务新 jar）；修复 PermCode 平台级 bug（驼峰码 `resetPwd`/`bpmnImport` 导致 /auth/me 400，改全小写 + migration-5.56，前一日已修 `add-sign`）；§14.5 E2E 3/3 通过；Agent 索引重建 READY（2107 文件/13438 块）；详见 [检查报告](../reviews/2026-10-26-wave5.54-e2e-agent-index.md) |
| 2026-10-26 | 序号 2（5.4 E2E） | 新增 `tenant-switch.spec.ts`（数据自包含：API 确保 acme + admin 绑定）；修复雪花 ID `Number()` 精度丢失导致绑定 404；切换 acme→新令牌 tenantId 校验→切回默认全过；全量 E2E 4/4 绿 |
| 2026-10-26 | 序号 3（密级矩阵复测） | 三级密级权限矩阵经网关真机复测 **PASS**（e2e_none 0/0、e2e_item 密级 2 可见/密级 3 不可见、e2e_secret 均可见）；同步修复 4 个检索链路缺陷：增量扫描误删 `file:` 知识块、`/file/internal/{id}` 契约缺 status 字段致单文件重建恒 SKIPPED、全量重建无台账自愈、网关 `/auth/me` 回落未透传 `X-Tenant-Id` 致权限码头静默为空；详见 [检查报告](../reviews/2026-10-26-wave5.53-security-matrix.md) |
| 2026-10-26 | 序号 10（P2-4） | `ImBotService#openDefault` 并发首建修复：恢复读包入 `REQUIRES_NEW + READ_COMMITTED` 独立事务，跳出外层 REPEATABLE READ 快照；新增事务属性断言 + 双线程并发用例；whale-im 139 用例全绿、Checkstyle 0；详见 [检查报告](../reviews/2026-10-26-p2-imbot-concurrent-create.md) |
| 2026-10-26 | 序号 11（P2-5） | PM 审批开启态联调闭环：补建 `bluewhale_pm` 库（12 表）、流程定义 2114553480332849153、PM 开启态全链路 8/8 通过（发起→动态→实例对账→审批完结）；新增回归脚本 `bw-pm-approval-smoke.ps1`；详见 [检查报告](../reviews/2026-10-26-p2-pm-approval-enabled.md) |
| 2026-10-26 | 序号 12（P2-6） | PM 权限 172–202 落真实业务角色：`migration-5.57-pm-role-grant.sql`（pm_manager 31 全量 / pm_member 16 子集 + workflow `instance:start` 44）；pm_mgr/pm_mem 经网关 12 项正负向验证全过（含 403 边界与发起审批闭环）；详见 [检查报告](../reviews/2026-10-26-p2-pm-role-grant.md) |
| 2026-10-26 | 序号 13（P2-7） | `api_log` 标准 DDL 补入 11 个服务 init.sql（workflow/pay/job/im/form/file/archive/agent/alert/pm/oa）；存量 9 库在线补建；实机验证 bluewhale_pm.api_log 落库（tenant_id=1）；详见 [检查报告](../reviews/2026-10-26-p2-api-log-init-sql.md) |
| 2026-10-26 | 序号 14（P2-8） | 网关从已提交 main 重打包（含 2c6a663），84 用例全绿；重启 8080 后健康 UP，经网关冒烟 3/3（登录 / `/auth/me` 142 权限 / `lb://whale-pm` 路由）；详见 [检查报告](../reviews/2026-10-26-p2-gateway-repackage.md) |
| 2026-10-26 | 序号 15（P3-9a） | ERP 物料闭环：`erp_item` 库表 + 5 接口（权限 203–206）+ 前端去 mock；构建成功、9001 UP、网关 CRUD 4/4、tsc+build 通过；详见 [检查报告](../reviews/2026-10-26-p3-erp-item.md) |
