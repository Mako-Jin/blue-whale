-- =====================================================================
-- Wave 5.65：业务权限补授真实角色（ERP / MES / PDM / OA / 合同 / 档案，幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.65-business-role-grant.sql
-- 幂等说明 : 角色与授权均为 NOT EXISTS 幂等插入，可重复执行；不修改/删除既有数据。
-- 背景     : migration-5.46/5.53/5.58/5.59/5.60/5.61/5.62/5.63 只播种权限点，
--            遵循「不写 sys_role_permission」的约定，导致除 super_admin 外没有真实
--            角色持有这些权限，业务用户登录后菜单可见但接口 403。
-- 角色口径 : 平台租户（tenant_id=1）新增 13 个业务角色作为模板；manager 含模块全量
--            （含删除），member/engineer 为不含删除的协作子集，contract_signatory
--            仅签署与证据查看。既有业务租户（tenant_type=TENANT）按模板复制同名角色，
--            既有租户的 tenant_admin 回填 140–151、168–171、203–232 全量业务权限。
-- 编号约定 : 平台角色 ID 采用「模块权限段 + 序号」；租户复制角色与全部授权的 ID
--            采用「业务键 MD5 前 15 位」（< 2^60），与 PM（17201/17202）及雪花主键
--            不冲突，且重复执行 ID 稳定。
-- 租户开通 : TenantProvisioningAppService 以本脚本在平台租户维护的 13 个角色为模板，
--            新租户开通时自动下发同名角色并复制模板当前权限集；模板缺失仅告警跳过。
-- 依赖     : 先执行 5.46、5.53、5.54、5.58–5.64，确保权限点 140–232 与
--            sys_tenant.tenant_type 已存在。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 业务角色模板（平台租户 tenant_id = 1）
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 14001, 1, 'archive_manager', '档案管理员', '档案全量：门类/规则/条目整理/归档确认/销毁/同步/秘密级检索', 140, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 14001 OR (tenant_id = 1 AND role_code = 'archive_manager'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 14002, 1, 'archive_member', '档案查阅员', '档案查阅子集：门类树、条目查询与详情、文件下载', 141, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 14002 OR (tenant_id = 1 AND role_code = 'archive_member'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 16801, 1, 'oa_manager', 'OA 主管', 'OA 请假全量：查询/新增/审批/删除', 168, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 16801 OR (tenant_id = 1 AND role_code = 'oa_manager'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 16802, 1, 'oa_member', 'OA 员工', 'OA 请假协作子集：查询与新增（不含审批/删除）', 169, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 16802 OR (tenant_id = 1 AND role_code = 'oa_member'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 20301, 1, 'erp_manager', 'ERP 管理员', 'ERP 物料全量：查询/新增/编辑/删除', 203, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 20301 OR (tenant_id = 1 AND role_code = 'erp_manager'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 20302, 1, 'erp_member', 'ERP 物料员', 'ERP 物料协作子集：查询/新增/编辑（不含删除）', 204, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 20302 OR (tenant_id = 1 AND role_code = 'erp_member'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 20701, 1, 'mes_manager', 'MES 管理员', 'MES 工单全量：查询/新增/编辑/删除', 207, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 20701 OR (tenant_id = 1 AND role_code = 'mes_manager'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 20702, 1, 'mes_member', 'MES 计划员', 'MES 工单协作子集：查询/新增/编辑（不含删除）', 208, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 20702 OR (tenant_id = 1 AND role_code = 'mes_member'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 21101, 1, 'pdm_manager', 'PDM 管理员', 'PDM 产品全量：查询/新增/编辑/删除', 211, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 21101 OR (tenant_id = 1 AND role_code = 'pdm_manager'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 21102, 1, 'pdm_member', 'PDM 工程师', 'PDM 产品协作子集：查询/新增/编辑（不含删除）', 212, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 21102 OR (tenant_id = 1 AND role_code = 'pdm_member'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 21501, 1, 'contract_manager', '合同管理员', '合同全量：拟稿/审批/签署/印章/用章授权/证据/作废/生效/归档', 215, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 21501 OR (tenant_id = 1 AND role_code = 'contract_manager'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 21502, 1, 'contract_member', '合同专员', '合同协作子集：拟稿/提交/撤回/终止/生效/归档（不含删除、印章、签署发起与作废）', 216, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 21502 OR (tenant_id = 1 AND role_code = 'contract_member'));
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT 21503, 1, 'contract_signatory', '合同签署人', '签署子集：查看合同、执行签署/拒签、查看证据与验签', 217, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 21503 OR (tenant_id = 1 AND role_code = 'contract_signatory'));

-- ---------------------------------------------------------------------
-- 2. 既有业务租户复制角色
--    角色管理接口按 TenantContext 过滤 tenant_id（RoleRepositoryImpl#page /
--    findById / listRoleIdsByUserId），平台租户的角色模板在业务租户不可见、
--    也不能用于授权计算，因此必须为每个既有业务租户复制一份同名角色。
--    · 仅复制 tenant_type = 'TENANT' 的未删除启用租户（排除平台租户）
--    · 已存在同名角色（uk_tenant_role_code）时 NOT EXISTS 幂等插入 跳过，不覆盖人工调整
-- ---------------------------------------------------------------------
INSERT INTO sys_role (id, tenant_id, role_code, role_name, description, sort_no, status)
SELECT src.id, src.tenant_id, src.role_code, src.role_name, src.description, src.sort_no, src.status
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':', x.role_code)), 1, 15), 16, 10) AS UNSIGNED) AS id, t.id AS tenant_id, x.role_code AS role_code, x.role_name AS role_name, x.description AS description, x.sort_no AS sort_no, 1 AS status FROM sys_tenant t CROSS JOIN ( SELECT 'archive_manager' AS role_code, '档案管理员' AS role_name, '档案全量：门类/规则/条目整理/归档确认/销毁/同步/秘密级检索' AS description, 140 AS sort_no UNION ALL SELECT 'archive_member', '档案查阅员', '档案查阅子集：门类树、条目查询与详情、文件下载', 141 UNION ALL SELECT 'oa_manager', 'OA 主管', 'OA 请假全量：查询/新增/审批/删除', 168 UNION ALL SELECT 'oa_member', 'OA 员工', 'OA 请假协作子集：查询与新增（不含审批/删除）', 169 UNION ALL SELECT 'erp_manager', 'ERP 管理员', 'ERP 物料全量：查询/新增/编辑/删除', 203 UNION ALL SELECT 'erp_member', 'ERP 物料员', 'ERP 物料协作子集：查询/新增/编辑（不含删除）', 204 UNION ALL SELECT 'mes_manager', 'MES 管理员', 'MES 工单全量：查询/新增/编辑/删除', 207 UNION ALL SELECT 'mes_member', 'MES 计划员', 'MES 工单协作子集：查询/新增/编辑（不含删除）', 208 UNION ALL SELECT 'pdm_manager', 'PDM 管理员', 'PDM 产品全量：查询/新增/编辑/删除', 211 UNION ALL SELECT 'pdm_member', 'PDM 工程师', 'PDM 产品协作子集：查询/新增/编辑（不含删除）', 212 UNION ALL SELECT 'contract_manager', '合同管理员', '合同全量：拟稿/审批/签署/印章/用章授权/证据/作废/生效/归档', 215 UNION ALL SELECT 'contract_member', '合同专员', '合同协作子集：拟稿/提交/撤回/终止/生效/归档（不含删除、印章、签署发起与作废）', 216 UNION ALL SELECT 'contract_signatory', '合同签署人', '签署子集：查看合同、执行签署/拒签、查看证据与验签', 217 ) x WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role tgt WHERE tgt.id = src.id OR (tgt.tenant_id = src.tenant_id AND tgt.role_code = src.role_code));

-- ---------------------------------------------------------------------
-- 3. 授权（按角色编码匹配，覆盖平台与既有业务租户；全部租户内同名角色）
--    授权 ID = MD5(role_id + ':' + permission_id) 前 15 位，稳定且幂等；
--    uk_role_permission(role_id, permission_id) 兜底重复插入。
--    权限口径：
--      archive_manager  140–151（12）
--      archive_member   140 / 144 / 145 / 148（4）
--      oa_manager       168–171（4）
--      oa_member        168 / 169（2）
--      erp_manager      203–206（4）
--      erp_member       203 / 204 / 205（3）
--      mes_manager      207–210（4）
--      mes_member       207 / 208 / 209（3）
--      pdm_manager      211–214（4）
--      pdm_member       211 / 212 / 213（3）
--      contract_manager 215–232（18）+ workflow 44（1）
--      contract_member  215/216/217/219/220/221/222/232（8）+ workflow 44（1）
--      contract_signatory 215 / 229 / 230（3）
--      tenant_admin     140–151、168–171、203–232（46）+ workflow 44（1）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id, r.id AS role_id, p.id AS permission_id FROM sys_role r JOIN sys_permission p ON p.deleted = 0 AND p.status = 1 WHERE r.deleted = 0 AND r.status = 1 AND ( (r.role_code = 'archive_manager' AND p.id BETWEEN 140 AND 151) OR (r.role_code = 'archive_member' AND p.id IN (140, 144, 145, 148)) OR (r.role_code = 'oa_manager' AND p.id BETWEEN 168 AND 171) OR (r.role_code = 'oa_member' AND p.id IN (168, 169)) OR (r.role_code = 'erp_manager' AND p.id BETWEEN 203 AND 206) OR (r.role_code = 'erp_member' AND p.id IN (203, 204, 205)) OR (r.role_code = 'mes_manager' AND p.id BETWEEN 207 AND 210) OR (r.role_code = 'mes_member' AND p.id IN (207, 208, 209)) OR (r.role_code = 'pdm_manager' AND p.id BETWEEN 211 AND 214) OR (r.role_code = 'pdm_member' AND p.id IN (211, 212, 213)) OR (r.role_code = 'contract_manager' AND (p.id BETWEEN 215 AND 232 OR p.id = 44)) OR (r.role_code = 'contract_member' AND (p.id IN (215, 216, 217, 219, 220, 221, 222, 232) OR p.id = 44)) OR (r.role_code = 'contract_signatory' AND p.id IN (215, 229, 230)) OR (r.role_code = 'tenant_admin' AND (p.id BETWEEN 140 AND 151 OR p.id BETWEEN 168 AND 171 OR p.id BETWEEN 203 AND 232 OR p.id = 44)) )) src
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission tgt WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id));

