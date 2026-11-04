-- =====================================================================
-- BlueWhale 微服务平台 - OA 应用（whale-oa） Flyway 版本化迁移 V3
-- =====================================================================
-- 适用库   : bluewhale_oa
-- 上级计划 : Wave 5.82 M0-6（模板/流程定义种子）
-- 说明     : 1) 种子请假审批配置（business_type = LEAVE → OA_LEAVE）；
--            2) 种子请假审批人解析规则（部门经理 DEPT_LEADER、HR 角色 ROLE）。
-- 幂等     : INSERT ... WHERE NOT EXISTS 按业务唯一键守卫，可重复执行，不覆盖已维护配置。
-- 租户     : 只种默认租户，tenant_id = 1。
-- 规则     : 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 请假审批业务配置
-- ---------------------------------------------------------------------
INSERT INTO oa_approval_config
    (id, tenant_id, business_type, business_name, definition_code, title_template,
     business_url, cc_user_ids, enabled, remark, deleted)
SELECT 900000000000000301, 1, 'LEAVE', '请假', 'OA_LEAVE', '{applicant}的请假申请',
       '/oa/hr/leave', NULL, 1, 'M0-6 种子：请假审批配置', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_config
    WHERE tenant_id = 1 AND business_type = 'LEAVE'
);

-- ---------------------------------------------------------------------
-- 2. 请假审批人解析规则：部门经理审批（按发起人部门负责人解析）
-- ---------------------------------------------------------------------
INSERT INTO oa_approval_route_rule
    (id, tenant_id, business_type, node_key, node_name, resolver_type, resolver_value,
     sort_no, enabled, remark, deleted)
SELECT 900000000000000302, 1, 'LEAVE', 'dept_manager', '部门经理审批', 'DEPT_LEADER', NULL,
       10, 1, 'M0-6 种子：部门经理审批人', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_route_rule
    WHERE tenant_id = 1 AND business_type = 'LEAVE' AND node_key = 'dept_manager'
);

-- ---------------------------------------------------------------------
-- 3. 请假审批人解析规则：HR 审批（按 oa_hr_admin 角色解析）
-- ---------------------------------------------------------------------
INSERT INTO oa_approval_route_rule
    (id, tenant_id, business_type, node_key, node_name, resolver_type, resolver_value,
     sort_no, enabled, remark, deleted)
SELECT 900000000000000303, 1, 'LEAVE', 'hr', 'HR审批', 'ROLE', 'oa_hr_admin',
       20, 1, 'M0-6 种子：HR 审批人（oa_hr_admin 角色）', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_route_rule
    WHERE tenant_id = 1 AND business_type = 'LEAVE' AND node_key = 'hr'
);
