-- ============================================================================
-- V3 · 权限域表结构（达梦 DM8 方言）
--
-- 【执行方式】达梦 profile 下 Flyway 是关闭的（Flyway 官方无达梦模块，
-- 见 application-dameng.yml 与 docs/decisions/0010），本脚本作为**交付脚本**
-- 在部署时执行一次，辅助脚本 scripts/init-dameng.sh。
--
-- 【编号仍用 V3__】不是给 Flyway 看的，是为了三套脚本能按同一条时间线对照；
-- 将来若在达梦上恢复自动迁移，文件无需改名挪位即可被 Flyway 接管。
--
-- 达梦方言相对 MySQL 的差异：
--   1. 自增列是 IDENTITY(种子, 增量)，不是 AUTO_INCREMENT
--   2. 列注释必须用独立的 COMMENT ON COLUMN，不支持 inline COMMENT '...'
--   3. 没有 ENGINE / DEFAULT CHARSET / COLLATE 表选项
--   4. 标识符不加引号会被自动转成大写存储（与 Oracle 一致）
--   5. VARCHAR 长度默认按**字节**计 → 一律显式写 CHAR 语义
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 租户
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_tenant
(
    id             BIGINT IDENTITY(1, 1) NOT NULL,
    tenant_code    VARCHAR(64 CHAR)  NOT NULL,
    tenant_name    VARCHAR(128 CHAR) NOT NULL,
    parent_id      BIGINT,
    tenant_level   INT,
    code_path      VARCHAR(512 CHAR),
    has_child      TINYINT DEFAULT 0,
    status         VARCHAR(16 CHAR)  NOT NULL,
    description    VARCHAR(512 CHAR),
    allow_register TINYINT DEFAULT 0,
    allow_add      TINYINT DEFAULT 0,
    create_by      BIGINT,
    create_time    BIGINT,
    update_by      BIGINT,
    update_time    BIGINT,
    deleted        TINYINT DEFAULT 0 NOT NULL,
    CONSTRAINT pk_bw_auth_tenant PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_bw_auth_tenant_code ON bw_auth_tenant (tenant_code);
CREATE INDEX idx_bw_auth_tenant_parent ON bw_auth_tenant (parent_id);

-- ---------------------------------------------------------------------------
-- 2. 用户
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_user
(
    id                   BIGINT IDENTITY(1, 1) NOT NULL,
    tenant_id            BIGINT       NOT NULL,
    username             VARCHAR(64 CHAR)  NOT NULL,
    display_name         VARCHAR(128 CHAR),
    email                VARCHAR(128 CHAR),
    phone                VARCHAR(32 CHAR),
    password_hash        VARCHAR(255 CHAR),
    password_algorithm   VARCHAR(32 CHAR),
    password_updated_at  BIGINT,
    password_expires_at  BIGINT,
    must_change_password TINYINT DEFAULT 0,
    status               VARCHAR(16 CHAR) NOT NULL,
    failed_attempts      INT DEFAULT 0,
    locked_until         BIGINT,
    lock_reason          VARCHAR(255 CHAR),
    last_login_at        BIGINT,
    avatar_url           VARCHAR(512 CHAR),
    mfa_enabled          TINYINT DEFAULT 0,
    mfa_type             VARCHAR(16 CHAR),
    create_by            BIGINT,
    create_time          BIGINT,
    update_by            BIGINT,
    update_time          BIGINT,
    deleted              TINYINT DEFAULT 0 NOT NULL,
    CONSTRAINT pk_bw_auth_user PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_bw_auth_user_tenant_username ON bw_auth_user (tenant_id, username);
CREATE INDEX idx_bw_auth_user_tenant_email ON bw_auth_user (tenant_id, email);
CREATE INDEX idx_bw_auth_user_tenant_phone ON bw_auth_user (tenant_id, phone);

-- ---------------------------------------------------------------------------
-- 3. 组织 / 部门
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_organization
(
    id            BIGINT IDENTITY(1, 1) NOT NULL,
    tenant_id     BIGINT       NOT NULL,
    org_code      VARCHAR(64 CHAR)  NOT NULL,
    org_name      VARCHAR(128 CHAR) NOT NULL,
    full_name     VARCHAR(512 CHAR),
    parent_id     BIGINT,
    org_level     INT,
    code_path     VARCHAR(512 CHAR),
    has_child     TINYINT DEFAULT 0,
    org_type      VARCHAR(32 CHAR),
    manager_id    BIGINT,
    manager_name  VARCHAR(128 CHAR),
    sort_no       INT DEFAULT 0,
    status        VARCHAR(16 CHAR),
    description   VARCHAR(512 CHAR),
    contact_name  VARCHAR(128 CHAR),
    contact_phone VARCHAR(32 CHAR),
    contact_email VARCHAR(128 CHAR),
    create_by     BIGINT,
    create_time   BIGINT,
    update_by     BIGINT,
    update_time   BIGINT,
    deleted       TINYINT DEFAULT 0 NOT NULL,
    CONSTRAINT pk_bw_auth_organization PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_bw_auth_org_tenant_code ON bw_auth_organization (tenant_id, org_code);
CREATE INDEX idx_bw_auth_org_parent ON bw_auth_organization (parent_id);
CREATE INDEX idx_bw_auth_org_tenant ON bw_auth_organization (tenant_id);

-- ---------------------------------------------------------------------------
-- 4. 角色
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_role
(
    id               BIGINT IDENTITY(1, 1) NOT NULL,
    tenant_id        BIGINT       NOT NULL,
    role_code        VARCHAR(64 CHAR)  NOT NULL,
    role_name        VARCHAR(128 CHAR) NOT NULL,
    parent_id        BIGINT,
    role_level       INT,
    category         VARCHAR(32 CHAR),
    is_default       TINYINT DEFAULT 0,
    inherit_strategy VARCHAR(32 CHAR),
    data_scope       VARCHAR(32 CHAR),
    filter_json      TEXT,
    sort_no          INT DEFAULT 0,
    status           VARCHAR(16 CHAR),
    description      VARCHAR(512 CHAR),
    create_by        BIGINT,
    create_time      BIGINT,
    update_by        BIGINT,
    update_time      BIGINT,
    deleted          TINYINT DEFAULT 0 NOT NULL,
    CONSTRAINT pk_bw_auth_role PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_bw_auth_role_tenant_code ON bw_auth_role (tenant_id, role_code);
CREATE INDEX idx_bw_auth_role_parent ON bw_auth_role (parent_id);
CREATE INDEX idx_bw_auth_role_tenant ON bw_auth_role (tenant_id);

-- ---------------------------------------------------------------------------
-- 5. 用户组
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_user_group
(
    id             BIGINT IDENTITY(1, 1) NOT NULL,
    tenant_id      BIGINT       NOT NULL,
    group_code     VARCHAR(64 CHAR)  NOT NULL,
    group_name     VARCHAR(128 CHAR) NOT NULL,
    parent_id      BIGINT,
    category       VARCHAR(32 CHAR),
    filter_json    TEXT,
    org_scope_json TEXT,
    is_default     TINYINT DEFAULT 0,
    is_enabled     TINYINT DEFAULT 1,
    sort_no        INT DEFAULT 0,
    description    VARCHAR(512 CHAR),
    create_by      BIGINT,
    create_time    BIGINT,
    update_by      BIGINT,
    update_time    BIGINT,
    deleted        TINYINT DEFAULT 0 NOT NULL,
    CONSTRAINT pk_bw_auth_user_group PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_bw_auth_user_group_tenant_code ON bw_auth_user_group (tenant_id, group_code);
CREATE INDEX idx_bw_auth_user_group_parent ON bw_auth_user_group (parent_id);
CREATE INDEX idx_bw_auth_user_group_tenant ON bw_auth_user_group (tenant_id);

-- ---------------------------------------------------------------------------
-- 6. 资源
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_resource
(
    id                BIGINT IDENTITY(1, 1) NOT NULL,
    tenant_id         BIGINT,
    resource_code     VARCHAR(128 CHAR) NOT NULL,
    resource_name     VARCHAR(128 CHAR) NOT NULL,
    resource_type     VARCHAR(32 CHAR)  NOT NULL,
    resource_category VARCHAR(64 CHAR),
    parent_code       VARCHAR(128 CHAR),
    request_url       VARCHAR(512 CHAR),
    request_method    VARCHAR(16 CHAR),
    icon              VARCHAR(128 CHAR),
    sort_no           INT DEFAULT 0,
    is_enabled        TINYINT DEFAULT 1,
    is_whitelist      TINYINT DEFAULT 0,
    is_deprecated     TINYINT DEFAULT 0,
    resource_version  VARCHAR(32 CHAR),
    description       VARCHAR(512 CHAR),
    create_by         BIGINT,
    create_time       BIGINT,
    update_by         BIGINT,
    update_time       BIGINT,
    deleted           TINYINT DEFAULT 0 NOT NULL,
    CONSTRAINT pk_bw_auth_resource PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_bw_auth_resource_code ON bw_auth_resource (resource_code);
CREATE INDEX idx_bw_auth_resource_parent ON bw_auth_resource (parent_code);
CREATE INDEX idx_bw_auth_resource_type ON bw_auth_resource (resource_type);

-- ---------------------------------------------------------------------------
-- 7. 角色-成员
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_rel_role_member
(
    id             BIGINT IDENTITY(1, 1) NOT NULL,
    tenant_id      BIGINT      NOT NULL,
    role_id        BIGINT      NOT NULL,
    member_id      BIGINT      NOT NULL,
    member_type    VARCHAR(16 CHAR) NOT NULL,
    effective_from BIGINT,
    effective_to   BIGINT,
    create_by      BIGINT,
    create_time    BIGINT,
    update_by      BIGINT,
    update_time    BIGINT,
    deleted        TINYINT DEFAULT 0 NOT NULL,
    CONSTRAINT pk_bw_auth_rel_role_member PRIMARY KEY (id)
);

CREATE INDEX idx_bw_auth_rel_role_member_role ON bw_auth_rel_role_member (role_id);
CREATE INDEX idx_bw_auth_rel_role_member_member ON bw_auth_rel_role_member (member_type, member_id);
CREATE INDEX idx_bw_auth_rel_role_member_tenant ON bw_auth_rel_role_member (tenant_id);

-- ---------------------------------------------------------------------------
-- 8. 角色-资源
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_rel_role_resource
(
    id             BIGINT IDENTITY(1, 1) NOT NULL,
    tenant_id      BIGINT      NOT NULL,
    role_id        BIGINT      NOT NULL,
    resource_id    BIGINT      NOT NULL,
    effect         VARCHAR(8 CHAR) DEFAULT 'ALLOW',
    priority       INT DEFAULT 0,
    effective_from BIGINT,
    effective_to   BIGINT,
    create_by      BIGINT,
    create_time    BIGINT,
    update_by      BIGINT,
    update_time    BIGINT,
    deleted        TINYINT DEFAULT 0 NOT NULL,
    CONSTRAINT pk_bw_auth_rel_role_resource PRIMARY KEY (id)
);

CREATE INDEX idx_bw_auth_rel_role_resource_role ON bw_auth_rel_role_resource (role_id);
CREATE INDEX idx_bw_auth_rel_role_resource_resource ON bw_auth_rel_role_resource (resource_id);
CREATE INDEX idx_bw_auth_rel_role_resource_tenant ON bw_auth_rel_role_resource (tenant_id);

-- ---------------------------------------------------------------------------
-- 9. 角色-资源实例（数据权限）
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_rel_role_resource_instance
(
    id             BIGINT IDENTITY(1, 1) NOT NULL,
    tenant_id      BIGINT       NOT NULL,
    role_id        BIGINT       NOT NULL,
    resource_id    BIGINT       NOT NULL,
    resource_type  VARCHAR(32 CHAR),
    instance_id    VARCHAR(128 CHAR),
    instance_name  VARCHAR(255 CHAR),
    actions_json   TEXT,
    filter_json    TEXT,
    effective_from BIGINT,
    effective_to   BIGINT,
    create_by      BIGINT,
    create_time    BIGINT,
    update_by      BIGINT,
    update_time    BIGINT,
    deleted        TINYINT DEFAULT 0 NOT NULL,
    CONSTRAINT pk_bw_auth_rel_role_resource_instance PRIMARY KEY (id)
);

CREATE INDEX idx_bw_auth_rel_rri_role ON bw_auth_rel_role_resource_instance (role_id);
CREATE INDEX idx_bw_auth_rel_rri_resource ON bw_auth_rel_role_resource_instance (resource_id);

-- ---------------------------------------------------------------------------
-- 10. 组织-用户
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_rel_org_user
(
    id                  BIGINT IDENTITY(1, 1) NOT NULL,
    tenant_id           BIGINT      NOT NULL,
    organization_id     BIGINT      NOT NULL,
    user_id             BIGINT      NOT NULL,
    is_primary_position TINYINT DEFAULT 0,
    position_name       VARCHAR(128 CHAR),
    relation_type       VARCHAR(32 CHAR),
    effective_from      BIGINT,
    effective_to        BIGINT,
    create_by           BIGINT,
    create_time         BIGINT,
    update_by           BIGINT,
    update_time         BIGINT,
    deleted             TINYINT DEFAULT 0 NOT NULL,
    CONSTRAINT pk_bw_auth_rel_org_user PRIMARY KEY (id)
);

CREATE INDEX idx_bw_auth_rel_org_user_org ON bw_auth_rel_org_user (organization_id);
CREATE INDEX idx_bw_auth_rel_org_user_user ON bw_auth_rel_org_user (user_id);

-- ---------------------------------------------------------------------------
-- 11. 用户组-成员
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_rel_user_group_member
(
    id             BIGINT IDENTITY(1, 1) NOT NULL,
    tenant_id      BIGINT      NOT NULL,
    user_group_id  BIGINT      NOT NULL,
    member_id      BIGINT      NOT NULL,
    member_type    VARCHAR(16 CHAR) NOT NULL,
    effective_from BIGINT,
    effective_to   BIGINT,
    create_by      BIGINT,
    create_time    BIGINT,
    update_by      BIGINT,
    update_time    BIGINT,
    deleted        TINYINT DEFAULT 0 NOT NULL,
    CONSTRAINT pk_bw_auth_rel_user_group_member PRIMARY KEY (id)
);

CREATE INDEX idx_bw_auth_rel_ugm_group ON bw_auth_rel_user_group_member (user_group_id);
CREATE INDEX idx_bw_auth_rel_ugm_member ON bw_auth_rel_user_group_member (member_type, member_id);

-- ---------------------------------------------------------------------------
-- 12. 刷新令牌
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_refresh_token
(
    id         BIGINT IDENTITY(1, 1) NOT NULL,
    tenant_id  BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    jti        VARCHAR(64 CHAR)  NOT NULL,
    token_hash VARCHAR(128 CHAR) NOT NULL,
    issued_at  BIGINT       NOT NULL,
    expires_at BIGINT       NOT NULL,
    revoked    TINYINT DEFAULT 0 NOT NULL,
    revoked_at BIGINT,
    client_ip  VARCHAR(64 CHAR),
    user_agent VARCHAR(512 CHAR),
    CONSTRAINT pk_bw_auth_refresh_token PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_bw_auth_refresh_token_jti ON bw_auth_refresh_token (jti);
CREATE INDEX idx_bw_auth_refresh_token_user ON bw_auth_refresh_token (user_id);
CREATE INDEX idx_bw_auth_refresh_token_expires ON bw_auth_refresh_token (expires_at);

-- ---------------------------------------------------------------------------
-- 注释：达梦不支持 inline COMMENT，必须拆成独立语句。
-- 只给语义不直观的列补注（表名与列名本身已相当自解释），避免脚本过长。
-- ---------------------------------------------------------------------------
COMMENT ON TABLE bw_auth_tenant IS '租户';
COMMENT ON COLUMN bw_auth_tenant.tenant_code IS '租户编码，对外稳定标识，全局唯一';
COMMENT ON COLUMN bw_auth_tenant.code_path IS '物化路径，如 /1/7/23，用于子树查询';
COMMENT ON COLUMN bw_auth_tenant.deleted IS '逻辑删除：0 正常 1 已删除';

COMMENT ON TABLE bw_auth_user IS '用户（账号主体）';
COMMENT ON COLUMN bw_auth_user.password_hash IS '口令哈希，算法见 password_algorithm';
COMMENT ON COLUMN bw_auth_user.password_algorithm IS '口令算法标识，如 PBKDF2-SHA256';
COMMENT ON COLUMN bw_auth_user.locked_until IS '锁定至（毫秒），NULL 表示未锁定';
COMMENT ON COLUMN bw_auth_user.deleted IS '逻辑删除：0 正常 1 已删除';

COMMENT ON TABLE bw_auth_organization IS '组织 / 部门';
COMMENT ON COLUMN bw_auth_organization.org_type IS '组织类型：COMPANY / DEPT / TEAM ...';

COMMENT ON TABLE bw_auth_role IS '角色';
COMMENT ON COLUMN bw_auth_role.data_scope IS '数据范围：ALL / DEPT / DEPT_AND_CHILD / SELF / CUSTOM';
COMMENT ON COLUMN bw_auth_role.inherit_strategy IS '继承策略：NONE / INHERIT / OVERRIDE';

COMMENT ON TABLE bw_auth_user_group IS '用户组';
COMMENT ON COLUMN bw_auth_user_group.category IS '分组方式：STATIC / DYNAMIC';

COMMENT ON TABLE bw_auth_resource IS '资源（菜单 / 页面 / 按钮 / API）';
COMMENT ON COLUMN bw_auth_resource.resource_code IS '资源标识，即 @RequiresPermission 的值，全局唯一';
COMMENT ON COLUMN bw_auth_resource.is_whitelist IS '是否免鉴权白名单：0 否 1 是';

COMMENT ON TABLE bw_auth_rel_role_member IS '角色-成员（用户 / 用户组）';
COMMENT ON COLUMN bw_auth_rel_role_member.member_type IS '成员类型：USER / GROUP';

COMMENT ON TABLE bw_auth_rel_role_resource IS '角色-资源（RBAC 授权关系）';
COMMENT ON COLUMN bw_auth_rel_role_resource.effect IS '效果：ALLOW / DENY';

COMMENT ON TABLE bw_auth_rel_role_resource_instance IS '角色-资源实例（数据权限）';

COMMENT ON TABLE bw_auth_rel_org_user IS '组织-用户';
COMMENT ON COLUMN bw_auth_rel_org_user.is_primary_position IS '是否主岗：0 否 1 是';

COMMENT ON TABLE bw_auth_rel_user_group_member IS '用户组-成员（用户 / 用户组）';

COMMENT ON TABLE bw_auth_refresh_token IS '刷新令牌';
COMMENT ON COLUMN bw_auth_refresh_token.token_hash IS '令牌摘要：只存哈希，库泄露也不能直接拿去用';

-- ============================================================================
-- 方言差异对照（相对 mysql/V3__create_bw_auth_tables.sql）
--
--   项目            MySQL 8                           达梦 DM8
--   ------------    ------------------------------    ------------------------------
--   自增主键        BIGINT NOT NULL AUTO_INCREMENT    BIGINT IDENTITY(1, 1) NOT NULL
--   变长字符串      VARCHAR(64)                       VARCHAR(64 CHAR)
--   长文本          TEXT                              TEXT（等价 CLOB）
--   列注释          COMMENT '...'（inline）            COMMENT ON COLUMN ... IS '...'
--   表选项          ENGINE / CHARSET / COLLATE        无
--   标识符大小写    受 lower_case_table_names 影响     不加引号一律转大写
--
-- 未在真实实例验证；首次接入真实达梦请按 docs/decisions/0010 的「未验证清单」逐条确认。
-- ============================================================================
