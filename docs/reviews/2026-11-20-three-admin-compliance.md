# 三员分立等保 2.0 对照与验收归档（2026-11-20）

> 交付批次：① commit `5de909f6`（三员角色与职责矩阵）；② `7e21c1cc` + `09244ea3`（审计防篡改、超管并入互斥、验收断言集）；③ 归档哈希链与清单签名、留存巡检、超管退役闸门；④ 作业心跳与陈旧告警、应用账号最小权限核查；⑤ 本批（归档签名可强制 + 二次认证接线门禁修复）。
> **部署提醒**：本批改了 `whale-audit` 的代码与配置（`application.yml` 新增 `export.sign-key` / `require-sign-key`），必须重建 `whale-audit` 镜像后重启，`restart` 不会带新代码。
> 对照标准：GB/T 22239-2019《信息安全技术 网络安全等级保护基本要求》第三级——8.1.4 安全计算环境、8.1.5 安全管理中心。
> 本文是安全评审归档件：每条要求都给出**落地位置**与**可重复的验证命令**，不留「已实现」这种无法验证的结论。

## 一、结论

| 项 | 结论 |
| --- | --- |
| 三权分立（系统管理员 / 安全管理员 / 安全审计员） | 已落地，三员互为内置角色、权限集逐码白名单、互不重叠（写权限零重叠） |
| 职责分离的可执行性 | 已落地：MUTEX 约束 `three_admin_mutex`（含 `super_admin`），分配角色即拦截 |
| 审计记录保护 | 已落地：代码层门禁 + 数据库层触发器（UPDATE / DELETE 均被拒绝） |
| 归档完整性 | 已落地：每批 SHA-256 + 跨批链式哈希 + 可选 HMAC 清单签名，配套校验工具（§4.2） |
| 归档签名强度 | 已落地：`require-sign-key=true` 时无密钥即拒绝归档；缺失以 `audit.archive.signed=0` 暴露；校验器 `--require-signature` 强制验签（§4.2） |
| 危险操作二次认证（8.1.4.2 g） | 已落地接线门禁：由 `@RequireReauth` 反推受保护路径，核对前端是否注入 `onReauthRequired`（本批修复该门禁的解析缺陷） |
| 审计留存 | 已落地：指标 `audit.log.oldest.days` + 低于 180 天告警（§4.3） |
| 审计作业存活 | 已落地：`audit_job_heartbeat` 落库 + 指标 `audit.job.stale.days`，连续 2 天未成功即告警（§4.4） |
| 应用账号最小权限 | 已落地核查工具：`AuditDbGrantCheck` 判定 `audit_log` 是否只余 SELECT/INSERT，并给出按级整改语句（§4.5） |
| 超管退役 | 已落地巡检：`scripts/verify/super-admin-retirement.sql` 4 条交付闸门（§5） |
| 审计的审计 | 已落地：审计查询/详情/链路/导出全部标注 `@AuditLog(type=QUERY/EXPORT)` |
| 未实现（已登记为残余风险） | 三员账号「双人复核启用」、归档历史批次补签、归档链头外部锚点 |

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
| c) | **审计记录保护，避免未预期的删除、修改或覆盖** | 三重：① 服务层只 INSERT/SELECT（归档只导出不删除）；② 静态门禁 `scripts/security/check-audit-append-only.py`；③ 数据库触发器 `trg_audit_log_no_update` / `trg_audit_log_no_delete`（28 个库） | §4.1 正向+负向验证 |
| b) c) 完整性 | 归档文件可自证未被改、批次序列可自证未被插入/删除 | 每批 CSV 的 SHA-256 + 链式哈希 `chain(n)=sha256(chain(n-1)+sha256(batch n))` 写入 `audit_archive_watermark`；同批上传 `.sha256` 清单，配置 `whale.audit.export.sign-key` 后追加 HMAC-SHA256 | §4.2 |
| d) | 审计记录定期备份、**留存不少于 6 个月** | ① `AuditLogArchiver` 按水位分批导出 CSV 上传对象存储；② 留存巡检：指标 `audit.log.oldest.days`（Gauge）+ 低于 `whale.audit.retention.minimum-days`（默认 180）告警 | §4.3；归档水位表 `audit_archive_watermark` |
| 「审计的审计」 | 审计员自身操作亦须留痕 | `AuditLogController` 查询/详情/链路/导出任务提交均 `@AuditLog(type=QUERY/EXPORT)`；`/ingest` 显式不加（避免自激） | `AuditLogController` 类注释 AUTH-07 |
| 审计持续性 | 审计能力不得无声停摆（作业停用 / cron 写错 / 反复重启） | 作业心跳落库 `audit_job_heartbeat` + 指标 `audit.job.stale.days`，连续 2 天未成功即告警；失败同样留痕（`fail_count`），避免「一直跑一直失败」被当成健康 | §4.4 |
| 最小权限 | 应用账号不应具备改写/拆守卫能力 | `AuditDbGrantCheck`：`audit_log` 生效权限须精确等于 `SELECT, INSERT`，按表级/库级/全局分级出整改语句 | §4.5 |

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

