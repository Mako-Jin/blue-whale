-- =====================================================================
-- whale-pay：支付闭环（退款 / 流水 / 对账）增量迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_pay
-- 前置条件 : 已执行 db/init.sql（pay_order 已存在）
-- 执行方式 : mysql -u<user> -p bluewhale_pay < migration-5.71-pay-flow.sql
-- 幂等说明 : 列迁移先查 information_schema，不存在才 ALTER；新表用 CREATE TABLE IF NOT EXISTS。
-- 背景     : F-04 支付服务此前只有订单 CRUD 与支付/取消占位，缺少退款、渠道流水号与
--            支付/退款流水；同时补齐 @AuditLog 依赖的 audit_log 表。
-- =====================================================================

-- 0. pay_order 补 tenant_id（历史库由更早版本建表时缺失，多租户拦截器会追加该列条件）
SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pay_order'
      AND COLUMN_NAME = 'tenant_id'
);

SET @ddl := IF(
    @column_exists = 0,
    'ALTER TABLE pay_order ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT ''租户ID（行级隔离）'' AFTER id',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 0.1 唯一键从「全局单号」对齐为「租户内单号」
SET @index_exists := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pay_order'
      AND INDEX_NAME = 'uk_order_no'
);

SET @ddl := IF(
    @index_exists > 0,
    'ALTER TABLE pay_order DROP INDEX uk_order_no',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pay_order'
      AND INDEX_NAME = 'uk_tenant_order_no'
);

SET @ddl := IF(
    @index_exists = 0,
    'ALTER TABLE pay_order ADD UNIQUE KEY uk_tenant_order_no (tenant_id, order_no)',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1. pay_order 增加渠道流水号
SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pay_order'
      AND COLUMN_NAME = 'channel_trade_no'
);

SET @ddl := IF(
    @column_exists = 0,
    'ALTER TABLE pay_order ADD COLUMN channel_trade_no VARCHAR(64) DEFAULT NULL COMMENT ''渠道流水号（支付成功后回填；mock 模式为模拟流水号）'' AFTER pay_time',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. pay_order 增加已退款金额
SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pay_order'
      AND COLUMN_NAME = 'refund_amount'
);

SET @ddl := IF(
    @column_exists = 0,
    'ALTER TABLE pay_order ADD COLUMN refund_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT ''已退款金额（未退款为 0）'' AFTER channel_trade_no',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. pay_order 增加退款完成时间
SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pay_order'
      AND COLUMN_NAME = 'refund_time'
);

SET @ddl := IF(
    @column_exists = 0,
    'ALTER TABLE pay_order ADD COLUMN refund_time DATETIME DEFAULT NULL COMMENT ''退款完成时间（未退款为 NULL）'' AFTER refund_amount',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. 支付/退款流水表
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
) COMMENT='支付退款流水表';

-- 5. 操作审计日志表（@AuditLog 依赖，缺失时审计写库失败会被吞掉）
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
) COMMENT='操作审计日志表';

-- =====================================================================
-- 完成。校验建议：
--   SHOW COLUMNS FROM pay_order LIKE 'channel_trade_no';
--   SELECT TABLE_NAME FROM information_schema.TABLES
--   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('pay_transaction', 'audit_log');
-- =====================================================================
