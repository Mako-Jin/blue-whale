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

-- ---------------------------------------------------------------------
-- 10. 用车管理：车辆、司机、预约、出车登记
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_vehicle (
    id              BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    plate_no        VARCHAR(32)   NOT NULL            COMMENT '车牌号（租户内唯一）',
    vehicle_type    VARCHAR(32)   NOT NULL DEFAULT 'SEDAN' COMMENT '车型：SEDAN/SUV/MPV/BUS/OTHER',
    seats           INT           NOT NULL DEFAULT 5  COMMENT '核定座位数',
    status          VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED 启用 / DISABLED 停用',
    current_mileage DECIMAL(12,1) NOT NULL DEFAULT 0.0 COMMENT '当前里程（公里）',
    purchase_date   DATE          DEFAULT NULL        COMMENT '购置日期',
    remark          VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_vehicle_plate (tenant_id, plate_no),
    KEY idx_oa_vehicle_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 车辆主数据表';

CREATE TABLE IF NOT EXISTS oa_driver (
    id           BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    name         VARCHAR(64)  NOT NULL            COMMENT '司机姓名',
    phone        VARCHAR(32)  DEFAULT NULL        COMMENT '联系电话',
    license_no   VARCHAR(64)  NOT NULL            COMMENT '驾驶证号（租户内唯一）',
    license_type VARCHAR(16)  DEFAULT NULL        COMMENT '准驾车型',
    status       VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED 可派 / DISABLED 停用',
    remark       VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_driver_license (tenant_id, license_no),
    KEY idx_oa_driver_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 司机主数据表';

CREATE TABLE IF NOT EXISTS oa_vehicle_booking (
    id                   BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id            BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code                 VARCHAR(64)  NOT NULL            COMMENT '用车单号（租户内唯一）',
    applicant            VARCHAR(64)  NOT NULL            COMMENT '申请人账号',
    applicant_id         BIGINT       DEFAULT NULL        COMMENT '申请人用户ID（逻辑关联 sys_user.id）',
    dept_id              BIGINT       DEFAULT NULL        COMMENT '申请部门ID',
    vehicle_id           BIGINT       DEFAULT NULL        COMMENT '派车车辆ID（逻辑关联 oa_vehicle.id）',
    driver_id            BIGINT       DEFAULT NULL        COMMENT '派车司机ID（逻辑关联 oa_driver.id）',
    start_time           DATETIME     NOT NULL            COMMENT '用车开始时间',
    end_time             DATETIME     NOT NULL            COMMENT '用车结束时间',
    origin               VARCHAR(128) DEFAULT NULL        COMMENT '起点',
    destination          VARCHAR(128) DEFAULT NULL        COMMENT '终点',
    reason               VARCHAR(255) DEFAULT NULL        COMMENT '用车事由',
    status               VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                                                          COMMENT '状态：PENDING 待审批 / APPROVED 已通过 / REJECTED 已驳回 / CANCELLED 已撤销',
    approval_instance_id BIGINT       DEFAULT NULL        COMMENT '审批实例ID（逻辑关联 oa_approval_instance.id）',
    remark               VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted              TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_vehicle_booking_code (tenant_id, code),
    KEY idx_oa_vehicle_booking_time (tenant_id, start_time, end_time),
    KEY idx_oa_vehicle_booking_vehicle (tenant_id, vehicle_id, start_time),
    KEY idx_oa_vehicle_booking_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 用车预约表';

CREATE TABLE IF NOT EXISTS oa_vehicle_usage (
    id            BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    booking_id    BIGINT        NOT NULL            COMMENT '用车预约ID（逻辑关联 oa_vehicle_booking.id）',
    vehicle_id    BIGINT        NOT NULL            COMMENT '车辆ID（逻辑关联 oa_vehicle.id）',
    start_mileage DECIMAL(12,1) NOT NULL DEFAULT 0.0 COMMENT '出车里程（公里）',
    end_mileage   DECIMAL(12,1) NOT NULL DEFAULT 0.0 COMMENT '回车里程（公里）',
    fuel_cost     DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '油费（元）',
    remark        VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_vehicle_usage_booking (tenant_id, booking_id),
    KEY idx_oa_vehicle_usage_vehicle (tenant_id, vehicle_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 出车登记表';

-- ---------------------------------------------------------------------
-- 11. 用章管理：用章申请、用章文件
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_seal_use (
    id                   BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id            BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code                 VARCHAR(64)  NOT NULL            COMMENT '用章单号（租户内唯一）',
    applicant            VARCHAR(64)  NOT NULL            COMMENT '申请人账号',
    applicant_id         BIGINT       DEFAULT NULL        COMMENT '申请人用户ID（逻辑关联 sys_user.id）',
    dept_id              BIGINT       DEFAULT NULL        COMMENT '申请部门ID',
    seal_id              BIGINT       NOT NULL            COMMENT '印章ID（Contract 主数据 ct_seal.id）',
    seal_name            VARCHAR(64)  DEFAULT NULL        COMMENT '印章名称快照',
    reason               VARCHAR(255) NOT NULL            COMMENT '用章事由',
    file_count           INT          NOT NULL DEFAULT 0  COMMENT '用印文件数',
    use_time             DATETIME     DEFAULT NULL        COMMENT '用印时间',
    status               VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                                                          COMMENT '状态：PENDING 待审批 / APPROVED 已通过 / REJECTED 已驳回 / CANCELLED 已撤销',
    registered_time      DATETIME     DEFAULT NULL        COMMENT '用印登记时间',
    registered_by        BIGINT       DEFAULT NULL        COMMENT '用印登记人用户ID',
    approval_instance_id BIGINT       DEFAULT NULL        COMMENT '审批实例ID（逻辑关联 oa_approval_instance.id）',
    remark               VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted              TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_seal_use_code (tenant_id, code),
    KEY idx_oa_seal_use_applicant (tenant_id, applicant_id, create_time),
    KEY idx_oa_seal_use_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 用章申请表';

CREATE TABLE IF NOT EXISTS oa_seal_use_file (
    id          BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    seal_use_id BIGINT       NOT NULL            COMMENT '用章申请ID（逻辑关联 oa_seal_use.id）',
    file_id     BIGINT       DEFAULT NULL        COMMENT '文件ID（逻辑关联 oa_file_ref.id）',
    file_name   VARCHAR(255) NOT NULL            COMMENT '文件名',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_oa_seal_use_file_use (tenant_id, seal_use_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 用章文件表';

-- ---------------------------------------------------------------------
-- 12. 采购申请：采购申请、采购明细
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_purchase_request (
    id                   BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id            BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code                 VARCHAR(64)   NOT NULL            COMMENT '采购申请单号（租户内唯一）',
    applicant            VARCHAR(64)   NOT NULL            COMMENT '申请人账号',
    applicant_id         BIGINT        DEFAULT NULL        COMMENT '申请人用户ID（逻辑关联 sys_user.id）',
    dept_id              BIGINT        DEFAULT NULL        COMMENT '申请部门ID',
    reason               VARCHAR(255)  NOT NULL            COMMENT '采购事由',
    expected_date        DATE          DEFAULT NULL        COMMENT '期望到货日期',
    supplier_id          BIGINT        DEFAULT NULL        COMMENT '供应商ID（ERP 主数据，推送时必填）',
    warehouse_id         BIGINT        DEFAULT NULL        COMMENT '收货仓库ID（ERP 主数据，推送时必填）',
    total_amount         DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '合计金额（元，服务端按明细重算）',
    currency             VARCHAR(8)    NOT NULL DEFAULT 'CNY' COMMENT '币种',
    status               VARCHAR(16)   NOT NULL DEFAULT 'PENDING'
                                                          COMMENT '状态：PENDING 待审批 / APPROVED 已通过 / REJECTED 已驳回 / CANCELLED 已撤销',
    approval_instance_id BIGINT        DEFAULT NULL        COMMENT '审批实例ID（逻辑关联 oa_approval_instance.id）',
    erp_push_status      VARCHAR(16)   NOT NULL DEFAULT 'NONE'
                                                          COMMENT 'ERP 推送状态：NONE 未推送 / PENDING 待推送 / SENT 已推送 / FAILED 推送失败',
    erp_po_no            VARCHAR(64)   DEFAULT NULL        COMMENT 'ERP 采购订单号',
    remark               VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted              TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_purchase_request_code (tenant_id, code),
    KEY idx_oa_purchase_request_status (tenant_id, status, erp_push_status),
    KEY idx_oa_purchase_request_applicant (tenant_id, applicant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 采购申请表';

CREATE TABLE IF NOT EXISTS oa_purchase_item (
    id              BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    purchase_id     BIGINT        NOT NULL            COMMENT '采购申请ID（逻辑关联 oa_purchase_request.id）',
    line_no         INT           NOT NULL            COMMENT '行号（从 1 开始）',
    item_code       VARCHAR(64)   NOT NULL            COMMENT '物料编码（ERP 主数据）',
    item_name       VARCHAR(128)  DEFAULT NULL        COMMENT '物料名称',
    quantity        DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '数量',
    unit_price      DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '单价（元）',
    amount          DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '金额（元）',
    supplier_advice VARCHAR(255)  DEFAULT NULL        COMMENT '供应商建议',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_purchase_item_line (tenant_id, purchase_id, line_no),
    KEY idx_oa_purchase_item_purchase (tenant_id, purchase_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 采购明细表';

-- ---------------------------------------------------------------------
-- 13. 用车 / 用章 / 采购审批配置与审批人规则
-- ---------------------------------------------------------------------
INSERT INTO oa_approval_config
    (id, tenant_id, business_type, business_name, definition_code, title_template,
     business_url, cc_user_ids, enabled, remark, deleted)
SELECT 900000000000000831, 1, 'VEHICLE', '用车', 'OA_VEHICLE', '{applicant}的用车申请',
       '/oa/vehicle', NULL, 1, 'M2-5c 种子：用车审批配置', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_config WHERE tenant_id = 1 AND business_type = 'VEHICLE'
);
INSERT INTO oa_approval_route_rule
    (id, tenant_id, business_type, node_key, node_name, resolver_type, resolver_value,
     sort_no, enabled, remark, deleted)
SELECT 900000000000000832, 1, 'VEHICLE', 'dept_manager', '部门经理审批', 'DEPT_LEADER', NULL,
       10, 1, 'M2-5c 种子：用车部门经理审批人', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_route_rule
    WHERE tenant_id = 1 AND business_type = 'VEHICLE' AND node_key = 'dept_manager'
);

INSERT INTO oa_approval_config
    (id, tenant_id, business_type, business_name, definition_code, title_template,
     business_url, cc_user_ids, enabled, remark, deleted)
SELECT 900000000000000841, 1, 'SEAL_USE', '用章', 'OA_SEAL', '{applicant}的用章申请',
       '/oa/seal', NULL, 1, 'M2-5c 种子：用章审批配置', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_config WHERE tenant_id = 1 AND business_type = 'SEAL_USE'
);
INSERT INTO oa_approval_route_rule
    (id, tenant_id, business_type, node_key, node_name, resolver_type, resolver_value,
     sort_no, enabled, remark, deleted)
SELECT 900000000000000842, 1, 'SEAL_USE', 'dept_manager', '部门经理审批', 'DEPT_LEADER', NULL,
       10, 1, 'M2-5c 种子：用章部门经理审批人', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_route_rule
    WHERE tenant_id = 1 AND business_type = 'SEAL_USE' AND node_key = 'dept_manager'
);

INSERT INTO oa_approval_config
    (id, tenant_id, business_type, business_name, definition_code, title_template,
     business_url, cc_user_ids, enabled, remark, deleted)
SELECT 900000000000000851, 1, 'PURCHASE', '采购申请', 'OA_PURCHASE', '{applicant}的采购申请',
       '/oa/purchase', NULL, 1, 'M2-5c 种子：采购申请审批配置', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_config WHERE tenant_id = 1 AND business_type = 'PURCHASE'
);
INSERT INTO oa_approval_route_rule
    (id, tenant_id, business_type, node_key, node_name, resolver_type, resolver_value,
     sort_no, enabled, remark, deleted)
SELECT 900000000000000852, 1, 'PURCHASE', 'dept_manager', '部门经理审批', 'DEPT_LEADER', NULL,
       10, 1, 'M2-5c 种子：采购部门经理审批人', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_route_rule
    WHERE tenant_id = 1 AND business_type = 'PURCHASE' AND node_key = 'dept_manager'
);
INSERT INTO oa_approval_route_rule
    (id, tenant_id, business_type, node_key, node_name, resolver_type, resolver_value,
     sort_no, enabled, remark, deleted)
SELECT 900000000000000853, 1, 'PURCHASE', 'finance', '财务审批', 'ROLE', 'oa_finance',
       20, 1, 'M2-5c 种子：采购财务审批人', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_route_rule
    WHERE tenant_id = 1 AND business_type = 'PURCHASE' AND node_key = 'finance'
);

-- =====================================================================
-- 完成。校验建议：
--   SHOW COLUMNS FROM oa_expense;
--   SHOW COLUMNS FROM oa_vehicle_booking;
--   SELECT code, name FROM oa_expense_category WHERE deleted = 0 ORDER BY sort_no;
--   SELECT business_type, definition_code FROM oa_approval_config
--   WHERE business_type IN ('EXPENSE','VEHICLE','SEAL_USE','PURCHASE');
--   SELECT node_key, resolver_type, resolver_value FROM oa_approval_route_rule
--   WHERE business_type IN ('EXPENSE','VEHICLE','SEAL_USE','PURCHASE') ORDER BY business_type, sort_no;
-- =====================================================================
