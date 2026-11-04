-- =====================================================================
-- Wave 5.80 M0-B：PDM 图文档 ACL 主体标识扩展
-- =====================================================================
-- 说明：网关只下发角色 code（X-User-Roles），不下发角色 ID，
--       因此 ACL 的 ROLE 主体必须按角色码存储。
--       subject_id 由 BIGINT 扩展为 VARCHAR(64)：
--         ALL  -> '0'
--         USER -> 用户 ID 十进制文本
--         ROLE -> 角色 code（如 pdm_member）
-- 幂等：仅当当前类型仍为 bigint 时执行 MODIFY，可重复执行。
-- 前置：bluewhale_pdm 已执行 V2__document_center.sql。
-- =====================================================================

SET @subject_type := (
    SELECT DATA_TYPE
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'pdm_doc_acl'
      AND COLUMN_NAME = 'subject_id'
);

SET @ddl := IF(@subject_type = 'bigint',
    'ALTER TABLE pdm_doc_acl MODIFY COLUMN subject_id VARCHAR(64) NOT NULL DEFAULT ''0'' COMMENT ''主体标识：USER 存用户ID，ROLE 存角色码，ALL 为 0''',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 完成。校验建议：
--   SHOW COLUMNS FROM pdm_doc_acl LIKE 'subject_id';
--   -- 期望：Type = varchar(64)
-- =====================================================================
