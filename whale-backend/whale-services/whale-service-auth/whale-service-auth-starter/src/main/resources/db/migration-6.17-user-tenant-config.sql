-- =====================================================================
-- Wave 6.17：用户/租户通用配置（bluewhale_auth）手工迁移脚本（幂等）
-- =====================================================================
-- 内容     : sys_user_config / sys_tenant_config 两张通用配置表 +
--            租户配置管理权限码种子（2322/2323）+ tenant_admin 角色授予
-- 幂等性   : 全部 IF NOT EXISTS / WHERE NOT EXISTS，可安全重放；
--            不含 DROP / TRUNCATE / DELETE
-- 口径     : 权限中心是身份与配置的唯一权威；auth 只存取不解释业务语义，
--            config_key 前缀分域（notify.* / ui.* / ...），消费方自行解释
-- 读取优先 : 用户配置 > 租户配置 > 消费方内置默认（由读取端点合并实现）
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_user_config (
    id           BIGINT      NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT      NOT NULL               COMMENT '租户ID（逻辑关联 sys_tenant.id）',
    user_id      BIGINT      NOT NULL               COMMENT '用户ID（逻辑关联 sys_user.id）',
    config_key   VARCHAR(128) NOT NULL              COMMENT '配置键（前缀分域，如 notify.twin.alert / ui.theme）',
    config_value TEXT        NOT NULL               COMMENT '配置值（JSON，结构由 config_key 所属业务域定义）',
    updated_by   BIGINT      DEFAULT NULL           COMMENT '最后更新人用户ID',
    create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_config (tenant_id, user_id, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户配置表（权限中心通用配置存储）';

CREATE TABLE IF NOT EXISTS sys_tenant_config (
    id           BIGINT      NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT      NOT NULL               COMMENT '租户ID（逻辑关联 sys_tenant.id）',
    config_key   VARCHAR(128) NOT NULL              COMMENT '配置键（前缀分域，与 sys_user_config 同一口径）',
    config_value TEXT        NOT NULL               COMMENT '配置值（JSON），作为该租户用户的默认值层',
    updated_by   BIGINT      DEFAULT NULL           COMMENT '最后更新人用户ID',
    create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_config (tenant_id, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='租户配置表（租户管理员维护的默认值层）';

-- ---------------------------------------------------------------------
-- 权限码种子：租户配置管理（查询 / 编辑）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2322, 0, 'sys:tenant:config:list', '查询租户配置', 'API', '/tenant/config', 'GET', 2322, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2322 OR perm_code = 'sys:tenant:config:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2323, 0, 'sys:tenant:config:edit', '编辑租户配置', 'API', '/tenant/config', 'PUT', 2323, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 2323 OR perm_code = 'sys:tenant:config:edit');

-- 授予 tenant_admin（按角色码联查，幂等）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.id IN (2322, 2323) AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code IN ('tenant_admin')
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);

-- =====================================================================
-- 完成。校验建议：
--   SHOW TABLES LIKE 'sys_%config';
--   SELECT id, perm_code FROM sys_permission WHERE id IN (2322, 2323);
--   SELECT role_id, permission_id FROM sys_role_permission WHERE permission_id IN (2322, 2323);
-- =====================================================================
