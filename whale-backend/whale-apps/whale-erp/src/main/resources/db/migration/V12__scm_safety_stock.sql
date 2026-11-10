-- =====================================================================
-- BlueWhale ERP - V12 安全库存（M2 剩余 B 第四期：安全库存 / 缺料预警 / 呆滞分析）
-- =====================================================================
-- 适用库   : bluewhale_erp（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/erp-module-panorama.md §3.2
-- 计划     : docs/plans/2026-11-03-wave5.78-erp-panorama.md §12.8
-- 规格     : docs/specs/2026-11-05-wave5.78-erp-m2-safety-stock/spec.md
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) 数量 DECIMAL(18,4)，金额沿用余额表口径；
--            4) 表间逻辑外键，不建物理外键；
--            5) 缺料预警与呆滞分析均为查询计算，不建快照表。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 安全库存配置（仓库 + 物料 租户内唯一）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS erp_scm_safety_stock (
    id                BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    warehouse_id      BIGINT        NOT NULL            COMMENT '仓库ID',
    item_id           BIGINT        NOT NULL            COMMENT '物料ID',
    item_code         VARCHAR(64)   NOT NULL            COMMENT '物料编码快照',
    item_name         VARCHAR(128)  NOT NULL            COMMENT '物料名称快照',
    safety_qty        DECIMAL(18,4) NOT NULL DEFAULT 0  COMMENT '安全库存量',
    max_qty           DECIMAL(18,4) DEFAULT NULL        COMMENT '最高库存量（空表示不设上限）',
    remark            VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_safety_stock (tenant_id, warehouse_id, item_id),
    KEY idx_safety_stock_item (tenant_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='安全库存配置';
