-- =============================================================================
-- 三员分立验收断言集（等保 2.0 三员分立条款的可重复验收）
-- =============================================================================
-- 用法（仓库根执行，输出每行 PASS/FAIL；有任一行 FAIL 即验收不通过）：
--   java -cp F:/respository/maven/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar \
--        .workbuddy/tmp/DbQ.java "$(cat scripts/verify/three-admin-assertions.sql)"
-- 说明：
--   · 这里只做「只读断言」，不改数据；可在交付前、变更后、巡检时反复跑。
--   · 断言口径与 V36/V37 迁移、AuthRoleCodes、RoleConstraintCheckerImpl 一致。
--   · 第 10 条依赖 scripts/security/AuditAppendOnlyGuard.java 已执行（数据库层防篡改）。
-- =============================================================================
SELECT '01 平台三员角色齐备(期望3)' AS check_name, '3' AS expected,
       CAST((SELECT COUNT(*) FROM bluewhale_auth.sys_role
             WHERE tenant_id = 1 AND deleted = 0
               AND role_code IN ('system_admin', 'security_admin', 'audit_admin')) AS CHAR) AS actual,
       IF((SELECT COUNT(*) FROM bluewhale_auth.sys_role
           WHERE tenant_id = 1 AND deleted = 0
             AND role_code IN ('system_admin', 'security_admin', 'audit_admin')) = 3, 'PASS', 'FAIL') AS result
UNION ALL
SELECT '02 三员角色分组为SYSTEM(期望3)', '3',
       CAST((SELECT COUNT(*) FROM bluewhale_auth.sys_role
             WHERE tenant_id = 1 AND deleted = 0 AND category = 'SYSTEM'
               AND role_code IN ('system_admin', 'security_admin', 'audit_admin')) AS CHAR),
       IF((SELECT COUNT(*) FROM bluewhale_auth.sys_role
           WHERE tenant_id = 1 AND deleted = 0 AND category = 'SYSTEM'
             AND role_code IN ('system_admin', 'security_admin', 'audit_admin')) = 3, 'PASS', 'FAIL')
UNION ALL
SELECT '03 审计员权限恰为2条只读(期望2)', '2',
       CAST((SELECT COUNT(*) FROM bluewhale_auth.sys_role r
             JOIN bluewhale_auth.sys_role_permission rp ON rp.role_id = r.id
             WHERE r.tenant_id = 1 AND r.deleted = 0 AND r.role_code = 'audit_admin') AS CHAR),
       IF((SELECT COUNT(*) FROM bluewhale_auth.sys_role r
           JOIN bluewhale_auth.sys_role_permission rp ON rp.role_id = r.id
           WHERE r.tenant_id = 1 AND r.deleted = 0 AND r.role_code = 'audit_admin') = 2, 'PASS', 'FAIL')
UNION ALL
SELECT '04 审计员无非白名单权限(期望0)', '0',
       CAST((SELECT COUNT(*) FROM bluewhale_auth.sys_role r
             JOIN bluewhale_auth.sys_role_permission rp ON rp.role_id = r.id
             JOIN bluewhale_auth.sys_permission p ON p.id = rp.permission_id
             WHERE r.tenant_id = 1 AND r.deleted = 0 AND r.role_code = 'audit_admin'
               AND p.perm_code NOT IN ('sys:audit:log:list', 'sys:login:history:list')) AS CHAR),
       IF((SELECT COUNT(*) FROM bluewhale_auth.sys_role r
           JOIN bluewhale_auth.sys_role_permission rp ON rp.role_id = r.id
           JOIN bluewhale_auth.sys_permission p ON p.id = rp.permission_id
           WHERE r.tenant_id = 1 AND r.deleted = 0 AND r.role_code = 'audit_admin'
             AND p.perm_code NOT IN ('sys:audit:log:list', 'sys:login:history:list')) = 0, 'PASS', 'FAIL')
