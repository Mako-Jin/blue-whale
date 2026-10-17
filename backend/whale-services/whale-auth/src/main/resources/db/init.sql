-- =====================================================================
-- BlueWhale 微服务平台 - 认证授权服务（whale-auth）数据库初始化脚本
-- =====================================================================
-- 建议库名   : bluewhale_auth
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci（完整支持中文与 emoji）
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成，无自增）、create_time、update_time、
--              deleted TINYINT（逻辑删除：0 未删除 / 1 已删除，与 MyBatis-Plus
--              logic-delete-field: deleted 配置对齐）
-- 命名风格   : 列名统一下划线风格，与 mybatis-plus map-underscore-to-camel-case 对齐
-- 说明       : 表间关联均为逻辑外键（不建物理 FOREIGN KEY），便于微服务独立演进
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 系统用户表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id              BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    username        VARCHAR(64)   NOT NULL                COMMENT '登录账号（唯一）',
    password        VARCHAR(100)  NOT NULL                COMMENT '登录密码（BCrypt 密文）',
    nickname        VARCHAR(64)   DEFAULT NULL            COMMENT '用户昵称',
    phone           VARCHAR(20)   DEFAULT NULL            COMMENT '手机号',
    email           VARCHAR(128)  DEFAULT NULL            COMMENT '电子邮箱',
    avatar          VARCHAR(255)  DEFAULT NULL            COMMENT '头像URL',
    dept_id         BIGINT        DEFAULT NULL            COMMENT '所属部门ID（逻辑关联 sys_dept.id）',
    status          TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用',
    last_login_time DATETIME      DEFAULT NULL            COMMENT '最后登录时间',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统用户表';

-- ---------------------------------------------------------------------
-- 2. 系统角色表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
    id          BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    role_code   VARCHAR(64)   NOT NULL               COMMENT '角色编码（唯一），如 super_admin',
    role_name   VARCHAR(64)   NOT NULL               COMMENT '角色名称',
    description VARCHAR(255)  DEFAULT NULL           COMMENT '角色描述',
    sort_no     INT           NOT NULL DEFAULT 0     COMMENT '显示顺序（越小越靠前）',
    status      TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 启用 / 0 停用',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统角色表';

