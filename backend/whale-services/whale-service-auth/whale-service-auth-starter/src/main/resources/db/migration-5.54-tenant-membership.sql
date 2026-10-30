-- =====================================================================
-- Wave 5.54 Phase 5.1：一人多租户关系表迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 前置条件 : 已执行 migration-5.54-tenant.sql 与 migration-5.54-tenant-rbac.sql
--            （sys_tenant、sys_user.tenant_id、sys_role.tenant_id 已就绪）
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-5.54-tenant-membership.sql
-- 幂等说明 : CREATE TABLE IF NOT EXISTS + 回填 NOT EXISTS，可重复执行，不删除任何业务数据。
-- 变更内容 :
--   1. 新增 sys_user_tenant：记录「同一 userId 可进入哪些租户」；
--   2. 回填首页关系：每个 sys_user 必有一行 (user_id=self, tenant_id=self.tenant)；
--   3. Phase 5.3：新增租户成员权限点 165–167 与「租户成员」菜单（NOT EXISTS 幂等插入）。
-- 授权说明 : 遵循既有约定，本脚本不写 sys_role_permission；新开通租户由
--            TenantProvisioningAppService 自动授予 165/166，存量租户由角色分配界面补授。
-- 说明     : 该表为跨租户关系表，不参与 MyBatis 行级租户过滤（见 whale.tenant.sql.ignore-tables），
--            所有查询必须显式带 user_id 条件。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 关系表（幂等建表）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_tenant (
    id          BIGINT   NOT NULL            COMMENT '主键ID（雪花算法）',
    user_id     BIGINT   NOT NULL            COMMENT '全局用户ID（逻辑关联 sys_user.id，切换前后不变）',
    tenant_id   BIGINT   NOT NULL            COMMENT '可进入的租户ID（逻辑关联 sys_tenant.id）',
    status      TINYINT  NOT NULL DEFAULT 1  COMMENT '状态：1 启用 / 0 停用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_tenant (user_id, tenant_id),
    KEY idx_tenant_id (tenant_id)
) COMMENT='用户可进入的租户关系表（一人多租户）';

-- ---------------------------------------------------------------------
-- 2. 回填首页关系（仅对尚无任何关系的用户执行）
--    主键复用 sys_user.id：关系表为空或仅含已有用户的首页行，
--    未回填用户的 id 不会与已插入行冲突。
-- ---------------------------------------------------------------------
INSERT INTO sys_user_tenant (id, user_id, tenant_id, status)
SELECT s.id, s.id, s.tenant_id, 1
FROM sys_user s
WHERE NOT EXISTS (
    SELECT 1 FROM sys_user_tenant m WHERE m.user_id = s.id
);

-- ---------------------------------------------------------------------
-- 3. Phase 5.3：租户成员权限点（165–167）与菜单
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 165, 0, 'sys:tenant:member:list', '查询租户成员', 'API', '/tenant/members', 'GET', 165, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 165 OR perm_code = 'sys:tenant:member:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 166, 0, 'sys:tenant:member:assign', '分配租户成员角色', 'API', '/tenant/members/{userId}/roles', 'PUT', 166, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 166 OR perm_code = 'sys:tenant:member:assign');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 167, 0, 'sys:user:tenant', '绑定用户租户', 'API', '/user/{id}/tenants,/user/{id}/tenants/{tenantId}', 'POST,DELETE', 167, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 167 OR perm_code = 'sys:user:tenant');

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 17, 1, 1, '租户成员', '/system/tenant-member', 'tenant/member/index', 'team', 8, 'MENU', 'sys:tenant:member:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 17);

-- =====================================================================
-- 完成。校验建议：
--   SHOW CREATE TABLE sys_user_tenant;
--   SELECT id, perm_code FROM sys_permission WHERE id BETWEEN 165 AND 167;
--   SELECT id, tenant_id, path FROM sys_menu WHERE id = 17;
--   -- 缺失首页关系数，期望 0
--   SELECT COUNT(*) FROM sys_user s
--     LEFT JOIN sys_user_tenant m ON m.user_id = s.id WHERE m.id IS NULL;
--   -- 每人关系数，期望 >= 1
--   SELECT user_id, COUNT(*) FROM sys_user_tenant GROUP BY user_id;
-- =====================================================================
