-- =============================================================================
-- 文件        : V36__three_admin_segregation.sql
-- 说明        : 等保 2.0 三员分立 —— 系统管理员 / 安全管理员 / 安全审计员
--
-- 背景（docs/plans/2026-11-19-three-admin-backlog.md 第 1/2/3 节）：
--   现状只有 sys_role 的通用业务角色与 tenant_admin，三员只能靠「自建角色 + 手工勾权限」凑，
--   于是出现三类静默失效：
--     1) 没人建三员角色 → 代码/文档里谈的职责分离在库里根本不存在；
--     2) 权限靠手工勾 → 安全管理员顺手给自己勾上 sys:audit:log:list，职责分离被绕过且无人察觉；
--     3) 没有互斥约束 → 同一用户可同时持有审计员与系统管理员，等于「自己干、自己审」。
--
-- 本脚本（幂等，判重键一律不含固定 id）：
--   1. 建三员角色（平台租户固定号段 26004–26006；业务租户按 MD5(tenant:role) 派生主键复制）
--   2. 按职责矩阵白名单授权（三段，逐码枚举；不是「全量减」——差异就是安全边界）
--   3. 从 tenant_admin 收回审计日志查看权（审计只归安全审计员；super_admin 仍走代码级旁路）
--   4. 播种三员互斥约束（MUTEX：三员两两互斥，同一用户最多持有一个）
--
-- 号段登记：
--   角色编号 : 26004 = system_admin；26005 = security_admin；26006 = audit_admin
--              （26000–26099 段，V10 已用 26001–26003）
--   约束编号 : 主键 = MD5(tenant_id:three_admin_mutex) 前 15 位，重复执行 ID 稳定；
--              constraint_code 固定 'three_admin_mutex'（uk_constraint_code 兜底）
--   权限点   : 不新增（复用既有 966 个权限码），只在 sys_role_permission 建关联
--
-- 判重键（与既有脚本一致，禁止含 id）：
--   角色 (tenant_id, role_code) / 角色权限 (role_id, permission_id) / 约束 (tenant_id, constraint_code)
--
-- 校验（执行后应满足）：
--   -- a) 三员角色在每个租户各 1 条
--   SELECT t.id, COUNT(r.id) FROM sys_tenant t
--   LEFT JOIN sys_role r ON r.tenant_id=t.id AND r.deleted=0
--        AND r.role_code IN ('system_admin','security_admin','audit_admin')
--   WHERE t.deleted=0 GROUP BY t.id;                     -- 期望每行 = 3
--   -- b) 互斥约束每租户 1 条且 role_ids 含 3 个角色
--   SELECT tenant_id, constraint_code, role_ids FROM sys_role_constraint
--   WHERE constraint_code='three_admin_mutex' AND deleted=0;   -- 期望 role_ids 形如 [26004,26005,26006]
--   -- c) 职责分离反向断言：三员「写权限」零重叠，重叠只允许出现在只读选人码上
--   --    期望重叠集合 = sys:user:list / sys:user:group:list / sys:user:profile:view /
--   --    sys:role:list / sys:tenant:member:list / sys:login:history:list（均为只读）
--   SELECT r.role_code, p.perm_code FROM sys_role r
--   JOIN sys_role_permission rp ON rp.role_id=r.id
--   JOIN sys_permission p ON p.id=rp.permission_id
--   WHERE r.deleted=0 AND r.tenant_id=1
--     AND r.role_code IN ('system_admin','security_admin','audit_admin')
--   ORDER BY r.role_code, p.perm_code;
--   -- d) 审计权限只归 audit_admin（tenant_admin 已被收回）
--   SELECT r.role_code FROM sys_role r
--   JOIN sys_role_permission rp ON rp.role_id=r.id
--   JOIN sys_permission p ON p.id=rp.permission_id AND p.perm_code='sys:audit:log:list'
--   WHERE r.deleted=0;                                   -- 期望只有 audit_admin
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1. 三员角色模板（平台租户 tenant_id = 1）
--    category = 'SYSTEM' 仅用于管理端分组展示；内置保护判据是角色编码
--    （AuthRoleCodes.isBuiltIn），不依赖 category —— 历史数据可能写成 GENERAL。
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status, category)
SELECT 26004, 1, 'system_admin', '系统管理员',
       '三员之一：负责用户/组织/菜单/任务/文件/系统配置等运行维护，不含角色授权与安全策略，不可查看审计',
       10, 1, 'SYSTEM'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = 1 AND role_code = 'system_admin');

INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status, category)
SELECT 26005, 1, 'security_admin', '安全管理员',
       '三员之一：负责角色授权、权限模型、口令与会话策略、密钥与网关安全，不含用户/组织维护，不可查看审计',
       11, 1, 'SYSTEM'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = 1 AND role_code = 'security_admin');

INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status, category)
SELECT 26006, 1, 'audit_admin', '安全审计员',
       '三员之一：只读查询与导出审计记录与登录历史，不含任何业务数据与管理写权限',
       12, 1, 'SYSTEM'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE tenant_id = 1 AND role_code = 'audit_admin');

-- ---------------------------------------------------------------------
-- 2. 复制三员角色到已有业务租户
--    角色管理接口按 TenantContext 过滤 tenant_id，平台租户的模板角色在业务租户
--    不可见也不能用于授权计算，必须逐租户复制一份。新租户由
--    TenantProvisioningAppService 在开通时直接创建（两处编码必须一致）。
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status, category)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, 1, 'SYSTEM'
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':', x.role_code)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           t.id AS tenant_id, x.role_code AS role_code, x.role_name AS role_name,
           x.description AS description, x.sort_no AS sort_no
    FROM sys_tenant t
    CROSS JOIN (
        SELECT 'system_admin' AS role_code, '系统管理员' AS role_name,
               '三员之一：负责用户/组织/菜单/任务/文件/系统配置等运行维护，不含角色授权与安全策略，不可查看审计' AS description,
               10 AS sort_no
        UNION ALL
        SELECT 'security_admin', '安全管理员',
               '三员之一：负责角色授权、权限模型、口令与会话策略、密钥与网关安全，不含用户/组织维护，不可查看审计',
               11
        UNION ALL
        SELECT 'audit_admin', '安全审计员',
               '三员之一：只读查询与导出审计记录与登录历史，不含任何业务数据与管理写权限',
               12
    ) x
    WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role tgt
    WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code)
);

-- ---------------------------------------------------------------------
-- 3. 职责矩阵授权（白名单逐码枚举）
--    三段互不重叠，只有 sys:user:list / sys:user:group:list 这类「选人只读码」
--    在两个角色间共享（授权操作要选目标用户，不共享则谁都授不了角色）。
--    授权段对所有租户的同名角色生效（平台 + 业务租户一次覆盖）。
-- ---------------------------------------------------------------------

-- 3.1 系统管理员：管人、管组织、管运行，不管安全策略、不管授权、不看审计
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
         AND p.perm_code IN (
             -- 账号与用户组
             'sys:user:list', 'sys:user:add', 'sys:user:edit', 'sys:user:delete',
             'sys:user:status', 'sys:user:resetpwd', 'sys:user:import', 'sys:user:export',
             'sys:user:group:list', 'sys:user:group:add', 'sys:user:group:edit',
             'sys:user:group:delete', 'sys:user:group:member',
             -- 用户档案（不含凭证视图，凭证属安全管理员）
             'sys:user:profile:view', 'sys:user:profile:save', 'sys:user:profile:delete',
             -- 组织与菜单
             'sys:dept:list', 'sys:dept:add', 'sys:dept:edit', 'sys:dept:delete',
             'sys:menu:list', 'sys:menu:add', 'sys:menu:edit', 'sys:menu:delete',
             -- 角色只读：能看有哪些角色，但授不出去
             'sys:role:list',
             -- 定时任务
             'sys:job:list', 'sys:job:add', 'sys:job:edit', 'sys:job:delete',
             'sys:job:log:list', 'sys:job:run',
             -- 文件
             'sys:file:list', 'sys:file:upload', 'sys:file:download',
             'sys:file:delete', 'sys:file:manage',
             -- 系统配置与多语言
             'sys:config:list', 'sys:config:add', 'sys:config:edit',
             'sys:config:delete', 'sys:config:rollback',
             'sys:i18n:list', 'sys:i18n:add', 'sys:i18n:edit', 'sys:i18n:delete',
             -- 租户自身配置与成员只读
             'sys:tenant:config:list', 'sys:tenant:config:edit', 'sys:tenant:member:list',
             -- 运维观测
             'sys:monitor:list', 'sys:alert:record:list',
             'sys:alert:rule:list', 'sys:alert:rule:add', 'sys:alert:rule:edit', 'sys:alert:rule:delete'
         )
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'system_admin'
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);

