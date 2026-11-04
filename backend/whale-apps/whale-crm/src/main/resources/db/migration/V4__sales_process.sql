-- =====================================================================
-- BlueWhale 客户关系应用（whale-crm）Flyway 增量 V4：M2 线索与商机
-- =====================================================================
-- 适用库   : bluewhale_crm（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/crm-module-panorama.md
-- 计划     : docs/plans/2026-11-04-wave5.83-crm-panorama.md（M2）
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS / ALTER 增列 / UPDATE；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) MySQL 不支持 ADD COLUMN IF NOT EXISTS，统一用 information_schema 守卫；
--            4) 表间逻辑外键，不建物理 FOREIGN KEY。
-- 说明     : 新建线索、线索活动与商机阶段历史三表；商机表补概率、来源、
--            竞争对手、下次动作与阶段进入时间，支撑阶段机与销售漏斗。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 商机表补列（阶段机 / 漏斗）
-- ---------------------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_opportunity' AND COLUMN_NAME = 'probability');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_opportunity ADD COLUMN probability DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT ''赢单概率（0–100）'' AFTER stage', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_opportunity' AND COLUMN_NAME = 'source');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_opportunity ADD COLUMN source VARCHAR(64) DEFAULT NULL COMMENT ''商机来源'' AFTER probability', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_opportunity' AND COLUMN_NAME = 'competitor');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_opportunity ADD COLUMN competitor VARCHAR(255) DEFAULT NULL COMMENT ''主要竞争对手'' AFTER source', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_opportunity' AND COLUMN_NAME = 'next_action_at');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_opportunity ADD COLUMN next_action_at DATETIME DEFAULT NULL COMMENT ''下次跟进行动时间'' AFTER competitor', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_opportunity' AND COLUMN_NAME = 'stage_entered_at');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_opportunity ADD COLUMN stage_entered_at DATETIME DEFAULT NULL COMMENT ''进入当前阶段时间'' AFTER next_action_at', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_opportunity' AND COLUMN_NAME = 'version');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_opportunity ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''版本号（阶段流转乐观并发）'' AFTER stage_entered_at', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_opportunity' AND INDEX_NAME = 'idx_crm_opportunity_stage_owner');
SET @ddl := IF(@has = 0, 'CREATE INDEX idx_crm_opportunity_stage_owner ON crm_opportunity (tenant_id, stage, owner_id)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 线索（聚合根）
--    status: NEW / CONTACTED / QUALIFIED / CONVERTED / INVALID
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_lead (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    lead_no             VARCHAR(64)   NOT NULL                COMMENT '线索编号（租户内唯一）',
    name                VARCHAR(128)  NOT NULL                COMMENT '线索名称（公司或联系人）',
    source              VARCHAR(32)   DEFAULT NULL            COMMENT '来源：WEB / EXHIBITION / REFERRAL / OUTBOUND / OTHER',
    industry            VARCHAR(64)   DEFAULT NULL            COMMENT '所属行业',
    region              VARCHAR(64)   DEFAULT NULL            COMMENT '地区',
    contact_name        VARCHAR(64)   DEFAULT NULL            COMMENT '联系人姓名',
    contact_phone       VARCHAR(64)   DEFAULT NULL            COMMENT '联系电话（输出脱敏）',
    contact_email       VARCHAR(128)  DEFAULT NULL            COMMENT '联系邮箱（输出脱敏）',
    status              VARCHAR(16)   NOT NULL DEFAULT 'NEW'  COMMENT '状态：NEW / CONTACTED / QUALIFIED / CONVERTED / INVALID',
    invalid_reason      VARCHAR(255)  DEFAULT NULL            COMMENT '无效原因（INVALID 必填）',
    owner_id            BIGINT        DEFAULT NULL            COMMENT '线索负责人（数据权限归属人）',
    dept_id             BIGINT        DEFAULT NULL            COMMENT '归属部门',
    customer_id         BIGINT        DEFAULT NULL            COMMENT '转化后的客户ID',
    opportunity_id      BIGINT        DEFAULT NULL            COMMENT '转化后的商机ID',
    converted_at        DATETIME      DEFAULT NULL            COMMENT '转化时间',
    normalized_name     VARCHAR(128)  DEFAULT NULL            COMMENT '归一化名称（查重）',
    normalized_phone    VARCHAR(32)   DEFAULT NULL            COMMENT '归一化电话（查重）',
    normalized_email    VARCHAR(128)  DEFAULT NULL            COMMENT '归一化邮箱（查重）',
    remark              VARCHAR(500)  DEFAULT NULL            COMMENT '备注（不记录敏感信息）',
    version             INT           NOT NULL DEFAULT 0      COMMENT '版本号（转化幂等）',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_lead_no (tenant_id, lead_no),
    KEY idx_crm_lead_owner (tenant_id, owner_id, status),
    KEY idx_crm_lead_status (tenant_id, status, update_time),
    KEY idx_crm_lead_norm_phone (tenant_id, normalized_phone),
    KEY idx_crm_lead_norm_email (tenant_id, normalized_email),
    KEY idx_crm_lead_norm_name (tenant_id, normalized_name)
) COMMENT='销售线索';

-- ---------------------------------------------------------------------
-- 3. 线索活动（append-only 跟进）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_lead_activity (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    lead_id             BIGINT        NOT NULL                COMMENT '线索ID',
    channel             VARCHAR(16)   NOT NULL                COMMENT '渠道：CALL / VISIT / EMAIL / IM / WECHAT / OTHER',
    content             VARCHAR(1000) NOT NULL                COMMENT '活动内容',
    result              VARCHAR(255)  DEFAULT NULL            COMMENT '活动结果',
    next_action_at      DATETIME      DEFAULT NULL            COMMENT '下次行动时间',
    created_by          BIGINT        DEFAULT NULL            COMMENT '记录人',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_crm_lead_activity_lead (tenant_id, lead_id, create_time)
) COMMENT='线索活动记录';

-- ---------------------------------------------------------------------
-- 4. 商机阶段历史（append-only）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_opportunity_stage_history (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    opportunity_id      BIGINT        NOT NULL                COMMENT '商机ID',
    from_stage          VARCHAR(16)   DEFAULT NULL            COMMENT '原阶段；创建时为空',
    to_stage            VARCHAR(16)   NOT NULL                COMMENT '目标阶段',
    amount              DECIMAL(18,2) DEFAULT NULL            COMMENT '流转时金额快照',
    probability         DECIMAL(5,2)  DEFAULT NULL            COMMENT '流转时概率快照',
    lost_reason         VARCHAR(255)  DEFAULT NULL            COMMENT '输单原因（LOST 必填）',
    operator_id         BIGINT        DEFAULT NULL            COMMENT '操作人',
    changed_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '流转时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_crm_opportunity_history (tenant_id, opportunity_id, changed_at)
) COMMENT='商机阶段流转历史';
