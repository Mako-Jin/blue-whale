-- =============================================================================
-- V37：超管并入三员互斥（收口「以超管身份绕过职责分离」的旁路）
-- =============================================================================
-- 背景：super_admin 是 PermissionInterceptor 的代码级硬旁路（0 授权也可用），
--       所以「同一用户既是超管又是某个三员」这条路径无法靠权限点拦截：
--       审计员若同时持超管，审计职责分离形同虚设。
-- 做法：把 super_admin 角色 id 追加进各租户的 three_admin_mutex 的 role_ids。
--       RoleConstraintCheckerImpl 判定 MUTEX 为「目标集合命中同一互斥集 ≥2 即拒绝」，
--       因此「超管 + 任一三员」与「三员相互兼任」在同一张约束里统一被拦。
-- 判重键：约束 (tenant_id, constraint_code)；追加前用 JSON_CONTAINS 判存，
--       执行任意次结果一致（幂等）。
-- 边界：不给三员角色授予超管权限、也不改 super_admin 的任何能力——只新增一条互斥关系。
--       交付后仍需停用超管账号（见 docs/security 对照表运维口径）。
--
-- 校验（执行后应满足）：
--   SELECT tenant_id, role_ids FROM sys_role_constraint
--   WHERE constraint_code='three_admin_mutex' AND deleted=0;
--   -- 平台租户期望形如 [26004,26005,26006,1]（业务租户无 super_admin，保持三元素）
-- =============================================================================

UPDATE sys_role_constraint c
    JOIN sys_role r
        ON r.tenant_id = c.tenant_id
       AND r.deleted = 0
       AND r.role_code = 'super_admin'
SET c.role_ids = JSON_ARRAY_APPEND(c.role_ids, '$', r.id)
WHERE c.constraint_code = 'three_admin_mutex'
  AND c.deleted = 0
  AND NOT JSON_CONTAINS(c.role_ids, CAST(r.id AS JSON));
