-- =====================================================================
-- Wave 5.79 M0：MES 车间主数据与工单模型升级（V2）
-- =====================================================================
-- 适用库   : bluewhale_mes
-- 执行方式 : Flyway 自动执行；或 mysql -u<user> -p bluewhale_mes < V2__mes_foundation.sql
-- 迁移说明 : 在 V1 基线上新增车间主数据、工艺路线快照与集成发件箱，
--            并升级 mes_work_order 为执行工单主表。
-- 数据迁移 : ENABLED -> RECEIVED，DISABLED -> CANCELLED；不删除任何存量数据。
-- 命名约定 : 新表按上下文二级前缀 mes_md_ / mes_common_。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 执行工单主表升级
-- ---------------------------------------------------------------------
ALTER TABLE mes_work_order
    ADD COLUMN erp_order_no VARCHAR(64) NULL COMMENT 'ERP 生产订单号，MES 侧幂等键',
    ADD COLUMN item_code VARCHAR(64) NULL COMMENT '物料编码',
    ADD COLUMN item_name VARCHAR(128) NULL COMMENT '物料名称',
    ADD COLUMN planned_quantity DECIMAL(18, 4) NULL COMMENT '计划数量',
    ADD COLUMN uom VARCHAR(16) NULL COMMENT '计量单位',
    ADD COLUMN routing_version VARCHAR(32) NULL COMMENT '工艺路线版本快照',
    ADD COLUMN priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT '优先级：NORMAL/URGENT',
    ADD COLUMN planned_start DATETIME NULL COMMENT '计划开工时间',
    ADD COLUMN planned_end DATETIME NULL COMMENT '计划完工时间',
    ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本';

ALTER TABLE mes_work_order
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'RECEIVED'
        COMMENT '状态：RECEIVED/DISPATCHED/IN_PROGRESS/PAUSED/COMPLETED/CLOSED/CANCELLED';

UPDATE mes_work_order SET status = 'RECEIVED' WHERE status = 'ENABLED';
UPDATE mes_work_order SET status = 'CANCELLED' WHERE status = 'DISABLED';

CREATE UNIQUE INDEX uk_mes_work_order_erp_order ON mes_work_order (tenant_id, erp_order_no);
CREATE INDEX idx_mes_work_order_status ON mes_work_order (tenant_id, status);
CREATE INDEX idx_mes_work_order_planned_start ON mes_work_order (tenant_id, planned_start);

