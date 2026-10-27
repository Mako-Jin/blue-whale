# P1 #4 检查报告：业务权限授予真实角色

- 日期：2026-10-27
- 范围：`bluewhale_auth` 角色与权限授予（ERP / MES / PDM / OA / 合同 / 档案）
- 关联：REQ-FUNC-021、权限点 140–151 / 168–171 / 203–232

## 1. 问题

历史迁移（5.46、5.53、5.58–5.63）按「只播种权限点、不写 `sys_role_permission`」的约定交付，实际库中：

- 仅 `super_admin` 隐式拥有全部权限；
- 平台租户只有 PM 的 `pm_manager` / `pm_member` 有真实授权；
- ERP / MES / PDM / OA / 合同 / 档案的权限未授予任何业务角色；
- 既有租户（如 acme）的 `tenant_admin` 在建租户后未获得后续新增权限。

结果：业务用户登录后菜单可见（菜单未做权限过滤），调用接口被 `RequirePermissionInterceptor` 拒绝，返回 `403`。

角色管理链路按租户隔离（`RoleRepositoryImpl#page / findById / listRoleIdsByUserId / listPermissionIdsByRoleIds` 均以
`TenantContext` 过滤 `sys_role.tenant_id`），因此只在平台租户建角色，业务租户既看不到也无法分配、更不会进入权限计算。

## 2. 改动

新增幂等迁移 `backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/migration-5.65-business-role-grant.sql`。

### 2.1 平台租户业务角色模板（13 个）

| 角色 | ID | 权限范围 | 授权数 |
|---|---|---|---|
| `archive_manager` | 14001 | 140–151 全量 | 12 |
| `archive_member` | 14002 | 140/144/145/148 | 4 |
| `oa_manager` | 16801 | 168–171 全量 | 4 |
| `oa_member` | 16802 | 168/169 | 2 |
| `erp_manager` | 20301 | 203–206 全量 | 4 |
| `erp_member` | 20302 | 203/204/205 | 3 |
| `mes_manager` | 20701 | 207–210 全量 | 4 |
| `mes_member` | 20702 | 207/208/209 | 3 |
| `pdm_manager` | 21101 | 211–214 全量 | 4 |
| `pdm_member` | 21102 | 211/212/213 | 3 |
| `contract_manager` | 21501 | 215–232 全量 + 44 | 19 |
| `contract_member` | 21502 | 215/216/217/219/220/221/222/232 + 44 | 9 |
| `contract_signatory` | 21503 | 215/229/230 | 3 |

口径：

- `*_manager` 含删除类权限，`*_member` / `*_engineer` 为不含删除的协作子集；
- `oa_member` 不含审批 `sys:oa:leave:edit`（170），审批仅 `oa_manager`；
- `contract_signatory` 只能查看合同、执行签署/拒签、查看证据；
- 合同提交审批需要 workflow `sys:workflow:instance:start`（44），授予 manager/member；
- 角色 ID 规则：平台模板为「模块权限段 + 序号」；租户复制角色与授权行 ID 统一取
  `MD5(业务键) 前 15 位`（< 2^60），与 PM（17201/17202）及雪花主键不冲突且重复执行稳定。

### 2.2 既有业务租户角色复制

`sys_tenant` 中 `tenant_type = 'TENANT'`、未删除启用的租户，按上面的 13 个模板各复制一份同名角色：

```sql
INSERT IGNORE INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':', x.role_code)), 1, 15), 16, 10) AS UNSIGNED),
       t.id, x.role_code, x.role_name, x.description, x.sort_no, 1
FROM sys_tenant t
CROSS JOIN ( /* 13 个角色模板 UNION ALL */ ) x
WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1;
```

`uk_tenant_role_code (tenant_id, role_code)` 保证幂等；租户内已存在同名角色时跳过，不覆盖人工调整。

### 2.3 统一授权

按**角色编码**匹配（不再硬编码授权角色 ID），覆盖平台模板与各租户副本，并包含 `tenant_admin` 回填：

- 13 个业务角色按 2.1 的权限矩阵授予；
- `tenant_admin`（任意租户）授予 140–151、168–171、203–232 共 46 项业务权限 + workflow 44。

```sql
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED),
       r.id, p.id
FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
WHERE r.deleted = 0 AND r.status = 1
  AND ( r.role_code = 'archive_manager' AND p.id BETWEEN 140 AND 151
     OR /* ... 其余 12 个角色 + tenant_admin ... */ );
```

### 2.4 新租户自动下发（provisioning）

