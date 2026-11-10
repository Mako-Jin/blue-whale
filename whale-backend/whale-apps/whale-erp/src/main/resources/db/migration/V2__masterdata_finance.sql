-- =====================================================================
-- BlueWhale ERP - V2 主数据与财务核心（M0 + M1）
-- =====================================================================
-- 适用库   : bluewhale_erp（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/erp-module-panorama.md
-- 计划     : docs/plans/2026-11-03-wave5.78-erp-panorama.md
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS / INSERT IGNORE；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) 金额 DECIMAL(18,2)，汇率 DECIMAL(18,8)；
--            4) 表间逻辑外键，不建物理 FOREIGN KEY。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 科目表（总账记账维度；负债/权益/收入余额方向为 CREDIT）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_account (
    id            BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code          VARCHAR(32)   NOT NULL            COMMENT '科目编码（租户内唯一）',
    name          VARCHAR(64)   NOT NULL            COMMENT '科目名称',
    account_type  VARCHAR(16)   NOT NULL            COMMENT '科目类型：ASSET 资产 / LIABILITY 负债 / EQUITY 权益 / REVENUE 收入 / EXPENSE 费用',
    direction     VARCHAR(8)    NOT NULL            COMMENT '余额方向：DEBIT 借 / CREDIT 贷',
    parent_code   VARCHAR(32)   DEFAULT NULL        COMMENT '上级科目编码（顶级为 NULL）',
    leaf_flag     TINYINT       NOT NULL DEFAULT 1  COMMENT '是否末级科目：1 是 / 0 否（仅末级可记账）',
    status        VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED 启用 / DISABLED 禁用',
    remark        VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_code (tenant_id, code),
    KEY idx_account_type (account_type),
    KEY idx_account_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='会计科目表';

-- ---------------------------------------------------------------------
-- 2. 会计期间（记账日期必须落在 OPEN 期间；关账后只允许红冲）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_accounting_period (
    id            BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    period_code   CHAR(6)       NOT NULL            COMMENT '期间编码 yyyyMM',
    start_date    DATE          NOT NULL            COMMENT '期间开始日期',
    end_date      DATE          NOT NULL            COMMENT '期间结束日期',
    status        VARCHAR(16)   NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN 打开 / CLOSED 已关账',
    closed_at     DATETIME      DEFAULT NULL        COMMENT '关账时间',
    closed_by     BIGINT        DEFAULT NULL        COMMENT '关账人ID',
    remark        VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_period_code (tenant_id, period_code),
    KEY idx_period_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='会计期间表';

-- ---------------------------------------------------------------------
-- 3. 客户业务主数据（关系/商机归 CRM；本表只承接交易、开票与应收主体字段）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_customer (
    id            BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code          VARCHAR(64)   NOT NULL            COMMENT '客户编码（租户内唯一）',
    name          VARCHAR(128)  NOT NULL            COMMENT '客户名称',
    short_name    VARCHAR(64)   DEFAULT NULL        COMMENT '客户简称',
    tax_no        VARCHAR(64)   DEFAULT NULL        COMMENT '纳税人识别号',
    contact_name  VARCHAR(64)   DEFAULT NULL        COMMENT '联系人',
    contact_phone VARCHAR(32)   DEFAULT NULL        COMMENT '联系电话',
    credit_limit  DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '信用额度（0 表示不限制）',
    status        VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED 启用 / DISABLED 禁用',
    remark        VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_code (tenant_id, code),
    KEY idx_customer_name (name),
    KEY idx_customer_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户业务主数据表';

-- ---------------------------------------------------------------------
-- 4. 仓库主数据（M2 库存使用；M0 先建档）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_warehouse (
    id            BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code          VARCHAR(64)   NOT NULL            COMMENT '仓库编码（租户内唯一）',
    name          VARCHAR(128)  NOT NULL            COMMENT '仓库名称',
    manager       VARCHAR(64)   DEFAULT NULL        COMMENT '仓库负责人',
    address       VARCHAR(255)  DEFAULT NULL        COMMENT '仓库地址',
    status        VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED 启用 / DISABLED 禁用',
    remark        VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_warehouse_code (tenant_id, code),
    KEY idx_warehouse_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='仓库主数据表';

-- ---------------------------------------------------------------------
-- 5. 计量单位（参考数据，M0 只读）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_uom (
    id            BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code          VARCHAR(32)   NOT NULL            COMMENT '计量单位编码（租户内唯一）',
    name          VARCHAR(64)   NOT NULL            COMMENT '计量单位名称',
    precision_no  TINYINT       NOT NULL DEFAULT 0  COMMENT '数量小数位',
    status        VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED 启用 / DISABLED 禁用',
    remark        VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_uom_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='计量单位表';

-- ---------------------------------------------------------------------
-- 6. 币种（参考数据；base_flag=1 为本位币）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_currency (
    id            BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code          CHAR(3)       NOT NULL            COMMENT '币种代码 ISO 4217',
    name          VARCHAR(64)   NOT NULL            COMMENT '币种名称',
    symbol        VARCHAR(16)   DEFAULT NULL        COMMENT '币种符号',
    base_flag     TINYINT       NOT NULL DEFAULT 0  COMMENT '是否本位币：1 是 / 0 否',
    status        VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED 启用 / DISABLED 禁用',
    remark        VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_currency_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='币种表';

-- ---------------------------------------------------------------------
-- 7. 税码（参考数据；税率以小数存储，如 0.1300）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_tax_code (
    id            BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code          VARCHAR(32)   NOT NULL            COMMENT '税码（租户内唯一）',
    name          VARCHAR(64)   NOT NULL            COMMENT '税码名称',
    tax_rate      DECIMAL(8,4)  NOT NULL DEFAULT 0  COMMENT '税率（小数）',
    tax_type      VARCHAR(16)   NOT NULL DEFAULT 'VAT_OUTPUT' COMMENT '类型：VAT_OUTPUT 销项 / VAT_INPUT 进项 / OTHER 其他',
    status        VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED 启用 / DISABLED 禁用',
    remark        VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tax_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='税码表';

-- ---------------------------------------------------------------------
-- 8. 会计凭证头（DRAFT / POSTED / REVERSED；已记账不可改，只能红冲）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_fin_voucher (
    id             BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    voucher_no     VARCHAR(32)   NOT NULL            COMMENT '凭证号（租户+期间内唯一）',
    voucher_date   DATE          NOT NULL            COMMENT '凭证日期（必须落在 OPEN 期间）',
    period_code    CHAR(6)       NOT NULL            COMMENT '期间编码 yyyyMM',
    voucher_type   VARCHAR(16)   NOT NULL DEFAULT 'MANUAL' COMMENT '类型：MANUAL 手工 / REVERSAL 红冲',
    status         VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿 / POSTED 已记账 / REVERSED 已红冲',
    currency       CHAR(3)       NOT NULL DEFAULT 'CNY' COMMENT '凭证币种',
    exchange_rate  DECIMAL(18,8) NOT NULL DEFAULT 1 COMMENT '对本位币汇率',
    total_debit    DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '借方合计（原币）',
    total_credit   DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '贷方合计（原币）',
    source_type    VARCHAR(32)   DEFAULT NULL        COMMENT '来源类型（后续 SCM/MFG 自动记账）',
    source_id      VARCHAR(64)   DEFAULT NULL        COMMENT '来源单据ID（幂等键组成部分）',
    reversal_of_id BIGINT        DEFAULT NULL        COMMENT '红冲的原凭证ID',
    reversed_by_id BIGINT        DEFAULT NULL        COMMENT '被哪张红冲凭证冲销',
    remark         VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    posted_at      DATETIME      DEFAULT NULL        COMMENT '记账时间',
    posted_by      BIGINT        DEFAULT NULL        COMMENT '记账人ID',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_voucher_no (tenant_id, period_code, voucher_no),
    KEY idx_voucher_date (tenant_id, voucher_date),
    KEY idx_voucher_source (source_type, source_id),
    KEY idx_voucher_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='会计凭证头表';

-- ---------------------------------------------------------------------
-- 9. 会计凭证行（借贷平衡以行合计校验）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_fin_voucher_line (
    id             BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    voucher_id     BIGINT        NOT NULL            COMMENT '凭证ID',
    line_no        INT           NOT NULL            COMMENT '行号（凭证内从 1 递增）',
    account_code   VARCHAR(32)   NOT NULL            COMMENT '科目编码',
    account_name   VARCHAR(64)   NOT NULL            COMMENT '科目名称（快照）',
    summary        VARCHAR(255)  DEFAULT NULL        COMMENT '摘要',
    debit_amount   DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '借方金额（原币）',
    credit_amount  DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '贷方金额（原币）',
    currency       CHAR(3)       NOT NULL DEFAULT 'CNY' COMMENT '币种',
    exchange_rate  DECIMAL(18,8) NOT NULL DEFAULT 1 COMMENT '对本位币汇率',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_voucher_line_voucher (voucher_id),
    KEY idx_voucher_line_account (tenant_id, account_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='会计凭证行表';

-- ---------------------------------------------------------------------
-- 10. 应收台账（核销额 ≤ 金额；账龄按到期日计算）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_fin_receivable (
    id              BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    doc_no          VARCHAR(32)   NOT NULL            COMMENT '应收单号（租户内唯一）',
    customer_id     BIGINT        NOT NULL            COMMENT '客户ID',
    customer_name   VARCHAR(128)  NOT NULL            COMMENT '客户名称快照',
    source_type     VARCHAR(32)   DEFAULT NULL        COMMENT '来源类型',
    source_id       VARCHAR(64)   DEFAULT NULL        COMMENT '来源单据ID',
    doc_date        DATE          NOT NULL            COMMENT '单据日期',
    due_date        DATE          NOT NULL            COMMENT '到期日',
    currency        CHAR(3)       NOT NULL DEFAULT 'CNY' COMMENT '币种',
    amount          DECIMAL(18,2) NOT NULL            COMMENT '应收金额',
    settled_amount  DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '已核销金额',
    bad_debt_amount DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '已计提坏账金额',
    status          VARCHAR(16)   NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN 未结 / PARTIALLY_SETTLED 部分核销 / SETTLED 已核销 / WRITTEN_OFF 已核销坏账',
    remark          VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_receivable_doc_no (tenant_id, doc_no),
    KEY idx_receivable_customer (tenant_id, customer_id),
    KEY idx_receivable_due (tenant_id, due_date),
    KEY idx_receivable_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应收台账表';

-- ---------------------------------------------------------------------
-- 11. 应收核销流水（只追加）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_fin_receivable_settlement (
    id             BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    receivable_id  BIGINT        NOT NULL            COMMENT '应收单ID',
    settle_date    DATE          NOT NULL            COMMENT '核销日期',
    amount         DECIMAL(18,2) NOT NULL            COMMENT '核销金额',
    settle_type    VARCHAR(16)   NOT NULL DEFAULT 'BANK' COMMENT '方式：CASH 现金 / BANK 银行 / OFFSET 冲抵',
    reference_no   VARCHAR(64)   DEFAULT NULL        COMMENT '回款单号/流水号',
    remark         VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_receivable_settle (tenant_id, receivable_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应收核销流水表';

-- ---------------------------------------------------------------------
-- 12. 应付台账
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_fin_payable (
    id              BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    doc_no          VARCHAR(32)   NOT NULL            COMMENT '应付单号（租户内唯一）',
    supplier_id     BIGINT        NOT NULL            COMMENT '供应商ID',
    supplier_name   VARCHAR(128)  NOT NULL            COMMENT '供应商名称快照',
    source_type     VARCHAR(32)   DEFAULT NULL        COMMENT '来源类型',
    source_id       VARCHAR(64)   DEFAULT NULL        COMMENT '来源单据ID',
    doc_date        DATE          NOT NULL            COMMENT '单据日期',
    due_date        DATE          NOT NULL            COMMENT '到期日',
    currency        CHAR(3)       NOT NULL DEFAULT 'CNY' COMMENT '币种',
    amount          DECIMAL(18,2) NOT NULL            COMMENT '应付金额',
    settled_amount  DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '已核销金额',
    status          VARCHAR(16)   NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN 未结 / PARTIALLY_SETTLED 部分核销 / SETTLED 已核销',
    remark          VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payable_doc_no (tenant_id, doc_no),
    KEY idx_payable_supplier (tenant_id, supplier_id),
    KEY idx_payable_due (tenant_id, due_date),
    KEY idx_payable_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应付台账表';

-- ---------------------------------------------------------------------
-- 13. 应付核销流水（只追加）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_fin_payable_settlement (
    id             BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    payable_id     BIGINT        NOT NULL            COMMENT '应付单ID',
    settle_date    DATE          NOT NULL            COMMENT '付款日期',
    amount         DECIMAL(18,2) NOT NULL            COMMENT '付款金额',
    settle_type    VARCHAR(16)   NOT NULL DEFAULT 'BANK' COMMENT '方式：CASH 现金 / BANK 银行 / OFFSET 冲抵',
    reference_no   VARCHAR(64)   DEFAULT NULL        COMMENT '付款单号/流水号',
    remark         VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_payable_settle (tenant_id, payable_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='应付核销流水表';

-- ---------------------------------------------------------------------
-- 14. 种子：科目表（企业会计准则常用末级科目，可后续按需调整）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO erp_account (id, tenant_id, code, name, account_type, direction, leaf_flag, status, remark)
VALUES
    (7001, 1, '1001', '库存现金', 'ASSET', 'DEBIT', 1, 'ENABLED', '内置科目'),
    (7002, 1, '1002', '银行存款', 'ASSET', 'DEBIT', 1, 'ENABLED', '内置科目'),
    (7003, 1, '1122', '应收账款', 'ASSET', 'DEBIT', 1, 'ENABLED', '内置科目'),
    (7004, 1, '1123', '预付账款', 'ASSET', 'DEBIT', 1, 'ENABLED', '内置科目'),
    (7005, 1, '1405', '库存商品', 'ASSET', 'DEBIT', 1, 'ENABLED', '内置科目'),
    (7006, 1, '1601', '固定资产', 'ASSET', 'DEBIT', 1, 'ENABLED', '内置科目'),
    (7007, 1, '1602', '累计折旧', 'ASSET', 'CREDIT', 1, 'ENABLED', '内置科目'),
    (7008, 1, '2202', '应付账款', 'LIABILITY', 'CREDIT', 1, 'ENABLED', '内置科目'),
    (7009, 1, '2211', '应付职工薪酬', 'LIABILITY', 'CREDIT', 1, 'ENABLED', '内置科目'),
    (7010, 1, '2221', '应交税费', 'LIABILITY', 'CREDIT', 1, 'ENABLED', '内置科目'),
    (7011, 1, '4001', '实收资本', 'EQUITY', 'CREDIT', 1, 'ENABLED', '内置科目'),
    (7012, 1, '4103', '本年利润', 'EQUITY', 'CREDIT', 1, 'ENABLED', '内置科目'),
    (7013, 1, '4104', '利润分配', 'EQUITY', 'CREDIT', 1, 'ENABLED', '内置科目'),
    (7014, 1, '6001', '主营业务收入', 'REVENUE', 'CREDIT', 1, 'ENABLED', '内置科目'),
    (7015, 1, '6051', '其他业务收入', 'REVENUE', 'CREDIT', 1, 'ENABLED', '内置科目'),
    (7016, 1, '6401', '主营业务成本', 'EXPENSE', 'DEBIT', 1, 'ENABLED', '内置科目'),
    (7017, 1, '6601', '销售费用', 'EXPENSE', 'DEBIT', 1, 'ENABLED', '内置科目'),
    (7018, 1, '6602', '管理费用', 'EXPENSE', 'DEBIT', 1, 'ENABLED', '内置科目'),
    (7019, 1, '6603', '财务费用', 'EXPENSE', 'DEBIT', 1, 'ENABLED', '内置科目'),
    (7020, 1, '5001', '生产成本', 'EXPENSE', 'DEBIT', 1, 'ENABLED', '内置科目（M3 成本归集使用）');

-- ---------------------------------------------------------------------
-- 15. 种子：2026 年 1-12 月会计期间（默认全部 OPEN，按需关账）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO erp_accounting_period (id, tenant_id, period_code, start_date, end_date, status, remark)
VALUES
    (7101, 1, '202601', '2026-01-01', '2026-01-31', 'OPEN', '内置期间'),
    (7102, 1, '202602', '2026-02-01', '2026-02-28', 'OPEN', '内置期间'),
    (7103, 1, '202603', '2026-03-01', '2026-03-31', 'OPEN', '内置期间'),
    (7104, 1, '202604', '2026-04-01', '2026-04-30', 'OPEN', '内置期间'),
    (7105, 1, '202605', '2026-05-01', '2026-05-31', 'OPEN', '内置期间'),
    (7106, 1, '202606', '2026-06-01', '2026-06-30', 'OPEN', '内置期间'),
    (7107, 1, '202607', '2026-07-01', '2026-07-31', 'OPEN', '内置期间'),
    (7108, 1, '202608', '2026-08-01', '2026-08-31', 'OPEN', '内置期间'),
    (7109, 1, '202609', '2026-09-01', '2026-09-30', 'OPEN', '内置期间'),
    (7110, 1, '202610', '2026-10-01', '2026-10-31', 'OPEN', '内置期间'),
    (7111, 1, '202611', '2026-11-01', '2026-11-30', 'OPEN', '内置期间'),
    (7112, 1, '202612', '2026-12-01', '2026-12-31', 'OPEN', '内置期间');

-- ---------------------------------------------------------------------
-- 16. 种子：参考数据（计量单位 / 币种 / 税码）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO erp_uom (id, tenant_id, code, name, precision_no, status, remark)
VALUES
    (7401, 1, 'PCS', '件', 0, 'ENABLED', '内置单位'),
    (7402, 1, 'KG', '千克', 3, 'ENABLED', '内置单位'),
    (7403, 1, 'M', '米', 2, 'ENABLED', '内置单位'),
    (7404, 1, 'HOUR', '小时', 2, 'ENABLED', '内置单位');

INSERT IGNORE INTO erp_currency (id, tenant_id, code, name, symbol, base_flag, status, remark)
VALUES
    (7501, 1, 'CNY', '人民币', '¥', 1, 'ENABLED', '本位币'),
    (7502, 1, 'USD', '美元', '$', 0, 'ENABLED', '内置币种');

INSERT IGNORE INTO erp_tax_code (id, tenant_id, code, name, tax_rate, tax_type, status, remark)
VALUES
    (7601, 1, 'VAT13', '增值税 13%', 0.1300, 'VAT_OUTPUT', 'ENABLED', '内置税码'),
    (7602, 1, 'VAT9', '增值税 9%', 0.0900, 'VAT_OUTPUT', 'ENABLED', '内置税码'),
    (7603, 1, 'VAT6', '增值税 6%', 0.0600, 'VAT_OUTPUT', 'ENABLED', '内置税码'),
    (7604, 1, 'VAT0', '免税/零税率', 0.0000, 'VAT_OUTPUT', 'ENABLED', '内置税码');

-- ---------------------------------------------------------------------
-- 17. 种子：仓库与客户业务主数据（便于联调）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO erp_warehouse (id, tenant_id, code, name, manager, address, status, remark)
VALUES
    (7301, 1, 'WH-001', '主仓', '王五', '上海市浦东新区示例路 1 号', 'ENABLED', '内置种子');

INSERT IGNORE INTO erp_customer (id, tenant_id, code, name, short_name, tax_no, contact_name, contact_phone,
                                 credit_limit, status, remark)
VALUES
    (7201, 1, 'CUS-001', '示例客户 A', '客户A', '91310000XXXXXXXX1A', '赵六', '13900000001', 100000.00, 'ENABLED', '内置种子'),
    (7202, 1, 'CUS-002', '示例客户 B', '客户B', '91310000XXXXXXXX2B', '钱七', '13900000002', 50000.00, 'ENABLED', '内置种子');
