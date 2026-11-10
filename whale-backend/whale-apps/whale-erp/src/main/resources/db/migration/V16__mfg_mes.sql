-- =====================================================================
-- BlueWhale ERP - V16 ERP ↔ MES 打通（下发状态 / 回调幂等 / 进度快照）
-- =====================================================================
-- 适用库   : bluewhale_erp（MySQL 8.0+，utf8mb4）
-- 计划     : docs/plans/2026-11-06-five-services-backlog-stepwise.md Step 4
-- 规则     : 1) ALTER TABLE ADD COLUMN / CREATE TABLE IF NOT EXISTS；
--            2) 禁止 DROP / TRUNCATE / DELETE；
--            3) 数量 DECIMAL(18,4)，金额 DECIMAL(18,2)；
--            4) 表间逻辑外键，不建物理 FOREIGN KEY。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 生产订单头：MES 下发状态与回执信息
-- ---------------------------------------------------------------------
ALTER TABLE erp_mfg_production_order
    ADD COLUMN mes_sync_status VARCHAR(16) NOT NULL DEFAULT 'NOT_SYNCED'
        COMMENT 'MES 下发状态：NOT_SYNCED/PUSHED/FAILED',
    ADD COLUMN mes_work_order_code VARCHAR(64) DEFAULT NULL
        COMMENT 'MES 工单编码',
    ADD COLUMN mes_synced_at DATETIME DEFAULT NULL
        COMMENT '最近成功下发时间',
    ADD COLUMN mes_last_error VARCHAR(255) DEFAULT NULL
        COMMENT '最近一次下发/拉取失败摘要';

-- ---------------------------------------------------------------------
-- 2. MES 回调幂等流水（COMPLETION / PROGRESS）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_mfg_mes_callback (
    id                  BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    order_id            BIGINT        DEFAULT NULL        COMMENT '生产订单ID（单号未知时为空）',
    erp_order_no        VARCHAR(64)   NOT NULL            COMMENT 'ERP 生产订单号',
    callback_type       VARCHAR(16)   NOT NULL            COMMENT '回调类型：COMPLETION/PROGRESS',
    source_event_id     VARCHAR(128)  NOT NULL            COMMENT 'MES 事件幂等键',
    status              VARCHAR(16)   NOT NULL            COMMENT '处理结果：APPLIED/REJECTED/DUPLICATE',
    payload             TEXT          DEFAULT NULL        COMMENT '原始事件报文',
    error               VARCHAR(255)  DEFAULT NULL        COMMENT '失败摘要',
    received_at         DATETIME      NOT NULL            COMMENT '接收时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mfg_mes_callback_event (tenant_id, source_event_id),
    KEY idx_mfg_mes_callback_order (tenant_id, erp_order_no),
    KEY idx_mfg_mes_callback_status (tenant_id, callback_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='MES 回调幂等流水';

-- ---------------------------------------------------------------------
-- 3. MES 进度快照（按工单覆盖更新，只读回传不写 ERP 工序）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_mfg_mes_progress (
    id                  BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    order_id            BIGINT        DEFAULT NULL        COMMENT '生产订单ID',
    erp_order_no        VARCHAR(64)   NOT NULL            COMMENT 'ERP 生产订单号',
    mes_status          VARCHAR(24)   DEFAULT NULL        COMMENT 'MES 工单状态',
    planned_quantity    DECIMAL(18,4) DEFAULT NULL        COMMENT 'MES 计划数量',
    reported_quantity   DECIMAL(18,4) DEFAULT NULL        COMMENT 'MES 累计报工数量',
    qualified_quantity  DECIMAL(18,4) DEFAULT NULL        COMMENT 'MES 累计合格数量',
    completed_quantity  DECIMAL(18,4) DEFAULT NULL        COMMENT 'MES 累计完工数量',
    task_json           TEXT          DEFAULT NULL        COMMENT 'MES 任务明细 JSON',
    event_id            VARCHAR(128)  DEFAULT NULL        COMMENT '最近事件幂等键',
    received_at         DATETIME      NOT NULL            COMMENT '接收时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mfg_mes_progress_order (tenant_id, erp_order_no),
    KEY idx_mfg_mes_progress_status (tenant_id, mes_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='MES 进度快照';