-- ---------------------------------------------------------------------
-- 3. 系统权限表（权限点：接口 / 按钮 / 数据 / 属性等）
--    列名与实体 com.bluewhale.auth.entity.SysPermission 对齐
--    （perm_code / perm_name / perm_type / path）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS sys_permission;
CREATE TABLE sys_permission (
    id          BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    parent_id   BIGINT        NOT NULL DEFAULT 0     COMMENT '父权限ID，0 表示顶级（逻辑关联 sys_permission.id）',
    perm_code   VARCHAR(128)  NOT NULL               COMMENT '权限编码（唯一），如 sys:user:list',
    perm_name   VARCHAR(64)   NOT NULL               COMMENT '权限名称，如 查询用户',
    perm_type   VARCHAR(16)   NOT NULL               COMMENT '权限类型：SYSTEM 系统 / MENU 菜单 / API 接口 / BUTTON 按钮 / DATA 数据 / ATTR 属性',
    path        VARCHAR(255)  DEFAULT NULL           COMMENT '接口请求路径（URI），如 /api/v1/users/**',
    method      VARCHAR(16)   DEFAULT NULL           COMMENT 'HTTP 方法：GET / POST / PUT / DELETE / ALL',
    sort_no     INT           NOT NULL DEFAULT 0     COMMENT '显示顺序（越小越靠前）',
    status      TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 启用 / 0 停用',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_perm_code (perm_code),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统权限表';

-- ---------------------------------------------------------------------
-- 4. 系统菜单表（前端导航：目录 / 菜单）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS sys_menu;
CREATE TABLE sys_menu (
    id              BIGINT        NOT NULL              COMMENT '主键ID（雪花算法）',
    parent_id       BIGINT        NOT NULL DEFAULT 0    COMMENT '父菜单ID，0 表示顶级（逻辑关联 sys_menu.id）',
    name            VARCHAR(64)   NOT NULL              COMMENT '菜单名称',
    path            VARCHAR(255)  DEFAULT NULL          COMMENT '前端路由路径，如 /system/user',
    component       VARCHAR(255)  DEFAULT NULL          COMMENT '前端组件路径，如 system/user/index',
    icon            VARCHAR(64)   DEFAULT NULL          COMMENT '菜单图标',
    sort            INT           NOT NULL DEFAULT 0    COMMENT '显示顺序（越小越靠前）',
    menu_type       VARCHAR(16)   NOT NULL DEFAULT 'MENU' COMMENT '菜单类型：DIR 目录 / MENU 菜单',
    permission_code VARCHAR(128)  DEFAULT NULL          COMMENT '关联权限编码（逻辑关联 sys_permission.perm_code）',
    visible         TINYINT       NOT NULL DEFAULT 1    COMMENT '是否可见：1 显示 / 0 隐藏',
    status          TINYINT       NOT NULL DEFAULT 1    COMMENT '状态：1 启用 / 0 停用',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0    COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统菜单表';

-- ---------------------------------------------------------------------
-- 5. 系统部门表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS sys_dept;
CREATE TABLE sys_dept (
    id          BIGINT       NOT NULL               COMMENT '主键ID（雪花算法）',
    parent_id   BIGINT       NOT NULL DEFAULT 0     COMMENT '父部门ID，0 表示顶级（逻辑关联 sys_dept.id）',
    dept_name   VARCHAR(64)  NOT NULL               COMMENT '部门名称',
    sort        INT          NOT NULL DEFAULT 0     COMMENT '显示顺序（越小越靠前）',
    leader      VARCHAR(64)  DEFAULT NULL           COMMENT '负责人姓名',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统部门表';

-- ---------------------------------------------------------------------
-- 6. 用户-角色关联表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
    id          BIGINT   NOT NULL            COMMENT '主键ID（雪花算法）',
    user_id     BIGINT   NOT NULL            COMMENT '用户ID（逻辑关联 sys_user.id）',
    role_id     BIGINT   NOT NULL            COMMENT '角色ID（逻辑关联 sys_role.id）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT  NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户-角色关联表';

-- ---------------------------------------------------------------------
-- 7. 角色-权限关联表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS sys_role_permission;
CREATE TABLE sys_role_permission (
    id            BIGINT   NOT NULL            COMMENT '主键ID（雪花算法）',
    role_id       BIGINT   NOT NULL            COMMENT '角色ID（逻辑关联 sys_role.id）',
    permission_id BIGINT   NOT NULL            COMMENT '权限ID（逻辑关联 sys_permission.id）',
    create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT  NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    KEY idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色-权限关联表';

-- ---------------------------------------------------------------------
-- 8. 用户-权限属性表（ABAC 数据级属性控制）
--    为「用户 + 权限点」附加属性键值对，供鉴权时做数据范围过滤，
--    如 attr_key=deptId / attr_value=100 表示该用户此权限仅可见部门 100 的数据
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS sys_user_permission;
CREATE TABLE sys_user_permission (
    id            BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    user_id       BIGINT        NOT NULL            COMMENT '用户ID（逻辑关联 sys_user.id）',
    permission_id BIGINT        NOT NULL            COMMENT '权限ID（逻辑关联 sys_permission.id）',
    attr_key      VARCHAR(64)   NOT NULL            COMMENT '属性键，如 deptId、dataScope',
    attr_value    VARCHAR(255)  NOT NULL            COMMENT '属性值，如 100、SELF_DEPT',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_perm_attr (user_id, permission_id, attr_key),
    KEY idx_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户-权限属性表（ABAC数据级属性控制）';

-- ---------------------------------------------------------------------
-- 9. 操作审计日志表
--    归属说明：Wave 5.1b 阶段一采用「各服务写各自实例下的审计表」，
--              故审计表随各服务库初始化脚本一同部署。DDL 以
--              docs/design/audit-log.md 与 whale-audit/src/main/resources/db/init.sql 为准，
--              本文件保持一致，禁止单独演进。
--    结构说明：append-only，写入后不允许修改，因此不设 update_time / deleted；
--              id 由雪花算法生成（非自增）；create_time 即操作发生时间。
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS audit_log;
CREATE TABLE audit_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（多租户扩展预留）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名（冗余存储，用户删除后仍可追溯）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-auth',
    operation      VARCHAR(128)  DEFAULT NULL           COMMENT '操作描述，如 创建用户',
    operate_type   VARCHAR(32)   DEFAULT NULL           COMMENT '操作类型：LOGIN/LOGOUT/CREATE/UPDATE/DELETE/QUERY/GRANT/EXPORT/IMPORT/OTHER',
    method         VARCHAR(255)  DEFAULT NULL           COMMENT '请求方法（全限定类名.方法名）',
    request_uri    VARCHAR(255)  DEFAULT NULL           COMMENT '请求URI',
    http_method    VARCHAR(16)   DEFAULT NULL           COMMENT 'HTTP 方法：GET/POST/PUT/DELETE',
    target_type    VARCHAR(64)   DEFAULT NULL           COMMENT '操作对象类型，如 sys_user / sys_role',
    target_id      VARCHAR(64)   DEFAULT NULL           COMMENT '操作对象ID（批量操作为逗号分隔或 JSON 数组）',
    request_params TEXT          NULL                   COMMENT '请求参数（JSON 格式，敏感字段须脱敏后写入）',
    response_code  INT           DEFAULT NULL           COMMENT '响应状态码',
    biz_status     TINYINT       NOT NULL DEFAULT 1     COMMENT '业务结果：1 成功 / 0 失败',
    error_msg      VARCHAR(512)  DEFAULT NULL           COMMENT '失败原因（异常类名 + 消息）',
    ip             VARCHAR(64)   DEFAULT NULL           COMMENT '操作IP地址（兼容 IPv6）',
    user_agent     VARCHAR(512)  DEFAULT NULL           COMMENT '用户代理（浏览器/客户端标识）',
    cost_ms        BIGINT        DEFAULT NULL           COMMENT '接口耗时（毫秒）',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（即操作发生时间）',
    PRIMARY KEY (id),
    KEY idx_trace_id (trace_id),
    KEY idx_user_id (user_id),
    KEY idx_module (module),
    KEY idx_operate_type (operate_type),
    KEY idx_biz_status (biz_status),
    KEY idx_tenant_time (tenant_id, create_time),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作审计日志表';

-- =====================================================================
-- 内置种子数据
-- =====================================================================

-- 内置超级管理员用户（id=1）
-- password 为 BCrypt 密文（由项目内 BCryptPasswordEncoder 生成），明文为 123456。
-- 该密文经 BCryptPasswordEncoder.matches("123456", 密文) 实测校验通过，可用于首次登录。
-- 首次部署后请立即修改默认密码。
INSERT INTO sys_user
    (id, username, password, nickname, phone, email, avatar, dept_id, status, last_login_time)
VALUES
    (1, 'admin', '$2a$10$rzq21So0/Oj02kDlvSr2K.lWaqOwb6TzkgwhuGRKIRGnlJV.F/l8m', '超级管理员', NULL, NULL, NULL, NULL, 1, NULL);

-- 内置超级管理员角色（role_code = super_admin）
INSERT INTO sys_role
    (id, role_code, role_name, description, sort_no, status)
VALUES
    (1, 'super_admin', '超级管理员', '内置超级管理员角色，拥有系统全部权限', 1, 1);

-- admin 用户与超级管理员角色关联
INSERT INTO sys_user_role
    (id, user_id, role_id)
VALUES
    (1, 1, 1);

-- 基础菜单：系统管理目录 + 用户 / 角色 / 菜单 / 部门 / 权限 五个菜单项
INSERT INTO sys_menu
    (id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
VALUES
    (1, 0, '系统管理', '/system', NULL, 'setting', 1, 'DIR', NULL, 1, 1),
    (2, 1, '用户管理', '/system/user', 'system/user/index', 'user', 1, 'MENU', 'sys:user:list', 1, 1),
    (3, 1, '角色管理', '/system/role', 'system/role/index', 'peoples', 2, 'MENU', 'sys:role:list', 1, 1),
    (4, 1, '菜单管理', '/system/menu', 'system/menu/index', 'tree-table', 3, 'MENU', 'sys:menu:list', 1, 1),
    (5, 1, '部门管理', '/system/dept', 'system/dept/index', 'tree', 4, 'MENU', 'sys:dept:list', 1, 1),
    (6, 1, '权限管理', '/system/permission', 'system/permission/index', 'lock', 5, 'MENU', 'sys:permission:list', 1, 1);

-- 权限点：与各控制器 @RequirePermission 注解的 value 一一对应（AUTH-04）
INSERT INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (1, 0, 'sys:user:list', '查询用户', 'API', '/user/**', 'GET', 1, 1),
    (2, 0, 'sys:user:add', '新增用户', 'API', '/user', 'POST', 2, 1),
    (3, 0, 'sys:user:edit', '修改用户', 'API', '/user/**', 'PUT', 3, 1),
    (4, 0, 'sys:user:delete', '删除用户', 'API', '/user/{id}', 'DELETE', 4, 1),
    (5, 0, 'sys:user:assign', '分配用户角色', 'API', '/user/{id}/roles', 'POST', 5, 1),
    (6, 0, 'sys:role:list', '查询角色', 'API', '/role/**', 'GET', 6, 1),
    (7, 0, 'sys:role:add', '新增角色', 'API', '/role', 'POST', 7, 1),
    (8, 0, 'sys:role:edit', '修改角色', 'API', '/role/**', 'PUT', 8, 1),
    (9, 0, 'sys:role:delete', '删除角色', 'API', '/role/{id}', 'DELETE', 9, 1),
    (10, 0, 'sys:role:assign', '分配角色权限', 'API', '/role/{id}/permissions', 'POST', 10, 1),
    (11, 0, 'sys:menu:list', '查询菜单', 'API', '/menu/**', 'GET', 11, 1),
    (12, 0, 'sys:menu:add', '新增菜单', 'API', '/menu', 'POST', 12, 1),
    (13, 0, 'sys:menu:edit', '修改菜单', 'API', '/menu/**', 'PUT', 13, 1),
    (14, 0, 'sys:menu:delete', '删除菜单', 'API', '/menu/{id}', 'DELETE', 14, 1),
    (15, 0, 'sys:permission:list', '查询权限点', 'API', '/permission/**', 'GET', 15, 1),
    (16, 0, 'sys:permission:add', '新增权限点', 'API', '/permission', 'POST', 16, 1),
    (17, 0, 'sys:permission:edit', '修改权限点', 'API', '/permission/**', 'PUT', 17, 1),
    (18, 0, 'sys:permission:delete', '删除权限点', 'API', '/permission/{id}', 'DELETE', 18, 1),
    (19, 0, 'sys:dept:list', '查询部门', 'API', '/dept/**', 'GET', 19, 1),
    (20, 0, 'sys:dept:add', '新增部门', 'API', '/dept', 'POST', 20, 1),
    (21, 0, 'sys:dept:edit', '修改部门', 'API', '/dept/**', 'PUT', 21, 1),
    (22, 0, 'sys:dept:delete', '删除部门', 'API', '/dept/{id}', 'DELETE', 22, 1);
