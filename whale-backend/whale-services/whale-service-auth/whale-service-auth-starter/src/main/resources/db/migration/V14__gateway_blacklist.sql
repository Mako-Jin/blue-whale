-- =============================================================================
-- 文件        : V14__gateway_blacklist.sql
-- 说明        : 网关路径黑名单入库（权限中心控制平面）
--
-- 语义：黑名单优先于白名单——请求路径命中黑名单时，即使同时命中白名单也一律拒绝
--       （网关返回 403），用于紧急封禁路径 / 临时下线接口，无需重启网关。
--       网关每 60s 拉取 GET /auth/v1/internal/gateway/blacklist（V14 之前的网关只拉白名单，
--       互不影响，可独立滚动升级）。
--
-- 本脚本（幂等）：
--   1. 建表 sys_gateway_blacklist（结构同 sys_gateway_whitelist，默认无种子）；
--   2. 权限点 sys:gateway:blacklist:list/add/edit/delete（号段 2364–2367，按 perm_code 判重），
--      为后续管理端 CRUD 预留；本轮不建菜单、不授租户管理员。
--
-- 号段登记：2364–2367 = 网关黑名单管理。
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1. 网关黑名单表
--    仅 status=1 的条目参与下发；status=0 表示封禁已解除（保留记录便于审计）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_gateway_blacklist (
    id              BIGINT       NOT NULL                COMMENT '主键ID',
    path_pattern    VARCHAR(255) NOT NULL                COMMENT '封禁路径（Ant 通配，如 /pay/**）',
    http_method     VARCHAR(16)  NOT NULL DEFAULT '*'    COMMENT 'HTTP 方法：* 表示不限',
    scope           VARCHAR(16)  NOT NULL DEFAULT 'PLATFORM' COMMENT '作用域：PLATFORM 平台',
    source          VARCHAR(16)  NOT NULL DEFAULT 'MANUAL'   COMMENT '来源：MANUAL 手工 / MIGRATION 迁移',
    remark          VARCHAR(255) DEFAULT NULL            COMMENT '备注（封禁原因 / 工单号）',
    status          TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 生效 / 0 解除',
    sort_no         INT          NOT NULL DEFAULT 0      COMMENT '排序号（小者优先）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gateway_blacklist (path_pattern, http_method, scope),
    KEY idx_gateway_blacklist_status (status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='网关路径黑名单表（优先于白名单）';

-- ---------------------------------------------------------------------
-- 2. 权限点（判重键：perm_code；为后续管理端 CRUD 预留，本轮不建菜单）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2364, 0, 'sys:gateway:blacklist:list', '查询网关黑名单', 'API',
       '/auth/v1/gateway/blacklist,/auth/v1/gateway/blacklist/*', 'GET', 2364, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:gateway:blacklist:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2365, 0, 'sys:gateway:blacklist:add', '新增网关黑名单', 'API',
       '/auth/v1/gateway/blacklist', 'POST', 2365, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:gateway:blacklist:add');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2366, 0, 'sys:gateway:blacklist:edit', '编辑网关黑名单', 'API',
       '/auth/v1/gateway/blacklist/*', 'PUT', 2366, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:gateway:blacklist:edit');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2367, 0, 'sys:gateway:blacklist:delete', '删除网关黑名单', 'API',
       '/auth/v1/gateway/blacklist/*', 'DELETE', 2367, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:gateway:blacklist:delete');
