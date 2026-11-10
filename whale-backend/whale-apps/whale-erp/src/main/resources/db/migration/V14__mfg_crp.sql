-- =====================================================================
-- BlueWhale ERP - V14 CRP（M3 剩余：工作中心 / 工序快照 / 产能负荷）
-- =====================================================================
-- 适用库   : bluewhale_erp（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/erp-module-panorama.md §5.1
-- 计划     : docs/plans/2026-11-03-wave5.78-erp-panorama.md §13.6
-- 规则     : 1) CREATE TABLE IF NOT EXISTS / ALTER TABLE ADD COLUMN；
--            2) 禁止 DROP / TRUNCATE / DELETE；
--            3) 数量 DECIMAL(18,4)，工时/产能 DECIMAL(18,2)；
--            4) 工艺路线快照来自 PDM 内网契约，ERP 只保存工序结果；
--            5) 表间逻辑外键，不建物理 FOREIGN KEY。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 工作中心（产能日历按工作日集合 + 日产能 + 效率折算）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_mfg_work_center (
    id                  BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code                VARCHAR(64)   NOT NULL            COMMENT '工作中心编码（租户内唯一）',
    name                VARCHAR(128)  NOT NULL            COMMENT '工作中心名称',
    department          VARCHAR(128)  DEFAULT NULL        COMMENT '所属部门',
    daily_capacity_hours DECIMAL(18,2) NOT NULL           COMMENT '日产能工时（小时）',
    efficiency_rate     DECIMAL(5,2)  NOT NULL DEFAULT 100 COMMENT '效率百分比（0-100）',
    work_days           VARCHAR(20)   NOT NULL DEFAULT '1,2,3,4,5' COMMENT '工作日（ISO 1-7，逗号分隔）',
    status              VARCHAR(24)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    remark              VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mfg_work_center_code (tenant_id, code),
    KEY idx_mfg_work_center_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='生产工作中心';

-- ---------------------------------------------------------------------
-- 2. 生产订单工序（建单/同步时冻结 PDM 已发布路线工序，累计报工数量）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_mfg_production_operation (
    id                  BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    order_id            BIGINT        NOT NULL            COMMENT '生产订单ID',
    step_no             INT           NOT NULL            COMMENT '工序序号（订单内唯一）',
    name                VARCHAR(128)  NOT NULL            COMMENT '工序名称',
    work_center_code    VARCHAR(64)   NOT NULL            COMMENT '工作中心编码快照',
    standard_hours      DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '单件标准工时（小时）',
    setup_hours         DECIMAL(18,2) NOT NULL DEFAULT 0  COMMENT '准备工时（小时）',
    planned_quantity    DECIMAL(18,4) NOT NULL            COMMENT '计划数量',
    qualified_quantity  DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '累计合格数量',
    scrapped_quantity   DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '累计报废数量',
    status              VARCHAR(24)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/IN_PROGRESS/COMPLETED',
    first_report_at     DATETIME      DEFAULT NULL        COMMENT '首次报工时间',
    last_report_at      DATETIME      DEFAULT NULL        COMMENT '最近报工时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mfg_operation_step (tenant_id, order_id, step_no),
    KEY idx_mfg_operation_center (tenant_id, work_center_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='生产订单工序';

-- ---------------------------------------------------------------------
-- 3. 生产订单头：工艺路线快照标识（NONE 无路线 / SNAPSHOT 已冻结 / FAILED 拉取失败）
-- ---------------------------------------------------------------------
ALTER TABLE erp_mfg_production_order
    ADD COLUMN routing_no VARCHAR(64) DEFAULT NULL COMMENT 'PDM 工艺路线编号快照',
    ADD COLUMN routing_version VARCHAR(32) DEFAULT NULL COMMENT 'PDM 工艺路线版本快照',
    ADD COLUMN routing_status VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT '工艺路线状态：NONE/SNAPSHOT/FAILED';
