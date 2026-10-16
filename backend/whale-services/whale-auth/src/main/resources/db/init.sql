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

-- =====================================================================
-- 内置种子数据
-- =====================================================================

-- 内置超级管理员用户（id=1）
-- password 为 BCrypt 占位密文，明文为 123456（BCrypt 单向加密，不可反解）。
-- 该哈希为业界通用示例占位串，首次部署后请立即修改默认密码。
INSERT INTO sys_user
    (id, username, password, nickname, phone, email, avatar, dept_id, status, last_login_time)
VALUES
    (1, 'admin', '$2a$10$mE.qmcV0mFU5N5Kh6LqfUuRxvWuJrz9vanl4GHlJymxLB7kvNiYqa', '超级管理员', NULL, NULL, NULL, NULL, 1, NULL);

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

-- 基础菜单：系统管理目录 + 用户 / 角色 / 菜单 三个菜单项
INSERT INTO sys_menu
    (id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
VALUES
    (1, 0, '系统管理', '/system', NULL, 'setting', 1, 'DIR', NULL, 1, 1),
    (2, 1, '用户管理', '/system/user', 'system/user/index', 'user', 1, 'MENU', 'sys:user:list', 1, 1),
    (3, 1, '角色管理', '/system/role', 'system/role/index', 'peoples', 2, 'MENU', 'sys:role:list', 1, 1),
    (4, 1, '菜单管理', '/system/menu', 'system/menu/index', 'tree-table', 3, 'MENU', 'sys:menu:list', 1, 1);