-- ---------------------------------------------------------------------
-- 2. 车间主数据
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_md_workshop (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    code VARCHAR(64) NOT NULL COMMENT '车间编码',
    name VARCHAR(128) NOT NULL COMMENT '车间名称',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    address VARCHAR(255) NULL COMMENT '车间位置',
    manager_name VARCHAR(64) NULL COMMENT '车间负责人',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_md_workshop_code (tenant_id, code),
    KEY idx_mes_md_workshop_status (tenant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 车间主数据';

CREATE TABLE IF NOT EXISTS mes_md_line (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    code VARCHAR(64) NOT NULL COMMENT '产线编码',
    name VARCHAR(128) NOT NULL COMMENT '产线名称',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    workshop_id BIGINT NOT NULL COMMENT '所属车间 ID',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_md_line_code (tenant_id, code),
    KEY idx_mes_md_line_workshop (tenant_id, workshop_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 产线主数据';

CREATE TABLE IF NOT EXISTS mes_md_work_center (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    code VARCHAR(64) NOT NULL COMMENT '工作中心编码',
    name VARCHAR(128) NOT NULL COMMENT '工作中心名称',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    workshop_id BIGINT NOT NULL COMMENT '所属车间 ID',
    line_id BIGINT NOT NULL COMMENT '所属产线 ID',
    center_type VARCHAR(32) NOT NULL DEFAULT 'MACHINE' COMMENT '类型：MACHINE/LINE/CELL',
    capacity_per_shift DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '每班产能',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_md_work_center_code (tenant_id, code),
    KEY idx_mes_md_work_center_line (tenant_id, line_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 工作中心主数据';

CREATE TABLE IF NOT EXISTS mes_md_station (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    code VARCHAR(64) NOT NULL COMMENT '工位编码',
    name VARCHAR(128) NOT NULL COMMENT '工位名称',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    line_id BIGINT NOT NULL COMMENT '所属产线 ID',
    work_center_id BIGINT NOT NULL COMMENT '所属工作中心 ID',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_md_station_code (tenant_id, code),
    KEY idx_mes_md_station_work_center (tenant_id, work_center_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 工位主数据';

CREATE TABLE IF NOT EXISTS mes_md_team (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    code VARCHAR(64) NOT NULL COMMENT '班组编码',
    name VARCHAR(128) NOT NULL COMMENT '班组名称',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    workshop_id BIGINT NOT NULL COMMENT '所属车间 ID',
    leader_name VARCHAR(64) NULL COMMENT '班组长',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_md_team_code (tenant_id, code),
    KEY idx_mes_md_team_workshop (tenant_id, workshop_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 班组主数据';

CREATE TABLE IF NOT EXISTS mes_md_shift (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    code VARCHAR(64) NOT NULL COMMENT '班次编码',
    name VARCHAR(128) NOT NULL COMMENT '班次名称',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    start_time TIME NOT NULL COMMENT '开始时间',
    end_time TIME NOT NULL COMMENT '结束时间',
    cross_day TINYINT NOT NULL DEFAULT 0 COMMENT '是否跨日：0 否，1 是',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_md_shift_code (tenant_id, code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 班次主数据';

CREATE TABLE IF NOT EXISTS mes_md_operation (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    code VARCHAR(64) NOT NULL COMMENT '工序编码',
    name VARCHAR(128) NOT NULL COMMENT '工序名称',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    operation_type VARCHAR(32) NOT NULL DEFAULT 'PROCESS' COMMENT '工序类型：PROCESS/INSPECT/PACK',
    standard_minutes DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '标准工时（分钟）',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_md_operation_code (tenant_id, code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 工序主数据';

CREATE TABLE IF NOT EXISTS mes_md_defect_code (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    code VARCHAR(64) NOT NULL COMMENT '缺陷编码',
    name VARCHAR(128) NOT NULL COMMENT '缺陷名称',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    category VARCHAR(64) NULL COMMENT '缺陷分类',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_md_defect_code_code (tenant_id, code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 缺陷代码主数据';

CREATE TABLE IF NOT EXISTS mes_md_reason_code (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    code VARCHAR(64) NOT NULL COMMENT '原因编码',
    name VARCHAR(128) NOT NULL COMMENT '原因名称',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    category VARCHAR(64) NULL COMMENT '原因分类',
    affect_availability TINYINT NOT NULL DEFAULT 0 COMMENT '是否影响 OEE 可用率：0 否，1 是',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_md_reason_code_code (tenant_id, code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 异常/停机原因主数据';

-- ---------------------------------------------------------------------
-- 3. 工艺路线执行快照（PDM 未就绪前允许手工维护）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_md_routing_snapshot (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    item_code VARCHAR(64) NOT NULL COMMENT '物料编码',
    item_name VARCHAR(128) NULL COMMENT '物料名称',
    version VARCHAR(32) NOT NULL COMMENT '工艺路线版本',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/RELEASED/DISABLED',
    effective_date DATE NULL COMMENT '生效日期',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_md_routing_version (tenant_id, item_code, version),
    KEY idx_mes_md_routing_item (tenant_id, item_code, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 工艺路线执行版本快照';

CREATE TABLE IF NOT EXISTS mes_md_routing_operation (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    routing_id BIGINT NOT NULL COMMENT '工艺路线快照 ID',
    seq_no INT NOT NULL COMMENT '工序顺序',
    operation_code VARCHAR(64) NOT NULL COMMENT '工序编码',
    operation_name VARCHAR(128) NOT NULL COMMENT '工序名称',
    work_center_code VARCHAR(64) NULL COMMENT '工作中心编码',
    standard_minutes DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '标准工时（分钟）',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_md_routing_operation_seq (tenant_id, routing_id, seq_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 工艺路线工序快照';

-- ---------------------------------------------------------------------
-- 4. 集成发件箱（MES -> ERP 可靠回传）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_common_outbox (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    source_event_id VARCHAR(64) NOT NULL COMMENT '源事件幂等键',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
    aggregate_type VARCHAR(64) NULL COMMENT '聚合类型',
    aggregate_id VARCHAR(64) NULL COMMENT '聚合 ID',
    payload TEXT NULL COMMENT '事件报文',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/SENT/FAILED',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_time DATETIME NULL COMMENT '下次重试时间',
    last_error VARCHAR(512) NULL COMMENT '最后错误',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_common_outbox_event (tenant_id, source_event_id),
    KEY idx_mes_common_outbox_retry (status, next_retry_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 集成发件箱';

-- ---------------------------------------------------------------------
-- 5. M0 种子数据（幂等）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO mes_md_workshop (id, tenant_id, code, name, status, address, manager_name, remark)
VALUES (101, 1, 'WS01', '总装车间', 'ENABLED', '一号厂房', '张伟', 'M0 示例车间');

INSERT IGNORE INTO mes_md_line (id, tenant_id, code, name, status, workshop_id, remark)
VALUES (201, 1, 'LN01', '总装一线', 'ENABLED', 101, 'M0 示例产线');

INSERT IGNORE INTO mes_md_work_center (id, tenant_id, code, name, status, workshop_id, line_id, center_type, capacity_per_shift, remark)
VALUES (301, 1, 'WC01', '装配工作中心', 'ENABLED', 101, 201, 'MACHINE', 480, 'M0 示例工作中心');

INSERT IGNORE INTO mes_md_station (id, tenant_id, code, name, status, line_id, work_center_id, remark)
VALUES (401, 1, 'ST01', '装配工位', 'ENABLED', 201, 301, 'M0 示例工位');

INSERT IGNORE INTO mes_md_team (id, tenant_id, code, name, status, workshop_id, leader_name, remark)
VALUES (501, 1, 'TM01', '总装一班', 'ENABLED', 101, '王强', 'M0 示例班组');

INSERT IGNORE INTO mes_md_shift (id, tenant_id, code, name, status, start_time, end_time, cross_day, remark)
VALUES (601, 1, 'SH01', '白班', 'ENABLED', '08:00:00', '20:00:00', 0, 'M0 示例白班');

INSERT IGNORE INTO mes_md_shift (id, tenant_id, code, name, status, start_time, end_time, cross_day, remark)
VALUES (602, 1, 'SH02', '夜班', 'ENABLED', '20:00:00', '08:00:00', 1, 'M0 示例夜班');

INSERT IGNORE INTO mes_md_operation (id, tenant_id, code, name, status, operation_type, standard_minutes, remark)
VALUES (701, 1, 'OP01', '装配', 'ENABLED', 'PROCESS', 30, 'M0 示例工序');

INSERT IGNORE INTO mes_md_operation (id, tenant_id, code, name, status, operation_type, standard_minutes, remark)
VALUES (702, 1, 'OP02', '检验', 'ENABLED', 'INSPECT', 10, 'M0 示例工序');

INSERT IGNORE INTO mes_md_defect_code (id, tenant_id, code, name, status, category, remark)
VALUES (801, 1, 'DF01', '外观不良', 'ENABLED', '外观', 'M0 示例缺陷');

INSERT IGNORE INTO mes_md_defect_code (id, tenant_id, code, name, status, category, remark)
VALUES (802, 1, 'DF02', '尺寸超差', 'ENABLED', '尺寸', 'M0 示例缺陷');

INSERT IGNORE INTO mes_md_reason_code (id, tenant_id, code, name, status, category, affect_availability, remark)
VALUES (901, 1, 'RS01', '设备故障', 'ENABLED', '设备', 1, 'M0 示例原因');

INSERT IGNORE INTO mes_md_reason_code (id, tenant_id, code, name, status, category, affect_availability, remark)
VALUES (902, 1, 'RS02', '物料短缺', 'ENABLED', '物料', 0, 'M0 示例原因');

INSERT IGNORE INTO mes_md_routing_snapshot (id, tenant_id, item_code, item_name, version, status, effective_date, remark)
VALUES (1001, 1, 'DEMO-ITEM', '示例成品', 'V1', 'RELEASED', '2026-11-04', 'M0 示例工艺路线快照');

INSERT IGNORE INTO mes_md_routing_operation (id, tenant_id, routing_id, seq_no, operation_code, operation_name, work_center_code, standard_minutes)
VALUES (1101, 1, 1001, 1, 'OP01', '装配', 'WC01', 30);

INSERT IGNORE INTO mes_md_routing_operation (id, tenant_id, routing_id, seq_no, operation_code, operation_name, work_center_code, standard_minutes)
VALUES (1102, 1, 1001, 2, 'OP02', '检验', 'WC01', 10);

-- =====================================================================
-- 完成。校验建议：
--   SHOW TABLES LIKE 'mes_md_%';
--   SELECT code, name, status FROM mes_md_workshop;
--   SELECT code, name, status, erp_order_no FROM mes_work_order;
-- =====================================================================
