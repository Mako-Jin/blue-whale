# 三员分立等保 2.0 对照与验收归档（2026-11-20）

> 交付批次：commit `5de909f6`（三员角色与职责矩阵）+ 本轮（审计防篡改、超管并入互斥、验收断言集）。
> 对照标准：GB/T 22239-2019《信息安全技术 网络安全等级保护基本要求》第三级——8.1.4 安全计算环境、8.1.5 安全管理中心。
> 本文是安全评审归档件：每条要求都给出**落地位置**与**可重复的验证命令**，不留「已实现」这种无法验证的结论。

## 一、结论

| 项 | 结论 |
| --- | --- |
| 三权分立（系统管理员 / 安全管理员 / 安全审计员） | 已落地，三员互为内置角色、权限集逐码白名单、互不重叠（写权限零重叠） |
| 职责分离的可执行性 | 已落地：MUTEX 约束 `three_admin_mutex`（含 `super_admin`），分配角色即拦截 |
| 审计记录保护 | 已落地：代码层门禁 + 数据库层触发器（UPDATE / DELETE 均被拒绝） |
| 审计的审计 | 已落地：审计查询/详情/链路/导出全部标注 `@AuditLog(type=QUERY/EXPORT)` |
| 未实现（已登记为残余风险） | 三员账号「双人复核启用」、归档包哈希与签名、应用账号按表收权、审计留存期与等保 6 个月对齐的强制校验 |

## 二、条款对照

### 2.1 安全管理中心——三权分立（8.1.5.1 / 8.1.5.2 / 8.1.5.3）

| 条款 | 要求摘要 | 本项目落地 | 验证 |
| --- | --- | --- | --- |
| 8.1.5.1 a) | 系统管理员身份鉴别、仅能通过特定界面配置管理 | `system_admin` 角色：54 个权限码，覆盖用户/部门/菜单/任务/文件/系统配置；**不含** `sys:role:assign`、`sys:permission:*`、口令与登录策略、审计查看 | 断言 01/02/06；`/auth/v1/me` 实测权限集 |
| 8.1.5.2 a) b) | 审计管理员身份鉴别、仅做审计记录分析处理 | `audit_admin` 角色：**仅 2 个权限码**（`sys:audit:log:list`、`sys:login:history:list`），零写权限 | 断言 03/04 |
| 8.1.5.3 a) b) | 安全管理员身份鉴别、配置安全策略与授权 | `security_admin` 角色：48 个权限码，覆盖角色授权、权限模型、口令/登录/会话策略、OAuth 客户端与密钥、网关黑白名单；**不含**用户/部门维护与审计查看 | 断言 05/06 |
| 三权分立 | 三种管理职责由不同人员承担 | 内置角色 `category='SYSTEM'`，禁删除、禁改名、三员禁停用、**权限集禁止手工调整**（`RoleAppServiceImpl` 直接拒绝，前端按钮置灰） | 单测 5 例（`RoleAppServiceTest`） |

### 2.2 访问控制（8.1.4.2）与默认账户

| 条款 | 要求摘要 | 本项目落地 | 验证 |
| --- | --- | --- | --- |
| 8.1.4.2 a) | 授权主体配置访问控制策略，主体客体授权 | `sys_role_permission` + `@RequirePermission` + 网关 `X-Permission-Codes` | 权限点 966 个，`tenant_admin` 945 |
| 8.1.4.2 b) | 重命名/删除默认账户，修改默认口令 | `super_admin` 定位收敛为「交付初始化账号」；交付后按本文 §5 停用；三员账号首登强制改密（`must_change_password`） | §5 流程；登录返回 `mustChangePassword=true` |
| 8.1.4.2 c) | 授予管理用户所需最小权限，实现管理用户的权限分离 | 三员白名单逐码枚举（V36 三段 `INSERT … SELECT`），**不是「全量减」**；三员写权限零重叠 | 断言 06；V36 校验段 c) |
| 职责分离 | 同一用户不得兼任冲突岗位 | `three_admin_mutex`：`role_ids = [26004, 26005, 26006, <super_admin id>]`，MUTEX 判定「命中同一互斥集 ≥2 即拒绝」 | 断言 07/08/09 + `RoleConstraintCheckerImpl` |

### 2.3 安全审计（8.1.4.3）

