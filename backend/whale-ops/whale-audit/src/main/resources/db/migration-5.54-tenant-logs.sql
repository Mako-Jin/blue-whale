-- =====================================================================
-- Wave 5.54 Phase 2：日志表租户字段迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_audit / bluewhale_auth（两库日志表同构，需分别执行）
-- 前置条件 : 无
-- 执行方式 : mysql -u<user> -p <database> < migration-5.54-tenant-logs.sql
-- 幂等说明 : 列/索引均先检测再变更，可重复执行；不删除任何业务数据。
-- 变更内容 :
--   1. api_log / outbound_log / frontend_log / behavior_log 增加 tenant_id；
--   2. 新增 (tenant_id, create_time) 复合索引，支撑按租户检索与导出。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. 幂等工具：列 / 索引存在性检测
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing;

DELIMITER $$

CREATE PROCEDURE add_column_if_missing(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_ddl VARCHAR(512))
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = p_table AND column_name = p_column) = 0 THEN
        SET @ddl = p_ddl;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE add_index_if_missing(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_ddl VARCHAR(512))
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = p_table AND index_name = p_index) = 0 THEN
        SET @ddl = p_ddl;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- 1. 日志表增加 tenant_id 与租户时间索引
-- ---------------------------------------------------------------------
CALL add_column_if_missing('api_log', 'tenant_id',
    'ALTER TABLE api_log ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT ''租户ID（网关可信注入）'' AFTER username');
CALL add_index_if_missing('api_log', 'idx_tenant_create',
    'ALTER TABLE api_log ADD INDEX idx_tenant_create (tenant_id, create_time)');

CALL add_column_if_missing('outbound_log', 'tenant_id',
    'ALTER TABLE outbound_log ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT ''租户ID（请求上下文）'' AFTER username');
CALL add_index_if_missing('outbound_log', 'idx_tenant_create',
    'ALTER TABLE outbound_log ADD INDEX idx_tenant_create (tenant_id, create_time)');

CALL add_column_if_missing('frontend_log', 'tenant_id',
    'ALTER TABLE frontend_log ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT ''租户ID（网关可信注入）'' AFTER user_id');
CALL add_index_if_missing('frontend_log', 'idx_tenant_create',
    'ALTER TABLE frontend_log ADD INDEX idx_tenant_create (tenant_id, create_time)');

CALL add_column_if_missing('behavior_log', 'tenant_id',
    'ALTER TABLE behavior_log ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT ''租户ID（网关可信注入）'' AFTER user_id');
CALL add_index_if_missing('behavior_log', 'idx_tenant_create',
    'ALTER TABLE behavior_log ADD INDEX idx_tenant_create (tenant_id, create_time)');

-- ---------------------------------------------------------------------
-- 2. 清理幂等工具
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing;
