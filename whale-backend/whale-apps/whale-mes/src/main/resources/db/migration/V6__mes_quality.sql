-- =====================================================================
-- Wave 5.79 M3：MES 车间质量（V6）
-- =====================================================================
-- 适用库   : bluewhale_mes
-- 执行方式 : Flyway 自动执行；或 mysql -u<user> -p bluewhale_mes < V6__mes_quality.sql
-- 迁移说明 : 在 M2（V5 物料追溯 8 表）基础上新增 quality 上下文 mes_qc_* 共 10 张表：
--            检验计划、计划检验项、检验任务、检验结果、检验不良、NCR、处置、返工单、SPC 样本、SPC 规则。
-- 数据迁移 : 只新增表与索引，不修改/删除存量数据；空库可重复执行。
-- 命名约定 : 上下文二级前缀 mes_qc_；逻辑外键，不建物理外键；质量成本与 IQC/FQC 归 ERP。
-- 状态口径 : 检验任务 PENDING/INSPECTED/PASSED/FAILED/CANCELLED；
--            NCR OPEN/DISPOSITION/REWORK/REPAIR/SCRAP/CONCESSION/CLOSED；
--            处置 CONFIRMED/EXECUTED/CANCELLED；返工 CREATED/IN_PROGRESS/COMPLETED/CANCELLED；
--            SPC 规则 ENABLED/DISABLED。
-- 不变量   : 检验项未全部录入不可判定；检验 FAILED 必生成 NCR；
--            处置数量 = NCR 不良数量；NCR 关闭必须给出结论；
--            SPC 样本必须关联有效检验项，规则命中留痕（alert_flag + alert_rule）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 检验计划（首件 / IPQC 巡检）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_qc_inspection_plan (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    plan_no VARCHAR(64) NOT NULL COMMENT '计划编号（租户内唯一）',
    plan_name VARCHAR(128) NOT NULL COMMENT '计划名称',
    inspection_type VARCHAR(16) NOT NULL COMMENT '检验类型：FIRST_ARTICLE 首件 / IPQC 巡检',
    item_code VARCHAR(64) NULL COMMENT '适用物料编码（空表示不限）',
    operation_code VARCHAR(64) NULL COMMENT '适用工序编码（空表示不限）',
    line_id BIGINT NULL COMMENT '适用产线 ID',
    line_code VARCHAR(64) NULL COMMENT '适用产线编码快照',
    sample_size INT NOT NULL DEFAULT 1 COMMENT '抽样数量',
    frequency_minutes INT NOT NULL DEFAULT 0 COMMENT '巡检频次（分钟，首件为 0）',
    auto_ncr TINYINT NOT NULL DEFAULT 1 COMMENT '检验 FAILED 是否自动生成 NCR：0 否，1 是',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    remark VARCHAR(255) NULL COMMENT '备注',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_qc_plan_no (tenant_id, plan_no),
    KEY idx_mes_qc_plan_type (tenant_id, inspection_type, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 检验计划';

-- ---------------------------------------------------------------------
-- 2. 检验计划项（判定规则：目视/测量/功能，上下限）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_qc_inspection_plan_item (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    plan_id BIGINT NOT NULL COMMENT '检验计划 ID',
    item_no INT NOT NULL COMMENT '检验项序号',
    item_name VARCHAR(128) NOT NULL COMMENT '检验项名称',
    inspection_method VARCHAR(32) NOT NULL DEFAULT 'MEASURE'
        COMMENT '检验方式：VISUAL 目视 / MEASURE 测量 / FUNCTION 功能 / TEST 试验',
    standard_text VARCHAR(255) NULL COMMENT '标准描述',
    min_value DECIMAL(18, 4) NULL COMMENT '下限（测量型）',
    max_value DECIMAL(18, 4) NULL COMMENT '上限（测量型）',
    unit VARCHAR(16) NULL COMMENT '计量单位',
    critical_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否关键项：0 否，1 是',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_qc_plan_item (tenant_id, plan_id, item_no),
    KEY idx_mes_qc_plan_item_plan (tenant_id, plan_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 检验计划项';

-- ---------------------------------------------------------------------
-- 3. 检验任务（首件 / IPQC，绑定工单、工序任务与批次）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_qc_inspection_task (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    task_no VARCHAR(64) NOT NULL COMMENT '检验任务号（租户内唯一）',
    task_type VARCHAR(16) NOT NULL COMMENT '任务类型：FIRST_ARTICLE 首件 / IPQC 巡检',
    plan_id BIGINT NULL COMMENT '检验计划 ID',
    plan_no VARCHAR(64) NULL COMMENT '检验计划编号快照',
    work_order_id BIGINT NULL COMMENT '工单 ID',
    work_order_code VARCHAR(64) NULL COMMENT '工单编码快照',
    exec_task_id BIGINT NULL COMMENT '工单工序任务 ID',
    exec_task_no VARCHAR(64) NULL COMMENT '工单工序任务号快照',
    operation_code VARCHAR(64) NULL COMMENT '工序编码',
    line_id BIGINT NULL COMMENT '产线 ID',
    line_code VARCHAR(64) NULL COMMENT '产线编码快照',
    station_code VARCHAR(64) NULL COMMENT '工位编码',
    batch_no VARCHAR(64) NULL COMMENT '检验批次号',
    sample_size INT NOT NULL DEFAULT 1 COMMENT '应检样本数',
    inspected_quantity INT NOT NULL DEFAULT 0 COMMENT '已录入检验项数',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT '状态：PENDING 待检 / INSPECTED 已录入 / PASSED 合格 / FAILED 不合格 / CANCELLED 取消',
    inspector VARCHAR(64) NULL COMMENT '检验员',
    judge VARCHAR(64) NULL COMMENT '判定人',
    judged_at DATETIME NULL COMMENT '判定时间',
    conclusion VARCHAR(255) NULL COMMENT '判定结论',
    ncr_id BIGINT NULL COMMENT '自动生成的 NCR ID',
    remark VARCHAR(255) NULL COMMENT '备注',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_qc_task_no (tenant_id, task_no),
    KEY idx_mes_qc_task_wo (tenant_id, work_order_id, operation_code, task_type),
    KEY idx_mes_qc_task_status (tenant_id, status, task_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 检验任务';

-- ---------------------------------------------------------------------
-- 4. 检验结果（按检验项 + 样本录入）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_qc_inspection_result (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    task_id BIGINT NOT NULL COMMENT '检验任务 ID',
    plan_item_id BIGINT NULL COMMENT '计划检验项 ID',
    item_no INT NOT NULL COMMENT '检验项序号快照',
    item_name VARCHAR(128) NULL COMMENT '检验项名称快照',
    sample_no INT NOT NULL DEFAULT 1 COMMENT '样本序号',
    measured_value VARCHAR(128) NULL COMMENT '实测值（文本，如 OK/NG 描述）',
    numeric_value DECIMAL(18, 4) NULL COMMENT '实测数值',
    result VARCHAR(8) NOT NULL DEFAULT 'OK' COMMENT '判定：OK/NG',
    inspector VARCHAR(64) NULL COMMENT '检验员',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    KEY idx_mes_qc_result_task (tenant_id, task_id, item_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 检验结果';

-- ---------------------------------------------------------------------
-- 5. 检验不良（检验任务内不良数量与缺陷代码）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_qc_inspection_defect (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    task_id BIGINT NOT NULL COMMENT '检验任务 ID',
    defect_code VARCHAR(64) NOT NULL COMMENT '缺陷代码',
    defect_name VARCHAR(128) NULL COMMENT '缺陷名称快照',
    quantity DECIMAL(18, 4) NOT NULL COMMENT '不良数量',
    severity VARCHAR(16) NOT NULL DEFAULT 'MAJOR' COMMENT '严重度：MINOR/MAJOR/CRITICAL',
    description VARCHAR(255) NULL COMMENT '不良描述',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    KEY idx_mes_qc_defect_task (tenant_id, task_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 检验不良';

-- ---------------------------------------------------------------------
-- 6. 不合格品记录 NCR（FAILED 必生成；处置与关闭的状态机载体）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_qc_ncr (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    ncr_no VARCHAR(64) NOT NULL COMMENT 'NCR 编号（租户内唯一）',
    source_type VARCHAR(16) NOT NULL DEFAULT 'INSPECTION'
        COMMENT '来源：INSPECTION 检验 / REPORT 报工 / MANUAL 手工',
    inspection_task_id BIGINT NULL COMMENT '来源检验任务 ID',
    inspection_task_no VARCHAR(64) NULL COMMENT '来源检验任务号快照',
    report_id BIGINT NULL COMMENT '来源报工记录 ID',
    work_order_id BIGINT NULL COMMENT '工单 ID',
    work_order_code VARCHAR(64) NULL COMMENT '工单编码快照',
    exec_task_id BIGINT NULL COMMENT '工序任务 ID',
    exec_task_no VARCHAR(64) NULL COMMENT '工序任务号快照',
    item_code VARCHAR(64) NULL COMMENT '物料编码',
    item_name VARCHAR(128) NULL COMMENT '物料名称快照',
    batch_no VARCHAR(64) NULL COMMENT '批次号',
    operation_code VARCHAR(64) NULL COMMENT '工序编码',
    defect_code VARCHAR(64) NULL COMMENT '缺陷代码',
    defect_name VARCHAR(128) NULL COMMENT '缺陷名称快照',
    defect_quantity DECIMAL(18, 4) NOT NULL COMMENT '不良数量（处置数量必须等于该值）',
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN'
        COMMENT '状态：OPEN/DISPOSITION/REWORK/REPAIR/SCRAP/CONCESSION/CLOSED',
    conclusion VARCHAR(255) NULL COMMENT '关闭结论',
    closed_by VARCHAR(64) NULL COMMENT '关闭人',
    closed_at DATETIME NULL COMMENT '关闭时间',
    remark VARCHAR(255) NULL COMMENT '备注',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_qc_ncr_no (tenant_id, ncr_no),
    KEY idx_mes_qc_ncr_status (tenant_id, status),
    KEY idx_mes_qc_ncr_wo (tenant_id, work_order_id, batch_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 不合格品记录 NCR';

-- ---------------------------------------------------------------------
-- 7. 处置（返工/返修/让步/报废；一个 NCR 一条处置，数量必须一致）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_qc_disposition (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    disposition_no VARCHAR(64) NOT NULL COMMENT '处置单号（租户内唯一）',
    ncr_id BIGINT NOT NULL COMMENT 'NCR ID',
    ncr_no VARCHAR(64) NOT NULL COMMENT 'NCR 编号快照',
    disposition_type VARCHAR(16) NOT NULL
        COMMENT '处置类型：REWORK 返工 / REPAIR 返修 / SCRAP 报废 / CONCESSION 让步接收',
    quantity DECIMAL(18, 4) NOT NULL COMMENT '处置数量（必须等于 NCR 不良数量）',
    reason VARCHAR(255) NULL COMMENT '处置原因',
    approved_by VARCHAR(64) NULL COMMENT '审批人（审批流归 OA/Workflow）',
    status VARCHAR(16) NOT NULL DEFAULT 'CONFIRMED' COMMENT '状态：CONFIRMED/EXECUTED/CANCELLED',
    executed_at DATETIME NULL COMMENT '执行时间',
    remark VARCHAR(255) NULL COMMENT '备注',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_qc_disposition_no (tenant_id, disposition_no),
    UNIQUE KEY uk_mes_qc_disposition_ncr (tenant_id, ncr_id),
    KEY idx_mes_qc_disposition_status (tenant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 不合格品处置';

-- ---------------------------------------------------------------------
-- 8. 返工单（回链执行任务，返工完成后方可关闭 NCR）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_qc_rework_order (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    rework_no VARCHAR(64) NOT NULL COMMENT '返工单号（租户内唯一）',
    ncr_id BIGINT NOT NULL COMMENT 'NCR ID',
    ncr_no VARCHAR(64) NOT NULL COMMENT 'NCR 编号快照',
    disposition_id BIGINT NOT NULL COMMENT '处置 ID',
    work_order_id BIGINT NULL COMMENT '工单 ID',
    work_order_code VARCHAR(64) NULL COMMENT '工单编码快照',
    exec_task_id BIGINT NULL COMMENT '工序任务 ID',
    exec_task_no VARCHAR(64) NULL COMMENT '工序任务号快照',
    item_code VARCHAR(64) NULL COMMENT '物料编码',
    item_name VARCHAR(128) NULL COMMENT '物料名称快照',
    batch_no VARCHAR(64) NULL COMMENT '批次号',
    quantity DECIMAL(18, 4) NOT NULL COMMENT '返工数量',
    status VARCHAR(16) NOT NULL DEFAULT 'CREATED' COMMENT '状态：CREATED/IN_PROGRESS/COMPLETED/CANCELLED',
    reason VARCHAR(255) NULL COMMENT '返工原因',
    operator VARCHAR(64) NULL COMMENT '返工人',
    started_at DATETIME NULL COMMENT '开工时间',
    completed_at DATETIME NULL COMMENT '完工时间',
    remark VARCHAR(255) NULL COMMENT '备注',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_qc_rework_no (tenant_id, rework_no),
    KEY idx_mes_qc_rework_ncr (tenant_id, ncr_id),
    KEY idx_mes_qc_rework_status (tenant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 返工单';

-- ---------------------------------------------------------------------
-- 9. SPC 规则（Xbar-R 默认，控制限可配置）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_qc_spc_rule (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    rule_code VARCHAR(64) NOT NULL COMMENT '规则编码（租户内唯一）',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    plan_item_id BIGINT NOT NULL COMMENT '关联检验计划项 ID（样本必须来自有效检验项）',
    operation_code VARCHAR(64) NULL COMMENT '工序编码快照',
    chart_type VARCHAR(16) NOT NULL DEFAULT 'XBAR_R' COMMENT '控制图类型：XBAR_R 均值-极差',
    subgroup_size INT NOT NULL DEFAULT 5 COMMENT '子组大小（2–10）',
    center_line DECIMAL(18, 4) NULL COMMENT '中心线 CL',
    ucl DECIMAL(18, 4) NULL COMMENT '均值上控制限 UCL',
    lcl DECIMAL(18, 4) NULL COMMENT '均值下控制限 LCL',
    r_ucl DECIMAL(18, 4) NULL COMMENT '极差上控制限',
    r_lcl DECIMAL(18, 4) NULL COMMENT '极差下控制限',
    alert_action VARCHAR(16) NOT NULL DEFAULT 'WARN' COMMENT '命中处理：WARN 告警 / BLOCK 阻断',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    remark VARCHAR(255) NULL COMMENT '备注',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_qc_spc_rule_code (tenant_id, rule_code),
    KEY idx_mes_qc_spc_rule_item (tenant_id, plan_item_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES SPC 控制规则';

-- ---------------------------------------------------------------------
-- 10. SPC 样本（子组内逐点录入，子组满额后计算 xbar/极差并留痕告警）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_qc_spc_sample (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    sample_no VARCHAR(64) NOT NULL COMMENT '样本号（租户内唯一）',
    rule_id BIGINT NOT NULL COMMENT 'SPC 规则 ID',
    rule_code VARCHAR(64) NULL COMMENT '规则编码快照',
    plan_item_id BIGINT NOT NULL COMMENT '检验计划项 ID',
    inspection_task_id BIGINT NULL COMMENT '来源检验任务 ID',
    operation_code VARCHAR(64) NULL COMMENT '工序编码',
    subgroup_no INT NOT NULL COMMENT '子组号',
    sample_seq INT NOT NULL DEFAULT 1 COMMENT '子组内样本序号',
    subgroup_size INT NOT NULL DEFAULT 5 COMMENT '子组大小快照',
    measured_value DECIMAL(18, 4) NOT NULL COMMENT '实测值',
    subgroup_xbar DECIMAL(18, 4) NULL COMMENT '子组均值（满额后回填）',
    subgroup_range DECIMAL(18, 4) NULL COMMENT '子组极差（满额后回填）',
    alert_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否命中规则：0 否，1 是',
    alert_rule VARCHAR(255) NULL COMMENT '命中规则说明（留痕）',
    sampled_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '采样时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_qc_spc_sample_no (tenant_id, sample_no),
    KEY idx_mes_qc_spc_sample_subgroup (tenant_id, rule_id, subgroup_no, sample_seq),
    KEY idx_mes_qc_spc_sample_item (tenant_id, plan_item_id, sampled_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES SPC 样本';

-- =====================================================================
-- 完成。M3 落地后空库累计 46 张业务表（V1 2 + V2 12 + V3 1 + V4 13 + V5 8 + V6 10）。
-- 校验建议：
--   SELECT COUNT(*) FROM information_schema.tables
--    WHERE table_schema = 'bluewhale_mes' AND table_name LIKE 'mes\_%';
-- =====================================================================
