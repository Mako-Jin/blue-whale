-- =============================================================================
-- 文件        : V17__oauth2_server.sql
-- 说明        : 权限中心扩展 M1 —— OAuth2.0 授权服务器持久化（授权 / 授权确认）
--
-- 背景：M0 已建立客户端注册基座（sys_oauth_client）。M1 接入 Spring Security 7
--       内置的 Spring Authorization Server，需要两张协议级状态表：
--         oauth2_authorization         —— 授权码 / 访问令牌 / 刷新令牌 / 状态等
--         oauth2_authorization_consent —— 用户对客户端 scope 的授权确认
--       表名与列名严格对齐官方 schema（oauth2-authorization-schema.sql），
--       避免升级 Spring Security 版本时手工改表。
--
-- 说明：
--   1. 官方脚本使用 blob / timestamp，MySQL 需在连接串追加
--      preserveInstants=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
--      以保证时间语义一致；本服务连接串由环境变量维护，部署侧需确认。
--   2. 授权码 / 令牌均为一次性状态，JdbcOAuth2AuthorizationService 按值直接查询，
--      故不额外建索引；单表规模受令牌 TTL 约束。
--   3. 客户端注册信息仍以 sys_oauth_client 为唯一事实源，不回落到
--      oauth2_registered_client（不创建该表）。
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1. 授权记录（授权码 / 访问令牌 / 刷新令牌）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oauth2_authorization (
    id                          VARCHAR(100) NOT NULL                COMMENT '主键（授权记录 ID）',
    registered_client_id        VARCHAR(100) NOT NULL                COMMENT '注册客户端主键（sys_oauth_client.id）',
    principal_name              VARCHAR(200) NOT NULL                COMMENT '主体名（用户名或 client_id）',
    authorization_grant_type    VARCHAR(100) NOT NULL                COMMENT '授权类型',
    authorized_scopes           VARCHAR(1000) DEFAULT NULL           COMMENT '已授权 scope（逗号分隔）',
    attributes                  BLOB          DEFAULT NULL           COMMENT '协议扩展属性',
    state                       VARCHAR(500)  DEFAULT NULL           COMMENT '授权请求 state',
    authorization_code_value    BLOB          DEFAULT NULL           COMMENT '授权码值',
    authorization_code_issued_at  TIMESTAMP   DEFAULT NULL           COMMENT '授权码签发时间',
    authorization_code_expires_at TIMESTAMP   DEFAULT NULL           COMMENT '授权码过期时间',
    authorization_code_metadata BLOB          DEFAULT NULL           COMMENT '授权码元数据',
    access_token_value          BLOB          DEFAULT NULL           COMMENT '访问令牌值',
    access_token_issued_at      TIMESTAMP     DEFAULT NULL           COMMENT '访问令牌签发时间',
    access_token_expires_at     TIMESTAMP     DEFAULT NULL           COMMENT '访问令牌过期时间',
    access_token_metadata       BLOB          DEFAULT NULL           COMMENT '访问令牌元数据',
    access_token_type           VARCHAR(100)  DEFAULT NULL           COMMENT '访问令牌类型',
    access_token_scopes         VARCHAR(1000) DEFAULT NULL           COMMENT '访问令牌 scope（逗号分隔）',
    oidc_id_token_value         BLOB          DEFAULT NULL           COMMENT 'OIDC ID Token 值',
    oidc_id_token_issued_at     TIMESTAMP     DEFAULT NULL           COMMENT 'OIDC ID Token 签发时间',
    oidc_id_token_expires_at    TIMESTAMP     DEFAULT NULL           COMMENT 'OIDC ID Token 过期时间',
    oidc_id_token_metadata      BLOB          DEFAULT NULL           COMMENT 'OIDC ID Token 元数据',
    refresh_token_value         BLOB          DEFAULT NULL           COMMENT '刷新令牌值',
    refresh_token_issued_at     TIMESTAMP     DEFAULT NULL           COMMENT '刷新令牌签发时间',
    refresh_token_expires_at    TIMESTAMP     DEFAULT NULL           COMMENT '刷新令牌过期时间',
    refresh_token_metadata      BLOB          DEFAULT NULL           COMMENT '刷新令牌元数据',
    user_code_value             BLOB          DEFAULT NULL           COMMENT '设备授权 user_code',
    user_code_issued_at         TIMESTAMP     DEFAULT NULL           COMMENT 'user_code 签发时间',
    user_code_expires_at        TIMESTAMP     DEFAULT NULL           COMMENT 'user_code 过期时间',
    user_code_metadata          BLOB          DEFAULT NULL           COMMENT 'user_code 元数据',
    device_code_value           BLOB          DEFAULT NULL           COMMENT '设备授权 device_code',
    device_code_issued_at       TIMESTAMP     DEFAULT NULL           COMMENT 'device_code 签发时间',
    device_code_expires_at      TIMESTAMP     DEFAULT NULL           COMMENT 'device_code 过期时间',
    device_code_metadata        BLOB          DEFAULT NULL           COMMENT 'device_code 元数据',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OAuth2 授权记录';

-- ---------------------------------------------------------------------
-- 2. 授权确认（免确认客户端 auto_approve=1 时不落库）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oauth2_authorization_consent (
    registered_client_id VARCHAR(100)  NOT NULL COMMENT '注册客户端主键（sys_oauth_client.id）',
    principal_name       VARCHAR(200)  NOT NULL COMMENT '主体名（用户名）',
    authorities          VARCHAR(1000) NOT NULL COMMENT '已确认的 scope 权限（逗号分隔）',
    PRIMARY KEY (registered_client_id, principal_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OAuth2 授权确认';
