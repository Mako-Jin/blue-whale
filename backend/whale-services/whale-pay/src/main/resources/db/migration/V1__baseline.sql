-- =====================================================================
-- BlueWhale 微服务平台 - 支付服务（whale-pay） Flyway 版本化基线 V1
-- =====================================================================
-- 适用库   : bluewhale_pay（MySQL 8.0+，utf8mb4）
-- 来源     : 由 db/init.sql 转换导出（2026-11-01 阶段 4 铺开）
-- 转换规则 : 1) 跳过 DROP TABLE；2) CREATE TABLE 统一 IF NOT EXISTS；
--            3) INSERT 统一 INSERT IGNORE（种子幂等）；4) 表尾统一 ENGINE/CHARSET/COLLATE。
-- 说明     : db/init.sql 为手工空库初始化入口，历史 migration-*.sql 仅供追溯，
--            二者均不在 spring.flyway.locations 扫描路径内（详见 db/README.md）。
-- =====================================================================

-- =====================================================================
-- BlueWhale 微服务平台 - 支付服务（whale-pay）数据库初始化脚本
-- =====================================================================
-- 库名       : bluewhale_pay
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
-- 支付单表
--   状态流转单向：CREATED → PAID / CANCELLED、PAID → REFUNDED，其余迁移一律拒绝。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pay_order (
    id            BIGINT         NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT         NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    order_no      VARCHAR(64)    NOT NULL            COMMENT '支付单号（租户内唯一）',
    business_key  VARCHAR(128)   DEFAULT NULL        COMMENT '业务标识（外部订单号 / 业务键）',
    amount        DECIMAL(18,2)  NOT NULL            COMMENT '支付金额',
    currency      VARCHAR(8)     NOT NULL DEFAULT 'CNY' COMMENT '币种，默认 CNY',
    channel       VARCHAR(32)    DEFAULT NULL        COMMENT '支付渠道，如 ALIPAY / WECHAT / BALANCE',
    status        VARCHAR(16)    NOT NULL DEFAULT 'CREATED'
                                                   COMMENT '状态：CREATED 待支付 / PAID 已支付 / CANCELLED 已取消 / REFUNDED 已退款',
    pay_time      DATETIME       DEFAULT NULL        COMMENT '支付完成时间（未支付为 NULL）',
    channel_trade_no VARCHAR(64) DEFAULT NULL        COMMENT '渠道流水号（支付成功后回填；mock 模式为模拟流水号）',
    refund_amount DECIMAL(18,2)  NOT NULL DEFAULT 0.00 COMMENT '已退款金额（未退款为 0）',
    refund_time   DATETIME       DEFAULT NULL        COMMENT '退款完成时间（未退款为 NULL）',
    remark        VARCHAR(255)   DEFAULT NULL        COMMENT '备注',
    create_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT        NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_order_no (tenant_id, order_no),
    KEY idx_business_key (business_key),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='支付单表';

-- ---------------------------------------------------------------------
-- 支付/退款流水表
--   只追加不修改：每次支付或退款成功追加一行，便于按支付单追溯渠道动作。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pay_transaction (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    order_id          BIGINT        NOT NULL            COMMENT '支付单ID（逻辑关联 pay_order.id）',
    order_no          VARCHAR(64)   NOT NULL            COMMENT '支付单号（冗余存储）',
    type              VARCHAR(16)   NOT NULL            COMMENT '流水类型：PAY 支付 / REFUND 退款',
    channel           VARCHAR(32)   DEFAULT NULL        COMMENT '支付渠道',
    amount            DECIMAL(18,2) NOT NULL            COMMENT '本次流水金额',
    currency          VARCHAR(8)    NOT NULL DEFAULT 'CNY' COMMENT '币种',
    provider_trade_no VARCHAR(64)   DEFAULT NULL        COMMENT '渠道流水号',
    status            VARCHAR(16)   NOT NULL            COMMENT '流水状态：SUCCESS 成功 / FAILED 失败',
    error_message     VARCHAR(512)  DEFAULT NULL        COMMENT '失败原因（成功为 NULL）',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_order (tenant_id, order_id),
    KEY idx_provider_trade_no (provider_trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='支付退款流水表';

-- ---------------------------------------------------------------------
-- 操作审计日志表（whale-spring-boot-starter @AuditLog 切面采集，结构与 bluewhale_auth.audit_log 一致）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名（冗余存储，用户删除后仍可追溯）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-pay',
    operation      VARCHAR(128)  DEFAULT NULL           COMMENT '操作描述',
    operate_type   VARCHAR(32)   DEFAULT NULL           COMMENT '操作类型：CREATE/UPDATE/DELETE/QUERY/OTHER 等',
    method         VARCHAR(255)  DEFAULT NULL           COMMENT '请求方法（全限定类名.方法名）',
    request_uri    VARCHAR(255)  DEFAULT NULL           COMMENT '请求URI',
    http_method    VARCHAR(16)   DEFAULT NULL           COMMENT 'HTTP 方法：GET/POST/PUT/DELETE',
    target_type    VARCHAR(64)   DEFAULT NULL           COMMENT '操作对象类型，如 pay_order',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作审计日志表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='后端接口日志表';
