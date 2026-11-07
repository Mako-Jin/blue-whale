-- =====================================================================
-- BlueWhale 微服务平台 - OA 应用（whale-oa） Flyway 版本化迁移 V8
-- =====================================================================
-- 适用库   : bluewhale_oa（MySQL 8.0+，utf8mb4）
-- 上级计划 : docs/plans/2026-11-04-wave5.82-oa-panorama.md
-- 里程碑   : Wave 5.82 M2-5b 报销申请（含 M1B 预算超限条件分支配置）
-- 说明     : 1) 新建报销单、明细、发票、费用类别、费用标准 5 张表；
--            2) 种子常用费用类别与默认标准；
--            3) 种子 EXPENSE 审批配置（OA_EXPENSE）与部门经理 / 财务角色审批规则。
-- 幂等     : CREATE TABLE IF NOT EXISTS；种子用 INSERT ... WHERE NOT EXISTS。
-- 规则     : 禁止 DROP / TRUNCATE / DELETE；表间逻辑外键，不建物理 FOREIGN KEY。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 费用类别主数据（类别编码租户内唯一）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_expense_category (
    id             BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code           VARCHAR(32)   NOT NULL            COMMENT '类别编码（租户内唯一）',
    name           VARCHAR(64)   NOT NULL            COMMENT '类别名称',
    account_subject VARCHAR(64)  DEFAULT NULL        COMMENT '会计科目',
    require_invoice TINYINT      NOT NULL DEFAULT 1  COMMENT '是否需要发票：0 否 / 1 是',
    single_limit   DECIMAL(12,2) DEFAULT NULL        COMMENT '单笔限额（元，空表示不限）',
    sort_no        INT           NOT NULL DEFAULT 0  COMMENT '排序号',
    enabled        TINYINT       NOT NULL DEFAULT 1  COMMENT '是否启用：0 停用 / 1 启用',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_expense_category_code (tenant_id, code),
    KEY idx_oa_expense_category_enabled (tenant_id, enabled, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 费用类别表';

-- ---------------------------------------------------------------------
-- 2. 费用标准（类别 × 职级/城市 → 限额）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_expense_standard (
    id            BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    category_id   BIGINT        NOT NULL            COMMENT '费用类别ID（逻辑关联 oa_expense_category.id）',
    level_code    VARCHAR(32)   NOT NULL DEFAULT 'DEFAULT'
                                                  COMMENT '适用职级/城市编码（DEFAULT 表示默认标准）',
    limit_amount  DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '限额（元）',
    enabled       TINYINT       NOT NULL DEFAULT 1  COMMENT '是否启用：0 停用 / 1 启用',
    remark        VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_expense_standard_scope (tenant_id, category_id, level_code),
    KEY idx_oa_expense_standard_category (tenant_id, category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 费用标准表';

-- ---------------------------------------------------------------------
-- 3. 报销单（单号租户内唯一；合计金额由服务端按明细重算）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_expense (
    id                   BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id            BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code                 VARCHAR(64)   NOT NULL            COMMENT '报销单号（租户内唯一）',
    applicant            VARCHAR(64)   NOT NULL            COMMENT '申请人账号',
    applicant_id         BIGINT        DEFAULT NULL        COMMENT '申请人用户ID（逻辑关联 sys_user.id）',
    dept_id              BIGINT        DEFAULT NULL        COMMENT '申请部门ID',
    category_code        VARCHAR(32)   NOT NULL            COMMENT '主费用类别编码（逻辑关联 oa_expense_category.code）',
    title                VARCHAR(128)  NOT NULL            COMMENT '报销事由标题',
    total_amount         DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '合计金额（元，服务端按明细重算）',
    currency             VARCHAR(8)    NOT NULL DEFAULT 'CNY' COMMENT '币种',
    status               VARCHAR(16)   NOT NULL DEFAULT 'PENDING'
                                                          COMMENT '状态：PENDING 待审批 / APPROVED 已通过 / REJECTED 已驳回 / CANCELLED 已撤销',
    approval_instance_id BIGINT        DEFAULT NULL        COMMENT '审批实例ID（逻辑关联 oa_approval_instance.id）',
    erp_push_status      VARCHAR(16)   NOT NULL DEFAULT 'NONE'
                                                          COMMENT 'ERP 推送状态：NONE 未推送 / PENDING 待推送 / SENT 已推送 / FAILED 推送失败',
    erp_payable_no       VARCHAR(64)   DEFAULT NULL        COMMENT 'ERP 应付单号',
    submit_time          DATETIME      DEFAULT NULL        COMMENT '提交时间',
    remark               VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted              TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_expense_code (tenant_id, code),
    KEY idx_oa_expense_applicant (tenant_id, applicant_id, create_time),
    KEY idx_oa_expense_status (tenant_id, status, submit_time),
    KEY idx_oa_expense_push (tenant_id, erp_push_status, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 报销单表';

-- ---------------------------------------------------------------------
-- 4. 报销明细（同一报销单内行号唯一）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_expense_item (
    id            BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    expense_id    BIGINT        NOT NULL            COMMENT '报销单ID（逻辑关联 oa_expense.id）',
    line_no       INT           NOT NULL            COMMENT '行号（从 1 开始）',
    category_code VARCHAR(32)   NOT NULL            COMMENT '费用类别编码',
    expense_date  DATE          NOT NULL            COMMENT '费用发生日期',
    amount        DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '金额（元）',
    tax_amount    DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '税额（元）',
    description   VARCHAR(255)  DEFAULT NULL        COMMENT '费用说明',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_expense_item_line (tenant_id, expense_id, line_no),
    KEY idx_oa_expense_item_expense (tenant_id, expense_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 报销明细表';

-- ---------------------------------------------------------------------
-- 5. 报销发票（发票代码 + 号码租户内全局唯一，拦截跨单重复）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_expense_invoice (
    id           BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    expense_id   BIGINT        NOT NULL            COMMENT '报销单ID（逻辑关联 oa_expense.id）',
    invoice_code VARCHAR(32)   DEFAULT NULL        COMMENT '发票代码（纸质票）',
    invoice_no   VARCHAR(64)   NOT NULL            COMMENT '发票号码',
    invoice_date DATE          DEFAULT NULL        COMMENT '开票日期',
    amount       DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '发票金额（元）',
    tax_amount   DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '发票税额（元）',
    file_id      BIGINT        DEFAULT NULL        COMMENT '发票影像文件ID（逻辑关联 oa_file_ref.id）',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_expense_invoice_no (tenant_id, invoice_code, invoice_no),
    KEY idx_oa_expense_invoice_expense (tenant_id, expense_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 报销发票表';

-- ---------------------------------------------------------------------
-- 6. 常用费用类别种子
-- ---------------------------------------------------------------------
INSERT INTO oa_expense_category
    (id, tenant_id, code, name, account_subject, require_invoice, single_limit, sort_no, enabled, deleted)
SELECT 900000000000000801, 1, 'TRAVEL', '差旅费', '6601 管理费用-差旅费', 1, NULL, 10, 1, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM oa_expense_category WHERE tenant_id = 1 AND code = 'TRAVEL');
INSERT INTO oa_expense_category
    (id, tenant_id, code, name, account_subject, require_invoice, single_limit, sort_no, enabled, deleted)
SELECT 900000000000000802, 1, 'MEAL', '餐饮费', '6601 管理费用-业务招待费', 1, NULL, 20, 1, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM oa_expense_category WHERE tenant_id = 1 AND code = 'MEAL');
INSERT INTO oa_expense_category
    (id, tenant_id, code, name, account_subject, require_invoice, single_limit, sort_no, enabled, deleted)
SELECT 900000000000000803, 1, 'OFFICE', '办公费', '6601 管理费用-办公费', 1, NULL, 30, 1, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM oa_expense_category WHERE tenant_id = 1 AND code = 'OFFICE');
INSERT INTO oa_expense_category
    (id, tenant_id, code, name, account_subject, require_invoice, single_limit, sort_no, enabled, deleted)
SELECT 900000000000000804, 1, 'OTHER', '其他费用', '6601 管理费用-其他', 0, NULL, 40, 1, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM oa_expense_category WHERE tenant_id = 1 AND code = 'OTHER');

-- ---------------------------------------------------------------------
-- 7. 默认费用标准种子（DEFAULT 职级/城市）
-- ---------------------------------------------------------------------
INSERT INTO oa_expense_standard
    (id, tenant_id, category_id, level_code, limit_amount, enabled, remark, deleted)
SELECT 900000000000000811, 1, c.id, 'DEFAULT', 2000.00, 1, 'M2-5b 种子：差旅费默认标准', 0
FROM oa_expense_category c
WHERE c.tenant_id = 1 AND c.code = 'TRAVEL'
  AND NOT EXISTS (
      SELECT 1 FROM oa_expense_standard s
      WHERE s.tenant_id = 1 AND s.category_id = c.id AND s.level_code = 'DEFAULT'
  );
INSERT INTO oa_expense_standard
    (id, tenant_id, category_id, level_code, limit_amount, enabled, remark, deleted)
SELECT 900000000000000812, 1, c.id, 'DEFAULT', 300.00, 1, 'M2-5b 种子：餐饮费默认标准', 0
FROM oa_expense_category c
WHERE c.tenant_id = 1 AND c.code = 'MEAL'
  AND NOT EXISTS (
      SELECT 1 FROM oa_expense_standard s
      WHERE s.tenant_id = 1 AND s.category_id = c.id AND s.level_code = 'DEFAULT'
  );
INSERT INTO oa_expense_standard
    (id, tenant_id, category_id, level_code, limit_amount, enabled, remark, deleted)
SELECT 900000000000000813, 1, c.id, 'DEFAULT', 1000.00, 1, 'M2-5b 种子：办公费默认标准', 0
FROM oa_expense_category c
WHERE c.tenant_id = 1 AND c.code = 'OFFICE'
  AND NOT EXISTS (
      SELECT 1 FROM oa_expense_standard s
      WHERE s.tenant_id = 1 AND s.category_id = c.id AND s.level_code = 'DEFAULT'
  );
INSERT INTO oa_expense_standard
    (id, tenant_id, category_id, level_code, limit_amount, enabled, remark, deleted)
SELECT 900000000000000814, 1, c.id, 'DEFAULT', 500.00, 1, 'M2-5b 种子：其他费用默认标准', 0
FROM oa_expense_category c
WHERE c.tenant_id = 1 AND c.code = 'OTHER'
  AND NOT EXISTS (
      SELECT 1 FROM oa_expense_standard s
      WHERE s.tenant_id = 1 AND s.category_id = c.id AND s.level_code = 'DEFAULT'
  );

-- ---------------------------------------------------------------------
-- 8. 报销审批业务配置（business_type = EXPENSE → OA_EXPENSE）
-- ---------------------------------------------------------------------
INSERT INTO oa_approval_config
    (id, tenant_id, business_type, business_name, definition_code, title_template,
     business_url, cc_user_ids, enabled, remark, deleted)
SELECT 900000000000000821, 1, 'EXPENSE', '报销', 'OA_EXPENSE', '{applicant}的报销申请',
       '/oa/expense', NULL, 1, 'M2-5b 种子：报销审批配置（预算超限条件分支）', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_config
    WHERE tenant_id = 1 AND business_type = 'EXPENSE'
);

-- ---------------------------------------------------------------------
-- 9. 报销审批人解析规则：部门经理 + 财务角色
-- ---------------------------------------------------------------------
INSERT INTO oa_approval_route_rule
    (id, tenant_id, business_type, node_key, node_name, resolver_type, resolver_value,
     sort_no, enabled, remark, deleted)
SELECT 900000000000000822, 1, 'EXPENSE', 'dept_manager', '部门经理审批', 'DEPT_LEADER', NULL,
       10, 1, 'M2-5b 种子：报销部门经理审批人', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_route_rule
    WHERE tenant_id = 1 AND business_type = 'EXPENSE' AND node_key = 'dept_manager'
);
INSERT INTO oa_approval_route_rule
    (id, tenant_id, business_type, node_key, node_name, resolver_type, resolver_value,
     sort_no, enabled, remark, deleted)
SELECT 900000000000000823, 1, 'EXPENSE', 'finance', '财务审批', 'ROLE', 'oa_finance',
       20, 1, 'M2-5b 种子：报销财务审批人', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_route_rule
    WHERE tenant_id = 1 AND business_type = 'EXPENSE' AND node_key = 'finance'
);

-- =====================================================================
-- 完成。校验建议：
--   SHOW COLUMNS FROM oa_expense;
--   SELECT code, name FROM oa_expense_category WHERE deleted = 0 ORDER BY sort_no;
--   SELECT business_type, definition_code FROM oa_approval_config WHERE business_type = 'EXPENSE';
--   SELECT node_key, resolver_type, resolver_value FROM oa_approval_route_rule
--   WHERE business_type = 'EXPENSE' ORDER BY sort_no;
-- =====================================================================
