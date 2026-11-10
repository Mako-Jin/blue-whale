# T3 检查报告：PM 业务角色模板化（含租户范围确认）

- 日期：2026-10-27
- 范围：`whale-service-auth` 租户开通编排、`migration-5.66-pm-role-template.sql`、授权校验脚本
- 关联：待办清单 T3、P1 #4 业务角色模板化剩余缺口

## 1. 先确认：PM 是否仅限平台租户

**结论：PM 不是平台专属，属于业务租户能力。** 依据：

| 证据 | 位置 |
|---|---|
| PM 所有业务表必须带 `tenant_id`，复用 MyBatis-Plus 租户插件，不另造隔离 | [wave5.55-project-management.md:33](../plans/wave5.55-project-management.md#L33) |
| PM 菜单 ID 从 18 起编（预留 18–29），随 `business` 租户开通模板复制 | [wave5.55-project-management.md:31](../plans/wave5.55-project-management.md#L31) |
| PM 依赖平台 auth 的 RBAC + 租户能力，权限按租户授予 | [wave5.55-project-management.md:18](../plans/wave5.55-project-management.md#L18) |
| Wave 5.55 联调时 `acme` 租户 `tenant_admin` 已被授予 PM 全量 31 项 | [migration-5.57-pm-role-grant.sql:13](../../whale-backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/migration-5.57-pm-role-grant.sql#L13) |
| 平台壳已为业务租户提供 `/pm` 入口，权限码 `sys:pm:project:list` | `whale-frontend/platform-apps/whale-platform/src/router/menu.ts` |

因此缺口成立：`migration-5.57-pm-role-grant.sql` 只在平台租户创建 `pm_manager` / `pm_member`，`TenantProvisioningAppService` 的模板清单也未包含 PM，业务租户没有可分配的 PM 角色。

同时核实 T3 附带的 165–167：

- `165`（`sys:tenant:member:list`）、`166`（`sys:tenant:member:assign`）不属于平台专属，业务租户开通时随 `tenant_admin` 全量权限下发；
- `167`（`sys:user:tenant`）在 `PLATFORM_ONLY_PERM_CODES` 中，按设计**不向业务租户下发**；
- 5.66 仍对存量 `tenant_admin` 幂等补齐 165/166，防止 5.54 之前开通的租户遗漏；167 明确排除。

## 2. 改动

| 文件 | 说明 |
|---|---|
| `whale-service-auth-application/.../tenant/TenantProvisioningAppService.java` | `BUSINESS_ROLE_TEMPLATE_CODES` 由 13 个增至 15 个，加入 `pm_manager` / `pm_member`；Javadoc 更新模板来源为 5.65 + 5.66 |
| `db/migration-5.66-pm-role-template.sql` | 新增幂等迁移：① 确保平台租户 PM 角色模板存在（5.57 已执行时忽略）；② 为全部 `tenant_type=TENANT` 未删除启用租户按模板复制 PM 角色（MD5 前 15 位稳定 ID，`uk_tenant_role_code` 兜底）；③ 按角色编码授予 `pm_manager` 172–202+44、`pm_member` 16 项+44；④ 为存量 `tenant_admin` 补齐 172–202、165/166、44，明确不含 167 |
| `scripts/smoke/bw-auth-business-role-grant-smoke.ps1` | 角色清单扩至 15 个；期望授权数新增 `pm_manager=32`、`pm_member=17`；权限种子期望 46→77；全量授权覆盖范围加入 172–202；新增第 6 段校验 `tenant_admin` 至少覆盖 34 项（PM 31 + 165/166 + workflow 44） |
| `TenantProvisioningAppServiceTest.java` | 新增「模板包含 PM 角色」用例；缺失告警用例的模板总数日志由 13 调整为 15 |

## 3. 验证

- `mvn -o -pl whale-services/whale-service-auth/whale-service-auth-starter -am -Dtest=TenantProvisioningAppServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` → exit 0；auth starter 共 404 用例、0 失败，`TenantProvisioningAppServiceTest` 6 用例通过；日志显示 `总模板数=15`，`pm_manager`/`pm_member` 按缺失模板告警跳过（单测只 mock 了 `archive_manager` 模板，符合预期）。
- 迁移 SQL 未在目标库执行：本机 MySQL 未启动（`127.0.0.1:3306` 连接被拒绝），无法实机验证幂等与授权数量。
- 执行方式与校验 SQL 已写入迁移文件尾部；目标库执行后应运行：
  `$env:MYSQL_PASSWORD='***'; powershell -File scripts/smoke/bw-auth-business-role-grant-smoke.ps1`
  期望 `RESULT: PASSED`，且每个租户 15 个业务角色、`pm_manager=32`、`pm_member=17`。

## 4. 边界

- 5.66 只复制「当前平台模板」的角色元数据与权限集；平台后续调整模板权限不会回刷已开通租户（与 5.65 一致）。
- 业务租户中已存在的同名 PM 角色不会被覆盖，只补授权，保留人工调整的名称/排序/状态。
- 存量 `tenant_admin` 被补授 PM 全量权限；若某租户希望收窄，需在角色管理界面人工调整。
- 已开通租户执行迁移后立即获得 PM 角色；未来租户由 `TenantProvisioningAppService` 自动下发。
- 真实库执行、smoke 通过前，T3 的部署动作仍待完成（见待办 V-13）。
