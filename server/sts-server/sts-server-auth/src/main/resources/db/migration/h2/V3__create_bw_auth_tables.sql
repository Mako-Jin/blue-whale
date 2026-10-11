-- ============================================================================
-- V3 · 权限域表结构（H2 方言）
--
-- 与 db/migration/mysql/V3__create_bw_auth_tables.sql 结构一一对应，
-- 差别只在方言（对照见文件末尾）。
--
-- 【为什么是 V3】Flyway 的 locations 是全模块共享的 classpath:db/migration/<vendor>，
-- 版本号**全仓唯一**：日志域占 V1、行为事件域占 V2，权限域接 V3。
-- 若这里从 V1 起编号，Flyway 会在启动阶段直接失败：
--   Found more than one migration with version 1
-- 详见 docs/decisions/0009 的「补充」章节。
--
-- 【命名】表名统一 bw_ 前缀（与 bw_client_log / bw_client_event 一致），
-- 域命名空间用 auth_：本域既管身份（identity）也管访问（access），
-- 用 auth_ 一个词覆盖两者，避免既叫 iam_ 又叫 permission_ 的分裂。
--
-- 【主键】沿用本仓既有形态：BIGINT 自增。参照实现用的是字符串雪花 ID，
-- 这里改回自增并在 ADR-0016 记录理由 —— 单库阶段自增更简单，
-- 对外稳定标识交给各表的 *_code 业务编码字段承担。
--
-- 【审计与逻辑删除】统一 5 列：create_by / create_time / update_by / update_time / deleted。
--   时间一律 BIGINT 毫秒（与 bw_client_log 一致）：**不碰日期类型**，
--   从而绕开三套数据库在时区与日期语义上的差异 —— 这是本仓已验证的一条省事做法。
--   deleted 语义**全仓统一为 0=正常、1=已删除**，并由 @TableLogic 承接。
--   参照实现在这一点上自相矛盾（brand_config 是 0=未删，user_info 是 1=未删），
--   那是迁移时必须修掉的缺陷，不能照搬。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 租户
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_tenant
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_code   VARCHAR(64)  NOT NULL,
    tenant_name   VARCHAR(128) NOT NULL,
    parent_id     BIGINT,
    tenant_level  INT,
    code_path     VARCHAR(512),
    has_child     TINYINT,
    status        VARCHAR(16)  NOT NULL,
    description   VARCHAR(512),
    allow_register TINYINT,
    allow_add      TINYINT,

    create_by     BIGINT,
    create_time   BIGINT,
    update_by     BIGINT,
    update_time   BIGINT,
    deleted       TINYINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_bw_auth_tenant_code ON bw_auth_tenant (tenant_code);
CREATE INDEX idx_bw_auth_tenant_parent ON bw_auth_tenant (parent_id);

-- ---------------------------------------------------------------------------
-- 2. 用户（账号主体）
--    密码等凭证直接落在本表：参照实现把口令同时冗余在 user_info 与
--    rel_tenant_users 两处，两副本迟早不一致 —— 这里只保留一份。
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_user
(
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id              BIGINT       NOT NULL,
    username               VARCHAR(64)  NOT NULL,
    display_name           VARCHAR(128),
    email                  VARCHAR(128),
    phone                  VARCHAR(32),
    password_hash          VARCHAR(255),
    password_algorithm     VARCHAR(32),
    password_updated_at    BIGINT,
    password_expires_at    BIGINT,
    must_change_password   TINYINT,
    status                 VARCHAR(16)  NOT NULL,
    failed_attempts        INT,
    locked_until           BIGINT,
    lock_reason            VARCHAR(255),
    last_login_at          BIGINT,
    avatar_url             VARCHAR(512),
    mfa_enabled            TINYINT,
    mfa_type               VARCHAR(16),

    create_by              BIGINT,
    create_time            BIGINT,
    update_by              BIGINT,
    update_time            BIGINT,
    deleted                TINYINT      NOT NULL DEFAULT 0
);

-- 用户名在**租户内**唯一，不是全库唯一 —— 多租户下不同租户可以有同名账号
CREATE UNIQUE INDEX uk_bw_auth_user_tenant_username ON bw_auth_user (tenant_id, username);
CREATE INDEX idx_bw_auth_user_tenant_email ON bw_auth_user (tenant_id, email);
CREATE INDEX idx_bw_auth_user_tenant_phone ON bw_auth_user (tenant_id, phone);

-- ---------------------------------------------------------------------------
-- 3. 组织 / 部门
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_organization
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id      BIGINT       NOT NULL,
    org_code       VARCHAR(64)  NOT NULL,
    org_name       VARCHAR(128) NOT NULL,
    full_name      VARCHAR(512),
    parent_id      BIGINT,
    org_level      INT,
    code_path      VARCHAR(512),
    has_child      TINYINT,
    org_type       VARCHAR(32),
    manager_id     BIGINT,
    manager_name   VARCHAR(128),
    sort_no        INT,
    status         VARCHAR(16),
    description    VARCHAR(512),
    contact_name   VARCHAR(128),
    contact_phone  VARCHAR(32),
    contact_email  VARCHAR(128),

    create_by      BIGINT,
    create_time    BIGINT,
    update_by      BIGINT,
    update_time    BIGINT,
    deleted        TINYINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_bw_auth_org_tenant_code ON bw_auth_organization (tenant_id, org_code);
