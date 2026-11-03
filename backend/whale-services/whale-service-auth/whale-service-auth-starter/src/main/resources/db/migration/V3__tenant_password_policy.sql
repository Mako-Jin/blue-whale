-- =====================================================================
-- V3（REQ-SEC-023 / REQ-SEC-024）：租户口令策略配置
-- =====================================================================
-- 适用范围 : 存量库与空库（空库由 V1 建表/种子，本脚本守卫后为 no-op）
-- 背景     : 口令有效期由租户管理员按租户配置，未配置回退全局默认
--            （whale.auth.password.max-age-days / warn-days）。
-- 权限编号 : 租户管理预留段 160–179，172–173 用于本功能。
-- 菜单编号 : 36（平台模板），并复制到已有业务租户。
-- 幂等说明 : 建表 IF NOT EXISTS；权限/授权/菜单均 NOT EXISTS 幂等插入；不修改、不删除既有数据。
-- 安全约束 : 不含 DROP / TRUNCATE / DELETE。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 租户口令策略表（每租户至多一条）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_tenant_password_policy (
    id           BIGINT   NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT   NOT NULL                COMMENT '租户ID（逻辑关联 sys_tenant.id）',
    max_age_days INT      NOT NULL                COMMENT '口令有效期（天），范围 1–365',
    warn_days    INT      NOT NULL DEFAULT 7      COMMENT '到期前提醒天数（0–有效期）',
    deleted      TINYINT  NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_pwd_policy (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户口令策略配置表';

-- ---------------------------------------------------------------------
-- 2. 权限点：查询 / 配置本租户口令策略（下发租户管理员，不属于平台专属码）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 172, 0, 'sys:tenant:pwdpolicy:list', '查询租户口令策略', 'API', '/tenant/password-policy', 'GET', 172, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 172 OR perm_code = 'sys:tenant:pwdpolicy:list');
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 173, 0, 'sys:tenant:pwdpolicy:edit', '配置租户口令策略', 'API', '/tenant/password-policy', 'PUT', 173, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 173 OR perm_code = 'sys:tenant:pwdpolicy:edit');

-- ---------------------------------------------------------------------
-- 3. 授权：已有租户的 tenant_admin 角色补齐 172–173
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.id IN (172, 173) AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin')
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);

-- ---------------------------------------------------------------------
-- 4. 菜单模板（平台租户）+ 复制到已有业务租户
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT 36, 1, 1, '口令策略', '/system/password-policy', 'system/password-policy/index', 'lock', 9, 'MENU', 'sys:tenant:pwdpolicy:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 36);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':menu:36')), 1, 15), 16, 10) AS UNSIGNED),
       t.id, COALESCE(pm.id, 0), m.name, m.path, m.component, m.icon, m.sort, m.menu_type,
       m.permission_code, m.visible, m.status
FROM sys_tenant t
JOIN sys_menu m ON m.tenant_id = 1 AND m.id = 36
LEFT JOIN sys_menu pm ON pm.tenant_id = t.id AND pm.parent_id = 0
     AND pm.path = '/system' AND pm.menu_type = 'DIR' AND pm.deleted = 0
WHERE t.id <> 1 AND t.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.tenant_id = t.id AND x.path = m.path);
