-- =====================================================================
-- BlueWhale 客户关系应用（whale-crm）Flyway 增量 V8：M3 商务订单
-- =====================================================================
-- 适用库   : bluewhale_crm（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/crm-module-panorama.md
-- 计划     : docs/plans/2026-11-04-wave5.83-crm-panorama.md（M3 / 7f-2）
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) 表间逻辑外键，不建物理 FOREIGN KEY；
--            4) 不写业务种子数据。
-- 说明     : CRM 建商务订单并审批，审批通过后经 outbox 幂等下发 ERP；
--            发货 / 库存 / 应收 / 信用 / 记账仍归 ERP 所有。
--            下发事件复用 M0 已建的 crm_common_outbox，不重复建表。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 商务订单头
--    status: DRAFT / PENDING_APPROVAL / APPROVED / REJECTED /
--            PUSHING / PUSHED / PUSH_FAILED / CANCELLED
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_sales_order (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    order_no              VARCHAR(64)   NOT NULL                COMMENT '商务订单编号',
    source_type           VARCHAR(32)   NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：CONTRACT / QUOTE / MANUAL',
    source_id             BIGINT        DEFAULT NULL            COMMENT '来源单据ID（合同台账 / 报价）',
    source_order_no       VARCHAR(64)   DEFAULT NULL            COMMENT '来源单据编号快照',
    customer_id           BIGINT        NOT NULL                COMMENT 'CRM 客户ID',
    customer_code         VARCHAR(64)   DEFAULT NULL            COMMENT '客户编码快照（ERP 解析用）',
    title                 VARCHAR(255)  DEFAULT NULL            COMMENT '订单标题',
    currency              VARCHAR(8)    NOT NULL DEFAULT 'CNY'  COMMENT '币种',
    total_amount          DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '订单总额',
    status                VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '订单状态',
    approval_instance_id  BIGINT        DEFAULT NULL            COMMENT '审批流程实例ID（whale-workflow）',
    submitted_at          DATETIME      DEFAULT NULL            COMMENT '提交审批时间',
    approved_at           DATETIME      DEFAULT NULL            COMMENT '审批通过时间',
    erp_order_no          VARCHAR(64)   DEFAULT NULL            COMMENT 'ERP 销售订单号（下发回写）',
    erp_order_id          BIGINT        DEFAULT NULL            COMMENT 'ERP 销售订单ID',
    erp_status            VARCHAR(24)   DEFAULT NULL            COMMENT 'ERP 履约状态（对账回读）',
    pushed_at             DATETIME      DEFAULT NULL            COMMENT '下发成功时间',
    push_attempts         INT           NOT NULL DEFAULT 0      COMMENT '下发尝试次数',
    last_push_error       VARCHAR(512)  DEFAULT NULL            COMMENT '最近下发失败原因',
    owner_id              BIGINT        DEFAULT NULL            COMMENT '负责人（数据权限归属人）',
    dept_id               BIGINT        DEFAULT NULL            COMMENT '归属部门',
    remark                VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    lock_version          INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_sales_order_no (tenant_id, order_no),
    UNIQUE KEY uk_crm_sales_order_source (tenant_id, source_type, source_id),
    KEY idx_crm_sales_order_customer (tenant_id, customer_id, status),
    KEY idx_crm_sales_order_erp (tenant_id, erp_order_no),
    KEY idx_crm_sales_order_status (tenant_id, status, update_time)
) COMMENT='CRM 商务订单';

-- ---------------------------------------------------------------------
-- 2. 商务订单行
--    item_code 可为空：报价转单先复制行，提交审批前必须补录
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_sales_order_line (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    order_id              BIGINT        NOT NULL                COMMENT '商务订单ID',
    line_no               INT           NOT NULL                COMMENT '行号（从 1 起）',
    item_code             VARCHAR(64)   DEFAULT NULL            COMMENT 'ERP 物料编码（下发必填）',
    item_name             VARCHAR(255)  DEFAULT NULL            COMMENT '物料 / 产品名称',
    specification         VARCHAR(255)  DEFAULT NULL            COMMENT '规格',
    uom                   VARCHAR(32)   DEFAULT NULL            COMMENT '计量单位',
    quantity              DECIMAL(18,4) NOT NULL DEFAULT 0      COMMENT '数量',
    unit_price            DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '单价',
    amount                DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '行金额',
    remark                VARCHAR(500)  DEFAULT NULL            COMMENT '行备注',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_sales_order_line_no (tenant_id, order_id, line_no),
    KEY idx_crm_sales_order_line_order (tenant_id, order_id)
) COMMENT='CRM 商务订单行';

-- ---------------------------------------------------------------------
-- 3. 下发日志（每次 HTTP 尝试一条，追加式）
--    status: SUCCESS / FAILED
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_order_push_log (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    order_id              BIGINT        NOT NULL                COMMENT '商务订单ID',
    attempt_no            INT           NOT NULL                COMMENT '第几次下发尝试',
    request_ref           VARCHAR(128)  DEFAULT NULL            COMMENT '请求引用（crmOrderNo）',
    response_ref          VARCHAR(128)  DEFAULT NULL            COMMENT '响应引用（erpOrderNo）',
    status                VARCHAR(16)   NOT NULL                COMMENT '结果：SUCCESS / FAILED',
    error_msg             VARCHAR(512)  DEFAULT NULL            COMMENT '失败原因',
    cost_ms               BIGINT        DEFAULT NULL            COMMENT '耗时（毫秒）',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_crm_order_push_log (tenant_id, order_id, create_time)
) COMMENT='CRM 商务订单下发日志';