CREATE INDEX idx_bw_auth_org_parent ON bw_auth_organization (parent_id);
CREATE INDEX idx_bw_auth_org_tenant ON bw_auth_organization (tenant_id);

-- ---------------------------------------------------------------------------
-- 4. 角色
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_role
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL,
    role_code         VARCHAR(64)  NOT NULL,
    role_name         VARCHAR(128) NOT NULL,
    parent_id         BIGINT,
    role_level        INT,
    category          VARCHAR(32),
    is_default        TINYINT,
    inherit_strategy  VARCHAR(32),
    data_scope        VARCHAR(32),
    filter_json       TEXT,
    sort_no           INT,
    status            VARCHAR(16),
    description       VARCHAR(512),

    create_by         BIGINT,
    create_time       BIGINT,
    update_by         BIGINT,
    update_time       BIGINT,
    deleted           TINYINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_bw_auth_role_tenant_code ON bw_auth_role (tenant_id, role_code);
CREATE INDEX idx_bw_auth_role_parent ON bw_auth_role (parent_id);
CREATE INDEX idx_bw_auth_role_tenant ON bw_auth_role (tenant_id);

-- ---------------------------------------------------------------------------
-- 5. 用户组
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_user_group
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    group_code      VARCHAR(64)  NOT NULL,
    group_name      VARCHAR(128) NOT NULL,
    parent_id       BIGINT,
    category        VARCHAR(32),
    filter_json     TEXT,
    org_scope_json  TEXT,
    is_default      TINYINT,
    is_enabled      TINYINT,
    sort_no         INT,
    description     VARCHAR(512),

    create_by       BIGINT,
    create_time     BIGINT,
    update_by       BIGINT,
    update_time     BIGINT,
    deleted         TINYINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_bw_auth_user_group_tenant_code ON bw_auth_user_group (tenant_id, group_code);
CREATE INDEX idx_bw_auth_user_group_parent ON bw_auth_user_group (parent_id);
CREATE INDEX idx_bw_auth_user_group_tenant ON bw_auth_user_group (tenant_id);

-- ---------------------------------------------------------------------------
-- 6. 资源（菜单 / 页面 / 按钮 / API）—— 鉴权的判定对象
--    resource_code 就是 @RequiresPermission 里写的那个权限标识，全库唯一。
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_resource
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id          BIGINT,
    resource_code      VARCHAR(128) NOT NULL,
    resource_name      VARCHAR(128) NOT NULL,
    resource_type      VARCHAR(32)  NOT NULL,
    resource_category  VARCHAR(64),
    parent_code        VARCHAR(128),
    request_url        VARCHAR(512),
    request_method     VARCHAR(16),
    icon               VARCHAR(128),
    sort_no            INT,
    is_enabled         TINYINT,
    is_whitelist       TINYINT,
    is_deprecated      TINYINT,
    resource_version   VARCHAR(32),
    description        VARCHAR(512),

    create_by          BIGINT,
    create_time        BIGINT,
    update_by          BIGINT,
    update_time        BIGINT,
    deleted            TINYINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_bw_auth_resource_code ON bw_auth_resource (resource_code);
CREATE INDEX idx_bw_auth_resource_parent ON bw_auth_resource (parent_code);
CREATE INDEX idx_bw_auth_resource_type ON bw_auth_resource (resource_type);

-- ---------------------------------------------------------------------------
-- 7. 角色-成员（用户或用户组，多态）
--    参照实现里用户与角色之间**没有**独立的角色-用户表，一律走这张多态表，
--    这里沿用它：一张表同时表达"角色授予用户"与"角色授予用户组"，
--    减少一类表，也让"我为什么有权限"的追溯只有一条路径。
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_rel_role_member
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id      BIGINT      NOT NULL,
    role_id        BIGINT      NOT NULL,
    member_id      BIGINT      NOT NULL,
    member_type    VARCHAR(16) NOT NULL,
    effective_from BIGINT,
    effective_to   BIGINT,

    create_by      BIGINT,
    create_time    BIGINT,
    update_by      BIGINT,
    update_time    BIGINT,
    deleted        TINYINT     NOT NULL DEFAULT 0
);

CREATE INDEX idx_bw_auth_rel_role_member_role ON bw_auth_rel_role_member (role_id);
CREATE INDEX idx_bw_auth_rel_role_member_member ON bw_auth_rel_role_member (member_type, member_id);
CREATE INDEX idx_bw_auth_rel_role_member_tenant ON bw_auth_rel_role_member (tenant_id);

-- ---------------------------------------------------------------------------
-- 8. 角色-资源（RBAC 的核心授权关系）
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_rel_role_resource
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id      BIGINT      NOT NULL,
    role_id        BIGINT      NOT NULL,
    resource_id    BIGINT      NOT NULL,
    effect         VARCHAR(8),
    priority       INT,
    effective_from BIGINT,
    effective_to   BIGINT,

    create_by      BIGINT,
    create_time    BIGINT,
    update_by      BIGINT,
    update_time    BIGINT,
    deleted        TINYINT     NOT NULL DEFAULT 0
);

