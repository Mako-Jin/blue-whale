-- =====================================================================
-- BlueWhale 微服务平台 - 表单服务（whale-form） Flyway V5：提交幂等键
-- =====================================================================
-- 背景   : 网关超时重试或用户重复点击会让同一份提交落多条记录。
--          客户端可携带 client_request_id，服务端按（租户, 表单编码, 幂等键）去重。
-- 幂等   : 列与索引均先查 information_schema，已存在则执行 SELECT 1 跳过，可重复执行。
-- 边界   : form_data 含逻辑删除，唯一键不区分 deleted——记录被软删后同一
--          client_request_id 仍被唯一键占用，客户端需重新生成请求ID；
--          服务层回查不到（软删已过滤）时返回明确参数错误而非 500。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 主表：客户端幂等键
-- ---------------------------------------------------------------------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_data'
        AND COLUMN_NAME = 'client_request_id') = 0,
    'ALTER TABLE form_data ADD COLUMN client_request_id VARCHAR(64) DEFAULT NULL COMMENT ''客户端幂等键（可选，最长64）'' AFTER biz_key',
    'SELECT 1');
PREPARE form_v5_stmt FROM @ddl;
EXECUTE form_v5_stmt;
DEALLOCATE PREPARE form_v5_stmt;

-- ---------------------------------------------------------------------
-- 幂等唯一键：（租户, 表单编码, 幂等键）唯一
-- 历史数据该列为 NULL，MySQL 唯一索引允许多个 NULL，不影响存量
-- ---------------------------------------------------------------------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_data'
        AND INDEX_NAME = 'uk_tenant_definition_client_req') = 0,
    'ALTER TABLE form_data ADD UNIQUE KEY uk_tenant_definition_client_req (tenant_id, definition_code, client_request_id)',
    'SELECT 1');
PREPARE form_v5_stmt FROM @ddl;
EXECUTE form_v5_stmt;
DEALLOCATE PREPARE form_v5_stmt;
