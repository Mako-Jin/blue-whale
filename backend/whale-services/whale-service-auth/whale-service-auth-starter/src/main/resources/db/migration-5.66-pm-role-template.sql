-- =====================================================================
-- Wave 5.66：PM 业务角色模板化（平台租户 → 全部业务租户，幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.66-pm-role-template.sql
-- 幂等说明 : 角色与授权均为 NOT EXISTS 幂等插入，可重复执行；不修改/删除既有数据。
-- 背景     : migration-5.57-pm-role-grant.sql 只在平台租户（tenant_id=1）创建
--            pm_manager / pm_member，TenantProvisioningAppService 的模板清单也未包含
--            PM，导致业务租户既没有 PM 角色可分配，开通后也无法自行创建同等模板。
-- 角色口径 : PM 属于业务能力，不由平台专属；平台租户保留模板，全部既有业务租户
--            按模板复制同名角色，新租户由开通编排按模板下发。
--   pm_manager 项目经理：172–202 全量 31 项 + workflow 44；
--   pm_member  项目成员：16 项协作子集 + workflow 44。
-- 顺带修补 : 存量 tenant_admin 补齐 165/166（本租户成员查询/分配）与 PM 权限；
--            167（sys:user:tenant）为平台专属，明确不下发。
-- 编号约定 : 平台角色沿用 17201/17202；租户复制角色与授权 ID 采用「业务键 MD5 前 15 位」，
--            与 5.65 一致，重复执行 ID 稳定。
-- 依赖     : 先执行 5.55-pm.sql（权限点 172–202）与 5.65（业务角色模板）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 平台租户 PM 角色模板（5.57 已执行时忽略）
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 17201, 1, 'pm_manager', '项目经理', 'PM 全量权限：项目/成员/状态/流转/迭代/看板管理与工作项全操作', 172, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 17201 OR (tenant_id = 1 AND role_code = 'pm_manager'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 17202, 1, 'pm_member', '项目成员', 'PM 协作子集：查询 + 工作项协作 + 评论/附件/关联 + 发起审批', 173, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 17202 OR (tenant_id = 1 AND role_code = 'pm_member'));

-- ---------------------------------------------------------------------
-- 2. 既有业务租户复制 PM 角色
--    仅复制 tenant_type='TENANT' 的未删除启用租户（排除平台租户）；
--    已存在同名角色（uk_tenant_role_code）时跳过，不覆盖人工调整。
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':', x.role_code)), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, x.role_code AS role_code, x.role_name AS role_name, x.description AS description, x.sort_no AS sort_no, 1 AS status FROM sys_tenant t CROSS JOIN ( SELECT 'pm_manager' AS role_code, '项目经理' AS role_name, 'PM 全量权限：项目/成员/状态/流转/迭代/看板管理与工作项全操作' AS description, 172 AS sort_no UNION ALL SELECT 'pm_member', '项目成员', 'PM 协作子集：查询 + 工作项协作 + 评论/附件/关联 + 发起审批', 173 ) x WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));

-- ---------------------------------------------------------------------
-- 3. 授权（按角色编码匹配，覆盖平台与全部业务租户）
--    授权 ID = MD5(role_id + ':' + permission_id) 前 15 位，稳定且幂等；
--    uk_role_permission(role_id, permission_id) 兜底重复插入。
--    权限口径：
--      pm_manager   172–202（31）+ 44（1）
--      pm_member    172/176/180/184/187/188/189/191/192/193/195/196/197/199/201/202（16）+ 44（1）
--      tenant_admin 172–202（31）+ 165/166（2）+ 44（1）；不含平台专属 167
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code = 'pm_manager' AND (p.id BETWEEN 172 AND 202 OR p.id = 44)) OR (r.role_code = 'pm_member' AND (p.id IN (172, 176, 180, 184, 187, 188, 189, 191, 192, 193, 195, 196, 197, 199, 201, 202) OR p.id = 44)) OR (r.role_code = 'tenant_admin' AND (p.id BETWEEN 172 AND 202 OR p.id IN (165, 166) OR p.id = 44)) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 完成。校验建议：
--   -- 1) 每个目标租户都应有 15 个业务角色（13 个 5.65 模板 + 2 个 PM）
--   SELECT t.tenant_code, COUNT(r.id) AS role_count
--   FROM sys_tenant t
--   LEFT JOIN sys_role r ON r.tenant_id = t.id AND r.deleted = 0
--       AND r.role_code IN ('archive_manager','archive_member','oa_manager','oa_member',
--                           'erp_manager','erp_member','mes_manager','mes_member',
--                           'pdm_manager','pdm_member','pm_manager','pm_member',
--                           'contract_manager','contract_member','contract_signatory')
--   WHERE t.deleted = 0 AND t.status = 1 AND (t.id = 1 OR t.tenant_type = 'TENANT')
--   GROUP BY t.tenant_code;
--   -- 期望：每行 role_count = 15
--
--   -- 2) PM 角色授权数量
--   SELECT t.tenant_code, r.role_code, COUNT(rp.permission_id)
--   FROM sys_tenant t
--   JOIN sys_role r ON r.tenant_id = t.id AND r.deleted = 0
--   LEFT JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.deleted = 0
--   WHERE r.role_code IN ('pm_manager','pm_member')
--     AND t.deleted = 0 AND t.status = 1
--   GROUP BY t.tenant_code, r.role_code;
--   -- 期望：pm_manager=32（31+44），pm_member=17（16+44）
--
--   -- 3) 存量 tenant_admin 补齐成员管理与 PM 权限
--   SELECT r.tenant_id, COUNT(DISTINCT rp.permission_id)
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.role_code = 'tenant_admin' AND r.deleted = 0
--     AND (rp.permission_id BETWEEN 172 AND 202 OR rp.permission_id IN (165, 166, 44))
--   GROUP BY r.tenant_id;
--   -- 期望：每个 tenant_admin ≥ 34（31 PM + 165/166 + workflow 44）
-- =====================================================================