CREATE INDEX idx_bw_auth_rel_role_resource_role ON bw_auth_rel_role_resource (role_id);
CREATE INDEX idx_bw_auth_rel_role_resource_resource ON bw_auth_rel_role_resource (resource_id);
CREATE INDEX idx_bw_auth_rel_role_resource_tenant ON bw_auth_rel_role_resource (tenant_id);

-- ---------------------------------------------------------------------------
-- 9. 角色-资源实例（数据权限：把授权细化到"某条数据"）
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_rel_role_resource_instance
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id      BIGINT       NOT NULL,
    role_id        BIGINT       NOT NULL,
    resource_id    BIGINT       NOT NULL,
    resource_type  VARCHAR(32),
    instance_id    VARCHAR(128),
    instance_name  VARCHAR(255),
    actions_json   TEXT,
    filter_json    TEXT,
    effective_from BIGINT,
    effective_to   BIGINT,

    create_by      BIGINT,
    create_time    BIGINT,
    update_by      BIGINT,
    update_time    BIGINT,
    deleted        TINYINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_bw_auth_rel_rri_role ON bw_auth_rel_role_resource_instance (role_id);
CREATE INDEX idx_bw_auth_rel_rri_resource ON bw_auth_rel_role_resource_instance (resource_id);

-- ---------------------------------------------------------------------------
-- 10. 组织-用户（含主岗标记）
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_rel_org_user
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT      NOT NULL,
    organization_id     BIGINT      NOT NULL,
    user_id             BIGINT      NOT NULL,
    is_primary_position TINYINT,
    position_name       VARCHAR(128),
    relation_type       VARCHAR(32),
    effective_from      BIGINT,
    effective_to        BIGINT,

    create_by           BIGINT,
    create_time         BIGINT,
    update_by           BIGINT,
    update_time         BIGINT,
    deleted             TINYINT     NOT NULL DEFAULT 0
);

CREATE INDEX idx_bw_auth_rel_org_user_org ON bw_auth_rel_org_user (organization_id);
CREATE INDEX idx_bw_auth_rel_org_user_user ON bw_auth_rel_org_user (user_id);

-- ---------------------------------------------------------------------------
-- 11. 用户组-成员（用户或用户组，多态）
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_rel_user_group_member
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id      BIGINT      NOT NULL,
    user_group_id  BIGINT      NOT NULL,
    member_id      BIGINT      NOT NULL,
    member_type    VARCHAR(16) NOT NULL,
    effective_from BIGINT,
    effective_to   BIGINT,

    create_by      BIGINT,
    create_time    BIGINT,
    update_by      BIGINT,
    update_time    BIGINT,
    deleted        TINYINT     NOT NULL DEFAULT 0
);

CREATE INDEX idx_bw_auth_rel_ugm_group ON bw_auth_rel_user_group_member (user_group_id);
CREATE INDEX idx_bw_auth_rel_ugm_member ON bw_auth_rel_user_group_member (member_type, member_id);

-- ---------------------------------------------------------------------------
-- 12. 刷新令牌
--     刻意**不继承审计基类**：令牌只有"签发/撤销"两种状态，
--     没有"更新人"，也不该被逻辑删除（撤销是状态变更，不是删除）。
--     与 bw_auth_user 的物理形态一致：都存 BIGINT 毫秒。
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_refresh_token
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    jti         VARCHAR(64)  NOT NULL,
    token_hash  VARCHAR(128) NOT NULL,
    issued_at   BIGINT       NOT NULL,
    expires_at  BIGINT       NOT NULL,
    revoked     TINYINT      NOT NULL DEFAULT 0,
    revoked_at  BIGINT,
    client_ip   VARCHAR(64),
    user_agent  VARCHAR(512)
);

CREATE UNIQUE INDEX uk_bw_auth_refresh_token_jti ON bw_auth_refresh_token (jti);
CREATE INDEX idx_bw_auth_refresh_token_user ON bw_auth_refresh_token (user_id);
CREATE INDEX idx_bw_auth_refresh_token_expires ON bw_auth_refresh_token (expires_at);

-- ============================================================================
-- 方言差异（相对 mysql/V3__create_bw_auth_tables.sql）
--   1. H2 无列注释与存储引擎选项，列语义以本文件的 -- 注释与实体字段注释为准
--   2. H2 的 TEXT 等价 CLOB；MySQL 是 TEXT
--   3. H2 跑在 MODE=MySQL 下，AUTO_INCREMENT / TINYINT 语义与 MySQL 接近，
--      但**兼容模式不等于等价** —— 保留字与索引实现仍有差异，
--      所以三套脚本必须分厂商维护，不能合一份
--   4. 三份脚本都刻意不写 IF NOT EXISTS：Flyway 用 schema_history 保证只执行一次，
--      加上它只会把"表已存在但结构不对"静默吞掉，那是比建表失败更糟的结果
-- ============================================================================