UNION ALL
SELECT '05 租户管理员无审计查看权(期望0)', '0',
       CAST((SELECT COUNT(*) FROM bluewhale_auth.sys_role r
             JOIN bluewhale_auth.sys_role_permission rp ON rp.role_id = r.id
             JOIN bluewhale_auth.sys_permission p ON p.id = rp.permission_id
             WHERE r.tenant_id = 1 AND r.deleted = 0 AND r.role_code = 'tenant_admin'
               AND p.perm_code = 'sys:audit:log:list') AS CHAR),
       IF((SELECT COUNT(*) FROM bluewhale_auth.sys_role r
           JOIN bluewhale_auth.sys_role_permission rp ON rp.role_id = r.id
           JOIN bluewhale_auth.sys_permission p ON p.id = rp.permission_id
           WHERE r.tenant_id = 1 AND r.deleted = 0 AND r.role_code = 'tenant_admin'
             AND p.perm_code = 'sys:audit:log:list') = 0, 'PASS', 'FAIL')
UNION ALL
SELECT '06 三员重叠权限全在只读白名单(期望0)', '0',
       CAST((SELECT COUNT(*) FROM (
               SELECT p.perm_code
               FROM bluewhale_auth.sys_role r
               JOIN bluewhale_auth.sys_role_permission rp ON rp.role_id = r.id
               JOIN bluewhale_auth.sys_permission p ON p.id = rp.permission_id
               WHERE r.tenant_id = 1 AND r.deleted = 0
                 AND r.role_code IN ('system_admin', 'security_admin', 'audit_admin')
               GROUP BY p.perm_code
               HAVING COUNT(DISTINCT r.role_code) > 1
                  AND p.perm_code NOT IN ('sys:user:list', 'sys:user:group:list', 'sys:user:profile:view',
                                          'sys:role:list', 'sys:tenant:member:list', 'sys:login:history:list')
           ) overlap) AS CHAR),
       IF((SELECT COUNT(*) FROM (
               SELECT p.perm_code
               FROM bluewhale_auth.sys_role r
               JOIN bluewhale_auth.sys_role_permission rp ON rp.role_id = r.id
               JOIN bluewhale_auth.sys_permission p ON p.id = rp.permission_id
               WHERE r.tenant_id = 1 AND r.deleted = 0
                 AND r.role_code IN ('system_admin', 'security_admin', 'audit_admin')
               GROUP BY p.perm_code
               HAVING COUNT(DISTINCT r.role_code) > 1
                  AND p.perm_code NOT IN ('sys:user:list', 'sys:user:group:list', 'sys:user:profile:view',
                                          'sys:role:list', 'sys:tenant:member:list', 'sys:login:history:list')
           ) overlap) = 0, 'PASS', 'FAIL')
UNION ALL
SELECT '07 三员互斥约束已播种(期望1)', '1',
       CAST((SELECT COUNT(*) FROM bluewhale_auth.sys_role_constraint
             WHERE tenant_id = 1 AND deleted = 0 AND constraint_code = 'three_admin_mutex'
               AND constraint_type = 'MUTEX'
               AND JSON_CONTAINS(role_ids, '26004') AND JSON_CONTAINS(role_ids, '26005')
               AND JSON_CONTAINS(role_ids, '26006')) AS CHAR),
       IF((SELECT COUNT(*) FROM bluewhale_auth.sys_role_constraint
           WHERE tenant_id = 1 AND deleted = 0 AND constraint_code = 'three_admin_mutex'
             AND constraint_type = 'MUTEX'
             AND JSON_CONTAINS(role_ids, '26004') AND JSON_CONTAINS(role_ids, '26005')
             AND JSON_CONTAINS(role_ids, '26006')) = 1, 'PASS', 'FAIL')
