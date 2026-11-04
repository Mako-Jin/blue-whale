-- =====================================================================
-- BlueWhale ERP - V6 轻量人力（M4：组织/员工/异动/考勤/薪资/绩效）
-- =====================================================================
-- 适用库   : bluewhale_erp（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/erp-module-panorama.md §3.5 / §6.2
-- 计划     : docs/plans/2026-11-03-wave5.78-erp-panorama.md §M4
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS / INSERT IGNORE；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) 金额 DECIMAL(18,2)，工时/天数 DECIMAL(18,2)；
--            4) 表间逻辑外键，不建物理 FOREIGN KEY；
--            5) 薪资期间租户内唯一（uk_payroll_period）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 组织单元（扁平层次，parent_code 逻辑引用）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_hr_org_unit (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code              VARCHAR(64)   NOT NULL            COMMENT '组织编码（租户内唯一）',
    name              VARCHAR(128)  NOT NULL            COMMENT '组织名称',
    parent_code       VARCHAR(64)   DEFAULT NULL        COMMENT '上级组织编码（顶层为空）',
    manager           VARCHAR(64)   DEFAULT NULL        COMMENT '负责人',
    status            VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_hr_org_code (tenant_id, code),
    KEY idx_hr_org_parent (tenant_id, parent_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='组织单元';

-- ---------------------------------------------------------------------
-- 2. 员工档案（user_id 关联平台账号，供「我的工资条」数据范围）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_hr_employee (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    employee_no       VARCHAR(64)   NOT NULL            COMMENT '员工编号（租户内唯一）',
    name              VARCHAR(64)   NOT NULL            COMMENT '姓名',
    gender            VARCHAR(8)    DEFAULT NULL        COMMENT '性别：MALE/FEMALE/UNKNOWN',
    phone             VARCHAR(32)   DEFAULT NULL        COMMENT '手机号',
    email             VARCHAR(128)  DEFAULT NULL        COMMENT '邮箱',
    org_code          VARCHAR(64)   NOT NULL            COMMENT '所属组织编码',
    position          VARCHAR(64)   DEFAULT NULL        COMMENT '岗位',
    hire_date         DATE          NOT NULL            COMMENT '入职日期',
    leave_date        DATE          DEFAULT NULL        COMMENT '离职日期',
    status            VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/LEFT',
    base_salary       DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '月基本工资',
    allowance         DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '固定津贴',
    social_base       DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '社保/公积金基数（0 表示取基本工资）',
    user_id           BIGINT        DEFAULT NULL        COMMENT '平台账号ID',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_hr_employee_no (tenant_id, employee_no),
    KEY idx_hr_employee_org (tenant_id, org_code, status),
    KEY idx_hr_employee_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='员工档案';

-- ---------------------------------------------------------------------
-- 3. 员工异动（入职/调动/晋升/离职，生效后回写员工档案）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_hr_employment_change (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    change_no         VARCHAR(64)   NOT NULL            COMMENT '异动单号（租户内唯一）',
    employee_no       VARCHAR(64)   NOT NULL            COMMENT '员工编号',
    change_type       VARCHAR(16)   NOT NULL            COMMENT '类型：HIRE/TRANSFER/PROMOTION/RESIGN/RETURN',
    effective_date    DATE          NOT NULL            COMMENT '生效日期',
    from_org_code     VARCHAR(64)   DEFAULT NULL        COMMENT '原组织编码',
    to_org_code       VARCHAR(64)   DEFAULT NULL        COMMENT '新组织编码',
    from_position     VARCHAR(64)   DEFAULT NULL        COMMENT '原岗位',
    to_position       VARCHAR(64)   DEFAULT NULL        COMMENT '新岗位',
    from_salary       DECIMAL(18,2) DEFAULT NULL        COMMENT '原月薪',
    to_salary         DECIMAL(18,2) DEFAULT NULL        COMMENT '新月薪',
    status            VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/EFFECTIVE/CANCELLED',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_hr_change_no (tenant_id, change_no),
    KEY idx_hr_change_employee (tenant_id, employee_no, effective_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='员工异动单';

-- ---------------------------------------------------------------------
-- 4. 考勤汇总（员工 + 期间唯一，供薪资计算）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_hr_attendance_summary (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    employee_no       VARCHAR(64)   NOT NULL            COMMENT '员工编号',
    period            VARCHAR(16)   NOT NULL            COMMENT '考勤期间（YYYY-MM）',
    work_days         DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '应出勤天数',
    actual_days       DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '实际出勤天数',
    leave_hours       DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '请假小时数',
    overtime_hours    DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '加班小时数',
    absent_days       DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '缺勤天数',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_hr_attendance (tenant_id, employee_no, period),
    KEY idx_hr_attendance_period (tenant_id, period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='考勤汇总';

-- ---------------------------------------------------------------------
-- 5. 薪资批次（期间唯一：DRAFT → CALCULATED → APPROVED → PAID → CLOSED）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_hr_payroll_run (
    id                     BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id              BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    run_no                 VARCHAR(64)   NOT NULL            COMMENT '薪资批次号（租户内唯一）',
    period                 VARCHAR(16)   NOT NULL            COMMENT '薪资期间（YYYY-MM，租户内唯一）',
    status                 VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/CALCULATED/APPROVED/PAID/CLOSED/CANCELLED',
    employee_count         INT           NOT NULL DEFAULT 0  COMMENT '员工人数',
    total_gross            DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '应发合计',
    total_social_insurance DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '社保合计（个人部分）',
    total_housing_fund     DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '公积金合计（个人部分）',
    total_tax              DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '个人所得税合计',
    total_net              DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '实发合计',
    remark                 VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted                TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payroll_period (tenant_id, period),
    UNIQUE KEY uk_payroll_run_no (tenant_id, run_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='薪资批次';

-- ---------------------------------------------------------------------
-- 6. 工资条（批次 + 员工唯一；仅本人可见由权限与数据范围控制）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_hr_payslip (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    payroll_id        BIGINT        NOT NULL            COMMENT '薪资批次ID',
    employee_no       VARCHAR(64)   NOT NULL            COMMENT '员工编号',
    employee_name     VARCHAR(64)   NOT NULL            COMMENT '员工姓名快照',
    org_code          VARCHAR(64)   DEFAULT NULL        COMMENT '组织编码快照',
    base_salary       DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '基本工资',
    allowance         DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '固定津贴',
    overtime_pay      DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '加班费',
    leave_deduction   DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '请假扣款',
    absence_deduction DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '缺勤扣款',
    gross_pay         DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '应发工资',
    social_insurance  DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '社保个人部分',
    housing_fund      DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '公积金个人部分',
    tax               DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '个人所得税',
    net_pay           DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '实发工资',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payslip_employee (tenant_id, payroll_id, employee_no),
    KEY idx_payslip_employee (tenant_id, employee_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工资条';

-- ---------------------------------------------------------------------
-- 7. 绩效周期（DRAFT → IN_PROGRESS → CLOSED）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_hr_performance_cycle (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    cycle_no          VARCHAR(64)   NOT NULL            COMMENT '周期编号（租户内唯一）',
    name              VARCHAR(128)  NOT NULL            COMMENT '周期名称',
    period            VARCHAR(16)   NOT NULL            COMMENT '考核期间（YYYY-MM）',
    start_date        DATE          NOT NULL            COMMENT '考核开始日期',
    end_date          DATE          NOT NULL            COMMENT '考核结束日期',
    status            VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/IN_PROGRESS/CLOSED',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_hr_cycle_no (tenant_id, cycle_no),
    KEY idx_hr_cycle_period (tenant_id, period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='绩效周期';

-- ---------------------------------------------------------------------
-- 8. 绩效指标（周期内名称唯一，权重百分比合计建议 100）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_hr_kpi (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    cycle_id          BIGINT        NOT NULL            COMMENT '绩效周期ID',
    name              VARCHAR(128)  NOT NULL            COMMENT '指标名称',
    weight            DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '权重（百分比）',
    target_value      DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '目标值',
    unit              VARCHAR(32)   DEFAULT NULL        COMMENT '计量单位',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_hr_kpi_name (tenant_id, cycle_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='绩效指标';

-- ---------------------------------------------------------------------
-- 9. 绩效考核结果（周期 + 员工 + 指标唯一；等级 A/B/C/D 由分数推导）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_hr_appraisal (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    cycle_id          BIGINT        NOT NULL            COMMENT '绩效周期ID',
    kpi_id            BIGINT        NOT NULL            COMMENT '绩效指标ID',
    kpi_name          VARCHAR(128)  NOT NULL            COMMENT '指标名称快照',
    employee_no       VARCHAR(64)   NOT NULL            COMMENT '员工编号',
    employee_name     VARCHAR(64)   NOT NULL            COMMENT '员工姓名快照',
    score             DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '得分（0-100）',
    grade             VARCHAR(8)    NOT NULL DEFAULT 'D' COMMENT '等级：A/B/C/D',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '评语',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_hr_appraisal (tenant_id, cycle_id, employee_no, kpi_id),
    KEY idx_hr_appraisal_cycle (tenant_id, cycle_id, employee_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='绩效考核结果';

-- ---------------------------------------------------------------------
-- 10. 幂等种子：其他应付款（薪资代扣社保/公积金挂账）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO erp_account
    (id, tenant_id, code, name, account_type, direction, leaf, status, remark)
VALUES
    (7023, 1, '2241', '其他应付款', 'LIABILITY', 'CREDIT', 1, 'ENABLED', '内置科目（M4 薪资代扣挂账）');