## 四、验收与校验（可重复执行）

### 4.1 三员分立与审计防篡改

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
- 迁移幂等：V36 重跑 affected 全 0；V37 二次执行 `affected=0`，约束 `role_ids` 稳定为 `[26004, 26005, 26006, 1]`；V3（audit 库）二次执行不报重复列。

### 4.2 归档完整性（哈希 + 链 + 可选签名）

归档时每批产出两个对象：`auditLog-archive-<ts>-<rand>.csv` 与同名 `…csv.sha256` 清单；同一组凭证写入
`audit_archive_watermark`（`last_batch_sha256` / `chain_sha256` / `last_object_key` / `last_manifest_key` /
`last_batch_rows` / `archived_total`）。

```bash
# 单文件自证（自动发现同名 .sha256 清单）
python scripts/verify/archive-hash-verify.py auditLog-archive-20261101-1a2b3c4d.csv

# 校验签名（密钥 = whale.audit.export.sign-key）
python scripts/verify/archive-hash-verify.py xxx.csv --hmac-key "$WHALE_AUDIT_SIGN_KEY"

# 生产审计：要求必须有签名，未签名直接不通过（rc=1）
python scripts/verify/archive-hash-verify.py xxx.csv --hmac-key "$KEY" --require-signature

# 校验跨批连续性：prev 取上一批 chain_sha256（见下方 SQL）
python scripts/verify/archive-hash-verify.py xxx.csv --prev-chain <上一批 chain>

# 取链头（当前水位）
SELECT chain_sha256, archived_total, last_object_key, last_manifest_key
FROM bluewhale_audit.audit_archive_watermark WHERE id = 1;
```

退出码：`0` 全部通过 / `1` 哈希或链值或签名不符 / `2` 用法或文件缺失。

本轮实测：

| 场景 | 结果 |
| --- | --- |
| Java 生成的清单交给 Python 校验（跨实现一致性） | `OK 校验通过（3 项）` rc=0 |
| 篡改 CSV 一个字节 | rc=1，报 `CSV 内容哈希与清单不符` |
| 错误密钥验签 | rc=1，报 `清单签名不匹配` |
| 错误前驱链 | rc=1，报 `链值不连续` |
| 不给密钥（只校验哈希） | rc=0，但**必打三行 WARN**：明确写出「只能证明未被误改，不能证明未被有意改写」 |
| 未签名清单 + `--require-signature` | rc=1，报 `清单无 HMAC 签名或未提供 --hmac-key` |
| 清单缺失 | rc=2 |

> 链式哈希能发现「改批、删批、插批」；配了签名密钥后，**即使有数据库写权限**也无法为新批次生成可验证签名。
> 注意：历史批次若在配置密钥之前归档，其清单 `hmac=` 为空，验签时不要对那批强制 `--hmac-key`。

**签名策略（服务端）**

| 配置 | 行为 |
| --- | --- |
| `whale.audit.export.sign-key` 有值 | 清单尾部追加 64 位 HMAC；指标 `audit.archive.signed=1` |
| 为空 + `require-sign-key=false`（默认） | 照常归档，但每轮 WARN 且 `audit.archive.signed=0` |
| 为空 + `require-sign-key=true` | **归档直接失败、不产生任何批次**，失败经作业心跳暴露为 `audit.job.stale.days` 增长 |

> 为什么默认不 fail-fast：会让尚未配密钥的环境（含本地开发）整条归档链路起不来。
> 生产应在部署环境注入 `WHALE_AUDIT_EXPORT_SIGN_KEY` 并置 `WHALE_AUDIT_EXPORT_REQUIRE_SIGN_KEY=true`，
> 告警规则加一条 `audit.archive.signed == 0`——签名缺失时归档批次计数、行数、作业心跳<b>全部正常</b>，
> 只有这个指标能区分「归档物不可验签」。密钥禁止写进仓库与镜像。

### 4.3 审计留存巡检（6 个月）

