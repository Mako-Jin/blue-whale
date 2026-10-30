-- =====================================================================
-- BlueWhale 微服务平台 - 业务配置管理服务（whale-config）数据库初始化脚本
-- =====================================================================
-- 库名       : bluewhale_config
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成，无自增）、create_time、update_time、
--              deleted TINYINT（逻辑删除：0 未删除 / 1 已删除，与 MyBatis-Plus
--              logic-delete-field: deleted 配置对齐）
-- 命名风格   : 列名统一下划线风格，与 mybatis-plus map-underscore-to-camel-case 对齐
-- 说明       : 表间关联均为逻辑外键（不建物理 FOREIGN KEY），便于微服务独立演进
-- =====================================================================

-- ---------------------------------------------------------------------
-- 配置项表
--   config_key 租户内唯一（逻辑删除保留行，唯一性由唯一索引 + 服务层前置校验保证）；
--   version 从 1 起单调递增，每次值变更 +1，历史留痕于 config_item_history。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS config_item (
    id          BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    config_key  VARCHAR(128) NOT NULL            COMMENT '配置键（租户内唯一，如 order.pay.timeout）',
    config_value TEXT        DEFAULT NULL        COMMENT '配置值（按 value_type 解释，JSON 原文存储）',
    value_type  VARCHAR(16)  NOT NULL DEFAULT 'STRING' COMMENT '值类型：STRING / NUMBER / BOOLEAN / JSON',
    description VARCHAR(255) DEFAULT NULL        COMMENT '配置说明',
    version     INT          NOT NULL DEFAULT 1  COMMENT '当前版本号（创建为 1，每次值变更 +1）',
    status      TINYINT      NOT NULL DEFAULT 1  COMMENT '状态：1 启用 / 0 停用（停用项消费方应回落默认值）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_config_key (tenant_id, config_key),
    KEY idx_status (status)
) COMMENT='业务配置项表';

-- ---------------------------------------------------------------------
-- 配置变更历史表
--   只追加不修改：创建 / 更新 / 回滚 / 删除均落一行，version 记变更后版本号；
--   回滚也产生新版本（不覆盖历史），保证审计链完整可追溯。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS config_item_history (
    id          BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    item_id     BIGINT       NOT NULL            COMMENT '配置项ID（逻辑关联 config_item.id）',
    config_key  VARCHAR(128) NOT NULL            COMMENT '配置键（冗余存储，项删除后仍可追溯）',
    operation   VARCHAR(16)  NOT NULL            COMMENT '操作类型：CREATE 创建 / UPDATE 更新 / ROLLBACK 回滚 / DELETE 删除',
    old_value   TEXT         DEFAULT NULL        COMMENT '变更前值（创建为 NULL）',
    new_value   TEXT         DEFAULT NULL        COMMENT '变更后值（删除为 NULL）',
    version     INT          NOT NULL            COMMENT '变更后版本号',
    operator_id BIGINT       DEFAULT NULL        COMMENT '操作人ID（网关 X-User-Id 注入，内部操作为 NULL）',
    operator    VARCHAR(64)  DEFAULT NULL        COMMENT '操作人用户名（冗余存储）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_item (tenant_id, item_id),
    KEY idx_config_key (config_key)
) COMMENT='配置变更历史表';

-- ---------------------------------------------------------------------
-- 操作审计日志表（whale-spring-boot-starter @AuditLog 切面采集，结构与 bluewhale_auth.audit_log 一致）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名（冗余存储，用户删除后仍可追溯）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-config',
    operation      VARCHAR(128)  DEFAULT NULL           COMMENT '操作描述',
    operate_type   VARCHAR(32)   DEFAULT NULL           COMMENT '操作类型：CREATE/UPDATE/DELETE/QUERY/OTHER 等',
    method         VARCHAR(255)  DEFAULT NULL           COMMENT '请求方法（全限定类名.方法名）',
    request_uri    VARCHAR(255)  DEFAULT NULL           COMMENT '请求URI',
    http_method    VARCHAR(16)   DEFAULT NULL           COMMENT 'HTTP 方法：GET/POST/PUT/DELETE',
    target_type    VARCHAR(64)   DEFAULT NULL           COMMENT '操作对象类型，如 config_item',
    target_id      VARCHAR(64)   DEFAULT NULL           COMMENT '操作对象ID',
    request_params TEXT          NULL                   COMMENT '请求参数（JSON，敏感字段已脱敏）',
    result_body    TEXT          NULL                   COMMENT '返回体（JSON，脱敏 + 截断）',
    response_code  INT           DEFAULT NULL           COMMENT '响应状态码',
    biz_status     TINYINT       NOT NULL DEFAULT 1     COMMENT '业务结果：1 成功 / 0 失败',
    error_msg      VARCHAR(512)  DEFAULT NULL           COMMENT '失败原因（异常类名 + 消息，已脱敏）',
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
) COMMENT='操作审计日志表';

-- ---------------------------------------------------------------------
-- 后端接口日志表（whale-spring-boot-starter ApiLogFilter 采集，结构与 bluewhale_auth.api_log 一致）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS api_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
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
    KEY idx_tenant_create (tenant_id, create_time),
    KEY idx_biz_status (biz_status)
) COMMENT='后端接口日志表';
