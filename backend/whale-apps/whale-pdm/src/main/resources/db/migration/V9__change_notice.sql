-- =====================================================================
-- Wave 5.80 M2-4..M2-8：PDM 变更通知（ECN）、追溯、通知日志、任务锁
-- =====================================================================
-- 适用库   : bluewhale_pdm（MySQL 8.0+，utf8mb4）
-- 幂等说明 : 新表 CREATE TABLE IF NOT EXISTS；扩展列逐列检查 information_schema；
--            本脚本不含 DROP / TRUNCATE / DELETE。
-- 边界     : PDM 只保存变更业务事实与追溯；审批归 OA/Workflow，通知归 whale-message。
-- 状态机   : ECN DRAFT → NOTIFIED → EFFECTIVE → CLOSED，旁支 CANCELLED
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 扩展 ECR / ECO 审批快照字段（逐列幂等）
-- ---------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pdm_chg_request' AND COLUMN_NAME = 'approval_instance_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE pdm_chg_request ADD COLUMN approval_instance_id BIGINT DEFAULT NULL COMMENT ''OA 审批实例ID'' AFTER close_time',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pdm_chg_request' AND COLUMN_NAME = 'approval_status');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE pdm_chg_request ADD COLUMN approval_status VARCHAR(16) DEFAULT NULL COMMENT ''OA 审批状态：RUNNING/APPROVED/REJECTED/WITHDRAWN'' AFTER approval_instance_id',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pdm_chg_request' AND COLUMN_NAME = 'approval_sync_time');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE pdm_chg_request ADD COLUMN approval_sync_time DATETIME DEFAULT NULL COMMENT ''OA 状态同步时间'' AFTER approval_status',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pdm_chg_request' AND COLUMN_NAME = 'approval_source');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE pdm_chg_request ADD COLUMN approval_source VARCHAR(16) DEFAULT NULL COMMENT ''审批来源：OA/MANUAL'' AFTER approval_sync_time',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pdm_chg_order' AND COLUMN_NAME = 'approval_instance_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE pdm_chg_order ADD COLUMN approval_instance_id BIGINT DEFAULT NULL COMMENT ''OA 审批实例ID'' AFTER close_time',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pdm_chg_order' AND COLUMN_NAME = 'approval_status');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE pdm_chg_order ADD COLUMN approval_status VARCHAR(16) DEFAULT NULL COMMENT ''OA 审批状态：RUNNING/APPROVED/REJECTED/WITHDRAWN'' AFTER approval_instance_id',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pdm_chg_order' AND COLUMN_NAME = 'approval_sync_time');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE pdm_chg_order ADD COLUMN approval_sync_time DATETIME DEFAULT NULL COMMENT ''OA 状态同步时间'' AFTER approval_status',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pdm_chg_order' AND COLUMN_NAME = 'approval_source');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE pdm_chg_order ADD COLUMN approval_source VARCHAR(16) DEFAULT NULL COMMENT ''审批来源：OA/MANUAL'' AFTER approval_sync_time',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 变更通知（ECN）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pdm_chg_notice (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       NOT NULL COMMENT '租户ID',
    notice_no       VARCHAR(32)  NOT NULL COMMENT '通知编号（租户内唯一）',
    eco_id          BIGINT       NOT NULL COMMENT '来源变更单ID',
    title           VARCHAR(200) NOT NULL COMMENT '通知标题',
    content         TEXT         DEFAULT NULL COMMENT '通知内容',
    impact_json     LONGTEXT     DEFAULT NULL COMMENT '影响对象快照 JSON（复制自 ECO）',
    notify_targets  VARCHAR(1000) DEFAULT NULL COMMENT '接收人 JSON（[{userId}]）',
    notify_channels VARCHAR(64)  NOT NULL DEFAULT 'INBOX' COMMENT '通知渠道，逗号分隔',
    effective_from  DATETIME     NOT NULL COMMENT '生效日期（不早于 ECO 审批通过日）',
    status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/NOTIFIED/EFFECTIVE/CLOSED/CANCELLED',
    notified_time   DATETIME     DEFAULT NULL COMMENT '通知时间',
    effective_time  DATETIME     DEFAULT NULL COMMENT '实际生效时间',
    close_time      DATETIME     DEFAULT NULL COMMENT '关闭时间',
    create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_time     DATETIME     DEFAULT NULL COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_chg_notice_no (tenant_id, notice_no),
    UNIQUE KEY uk_chg_notice_eco (tenant_id, eco_id),
    KEY idx_chg_notice_due (tenant_id, status, effective_from)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM 变更通知（ECN）';

-- ---------------------------------------------------------------------
-- 3. 变更追溯（只追加）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pdm_chg_trace (
    id           BIGINT       NOT NULL COMMENT '主键',
    tenant_id    BIGINT       NOT NULL COMMENT '租户ID',
    biz_type     VARCHAR(8)   NOT NULL COMMENT '业务类型：ECR/ECO/ECN',
    biz_id       BIGINT       NOT NULL COMMENT '业务ID',
    biz_no       VARCHAR(32)  DEFAULT NULL COMMENT '业务编号',
    object_type  VARCHAR(16)  DEFAULT NULL COMMENT '对象类型：DOCUMENT/BOM/PART',
    object_id    BIGINT       DEFAULT NULL COMMENT '对象ID',
    object_code  VARCHAR(64)  DEFAULT NULL COMMENT '对象编码',
    action       VARCHAR(32)  NOT NULL COMMENT '动作：CREATE/SUBMIT/APPROVE/REJECT/EXECUTE/EFFECTIVE/CLOSE 等',
    from_version VARCHAR(32)  DEFAULT NULL COMMENT '变更前版本',
    to_version   VARCHAR(32)  DEFAULT NULL COMMENT '变更后版本',
    content      VARCHAR(1000) DEFAULT NULL COMMENT '变更内容摘要',
    operator_id  BIGINT       DEFAULT NULL COMMENT '操作人',
    trace_time   DATETIME     NOT NULL COMMENT '追溯时间',
    create_time  DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_time  DATETIME     DEFAULT NULL COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_chg_trace_object (tenant_id, object_type, object_id, trace_time),
    KEY idx_chg_trace_biz (tenant_id, biz_type, biz_id, trace_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM 变更追溯（只追加）';

-- ---------------------------------------------------------------------
-- 4. 变更通知投递日志（失败可重试）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pdm_chg_notify_log (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       NOT NULL COMMENT '租户ID',
    notice_id       BIGINT       NOT NULL COMMENT '变更通知ID',
    event_type      VARCHAR(32)  NOT NULL COMMENT '事件类型：ECN_NOTIFIED/ECN_EFFECTIVE/ECN_ALERT',
    recipients      VARCHAR(1000) DEFAULT NULL COMMENT '接收人 JSON',
    title           VARCHAR(200) DEFAULT NULL COMMENT '标题',
    content         VARCHAR(1000) DEFAULT NULL COMMENT '正文',
    status          VARCHAR(16)  NOT NULL DEFAULT 'FAILED' COMMENT '状态：SUCCESS/FAILED',
    retry_count     INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    last_error      VARCHAR(500) DEFAULT NULL COMMENT '最后失败原因',
    idempotency_key VARCHAR(64)  NOT NULL COMMENT '幂等键',
    create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_time     DATETIME     DEFAULT NULL COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_chg_notify_idem (tenant_id, idempotency_key),
    KEY idx_chg_notify_notice (tenant_id, notice_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM 变更通知投递日志';

-- ---------------------------------------------------------------------
-- 5. 定时任务单实例锁
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pdm_chg_job_lock (
    lock_key     VARCHAR(64) NOT NULL COMMENT '锁键',
    owner        VARCHAR(128) DEFAULT NULL COMMENT '持有者',
    locked_until DATETIME    DEFAULT NULL COMMENT '锁到期时间',
    create_time  DATETIME    DEFAULT NULL COMMENT '创建时间',
    update_time  DATETIME    DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (lock_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM 变更任务锁';

-- =====================================================================
-- 完成。校验建议：
--   SHOW TABLES LIKE 'pdm_chg_%';
--   -- 期望：notice / trace / notify_log / job_lock 存在。
--   SHOW COLUMNS FROM pdm_chg_order LIKE 'approval%';
--   -- 期望：approval_instance_id / approval_status / approval_sync_time / approval_source。
-- =====================================================================