- 指标：`audit.log.oldest.days`（Gauge，`/actuator/prometheus` 可见），值 = `audit_log` 最早记录距今天数。
- 告警口径：`audit.log.oldest.days < 180` 且 `!= -1`（-1 表示表为空/查询失败，属「无数据」，不要当违规）。
- 巡检随 `audit.log.retention` 调度执行（默认每日 03:00），不新增 job 注册；日志里同步 WARN/INFO，可直接作为巡检证据。
- 阈值可配：`whale.audit.retention.minimum-days`（默认 180）。

实测（2026-11-20，本地库）：巡检返回并落 Gauge；单测 5 例覆盖达标 / 不足 / 空表 / 查询异常 / 阈值非法兜底。

### 4.4 作业心跳与陈旧告警（8.1.4.3 b：审计记录持续产生）

留存巡检能发现「留存不足」，但发现不了**作业压根没跑**——调度被停、cron 写错、容器反复重启导致从未触发，这三种情况在监控上都表现为「指标不更新」，与健康状态完全同形。

| 环节 | 落地 |
| --- | --- |
| 心跳落库 | `audit_job_heartbeat`（V4，主键 `job_code`）：`last_start_at` / `last_end_at` / `last_success_at` / `last_status` / `last_message` / `run_count` / `fail_count`。**落库而非内存**——内存计时器重启即清零，每晚重建容器的环境里会把「两周没跑」洗成「刚跑过」 |
| 埋点 | `LogRetentionScheduler#cleanExpiredLogs` 全程包裹：开始写 `RUNNING`，成功推进 `last_success_at`，失败只加 `fail_count` 并原样抛出（调度中心仍能记失败） |
| 指标 | `audit.job.stale.days{job="audit.log.retention"}`，抓取时读库（主键单行）+ 30 秒本地缓存；`-1` = 从未成功 |
| 告警口径 | `> whale.audit.retention.stale-alert-days`（默认 2 天）；同一段告警只打一次 WARN，恢复时打 INFO |
| 隔离要求 | 表无 `tenant_id`，已加入 `whale.tenant.sql.ignore-tables` 默认值，并由 `AuditJobHeartbeatTenantIgnoreTest` 钉住（漏加会 `Unknown column 'tenant_id'`，作业每轮静默失败） |

```sql
-- 取心跳（作业是否在跑、跑成没跑成，一眼可见）
SELECT job_code, last_start_at, last_success_at, last_status,
       TIMESTAMPDIFF(DAY, last_success_at, NOW()) AS stale_days,
       run_count, fail_count, last_message
FROM bluewhale_audit.audit_job_heartbeat;
```

本轮实测：

- V4 迁移在 `bluewhale_audit` 连跑两次，第二次 `affected=0` 不报错（幂等）。
- 单测 18 例：记录器 8 例（首次插入 / 只更新 / 成功推进 / 失败不推进 / 首次即失败补插 / 库挂不抛 / 超长截断 / 自增 SQL）+ 指标 8 例（无记录 / 有行无成功时间 / 3 天前 / 刚成功 / 时钟回拨 / 查询异常 / 缓存 / invalidate）+ 调度器 2 例（成功写心跳、异常写失败并重抛）+ 白名单 1 例。
- 负向验证：把 `audit_job_heartbeat` 从 `ignore-tables` 默认值里删掉 → `AuditJobHeartbeatTenantIgnoreTest` 立即 FAIL（`expected: <true> but was: <false>`），恢复后重新 install 通过。

### 4.5 应用账号按表收权（8.1.4.3 c：审计记录保护的第二道防线）

现状是「触发器守得住改写，守不住拆守卫」：应用账号仍持 `UPDATE`/`DELETE`/`ALTER`，而 `ALTER` 能直接 `DROP TRIGGER`。权限层收口后，应用账号连这些语法都发不出去。

```bash
# 核查应用账号对全部含 audit_log 的库的生效权限（全局 ∪ 库级 ∪ 表级）
DB_USER=root DB_PWD=*** java -cp F:/respository/maven/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar \
     scripts/security/AuditDbGrantCheck.java --app-user=whale_app --app-host=%
#   --schemas=a,b   只核查指定库
```

判定规则：`audit_log` 的生效权限必须**精确等于** `SELECT, INSERT`（缺一即 FAIL——缺 INSERT 意味着审计写不进去，同样是事故）。

关键设计：MySQL 权限是**累加**的，表级 `REVOKE` 抵不掉库级授权。所以输出按来源分级，整改代价不同：