| 条款 | 要求摘要 | 本项目落地 | 验证 |
| --- | --- | --- | --- |
| a) | 覆盖每个用户，记录重要用户行为与安全事件 | `@AuditLog` 覆盖 auth 全部管理端写操作（建/改/删/授权/重置口令/启停/强制下线/约束变更）+ 登录登出 + 网关侧拦截事件 | `grep -r "@AuditLog" whale-service-auth` |
| b) | 记录日期时间、用户、事件类型、是否成功等 | `audit_log` 字段含 `create_time`/`user_id`/`username`/`operate_type`/`biz_status`/`error_msg`/`ip`/`user_agent`/`trace_id` | 表结构见 `whale-audit/src/main/resources/db/init.sql` |
| c) | **审计记录保护，避免未预期的删除、修改或覆盖** | 三重：① 服务层只 INSERT/SELECT（归档只导出不删除）；② 静态门禁 `scripts/security/check-audit-append-only.py`；③ 数据库触发器 `trg_audit_log_no_update` / `trg_audit_log_no_delete`（28 个库） | §4 正向+负向验证 |
| d) | 审计记录定期备份、留存不少于 6 个月 | `AuditLogArchiver` 按水位分批导出 CSV 上传对象存储；保留期由 `whale.audit.retention.days` 配置 | 归档水位表 `audit_archive_watermark` |
| 「审计的审计」 | 审计员自身操作亦须留痕 | `AuditLogController` 查询/详情/链路/导出任务提交均 `@AuditLog(type=QUERY/EXPORT)`；`/ingest` 显式不加（避免自激） | `AuditLogController` 类注释 AUTH-07 |

## 三、三员权限边界（实测值，2026-11-20）

| 能力 | 系统管理员 54 | 安全管理员 48 | 安全审计员 2 |
| --- | --- | --- | --- |
| 用户 / 部门 / 菜单维护 | ✅ | ❌ | ❌ |
| 角色创建与权限分配 | ❌（仅 `sys:role:list` 只读） | ✅ | ❌ |
| 口令策略 / 登录策略 / 会话强制下线 | ❌ | ✅ | ❌ |
| OAuth 客户端 / 密钥 / 网关黑白名单 | ❌ | ✅ | ❌ |
| 定时任务 / 文件 / 租户成员 | ✅ | ❌ | ❌ |
| 审计日志查询 / 导出 | ❌ | ❌ | ✅（只读） |
| 审计日志改删 | ❌ | ❌ | ❌（全库触发器禁止） |

三员之间唯一重叠的是 6 个只读选人/视图码（`sys:user:list`、`sys:user:group:list`、`sys:user:profile:view`、`sys:role:list`、`sys:tenant:member:list`、`sys:login:history:list`），**写权限零重叠**——断言 06 就是这条的反向用例。

## 四、验收断言集（可重复执行）

```bash
# 1) 11 条三员断言（只读，不改数据）
#    DbQ.java 只是本地只读查询小工具；任何 MySQL 客户端（DBeaver / mysql CLI）执行
#    scripts/verify/three-admin-assertions.sql 等价，输出四列 check_name/expected/actual/result。
java -cp F:/respository/maven/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar \
     .workbuddy/tmp/DbQ.java "$(cat scripts/verify/three-admin-assertions.sql)"

# 2) 代码层门禁（提交前 / CI）
python scripts/security/check-audit-append-only.py

# 3) 数据库层守卫（新库、扩容后各跑一次；幂等）
DB_USER=root DB_PWD=*** java -cp F:/respository/maven/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar \
     scripts/security/AuditAppendOnlyGuard.java
```

本轮实测（2026-11-20）：

- 断言 01–11 全部 **PASS**（三员齐备、审计员 2 码、`tenant_admin` 无审计码、写权限零重叠、约束含超管、无用户兼任多员、28 个库 `audit_log` 均已加守卫）。
- 数据库守卫 28 个库全部 `[PASS]`；临时库端到端验证：`INSERT` 放行、`UPDATE` 返回 `audit_log is append-only, UPDATE is forbidden`、`DELETE` 返回 `... DELETE is forbidden`。
- 负向验证（防「门禁只是摆设」）：
  - 门禁：构造一句 `DELETE FROM audit_log ...` → 退出码 1；改回 `INSERT` → 退出码 0。
  - 断言集：临时建一个无守卫的 `audit_log` 表 → 断言 10 由 PASS 变 **FAIL**（`actual=1`），验证后已删库。
  - 守卫工具：内置行为验证——对每张表发一条「首列赋给自己」的 UPDATE（不改任何数据），未被拦截即判 FAIL。
