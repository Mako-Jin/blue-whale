-- =====================================================================
-- Wave 5.79 M1：MES 调度派工与生产执行（V4）
-- =====================================================================
-- 适用库   : bluewhale_mes
-- 执行方式 : Flyway 自动执行；或 mysql -u<user> -p bluewhale_mes < V4__mes_dispatch_execution.sql
-- 迁移说明 : 在 M0（V1 基线 + V2 主数据/工单 + V3 班历）基础上新增：
--            dispatch 上下文 mes_dp_*（任务、派工、排程）与 execution 上下文 mes_ex_*（报工、不良、
--            领料/退料、在制品快照、异常）。
-- 数据迁移 : 只新增表与索引，不修改/删除存量数据；空库可重复执行。
-- 命名约定 : 上下文二级前缀 mes_dp_ / mes_ex_；逻辑外键，不建物理外键。
-- 状态口径 : 任务 PLANNED/DISPATCHED/IN_PROGRESS/PAUSED/COMPLETED/SCRAPPED；
--            报工 SUBMITTED；异常 OPEN/ACKNOWLEDGED/RESOLVED/CLOSED。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 工单拆分任务（dispatch）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_dp_work_order_task (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    task_no VARCHAR(64) NOT NULL COMMENT '任务编号（租户内唯一）',
    work_order_id BIGINT NOT NULL COMMENT '工单 ID',
    work_order_code VARCHAR(64) NOT NULL COMMENT '工单编码快照',
    erp_order_no VARCHAR(64) NULL COMMENT 'ERP 生产订单号快照',
    seq_no INT NOT NULL COMMENT '工序顺序',
    operation_code VARCHAR(64) NOT NULL COMMENT '工序编码',
    operation_name VARCHAR(128) NOT NULL COMMENT '工序名称',
    work_center_id BIGINT NULL COMMENT '工作中心 ID',
    work_center_code VARCHAR(64) NULL COMMENT '工作中心编码',
    planned_quantity DECIMAL(18, 4) NOT NULL COMMENT '计划数量',
    dispatched_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '已派工数量',
    reported_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '累计报工数量',
    qualified_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '累计合格数量',
    defect_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '累计不良数量',
    completed_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '累计完工数量',
    scrapped_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '累计报废数量',
    status VARCHAR(16) NOT NULL DEFAULT 'PLANNED'
        COMMENT '状态：PLANNED/DISPATCHED/IN_PROGRESS/PAUSED/COMPLETED/SCRAPPED',
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT '优先级：NORMAL/URGENT',
    planned_start DATETIME NULL COMMENT '计划开工时间',
    planned_end DATETIME NULL COMMENT '计划完工时间',
    remark VARCHAR(255) NULL COMMENT '备注',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_dp_task_no (tenant_id, task_no),
    UNIQUE KEY uk_mes_dp_task_order_seq (tenant_id, work_order_id, seq_no),
    KEY idx_mes_dp_task_status (tenant_id, status),
    KEY idx_mes_dp_task_order (tenant_id, work_order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 工单拆分任务';

-- ---------------------------------------------------------------------
-- 2. 派工单与派工明细（dispatch）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_dp_dispatch_order (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    dispatch_no VARCHAR(64) NOT NULL COMMENT '派工单号（租户内唯一）',
    work_order_id BIGINT NOT NULL COMMENT '工单 ID',
    work_order_code VARCHAR(64) NOT NULL COMMENT '工单编码快照',
    line_id BIGINT NOT NULL COMMENT '派工产线 ID',
    line_code VARCHAR(64) NOT NULL COMMENT '派工产线编码',
    team_id BIGINT NULL COMMENT '派工班组 ID',
    team_code VARCHAR(64) NULL COMMENT '派工班组编码',
    task_count INT NOT NULL DEFAULT 0 COMMENT '任务数',
    dispatch_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '派工总数量',
    status VARCHAR(16) NOT NULL DEFAULT 'DISPATCHED' COMMENT '状态：DISPATCHED/WITHDRAWN',
    dispatched_by VARCHAR(64) NULL COMMENT '派工人',
    dispatched_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '派工时间',
    withdrawn_at DATETIME NULL COMMENT '撤回时间',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_dp_dispatch_no (tenant_id, dispatch_no),
    KEY idx_mes_dp_dispatch_order (tenant_id, work_order_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 派工单';

CREATE TABLE IF NOT EXISTS mes_dp_dispatch_line (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    dispatch_order_id BIGINT NOT NULL COMMENT '派工单 ID',
    task_id BIGINT NOT NULL COMMENT '任务 ID',
    task_no VARCHAR(64) NOT NULL COMMENT '任务编号快照',
    operation_code VARCHAR(64) NOT NULL COMMENT '工序编码快照',
    dispatch_quantity DECIMAL(18, 4) NOT NULL COMMENT '派工数量',
    status VARCHAR(16) NOT NULL DEFAULT 'DISPATCHED' COMMENT '状态：DISPATCHED/WITHDRAWN',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_dp_dispatch_line (tenant_id, dispatch_order_id, task_id),
    KEY idx_mes_dp_dispatch_line_task (tenant_id, task_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 派工明细';

-- ---------------------------------------------------------------------
-- 3. 有限产能排程（dispatch，MVP 软约束）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_dp_schedule_plan (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    plan_no VARCHAR(64) NOT NULL COMMENT '排程计划号（租户内唯一）',
    plan_date DATE NOT NULL COMMENT '排程日期',
    work_order_id BIGINT NOT NULL COMMENT '工单 ID',
    work_order_code VARCHAR(64) NOT NULL COMMENT '工单编码快照',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/LOCKED/CANCELLED',
    planned_by VARCHAR(64) NULL COMMENT '排程人',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_dp_schedule_no (tenant_id, plan_no),
    KEY idx_mes_dp_schedule_order (tenant_id, work_order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 排程计划';

CREATE TABLE IF NOT EXISTS mes_dp_schedule_slot (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    plan_id BIGINT NOT NULL COMMENT '排程计划 ID',
    task_id BIGINT NULL COMMENT '任务 ID',
    work_center_id BIGINT NOT NULL COMMENT '工作中心 ID',
    shift_id BIGINT NOT NULL COMMENT '班次 ID',
    schedule_date DATE NOT NULL COMMENT '排程日期',
    capacity_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '班次产能',
    allocated_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '已分配数量',
    status VARCHAR(16) NOT NULL DEFAULT 'RESERVED' COMMENT '状态：RESERVED/RELEASED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    KEY idx_mes_dp_slot_center (tenant_id, work_center_id, schedule_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 排程时段';

-- ---------------------------------------------------------------------
-- 4. 报工与不良明细（execution）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_ex_production_report (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    report_no VARCHAR(64) NOT NULL COMMENT '报工单号（租户内唯一）',
    task_id BIGINT NOT NULL COMMENT '任务 ID',
    task_no VARCHAR(64) NOT NULL COMMENT '任务编号快照',
    work_order_id BIGINT NOT NULL COMMENT '工单 ID',
    work_order_code VARCHAR(64) NOT NULL COMMENT '工单编码快照',
    operation_code VARCHAR(64) NOT NULL COMMENT '工序编码快照',
    station_id BIGINT NULL COMMENT '工位 ID',
    station_code VARCHAR(64) NULL COMMENT '工位编码快照',
    report_quantity DECIMAL(18, 4) NOT NULL COMMENT '报工数量',
    qualified_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '合格数量',
    defect_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '不良数量',
    status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED' COMMENT '状态：SUBMITTED',
    reporter VARCHAR(64) NULL COMMENT '报工人',
    reported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报工时间',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_ex_report_no (tenant_id, report_no),
    KEY idx_mes_ex_report_task (tenant_id, task_id, reported_at),
    KEY idx_mes_ex_report_order (tenant_id, work_order_id, reported_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 生产报工';

CREATE TABLE IF NOT EXISTS mes_ex_report_defect (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    report_id BIGINT NOT NULL COMMENT '报工记录 ID',
    defect_code VARCHAR(64) NOT NULL COMMENT '缺陷编码',
    defect_name VARCHAR(128) NULL COMMENT '缺陷名称快照',
    quantity DECIMAL(18, 4) NOT NULL COMMENT '不良数量',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    KEY idx_mes_ex_report_defect_report (tenant_id, report_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 报工不良明细';

-- ---------------------------------------------------------------------
-- 5. 领料 / 退料（execution，线边台账 M2 增强）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_ex_material_issue (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    issue_no VARCHAR(64) NOT NULL COMMENT '领料单号（租户内唯一）',
    work_order_id BIGINT NOT NULL COMMENT '工单 ID',
    task_id BIGINT NULL COMMENT '任务 ID',
    warehouse_code VARCHAR(64) NOT NULL COMMENT '领料仓库编码',
    issue_type VARCHAR(16) NOT NULL DEFAULT 'ISSUE' COMMENT '类型：ISSUE 领料 / SUPPLEMENT 补料',
    status VARCHAR(16) NOT NULL DEFAULT 'ISSUED' COMMENT '状态：ISSUED',
    issued_by VARCHAR(64) NULL COMMENT '领料人',
    issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领料时间',
    reason VARCHAR(255) NULL COMMENT '补料原因',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_ex_issue_no (tenant_id, issue_no),
    KEY idx_mes_ex_issue_order (tenant_id, work_order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 领料/补料单';

CREATE TABLE IF NOT EXISTS mes_ex_material_issue_line (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    issue_id BIGINT NOT NULL COMMENT '领料单 ID',
    item_code VARCHAR(64) NOT NULL COMMENT '物料编码',
    item_name VARCHAR(128) NULL COMMENT '物料名称',
    quantity DECIMAL(18, 4) NOT NULL COMMENT '领用数量',
    uom VARCHAR(16) NULL COMMENT '计量单位',
    batch_no VARCHAR(64) NULL COMMENT '批次号（M2 条码增强）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    KEY idx_mes_ex_issue_line_issue (tenant_id, issue_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 领料明细';

CREATE TABLE IF NOT EXISTS mes_ex_material_return (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    return_no VARCHAR(64) NOT NULL COMMENT '退料单号（租户内唯一）',
    work_order_id BIGINT NOT NULL COMMENT '工单 ID',
    task_id BIGINT NULL COMMENT '任务 ID',
    issue_id BIGINT NULL COMMENT '原领料单 ID',
    warehouse_code VARCHAR(64) NOT NULL COMMENT '退料仓库编码',
    item_code VARCHAR(64) NOT NULL COMMENT '物料编码',
    item_name VARCHAR(128) NULL COMMENT '物料名称',
    quantity DECIMAL(18, 4) NOT NULL COMMENT '退料数量',
    uom VARCHAR(16) NULL COMMENT '计量单位',
    batch_no VARCHAR(64) NULL COMMENT '批次号',
    reason VARCHAR(255) NULL COMMENT '退料原因',
    status VARCHAR(16) NOT NULL DEFAULT 'RETURNED' COMMENT '状态：RETURNED',
    returned_by VARCHAR(64) NULL COMMENT '退料人',
    returned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '退料时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_ex_return_no (tenant_id, return_no),
    KEY idx_mes_ex_return_order (tenant_id, work_order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 退料单';

-- ---------------------------------------------------------------------
-- 6. 在制品快照（execution）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_ex_wip_snapshot (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    work_order_id BIGINT NOT NULL COMMENT '工单 ID',
    task_id BIGINT NOT NULL COMMENT '任务 ID',
    task_no VARCHAR(64) NOT NULL COMMENT '任务编号快照',
    operation_code VARCHAR(64) NOT NULL COMMENT '工序编码快照',
    dispatched_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '已派工数量',
    completed_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '已完工数量',
    scrapped_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '已报废数量',
    wip_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '在制品数量',
    snapshot_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_ex_wip_task (tenant_id, task_id),
    KEY idx_mes_ex_wip_order (tenant_id, work_order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 在制品快照';

-- ---------------------------------------------------------------------
-- 7. 异常事件与处理动作（execution）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_ex_exception (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    exception_no VARCHAR(64) NOT NULL COMMENT '异常单号（租户内唯一）',
    work_order_id BIGINT NULL COMMENT '工单 ID',
    task_id BIGINT NULL COMMENT '任务 ID',
    exception_type VARCHAR(16) NOT NULL COMMENT '类型：EQUIPMENT/MATERIAL/QUALITY/OTHER',
    reason_code VARCHAR(64) NULL COMMENT '异常原因编码',
    reason_name VARCHAR(128) NULL COMMENT '异常原因名称快照',
    description VARCHAR(512) NOT NULL COMMENT '异常描述',
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN'
        COMMENT '状态：OPEN/ACKNOWLEDGED/RESOLVED/CLOSED',
    reported_by VARCHAR(64) NULL COMMENT '上报人',
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    resolved_at DATETIME NULL COMMENT '解决时间',
    resolution VARCHAR(512) NULL COMMENT '处理结论',
    escalation_level INT NOT NULL DEFAULT 0 COMMENT '升级层级',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_ex_exception_no (tenant_id, exception_no),
    KEY idx_mes_ex_exception_status (tenant_id, status),
    KEY idx_mes_ex_exception_task (tenant_id, task_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 现场异常事件';

CREATE TABLE IF NOT EXISTS mes_ex_exception_action (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    exception_id BIGINT NOT NULL COMMENT '异常事件 ID',
    action_type VARCHAR(16) NOT NULL COMMENT '动作：REPORT/ACKNOWLEDGE/RESOLVE/CLOSE',
    from_status VARCHAR(16) NULL COMMENT '原状态',
    to_status VARCHAR(16) NOT NULL COMMENT '新状态',
    action_by VARCHAR(64) NULL COMMENT '操作人',
    action_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    KEY idx_mes_ex_action_exception (tenant_id, exception_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 异常处理动作';

-- =====================================================================
-- 完成。校验建议：
--   SHOW TABLES LIKE 'mes_dp_%';
--   SHOW TABLES LIKE 'mes_ex_%';
--   -- 期望：mes_dp_* 5 张、mes_ex_* 8 张
-- =====================================================================
