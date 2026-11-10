-- =====================================================================
-- BlueWhale 客户关系应用（whale-crm）Flyway 增量 V5：M2 报价单
-- =====================================================================
-- 适用库   : bluewhale_crm（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/crm-module-panorama.md
-- 规格     : docs/specs/2026-11-04-wave5.83-crm-quote/spec.md
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) 表间逻辑外键，不建物理 FOREIGN KEY；
--            4) 不写业务种子数据。
-- 说明     : 报价头 / 行 / 审批记录三表；版本只追加，审批后报价锁定只读。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 报价头
--    status: DRAFT / PENDING_APPROVAL / APPROVED / REJECTED
--    version 为业务版本号；lock_version 为乐观锁字段
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_quote (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    quote_no              VARCHAR(64)   NOT NULL                COMMENT '报价编号（同号多版本）',
    version               INT           NOT NULL DEFAULT 1      COMMENT '业务版本号（只追加）',
    root_quote_id         BIGINT        DEFAULT NULL            COMMENT '首版报价ID',
    parent_quote_id       BIGINT        DEFAULT NULL            COMMENT '来源版本ID（指向首版）',
    opportunity_id        BIGINT        NOT NULL                COMMENT '商机ID',
    customer_id           BIGINT        NOT NULL                COMMENT '客户ID（来自商机）',
    title                 VARCHAR(128)  NOT NULL                COMMENT '报价标题',
    status                VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT / PENDING_APPROVAL / APPROVED / REJECTED',
    currency              VARCHAR(8)    NOT NULL DEFAULT 'CNY'  COMMENT '币种',
    valid_until           DATE          DEFAULT NULL            COMMENT '有效期',
    subtotal_amount       DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '行合计（未税）',
    tax_amount            DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '税额',
    discount_amount       DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '整单折扣金额',
    total_amount          DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '含税总价',
    discount_rate         DECIMAL(5,2)  NOT NULL DEFAULT 0      COMMENT '综合折扣率（审批阈值依据）',
    remark                VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    owner_id              BIGINT        DEFAULT NULL            COMMENT '负责人（数据权限归属人）',
    dept_id               BIGINT        DEFAULT NULL            COMMENT '归属部门',
    approval_instance_id  BIGINT        DEFAULT NULL            COMMENT '当前审批流程实例ID',
    submitted_at          DATETIME      DEFAULT NULL            COMMENT '提交审批时间',
    approved_at           DATETIME      DEFAULT NULL            COMMENT '审批通过时间',
    lock_version          INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_quote_no_version (tenant_id, quote_no, version),
    KEY idx_crm_quote_opp (tenant_id, opportunity_id, status),
    KEY idx_crm_quote_owner (tenant_id, owner_id, status),
    KEY idx_crm_quote_root (tenant_id, root_quote_id, version)
) COMMENT='销售报价单';

-- ---------------------------------------------------------------------
-- 2. 报价行
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_quote_line (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    quote_id              BIGINT        NOT NULL                COMMENT '报价ID',
    line_no               INT           NOT NULL                COMMENT '行号',
    product_name          VARCHAR(128)  NOT NULL                COMMENT '产品 / 服务名称',
    specification         VARCHAR(128)  DEFAULT NULL            COMMENT '规格',
    unit                  VARCHAR(16)   DEFAULT NULL            COMMENT '单位',
    quantity              DECIMAL(18,4) NOT NULL                COMMENT '数量',
    unit_price            DECIMAL(18,4) NOT NULL                COMMENT '单价',
    discount_rate         DECIMAL(5,2)  NOT NULL DEFAULT 0      COMMENT '行折扣率（0–100）',
    tax_rate              DECIMAL(5,2)  NOT NULL DEFAULT 13     COMMENT '税率（0–100）',
    line_amount           DECIMAL(18,2) NOT NULL                COMMENT '行金额快照',
    remark                VARCHAR(255)  DEFAULT NULL            COMMENT '行备注',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_crm_quote_line_quote (tenant_id, quote_id, line_no)
) COMMENT='销售报价行';

-- ---------------------------------------------------------------------
-- 3. 报价审批记录（每次提交一条，实例唯一）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_quote_approval (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    quote_id              BIGINT        NOT NULL                COMMENT '报价ID',
    quote_version         INT           NOT NULL                COMMENT '提交时业务版本号',
    instance_id           BIGINT        NOT NULL                COMMENT '流程实例ID',
    status                VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING / APPROVED / REJECTED',
    approval_level        VARCHAR(32)   DEFAULT NULL            COMMENT '审批层级：SUPERVISOR / DIRECTOR / GENERAL_MANAGER',
    discount_rate         DECIMAL(5,2)  DEFAULT NULL            COMMENT '提交时折扣率快照',
    comment               VARCHAR(500)  DEFAULT NULL            COMMENT '审批意见',
    submitter_id          BIGINT        DEFAULT NULL            COMMENT '提交人',
    submitted_at          DATETIME      DEFAULT NULL            COMMENT '提交时间',
    decided_at            DATETIME      DEFAULT NULL            COMMENT '决策时间',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_quote_approval_instance (tenant_id, instance_id),
    KEY idx_crm_quote_approval_quote (tenant_id, quote_id, status)
) COMMENT='销售报价审批记录';