- 迁移幂等：V36 重跑 affected 全 0；V37 二次执行 `affected=0`，约束 `role_ids` 稳定为 `[26004, 26005, 26006, 1]`。

## 五、三员账号全生命周期（运维必须照做）

| 阶段 | 动作 | 判据 |
| --- | --- | --- |
| 申请 | 三方书面/工单确认（系统管理员、安全管理员、安全审计员各 1 人，不得兼任） | 有审批记录；三人不同自然人 |
| 开通 | 平台 `super_admin` 建 3 个独立账号，分别授 `system_admin` / `security_admin` / `audit_admin` | 授第二个三员角色时被 `three_admin_mutex` 拒绝 |
| 首登 | 强制改密（管理员重置口令后 `must_change_password=1`） | 登录响应 `mustChangePassword=true`，未改密不可进业务 |
| 日常 | 三员权限集**不得**在管理端调整（需变更时发新迁移改白名单） | 角色页「分配权限」置灰；后端 `RoleAppServiceImpl` 拒绝 |
| 变更 | 换人＝新开账号 + 停用旧账号，禁止共用账号 | 登录历史可追溯到具体人 |
| 离岗 | 停用账号（三员角色本身不可停用，停的是**账号**）+ 强制下线全部会话 | `sys_user.status=0` + `SessionController` 强制下线留痕 |
| 交付收尾 | **停用或改密 `super_admin`** | 超管是代码级硬旁路（`PermissionInterceptor` 0 授权也放行），长期在线即等于职责分离失效 |

## 六、残余风险与后续

| # | 风险 | 现状 | 建议 |
| --- | --- | --- | --- |
| 1 | 三员账号「双人复核启用」未实现 | 无审批流模型，本轮不做 | 复用 workflow 模块做「开通/授权双人复核」；等保三级为可选项，四级必需 |
| 2 | 归档 CSV 无哈希/签名，无法证明未篡改 | 归档只导出不删除，水位可追溯 | 归档时记录 SHA-256 到 `audit_archive_watermark`（需加列）+ 归档清单签名 |
| 3 | 应用数据库账号对 `audit_log` 仍有 UPDATE 权限（靠触发器拦住） | 28 个库均靠触发器 | 生产按库 `GRANT SELECT, INSERT ON audit_log` 收权，形成「账户 + 触发器」双约束 |
| 4 | 审计留存 6 个月无强制校验 | 保留期靠配置 | 增加巡检：`audit_log` 最早记录时间 ≥ 6 个月，否则告警 |
| 5 | `super_admin` 停用依赖流程 | 无技术强制 | 交付脚本内置「停用超管」步骤；或加一条告警：超管账号 7 天内有登录即告警 |

## 七、变更清单（本轮新增）

| 文件 | 作用 |
| --- | --- |
| `scripts/security/check-audit-append-only.py` | 代码层门禁：禁止对 `audit_log` 与四张通道日志表的 UPDATE/DELETE/REPLACE/TRUNCATE（含既有豁免白名单） |
| `scripts/security/AuditAppendOnlyGuard.java` | 数据库层守卫：给所有含 `audit_log` 的库建 BEFORE UPDATE/DELETE 触发器，内置负向验证 |
| `scripts/verify/three-admin-assertions.sql` | 11 条只读验收断言，输出 PASS/FAIL |
| `whale-audit/.../db/migration/V2__audit_log_append_only_guard.sql` | 新环境自动带守卫（Flyway） |
| `whale-audit/.../db/init.sql` | 建库脚本同步触发器 DDL |
| `auth/.../db/migration/V37__super_admin_three_admin_mutex.sql` | 超管并入 `three_admin_mutex`，幂等 |

> 守卫范围**只到 `audit_log`**：`api_log` / `outbound_log` / `frontend_log` / `behavior_log` 有保留期清理任务（`LogRetentionCleaner` 按 cron 删除过期行），给它们加 DELETE 守卫会让保留期失效、表无限膨胀。这一条是本轮踩到又修正的口径，别再「顺手扩大到四张表」。
