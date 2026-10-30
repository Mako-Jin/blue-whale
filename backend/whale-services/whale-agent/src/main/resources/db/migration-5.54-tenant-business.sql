-- ============================================================================
-- Wave 5.54 Phase 3：Agent 表租户化（幂等脚本）
-- 适用库   : bluewhale_agent
-- 执行方式 : mysql -u<user> -p bluewhale_agent < migration-5.54-tenant-business.sql
-- 说明：会话/消息/评价/操作/文件台账新增 tenant_id（默认 1）；
--       agent_fact 与 agent_index_meta 为平台共享表，保持无租户列（MyBatis 忽略白名单）。
-- ============================================================================

DROP PROCEDURE IF EXISTS whale_add_tenant_column;
DROP PROCEDURE IF EXISTS whale_add_index;
DROP PROCEDURE IF EXISTS whale_drop_index;
DROP PROCEDURE IF EXISTS whale_backfill_log_tenant;

DELIMITER $$

CREATE PROCEDURE whale_add_tenant_column(IN tbl VARCHAR(64))
BEGIN
    IF NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = 'tenant_id') THEN
        SET @ddl = CONCAT('ALTER TABLE ', tbl, ' ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 '
            'COMMENT ''租户ID（行级隔离）'' AFTER id');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE whale_add_index(IN tbl VARCHAR(64), IN idx VARCHAR(64), IN cols VARCHAR(128))
BEGIN
    IF NOT EXISTS(SELECT 1 FROM information_schema.STATISTICS
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND INDEX_NAME = idx) THEN
        SET @ddl = CONCAT('ALTER TABLE ', tbl, ' ADD KEY ', idx, ' (', cols, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE whale_drop_index(IN tbl VARCHAR(64), IN idx VARCHAR(64))
BEGIN
    IF EXISTS(SELECT 1 FROM information_schema.STATISTICS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND INDEX_NAME = idx) THEN
        SET @ddl = CONCAT('ALTER TABLE ', tbl, ' DROP INDEX ', idx, '');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE whale_backfill_log_tenant(IN tbl VARCHAR(64))
BEGIN
    IF EXISTS(SELECT 1 FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = 'tenant_id') THEN
        SET @ddl = CONCAT('UPDATE ', tbl, ' SET tenant_id = 1 WHERE tenant_id IS NULL');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL whale_add_tenant_column('agent_conversation');
CALL whale_add_tenant_column('agent_message');
CALL whale_add_tenant_column('agent_feedback');
CALL whale_add_tenant_column('agent_operation');
CALL whale_add_tenant_column('agent_knowledge_file');

CALL whale_drop_index('agent_conversation', 'idx_user_id');
CALL whale_add_index('agent_conversation', 'idx_tenant_user', 'tenant_id, user_id, update_time');
CALL whale_add_index('agent_message', 'idx_tenant_id', 'tenant_id');
CALL whale_add_index('agent_feedback', 'idx_tenant_id', 'tenant_id');
CALL whale_add_index('agent_operation', 'idx_tenant_id', 'tenant_id');
CALL whale_add_index('agent_knowledge_file', 'idx_tenant_id', 'tenant_id');

CALL whale_backfill_log_tenant('audit_log');
CALL whale_backfill_log_tenant('api_log');
CALL whale_backfill_log_tenant('outbound_log');
CALL whale_backfill_log_tenant('frontend_log');
CALL whale_backfill_log_tenant('behavior_log');

DROP PROCEDURE IF EXISTS whale_backfill_log_tenant;
DROP PROCEDURE IF EXISTS whale_add_tenant_column;
DROP PROCEDURE IF EXISTS whale_add_index;
DROP PROCEDURE IF EXISTS whale_drop_index;
