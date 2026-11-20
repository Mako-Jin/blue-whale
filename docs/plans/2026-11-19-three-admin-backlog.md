# 三员分立（系统管理员 / 安全管理员 / 安全审计员）待办

- 日期：2026-11-19
- 结论：**是这三个**。等保 2.0 的标准表述为「系统管理员、安全管理员、安全审计员」，口语简称“三员”；你列的“审计员、安全员、系统管理员”分别对应“安全审计员、安全管理员、系统管理员”。

## 〇、2026-11-20 交付进展

| 项 | 落地位置 | 状态 |
| --- | --- | --- |
| 三员角色编码与内置判定 | `AuthRoleCodes`（THREE_ADMIN_CODES / isBuiltIn / isThreeAdmin） | ✅ |
| 内置角色不变量 | `RoleAggregate`：内置禁改名、三员禁停用；`RoleAppServiceImpl`：内置禁删、三员禁改权限集 | ✅ |
| 角色与权限建模（第 1 节） | `V36__three_admin_segregation.sql`：角色 26004-26006 + 业务租户复制 + 矩阵白名单授权 | ✅ |
| 职责分离与互斥（第 2 节） | 三员 MUTEX 约束播种（`three_admin_mutex`），复用既有 `RoleConstraintCheckerImpl` | ✅ |
| 权限边界矩阵（第 3 节） | V36 三段白名单逐码枚举；`sys:audit:log:list` 已从 tenant_admin 收回 | ✅ |
| 审计日志可见性（第 6 节） | ops 菜单按权限码过滤，管理员侧不再出现审计入口 | ✅ |
| 存量环境迁移（第 8 节） | V36 幂等（重跑 affected 全 0），平台 + 业务租户一次覆盖 | ✅ |
| 租户开通自动生成（验收 4） | `TenantProvisioningAppServiceImpl#copyThreeAdminRolesFromPlatform` + 互斥约束播种 | ✅ |
| 单测（第 7 节） | `RoleAppServiceTest` 新增 5 例、`TenantProvisioningAppServiceTest` 新增 1 例 | ✅ |
| 审计追加写保护（第 5 节 1、2、3 项） | 代码层门禁 `scripts/security/check-audit-append-only.py`（已挂 CI）+ 数据库触发器 `AuditAppendOnlyGuard.java`（28 库）+ `whale-audit` V2/init.sql | ✅ |
| 审计的审计（第 5 节 2 项） | `AuditLogController` 查询/详情/链路/导出均 `@AuditLog(QUERY/EXPORT)` | ✅（既有能力，本轮核对） |
| 超管与三员互斥（第 2 节 2 项） | `V37__super_admin_three_admin_mutex.sql`：super_admin 并入 `three_admin_mutex` | ✅ |
| 验收与安全评审归档（第 8 节） | `scripts/verify/three-admin-assertions.sql` 11 条断言 + `docs/reviews/2026-11-20-three-admin-compliance.md` 条款对照表 | ✅ |
| 归档哈希与签名（第 5 节 / 原残余 #2） | `whale-audit` V3 补 6 列 + 每批 `.sha256` 清单（SHA-256 / 链式哈希 / 可选 HMAC）+ `scripts/verify/archive-hash-verify.py` | ✅ |
| 审计留存 6 个月巡检（原残余 #4） | `AuditRetentionInspector` → Gauge `audit.log.oldest.days`，低于 `whale.audit.retention.minimum-days`（默认 180）WARN | ✅ |
| 超管退役技术闸门（原残余 #5） | `scripts/verify/super-admin-retirement.sql` 4 条断言（启用超管 0 / 近 7 天成功登录 0 / 三员各有账号 / 无人兼超管与三员） | ✅ |

**未做 / 待评估**：

- 三员账号「双人复核启用」：等保三级可选项，当前无对应审批流模型，未实现。
- 应用数据库账号按表收权：当前靠触发器拦改删，生产建议再 `GRANT SELECT, INSERT ON audit_log`。
- 归档历史批次（配签名密钥之前）的清单 `hmac=` 为空，不可追溯补签——审计时只做哈希与链值校验。
- `super_admin` 与三员的「互斥」只到角色分配层：超管本身是代码级硬旁路（`PermissionInterceptor`），**技术拦不住它在线**，但「是否已退役」现在有可执行闸门（上表最后一行），不再是纯流程。

**运维口径（交付后必做）**：

1. 用平台 `super_admin` 建三个独立账号，分别授 `system_admin` / `security_admin` / `audit_admin`（互斥约束会拦住一人多授）。
2. 三员账号首次登录必须改密（系统管理员重置口令即置 `must_change_password`，登录返回 `mustChangePassword=true`）。
3. 交付完成后停用或改密 `super_admin`，避免「硬旁路账号」长期在线；用 `scripts/verify/super-admin-retirement.sql` 验（4 条全 PASS 才签字，**不要挂 CI**）。
4. 三员角色权限集不允许在管理端调整（后端拒绝 + 前端按钮置灰）：需要变更职责边界时，改 `V36` 白名单并发新迁移。

---

## 一、现状盘点