| 越权来源 | 输出 | 代价 |
| --- | --- | --- |
| 表级（`TABLE_PRIVILEGES`） | `REVOKE UPDATE ON db.audit_log FROM ...` | 一行精确收敛，不影响其他表 |
| 库级（`SCHEMA_PRIVILEGES`） | `REVOKE UPDATE ON db.* FROM ...` + 提示「须评估后逐表补授」 | 影响该库全部表，属授权模型调整 |
| 全局（`USER_PRIVILEGES`，如 root） | 只给警告，**不生成**表级语句 | 表级语句在此处无效，只能改用专用应用账号 |

退出码：`0` 全部收口 / `1` 存在越权或缺失 / `2` 用法错误或账号不存在（会列出该用户名实际的 host，避免 `root@localhost` 被写成 `root@%` 的空转）。

本轮实测（临时账号 `qa_grant_probe`，验证后已 DROP）：

| 场景 | 结果 |
| --- | --- |
| 表级仅 SELECT+INSERT | `[PASS]`，rc=0 |
| 表级多授 UPDATE | `[FAIL]` + `REVOKE UPDATE ON db.audit_log`（表级精确收敛） |
| 库级授 SELECT 但缺 INSERT | `[FAIL]` + `GRANT INSERT ON db.audit_log`（缺失也要报，避免审计写不进） |
| 库级授 UPDATE | `[FAIL]` + `REVOKE UPDATE ON db.*`（提示影响全库） |
| 按输出语句整改后重跑 4 个库 | 全部 `[PASS]`，rc=0 |
| `root@localhost`（全局 ALL） | 24 个库全 `[FAIL]` + 一条全局警告，不刷 24 条无用 REVOKE |

> 本地开发常直接用 root，此检查必然 FAIL——它是**交付闸门**，与 `super-admin-retirement.sql` 同类，**不要挂 CI**。
> 生产建议：为应用新建专用账号，仅授各库 `SELECT, INSERT ON <db>.audit_log` + 业务表所需权限，而不是继续共用管理账号。

## 五、三员账号全生命周期（运维必须照做）

| 阶段 | 动作 | 判据 |
| --- | --- | --- |
| 申请 | 三方书面/工单确认（系统管理员、安全管理员、安全审计员各 1 人，不得兼任） | 有审批记录；三人不同自然人 |
| 开通 | 平台 `super_admin` 建 3 个独立账号，分别授 `system_admin` / `security_admin` / `audit_admin` | 授第二个三员角色时被 `three_admin_mutex` 拒绝 |
| 首登 | 强制改密（管理员重置口令后 `must_change_password=1`） | 登录响应 `mustChangePassword=true`，未改密不可进业务 |
| 日常 | 三员权限集**不得**在管理端调整（需变更时发新迁移改白名单） | 角色页「分配权限」置灰；后端 `RoleAppServiceImpl` 拒绝 |
| 变更 | 换人＝新开账号 + 停用旧账号，禁止共用账号 | 登录历史可追溯到具体人 |
| 离岗 | 停用账号（三员角色本身不可停用，停的是**账号**）+ 强制下线全部会话 | `sys_user.status=0` + `SessionController` 强制下线留痕 |
| 交付收尾 | **停用或改密 `super_admin`** | 超管是代码级硬旁路（`PermissionInterceptor` 0 授权也放行），长期在线即等于职责分离失效。用 `scripts/verify/super-admin-retirement.sql` 验：01 启用超管数=0、02 近 7 天超管成功登录=0、03 三员各有启用账号、04 无账号同时持超管与三员，**4 条全 PASS 才能签字** |

## 六、残余风险与后续

| # | 风险 | 现状 | 建议 |
| --- | --- | --- | --- |
| 1 | 三员账号「双人复核启用」未实现 | 无审批流模型，本轮不做 | 复用 workflow 模块做「开通/授权双人复核」；等保三级为可选项，四级必需 |
| 2 | 历史归档批次（配密钥之前）清单 `hmac=` 为空 | 已归档批次不可追溯补签（补签等于伪造时间线）；本批起服务端可强制签名（`require-sign-key`），未签名会以 `audit.archive.signed=0` 暴露 | 生产注入 `WHALE_AUDIT_EXPORT_SIGN_KEY` + `WHALE_AUDIT_EXPORT_REQUIRE_SIGN_KEY=true`；审计时对历史批次只做 SHA-256 + 链值校验 |
| 3 | 归档链值无外部锚点 | 链头只在自家库 | 交付时把 `chain_sha256` + `archived_total` 抄送第三方（邮件/纸质）封存，作为链头锚点 |
| 4 | 应用账号收权依赖人工执行 | 核查工具已就绪（§4.5），但**尚未改动任何现有账号授权** | 交付前用 `AuditDbGrantCheck` 出清单 → 评估 → 新建专用应用账号 → 切连接串；不要对共用管理账号做表级 REVOKE（无效） |

