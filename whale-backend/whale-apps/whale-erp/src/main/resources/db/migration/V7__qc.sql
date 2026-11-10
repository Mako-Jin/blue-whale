-- =====================================================================
-- BlueWhale ERP - V7 质量与设备（M5：检验 / 不合格品 / 设备 / 量具）
-- =====================================================================
-- 适用库   : bluewhale_erp（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/erp-module-panorama.md §3.5 / §5.2 / §6.2
-- 计划     : docs/plans/2026-11-03-wave5.78-erp-panorama.md §M5
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS / INSERT IGNORE；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) 金额 DECIMAL(18,2)，数量 DECIMAL(18,4)，比率 DECIMAL(9,4)；
--            4) 表间逻辑外键，不建物理 FOREIGN KEY；
--            5) OEE 记录按「设备 + 日期」唯一，保证 MES 回传幂等。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 检验单（IQC / IPQC / FQC；DRAFT → INSPECTING → PASSED / FAILED / CANCELLED）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_qc_inspection_order (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    order_no          VARCHAR(64)   NOT NULL            COMMENT '检验单号（租户内唯一）',
    inspection_type   VARCHAR(16)   NOT NULL            COMMENT '检验类型：IQC/IPQC/FQC',
    source_type       VARCHAR(32)   NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：PURCHASE_RECEIPT/PRODUCTION_ORDER/MANUAL',
    source_no         VARCHAR(64)   NOT NULL            COMMENT '来源单据号',
    item_id           BIGINT        DEFAULT NULL        COMMENT '物料ID',
    item_code         VARCHAR(64)   NOT NULL            COMMENT '物料编码快照',
    item_name         VARCHAR(128)  NOT NULL            COMMENT '物料名称快照',
    lot_no            VARCHAR(64)   DEFAULT NULL        COMMENT '批次号',
    quantity          DECIMAL(18,4) NOT NULL            COMMENT '送检数量',
    inspected_quantity DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '已检验数量',
    supplier_code     VARCHAR(64)   DEFAULT NULL        COMMENT '供应商编码（IQC）',
    status            VARCHAR(24)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/INSPECTING/PASSED/FAILED/CANCELLED',
    inspector         VARCHAR(64)   DEFAULT NULL        COMMENT '检验员',
    inspect_date      DATE          NOT NULL            COMMENT '检验日期',
    tool_id           BIGINT        DEFAULT NULL        COMMENT '量具ID',
    tool_no           VARCHAR(64)   DEFAULT NULL        COMMENT '量具编号快照',
    ncr_id            BIGINT        DEFAULT NULL        COMMENT '不合格品单ID（判定 FAILED 时回写）',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_qc_order_no (tenant_id, order_no),
    KEY idx_qc_order_type_date (tenant_id, inspection_type, inspect_date),
    KEY idx_qc_order_source (tenant_id, source_type, source_no),
    KEY idx_qc_order_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='质量检验单';

-- ---------------------------------------------------------------------
-- 2. 检验项（每项必须录入结论才允许判定整单）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_qc_inspection_item (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    order_id          BIGINT        NOT NULL            COMMENT '检验单ID',
    line_no           INT           NOT NULL            COMMENT '行号（检验单内唯一）',
    item_name         VARCHAR(128)  NOT NULL            COMMENT '检验项名称',
    standard_value    VARCHAR(128)  DEFAULT NULL        COMMENT '标准值/规格',
    actual_value      VARCHAR(128)  DEFAULT NULL        COMMENT '实测值',
    result            VARCHAR(16)   DEFAULT NULL        COMMENT '结论：PASS/FAIL（空表示未录入）',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_qc_item_line (tenant_id, order_id, line_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='质量检验项';

-- ---------------------------------------------------------------------
-- 3. 不合格品单（OPEN → DISPOSED → CLOSED；FAILED 检验必须生成）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_qc_ncr (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    ncr_no            VARCHAR(64)   NOT NULL            COMMENT '不合格品单号（租户内唯一）',
    source_type       VARCHAR(32)   NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型：INSPECTION/MANUAL',
    source_no         VARCHAR(64)   NOT NULL            COMMENT '来源单据号',
    item_id           BIGINT        DEFAULT NULL        COMMENT '物料ID',
    item_name         VARCHAR(128)  NOT NULL            COMMENT '物料名称快照',
    lot_no            VARCHAR(64)   DEFAULT NULL        COMMENT '批次号',
    quantity          DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '不合格数量',
    defect_desc       VARCHAR(255)  NOT NULL            COMMENT '缺陷描述',
    disposition       VARCHAR(16)   DEFAULT NULL        COMMENT '处置方式：REWORK/CONCESSION/SCRAP/RETURN',
    status            VARCHAR(16)   NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN/DISPOSED/CLOSED',
    cost_amount       DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '质量成本金额',
    dispose_date      DATE          DEFAULT NULL        COMMENT '处置日期',
    close_date        DATE          DEFAULT NULL        COMMENT '关闭日期',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_qc_ncr_no (tenant_id, ncr_no),
    KEY idx_qc_ncr_source (tenant_id, source_type, source_no),
    KEY idx_qc_ncr_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='不合格品处理单';

-- ---------------------------------------------------------------------
-- 4. 质量成本（来源 NCR 自动登记或人工登记）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_qc_quality_cost (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    cost_no           VARCHAR(64)   NOT NULL            COMMENT '质量成本单号（租户内唯一）',
    source_type       VARCHAR(32)   NOT NULL DEFAULT 'NCR' COMMENT '来源类型：NCR/MANUAL',
    source_no         VARCHAR(64)   NOT NULL            COMMENT '来源单据号',
    cost_type         VARCHAR(16)   NOT NULL            COMMENT '成本类型：REWORK/SCRAP/INSPECTION',
    amount            DECIMAL(18,2) NOT NULL            COMMENT '质量成本金额',
    cost_date         DATE          NOT NULL            COMMENT '成本日期',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_qc_cost_no (tenant_id, cost_no),
    KEY idx_qc_cost_source (tenant_id, source_type, source_no),
    KEY idx_qc_cost_date (tenant_id, cost_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='质量成本';

-- ---------------------------------------------------------------------
-- 5. 设备档案（设备编码租户内唯一）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_qc_equipment (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    equipment_no      VARCHAR(64)   NOT NULL            COMMENT '设备编码（租户内唯一）',
    name              VARCHAR(128)  NOT NULL            COMMENT '设备名称',
    equipment_type    VARCHAR(64)   DEFAULT NULL        COMMENT '设备类型',
    location          VARCHAR(128)  DEFAULT NULL        COMMENT '安装位置',
    status            VARCHAR(16)   NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING/IDLE/DOWN/RETIRED',
    purchase_date     DATE          DEFAULT NULL        COMMENT '购置日期',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_qc_equipment_no (tenant_id, equipment_no),
    KEY idx_qc_equipment_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='设备档案';

-- ---------------------------------------------------------------------
-- 6. 保养计划（下一保养日 = 上次保养日 + 周期天数）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_qc_maintenance_plan (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    plan_no           VARCHAR(64)   NOT NULL            COMMENT '保养计划号（租户内唯一）',
    equipment_no      VARCHAR(64)   NOT NULL            COMMENT '设备编码',
    cycle_days        INT           NOT NULL            COMMENT '保养周期（天）',
    last_date         DATE          DEFAULT NULL        COMMENT '上次保养日期',
    next_date         DATE          NOT NULL            COMMENT '下次保养日期',
    status            VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_qc_maint_plan_no (tenant_id, plan_no),
    KEY idx_qc_maint_plan_eq (tenant_id, equipment_no, next_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='设备保养计划';

-- ---------------------------------------------------------------------
-- 7. 保养工单（OPEN → IN_PROGRESS → COMPLETED / CANCELLED）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_qc_maintenance_order (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    order_no          VARCHAR(64)   NOT NULL            COMMENT '保养工单号（租户内唯一）',
    equipment_no      VARCHAR(64)   NOT NULL            COMMENT '设备编码',
    plan_id           BIGINT        DEFAULT NULL        COMMENT '保养计划ID',
    maintenance_type  VARCHAR(16)   NOT NULL DEFAULT 'PREVENTIVE' COMMENT '类型：PREVENTIVE/CORRECTIVE',
    status            VARCHAR(16)   NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN/IN_PROGRESS/COMPLETED/CANCELLED',
    plan_date         DATE          NOT NULL            COMMENT '计划保养日期',
    complete_date     DATE          DEFAULT NULL        COMMENT '实际完成日期',
    cost_amount       DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '保养费用',
    result            VARCHAR(255)  DEFAULT NULL        COMMENT '保养结果',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_qc_maint_order_no (tenant_id, order_no),
    KEY idx_qc_maint_order_eq (tenant_id, equipment_no, plan_date),
    KEY idx_qc_maint_order_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='设备保养工单';

-- ---------------------------------------------------------------------
-- 8. OEE 记录（设备 + 日期唯一，MES 重复回传覆盖而非追加）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_qc_oee_record (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    equipment_no      VARCHAR(64)   NOT NULL            COMMENT '设备编码',
    record_date       DATE          NOT NULL            COMMENT '统计日期',
    planned_minutes   DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '计划运行分钟',
    run_minutes       DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '实际运行分钟',
    down_minutes      DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '停机分钟',
    ideal_qty         DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '理论产量',
    output_qty        DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '实际产量',
    good_qty          DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '合格产量',
    availability_rate DECIMAL(9,4)  NOT NULL DEFAULT 0  COMMENT '时间开动率',
    performance_rate  DECIMAL(9,4)  NOT NULL DEFAULT 0  COMMENT '性能开动率',
    quality_rate      DECIMAL(9,4)  NOT NULL DEFAULT 0  COMMENT '合格率',
    oee_rate          DECIMAL(9,4)  NOT NULL DEFAULT 0  COMMENT 'OEE',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oee_equipment_date (tenant_id, equipment_no, record_date),
    KEY idx_oee_date (tenant_id, record_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='设备 OEE 记录';

-- ---------------------------------------------------------------------
-- 9. 量具台账（校准过期不可用于检验）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_qc_measuring_tool (
    id                     BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id              BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    tool_no                VARCHAR(64)   NOT NULL            COMMENT '量具编号（租户内唯一）',
    name                   VARCHAR(128)  NOT NULL            COMMENT '量具名称',
    tool_type              VARCHAR(64)   DEFAULT NULL        COMMENT '量具类型',
    precision_value        VARCHAR(64)   DEFAULT NULL        COMMENT '精度值描述',
    calibration_cycle_months INT         NOT NULL DEFAULT 12 COMMENT '校准周期（月）',
    last_calibration_date  DATE          DEFAULT NULL        COMMENT '上次校准日期',
    next_calibration_date  DATE          NOT NULL            COMMENT '下次校准日期',
    status                 VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
    remark                 VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted                TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_qc_tool_no (tenant_id, tool_no),
    KEY idx_qc_tool_next_cal (tenant_id, next_calibration_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='计量器具台账';

-- ---------------------------------------------------------------------
-- 10. 校准计划（按量具维护周期与下次校准日）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_qc_calibration_plan (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    plan_no           VARCHAR(64)   NOT NULL            COMMENT '校准计划号（租户内唯一）',
    tool_no           VARCHAR(64)   NOT NULL            COMMENT '量具编号',
    cycle_months      INT           NOT NULL            COMMENT '校准周期（月）',
    next_date         DATE          NOT NULL            COMMENT '下次校准日期',
    status            VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_qc_cal_plan_no (tenant_id, plan_no),
    KEY idx_qc_cal_plan_tool (tenant_id, tool_no, next_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='量具校准计划';

-- ---------------------------------------------------------------------
-- 11. 校准记录（PASS 回写量具校准日期，FAIL 保持过期状态）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_qc_calibration_record (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    record_no         VARCHAR(64)   NOT NULL            COMMENT '校准记录号（租户内唯一）',
    tool_no           VARCHAR(64)   NOT NULL            COMMENT '量具编号',
    plan_id           BIGINT        DEFAULT NULL        COMMENT '校准计划ID',
    calibration_date  DATE          NOT NULL            COMMENT '校准日期',
    result            VARCHAR(16)   NOT NULL            COMMENT '校准结论：PASS/FAIL',
    certificate_no    VARCHAR(64)   DEFAULT NULL        COMMENT '校准证书号',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_qc_cal_record_no (tenant_id, record_no),
    KEY idx_qc_cal_record_tool (tenant_id, tool_no, calibration_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='量具校准记录';
