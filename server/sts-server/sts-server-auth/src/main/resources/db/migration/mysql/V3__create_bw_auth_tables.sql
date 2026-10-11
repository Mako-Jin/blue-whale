-- ============================================================================
-- V3 · 权限域表结构（MySQL 8 方言）
--
-- 与 db/migration/h2/V3__create_bw_auth_tables.sql 结构一一对应，
-- 差别只在方言（对照见文件末尾）。列名三份必须**完全一致** ——
-- 列名一旦分叉，Java 侧的映射就得跟着分叉。
--
-- 版本号取全仓最大号 +1（日志域 V1、行为事件域 V2），详见 docs/decisions/0009。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 租户
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_tenant
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_code    VARCHAR(64)  NOT NULL COMMENT '租户编码，对外稳定标识，全局唯一',
    tenant_name    VARCHAR(128) NOT NULL COMMENT '租户名称',
    parent_id      BIGINT                DEFAULT NULL COMMENT '父租户主键，顶级为 NULL',
    tenant_level   INT                   DEFAULT NULL COMMENT '层级，顶级为 1',
    code_path      VARCHAR(512)          DEFAULT NULL COMMENT '物化路径，如 /1/7/23，用于子树查询',
    has_child      TINYINT               DEFAULT 0 COMMENT '是否有子节点：0 否 1 是',
    status         VARCHAR(16)  NOT NULL COMMENT '状态：ENABLED / DISABLED',
    description    VARCHAR(512)          DEFAULT NULL COMMENT '描述',
    allow_register TINYINT               DEFAULT 0 COMMENT '是否允许自助注册：0 否 1 是',
    allow_add      TINYINT               DEFAULT 0 COMMENT '是否允许下级创建租户：0 否 1 是',

    create_by      BIGINT                DEFAULT NULL COMMENT '创建人用户主键',
    create_time    BIGINT                DEFAULT NULL COMMENT '创建时间（毫秒）',
    update_by      BIGINT                DEFAULT NULL COMMENT '更新人用户主键',
    update_time    BIGINT                DEFAULT NULL COMMENT '更新时间（毫秒）',
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删除',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '租户';

CREATE UNIQUE INDEX uk_bw_auth_tenant_code ON bw_auth_tenant (tenant_code);
CREATE INDEX idx_bw_auth_tenant_parent ON bw_auth_tenant (parent_id);

