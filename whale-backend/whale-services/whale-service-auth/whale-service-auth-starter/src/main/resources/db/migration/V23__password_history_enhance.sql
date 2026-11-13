-- =============================================================================
-- 文件        : V23__password_history_enhance.sql
-- 说明        : 权限中心扩展 M3 —— 口令历史增强
--
-- 背景：按 auth_tbl_password_history 处置矩阵（计划 §1.2 M0 行，本里程碑统一落地）
--       为 sys_password_history 补 is_active / expires_at / change_source 三列。
--
-- 口径：
--   ① is_active：该历史记录对应的口令是否为当前生效口令；
--     新写入记录默认 0（换口令时被替换下来的口令即失效）；
--     支持管理员「回滚口令」语义的预留位。
--   ② expires_at：口令到期时间（租户策略 90 天口径的落库快照，便于离线判定）。
--   ③ change_source：口令变更来源 —— LEGACY 存量 / USER_CHANGE 用户自改 /
--     ADMIN_RESET 管理员重置 / SOCIAL_BIND 三方首次绑定设密 / EXPIRE_EXPIRED 到期强制改。
--   ④ 存量回填：全部历史记录 change_source=LEGACY、is_active=0（无法回溯来源），
--     expires_at 保持 NULL（不伪造到期时间）。
--
-- 本脚本（幂等，可安全重放，information_schema 判存写法与 V18 一致）。
-- =============================================================================

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_password_history' AND COLUMN_NAME = 'is_active');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_password_history ADD COLUMN is_active TINYINT NOT NULL DEFAULT 0 COMMENT ''是否当前生效口令：0 否（历史口令）/ 1 是'' AFTER password',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_password_history' AND COLUMN_NAME = 'expires_at');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_password_history ADD COLUMN expires_at DATETIME DEFAULT NULL COMMENT ''口令到期时间（租户策略到期口径快照，可空）'' AFTER is_active',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_password_history' AND COLUMN_NAME = 'change_source');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_password_history ADD COLUMN change_source VARCHAR(16) NOT NULL DEFAULT ''LEGACY'' COMMENT ''变更来源：LEGACY 存量 / USER_CHANGE 用户自改 / ADMIN_RESET 管理员重置 / SOCIAL_BIND 三方绑定设密'' AFTER expires_at',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 存量回填（幂等：仅当存在 LEGACY 之外的非法值或 NULL 语义行时补齐；
-- 新列带 DEFAULT，NULL 不会出现，这里只做一次显式对齐以便重放安全）
-- ---------------------------------------------------------------------
UPDATE sys_password_history SET change_source = 'LEGACY'
WHERE change_source IS NULL OR change_source = '';
