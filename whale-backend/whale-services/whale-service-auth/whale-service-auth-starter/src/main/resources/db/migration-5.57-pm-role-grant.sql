-- =====================================================================
-- Wave 5.55 遗留 P2-6：PM 权限点 172–202 补授真实业务角色（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.57-pm-role-grant.sql
-- 幂等说明 : 全部 NOT EXISTS 幂等插入，可重复执行；不修改/删除既有数据。
-- 角色口径 : 平台租户（tenant_id=1）下两个真实业务角色——
--   pm_manager 项目经理：172–202 全量 31 项（含项目/成员/状态/流转/迭代/看板管理与删除）；
--   pm_member  项目成员：16 项协作子集（查询 + 工作项新增/编辑/流转/排序 + 评论/附件/关联 + 发起审批），
--             不含任何 delete 与项目级管理权限。
-- 说明     : migration-5.55-pm.sql 仅建权限点不写授权（上线后手工勾选）；
--            本脚本把「平台租户业务角色」的授权固化为可重放迁移。
--            acme 租户的 tenant_admin 已于 Wave 5.55 联调时授予全量 31 项，不在本脚本范围。
-- =====================================================================

INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 17201, 1, 'pm_manager', '项目经理', 'PM 全量权限：项目/成员/状态/流转/迭代/看板管理与工作项全操作', 172, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 17201 OR (tenant_id = 1 AND role_code = 'pm_manager'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 17202, 1, 'pm_member', '项目成员', 'PM 协作子集：查询 + 工作项协作 + 评论/附件/关联 + 发起审批', 173, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 17202 OR (tenant_id = 1 AND role_code = 'pm_member'));

-- pm_manager：174–202 + 续段 2320–2321 全量（id 采用 17201000+权限ID 的确定性区间，幂等可重放）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT 17201000 + id AS id, 17201 AS role_id, id AS permission_id FROM sys_permission WHERE (id BETWEEN 174 AND 202 OR id IN (2320, 2321))) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- pm_member：协作子集 16 项（id 区间 17202000+权限ID）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT 17202000 + id AS id, 17202 AS role_id, id AS permission_id FROM sys_permission WHERE id IN (2320, 176, 180, 184, 187, 188, 189, 191, 192, 193, 195, 196, 197, 199, 201, 202)) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- 配套授权：sys:pm:item:approval(202) 的履约依赖 whale-workflow 的发起权限
-- sys:workflow:instance:start(44)，两个 PM 角色均需（否则发起被 workflow 侧 403）。
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 17201044, 17201, 44
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 17201044 OR (role_id = 17201 AND permission_id = 44));
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 17202044, 17202, 44
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 17202044 OR (role_id = 17202 AND permission_id = 44));

-- =====================================================================
-- 完成。校验建议：
--   SELECT r.role_code, COUNT(rp.permission_id) FROM sys_role r
--   JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.id IN (17201, 17202) GROUP BY r.role_code;
--   -- 期望：pm_manager=31，pm_member=16
-- =====================================================================
