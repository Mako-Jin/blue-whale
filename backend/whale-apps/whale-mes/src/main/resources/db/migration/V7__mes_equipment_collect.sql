-- =====================================================================
-- Wave 5.79 M4：MES 设备与数据采集（V7）
-- =====================================================================
-- 适用库   : bluewhale_mes
-- 执行方式 : Flyway 自动执行；或 mysql -u<user> -p bluewhale_mes < V7__mes_equipment_collect.sql
-- 迁移说明 : 在 M3（V6 车间质量 10 表，累计 46 表）基础上新增 equipment 上下文 mes_eq_* 10 张表与
--            collect 上下文 mes_cl_* 5 张表，共 15 张表，空库累计 61 张业务表。
-- 数据迁移 : 只新增表与索引，不修改/删除存量数据；空库可重复执行。
-- 命名约定 : 上下文二级前缀 mes_eq_ / mes_cl_；逻辑外键，不建物理外键；设备控制与 PLC 归边缘网关。
-- 状态口径 : 设备状态 RUNNING / IDLE / FAULT / MAINTENANCE / OFFLINE（事件流只追加不覆盖）；
--            保养工单 PLANNED → DUE → IN_PROGRESS → COMPLETED → ACCEPTED，旁支 OVERDUE；
--            维修工单 REPORTED → ASSIGNED → REPAIRING → VERIFIED → CLOSED；
--            备件流水 IN 入库 / OUT 领用 / RETURN 退回 / ADJUST 盘点调整；
--            采集设备 ONLINE / OFFLINE / DISABLED；归档策略 ENABLED / DISABLED。
-- 不变量   : 设备编码租户内唯一；故障设备门禁阻断开工；状态事件只追加；
--            保养项未全部有结论不可完工验收；备件库存 ≥ 0；维修完成需验证后关闭；
--            采集样本 (tenant_id, point_id, sampled_at, seq) 幂等；归档任务可重跑（预览幂等）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 设备台账（编码、参数、所属车间/产线、保养周期、状态基线）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_eq_equipment (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    equipment_code VARCHAR(64) NOT NULL COMMENT '设备编码（租户内唯一）',
    equipment_name VARCHAR(128) NOT NULL COMMENT '设备名称',
    equipment_type VARCHAR(32) NOT NULL DEFAULT 'MACHINE' COMMENT '设备类型：MACHINE/ROBOT/AGV/INSPECTION/OTHER',
    workshop_id BIGINT NULL COMMENT '所属车间 ID',
    workshop_code VARCHAR(64) NULL COMMENT '所属车间编码快照',
    line_id BIGINT NULL COMMENT '所属产线 ID',
    line_code VARCHAR(64) NULL COMMENT '所属产线编码快照',
    work_center_id BIGINT NULL COMMENT '所属工作中心 ID',
    work_center_code VARCHAR(64) NULL COMMENT '所属工作中心编码快照',
    station_code VARCHAR(64) NULL COMMENT '工位编码',
    model_no VARCHAR(64) NULL COMMENT '规格型号',
    manufacturer VARCHAR(128) NULL COMMENT '制造厂商',
    purchase_date DATE NULL COMMENT '购入日期',
    maintenance_cycle_days INT NOT NULL DEFAULT 30 COMMENT '保养周期（天，0 表示不启用）',
    status_baseline VARCHAR(16) NOT NULL DEFAULT 'RUNNING' COMMENT '状态基线：RUNNING/IDLE/FAULT/MAINTENANCE/OFFLINE',
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING' COMMENT '当前状态（由状态事件驱动）',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0 否，1 是',
    remark VARCHAR(255) NULL COMMENT '备注',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_eq_equipment_code (tenant_id, equipment_code),
    KEY idx_mes_eq_equipment_line (tenant_id, line_id, status),
    KEY idx_mes_eq_equipment_status (tenant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 设备台账';

-- ---------------------------------------------------------------------
-- 2. 设备状态事件（只追加，不覆盖历史）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_eq_status_event (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    equipment_id BIGINT NOT NULL COMMENT '设备 ID',
    equipment_code VARCHAR(64) NOT NULL COMMENT '设备编码快照',
    from_status VARCHAR(16) NULL COMMENT '变更前状态',
    to_status VARCHAR(16) NOT NULL COMMENT '变更后状态：RUNNING/IDLE/FAULT/MAINTENANCE/OFFLINE',
    event_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL 手工 / COLLECT 采集 / WORK_ORDER 工单联动',
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    duration_seconds BIGINT NULL COMMENT '上一状态持续秒数',
    operator VARCHAR(64) NULL COMMENT '操作人',
    source_event_id VARCHAR(64) NULL COMMENT '来源事件幂等键（采集/网关）',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    KEY idx_mes_eq_status_event_eq (tenant_id, equipment_id, occurred_at),
    KEY idx_mes_eq_status_event_source (tenant_id, source_event_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 设备状态事件';

-- ---------------------------------------------------------------------
-- 3. 停机记录（故障状态自动开单，恢复时关闭）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_eq_downtime (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    downtime_no VARCHAR(64) NOT NULL COMMENT '停机单号（租户内唯一）',
    equipment_id BIGINT NOT NULL COMMENT '设备 ID',
    equipment_code VARCHAR(64) NOT NULL COMMENT '设备编码快照',
    status_event_id BIGINT NULL COMMENT '触发停机的状态事件 ID',
    reason_code VARCHAR(64) NULL COMMENT '停机原因代码',
    reason_name VARCHAR(128) NULL COMMENT '停机原因名称',
    planned_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否计划停机：0 否（故障），1 是',
    start_at DATETIME NOT NULL COMMENT '停机开始时间',
    end_at DATETIME NULL COMMENT '停机结束时间（空表示未恢复）',
    duration_minutes DECIMAL(18, 4) NULL COMMENT '停机时长（分钟，恢复时回填）',
    remark VARCHAR(255) NULL COMMENT '备注',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_eq_downtime_no (tenant_id, downtime_no),
    KEY idx_mes_eq_downtime_eq (tenant_id, equipment_id, start_at),
    KEY idx_mes_eq_downtime_open (tenant_id, equipment_id, end_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 设备停机记录';

-- ---------------------------------------------------------------------
-- 4. 保养计划（周期、保养项清单、下次到期）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_eq_maintenance_plan (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    plan_no VARCHAR(64) NOT NULL COMMENT '保养计划编号（租户内唯一）',
    equipment_id BIGINT NOT NULL COMMENT '设备 ID',
    equipment_code VARCHAR(64) NOT NULL COMMENT '设备编码快照',
    maintenance_type VARCHAR(16) NOT NULL DEFAULT 'ROUTINE'
        COMMENT '保养类型：ROUTINE 例行 / INSPECTION 点检 / OVERHAUL 大修',
    cycle_days INT NOT NULL DEFAULT 30 COMMENT '保养周期（天）',
    checklist VARCHAR(1000) NULL COMMENT '保养项清单（分号分隔，每项均需结论才可完工）',
    last_done_at DATETIME NULL COMMENT '上次完成时间',
    next_due_at DATETIME NULL COMMENT '下次到期时间',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    remark VARCHAR(255) NULL COMMENT '备注',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_eq_maintenance_plan_no (tenant_id, plan_no),
    KEY idx_mes_eq_maintenance_plan_eq (tenant_id, equipment_id, status),
    KEY idx_mes_eq_maintenance_plan_due (tenant_id, status, next_due_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 保养计划';

-- ---------------------------------------------------------------------
-- 5. 保养工单（PLANNED → DUE → IN_PROGRESS → COMPLETED → ACCEPTED，旁支 OVERDUE）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_eq_maintenance_order (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    order_no VARCHAR(64) NOT NULL COMMENT '保养工单号（租户内唯一）',
    plan_id BIGINT NULL COMMENT '保养计划 ID',
    plan_no VARCHAR(64) NULL COMMENT '保养计划编号快照',
    equipment_id BIGINT NOT NULL COMMENT '设备 ID',
    equipment_code VARCHAR(64) NOT NULL COMMENT '设备编码快照',
    maintenance_type VARCHAR(16) NOT NULL DEFAULT 'ROUTINE' COMMENT '保养类型快照',
    checklist VARCHAR(1000) NULL COMMENT '保养项清单快照',
    due_at DATETIME NULL COMMENT '应完成时间',
    status VARCHAR(16) NOT NULL DEFAULT 'PLANNED'
        COMMENT '状态：PLANNED/DUE/IN_PROGRESS/COMPLETED/ACCEPTED，旁支 OVERDUE',
    assignee VARCHAR(64) NULL COMMENT '执行人',
    started_at DATETIME NULL COMMENT '开始时间',
    completed_at DATETIME NULL COMMENT '完工时间',
    completed_by VARCHAR(64) NULL COMMENT '完工登记人',
    accepted_by VARCHAR(64) NULL COMMENT '验收人',
    accepted_at DATETIME NULL COMMENT '验收时间',
    conclusion VARCHAR(255) NULL COMMENT '验收结论',
    checklist_result VARCHAR(1000) NULL COMMENT '保养项结论（分号分隔，项名=结论）',
    remark VARCHAR(255) NULL COMMENT '备注',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_eq_maintenance_order_no (tenant_id, order_no),
    KEY idx_mes_eq_maintenance_order_eq (tenant_id, equipment_id, status),
    KEY idx_mes_eq_maintenance_order_due (tenant_id, status, due_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 保养工单';

-- ---------------------------------------------------------------------
-- 6. 维修工单（REPORTED → ASSIGNED → REPAIRING → VERIFIED → CLOSED）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_eq_repair_order (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    repair_no VARCHAR(64) NOT NULL COMMENT '维修工单号（租户内唯一）',
    equipment_id BIGINT NOT NULL COMMENT '设备 ID',
    equipment_code VARCHAR(64) NOT NULL COMMENT '设备编码快照',
    fault_desc VARCHAR(255) NOT NULL COMMENT '故障描述',
    severity VARCHAR(16) NOT NULL DEFAULT 'MAJOR' COMMENT '严重度：MINOR/MAJOR/CRITICAL',
    status VARCHAR(16) NOT NULL DEFAULT 'REPORTED'
        COMMENT '状态：REPORTED/ASSIGNED/REPAIRING/VERIFIED/CLOSED',
    reported_by VARCHAR(64) NULL COMMENT '报修人',
    reported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报修时间',
    assignee VARCHAR(64) NULL COMMENT '维修人',
    assigned_at DATETIME NULL COMMENT '派工时间',
    started_at DATETIME NULL COMMENT '开始维修时间',
    repair_desc VARCHAR(500) NULL COMMENT '维修说明',
    verified_by VARCHAR(64) NULL COMMENT '验证人',
    verified_at DATETIME NULL COMMENT '验证时间',
    closed_at DATETIME NULL COMMENT '关闭时间',
    verify_conclusion VARCHAR(255) NULL COMMENT '验证结论',
    downtime_minutes DECIMAL(18, 4) NULL COMMENT '本次维修对应停机时长（分钟）',
    remark VARCHAR(255) NULL COMMENT '备注',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_eq_repair_no (tenant_id, repair_no),
    KEY idx_mes_eq_repair_eq (tenant_id, equipment_id, status),
    KEY idx_mes_eq_repair_status (tenant_id, status, reported_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 维修工单';

-- ---------------------------------------------------------------------
-- 7. 备件主档（编码、规格、安全库存）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_eq_spare_part (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    part_code VARCHAR(64) NOT NULL COMMENT '备件编码（租户内唯一）',
    part_name VARCHAR(128) NOT NULL COMMENT '备件名称',
    spec VARCHAR(128) NULL COMMENT '规格型号',
    uom VARCHAR(16) NOT NULL DEFAULT 'PCS' COMMENT '计量单位',
    safety_stock DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '安全库存',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    remark VARCHAR(255) NULL COMMENT '备注',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_eq_spare_part_code (tenant_id, part_code),
    KEY idx_mes_eq_spare_part_status (tenant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 备件主档';

-- ---------------------------------------------------------------------
-- 8. 备件库存（按仓库汇总，余额 ≥ 0）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_eq_spare_part_stock (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    part_id BIGINT NOT NULL COMMENT '备件 ID',
    part_code VARCHAR(64) NOT NULL COMMENT '备件编码快照',
    warehouse_code VARCHAR(64) NOT NULL DEFAULT 'DEFAULT' COMMENT '仓库编码',
    quantity DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '库存数量（不得为负）',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_eq_spare_part_stock (tenant_id, part_id, warehouse_code),
    KEY idx_mes_eq_spare_part_stock_code (tenant_id, part_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 备件库存';

-- ---------------------------------------------------------------------
-- 9. 备件流水（只追加，记录 before/after）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_eq_spare_part_movement (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    movement_no VARCHAR(64) NOT NULL COMMENT '流水号（租户内唯一）',
    part_id BIGINT NOT NULL COMMENT '备件 ID',
    part_code VARCHAR(64) NOT NULL COMMENT '备件编码快照',
    stock_id BIGINT NOT NULL COMMENT '库存行 ID',
    warehouse_code VARCHAR(64) NOT NULL COMMENT '仓库编码',
    movement_type VARCHAR(16) NOT NULL COMMENT '类型：IN 入库 / OUT 领用 / RETURN 退回 / ADJUST 盘点调整',
    quantity DECIMAL(18, 4) NOT NULL COMMENT '变动数量（正数）',
    before_quantity DECIMAL(18, 4) NOT NULL COMMENT '变动前数量',
    after_quantity DECIMAL(18, 4) NOT NULL COMMENT '变动后数量',
    ref_no VARCHAR(64) NULL COMMENT '关联单号（维修工单/采购单）',
    operator VARCHAR(64) NULL COMMENT '操作人',
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_eq_spare_part_movement_no (tenant_id, movement_no),
    KEY idx_mes_eq_spare_part_movement_part (tenant_id, part_id, occurred_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 备件流水';

-- ---------------------------------------------------------------------
-- 10. OEE 快照（按设备 + 班次；可用率/性能/质量/综合效率）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_eq_oee_snapshot (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    snapshot_no VARCHAR(64) NOT NULL COMMENT '快照编号（租户内唯一）',
    equipment_id BIGINT NOT NULL COMMENT '设备 ID',
    equipment_code VARCHAR(64) NOT NULL COMMENT '设备编码快照',
    shift_date DATE NOT NULL COMMENT '班次日期',
    shift_code VARCHAR(32) NOT NULL COMMENT '班次编码',
    planned_minutes DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '计划生产时间（分钟）',
    planned_downtime_minutes DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '计划停机时间（分钟，单列不计入可用率分母）',
    run_minutes DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '实际运行时间（分钟）',
    downtime_minutes DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '非计划停机时间（分钟）',
    total_count DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '总产量',
    good_count DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '合格产量',
    ideal_cycle_seconds DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '理论节拍（秒/件）',
    availability DECIMAL(9, 6) NOT NULL DEFAULT 0 COMMENT '可用率 = 运行 / (计划 − 计划停机)',
    performance DECIMAL(9, 6) NOT NULL DEFAULT 0 COMMENT '性能 = 理论产量 / 运行时间',
    quality DECIMAL(9, 6) NOT NULL DEFAULT 0 COMMENT '质量 = 合格 / 总产量',
    oee DECIMAL(9, 6) NOT NULL DEFAULT 0 COMMENT 'OEE = 可用率 × 性能 × 质量',
    formula_config VARCHAR(255) NULL COMMENT '口径配置（可覆盖默认公式）',
    computed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '计算时间',
    remark VARCHAR(255) NULL COMMENT '备注',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_eq_oee_snapshot (tenant_id, equipment_id, shift_date, shift_code),
    KEY idx_mes_eq_oee_snapshot_date (tenant_id, shift_date, equipment_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES OEE 快照';

-- ---------------------------------------------------------------------
-- 11. 采集设备/网关注册（REST/MQTT 推送，MES 不直连 PLC）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_cl_device (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    device_code VARCHAR(64) NOT NULL COMMENT '采集设备编码（租户内唯一）',
    device_name VARCHAR(128) NOT NULL COMMENT '采集设备名称',
    device_type VARCHAR(16) NOT NULL DEFAULT 'GATEWAY'
        COMMENT '类型：GATEWAY 网关 / EDGE 边缘节点 / SENSOR 传感器',
    protocol VARCHAR(16) NOT NULL DEFAULT 'REST' COMMENT '协议：REST / MQTT / OPCUA（经网关）',
    ip_address VARCHAR(64) NULL COMMENT '接入地址',
    line_id BIGINT NULL COMMENT '所属产线 ID',
    line_code VARCHAR(64) NULL COMMENT '所属产线编码快照',
    status VARCHAR(16) NOT NULL DEFAULT 'OFFLINE' COMMENT '状态：ONLINE/OFFLINE/DISABLED',
    last_heartbeat_at DATETIME NULL COMMENT '最近心跳时间',
    remark VARCHAR(255) NULL COMMENT '备注',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_cl_device_code (tenant_id, device_code),
    KEY idx_mes_cl_device_status (tenant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 采集设备';

-- ---------------------------------------------------------------------
-- 12. 采集点注册（绑定设备/工序/指标）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_cl_collection_point (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    point_code VARCHAR(64) NOT NULL COMMENT '采集点编码（租户内唯一）',
    point_name VARCHAR(128) NOT NULL COMMENT '采集点名称',
    device_id BIGINT NULL COMMENT '采集设备 ID',
    device_code VARCHAR(64) NULL COMMENT '采集设备编码快照',
    equipment_id BIGINT NULL COMMENT '设备 ID',
    equipment_code VARCHAR(64) NULL COMMENT '设备编码快照',
    operation_code VARCHAR(64) NULL COMMENT '工序编码',
    metric_type VARCHAR(32) NOT NULL DEFAULT 'METRIC'
        COMMENT '指标类型：STATUS 状态 / COUNT 计数 / TEMPERATURE 温度 / PRESSURE 压力 / SPEED 速度 / METRIC 通用',
    unit VARCHAR(16) NULL COMMENT '计量单位',
    collect_interval_seconds INT NOT NULL DEFAULT 60 COMMENT '采集间隔（秒）',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0 否，1 是',
    remark VARCHAR(255) NULL COMMENT '备注',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_cl_point_code (tenant_id, point_code),
    KEY idx_mes_cl_point_device (tenant_id, device_id, enabled),
    KEY idx_mes_cl_point_equipment (tenant_id, equipment_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 采集点';

-- ---------------------------------------------------------------------
-- 13. 采集样本（按采集点 + 采样时间 + 序号幂等）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_cl_metric_sample (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    point_id BIGINT NOT NULL COMMENT '采集点 ID',
    point_code VARCHAR(64) NOT NULL COMMENT '采集点编码快照',
    equipment_id BIGINT NULL COMMENT '设备 ID',
    equipment_code VARCHAR(64) NULL COMMENT '设备编码快照',
    metric_value DECIMAL(18, 4) NOT NULL COMMENT '指标值',
    unit VARCHAR(16) NULL COMMENT '计量单位快照',
    sampled_at DATETIME NOT NULL COMMENT '采样时间',
    seq INT NOT NULL DEFAULT 1 COMMENT '同采样点同时间的序号（幂等键组成）',
    source_event_id VARCHAR(64) NULL COMMENT '网关事件幂等键',
    payload VARCHAR(1000) NULL COMMENT '原始报文（JSON 文本）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_cl_metric_sample (tenant_id, point_id, sampled_at, seq),
    KEY idx_mes_cl_metric_sample_point (tenant_id, point_id, sampled_at),
    KEY idx_mes_cl_metric_sample_equipment (tenant_id, equipment_id, sampled_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 采集样本';

-- ---------------------------------------------------------------------
-- 14. 看板配置（产线、刷新间隔、指标与布局）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_cl_board_config (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    board_code VARCHAR(64) NOT NULL COMMENT '看板编码（租户内唯一）',
    board_name VARCHAR(128) NOT NULL COMMENT '看板名称',
    line_id BIGINT NULL COMMENT '产线 ID',
    line_code VARCHAR(64) NULL COMMENT '产线编码快照',
    refresh_seconds INT NOT NULL DEFAULT 10 COMMENT '刷新间隔（秒，5–60）',
    metric_codes VARCHAR(500) NULL COMMENT '展示指标编码列表（逗号分隔）',
    layout VARCHAR(500) NULL COMMENT '布局配置（JSON 文本）',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    remark VARCHAR(255) NULL COMMENT '备注',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_cl_board_code (tenant_id, board_code),
    KEY idx_mes_cl_board_line (tenant_id, line_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 看板配置';

-- ---------------------------------------------------------------------
-- 15. 归档策略（保留天数、批次、可重跑的归档预览）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_cl_archive_policy (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    policy_code VARCHAR(64) NOT NULL COMMENT '归档策略编码（租户内唯一）',
    table_name VARCHAR(64) NOT NULL COMMENT '归档目标表（如 mes_cl_metric_sample）',
    retention_days INT NOT NULL DEFAULT 365 COMMENT '保留天数（早于该天的数据可归档）',
    archive_batch_size INT NOT NULL DEFAULT 1000 COMMENT '单批归档条数',
    cron_expr VARCHAR(64) NOT NULL DEFAULT '0 2 * * *' COMMENT '调度表达式（分 时 日 月 周）',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    last_run_at DATETIME NULL COMMENT '最近执行时间',
    last_candidate_count BIGINT NOT NULL DEFAULT 0 COMMENT '最近一次归档候选条数',
    remark VARCHAR(255) NULL COMMENT '备注',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_cl_archive_policy_code (tenant_id, policy_code),
    KEY idx_mes_cl_archive_policy_status (tenant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'MES 归档策略';

-- =====================================================================
-- 完成。M4 落地后空库累计 61 张业务表（V1 2 + V2 12 + V3 1 + V4 13 + V5 8 + V6 10 + V7 15）。
-- 校验建议：
--   SELECT COUNT(*) FROM information_schema.tables
--    WHERE table_schema = 'bluewhale_mes' AND table_name LIKE 'mes\_%';
-- =====================================================================