-- =====================================================================
-- 完成。校验建议：
--   -- 1) 平台租户模板角色授权数量
--   SELECT r.role_code, COUNT(rp.permission_id) AS grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.tenant_id = 1 AND r.id IN (14001,14002,16801,16802,20301,20302,
--                                     20701,20702,21101,21102,21501,21502,21503)
--   GROUP BY r.role_code ORDER BY r.role_code;
--   -- 期望：archive_manager=12, archive_member=4, oa_manager=4, oa_member=2,
--   --       erp_manager=4, erp_member=3, mes_manager=4, mes_member=3,
--   --       pdm_manager=4, pdm_member=3, contract_manager=19（18+44）,
--   --       contract_member=9（8+44）, contract_signatory=3
--
--   -- 2) 每个业务租户都应有 13 个业务角色
--   SELECT t.tenant_code, COUNT(r.id) AS role_count
--   FROM sys_tenant t
--   LEFT JOIN sys_role r ON r.tenant_id = t.id AND r.deleted = 0
--       AND r.role_code IN ('archive_manager','archive_member','oa_manager','oa_member',
--                           'erp_manager','erp_member','mes_manager','mes_member',
--                           'pdm_manager','pdm_member','contract_manager',
--                           'contract_member','contract_signatory')
--   WHERE t.deleted = 0 AND t.status = 1 AND (t.id = 1 OR t.tenant_type = 'TENANT')
--   GROUP BY t.tenant_code;
--   -- 期望：每行 role_count = 13
--
--   -- 3) 既有租户管理员覆盖（每个 tenant_admin 应 = 46 项业务权限，另加 workflow 44）
--   SELECT r.tenant_id, r.role_code, COUNT(DISTINCT rp.permission_id) AS business_grants
--   FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
--   WHERE r.role_code = 'tenant_admin' AND rp.permission_id BETWEEN 140 AND 232
--   GROUP BY r.tenant_id, r.role_code;
--
--   -- 4) 未被任何启用角色持有的业务权限（期望空集）
--   SELECT p.id, p.perm_code
--   FROM sys_permission p
--   LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id AND rp.deleted = 0
--   LEFT JOIN sys_role r ON r.id = rp.role_id AND r.deleted = 0 AND r.status = 1
--   WHERE p.deleted = 0 AND p.status = 1
--     AND (p.id BETWEEN 140 AND 151 OR p.id BETWEEN 168 AND 171 OR p.id BETWEEN 203 AND 232)
--     AND r.id IS NULL
--   ORDER BY p.id;
-- =====================================================================
