-- =====================================================================
-- Wave 5.82 M4：OA 公文与档案 权限增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.82-oa-m4-document.sql
-- 幂等说明 : 权限、菜单与授权均为 NOT EXISTS 幂等执行，可重复执行；
--            不删除、不覆盖任何既有数据。
-- 背景     : whale-oa 新增公文模板、文号规则、发文、收文与归档映射。
-- 权限编号 : OA 段 1600–1799；本脚本使用 1760–1771。
-- 菜单编号 : OA 段 360–409；本脚本使用 391–394。
-- 授权口径 : oa_admin、tenant_admin 获全量 1760–1771；
--            oa_manager 获 1760/1761/1762/1764/1765/1766/1767/1769/1770；
--            oa_member  获 1760/1761/1762/1764/1767/1770；
--            预留角色 oa_doc_admin(25126) 尚未建号，本脚本不授予。
-- 前置条件 : whale-oa V12__document.sql；whale-archive 支持 OA_DOC 来源类型。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 权限点 1760–1771
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1760, 0, 'sys:oa:document:list', '查询公文', 'API',
       '/oa/document/outgoing/page,/oa/document/outgoing/{id},/oa/document/incoming/page,/oa/document/incoming/{id},/oa/document/templates/page,/oa/document/templates/all,/oa/document/no-rules,/oa/document/archive/refs/page',
       'GET', 1760, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1760 OR perm_code = 'sys:oa:document:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1761, 0, 'sys:oa:document:add', '拟稿/收文登记', 'API',
       '/oa/document/outgoing,/oa/document/incoming', 'POST', 1761, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1761 OR perm_code = 'sys:oa:document:add');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1762, 0, 'sys:oa:document:edit', '编辑公文', 'API',
       '/oa/document/outgoing/{id},/oa/document/incoming/{id}', 'PUT', 1762, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1762 OR perm_code = 'sys:oa:document:edit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1763, 0, 'sys:oa:document:delete', '删除公文草稿', 'API',
       '/oa/document/outgoing/{id},/oa/document/incoming/{id}', 'DELETE', 1763, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1763 OR perm_code = 'sys:oa:document:delete');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1764, 0, 'sys:oa:document:submit', '提交公文审核', 'API',
       '/oa/document/outgoing/{id}/submit', 'POST', 1764, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1764 OR perm_code = 'sys:oa:document:submit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1765, 0, 'sys:oa:document:audit', '审核公文', 'API',
       '/oa/document/outgoing/{id}/audit', 'POST', 1765, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1765 OR perm_code = 'sys:oa:document:audit');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1766, 0, 'sys:oa:document:sign', '签发/下发公文', 'API',
       '/oa/document/outgoing/{id}/sign,/oa/document/outgoing/{id}/issue', 'POST', 1766, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1766 OR perm_code = 'sys:oa:document:sign');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1767, 0, 'sys:oa:document:incoming', '收文批阅/转发/办结', 'API',
       '/oa/document/incoming/{id}/review,/oa/document/incoming/{id}/forward,/oa/document/incoming/{id}/complete',
       'POST', 1767, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1767 OR perm_code = 'sys:oa:document:incoming');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1768, 0, 'sys:oa:document:template', '公文模板维护', 'API',
       '/oa/document/templates/**', 'ALL', 1768, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1768 OR perm_code = 'sys:oa:document:template');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1769, 0, 'sys:oa:document:archive', '公文归档', 'API',
       '/oa/document/outgoing/{id}/archive,/oa/document/incoming/{id}/archive,/oa/document/archive/refs/{id}/retry',
       'POST', 1769, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1769 OR perm_code = 'sys:oa:document:archive');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1770, 0, 'sys:oa:document:download', '下载公文附件', 'API',
       '/oa/document/attachments/{docType}/{docId}/{attachmentId}/download', 'GET', 1770, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1770 OR perm_code = 'sys:oa:document:download');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 1771, 0, 'sys:oa:document:no-rule', '文号规则维护', 'API',
       '/oa/document/no-rules', 'ALL', 1771, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 1771 OR perm_code = 'sys:oa:document:no-rule');

-- ---------------------------------------------------------------------
-- 2. 授权（按角色分段）
-- ---------------------------------------------------------------------
-- 2.1 全量：oa_admin、tenant_admin
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code IN ('oa_admin', 'tenant_admin') AND p.id BETWEEN 1760 AND 1771) ) ) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- 2.2 审批管理：oa_manager
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code = 'oa_manager' AND p.id IN (1760, 1761, 1762, 1764, 1765, 1766, 1767, 1769, 1770)) ) ) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- 2.3 普通员工：oa_member
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code = 'oa_member' AND p.id IN (1760, 1761, 1762, 1764, 1767, 1770)) ) ) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- ---------------------------------------------------------------------
-- 3. 菜单 391–394
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 391, 1, 0, '发文管理', '/oa/document/outgoing', 'document/outgoing/index', 'send', 391, 'MENU', 'sys:oa:document:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 391);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 392, 1, 0, '收文管理', '/oa/document/incoming', 'document/incoming/index', 'inbox', 392, 'MENU', 'sys:oa:document:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 392);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 393, 1, 0, '公文模板', '/oa/document/template', 'document/template/index', 'file-text', 393, 'MENU', 'sys:oa:document:template', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 393);
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 394, 1, 0, '公文归档', '/oa/document/archive', 'document/archive/index', 'archive', 394, 'MENU', 'sys:oa:document:archive', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 394);

-- =====================================================================
-- 完成。校验建议：
--   SELECT id, perm_code, path FROM sys_permission WHERE id BETWEEN 1760 AND 1771 ORDER BY id;
--   SELECT id, name, path, permission_code FROM sys_menu WHERE id IN (391, 392, 393, 394) ORDER BY id;
-- =====================================================================
