-- =====================================================================
-- BlueWhale 客户关系应用（whale-crm）Flyway 增量 V7：M3 合同台账与回款
-- =====================================================================
-- 适用库   : bluewhale_crm（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/crm-module-panorama.md
-- 计划     : docs/plans/2026-11-04-wave5.83-crm-panorama.md（M3 / 7f-1）
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) 表间逻辑外键，不建物理 FOREIGN KEY；
--            4) 不写业务种子数据。
-- 说明     : 合同正文 / 模板 / 签署 / 归档归 whale-contract，本库只存台账投影；
--            ERP 是实收 / 税额 / 记账唯一所有者，回款表只存计划、承诺与催收记录。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 合同台账投影
--    renewal_status: PENDING / RENEWED / NOT_RENEWED
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_contract (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    contract_id           BIGINT        NOT NULL                COMMENT '合同服务合同ID（whale-contract）',
    contract_no           VARCHAR(64)   DEFAULT NULL            COMMENT '合同编号（投影）',
    customer_id           BIGINT        NOT NULL                COMMENT 'CRM 客户ID',
    title                 VARCHAR(255)  DEFAULT NULL            COMMENT '合同标题（投影）',
    category              VARCHAR(32)   DEFAULT NULL            COMMENT '合同类别（投影）',
    amount                DECIMAL(18,2) DEFAULT NULL            COMMENT '合同金额（投影）',
    currency              VARCHAR(8)    NOT NULL DEFAULT 'CNY'  COMMENT '币种',
    status                VARCHAR(20)   DEFAULT NULL            COMMENT '合同状态（投影，以合同服务为准）',
    effective_date        DATE          DEFAULT NULL            COMMENT '生效日期（投影）',
    expire_date           DATE          DEFAULT NULL            COMMENT '到期日期（投影）',
    biz_type              VARCHAR(32)   NOT NULL DEFAULT 'CRM_CUSTOMER' COMMENT '弱关联业务类型',
    biz_id                VARCHAR(64)   DEFAULT NULL            COMMENT '弱关联业务ID（客户ID字符串）',
    renewal_status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '续约状态：PENDING / RENEWED / NOT_RENEWED',
    renewal_owner_id      BIGINT        DEFAULT NULL            COMMENT '续约负责人',
    renewal_remark        VARCHAR(500)  DEFAULT NULL            COMMENT '续约备注',
    renewal_at            DATETIME      DEFAULT NULL            COMMENT '续约标记时间',
    last_sync_at          DATETIME      DEFAULT NULL            COMMENT '最近同步时间',
    owner_id              BIGINT        DEFAULT NULL            COMMENT '负责人（数据权限归属人）',
    dept_id               BIGINT        DEFAULT NULL            COMMENT '归属部门',
    remark                VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_contract_source (tenant_id, contract_id),
    KEY idx_crm_contract_customer (tenant_id, customer_id, status),
    KEY idx_crm_contract_expire (tenant_id, expire_date, status)
) COMMENT='CRM 合同台账投影';

-- ---------------------------------------------------------------------
-- 2. 回款计划分期
--    status: OPEN / CANCELLED；实收与对账由 ERP 同步（7f-3）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_collection_plan (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    contract_id           BIGINT        NOT NULL                COMMENT 'CRM 合同台账ID',
    customer_id           BIGINT        NOT NULL                COMMENT 'CRM 客户ID',
    installment_no        INT           NOT NULL                COMMENT '分期序号（从 1 起）',
    planned_amount        DECIMAL(18,2) NOT NULL                COMMENT '计划回款金额',
    planned_date          DATE          NOT NULL                COMMENT '计划回款日期',
    status                VARCHAR(20)   NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN / CANCELLED',
    priority_override     VARCHAR(10)   DEFAULT NULL            COMMENT '催收优先级覆盖：HIGH / MEDIUM / LOW',
    promise_date          DATE          DEFAULT NULL            COMMENT '承诺还款日期',
    promise_amount        DECIMAL(18,2) DEFAULT NULL            COMMENT '承诺还款金额',
    owner_id              BIGINT        DEFAULT NULL            COMMENT '负责人',
    dept_id               BIGINT        DEFAULT NULL            COMMENT '归属部门',
    remark                VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    lock_version          INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_crm_collection_installment (tenant_id, contract_id, installment_no),
    KEY idx_crm_collection_date (tenant_id, planned_date, status),
    KEY idx_crm_collection_customer (tenant_id, customer_id, status)
) COMMENT='CRM 回款计划分期';

-- ---------------------------------------------------------------------
-- 3. 催收记录（追加式）
--    action_type: CALL / VISIT / EMAIL / REMINDER / PROMISE
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_collection_action (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    plan_id               BIGINT        NOT NULL                COMMENT '回款计划分期ID',
    contract_id           BIGINT        NOT NULL                COMMENT 'CRM 合同台账ID',
    action_type           VARCHAR(20)   NOT NULL                COMMENT '催收动作：CALL / VISIT / EMAIL / REMINDER / PROMISE',
    content               VARCHAR(1000) DEFAULT NULL            COMMENT '催收内容',
    promise_date          DATE          DEFAULT NULL            COMMENT '承诺还款日期',
    promise_amount        DECIMAL(18,2) DEFAULT NULL            COMMENT '承诺还款金额',
    operator_id           BIGINT        DEFAULT NULL            COMMENT '操作人',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_crm_collection_action_plan (tenant_id, plan_id, create_time)
) COMMENT='CRM 催收记录';