UNION ALL
SELECT '08 超管已并入三员互斥(V37,期望1)', '1',
       CAST((SELECT COUNT(*) FROM bluewhale_auth.sys_role_constraint c
             WHERE c.tenant_id = 1 AND c.deleted = 0 AND c.constraint_code = 'three_admin_mutex'
               AND JSON_CONTAINS(c.role_ids, CAST((SELECT id FROM bluewhale_auth.sys_role
                                                   WHERE tenant_id = 1 AND deleted = 0
                                                     AND role_code = 'super_admin' LIMIT 1) AS JSON))) AS CHAR),
       IF((SELECT COUNT(*) FROM bluewhale_auth.sys_role_constraint c
           WHERE c.tenant_id = 1 AND c.deleted = 0 AND c.constraint_code = 'three_admin_mutex'
             AND JSON_CONTAINS(c.role_ids, CAST((SELECT id FROM bluewhale_auth.sys_role
                                                 WHERE tenant_id = 1 AND deleted = 0
                                                   AND role_code = 'super_admin' LIMIT 1) AS JSON))) = 1, 'PASS', 'FAIL')
UNION ALL
SELECT '09 无用户兼任多个三员角色(期望0)', '0',
       CAST((SELECT COUNT(*) FROM (
               SELECT ur.user_id
               FROM bluewhale_auth.sys_user_role ur
               JOIN bluewhale_auth.sys_role r ON r.id = ur.role_id AND r.deleted = 0
               WHERE ur.deleted = 0
                 AND r.role_code IN ('system_admin', 'security_admin', 'audit_admin')
               GROUP BY ur.user_id
               HAVING COUNT(DISTINCT r.role_code) > 1
           ) multi) AS CHAR),
       IF((SELECT COUNT(*) FROM (
               SELECT ur.user_id
               FROM bluewhale_auth.sys_user_role ur
               JOIN bluewhale_auth.sys_role r ON r.id = ur.role_id AND r.deleted = 0
               WHERE ur.deleted = 0
                 AND r.role_code IN ('system_admin', 'security_admin', 'audit_admin')
               GROUP BY ur.user_id
               HAVING COUNT(DISTINCT r.role_code) > 1
           ) multi) = 0, 'PASS', 'FAIL')
UNION ALL
SELECT '10 未加防篡改守卫的audit_log表(期望0)', '0',
       CAST((SELECT COUNT(*) FROM information_schema.tables t
             WHERE t.table_type = 'BASE TABLE' AND t.table_name = 'audit_log'
               AND t.table_schema NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys')
               AND 2 > (SELECT COUNT(DISTINCT g.event_manipulation) FROM information_schema.triggers g
                        WHERE g.trigger_schema = t.table_schema AND g.event_object_table = 'audit_log'
                          AND g.event_manipulation IN ('UPDATE', 'DELETE'))) AS CHAR),
       IF((SELECT COUNT(*) FROM information_schema.tables t
           WHERE t.table_type = 'BASE TABLE' AND t.table_name = 'audit_log'
             AND t.table_schema NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys')
             AND 2 > (SELECT COUNT(DISTINCT g.event_manipulation) FROM information_schema.triggers g
                      WHERE g.trigger_schema = t.table_schema AND g.event_object_table = 'audit_log'
                        AND g.event_manipulation IN ('UPDATE', 'DELETE'))) = 0, 'PASS', 'FAIL')
UNION ALL
SELECT '11 业务租户三员角色齐备(期望0缺失)', '0',
       CAST((SELECT COUNT(*) FROM (
               SELECT t.id
               FROM bluewhale_auth.sys_tenant t
               WHERE t.deleted = 0
                 AND 3 > (SELECT COUNT(DISTINCT r.role_code) FROM bluewhale_auth.sys_role r
                          WHERE r.tenant_id = t.id AND r.deleted = 0
                            AND r.role_code IN ('system_admin', 'security_admin', 'audit_admin'))
           ) miss) AS CHAR),
       IF((SELECT COUNT(*) FROM (
               SELECT t.id
               FROM bluewhale_auth.sys_tenant t
               WHERE t.deleted = 0
                 AND 3 > (SELECT COUNT(DISTINCT r.role_code) FROM bluewhale_auth.sys_role r
                          WHERE r.tenant_id = t.id AND r.deleted = 0
                            AND r.role_code IN ('system_admin', 'security_admin', 'audit_admin'))
           ) miss) = 0, 'PASS', 'FAIL')
ORDER BY check_name;
