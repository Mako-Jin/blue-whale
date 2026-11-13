-- =============================================================================
-- 文件        : V11__brand_config.sql
-- 说明        : 权限中心扩展 M0 —— 登录页品牌配置基座
--
-- 背景：按 auth_tbl_brand_config（源库 yc-sts-db-auth）补齐「品牌/主题」能力。
--       原表直接照搬会与本服务既有 sys_* 命名和审计列约定冲突，故按 §5.1 口径
--       重建为 sys_brand_config：target_type/target_id/domain 构成唯一寻址键，
--       priority 越小越优先，status=1 才参与登录页解析。
--
-- 本脚本（幂等）：
--   1. 建表 sys_brand_config；
--   2. 平台默认品牌种子（target_type=PLATFORM, target_id=0, domain=''）；
--   3. 权限点 sys:brand:list/add/edit/delete（号段 2370–2373，判重键 perm_code）；
--   4. 授予 tenant_admin（按 perm_code 联查，绝不按 id）；
--   5. 平台租户「品牌配置」菜单，并复制到已有业务租户。
--
-- 号段登记：2370–2348 = 品牌配置 / OAuth 客户端（2370 list、2371 add、2372 edit、
--           2373 delete；2344–2348 见 V12）。
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1. 品牌配置表
--    domain 用 NOT NULL DEFAULT '' 而非 NULL：MySQL 唯一键允许多个 NULL，
--    会导致同一 (target_type, target_id) 下出现多行「默认域名」品牌。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_brand_config (
    id              BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    target_type     VARCHAR(16)  NOT NULL DEFAULT 'PLATFORM' COMMENT '作用域：PLATFORM 平台 / TENANT 租户 / COMPANY 公司',
    target_id       BIGINT       NOT NULL DEFAULT 0      COMMENT '作用域ID：PLATFORM 固定 0，其余为租户/公司ID',
    domain          VARCHAR(128) NOT NULL DEFAULT ''     COMMENT '绑定域名（可空串表示默认品牌），同一作用域内唯一',
    brand_name      VARCHAR(128) NOT NULL                COMMENT '品牌名称',
    logo_url        VARCHAR(512) DEFAULT NULL            COMMENT 'Logo 地址',
    login_title     VARCHAR(128) DEFAULT NULL            COMMENT '登录页标题',
    institution_name VARCHAR(255) DEFAULT NULL           COMMENT '机构全称',
    copyright       VARCHAR(255) DEFAULT NULL            COMMENT '版权信息',
    primary_color   VARCHAR(16)  DEFAULT NULL            COMMENT '主题色（#RGB / #RRGGBB）',
    background_url  VARCHAR(512) DEFAULT NULL            COMMENT '登录页背景图地址',
    priority        INT          NOT NULL DEFAULT 0      COMMENT '解析优先级：数字越小越优先',
    status          TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_brand_target_domain (target_type, target_id, domain),
    KEY idx_brand_domain (domain, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='品牌配置表（登录页品牌/主题）';

-- ---------------------------------------------------------------------
-- 2. 平台默认品牌种子（判重键：target_type + target_id + domain）
-- ---------------------------------------------------------------------
INSERT INTO sys_brand_config (id, target_type, target_id, domain, brand_name, login_title,
                              institution_name, copyright, primary_color, priority, status)
SELECT 1, 'PLATFORM', 0, '', 'BlueWhale', 'BlueWhale 蓝鲸平台', 'BlueWhale',
       '© BlueWhale', '#1677ff', 100, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_brand_config
    WHERE target_type = 'PLATFORM' AND target_id = 0 AND domain = ''
);

-- ---------------------------------------------------------------------
-- 3. 权限点（判重键：perm_code —— 主键撞号不得让整条语句静默跳过）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2370, 0, 'sys:brand:list', '查询品牌配置', 'API', '/brand/page,/brand/*', 'GET', 2370, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:brand:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2371, 0, 'sys:brand:add', '新增品牌配置', 'API', '/brand', 'POST', 2371, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:brand:add');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2372, 0, 'sys:brand:edit', '编辑品牌配置', 'API', '/brand/*', 'PUT', 2372, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:brand:edit');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2373, 0, 'sys:brand:delete', '删除品牌配置', 'API', '/brand/*', 'DELETE', 2373, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:brand:delete');

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
         ('sys:brand:list', 'sys:brand:add', 'sys:brand:edit', 'sys:brand:delete')
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'tenant_admin'
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);

-- ---------------------------------------------------------------------
-- 5. 菜单（判重键：tenant_id + path，主键 MD5 派生）
--    父节点取各租户自己的 /system 目录，找不到时回落顶级（0）。
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5('menu:brand-config'), 1, 15), 16, 10) AS UNSIGNED),
       1,
       COALESCE((SELECT id FROM sys_menu
                 WHERE tenant_id = 1 AND path = '/system' AND menu_type = 'DIR' AND deleted = 0
                 LIMIT 1), 0),
       '品牌配置', '/system/brand', 'system/brand/index', 'picture', 12,
       'MENU', 'sys:brand:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND path = '/system/brand' AND deleted = 0
);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':menu:brand-config')), 1, 15), 16, 10) AS UNSIGNED),
       t.id, COALESCE(sysdir.id, 0), '品牌配置', '/system/brand', 'system/brand/index',
       'picture', 12, 'MENU', 'sys:brand:list', 1, 1
FROM sys_tenant t
LEFT JOIN sys_menu sysdir ON sysdir.tenant_id = t.id AND sysdir.path = '/system'
     AND sysdir.menu_type = 'DIR' AND sysdir.deleted = 0
WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu x
      WHERE x.tenant_id = t.id AND x.path = '/system/brand' AND x.deleted = 0
  );
