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
--   状态流转单向：CREATED → PAID / CANCELLED，其余迁移一律拒绝。
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS pay_order;
CREATE TABLE pay_order (
    id            BIGINT         NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT         NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    order_no      VARCHAR(64)    NOT NULL            COMMENT '支付单号（租户内唯一）',
    business_key  VARCHAR(128)   DEFAULT NULL        COMMENT '业务标识（外部订单号 / 业务键）',
    amount        DECIMAL(18,2)  NOT NULL            COMMENT '支付金额',
    currency      VARCHAR(8)     NOT NULL DEFAULT 'CNY' COMMENT '币种，默认 CNY',
    channel       VARCHAR(32)    DEFAULT NULL        COMMENT '支付渠道，如 ALIPAY / WECHAT / BALANCE',
    status        VARCHAR(16)    NOT NULL DEFAULT 'CREATED' COMMENT '状态：CREATED 待支付 / PAID 已支付 / CANCELLED 已取消',
    pay_time      DATETIME       DEFAULT NULL        COMMENT '支付完成时间（未支付为 NULL）',
    remark        VARCHAR(255)   DEFAULT NULL        COMMENT '备注',
    create_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT        NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_order_no (tenant_id, order_no),
    KEY idx_business_key (business_key),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='支付单表';
