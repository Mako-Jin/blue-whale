-- =============================================================================
-- 文件        : V22__session_device.sql
-- 说明        : 权限中心扩展 M3 —— 会话设备四表
--
-- 背景：按 auth_tbl_device_info / auth_tbl_login_history / auth_tbl_refresh_token /
--       auth_tbl_remember_me_token 处置矩阵（计划 §1.2 M3 行）新建：
--       sys_device / sys_login_history / sys_refresh_token / sys_remember_me_token。
--
-- 关键口径：
--   ① Redis 仍是实时校验源；本组表提供「会话可见性 + 管理端审计/吊销」能力；
--   ② 登录/刷新/登出/切换租户埋点写入（ADR #7）：账本写入失败不得阻断登录，
--     降级为仅告警（SessionLedgerPort 内 try-catch）；
--   ③ sys_login_history append-only：只插入、不更新、不带逻辑删除列；
--   ④ sys_refresh_token 记录只能「新增 / 撤销」，不可修改历史字段；
--     管理端强制下线 = 标记 revoked + 吊销时刻写入用户撤销通道（即时生效）；
--   ⑤ sys_remember_me_token 仅建表不接入 remember-me 功能（偏差说明，见计划
--     实施记录），为后续「记住我」预留 series 轮换存储。
--
-- 本脚本（幂等，可安全重放）：
--   1. 建表 sys_device / sys_login_history / sys_refresh_token / sys_remember_me_token；
--   2. 权限点 sys:session:* + sys:loginhistory:*（号段 2409–2412，判重键 perm_code）；
--   3. 授予 tenant_admin（按 perm_code 联查）；
--   4. 「会话与设备」「登录历史」菜单（tenant_id=1 + 复制到业务租户）。
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1. 设备表（设备指纹 + 用户维度唯一）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_device (
    id                 BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id          BIGINT       NOT NULL                COMMENT '租户ID',
    user_id            BIGINT       NOT NULL                COMMENT '用户ID（逻辑关联 sys_user.id）',
    device_fingerprint VARCHAR(128) NOT NULL                COMMENT '设备指纹（租户+用户+指纹唯一）',
    device_name        VARCHAR(128) DEFAULT NULL            COMMENT '设备名称',
    device_type        VARCHAR(16)  DEFAULT NULL            COMMENT '设备类型：PC / MOBILE / TABLET / UNKNOWN',
    os                 VARCHAR(64)  DEFAULT NULL            COMMENT '操作系统（User-Agent 解析快照）',
    browser            VARCHAR(64)  DEFAULT NULL            COMMENT '浏览器（User-Agent 解析快照）',
    trusted            TINYINT      NOT NULL DEFAULT 0      COMMENT '是否信任设备：0 否 / 1 是',
    last_login_time    DATETIME     DEFAULT NULL            COMMENT '最近一次使用时间',
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_user_fp (tenant_id, user_id, device_fingerprint),
    KEY idx_device_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='登录设备表';

-- ---------------------------------------------------------------------
-- 1.1 登录历史表（append-only：无 update_time、无 deleted，禁止 UPDATE）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_login_history (
    id                 BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id          BIGINT       NOT NULL                COMMENT '租户ID',
    user_id            BIGINT       DEFAULT NULL            COMMENT '用户ID（认证失败且用户不存在时为 NULL）',
    username           VARCHAR(64)  NOT NULL                COMMENT '尝试登录的用户名',
    login_type         VARCHAR(16)  NOT NULL                COMMENT '登录类型：PASSWORD 密码 / SOCIAL 三方 / OAUTH2 客户端 / SWITCH 切换租户 / REFRESH 刷新',
    login_result       VARCHAR(16)  NOT NULL                COMMENT '结果：SUCCESS 成功 / FAILURE 失败 / LOGOUT 登出',
    failure_reason     VARCHAR(255) DEFAULT NULL            COMMENT '失败原因（结果为 FAILURE 时记录）',
    ip                 VARCHAR(64)  DEFAULT NULL            COMMENT '来源 IP',
    user_agent         VARCHAR(512) DEFAULT NULL            COMMENT 'User-Agent',
    device_fingerprint VARCHAR(128) DEFAULT NULL            COMMENT '设备指纹（可为空：账本降级路径无指纹）',
    login_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间',
    PRIMARY KEY (id),
    KEY idx_login_history_user (tenant_id, user_id, login_time),
    KEY idx_login_history_time (login_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='登录历史表（append-only，禁止 UPDATE/DELETE）';

-- ---------------------------------------------------------------------
-- 1.2 refreshToken 记录表（只新增/撤销；jti 唯一，Redis 仍是实时校验源）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_refresh_token (
    id              BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT       NOT NULL                COMMENT '租户ID',
    user_id         BIGINT       NOT NULL                COMMENT '用户ID',
    jti             VARCHAR(64)  NOT NULL                COMMENT '令牌唯一标识（JWT jti，全局唯一）',
    family_id       VARCHAR(64)  NOT NULL                COMMENT '令牌族ID（同一会话轮换共用，AUTH-39）',
    issued_at       DATETIME     NOT NULL                COMMENT '签发时间',
    expires_at      DATETIME     NOT NULL                COMMENT '过期时间',
    revoked         TINYINT      NOT NULL DEFAULT 0      COMMENT '是否撤销：0 否 / 1 是（登出/强制下线/轮换复用时置 1）',
    revoked_at      DATETIME     DEFAULT NULL            COMMENT '撤销时间',
    revoked_reason  VARCHAR(64)  DEFAULT NULL            COMMENT '撤销原因：LOGOUT 登出 / ADMIN_REVOKE 强制下线 / FAMILY_REVOKED 族吊销 / ROTATED 轮换消费',
    device_id       BIGINT       DEFAULT NULL            COMMENT '登录设备ID（逻辑关联 sys_device.id，可为空）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_jti (jti),
    KEY idx_refresh_user (tenant_id, user_id, issued_at),
    KEY idx_refresh_family (family_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='refreshToken 会话记录表（只新增/撤销，不可改历史）';

-- ---------------------------------------------------------------------
-- 1.3 记住我令牌表（仅建表预留，暂不接入 remember-me 功能）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_remember_me_token (
    id              BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT       NOT NULL                COMMENT '租户ID',
    user_id         BIGINT       NOT NULL                COMMENT '用户ID',
    series          VARCHAR(64)  NOT NULL                COMMENT '系列标识（同一设备保持不变，验证后轮换 token）',
    token_hash      VARCHAR(128) NOT NULL                COMMENT '令牌哈希（SHA-256，禁止明文存储）',
    device_id       BIGINT       DEFAULT NULL            COMMENT '设备ID（逻辑关联 sys_device.id）',
    issued_at       DATETIME     NOT NULL                COMMENT '签发时间',
    last_used_at    DATETIME     DEFAULT NULL            COMMENT '最近使用时间',
    expires_at      DATETIME     NOT NULL                COMMENT '过期时间',
    revoked         TINYINT      NOT NULL DEFAULT 0      COMMENT '是否撤销：0 否 / 1 是',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_remember_series (series),
    KEY idx_remember_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='记住我令牌表（预留，暂未接入）';

-- ---------------------------------------------------------------------
-- 2. 权限点（判重键：perm_code）
-- ---------------------------------------------------------------------
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2409, 0, 'sys:session:list', '查询用户会话', 'API', '/session/v1/*', 'GET', 2409, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:session:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2410, 0, 'sys:session:revoke', '强制下线会话', 'API', '/session/v1/*/revoke', 'PUT', 2410, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:session:revoke');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2411, 0, 'sys:loginhistory:list', '查询登录历史', 'API', '/login-history/v1/*', 'GET', 2411, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:loginhistory:list');

INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
SELECT 2412, 0, 'sys:loginhistory:export', '导出登录历史', 'API', '/login-history/v1/export', 'GET', 2412, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE perm_code = 'sys:loginhistory:export');

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
         ('sys:session:list', 'sys:session:revoke',
          'sys:loginhistory:list', 'sys:loginhistory:export')
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
SELECT CAST(CONV(SUBSTR(MD5('menu:session-device'), 1, 15), 16, 10) AS UNSIGNED),
       1,
       COALESCE((SELECT id FROM sys_menu
                 WHERE tenant_id = 1 AND path = '/system' AND menu_type = 'DIR' AND deleted = 0
                 LIMIT 1), 0),
       '会话与设备', '/system/session', 'system/session/index', 'laptop', 17,
       'MENU', 'sys:session:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND path = '/system/session' AND deleted = 0
);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':menu:session-device')), 1, 15), 16, 10) AS UNSIGNED),
       t.id, COALESCE(sysdir.id, 0), '会话与设备', '/system/session', 'system/session/index',
       'laptop', 17, 'MENU', 'sys:session:list', 1, 1
FROM sys_tenant t
LEFT JOIN sys_menu sysdir ON sysdir.tenant_id = t.id AND sysdir.path = '/system'
     AND sysdir.menu_type = 'DIR' AND sysdir.deleted = 0
WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu x
      WHERE x.tenant_id = t.id AND x.path = '/system/session' AND x.deleted = 0
  );

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5('menu:login-history'), 1, 15), 16, 10) AS UNSIGNED),
       1,
       COALESCE((SELECT id FROM sys_menu
                 WHERE tenant_id = 1 AND path = '/system' AND menu_type = 'DIR' AND deleted = 0
                 LIMIT 1), 0),
       '登录历史', '/system/login-history', 'system/login-history/index', 'history', 18,
       'MENU', 'sys:loginhistory:list', 1, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE tenant_id = 1 AND path = '/system/login-history' AND deleted = 0
);

INSERT INTO sys_menu (id, tenant_id, parent_id, name, path, component, icon, sort,
                      menu_type, permission_code, visible, status)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(t.id, ':menu:login-history')), 1, 15), 16, 10) AS UNSIGNED),
       t.id, COALESCE(sysdir.id, 0), '登录历史', '/system/login-history', 'system/login-history/index',
       'history', 18, 'MENU', 'sys:loginhistory:list', 1, 1
FROM sys_tenant t
LEFT JOIN sys_menu sysdir ON sysdir.tenant_id = t.id AND sysdir.path = '/system'
     AND sysdir.menu_type = 'DIR' AND sysdir.deleted = 0
WHERE t.id <> 1 AND t.tenant_type = 'TENANT' AND t.deleted = 0 AND t.status = 1
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu x
      WHERE x.tenant_id = t.id AND x.path = '/system/login-history' AND x.deleted = 0
  );
