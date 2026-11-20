-- =============================================================================
-- 超管退役 / 三员账号就位 巡检（等保 2.0 8.1.4.2 b：重命名或删除默认账户）
-- =============================================================================
-- 用法（仓库根执行，输出每行 PASS/FAIL）：
--   java -cp F:/respository/maven/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar \
--        .workbuddy/tmp/DbQ.java "$(cat scripts/verify/super-admin-retirement.sql)"
--
-- 定位：这是**交付收尾闸门**，不是常规 CI 门禁。
--   · 开发/联调环境超管必然在用，此时 01/02 报 FAIL 是预期结果，不代表故障；
--   · 正式交付前必须 4 条全 PASS 才能签字（超管是 PermissionInterceptor 的代码级硬旁路，
--     0 授权也能用，只要它还活着，三权分立就形同虚设）；
--   · 只读，不改任何数据，可反复跑。
-- =============================================================================
SELECT '01 启用中的超管账号数(期望0=已退役)' AS check_name, '0' AS expected,
       CAST((SELECT COUNT(DISTINCT u.id) FROM bluewhale_auth.sys_user u
             JOIN bluewhale_auth.sys_user_role ur ON ur.user_id = u.id AND ur.deleted = 0
             JOIN bluewhale_auth.sys_role r ON r.id = ur.role_id AND r.deleted = 0
             WHERE u.deleted = 0 AND u.status = 1 AND r.role_code = 'super_admin') AS CHAR) AS actual,
       IF((SELECT COUNT(DISTINCT u.id) FROM bluewhale_auth.sys_user u
           JOIN bluewhale_auth.sys_user_role ur ON ur.user_id = u.id AND ur.deleted = 0
           JOIN bluewhale_auth.sys_role r ON r.id = ur.role_id AND r.deleted = 0
           WHERE u.deleted = 0 AND u.status = 1 AND r.role_code = 'super_admin') = 0, 'PASS', 'FAIL') AS result
UNION ALL
SELECT '02 超管近7天成功登录次数(期望0)', '0',
       CAST((SELECT COUNT(*) FROM bluewhale_auth.sys_login_history h
             WHERE h.login_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)
               AND h.login_result = 'SUCCESS'
               AND h.user_id IN (SELECT ur.user_id FROM bluewhale_auth.sys_user_role ur
                                 JOIN bluewhale_auth.sys_role r ON r.id = ur.role_id AND r.deleted = 0
                                 WHERE ur.deleted = 0 AND r.role_code = 'super_admin')) AS CHAR),
       IF((SELECT COUNT(*) FROM bluewhale_auth.sys_login_history h
           WHERE h.login_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)
             AND h.login_result = 'SUCCESS'
             AND h.user_id IN (SELECT ur.user_id FROM bluewhale_auth.sys_user_role ur
                               JOIN bluewhale_auth.sys_role r ON r.id = ur.role_id AND r.deleted = 0
                               WHERE ur.deleted = 0 AND r.role_code = 'super_admin')) = 0, 'PASS', 'FAIL')
UNION ALL
SELECT '03 缺启用账号的三员角色数(期望0)', '0',
       CAST((SELECT COUNT(*) FROM (
               SELECT r.role_code
               FROM bluewhale_auth.sys_role r
               WHERE r.tenant_id = 1 AND r.deleted = 0
                 AND r.role_code IN ('system_admin', 'security_admin', 'audit_admin')
                 AND NOT EXISTS (
                     SELECT 1 FROM bluewhale_auth.sys_user_role ur
                     JOIN bluewhale_auth.sys_user u ON u.id = ur.user_id AND u.deleted = 0 AND u.status = 1
                     WHERE ur.role_id = r.id AND ur.deleted = 0)
           ) vac) AS CHAR),
       IF((SELECT COUNT(*) FROM (
               SELECT r.role_code
               FROM bluewhale_auth.sys_role r
               WHERE r.tenant_id = 1 AND r.deleted = 0
                 AND r.role_code IN ('system_admin', 'security_admin', 'audit_admin')
                 AND NOT EXISTS (
                     SELECT 1 FROM bluewhale_auth.sys_user_role ur
                     JOIN bluewhale_auth.sys_user u ON u.id = ur.user_id AND u.deleted = 0 AND u.status = 1
                     WHERE ur.role_id = r.id AND ur.deleted = 0)
           ) vac) = 0, 'PASS', 'FAIL')
UNION ALL
SELECT '04 同时持超管与三员的账号数(期望0)', '0',
       CAST((SELECT COUNT(*) FROM (
               SELECT ur.user_id
               FROM bluewhale_auth.sys_user_role ur
               JOIN bluewhale_auth.sys_role r ON r.id = ur.role_id AND r.deleted = 0
               WHERE ur.deleted = 0
               GROUP BY ur.user_id
               HAVING SUM(r.role_code = 'super_admin') > 0
                  AND SUM(r.role_code IN ('system_admin', 'security_admin', 'audit_admin')) > 0
           ) both_admin) AS CHAR),
       IF((SELECT COUNT(*) FROM (
               SELECT ur.user_id
               FROM bluewhale_auth.sys_user_role ur
               JOIN bluewhale_auth.sys_role r ON r.id = ur.role_id AND r.deleted = 0
               WHERE ur.deleted = 0
               GROUP BY ur.user_id
               HAVING SUM(r.role_code = 'super_admin') > 0
                  AND SUM(r.role_code IN ('system_admin', 'security_admin', 'audit_admin')) > 0
           ) both_admin) = 0, 'PASS', 'FAIL')
ORDER BY check_name;
