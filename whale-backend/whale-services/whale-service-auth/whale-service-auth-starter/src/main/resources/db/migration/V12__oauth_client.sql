-- =============================================================================
-- 文件        : V12__oauth_client.sql
-- 说明        : 权限中心扩展 M0 —— OAuth2.0 客户端注册基座
--
-- 背景：按 auth_tbl_client_info 补齐「OAuth 客户端」能力，但按本服务口径重建为
--       sys_oauth_client：client_secret 只存 BCrypt 哈希（明文仅创建/轮换时返回一次），
--       redirect_uris / grant_types / scopes 以逗号分隔文本存储，避免 JSON 列在多库
--       适配时返工（§5.1-4）。
--
-- 本脚本（幂等）：
--   1. 建表 sys_oauth_client；
--   2. 内置 Web 客户端种子（secret 哈希，见下方说明）；
--   3. 权限点 sys:oauth:client:list/add/edit/secret/delete（号段 2374–2378）；
--   4. 授予 tenant_admin（按 perm_code 联查）。
--
-- 内置客户端说明：
--   client_id     = bluewhale-web
--   client_secret = BlueWhale@Web2026（仅用于本地联调，上线前必须轮换）
--   redirect_uri  = http://localhost:5173/oauth/callback,http://localhost:3000/oauth/callback
--   轮换方式：POST /oauth/client/v1/{id}/secret，旧密钥立即失效。
--
-- 号段登记：2374 list、2375 add、2376 edit、2377 secret、2378 delete。
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1. OAuth2 客户端表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_oauth_client (
    id                 BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    client_id          VARCHAR(64)   NOT NULL                COMMENT '客户端标识（全局唯一）',
    client_secret      VARCHAR(100)  NOT NULL                COMMENT '客户端密钥（BCrypt 哈希，明文不落库）',
    client_name        VARCHAR(128)  NOT NULL                COMMENT '客户端名称',
    redirect_uris      TEXT          DEFAULT NULL            COMMENT '回调地址白名单，逗号分隔；authorization_code 必填',
    grant_types        VARCHAR(255)  NOT NULL                COMMENT '授权类型，逗号分隔：authorization_code/client_credentials/refresh_token',
    scopes             VARCHAR(1024) NOT NULL                COMMENT '授权范围，逗号分隔（映射 sys_permission.perm_code 前缀）',
    access_token_ttl   INT           NOT NULL DEFAULT 7200   COMMENT '访问令牌有效期（秒）',
    refresh_token_ttl  INT           NOT NULL DEFAULT 604800 COMMENT '刷新令牌有效期（秒）',
    auto_approve       TINYINT       NOT NULL DEFAULT 0      COMMENT '是否免确认授权：1 是 / 0 否',
    status             TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用（停用后不得换票）',
    create_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oauth_client_id (client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OAuth2 客户端注册表';

-- ---------------------------------------------------------------------
-- 2. 内置 Web 客户端种子（判重键：client_id）
--    哈希为 BCrypt(cost=12) of "BlueWhale@Web2026"
-- ---------------------------------------------------------------------
INSERT INTO sys_oauth_client (id, client_id, client_secret, client_name, redirect_uris,
                              grant_types, scopes, access_token_ttl, refresh_token_ttl,
                              auto_approve, status)
SELECT 26010, 'bluewhale-web',
       '$2b$12$VJr7Q/tEHKImC3AizM5rBuvWn9ykj18/UZFWnitus8R1FtarAK3vS',
       'BlueWhale Web 管理端',
       'http://localhost:5173/oauth/callback,http://localhost:3000/oauth/callback',
       'authorization_code,refresh_token',
       'openid,profile,sys:user:list', 7200, 604800, 0, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_oauth_client WHERE client_id = 'bluewhale-web');

-- ---------------------------------------------------------------------
-- 3. 权限点（判重键：perm_code）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2374, 0, 'sys:oauth:client:list', '查询 OAuth 客户端', 'API', '/oauth-client/page,/oauth-client/*', 'GET', 2374, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:oauth:client:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2375, 0, 'sys:oauth:client:add', '注册 OAuth 客户端', 'API', '/oauth-client', 'POST', 2375, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:oauth:client:add');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2376, 0, 'sys:oauth:client:edit', '编辑 OAuth 客户端', 'API', '/oauth-client/*', 'PUT', 2376, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:oauth:client:edit');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2377, 0, 'sys:oauth:client:secret', '轮换客户端密钥', 'API', '/oauth-client/*/secret', 'POST', 2377, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:oauth:client:secret');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2378, 0, 'sys:oauth:client:delete', '停用 OAuth 客户端', 'API', '/oauth-client/*', 'DELETE', 2378, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:oauth:client:delete');

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
         ('sys:oauth:client:list', 'sys:oauth:client:add', 'sys:oauth:client:edit',
          'sys:oauth:client:secret', 'sys:oauth:client:delete')
         AND p.deleted = 0 AND p.status = 1
    WHERE r.deleted = 0 AND r.status = 1 AND r.role_code = 'tenant_admin'
) src
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission tgt
    WHERE tgt.id = src.id OR (tgt.role_id = src.role_id AND tgt.permission_id = src.permission_id)
);
