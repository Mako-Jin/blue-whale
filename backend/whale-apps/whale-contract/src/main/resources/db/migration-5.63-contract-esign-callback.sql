-- =====================================================================
-- Wave 5.61 M3：第三方电子签章回调（whale-contract）增量迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_contract
-- 执行方式 : mysql -u<user> -p bluewhale_contract < migration-5.63-contract-esign-callback.sql
-- 说明     : 新增回调幂等表；为签署任务补充三方任务与工作流任务关联列；
--            CREATE TABLE IF NOT EXISTS + information_schema 守卫 ALTER，可重复执行。
-- =====================================================================

CREATE TABLE IF NOT EXISTS ct_esign_callback (
    id                BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    provider          VARCHAR(16)  NOT NULL            COMMENT '签署渠道：LOCAL/ESIGNBAO/FADADA/SSQ',
    provider_event_id VARCHAR(128) NOT NULL            COMMENT '三方事件ID（幂等键）',
    contract_id       BIGINT       DEFAULT NULL        COMMENT '合同ID',
    sign_task_id      BIGINT       DEFAULT NULL        COMMENT '签署任务ID',
    payload_hash      VARCHAR(64)  NOT NULL            COMMENT '回调原文 SHA-256',
    payload           TEXT         NULL                COMMENT '回调原文（JSON）',
    status            VARCHAR(16)  NOT NULL DEFAULT 'RECEIVED'
                                                       COMMENT '状态：RECEIVED 已接收 / PROCESSED 已处理 / FAILED 处理失败',
    retry_count       INT          NOT NULL DEFAULT 0  COMMENT '处理失败重试次数',
    error_msg         VARCHAR(512) DEFAULT NULL        COMMENT '失败原因',
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_provider_event (tenant_id, provider, provider_event_id),
    KEY idx_contract (contract_id),
    KEY idx_sign_task (sign_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='电子签章回调幂等表';

-- ct_sign_task.workflow_task_id：三方回调完成后需推进的工作流任务
SET @has_workflow_task := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ct_sign_task' AND COLUMN_NAME = 'workflow_task_id'
);
SET @ddl := IF(@has_workflow_task = 0,
    'ALTER TABLE ct_sign_task ADD COLUMN workflow_task_id BIGINT DEFAULT NULL COMMENT ''关联工作流任务ID（三方回调后由内部完成接口推进）'' AFTER provider_task_id',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ct_sign_task.sign_url：三方签署链接（发起时返回，便于重复查看）
SET @has_sign_url := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ct_sign_task' AND COLUMN_NAME = 'sign_url'
);
SET @ddl := IF(@has_sign_url = 0,
    'ALTER TABLE ct_sign_task ADD COLUMN sign_url VARCHAR(512) DEFAULT NULL COMMENT ''三方签署链接'' AFTER workflow_task_id',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