> 已关闭项：归档哈希与签名、留存 6 个月强制校验、超管退役技术巡检（技术仍拦不住超管本身，
> 但「没退役」一定能被查出来）、作业存活监控（把「作业没跑」从「指标不更新」变成可告警的正数）。
> 应用账号收权在第四批给出可执行的判定与整改工具，但**动作本身仍待运维执行**，故保留在上表。
>
> 注意区分「工具就绪」与「已收权」：`AuditDbGrantCheck` 只读、不改授权，本轮实测用的是临时账号，
> 现有账号权限**未被修改**。收权动作涉及连接串与授权模型，必须由运维在交付环境按 §4.5 执行并留存输出。

## 七、变更清单（本轮新增）

| 文件 | 作用 |
| --- | --- |
| `scripts/security/check-audit-append-only.py` | 代码层门禁：禁止对 `audit_log` 与四张通道日志表的 UPDATE/DELETE/REPLACE/TRUNCATE（含既有豁免白名单） |
| `scripts/security/AuditAppendOnlyGuard.java` | 数据库层守卫：给所有含 `audit_log` 的库建 BEFORE UPDATE/DELETE 触发器，内置负向验证 |
| `scripts/verify/three-admin-assertions.sql` | 11 条只读验收断言，输出 PASS/FAIL |
| `whale-audit/.../db/migration/V2__audit_log_append_only_guard.sql` | 新环境自动带守卫（Flyway） |
| `whale-audit/.../db/init.sql` | 建库脚本同步触发器 DDL |
| `auth/.../db/migration/V37__super_admin_three_admin_mutex.sql` | 超管并入 `three_admin_mutex`，幂等 |
| `scripts/verify/archive-hash-verify.py` | 归档完整性校验：CSV 哈希 / 链值连续性 / 清单 HMAC 验签，退出码 0/1/2 |
| `scripts/verify/super-admin-retirement.sql` | 交付闸门：超管退役 + 三员账号就位 4 条断言（未收尾时必然 FAIL，**不要挂 CI**） |
| `whale-audit/.../db/migration/V3__archive_watermark_chain.sql` | 水位表补 6 列（批次哈希/链值/两份 objectKey/行数/累计），幂等 |
| `whale-audit/.../support/AuditArchiveDigest.java` | SHA-256、链式哈希、HMAC、清单生成与命名 |
| `whale-audit/.../retention/AuditRetentionInspector.java` | 留存巡检：最早记录距今天数 → `audit.log.oldest.days` + WARN |
| `whale-audit/.../db/migration/V4__audit_job_heartbeat.sql` | 作业心跳表（幂等 `CREATE TABLE IF NOT EXISTS`），并由 `db/init.sql` 同步 |
| `whale-audit/.../retention/AuditJobHeartbeatRecorder.java` | 心跳写入：开始 / 成功 / 失败三态，库故障不反噬主任务 |
| `whale-audit/.../retention/AuditJobStaleGauge.java` | 指标 `audit.job.stale.days{job}`：抓取读库 + 30s 缓存 + 告警去重 |
| `scripts/security/AuditDbGrantCheck.java` | 应用账号最小权限核查：按「表级 / 库级 / 全局」分级给出整改语句，只读不改授权 |
| `scripts/check-reauth-wiring.py` | **修复**：原先只在控制器单文件里找 `@XxxMapping`，而本项目控制器普遍 `implements` 接口契约（路径在接口、`@RequireReauth` 在实现类），解析出 0 个端点后直接放行——CI 长期绿灯但什么都没查。现按 `implements` 回溯接口取路径，解析不到即 rc=1 |
| `whale-audit/.../support/AuditMetrics.java` | 新增 `audit.archive.signed`（1/0/-1），签名缺失时唯一能区分的信号 |
| `whale-audit/.../config/AuditExportProperties.java` | 新增 `require-sign-key`：无密钥时拒绝归档而不是产出不可验签的批次 |
| `scripts/verify/archive-hash-verify.py` | 无签名清单不再静默通过（三行 WARN），新增 `--require-signature` 作生产审计闸门 |

> 守卫范围**只到 `audit_log`**：`api_log` / `outbound_log` / `frontend_log` / `behavior_log` 有保留期清理任务（`LogRetentionCleaner` 按 cron 删除过期行），给它们加 DELETE 守卫会让保留期失效、表无限膨胀。这一条是本轮踩到又修正的口径，别再「顺手扩大到四张表」。
