-- =====================================================================
-- BlueWhale 客户关系应用（whale-crm）Flyway 增量 V9：M3 发票与应收对账
-- =====================================================================
-- 适用库   : bluewhale_crm（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/crm-module-panorama.md
-- 计划     : docs/plans/2026-11-04-wave5.83-crm-panorama.md（M3 / 7f-3）
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) 表间逻辑外键，不建物理 FOREIGN KEY；
--            4) 不写业务种子数据。
-- 说明     : CRM 只做开票申请 / 审批 / 开票记录 / 寄送跟踪 / 红冲，
--            以及 ERP 应收只读快照对账；票面记账、税额、实收与核销归 ERP。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 开票申请头
--    status: DRAFT / PENDING_APPROVAL / APPROVED / REJECTED /
--            ISSUED / SENT / RED_FLUSHED / CANCELLED
--    request_type: NORMAL / RED_FLUSH
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_invoice_request (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    invoice_no            VARCHAR(64)   NOT NULL                COMMENT '开票申请单号（INV-yyyyMMdd-xxxx）',
    request_type          VARCHAR(16)   NOT NULL DEFAULT 'NORMAL' COMMENT '申请类型：NORMAL / RED_FLUSH',
    origin_invoice_id     BIGINT        DEFAULT NULL            COMMENT '红冲原开票申请ID',
    source_type           VARCHAR(32)   NOT NULL DEFAULT 'CONTRACT' COMMENT '来源类型：CONTRACT / ORDER',
    source_id             BIGINT        DEFAULT NULL            COMMENT '来源单据ID',
    source_order_no       VARCHAR(64)   DEFAULT NULL            COMMENT '来源单据编号快照',
    contract_id           BIGINT        DEFAULT NULL            COMMENT '合同台账ID',
    contract_no           VARCHAR(64)   DEFAULT NULL            COMMENT '合同编号快照',
    customer_id           BIGINT        NOT NULL                COMMENT 'CRM 客户ID',
    customer_code         VARCHAR(64)   DEFAULT NULL            COMMENT '客户编码快照（ERP 对账用）',
    customer_name         VARCHAR(255)  DEFAULT NULL            COMMENT '客户名称快照',
    currency              VARCHAR(8)    NOT NULL DEFAULT 'CNY'  COMMENT '币种',
    amount                DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '申请含税金额（红冲为负数）',
    tax_amount            DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '税额（记录开票时回填）',
    invoice_type          VARCHAR(24)   NOT NULL DEFAULT 'VAT_SPECIAL' COMMENT '发票类型：VAT_SPECIAL / VAT_NORMAL / ELECTRONIC',
    title_type            VARCHAR(16)   NOT NULL DEFAULT 'COMPANY' COMMENT '抬头类型：COMPANY / PERSONAL',
    invoice_title         VARCHAR(255)  DEFAULT NULL            COMMENT '发票抬头',
    tax_no                VARCHAR(64)   DEFAULT NULL            COMMENT '纳税人识别号',
    status                VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '申请状态',
    approval_instance_id  BIGINT        DEFAULT NULL            COMMENT '审批流程实例ID（whale-workflow）',
    submitted_at          DATETIME      DEFAULT NULL            COMMENT '提交审批时间',
    approved_at           DATETIME      DEFAULT NULL            COMMENT '审批通过时间',
    invoice_code          VARCHAR(64)   DEFAULT NULL            COMMENT '发票代码（开票记录）',
    invoice_number        VARCHAR(64)   DEFAULT NULL            COMMENT '发票号码（开票记录）',
    issued_at             DATETIME      DEFAULT NULL            COMMENT '开票时间',
    issued_amount         DECIMAL(18,2) DEFAULT NULL            COMMENT '实际开票含税金额',
    courier               VARCHAR(64)   DEFAULT NULL            COMMENT '寄送快递公司',
    tracking_no           VARCHAR(128)  DEFAULT NULL            COMMENT '寄送运单号',
    sent_at               DATETIME      DEFAULT NULL            COMMENT '寄送时间',
    red_flush_reason      VARCHAR(500)  DEFAULT NULL            COMMENT '红冲原因',
    red_flushed_at        DATETIME      DEFAULT NULL            COMMENT '红冲完成时间',
    owner_id              BIGINT        DEFAULT NULL            COMMENT '负责人（数据权限归属人）',
    dept_id               BIGINT        DEFAULT NULL            COMMENT '归属部门',
    remark                VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    lock_version          INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_invoice_request_no (tenant_id, invoice_no),
    KEY idx_crm_invoice_request_customer (tenant_id, customer_id, status),
    KEY idx_crm_invoice_request_contract (tenant_id, contract_id, status),
    KEY idx_crm_invoice_request_status (tenant_id, status, update_time),
    KEY idx_crm_invoice_request_origin (tenant_id, origin_invoice_id)
) COMMENT='CRM 开票申请';

-- ---------------------------------------------------------------------
-- 2. 开票申请行（明细为可选，汇总金额以头部为准）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_invoice_item (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    request_id            BIGINT        NOT NULL                COMMENT '开票申请ID',
    line_no               INT           NOT NULL                COMMENT '行号（从 1 起）',
    item_name             VARCHAR(255)  DEFAULT NULL            COMMENT '项目 / 物料名称',
    specification         VARCHAR(255)  DEFAULT NULL            COMMENT '规格',
    quantity              DECIMAL(18,4) NOT NULL DEFAULT 0      COMMENT '数量',
    unit_price            DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '单价（含税）',
    tax_rate              DECIMAL(8,4)  NOT NULL DEFAULT 0      COMMENT '税率（百分数，如 13 表示 13%）',
    tax_amount            DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '行税额',
    amount                DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '行含税金额',
    remark                VARCHAR(500)  DEFAULT NULL            COMMENT '行备注',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_invoice_item_no (tenant_id, request_id, line_no),
    KEY idx_crm_invoice_item_request (tenant_id, request_id)
) COMMENT='CRM 开票申请行';

-- ---------------------------------------------------------------------
-- 3. 客户应收快照（按客户按日，来自 ERP 只读契约）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_ar_snapshot (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    customer_id           BIGINT        NOT NULL                COMMENT 'CRM 客户ID',
    customer_code         VARCHAR(64)   DEFAULT NULL            COMMENT '客户编码快照',
    customer_name         VARCHAR(255)  DEFAULT NULL            COMMENT '客户名称快照',
    snapshot_date         DATE          NOT NULL                COMMENT '快照日期',
    currency              VARCHAR(8)    NOT NULL DEFAULT 'CNY'  COMMENT '币种',
    receivable_count      INT           NOT NULL DEFAULT 0      COMMENT '未结应收单数量',
    total_remaining       DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '未结余额合计',
    not_due               DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '未到期金额',
    overdue_1_30          DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '逾期 1-30 天金额',
    overdue_31_60         DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '逾期 31-60 天金额',
    overdue_61_90         DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '逾期 61-90 天金额',
    overdue_over_90       DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '逾期 90 天以上金额',
    source                VARCHAR(16)   NOT NULL DEFAULT 'ERP'  COMMENT '数据来源',
    owner_id              BIGINT        DEFAULT NULL            COMMENT '负责人（数据权限归属人）',
    dept_id               BIGINT        DEFAULT NULL            COMMENT '归属部门',
    last_sync_at          DATETIME      DEFAULT NULL            COMMENT '最近同步时间',
    remark                VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_ar_snapshot_day (tenant_id, customer_id, snapshot_date, currency),
    KEY idx_crm_ar_snapshot_date (tenant_id, snapshot_date)
) COMMENT='CRM 客户应收快照';