-- 3.2 安全管理员：管授权与安全策略，不管人、不管组织、不看审计
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
         AND p.perm_code IN (
             -- 角色与授权（含分配角色给用户：这是行使用户-角色矩阵的唯一入口）
             'sys:role:list', 'sys:role:add', 'sys:role:edit', 'sys:role:delete', 'sys:role:assign',
             'sys:user:list', 'sys:user:assign',
             'sys:user:group:list',
             'sys:tenant:member:list', 'sys:tenant:member:assign',
             -- 权限点与策略
             'sys:permission:list', 'sys:permission:add', 'sys:permission:edit', 'sys:permission:delete',
             'sys:perm:strategy:list', 'sys:perm:strategy:add', 'sys:perm:strategy:edit',
             'sys:perm:strategy:delete', 'sys:perm:strategy:set-default',
             'sys:role:constraint:list', 'sys:role:constraint:add',
             'sys:role:constraint:edit', 'sys:role:constraint:delete',
             -- 口令策略与会话
             'sys:tenant:pwdpolicy:list', 'sys:tenant:pwdpolicy:edit',
             'sys:session:list', 'sys:session:revoke',
             'sys:login:history:list', 'sys:login:history:export',
             -- 密钥与 OAuth 客户端
             'sys:oauth:client:list', 'sys:oauth:client:add', 'sys:oauth:client:edit',
             'sys:oauth:client:delete', 'sys:oauth:client:secret',
             -- 网关安全边界
             'sys:gateway:whitelist:list', 'sys:gateway:whitelist:add',
             'sys:gateway:whitelist:edit', 'sys:gateway:whitelist:delete',
             'sys:gateway:blacklist:list', 'sys:gateway:blacklist:add',
             'sys:gateway:blacklist:edit', 'sys:gateway:blacklist:delete',
             'sys:gateway:route-override:list', 'sys:gateway:route-override:add',
             'sys:gateway:route-override:edit', 'sys:gateway:route-override:delete',
             -- 凭证视图（安全事件取证），不含档案写
             'sys:user:profile:view', 'sys:user:profile:credential-view'
         )
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'security_admin'
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);

-- 3.3 安全审计员：只读审计（查询与导出走同一 /audit/log/** 谓词），零写权限
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.deleted = 0 AND p.status = 1
         AND p.perm_code IN (
             'sys:audit:log:list',
             'sys:login:history:list'
         )
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'audit_admin'
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);

-- ---------------------------------------------------------------------
-- 4. 从 tenant_admin 收回审计日志查看权
--    等保三员分立要求「管理员不得查看审计、审计员不得参与管理」。tenant_admin 的
--    授权口径本是「全部启用权限 − 平台专属码」，会把 sys:audit:log:list 一并带上，
--    于是租户管理员可以查、甚至导出审计记录 —— 职责分离失效。
--
--    这是全部迁移里唯一一处物理删除（sys_role_permission 无 deleted 列），谓词锁定
--    「tenant_admin × sys:audit:log:list」单点，不会误伤其他授权；已在
--    FlywayMigrationScriptTest.ALLOWED_DELETE_SCRIPTS 登记理由。
--
--    回退：DELETE 后如需恢复，重跑「INSERT ... SELECT role_id, permission_id
--    WHERE role_code='tenant_admin' AND perm_code='sys:audit:log:list'」即可。
-- ---------------------------------------------------------------------
DELETE FROM sys_role_permission
WHERE role_id IN (SELECT id FROM sys_role WHERE deleted = 0 AND role_code = 'tenant_admin')
  AND permission_id IN (SELECT id FROM sys_permission WHERE perm_code = 'sys:audit:log:list');

-- ---------------------------------------------------------------------
-- 5. 三员互斥约束（MUTEX）
--    三员两两互斥：同一用户最多持有系统管理员 / 安全管理员 / 安全审计员中的一个。
--    role_ids 是 JSON 数组（RoleConstraintPersistenceAssembler 用 Jackson 解析，
--    GROUP_CONCAT 输出的紧凑格式 [1,2,3] 可直接反序列化）。
--    分配角色时由 RoleConstraintCheckerImpl 校验，违约返回带约束名的业务异常。
-- ---------------------------------------------------------------------
INSERT INTO sys_role_constraint (id, tenant_id, constraint_code, constraint_name, constraint_type,
                                 role_ids, max_assign, status, description)
SELECT src.id, src.tenant_id, 'three_admin_mutex', '三员互斥', 'MUTEX',
       src.role_ids, NULL, 1,
       '等保 2.0 三员分立：同一用户最多持有系统管理员 / 安全管理员 / 安全审计员中的一个'
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':three_admin_mutex')), 1, 15), 16, 10) AS UNSIGNED) AS id,
           t.id AS tenant_id,
           CONCAT('[',
                  GROUP_CONCAT(r.id ORDER BY FIELD(r.role_code, 'system_admin', 'security_admin', 'audit_admin')
                               SEPARATOR ','),
                  ']') AS role_ids
    FROM sys_tenant t
    JOIN sys_role r ON r.tenant_id = t.id AND r.deleted = 0
         AND r.role_code IN ('system_admin', 'security_admin', 'audit_admin')
    WHERE t.deleted = 0
    GROUP BY t.id
    HAVING COUNT(DISTINCT r.role_code) >= 3
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_constraint tgt
    WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.constraint_code = 'three_admin_mutex')
);
