-- =============================================================================
-- 文件        : V20__user_group.sql
-- 说明        : 权限中心扩展 M3 —— 用户组（静态组 + 动态规则组）
--
-- 背景：按 auth_tbl_user_group + rel_user_group_member 处置矩阵（计划 §1.2 M3 行）
--       新建 sys_user_group + sys_user_group_member。
--
-- 聚合不变量（应用层聚合根强制）：
--   ① group_code 租户内唯一（DB 唯一键兜底）；
--   ② 组树不得成环（parent_id 链路检测，聚合根保证）；
--   ③ category=STATIC 成员显式维护；category=DYNAMIC 成员由 filters 规则计算，
--     不可手工增删成员（filters 为 TEXT 列存 JSON，不用 MySQL json 列——与既有
--     约定一致，跨库兼容且 MyBatis 映射简单）。
--
-- 本脚本（幂等，可安全重放）：
--   1. 建表 sys_user_group + sys_user_group_member；
--   2. 权限点 sys:user:group:*（号段 2395–2399，判重键 perm_code）；
--   3. 授予 tenant_admin（按 perm_code 联查）；
--   4. 「用户组」菜单（tenant_id=1 + 复制到业务租户）。
--
-- 号段登记：2395–2399 = 用户组（2395 list、2396 add、2397 edit、2398 delete、
--           2399 member）。
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1. 用户组表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_group (
    id              BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT       NOT NULL                COMMENT '租户ID',
    group_code      VARCHAR(64)  NOT NULL                COMMENT '组编码（租户内唯一）',
    group_name      VARCHAR(128) NOT NULL                COMMENT '组名称',
    parent_id       BIGINT       NOT NULL DEFAULT 0      COMMENT '父组ID（0 表示顶级，树不得成环）',
    category        VARCHAR(16)  NOT NULL DEFAULT 'STATIC' COMMENT '组类别：STATIC 静态（成员显式维护）/ DYNAMIC 动态（成员按 filters 计算）',
    filters         TEXT         DEFAULT NULL            COMMENT '动态组过滤规则（JSON 数组：[{"field":"deptId","op":"eq","value":10}]，op ∈ eq/ne/in/like；仅 DYNAMIC 组使用）',
    description     VARCHAR(512) DEFAULT NULL            COMMENT '描述',
    sort_no         INT          NOT NULL DEFAULT 0      COMMENT '排序号',
    status          TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_code (tenant_id, group_code),
    KEY idx_group_parent (tenant_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户组表（静态组/动态规则组）';

-- ---------------------------------------------------------------------
-- 1.1 组成员关系表（STATIC 组显式维护；DYNAMIC 组成员由求值器写入快照）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_group_member (
    id              BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT       NOT NULL                COMMENT '租户ID',
    group_id        BIGINT       NOT NULL                COMMENT '用户组ID（逻辑关联 sys_user_group.id）',
    user_id         BIGINT       NOT NULL                COMMENT '成员用户ID（逻辑关联 sys_user.id）',
    member_source   VARCHAR(16)  NOT NULL DEFAULT 'STATIC' COMMENT '成员来源：STATIC 手工维护 / DYNAMIC 规则计算（动态组不可手工改）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_user (group_id, user_id),
    KEY idx_member_tenant (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户组成员关系表';

-- ---------------------------------------------------------------------
-- 2. 权限点（判重键：perm_code）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2395, 0, 'sys:user:group:list', '查询用户组', 'API', '/user-group/v1/*', 'GET', 2395, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:user:group:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2396, 0, 'sys:user:group:add', '新增用户组', 'API', '/user-group/v1', 'POST', 2396, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:user:group:add');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2397, 0, 'sys:user:group:edit', '编辑用户组', 'API', '/user-group/v1/*', 'PUT', 2397, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:user:group:edit');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2398, 0, 'sys:user:group:delete', '删除用户组', 'API', '/user-group/v1/*', 'DELETE', 2398, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:user:group:delete');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2399, 0, 'sys:user:group:member', '维护组成员', 'API', '/user-group/v1/*/members', 'PUT', 2399, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:user:group:member');

-- ---------------------------------------------------------------------
-- 3. 授予 tenant_admin（按 perm_code 联查）
-- ---------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT src.id, src.role_id, src.permission_id
FROM (
    SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
           r.id AS role_id, p.id AS permission_id
    FROM sys_role r
    JOIN sys_permission p ON p.perm_code IN
         ('sys:user:group:list', 'sys:user:group:add', 'sys:user:group:edit',
          'sys:user:group:delete', 'sys:user:group:member')
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'tenant_admin'
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);

-- ---------------------------------------------------------------------
-- 4. 菜单（判重键：tenant_id + path，主键 MD5 派生）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5('menu:user-group'), 1, 15), 16, 10) AS UNSIGNED),
       1,
       COALESCE((SELECT id FROM sys_menu
                 WHERE tenant_id = 1 AND path = '/system' AND menu_type = 'DIR' AND deleted = 0
                 LIMIT 1), 0),
       '用户组', '/system/user-group', 'system/user-group/index', 'team', 14,
       'MENU', 'sys:user:group:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND path = '/system/user-group' AND deleted = 0
);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':menu:user-group')), 1, 15), 16, 10) AS UNSIGNED),
       t.id, COALESCE(sysdir.id, 0), '用户组', '/system/user-group', 'system/user-group/index',
       'team', 14, 'MENU', 'sys:user:group:list', 1, 1
FROM sys_tenant t
LEFT JOIN sys_menu sysdir ON sysdir.tenant_id = t.id AND sysdir.path = '/system'
     AND sysdir.menu_type = 'DIR' AND sysdir.deleted = 0
WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu x
      WHERE x.tenant_id = t.id AND x.path = '/system/user-group' AND x.deleted = 0
  );