| 能力 | 现状 |
| --- | --- |
| 角色模型 | `sys_role` / `sys_user_role`，内置 `super_admin`、`tenant_admin`，另有 `mall_admin`、`mall_operator`、`pm_manager` 等业务角色；**无三员角色** |
| 权限模型 | `sys_role_permission` + `@RequirePermission` + 网关 `X-Permission-Codes`；`super_admin` 全量放行 |
| 审计能力 | `whale-ops/whale-audit` 已支持 API / 行为 / 前端 / 出站四通道查询、导出、归档；查询权限 `sys:audit:log:list`；`audit_log` append-only |
| 二次认证 | 前端 428 弹窗验密并重放（Wave 6.22） |
| 缺口 | 三员角色与权限集、互斥约束、初始账号、职责边界矩阵、审计员自身操作留痕与不可抵赖 |

## 二、待办清单

### 1. 角色与权限建模

- [ ] 新增内置角色：`system_admin`（系统管理员）、`security_admin`（安全管理员）、`audit_admin`（安全审计员），写入 `AuthRoleCodes` 与 `db/init.sql` / Flyway 迁移
- [ ] 为三员分别配置最小权限集（见第 3 节矩阵），禁止直接复用 `super_admin`
- [ ] 三员角色设为租户内置角色，租户开通时按模板创建，不可删除、不可改名
- [ ] `super_admin` 定位收敛为“交付初始化账号”，交付后引导拆分三员并降权

### 2. 职责分离与互斥

- [ ] 用户角色分配接口增加互斥校验：同一用户最多持有三员中的一个
- [ ] 三员与 `super_admin` 互斥（避免以超管身份绕过职责分离），至少禁止“审计员 + 任一管理员”
- [ ] 互斥规则配置化（DB 或配置项），校验失败返回明确错误码
- [ ] 补充用户-角色变更审计事件（谁在何时授予/回收了哪个三员角色）

### 3. 权限边界矩阵

| 能力 | 系统管理员 | 安全管理员 | 安全审计员 |
| --- | --- | --- | --- |
| 用户 / 部门 / 菜单维护 | ✅ | ❌ | ❌ |
| 角色创建与权限分配 | ❌（仅查看） | ✅ | ❌ |
| 口令策略 / 登录策略 / 会话强制下线 | ❌ | ✅ | ❌ |
| 密钥、证书、OAuth 客户端 | ❌ | ✅ | ❌ |
| 定时任务 / 文件 / 租户成员 | ✅ | ❌ | ❌ |
| 审计日志查询 / 导出 | ❌ | ❌ | ✅（只读） |
| 审计日志删除 / 修改 | ❌ | ❌ | ❌（任何角色均禁止） |

- [ ] 按矩阵拆分 `sys:role:assign`、`sys:permission:*`、`sys:tenant:pwdpolicy:*`、`sys:audit:log:list` 等权限点归属
- [ ] 校验“审计员无法访问业务数据、管理员无法查看审计日志”的反向用例

### 4. 账号开通与初始化

- [ ] 提供三员初始账号创建流程（首次登录强制改密，复用 `must_change_password`）
- [ ] 三员账号支持双人复核启用（可选，按等保要求等级）
- [ ] 交付文档补充三员账号申请、审批、变更、离岗回收流程

### 5. 审计与不可抵赖

- [ ] 三员的所有管理操作纳入审计（角色授予、权限变更、策略修改、强制下线、审计导出）
- [ ] 审计员查询/导出行为本身也写审计，形成“审计的审计”
- [ ] 审计日志追加写、禁止 UPDATE/DELETE（服务层与数据库账号权限双重约束）
- [ ] 归档文件完整性校验（哈希 + 归档水位），支持审计回溯
- [ ] 审计日志保留期与等保要求对齐（当前保留策略见 `whale-audit` retention 配置）

### 6. 前端

- [ ] 角色管理页内置三员角色标识（不可删除、不可编辑编码）
- [ ] 用户角色分配页对互斥角色置灰并提示原因
- [ ] 菜单/按钮按三员权限渲染，隐藏无权入口
- [ ] 审计查询页仅对 `audit_admin` 可见；管理员侧不出现审计入口

### 7. 测试与文档

- [ ] 单测：互斥校验、权限矩阵、三员不可删除
- [ ] 集成测试：三角色登录后的菜单/接口可见性矩阵
- [ ] 更新 `docs/` 权限说明、初始化脚本说明与部署手册

### 8. 上线与验收

- [ ] 存量环境迁移脚本：创建三员角色、按租户复制、初始化账号
- [ ] 验收：三员互斥生效；审计员只读；管理员看不到审计；所有敏感操作有审计记录
- [ ] 安全评审归档（等保三员分立条款对照表）

## 三、验收标准

1. 三员角色均为内置、不可删除，且同一用户只能持有其中一个；
2. 系统管理员不能分配角色权限、不能查看审计；安全管理员不能维护用户/部门；审计员只能只读查询与导出审计；
3. 三员角色的授予、回收、策略变更、审计导出全部留痕且不可篡改；
4. 租户开通即自动生成三员角色，存量租户通过迁移脚本补齐。
