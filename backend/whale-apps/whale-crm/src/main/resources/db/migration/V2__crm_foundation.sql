-- =====================================================================
-- BlueWhale 客户关系应用（whale-crm）Flyway 增量 V2：M0 数据底座
-- =====================================================================
-- 适用库   : bluewhale_crm（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/crm-module-panorama.md
-- 计划     : docs/plans/2026-11-04-wave5.83-crm-panorama.md（M0-3）
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS / ALTER 增列 / UPDATE；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) MySQL 不支持 ADD COLUMN IF NOT EXISTS，统一用 information_schema 守卫；
--            4) 表间逻辑外键，不建物理 FOREIGN KEY。
-- 说明     : 客户表补齐档案、归一化查重、公海与最近跟进字段；联系人补微信、
--            决策角色与归一化字段；跟进补结果与下次动作；同时建立集成底座四表。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 客户表补列（档案 / 归一化 / 公海 / 最近跟进）
-- ---------------------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer' AND COLUMN_NAME = 'short_name');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_customer ADD COLUMN short_name VARCHAR(128) DEFAULT NULL COMMENT ''客户简称'' AFTER name', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer' AND COLUMN_NAME = 'website');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_customer ADD COLUMN website VARCHAR(255) DEFAULT NULL COMMENT ''官网（用于域名查重）'' AFTER industry', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer' AND COLUMN_NAME = 'scale');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_customer ADD COLUMN scale VARCHAR(32) DEFAULT NULL COMMENT ''企业规模：MICRO/SMALL/MEDIUM/LARGE'' AFTER website', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer' AND COLUMN_NAME = 'tax_no');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_customer ADD COLUMN tax_no VARCHAR(64) DEFAULT NULL COMMENT ''纳税人识别号（输出脱敏）'' AFTER scale', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer' AND COLUMN_NAME = 'province');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_customer ADD COLUMN province VARCHAR(64) DEFAULT NULL COMMENT ''省'' AFTER tax_no', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer' AND COLUMN_NAME = 'city');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_customer ADD COLUMN city VARCHAR(64) DEFAULT NULL COMMENT ''市'' AFTER province', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer' AND COLUMN_NAME = 'region');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_customer ADD COLUMN region VARCHAR(64) DEFAULT NULL COMMENT ''区县'' AFTER city', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer' AND COLUMN_NAME = 'normalized_name');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_customer ADD COLUMN normalized_name VARCHAR(128) DEFAULT NULL COMMENT ''归一化客户名称（查重）'' AFTER region', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer' AND COLUMN_NAME = 'normalized_domain');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_customer ADD COLUMN normalized_domain VARCHAR(128) DEFAULT NULL COMMENT ''归一化官网域名（查重）'' AFTER normalized_name', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer' AND COLUMN_NAME = 'normalized_tax_no');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_customer ADD COLUMN normalized_tax_no VARCHAR(64) DEFAULT NULL COMMENT ''归一化税号（查重）'' AFTER normalized_domain', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer' AND COLUMN_NAME = 'version');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_customer ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''版本号（画像/维护乐观递增）'' AFTER normalized_tax_no', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer' AND COLUMN_NAME = 'pool_status');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_customer ADD COLUMN pool_status VARCHAR(16) NOT NULL DEFAULT ''OWNED'' COMMENT ''公海状态：OWNED 私有 / IN_POOL 公海'' AFTER version', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer' AND COLUMN_NAME = 'pool_at');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_customer ADD COLUMN pool_at DATETIME DEFAULT NULL COMMENT ''进入/离开公海时间'' AFTER pool_status', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer' AND COLUMN_NAME = 'last_follow_up_at');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_customer ADD COLUMN last_follow_up_at DATETIME DEFAULT NULL COMMENT ''最近跟进时间（公海回收依据）'' AFTER pool_at', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 联系人表补列（微信 / 决策角色 / 归一化）
-- ---------------------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_contact' AND COLUMN_NAME = 'wechat');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_contact ADD COLUMN wechat VARCHAR(64) DEFAULT NULL COMMENT ''微信号（输出不明文）'' AFTER email', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_contact' AND COLUMN_NAME = 'decision_role');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_contact ADD COLUMN decision_role VARCHAR(32) DEFAULT NULL COMMENT ''决策角色：DECIDER/INFLUENCER/USER/GATEKEEPER'' AFTER wechat', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_contact' AND COLUMN_NAME = 'is_decision_maker');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_contact ADD COLUMN is_decision_maker TINYINT NOT NULL DEFAULT 0 COMMENT ''是否关键决策人：1 是 / 0 否'' AFTER decision_role', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_contact' AND COLUMN_NAME = 'normalized_phone');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_contact ADD COLUMN normalized_phone VARCHAR(32) DEFAULT NULL COMMENT ''归一化电话（查重）'' AFTER phone', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_contact' AND COLUMN_NAME = 'normalized_email');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_contact ADD COLUMN normalized_email VARCHAR(128) DEFAULT NULL COMMENT ''归一化邮箱（查重）'' AFTER email', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_contact' AND COLUMN_NAME = 'version');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_contact ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''版本号'' AFTER is_decision_maker', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3. 跟进记录补列（结果 / 下次动作）
-- ---------------------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_follow_up' AND COLUMN_NAME = 'result');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_follow_up ADD COLUMN result VARCHAR(128) DEFAULT NULL COMMENT ''跟进结果'' AFTER content', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_follow_up' AND COLUMN_NAME = 'next_action');
SET @ddl := IF(@has = 0, 'ALTER TABLE crm_follow_up ADD COLUMN next_action VARCHAR(255) DEFAULT NULL COMMENT ''下次动作'' AFTER result', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 4. 索引（information_schema 守卫，保证可重复执行）
-- ---------------------------------------------------------------------
SET @has := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer' AND INDEX_NAME = 'idx_crm_customer_norm_name');
SET @ddl := IF(@has = 0, 'CREATE INDEX idx_crm_customer_norm_name ON crm_customer (tenant_id, normalized_name)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer' AND INDEX_NAME = 'idx_crm_customer_norm_tax');
SET @ddl := IF(@has = 0, 'CREATE INDEX idx_crm_customer_norm_tax ON crm_customer (tenant_id, normalized_tax_no)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer' AND INDEX_NAME = 'idx_crm_customer_norm_domain');
SET @ddl := IF(@has = 0, 'CREATE INDEX idx_crm_customer_norm_domain ON crm_customer (tenant_id, normalized_domain)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer' AND INDEX_NAME = 'idx_crm_customer_pool');
SET @ddl := IF(@has = 0, 'CREATE INDEX idx_crm_customer_pool ON crm_customer (tenant_id, pool_status, pool_at)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_contact' AND INDEX_NAME = 'idx_crm_contact_norm_phone');
SET @ddl := IF(@has = 0, 'CREATE INDEX idx_crm_contact_norm_phone ON crm_contact (tenant_id, normalized_phone)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_contact' AND INDEX_NAME = 'idx_crm_contact_norm_email');
SET @ddl := IF(@has = 0, 'CREATE INDEX idx_crm_contact_norm_email ON crm_contact (tenant_id, normalized_email)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 5. 商机阶段口径收口：LEAD 改名为 CONTACT（M2 将扩展完整阶段机）
-- ---------------------------------------------------------------------
UPDATE crm_opportunity SET stage = 'CONTACT' WHERE stage = 'LEAD';

-- ---------------------------------------------------------------------
-- 6. 集成底座：outbox / 同步日志 / 外部引用 / 幂等
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_common_outbox (
    id             BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    aggregate_type VARCHAR(64)   NOT NULL                COMMENT '聚合类型，如 crm_customer',
    aggregate_id   BIGINT        NOT NULL                COMMENT '聚合ID',
    event_type     VARCHAR(64)   NOT NULL                COMMENT '领域事件类型',
    payload        TEXT          NULL                    COMMENT '事件负载（JSON）',
    status         VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/SENT/FAILED/DEAD',
    retry_count    INT           NOT NULL DEFAULT 0      COMMENT '重试次数',
    next_retry_at  DATETIME      DEFAULT NULL            COMMENT '下次重试时间',
    last_error     VARCHAR(512)  DEFAULT NULL            COMMENT '最近失败原因',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_crm_outbox_status (tenant_id, status, next_retry_at),
    KEY idx_crm_outbox_aggregate (tenant_id, aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='CRM 领域事件 Outbox';

CREATE TABLE IF NOT EXISTS crm_int_sync_log (
    id             BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    direction      VARCHAR(8)    NOT NULL                COMMENT '方向：OUT 出站 / IN 入站',
    target_system  VARCHAR(32)   NOT NULL                COMMENT '目标系统：ERP/CONTRACT/WORKFLOW/MESSAGE/FILE/DOC',
    biz_type       VARCHAR(64)   NOT NULL                COMMENT '业务类型',
    biz_id         BIGINT        DEFAULT NULL            COMMENT '业务ID',
    request_ref    VARCHAR(128)  DEFAULT NULL            COMMENT '请求引用',
    response_ref   VARCHAR(128)  DEFAULT NULL            COMMENT '响应引用',
    status         VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/SUCCESS/FAILED',
    error_msg      VARCHAR(512)  DEFAULT NULL            COMMENT '失败原因',
    cost_ms        BIGINT        DEFAULT NULL            COMMENT '耗时（毫秒）',
    trace_id       VARCHAR(64)   DEFAULT NULL            COMMENT '链路追踪ID',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_crm_sync_biz (tenant_id, biz_type, biz_id),
    KEY idx_crm_sync_status (tenant_id, status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='CRM 跨服务同步日志';

CREATE TABLE IF NOT EXISTS crm_external_ref (
    id              BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    biz_type        VARCHAR(64)   NOT NULL                COMMENT '业务类型，如 crm_customer',
    biz_id          BIGINT        NOT NULL                COMMENT '本系统业务ID',
    external_system VARCHAR(32)   NOT NULL                COMMENT '外部系统',
    external_id     VARCHAR(128)  DEFAULT NULL            COMMENT '外部主键',
    external_no     VARCHAR(128)  DEFAULT NULL            COMMENT '外部业务编号',
    sync_status     VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '同步状态：PENDING/SYNCED/FAILED',
    last_sync_at    DATETIME      DEFAULT NULL            COMMENT '最近同步时间',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_external_ref (tenant_id, biz_type, biz_id, external_system)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='CRM 外部系统引用映射';

CREATE TABLE IF NOT EXISTS crm_idempotency (
    id           BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    idem_key     VARCHAR(128)  NOT NULL                COMMENT '幂等键',
    biz_type     VARCHAR(64)   NOT NULL                COMMENT '业务类型',
    request_hash VARCHAR(128)  DEFAULT NULL            COMMENT '请求指纹',
    response_ref VARCHAR(128)  DEFAULT NULL            COMMENT '结果引用',
    status       VARCHAR(16)   NOT NULL DEFAULT 'PROCESSING' COMMENT '状态：PROCESSING/SUCCESS/FAILED',
    expire_at    DATETIME      DEFAULT NULL            COMMENT '过期时间',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_idem_key (tenant_id, idem_key),
    KEY idx_crm_idem_expire (tenant_id, expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='CRM 接口幂等记录';
