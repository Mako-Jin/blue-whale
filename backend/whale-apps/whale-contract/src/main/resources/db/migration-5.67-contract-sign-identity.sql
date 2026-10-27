-- =====================================================================
-- P1 S-04e：签署实名与意愿认证证据字段（whale-contract）增量迁移脚本（幂等）
-- =====================================================================
-- 适用库   : bluewhale_contract
-- 执行方式 : mysql -u<user> -p bluewhale_contract < migration-5.67-contract-sign-identity.sql
-- 说明     : 为 ct_sign_record 增加实名/意愿认证结果与证据编号，供电子签合规存证；
--            本地签署记录 LOGIN_SESSION + SIGN_CONFIRM，三方回调按供应商回执覆盖。
--            全部 ALTER 均以 information_schema 守卫，可重复执行。
-- =====================================================================

-- ct_sign_record.real_name_status：实名认证状态
SET @has_col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ct_sign_record' AND COLUMN_NAME = 'real_name_status'
);
SET @ddl := IF(@has_col = 0,
    'ALTER TABLE ct_sign_record ADD COLUMN real_name_status VARCHAR(16) NOT NULL DEFAULT ''UNVERIFIED'' COMMENT ''实名认证状态：VERIFIED/UNVERIFIED/PENDING'' AFTER client_ip',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ct_sign_record.real_name_method：实名认证方式
SET @has_col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ct_sign_record' AND COLUMN_NAME = 'real_name_method'
);
SET @ddl := IF(@has_col = 0,
    'ALTER TABLE ct_sign_record ADD COLUMN real_name_method VARCHAR(32) DEFAULT NULL COMMENT ''实名认证方式：LOGIN_SESSION/ID_CARD_OCR/FACE/VIDEO/SMS'' AFTER real_name_status',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ct_sign_record.real_name_evidence_no：实名认证证据编号
SET @has_col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ct_sign_record' AND COLUMN_NAME = 'real_name_evidence_no'
);
SET @ddl := IF(@has_col = 0,
    'ALTER TABLE ct_sign_record ADD COLUMN real_name_evidence_no VARCHAR(64) DEFAULT NULL COMMENT ''实名认证证据编号（供应商回执/本地会话凭据）'' AFTER real_name_method',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ct_sign_record.willingness_method：意愿认证方式
SET @has_col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ct_sign_record' AND COLUMN_NAME = 'willingness_method'
);
SET @ddl := IF(@has_col = 0,
    'ALTER TABLE ct_sign_record ADD COLUMN willingness_method VARCHAR(32) DEFAULT NULL COMMENT ''意愿认证方式：SIGN_CONFIRM/SMS/FACE/VIDEO'' AFTER real_name_evidence_no',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ct_sign_record.willingness_evidence_no：意愿认证证据编号
SET @has_col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ct_sign_record' AND COLUMN_NAME = 'willingness_evidence_no'
);
SET @ddl := IF(@has_col = 0,
    'ALTER TABLE ct_sign_record ADD COLUMN willingness_evidence_no VARCHAR(64) DEFAULT NULL COMMENT ''意愿认证证据编号'' AFTER willingness_method',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ct_sign_record.willingness_at：意愿确认时间
SET @has_col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ct_sign_record' AND COLUMN_NAME = 'willingness_at'
);
SET @ddl := IF(@has_col = 0,
    'ALTER TABLE ct_sign_record ADD COLUMN willingness_at DATETIME DEFAULT NULL COMMENT ''意愿确认时间'' AFTER willingness_evidence_no',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 存量记录补齐默认值，避免历史行 real_name_status 为空
UPDATE ct_sign_record
SET real_name_status = 'UNVERIFIED'
WHERE real_name_status IS NULL OR real_name_status = '';
