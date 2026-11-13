-- =============================================================================
-- 文件        : V21__permission_policy.sql
-- 说明        : 权限中心扩展 M3 —— 权限策略 / 角色约束 / 角色继承扩展
--
-- 背景：按 auth_tbl_permission_strategy + auth_tbl_role_constraint 处置矩阵
--       （计划 §1.2 M3 行）新建 sys_permission_strategy + sys_role_constraint，
--       并为 sys_role 补继承四列、sys_user_role 补 subject_type（成员类型）。
--
-- 聚合不变量（应用层聚合根强制）：
--   ① strategy_code 租户内唯一（DB 唯一键兜底）；
--   ② role_ids / resource_ids 引用的角色、资源必须存在；
--   ③ 同租户仅一条 is_default=1；
--   ④ priority 数字小者优先；
--   ⑤ 角色继承不得成环；role_level 只能由父角色 +1；
--   ⑥ MUTEX 约束需 ≥2 个角色且两两互斥；CARDINALITY.max_assign > 0；
--   ⑦ 分配角色时必须校验全部启用约束。
--
-- 本脚本（幂等，可安全重放）：
--   1. 建表 sys_permission_strategy + sys_role_constraint；
--   2. sys_role 补列 parent_id / role_level / inherit_strategy / category；
--   3. sys_user_role 补列 subject_type；
--   4. 权限点 sys:perm:strategy:*（2400–2404）+ sys:role:constraint:*（2405–2408）；
--   5. 授予 tenant_admin（按 perm_code 联查）；
--   6. 「权限策略」「角色约束」菜单（tenant_id=1 + 复制到业务租户）。
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1. 权限策略表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_permission_strategy (
    id              BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT       NOT NULL                COMMENT '租户ID',
    strategy_code   VARCHAR(64)  NOT NULL                COMMENT '策略编码（租户内唯一）',
    strategy_name   VARCHAR(128) NOT NULL                COMMENT '策略名称',
    strategy_type   VARCHAR(16)  NOT NULL DEFAULT 'GRANT' COMMENT '策略类型：GRANT 授权 / DENY 拒绝',
    role_ids        TEXT         NOT NULL                COMMENT '生效角色ID集合（JSON 数组：[1,2]，必须存在）',
    resource_ids    TEXT         NOT NULL                COMMENT '生效资源ID集合（JSON 数组：[10,20]，对应 sys_menu/sys_permission）',
    condition_json  TEXT         DEFAULT NULL            COMMENT '附加条件（JSON 数组：[{"field":"attr","op":"eq","value":"x"}]，为空表示无条件）',
    is_default      TINYINT      NOT NULL DEFAULT 0      COMMENT '是否默认策略：1 是（同租户仅一条，聚合根保证）',
    priority        INT          NOT NULL DEFAULT 100    COMMENT '优先级：数字小者优先',
    status          TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用',
    description     VARCHAR(512) DEFAULT NULL            COMMENT '描述',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_strategy_code (tenant_id, strategy_code),
    KEY idx_strategy_tenant (tenant_id, status, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='权限策略表（角色-资源的条件化授权/拒绝）';

-- ---------------------------------------------------------------------
-- 1.1 角色约束表（互斥 / 基数）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_role_constraint (
    id              BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT       NOT NULL                COMMENT '租户ID',
    constraint_code VARCHAR(64)  NOT NULL                COMMENT '约束编码（租户内唯一）',
    constraint_name VARCHAR(128) NOT NULL                COMMENT '约束名称',
    constraint_type VARCHAR(16)  NOT NULL                COMMENT '约束类型：MUTEX 互斥（≥2 角色两两互斥）/ CARDINALITY 基数（角色组合数量上限）',
    role_ids        TEXT         NOT NULL                COMMENT '约束覆盖的角色ID集合（JSON 数组）',
    max_assign      INT          DEFAULT NULL            COMMENT '最多可同时分配的角色数（仅 CARDINALITY，必须 > 0）',
    status          TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用',
    description     VARCHAR(512) DEFAULT NULL            COMMENT '描述',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_constraint_code (tenant_id, constraint_code),
    KEY idx_constraint_tenant (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色约束表（互斥/基数，分配角色时强制校验）';

-- ---------------------------------------------------------------------
-- 2. sys_role 补列：继承与分类（auth_tbl_role 处置矩阵 M3 行）
--    inherit_strategy ∈ {FULL 全量继承 / INCREMENT 增量继承 / OVERRIDE 覆盖继承 / NONE 不继承}
-- ---------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'parent_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_role ADD COLUMN parent_id BIGINT NOT NULL DEFAULT 0 COMMENT ''父角色ID（0 表示顶级，继承链不得成环）'' AFTER description',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'role_level');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_role ADD COLUMN role_level INT NOT NULL DEFAULT 1 COMMENT ''角色层级（顶级为 1，子角色只能父 +1）'' AFTER parent_id',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'inherit_strategy');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_role ADD COLUMN inherit_strategy VARCHAR(16) NOT NULL DEFAULT ''NONE'' COMMENT ''继承策略：FULL 全量 / INCREMENT 增量 / OVERRIDE 覆盖 / NONE 不继承'' AFTER role_level',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'category');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_role ADD COLUMN category VARCHAR(32) NOT NULL DEFAULT ''GENERAL'' COMMENT ''角色分类：GENERAL 通用 / BUSINESS 业务 / SYSTEM 系统'' AFTER inherit_strategy',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2.1 sys_user_role 补列：subject_type（用户/组/组织，auth_tbl_rel_role_user M3 行）
-- ---------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role' AND COLUMN_NAME = 'subject_type');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_user_role ADD COLUMN subject_type VARCHAR(16) NOT NULL DEFAULT ''USER'' COMMENT ''主体类型：USER 用户 / USER_GROUP 用户组 / DEPT 组织''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3. 权限点（判重键：perm_code）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2400, 0, 'sys:perm:strategy:list', '查询权限策略', 'API', '/perm-strategy/v1/*', 'GET', 2400, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:perm:strategy:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2401, 0, 'sys:perm:strategy:add', '新增权限策略', 'API', '/perm-strategy/v1', 'POST', 2401, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:perm:strategy:add');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2402, 0, 'sys:perm:strategy:edit', '编辑权限策略', 'API', '/perm-strategy/v1/*', 'PUT', 2402, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:perm:strategy:edit');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2403, 0, 'sys:perm:strategy:delete', '删除权限策略', 'API', '/perm-strategy/v1/*', 'DELETE', 2403, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:perm:strategy:delete');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2404, 0, 'sys:perm:strategy:set-default', '设置默认策略', 'API', '/perm-strategy/v1/*/default', 'PUT', 2404, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:perm:strategy:set-default');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2405, 0, 'sys:role:constraint:list', '查询角色约束', 'API', '/role-constraint/v1/*', 'GET', 2405, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:role:constraint:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2406, 0, 'sys:role:constraint:add', '新增角色约束', 'API', '/role-constraint/v1', 'POST', 2406, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:role:constraint:add');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2407, 0, 'sys:role:constraint:edit', '编辑角色约束', 'API', '/role-constraint/v1/*', 'PUT', 2407, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:role:constraint:edit');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2408, 0, 'sys:role:constraint:delete', '删除角色约束', 'API', '/role-constraint/v1/*', 'DELETE', 2408, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:role:constraint:delete');

-- ---------------------------------------------------------------------
-- 4. 授予 tenant_admin（按 perm_code 联查）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.perm_code IN
         ('sys:perm:strategy:list', 'sys:perm:strategy:add', 'sys:perm:strategy:edit',
          'sys:perm:strategy:delete', 'sys:perm:strategy:set-default',
          'sys:role:constraint:list', 'sys:role:constraint:add', 'sys:role:constraint:edit',
          'sys:role:constraint:delete')
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'tenant_admin'
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);

-- ---------------------------------------------------------------------
-- 5. 菜单（判重键：tenant_id + path，主键 MD5 派生）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5('menu:perm-strategy'), 1, 15), 16, 10) AS UNSIGNED),
       1,
       COALESCE((SELECT id FROM sys_menu
                 WHERE tenant_id = 1 AND path = '/system' AND menu_type = 'DIR' AND deleted = 0
                 LIMIT 1), 0),
       '权限策略', '/system/perm-strategy', 'system/perm-strategy/index', 'safety-certificate', 15,
       'MENU', 'sys:perm:strategy:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND path = '/system/perm-strategy' AND deleted = 0
);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':menu:perm-strategy')), 1, 15), 16, 10) AS UNSIGNED),
       t.id, COALESCE(sysdir.id, 0), '权限策略', '/system/perm-strategy', 'system/perm-strategy/index',
       'safety-certificate', 15, 'MENU', 'sys:perm:strategy:list', 1, 1
FROM sys_tenant t
LEFT JOIN sys_menu sysdir ON sysdir.tenant_id = t.id AND sysdir.path = '/system'
     AND sysdir.menu_type = 'DIR' AND sysdir.deleted = 0
WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu x
      WHERE x.tenant_id = t.id AND x.path = '/system/perm-strategy' AND x.deleted = 0
  );

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5('menu:role-constraint'), 1, 15), 16, 10) AS UNSIGNED),
       1,
       COALESCE((SELECT id FROM sys_menu
                 WHERE tenant_id = 1 AND path = '/system' AND menu_type = 'DIR' AND deleted = 0
                 LIMIT 1), 0),
       '角色约束', '/system/role-constraint', 'system/role-constraint/index', 'block', 16,
       'MENU', 'sys:role:constraint:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND path = '/system/role-constraint' AND deleted = 0
);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':menu:role-constraint')), 1, 15), 16, 10) AS UNSIGNED),
       t.id, COALESCE(sysdir.id, 0), '角色约束', '/system/role-constraint', 'system/role-constraint/index',
       'block', 16, 'MENU', 'sys:role:constraint:list', 1, 1
FROM sys_tenant t
LEFT JOIN sys_menu sysdir ON sysdir.tenant_id = t.id AND sysdir.path = '/system'
     AND sysdir.menu_type = 'DIR' AND sysdir.deleted = 0
WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu x
      WHERE x.tenant_id = t.id AND x.path = '/system/role-constraint' AND x.deleted = 0
  );
