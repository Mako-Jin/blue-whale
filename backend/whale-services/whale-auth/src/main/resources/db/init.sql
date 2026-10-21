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
    result_body    TEXT          NULL                   COMMENT '返回体（JSON，脱敏 + 截断；recordResult=true 时写入）',
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

-- ---------------------------------------------------------------------
-- 10. 多通道观测日志表（REQ-SEC-006，Wave 5.10）
--     归属说明：与 audit_log 一致，阶段一「各服务写各自实例下的日志表」，
--               故 4 张日志表随各服务库初始化脚本一同部署；DDL 以
--               docs/specs/2026-10-18-wave5.10-multichannel-log/spec.md §3.1
--               与 whale-audit/db/init.sql 为准，本文件保持一致，禁止单独演进。
--     结构说明：append-only（无 update_time / deleted），id 雪花、create_time 即产生时间。
-- ---------------------------------------------------------------------

-- 后端接口日志表（ApiLogFilter 采集）
DROP TABLE IF EXISTS api_log;
CREATE TABLE api_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-auth',
    request_uri    VARCHAR(255)  DEFAULT NULL           COMMENT '请求URI',
    http_method    VARCHAR(16)   DEFAULT NULL           COMMENT 'HTTP 方法：GET/POST/PUT/DELETE',
    query_string   VARCHAR(1024) DEFAULT NULL           COMMENT '查询串（敏感字段已脱敏）',
    request_params TEXT          NULL                   COMMENT '请求体（JSON，敏感字段已脱敏）',
    response_body  TEXT          NULL                   COMMENT '响应体（JSON，敏感字段已脱敏，超限截断）',
    response_code  INT           DEFAULT NULL           COMMENT '响应状态码（HTTP 状态）',
    biz_status     TINYINT       NOT NULL DEFAULT 1     COMMENT '业务结果：1 成功 / 0 失败',
    error_msg      VARCHAR(512)  DEFAULT NULL           COMMENT '失败原因（异常类名 + 消息）',
    ip             VARCHAR(64)   DEFAULT NULL           COMMENT '调用方IP（兼容 IPv6）',
    user_agent     VARCHAR(512)  DEFAULT NULL           COMMENT '用户代理（浏览器/客户端标识）',
    cost_ms        BIGINT        DEFAULT NULL           COMMENT '接口耗时（毫秒）',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_trace_id (trace_id),
    KEY idx_create_time (create_time),
    KEY idx_user_id (user_id),
    KEY idx_biz_status (biz_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='后端接口日志表';

-- 后端出站三方调用日志表（OutboundLogInterceptor 采集）
DROP TABLE IF EXISTS outbound_log;
CREATE TABLE outbound_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（出站请求头透传）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '发起调用的用户ID',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '发起调用的用户名',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-auth',
    target_host    VARCHAR(255)  DEFAULT NULL           COMMENT '目标主机（host:port）',
    target_uri     VARCHAR(512)  DEFAULT NULL           COMMENT '目标URI（含路径，query 已脱敏）',
    http_method    VARCHAR(16)   DEFAULT NULL           COMMENT 'HTTP 方法',
    request_params TEXT          NULL                   COMMENT '出站请求体（JSON，敏感字段已脱敏）',
    response_body  TEXT          NULL                   COMMENT '出站响应体（JSON，敏感字段已脱敏，超限截断）',
    response_code  INT           DEFAULT NULL           COMMENT '响应状态码（HTTP 状态）',
    biz_status     TINYINT       NOT NULL DEFAULT 1     COMMENT '业务结果：1 成功 / 0 失败（超时/连接异常为 0）',
    error_msg      VARCHAR(512)  DEFAULT NULL           COMMENT '失败原因（异常类名 + 消息）',
    cost_ms        BIGINT        DEFAULT NULL           COMMENT '调用耗时（毫秒）',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_trace_id (trace_id),
    KEY idx_create_time (create_time),
    KEY idx_target_host (target_host),
    KEY idx_biz_status (biz_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='后端出站三方调用日志表';

-- 前端开发者日志表（POST /audit/log/frontend/batch 落库）
DROP TABLE IF EXISTS frontend_log;
CREATE TABLE frontend_log (
    id          BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id    VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID',
    user_id     BIGINT        DEFAULT NULL           COMMENT '上报用户ID（token 存在时由网关回填）',
    client_id   VARCHAR(64)   DEFAULT NULL           COMMENT '前端客户端标识（一次会话唯一）',
    page_url    VARCHAR(1024) DEFAULT NULL           COMMENT '页面URL（query 已掩码）',
    log_level   VARCHAR(16)   DEFAULT NULL           COMMENT '日志级别：DEBUG/INFO/WARN/ERROR',
    message     TEXT          NULL                   COMMENT '日志内容（超限截断，敏感键掩码）',
    stack       TEXT          NULL                   COMMENT '错误堆栈（ERROR 时）',
    browser     VARCHAR(255)  DEFAULT NULL           COMMENT '浏览器/UA 摘要',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（前端产生时间约为上报时间）',
    PRIMARY KEY (id),
    KEY idx_trace_id (trace_id),
    KEY idx_create_time (create_time),
    KEY idx_client_id (client_id),
    KEY idx_log_level (log_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='前端开发者日志表';

-- 前端用户行为埋点日志表（POST /audit/log/behavior/batch 落库）
DROP TABLE IF EXISTS behavior_log;
CREATE TABLE behavior_log (
    id          BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id    VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID',
    user_id     BIGINT        DEFAULT NULL           COMMENT '上报用户ID（token 存在时由网关回填）',
    client_id   VARCHAR(64)   DEFAULT NULL           COMMENT '前端客户端标识（一次会话唯一）',
    event_type  VARCHAR(32)   DEFAULT NULL           COMMENT '事件类型：PAGE_VIEW/CLICK/SUBMIT/CUSTOM',
    event_name  VARCHAR(128)  DEFAULT NULL           COMMENT '事件名称，如 用户管理页访问',
    page_url    VARCHAR(1024) DEFAULT NULL           COMMENT '页面URL（query 已掩码）',
    target      VARCHAR(255)  DEFAULT NULL           COMMENT '触发目标（元素标识，如按钮 name/id）',
    payload     TEXT          NULL                   COMMENT '事件载荷（JSON，敏感字段已掩码）',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_trace_id (trace_id),
    KEY idx_create_time (create_time),
    KEY idx_client_id (client_id),
    KEY idx_event_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='前端用户行为埋点日志表';

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

-- 基础菜单：系统管理目录 + 用户 / 角色 / 菜单 / 部门 / 权限 / 定时任务 / 文件 七个菜单项
INSERT INTO sys_menu
    (id, parent_id, name, path, component, icon, sort, menu_type, permission_code, visible, status)
VALUES
    (1, 0, '系统管理', '/system', NULL, 'setting', 1, 'DIR', NULL, 1, 1),
    (2, 1, '用户管理', '/system/user', 'system/user/index', 'user', 1, 'MENU', 'sys:user:list', 1, 1),
    (3, 1, '角色管理', '/system/role', 'system/role/index', 'peoples', 2, 'MENU', 'sys:role:list', 1, 1),
    (4, 1, '菜单管理', '/system/menu', 'system/menu/index', 'tree-table', 3, 'MENU', 'sys:menu:list', 1, 1),
    (5, 1, '部门管理', '/system/dept', 'system/dept/index', 'tree', 4, 'MENU', 'sys:dept:list', 1, 1),
    (6, 1, '权限管理', '/system/permission', 'system/permission/index', 'lock', 5, 'MENU', 'sys:permission:list', 1, 1),
    (7, 1, '定时任务', '/system/job', 'system/job/index', 'clock-circle', 6, 'MENU', 'sys:job:list', 1, 1),
    (8, 1, '文件管理', '/system/file', 'system/file/index', 'folder', 7, 'MENU', 'sys:file:list', 1, 1);

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
    (22, 0, 'sys:dept:delete', '删除部门', 'API', '/dept/{id}', 'DELETE', 22, 1),
    (23, 0, 'sys:user:resetPwd', '重置用户密码', 'API', '/user/{id}/password', 'PUT', 23, 1),
    (24, 0, 'sys:user:status', '启停用户', 'API', '/user/{id}/status', 'PUT', 24, 1),
    -- AUTH-06（Wave 5.10）：审计日志查询权限点，与 whale-audit 查询接口 @RequirePermission("sys:audit:log:list") 对应
    (25, 0, 'sys:audit:log:list', '审计日志查询', 'API', '/audit/log/**', 'GET', 25, 1);

-- 定时任务管理权限点：与 whale-job 接口 @RequirePermission("sys:job:*") 一一对应
INSERT INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (26, 0, 'sys:job:list', '查询定时任务', 'API', '/job/info/**', 'GET', 26, 1),
    (27, 0, 'sys:job:add', '新增定时任务', 'API', '/job/info', 'POST', 27, 1),
    (28, 0, 'sys:job:edit', '修改定时任务', 'API', '/job/info/**', 'PUT', 28, 1),
    (29, 0, 'sys:job:delete', '删除定时任务', 'API', '/job/info/{id}', 'DELETE', 29, 1),
    (30, 0, 'sys:job:run', '立即执行定时任务', 'API', '/job/info/**', 'POST', 30, 1),
    (31, 0, 'sys:job:log:list', '查询任务执行日志', 'API', '/job/log/**', 'GET', 31, 1);

-- 文件服务权限点：与 whale-file 接口 @RequirePermission("sys:file:*") 一一对应
INSERT INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (32, 0, 'sys:file:upload', '文件上传', 'API', '/file/upload', 'POST', 32, 1),
    (33, 0, 'sys:file:download', '文件下载', 'API', '/file/{id}/download', 'GET', 33, 1),
    (34, 0, 'sys:file:list', '文件列表', 'API', '/file/page', 'GET', 34, 1),
    (35, 0, 'sys:file:delete', '文件删除', 'API', '/file/{id}', 'DELETE', 35, 1);

-- 表单服务权限点：与 whale-form 接口 @RequirePermission("sys:form:*") 一一对应
INSERT INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (36, 0, 'sys:form:add', '新增表单定义', 'API', '/form/definition', 'POST', 36, 1),
    (37, 0, 'sys:form:edit', '修改表单定义', 'API', '/form/definition/**', 'PUT', 37, 1),
    (38, 0, 'sys:form:delete', '删除表单定义', 'API', '/form/definition/{id}', 'DELETE', 38, 1),
    (39, 0, 'sys:form:list', '查询表单定义', 'API', '/form/definition/**', 'GET', 39, 1);

-- 工作流服务权限点：与 whale-workflow 接口 @RequirePermission("sys:workflow:*") 一一对应
INSERT INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (40, 0, 'sys:workflow:add', '新增流程定义', 'API', '/workflow/definition', 'POST', 40, 1),
    (41, 0, 'sys:workflow:edit', '修改流程定义', 'API', '/workflow/definition/**', 'PUT', 41, 1),
    (42, 0, 'sys:workflow:delete', '删除流程定义', 'API', '/workflow/definition/{id}', 'DELETE', 42, 1),
    (43, 0, 'sys:workflow:list', '查询流程定义', 'API', '/workflow/definition/**', 'GET', 43, 1);

-- 工作流服务权限点（流程实例 + 待办审批）：与 whale-workflow 接口 @RequirePermission("sys:workflow:*") 一一对应
INSERT INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (44, 0, 'sys:workflow:instance:start', '发起流程实例', 'API', '/workflow/instance', 'POST', 44, 1),
    (45, 0, 'sys:workflow:instance:list', '查询流程实例', 'API', '/workflow/instance/**', 'GET', 45, 1),
    (46, 0, 'sys:workflow:instance:cancel', '撤销流程实例', 'API', '/workflow/instance/{id}/cancel', 'POST', 46, 1),
    (47, 0, 'sys:workflow:task:list', '查询待办任务', 'API', '/workflow/task/**', 'GET', 47, 1),
    (48, 0, 'sys:workflow:task:approve', '审批待办任务', 'API', '/workflow/task/{id}/{approve|reject}', 'POST', 48, 1);

-- 表单服务权限点（表单数据）：与 whale-form 接口 @RequirePermission("sys:form:data:*") 一一对应
INSERT INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (49, 0, 'sys:form:data:submit', '提交表单数据', 'API', '/form/data', 'POST', 49, 1),
    (50, 0, 'sys:form:data:list', '查询表单数据', 'API', '/form/data/**', 'GET', 50, 1),
    (51, 0, 'sys:form:data:delete', '删除表单数据', 'API', '/form/data/{id}', 'DELETE', 51, 1);

-- 表单服务权限点（发布）：与 whale-form @RequirePermission("sys:form:publish") 对应
INSERT INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (52, 0, 'sys:form:publish', '发布表单定义', 'API', '/form/definition/{id}/publish', 'POST', 52, 1);

-- 表单服务权限点（表定义，元数据模型）：与 whale-form @RequirePermission("sys:form:table:*") 一一对应
INSERT INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (53, 0, 'sys:form:table:add', '新增表定义', 'API', '/form/table-definition', 'POST', 53, 1),
    (54, 0, 'sys:form:table:edit', '修改表定义', 'API', '/form/table-definition/**', 'PUT', 54, 1),
    (55, 0, 'sys:form:table:delete', '删除表定义', 'API', '/form/table-definition/{id}', 'DELETE', 55, 1),
    (56, 0, 'sys:form:table:list', '查询表定义', 'API', '/form/table-definition/**', 'GET', 56, 1),
    (57, 0, 'sys:form:table:publish', '发布表定义', 'API', '/form/table-definition/{id}/publish', 'POST', 57, 1);

-- 工作流服务权限点（定义校验 + BPMN 导入）：与 whale-workflow 接口 @RequirePermission("sys:workflow:*") 一一对应
INSERT INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (58, 0, 'sys:workflow:definition:validate', '校验流程定义', 'API', '/workflow/definition/validate', 'POST', 58, 1),
    (59, 0, 'sys:workflow:definition:bpmnImport', '导入 BPMN 流程定义', 'API', '/workflow/definition/bpmn', 'POST', 59, 1);

-- 国际化消息服务权限点：与 whale-i18n 接口 @RequirePermission("sys:i18n:*") 一一对应
INSERT INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (60, 0, 'sys:i18n:list', '查询国际化消息', 'API', '/i18n/message/**', 'GET', 60, 1),
    (61, 0, 'sys:i18n:add', '新增国际化消息', 'API', '/i18n/message', 'POST', 61, 1),
    (62, 0, 'sys:i18n:edit', '修改国际化消息', 'API', '/i18n/message/**', 'PUT', 62, 1),
    (63, 0, 'sys:i18n:delete', '删除国际化消息', 'API', '/i18n/message/{id}', 'DELETE', 63, 1);

-- 服务监控权限点：与 whale-monitor 接口 @RequirePermission("sys:monitor:list") 一一对应
INSERT INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (64, 0, 'sys:monitor:list', '查询服务监控', 'API', '/monitor/**', 'GET', 64, 1);

-- 告警中心权限点：与 whale-alert 接口 @RequirePermission("sys:alert:*") 一一对应
INSERT INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (65, 0, 'sys:alert:rule:add', '新增告警规则', 'API', '/alert/rule', 'POST', 65, 1),
    (66, 0, 'sys:alert:rule:edit', '修改告警规则', 'API', '/alert/rule/**', 'PUT', 66, 1),
    (67, 0, 'sys:alert:rule:delete', '删除告警规则', 'API', '/alert/rule/{id}', 'DELETE', 67, 1),
    (68, 0, 'sys:alert:rule:list', '查询告警规则', 'API', '/alert/rule/**', 'GET', 68, 1),
    (69, 0, 'sys:alert:record:list', '查询告警记录', 'API', '/alert/record/**', 'GET', 69, 1);

-- 支付服务权限点：与 whale-pay 接口 @RequirePermission("sys:pay:*") 一一对应
INSERT INTO sys_permission
    (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
VALUES
    (70, 0, 'sys:pay:list', '查询支付单', 'API', '/pay/order/**', 'GET', 70, 1),
    (71, 0, 'sys:pay:add', '创建支付单', 'API', '/pay/order', 'POST', 71, 1),
    (72, 0, 'sys:pay:pay', '支付单支付', 'API', '/pay/order/{id}/pay', 'POST', 72, 1),
    (73, 0, 'sys:pay:cancel', '支付单取消', 'API', '/pay/order/{id}/cancel', 'POST', 73, 1);