-- ---------------------------------------------------------------------------
-- 2. 用户（账号主体）
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_user
(
    id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id            BIGINT       NOT NULL COMMENT '所属租户主键',
    username             VARCHAR(64)  NOT NULL COMMENT '登录名，租户内唯一',
    display_name         VARCHAR(128)          DEFAULT NULL COMMENT '显示名 / 姓名',
    email                VARCHAR(128)          DEFAULT NULL COMMENT '邮箱',
    phone                VARCHAR(32)           DEFAULT NULL COMMENT '手机号',
    password_hash        VARCHAR(255)          DEFAULT NULL COMMENT '口令哈希，算法见 password_algorithm',
    password_algorithm   VARCHAR(32)           DEFAULT NULL COMMENT '口令算法标识，如 PBKDF2-SHA256',
    password_updated_at  BIGINT                DEFAULT NULL COMMENT '口令最后修改时间（毫秒）',
    password_expires_at  BIGINT                DEFAULT NULL COMMENT '口令过期时间（毫秒），NULL 表示不过期',
    must_change_password TINYINT               DEFAULT 0 COMMENT '是否强制下次登录改密：0 否 1 是',
    status               VARCHAR(16)  NOT NULL COMMENT '状态：ENABLED / DISABLED',
    failed_attempts      INT                   DEFAULT 0 COMMENT '连续登录失败次数',
    locked_until         BIGINT                DEFAULT NULL COMMENT '锁定至（毫秒），NULL 表示未锁定',
    lock_reason          VARCHAR(255)          DEFAULT NULL COMMENT '锁定原因',
    last_login_at        BIGINT                DEFAULT NULL COMMENT '最后登录时间（毫秒）',
    avatar_url           VARCHAR(512)          DEFAULT NULL COMMENT '头像地址',
    mfa_enabled          TINYINT               DEFAULT 0 COMMENT '是否启用多因子：0 否 1 是',
    mfa_type             VARCHAR(16)           DEFAULT NULL COMMENT '多因子类型，如 TOTP / SMS',

    create_by            BIGINT                DEFAULT NULL COMMENT '创建人用户主键',
    create_time          BIGINT                DEFAULT NULL COMMENT '创建时间（毫秒）',
    update_by            BIGINT                DEFAULT NULL COMMENT '更新人用户主键',
    update_time          BIGINT                DEFAULT NULL COMMENT '更新时间（毫秒）',
    deleted              TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删除',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户（账号主体）';

CREATE UNIQUE INDEX uk_bw_auth_user_tenant_username ON bw_auth_user (tenant_id, username);
CREATE INDEX idx_bw_auth_user_tenant_email ON bw_auth_user (tenant_id, email);
CREATE INDEX idx_bw_auth_user_tenant_phone ON bw_auth_user (tenant_id, phone);

-- ---------------------------------------------------------------------------
-- 3. 组织 / 部门
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_organization
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id     BIGINT       NOT NULL COMMENT '所属租户主键',
    org_code      VARCHAR(64)  NOT NULL COMMENT '组织编码，租户内唯一',
    org_name      VARCHAR(128) NOT NULL COMMENT '组织名称',
    full_name     VARCHAR(512)          DEFAULT NULL COMMENT '全称',
    parent_id     BIGINT                DEFAULT NULL COMMENT '父组织主键，顶级为 NULL',
    org_level     INT                   DEFAULT NULL COMMENT '层级，顶级为 1',
    code_path     VARCHAR(512)          DEFAULT NULL COMMENT '物化路径，用于子树查询',
    has_child     TINYINT               DEFAULT 0 COMMENT '是否有子节点：0 否 1 是',
    org_type      VARCHAR(32)           DEFAULT NULL COMMENT '组织类型：COMPANY / DEPT / TEAM ...',
    manager_id    BIGINT                DEFAULT NULL COMMENT '负责人用户主键',
    manager_name  VARCHAR(128)          DEFAULT NULL COMMENT '负责人姓名（冗余，便于列表展示）',
    sort_no       INT                   DEFAULT 0 COMMENT '同级排序号',
    status        VARCHAR(16)           DEFAULT NULL COMMENT '状态：ENABLED / DISABLED',
    description   VARCHAR(512)          DEFAULT NULL COMMENT '描述',
    contact_name  VARCHAR(128)          DEFAULT NULL COMMENT '联系人',
    contact_phone VARCHAR(32)           DEFAULT NULL COMMENT '联系电话',
    contact_email VARCHAR(128)          DEFAULT NULL COMMENT '联系邮箱',

    create_by     BIGINT                DEFAULT NULL COMMENT '创建人用户主键',
    create_time   BIGINT                DEFAULT NULL COMMENT '创建时间（毫秒）',
    update_by     BIGINT                DEFAULT NULL COMMENT '更新人用户主键',
    update_time   BIGINT                DEFAULT NULL COMMENT '更新时间（毫秒）',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删除',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '组织 / 部门';

CREATE UNIQUE INDEX uk_bw_auth_org_tenant_code ON bw_auth_organization (tenant_id, org_code);
CREATE INDEX idx_bw_auth_org_parent ON bw_auth_organization (parent_id);
CREATE INDEX idx_bw_auth_org_tenant ON bw_auth_organization (tenant_id);

-- ---------------------------------------------------------------------------
-- 4. 角色
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_role
(
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id        BIGINT       NOT NULL COMMENT '所属租户主键',
    role_code        VARCHAR(64)  NOT NULL COMMENT '角色编码，租户内唯一',
    role_name        VARCHAR(128) NOT NULL COMMENT '角色名称',
    parent_id        BIGINT                DEFAULT NULL COMMENT '父角色主键，用于角色继承',
    role_level       INT                   DEFAULT NULL COMMENT '角色层级',
    category         VARCHAR(32)           DEFAULT NULL COMMENT '角色分类：SYSTEM / BUSINESS / CUSTOM',
    is_default       TINYINT               DEFAULT 0 COMMENT '是否默认角色（新用户自动授予）：0 否 1 是',
    inherit_strategy VARCHAR(32)           DEFAULT NULL COMMENT '继承策略：NONE / INHERIT / OVERRIDE',
    data_scope       VARCHAR(32)           DEFAULT NULL COMMENT '数据范围：ALL / DEPT / DEPT_AND_CHILD / SELF / CUSTOM',
    filter_json      TEXT                  DEFAULT NULL COMMENT '自定义数据范围过滤条件的 JSON',
    sort_no          INT                   DEFAULT 0 COMMENT '排序号',
    status           VARCHAR(16)           DEFAULT NULL COMMENT '状态：ENABLED / DISABLED',
    description      VARCHAR(512)          DEFAULT NULL COMMENT '描述',

    create_by        BIGINT                DEFAULT NULL COMMENT '创建人用户主键',
    create_time      BIGINT                DEFAULT NULL COMMENT '创建时间（毫秒）',
    update_by        BIGINT                DEFAULT NULL COMMENT '更新人用户主键',
    update_time      BIGINT                DEFAULT NULL COMMENT '更新时间（毫秒）',
    deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删除',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色';

CREATE UNIQUE INDEX uk_bw_auth_role_tenant_code ON bw_auth_role (tenant_id, role_code);
CREATE INDEX idx_bw_auth_role_parent ON bw_auth_role (parent_id);
CREATE INDEX idx_bw_auth_role_tenant ON bw_auth_role (tenant_id);

-- ---------------------------------------------------------------------------
-- 5. 用户组
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_user_group
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id      BIGINT       NOT NULL COMMENT '所属租户主键',
    group_code     VARCHAR(64)  NOT NULL COMMENT '用户组编码，租户内唯一',
    group_name     VARCHAR(128) NOT NULL COMMENT '用户组名称',
    parent_id      BIGINT                DEFAULT NULL COMMENT '父用户组主键',
    category       VARCHAR(32)           DEFAULT NULL COMMENT '分组方式：STATIC / DYNAMIC',
    filter_json    TEXT                  DEFAULT NULL COMMENT '动态成员的过滤条件 JSON',
    org_scope_json TEXT                  DEFAULT NULL COMMENT '组织范围限制的 JSON',
    is_default     TINYINT               DEFAULT 0 COMMENT '是否默认用户组：0 否 1 是',
    is_enabled     TINYINT               DEFAULT 1 COMMENT '是否启用：0 否 1 是',
    sort_no        INT                   DEFAULT 0 COMMENT '排序号',
    description    VARCHAR(512)          DEFAULT NULL COMMENT '描述',

    create_by      BIGINT                DEFAULT NULL COMMENT '创建人用户主键',
    create_time    BIGINT                DEFAULT NULL COMMENT '创建时间（毫秒）',
    update_by      BIGINT                DEFAULT NULL COMMENT '更新人用户主键',
    update_time    BIGINT                DEFAULT NULL COMMENT '更新时间（毫秒）',
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删除',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户组';

CREATE UNIQUE INDEX uk_bw_auth_user_group_tenant_code ON bw_auth_user_group (tenant_id, group_code);
CREATE INDEX idx_bw_auth_user_group_parent ON bw_auth_user_group (parent_id);
CREATE INDEX idx_bw_auth_user_group_tenant ON bw_auth_user_group (tenant_id);

-- ---------------------------------------------------------------------------
-- 6. 资源（菜单 / 页面 / 按钮 / API）
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_resource
(
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id         BIGINT                DEFAULT NULL COMMENT '所属租户；平台级资源为 NULL',
    resource_code     VARCHAR(128) NOT NULL COMMENT '资源标识，即 @RequiresPermission 的值，全局唯一',
    resource_name     VARCHAR(128) NOT NULL COMMENT '资源名称',
    resource_type     VARCHAR(32)  NOT NULL COMMENT '类型：SYSTEM / SERVER / SERVICE / MODULE / MENU / PAGE / API / BUTTON / DATA',
    resource_category VARCHAR(64)           DEFAULT NULL COMMENT '业务分类',
    parent_code       VARCHAR(128)          DEFAULT NULL COMMENT '父资源标识',
    request_url       VARCHAR(512)          DEFAULT NULL COMMENT 'API 类资源的请求地址',
    request_method    VARCHAR(16)           DEFAULT NULL COMMENT 'API 类资源的请求方法',
    icon              VARCHAR(128)          DEFAULT NULL COMMENT '图标',
    sort_no           INT                   DEFAULT 0 COMMENT '排序号',
    is_enabled        TINYINT               DEFAULT 1 COMMENT '是否启用：0 否 1 是',
    is_whitelist      TINYINT               DEFAULT 0 COMMENT '是否免鉴权白名单：0 否 1 是',
    is_deprecated     TINYINT               DEFAULT 0 COMMENT '是否已废弃：0 否 1 是',
    resource_version  VARCHAR(32)           DEFAULT NULL COMMENT '资源版本号',
    description       VARCHAR(512)          DEFAULT NULL COMMENT '描述',

    create_by         BIGINT                DEFAULT NULL COMMENT '创建人用户主键',
    create_time       BIGINT                DEFAULT NULL COMMENT '创建时间（毫秒）',
    update_by         BIGINT                DEFAULT NULL COMMENT '更新人用户主键',
    update_time       BIGINT                DEFAULT NULL COMMENT '更新时间（毫秒）',
    deleted           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删除',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '资源（菜单 / 页面 / 按钮 / API）';

CREATE UNIQUE INDEX uk_bw_auth_resource_code ON bw_auth_resource (resource_code);
CREATE INDEX idx_bw_auth_resource_parent ON bw_auth_resource (parent_code);
CREATE INDEX idx_bw_auth_resource_type ON bw_auth_resource (resource_type);

-- ---------------------------------------------------------------------------
-- 7. 角色-成员（用户或用户组，多态）
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_rel_role_member
(
    id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id      BIGINT      NOT NULL COMMENT '所属租户主键',
    role_id        BIGINT      NOT NULL COMMENT '角色主键',
    member_id      BIGINT      NOT NULL COMMENT '成员主键（用户或用户组，由 member_type 决定）',
    member_type    VARCHAR(16) NOT NULL COMMENT '成员类型：USER / GROUP',
    effective_from BIGINT               DEFAULT NULL COMMENT '生效起始时间（毫秒）',
    effective_to   BIGINT               DEFAULT NULL COMMENT '生效结束时间（毫秒），NULL 表示长期',

    create_by      BIGINT               DEFAULT NULL COMMENT '创建人用户主键',
    create_time    BIGINT               DEFAULT NULL COMMENT '创建时间（毫秒）',
    update_by      BIGINT               DEFAULT NULL COMMENT '更新人用户主键',
    update_time    BIGINT               DEFAULT NULL COMMENT '更新时间（毫秒）',
    deleted        TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删除',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色-成员（用户 / 用户组）';

CREATE INDEX idx_bw_auth_rel_role_member_role ON bw_auth_rel_role_member (role_id);
CREATE INDEX idx_bw_auth_rel_role_member_member ON bw_auth_rel_role_member (member_type, member_id);
CREATE INDEX idx_bw_auth_rel_role_member_tenant ON bw_auth_rel_role_member (tenant_id);

-- ---------------------------------------------------------------------------
-- 8. 角色-资源
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_rel_role_resource
(
    id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id      BIGINT      NOT NULL COMMENT '所属租户主键',
    role_id        BIGINT      NOT NULL COMMENT '角色主键',
    resource_id    BIGINT      NOT NULL COMMENT '资源主键',
    effect         VARCHAR(8)           DEFAULT 'ALLOW' COMMENT '效果：ALLOW / DENY',
    priority       INT                  DEFAULT 0 COMMENT '优先级，DENY 优先于 ALLOW 时应更高',
    effective_from BIGINT               DEFAULT NULL COMMENT '生效起始时间（毫秒）',
    effective_to   BIGINT               DEFAULT NULL COMMENT '生效结束时间（毫秒）',

    create_by      BIGINT               DEFAULT NULL COMMENT '创建人用户主键',
    create_time    BIGINT               DEFAULT NULL COMMENT '创建时间（毫秒）',
    update_by      BIGINT               DEFAULT NULL COMMENT '更新人用户主键',
    update_time    BIGINT               DEFAULT NULL COMMENT '更新时间（毫秒）',
    deleted        TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删除',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色-资源（RBAC 授权关系）';

CREATE INDEX idx_bw_auth_rel_role_resource_role ON bw_auth_rel_role_resource (role_id);
CREATE INDEX idx_bw_auth_rel_role_resource_resource ON bw_auth_rel_role_resource (resource_id);
CREATE INDEX idx_bw_auth_rel_role_resource_tenant ON bw_auth_rel_role_resource (tenant_id);

-- ---------------------------------------------------------------------------
-- 9. 角色-资源实例（数据权限）
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_rel_role_resource_instance
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id      BIGINT       NOT NULL COMMENT '所属租户主键',
    role_id        BIGINT       NOT NULL COMMENT '角色主键',
    resource_id    BIGINT       NOT NULL COMMENT '资源主键',
    resource_type  VARCHAR(32)           DEFAULT NULL COMMENT '资源类型',
    instance_id    VARCHAR(128)          DEFAULT NULL COMMENT '数据实例标识',
    instance_name  VARCHAR(255)          DEFAULT NULL COMMENT '数据实例名称',
    actions_json   TEXT                  DEFAULT NULL COMMENT '允许的操作集合（JSON 数组）',
    filter_json    TEXT                  DEFAULT NULL COMMENT '数据过滤条件（JSON）',
    effective_from BIGINT                DEFAULT NULL COMMENT '生效起始时间（毫秒）',
    effective_to   BIGINT                DEFAULT NULL COMMENT '生效结束时间（毫秒）',

    create_by      BIGINT                DEFAULT NULL COMMENT '创建人用户主键',
    create_time    BIGINT                DEFAULT NULL COMMENT '创建时间（毫秒）',
    update_by      BIGINT                DEFAULT NULL COMMENT '更新人用户主键',
    update_time    BIGINT                DEFAULT NULL COMMENT '更新时间（毫秒）',
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删除',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色-资源实例（数据权限）';

CREATE INDEX idx_bw_auth_rel_rri_role ON bw_auth_rel_role_resource_instance (role_id);
CREATE INDEX idx_bw_auth_rel_rri_resource ON bw_auth_rel_role_resource_instance (resource_id);

-- ---------------------------------------------------------------------------
-- 10. 组织-用户
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_rel_org_user
(
    id                  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id           BIGINT      NOT NULL COMMENT '所属租户主键',
    organization_id     BIGINT      NOT NULL COMMENT '组织主键',
    user_id             BIGINT      NOT NULL COMMENT '用户主键',
    is_primary_position TINYINT              DEFAULT 0 COMMENT '是否主岗：0 否 1 是',
    position_name       VARCHAR(128)         DEFAULT NULL COMMENT '岗位名称',
    relation_type       VARCHAR(32)          DEFAULT NULL COMMENT '关系类型：MEMBER / LEADER ...',
    effective_from      BIGINT               DEFAULT NULL COMMENT '生效起始时间（毫秒）',
    effective_to        BIGINT               DEFAULT NULL COMMENT '生效结束时间（毫秒）',

    create_by           BIGINT               DEFAULT NULL COMMENT '创建人用户主键',
    create_time         BIGINT               DEFAULT NULL COMMENT '创建时间（毫秒）',
    update_by           BIGINT               DEFAULT NULL COMMENT '更新人用户主键',
    update_time         BIGINT               DEFAULT NULL COMMENT '更新时间（毫秒）',
    deleted             TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删除',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '组织-用户';

CREATE INDEX idx_bw_auth_rel_org_user_org ON bw_auth_rel_org_user (organization_id);
CREATE INDEX idx_bw_auth_rel_org_user_user ON bw_auth_rel_org_user (user_id);

-- ---------------------------------------------------------------------------
-- 11. 用户组-成员（用户或用户组，多态）
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_rel_user_group_member
(
    id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id      BIGINT      NOT NULL COMMENT '所属租户主键',
    user_group_id  BIGINT      NOT NULL COMMENT '用户组主键',
    member_id      BIGINT      NOT NULL COMMENT '成员主键（用户或用户组）',
    member_type    VARCHAR(16) NOT NULL COMMENT '成员类型：USER / GROUP',
    effective_from BIGINT               DEFAULT NULL COMMENT '生效起始时间（毫秒）',
    effective_to   BIGINT               DEFAULT NULL COMMENT '生效结束时间（毫秒）',

    create_by      BIGINT               DEFAULT NULL COMMENT '创建人用户主键',
    create_time    BIGINT               DEFAULT NULL COMMENT '创建时间（毫秒）',
    update_by      BIGINT               DEFAULT NULL COMMENT '更新人用户主键',
    update_time    BIGINT               DEFAULT NULL COMMENT '更新时间（毫秒）',
    deleted        TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删除',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户组-成员（用户 / 用户组）';

CREATE INDEX idx_bw_auth_rel_ugm_group ON bw_auth_rel_user_group_member (user_group_id);
CREATE INDEX idx_bw_auth_rel_ugm_member ON bw_auth_rel_user_group_member (member_type, member_id);

-- ---------------------------------------------------------------------------
-- 12. 刷新令牌
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_refresh_token
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id  BIGINT       NOT NULL COMMENT '所属租户主键',
    user_id    BIGINT       NOT NULL COMMENT '用户主键',
    jti        VARCHAR(64)  NOT NULL COMMENT '令牌唯一标识（JWT ID）',
    token_hash VARCHAR(128) NOT NULL COMMENT '令牌摘要：只存哈希，库泄露也不能直接拿去用',
    issued_at  BIGINT       NOT NULL COMMENT '签发时间（毫秒）',
    expires_at BIGINT       NOT NULL COMMENT '过期时间（毫秒）',
    revoked    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已撤销：0 否 1 是',
    revoked_at BIGINT                DEFAULT NULL COMMENT '撤销时间（毫秒）',
    client_ip  VARCHAR(64)           DEFAULT NULL COMMENT '签发时的客户端 IP',
    user_agent VARCHAR(512)          DEFAULT NULL COMMENT '签发时的客户端 UA',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '刷新令牌';

CREATE UNIQUE INDEX uk_bw_auth_refresh_token_jti ON bw_auth_refresh_token (jti);
CREATE INDEX idx_bw_auth_refresh_token_user ON bw_auth_refresh_token (user_id);
CREATE INDEX idx_bw_auth_refresh_token_expires ON bw_auth_refresh_token (expires_at);

-- ============================================================================
-- 保留字核对（新增列前必须复查，且 MySQL 与达梦的结论**不可互推**）
--
-- MySQL 8 保留字中本项目刻意避开的：release / rank / groups / system / window /
--   interval / desc / order / key / value / type
--   · 排序字段用 sort_no 而非 rank（rank 是 MySQL 8 保留字，见窗口函数）
--   · 层级字段用 tenant_level / org_level / role_level 而非裸 level
--     （LEVEL 是达梦的关键字，虽是"非保留"可作列名，但前缀化后彻底无歧义）
--   · 资源版本用 resource_version 而非 version —— 后者会与 MyBatis-Plus 的
--     @Version 乐观锁字段语义混淆（拦截器是全局注册的）
--   · 效果字段用 effect（不是 MySQL 保留字）
--
-- 达梦侧核对：上述列名中 level / version 在达梦属关键字但非保留，且已被前缀化规避；
--   其余列名均不在 V$RESERVED_WORDS 的 RESERVED='Y' 集合内。
--   仍建议在真实达梦实例上重跑：
--     SELECT KEYWORD FROM V$RESERVED_WORDS WHERE RESERVED='Y'
-- ============================================================================
