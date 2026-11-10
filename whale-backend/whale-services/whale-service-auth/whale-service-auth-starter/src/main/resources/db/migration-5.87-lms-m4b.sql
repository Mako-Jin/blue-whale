-- =====================================================================
-- Wave 5.87 M4-2：学习培训考试（whale-lms）学分规则与台账菜单增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.87-lms-m4b.sql
-- 幂等说明 : 菜单为 NOT EXISTS 幂等执行，可重复执行；不删除、不覆盖任何既有数据。
-- 背景     : whale-lms M4-2 学分（规则维护 + 记账 + 年度汇总）。
-- 权限说明 : 本波次不新增权限点——管理端用 m4a 已登记的 1949（sys:lms:credit:manage），
--            学员查看本人学分复用 1948（sys:lms:cert:mine，语义为「我的证书与学分」）。
--            LMS 段 1900–1949 至此用满，后续看板权限需另申请号段。
-- 菜单编号 : LMS 段 450–469；本脚本使用 458（学分）。
-- 前置条件 : bluewhale_lms 已由 Flyway V7 建表；migration-5.87-lms-m4a.sql 已执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 菜单 458（学分），挂在 450 学习培训下
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 458, 1, 450, '学分', '/lms/credit', 'lms/credit/index', 'medal', 458, 'MENU', 'sys:lms:credit:manage', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 458);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, name, path FROM sys_menu WHERE id = 458;
--   SELECT id, perm_code FROM sys_permission WHERE id IN (1948, 1949);
-- =====================================================================
