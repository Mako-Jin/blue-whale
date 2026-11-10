-- =====================================================================
-- Wave 5.79 M2：MES 物料与追溯（V5）
-- =====================================================================
-- 适用库   : bluewhale_mes
-- 执行方式 : Flyway 自动执行；或 mysql -u<user> -p bluewhale_mes < V5__mes_trace.sql
-- 迁移说明 : 在 M1（V4 调度/执行 13 表）基础上新增 trace 上下文 mes_tr_* 共 8 张表：
--            批次、条码、条码规则、谱系链接、线边库存、线边流水、扫码校验规则、补货请求。
-- 数据迁移 : 只新增表与索引，不修改/删除存量数据；空库可重复执行。
-- 命名约定 : 上下文二级前缀 mes_tr_；逻辑外键，不建物理外键；金额不在 MES（线边只记数量与批次）。
-- 状态口径 : 批次 ACTIVE/CLOSED；条码 ACTIVE/VOID；谱系链接 ACTIVE/REVERSED；
--            补货 REQUESTED/CONFIRMED/CANCELLED；扫码规则 ENABLED/DISABLED。
-- 不变量   : 条码值租户内唯一；批次号租户内唯一；谱系无环、消耗量 ≤ 投入量；
--            线边库存 ≥ 0，流水只追加（before/after 快照留痕）；补货按 request_no 幂等。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 物料批次（正反追溯的节点）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_tr_batch (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    batch_no VARCHAR(64) NOT NULL COMMENT '批次号（租户内唯一）',
    item_code VARCHAR(64) NOT NULL COMMENT '物料编码',
    item_name VARCHAR(128) NULL COMMENT '物料名称快照',
    batch_type VARCHAR(16) NOT NULL DEFAULT 'RAW'
        COMMENT '批次类型：RAW 来料 / SEMI 半成品 / FINISHED 成品',
    source_type VARCHAR(16) NOT NULL DEFAULT 'PRODUCTION'
        COMMENT '来源：PURCHASE 采购 / PRODUCTION 生产 / RETURN 退料',
    work_order_id BIGINT NULL COMMENT '来源工单 ID',
    work_order_code VARCHAR(64) NULL COMMENT '来源工单编码快照',
    supplier_code VARCHAR(64) NULL COMMENT '供应商编码（来料批次）',
    quantity DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '批次初始数量',
    consumed_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '已被下游消耗数量',
    uom VARCHAR(16) NULL COMMENT '计量单位',
    production_date DATE NULL COMMENT '生产/到货日期',
    expiry_date DATE NULL COMMENT '有效期至',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/CLOSED',
    remark VARCHAR(255) NULL COMMENT '备注',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_tr_batch_no (tenant_id, batch_no),
    KEY idx_mes_tr_batch_item (tenant_id, item_code, status),
    KEY idx_mes_tr_batch_order (tenant_id, work_order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 物料批次';

-- ---------------------------------------------------------------------
-- 2. 条码规则（Code128 / QR 双码，前缀 + 流水号）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_tr_barcode_rule (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    rule_code VARCHAR(64) NOT NULL COMMENT '规则编码（租户内唯一）',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    prefix VARCHAR(32) NOT NULL DEFAULT 'BW' COMMENT '条码前缀',
    code_type VARCHAR(16) NOT NULL DEFAULT 'CODE128' COMMENT '码制：CODE128/QR',
    serial_length INT NOT NULL DEFAULT 6 COMMENT '流水号长度（4–12）',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    remark VARCHAR(255) NULL COMMENT '备注',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_tr_barcode_rule_code (tenant_id, rule_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 条码规则';

-- ---------------------------------------------------------------------
-- 3. 条码（一条码只绑定一个批次/对象）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_tr_barcode (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    barcode_value VARCHAR(128) NOT NULL COMMENT '条码值（租户内唯一）',
    rule_id BIGINT NULL COMMENT '条码规则 ID',
    batch_id BIGINT NOT NULL COMMENT '绑定批次 ID',
    batch_no VARCHAR(64) NOT NULL COMMENT '绑定批次号快照',
    object_type VARCHAR(16) NOT NULL DEFAULT 'BATCH' COMMENT '绑定对象：BATCH 批次',
    item_code VARCHAR(64) NOT NULL COMMENT '物料编码快照',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/VOID',
    print_payload VARCHAR(512) NULL COMMENT '打印载荷（标签系统消费）',
    last_scan_no VARCHAR(64) NULL COMMENT '最近一次扫码幂等键',
    last_scan_time DATETIME NULL COMMENT '最近扫码时间',
    scan_count INT NOT NULL DEFAULT 0 COMMENT '累计扫码次数',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_tr_barcode_value (tenant_id, barcode_value),
    KEY idx_mes_tr_barcode_batch (tenant_id, batch_id, status),
    KEY idx_mes_tr_barcode_item (tenant_id, item_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 物料条码';

-- ---------------------------------------------------------------------
-- 4. 批次谱系链接（父 → 子，消耗关系；只追加，冲销置 REVERSED）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_tr_trace_link (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    parent_batch_id BIGINT NOT NULL COMMENT '上级批次 ID（被消耗）',
    parent_batch_no VARCHAR(64) NOT NULL COMMENT '上级批次号快照',
    child_batch_id BIGINT NOT NULL COMMENT '下级批次 ID（产出）',
    child_batch_no VARCHAR(64) NOT NULL COMMENT '下级批次号快照',
    link_type VARCHAR(16) NOT NULL DEFAULT 'CONSUME' COMMENT '链接类型：CONSUME 消耗',
    quantity DECIMAL(18, 4) NOT NULL COMMENT '消耗数量',
    uom VARCHAR(16) NULL COMMENT '计量单位',
    work_order_id BIGINT NULL COMMENT '关联工单 ID',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/REVERSED',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    KEY idx_mes_tr_link_parent (tenant_id, parent_batch_id, status),
    KEY idx_mes_tr_link_child (tenant_id, child_batch_id, status),
    KEY idx_mes_tr_link_pair (tenant_id, parent_batch_id, child_batch_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 批次谱系链接';

-- ---------------------------------------------------------------------
-- 5. 线边仓库存（物料 + 线边仓 + 批次维度余额，余额 ≥ 0）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_tr_line_side_stock (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    line_id BIGINT NOT NULL COMMENT '产线 ID',
    line_code VARCHAR(64) NOT NULL COMMENT '产线编码快照',
    warehouse_code VARCHAR(64) NOT NULL COMMENT '线边仓编码',
    item_code VARCHAR(64) NOT NULL COMMENT '物料编码',
    item_name VARCHAR(128) NULL COMMENT '物料名称快照',
    batch_no VARCHAR(64) NOT NULL DEFAULT '' COMMENT '批次号（无批次为空串）',
    uom VARCHAR(16) NULL COMMENT '计量单位',
    quantity DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '当前库存数量',
    safety_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '安全库存（低于即建议叫料）',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_tr_stock (tenant_id, line_id, warehouse_code, item_code, batch_no),
    KEY idx_mes_tr_stock_item (tenant_id, item_code, warehouse_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 线边仓库存';

-- ---------------------------------------------------------------------
-- 6. 线边仓流水（只追加，before/after 快照）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_tr_line_side_movement (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    movement_no VARCHAR(64) NOT NULL COMMENT '流水号（租户内唯一）',
    stock_id BIGINT NOT NULL COMMENT '线边库存 ID',
    line_id BIGINT NOT NULL COMMENT '产线 ID',
    line_code VARCHAR(64) NOT NULL COMMENT '产线编码快照',
    warehouse_code VARCHAR(64) NOT NULL COMMENT '线边仓编码',
    item_code VARCHAR(64) NOT NULL COMMENT '物料编码',
    batch_no VARCHAR(64) NOT NULL DEFAULT '' COMMENT '批次号',
    movement_type VARCHAR(16) NOT NULL COMMENT '类型：INBOUND 入库 / ISSUE 领用 / RETURN 退回 / ADJUST 调整',
    direction TINYINT NOT NULL COMMENT '方向：1 增加 / -1 扣减',
    quantity DECIMAL(18, 4) NOT NULL COMMENT '变动数量（正数）',
    before_quantity DECIMAL(18, 4) NOT NULL COMMENT '变动前数量',
    after_quantity DECIMAL(18, 4) NOT NULL COMMENT '变动后数量',
    source_type VARCHAR(16) NULL COMMENT '来源：MANUAL/ISSUE/RETURN/REPLENISH',
    source_no VARCHAR(64) NULL COMMENT '来源单号',
    work_order_id BIGINT NULL COMMENT '关联工单 ID',
    task_id BIGINT NULL COMMENT '关联任务 ID',
    operator VARCHAR(64) NULL COMMENT '操作人',
    remark VARCHAR(255) NULL COMMENT '备注',
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_tr_movement_no (tenant_id, movement_no),
    KEY idx_mes_tr_movement_stock (tenant_id, line_id, item_code, occurred_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 线边仓流水';

-- ---------------------------------------------------------------------
-- 7. 扫码防错规则（工位/工序 + 期望物料，错料阻断或告警）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_tr_scan_rule (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    rule_code VARCHAR(64) NOT NULL COMMENT '规则编码（租户内唯一）',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    station_code VARCHAR(64) NULL COMMENT '适用工位编码（空表示不限）',
    operation_code VARCHAR(64) NULL COMMENT '适用工序编码（空表示不限）',
    expected_item_code VARCHAR(64) NULL COMMENT '期望物料编码（空表示仅做条码有效性校验）',
    action VARCHAR(16) NOT NULL DEFAULT 'BLOCK' COMMENT '命中处理：BLOCK 阻断 / WARN 告警',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    remark VARCHAR(255) NULL COMMENT '备注',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_tr_scan_rule_code (tenant_id, rule_code),
    KEY idx_mes_tr_scan_rule_scope (tenant_id, station_code, operation_code, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 扫码防错规则';

-- ---------------------------------------------------------------------
-- 8. 线边叫料/补货请求（按 request_no 幂等，对接 ERP 库存调拨）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_tr_replenishment (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    request_no VARCHAR(64) NOT NULL COMMENT '补货请求号（租户内唯一，幂等键）',
    line_id BIGINT NOT NULL COMMENT '产线 ID',
    line_code VARCHAR(64) NOT NULL COMMENT '产线编码快照',
    warehouse_code VARCHAR(64) NOT NULL COMMENT '线边仓编码',
    item_code VARCHAR(64) NOT NULL COMMENT '物料编码',
    item_name VARCHAR(128) NULL COMMENT '物料名称快照',
    quantity DECIMAL(18, 4) NOT NULL COMMENT '叫料数量',
    uom VARCHAR(16) NULL COMMENT '计量单位',
    status VARCHAR(16) NOT NULL DEFAULT 'REQUESTED' COMMENT '状态：REQUESTED/CONFIRMED/CANCELLED',
    reason VARCHAR(255) NULL COMMENT '叫料原因',
    requested_by VARCHAR(64) NULL COMMENT '叫料人',
    requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '叫料时间',
    erp_transfer_no VARCHAR(64) NULL COMMENT 'ERP 调拨单号（回传后回填）',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_tr_replenishment_no (tenant_id, request_no),
    KEY idx_mes_tr_replenishment_status (tenant_id, line_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 线边叫料补货请求';

-- =====================================================================
-- 完成。M2 落地后空库累计 36 张业务表（V1 2 + V2 12 + V3 1 + V4 13 + V5 8）。
-- 校验建议：
--   SELECT COUNT(*) FROM information_schema.tables
--    WHERE table_schema = 'bluewhale_mes' AND table_name LIKE 'mes\_%';
-- =====================================================================