`TenantProvisioningAppService` 在创建 `tenant_admin` 后新增一步：按平台租户的 13 个业务角色模板下发同名角色。

- 模板来源：平台租户 `Tenant.PLATFORM_TENANT_ID` 下 `BUSINESS_ROLE_TEMPLATE_CODES` 对应的角色，
  名称/描述/排序/状态原样复制，权限集取模板当前 `sys_role_permission`；
- 下发权限集会与「本次可下发权限」求交集：剔除平台专属码（`PLATFORM_ONLY_PERM_CODES`）与停用权限，
  避免模板被误加平台权限后扩散到业务租户；
- 模板缺失（未执行 5.65）时逐项 `WARN` 跳过，不阻断租户开通（`tenant_admin` 仍持有全部启用业务权限）；
- 复制失败（数据库异常）随开通事务回滚，不产生半开租户。

### 2.5 历史迁移说明同步

5.46 / 5.58–5.63 的「授权说明」改为指向 5.65，避免运维重复手工勾选。

### 2.6 校验脚本

新增只读脚本 `scripts/smoke/bw-auth-business-role-grant-smoke.ps1`：

1. 业务权限点总数 = 46；
2. 目标租户（平台租户 + 全部业务租户）非空；
3. 每个目标租户的 13 个角色授权数与矩阵一致；
4. 每项业务权限至少被一个启用角色持有；
5. 每个 `tenant_admin` 至少持有 46 项业务权限。

脚本仅在 `MYSQL_PASSWORD` 存在时执行，失败返回 1。

## 3. 验证

本机 MySQL 8.0.26（`D:\tools\installer\mysql`，datadir 已含全部业务库）本次已启动并实测：

- 迁移执行：`mysql bluewhale_auth < migration-5.65-business-role-grant.sql` → exit 0，无错误输出；
- 幂等复核：同一脚本连续执行第二次 → exit 0，无重复行/唯一键报错；
- 执行前快照：`sys_tenant` 为 `default`（PLATFORM）+ `acme`（TENANT）；`acme` 仅有 `tenant_admin`；
  平台租户仅有 `super_admin` / `pm_manager` / `pm_member` / 3 个 e2e 角色；
- 执行后：平台租户与 `acme` 各 13 个业务角色，授权数与矩阵逐项一致；
- `scripts/smoke/bw-auth-business-role-grant-smoke.ps1` → **RESULT: PASSED**（29 项 OK，含
  `acme.contract_manager=19`、`tenantAdmin[2114526600112365569]=46`、`unassigned=` 空集）。

```powershell
$env:MYSQL_PASSWORD='***'
powershell -File scripts/smoke/bw-auth-business-role-grant-smoke.ps1
# 实测输出：RESULT: PASSED
```

未改动 Java 代码（迁移阶段）；provisioning 下发改造见下：

- `TenantProvisioningAppServiceTest` 新增 `shouldCopyBusinessRoleTemplates`：按模板复制名称/描述/排序/租户 ID，
  模板权限 `[140, 165(平台专属), 999(不存在)]` 仅下发 140；模板缺失的 12 个编码跳过不报错；
- `mvn -pl ...whale-service-auth-starter -am test`：**354 用例 0 失败**（7 个 Live IT 因无 DB/Redis 跳过），
  Checkstyle 0 违规；
- 已知非本轮问题：`whale-security` 的 `Sm4CryptoTest.shouldFailWhenTampered` 在本机 JDK/JCE 环境失败
  （依赖模块既有用例，与本次改动无关，已用 `-Dtest=!Sm4CryptoTest` 隔离后全绿）。

## 4. 边界

- 新租户开通时由 provisioning 自动下发 13 个业务角色并复制平台模板权限；模板未部署（未执行 5.65）时仅告警跳过，
  `tenant_admin` 仍持有全部启用业务权限，不会 403。
- 模板内容以数据库为准：平台侧调整 13 个模板角色的权限集后，仅影响**之后**开通的租户；已开通租户不回刷
  （需要时重跑迁移的授权段或由租户管理员调整）。
- 未自动把用户分配到新角色，避免越权；由管理员在「用户管理」按岗分配。
- 迁移假设前置权限点种子（5.46/5.53/5.54/5.58–5.64）已执行；缺失时授权 SELECT 会少插入，需先补前置迁移。
- 「菜单可见但接口 403」的菜单权限过滤不在本轮范围（另见前端菜单权限收口项）。
- 平台租户角色模板与业务租户副本通过 `role_code` 对齐：租户内管理员改过权限集时不会被本迁移覆盖
  （`INSERT IGNORE` 不覆盖既有授权，仅补缺）。
