-- =====================================================================
-- BlueWhale 客户关系应用（whale-crm）Flyway 增量 V6：M2 销售预测与销售目标
-- =====================================================================
-- 适用库   : bluewhale_crm（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/crm-module-panorama.md
-- 规格     : docs/specs/2026-11-04-wave5.83-crm-forecast-target/spec.md
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) 表间逻辑外键，不建物理 FOREIGN KEY；
--            4) 不写业务种子数据。
-- 说明     : 预测快照 / 预测调整 / 销售目标 / 目标调整四表。
--            scope_type = USER 时 owner_id 为负责人、dept_id 为 0；
--            scope_type = DEPT 时 dept_id 为部门、owner_id 为 0。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 销售预测快照（按周期 + 范围唯一，重跑覆盖）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_forecast_snapshot (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    period                VARCHAR(10)   NOT NULL                COMMENT '周期：yyyy-MM（月）或 yyyy-Qn（季）',
    period_type           VARCHAR(10)   NOT NULL DEFAULT 'MONTH' COMMENT '周期类型：MONTH / QUARTER',
    scope_type            VARCHAR(10)   NOT NULL DEFAULT 'USER'  COMMENT '范围类型：USER / DEPT',
    owner_id              BIGINT        NOT NULL DEFAULT 0      COMMENT '负责人（USER 范围）',
    dept_id               BIGINT        NOT NULL DEFAULT 0      COMMENT '归属部门（DEPT 范围）',
    pipeline_amount       DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '管道金额（全部未关闭商机）',
    best_amount           DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '最佳金额（谈判阶段商机）',
    commit_amount         DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '承诺金额（已赢单 + 高概率商机）',
    weighted_amount       DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '概率加权金额',
    opportunity_count     INT           NOT NULL DEFAULT 0      COMMENT '未关闭商机数',
    manual_adjusted       TINYINT       NOT NULL DEFAULT 0      COMMENT '是否经过手工调整：0 否 / 1 是',
    remark                VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    created_by            BIGINT        DEFAULT NULL            COMMENT '生成人',
    lock_version          INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_forecast_period_scope (tenant_id, period_type, period, scope_type, owner_id, dept_id),
    KEY idx_crm_forecast_owner (tenant_id, owner_id, period)
) COMMENT='销售预测快照';

-- ---------------------------------------------------------------------
-- 2. 销售预测手工调整留痕
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_forecast_adjustment (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    snapshot_id           BIGINT        NOT NULL                COMMENT '预测快照ID',
    field_code            VARCHAR(20)   NOT NULL                COMMENT '调整字段：PIPELINE / BEST / COMMIT',
    before_amount         DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '调整前金额',
    after_amount          DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '调整后金额',
    reason                VARCHAR(500)  NOT NULL                COMMENT '调整原因',
    operator_id           BIGINT        DEFAULT NULL            COMMENT '操作人',
    adjusted_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调整时间',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_crm_forecast_adjustment_snapshot (tenant_id, snapshot_id, adjusted_at)
) COMMENT='销售预测调整记录';

-- ---------------------------------------------------------------------
-- 3. 销售目标（个人 / 部门，按周期唯一）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_sales_target (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    period                VARCHAR(10)   NOT NULL                COMMENT '周期：yyyy-MM（月）或 yyyy-Qn（季）',
    period_type           VARCHAR(10)   NOT NULL DEFAULT 'MONTH' COMMENT '周期类型：MONTH / QUARTER',
    scope_type            VARCHAR(10)   NOT NULL DEFAULT 'USER'  COMMENT '范围类型：USER / DEPT',
    owner_id              BIGINT        NOT NULL DEFAULT 0      COMMENT '负责人（USER 范围）',
    dept_id               BIGINT        NOT NULL DEFAULT 0      COMMENT '归属部门（DEPT 范围）',
    target_amount         DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '目标金额（签约额口径）',
    remark                VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    created_by            BIGINT        DEFAULT NULL            COMMENT '创建人',
    lock_version          INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_sales_target_period_scope (tenant_id, period_type, period, scope_type, owner_id, dept_id),
    KEY idx_crm_sales_target_owner (tenant_id, owner_id, period)
) COMMENT='销售目标';

-- ---------------------------------------------------------------------
-- 4. 销售目标调整留痕
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_sales_target_adjustment (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    target_id             BIGINT        NOT NULL                COMMENT '销售目标ID',
    before_amount         DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '调整前金额',
    after_amount          DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '调整后金额',
    reason                VARCHAR(500)  NOT NULL                COMMENT '调整原因',
    operator_id           BIGINT        DEFAULT NULL            COMMENT '操作人',
    adjusted_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调整时间',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_crm_sales_target_adjustment_target (tenant_id, target_id, adjusted_at)
) COMMENT='销售目标调整记录';
