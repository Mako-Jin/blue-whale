-- V3: 审计归档水位补「批次哈希 + 链式哈希 + 归档清单」列（等保 2.0 8.1.4.3 b/c）
--
-- 背景：归档只导出不删除，原水位表仅记录 last_create_time/last_id，能回答「归档到哪」，
-- 但回答不了「归档出去的东西有没有被改过」。本轮为每批归档补：
--   last_batch_sha256  —— 本批 CSV 内容的 SHA-256（对单个文件可自证完整性）
--   chain_sha256       —— 链式哈希 sha256(上一批 chain || 本批 sha256)（跨批可自证顺序与连续性）
--   last_object_key    —— 本批 CSV 在对象存储的 objectKey（便于定位原件）
--   last_manifest_key  —— 本批 .sha256 清单的 objectKey（清单内含哈希、区间、行数）
--   last_batch_rows    —— 本批行数
--   archived_total     —— 累计归档行数（对账用）
--
-- 幂等：以 chain_sha256 是否存在作为「整组列是否已加」的判据，新环境 V1+V3 顺序执行、
--      存量库单跑本脚本、手工 db/init.sql 建库后再跑本脚本，三种路径都不会报重复列。

SET @ddl := (SELECT IF(
        (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'audit_archive_watermark'
           AND column_name = 'chain_sha256') = 0,
    'ALTER TABLE audit_archive_watermark
        ADD COLUMN last_batch_sha256 VARCHAR(64) DEFAULT NULL COMMENT ''本批归档 CSV 的 SHA-256 十六进制小写'',
        ADD COLUMN chain_sha256      VARCHAR(64) DEFAULT NULL COMMENT ''链式哈希 sha256(上一批 chain || 本批 sha256)，首批以空串参与计算'',
        ADD COLUMN last_object_key   VARCHAR(255) DEFAULT NULL COMMENT ''本批归档 CSV 的对象存储键'',
        ADD COLUMN last_manifest_key VARCHAR(255) DEFAULT NULL COMMENT ''本批 .sha256 校验清单的对象存储键'',
        ADD COLUMN last_batch_rows   INT DEFAULT NULL COMMENT ''本批归档行数'',
        ADD COLUMN archived_total    BIGINT NOT NULL DEFAULT 0 COMMENT ''累计归档行数''',
    'SELECT 1'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
