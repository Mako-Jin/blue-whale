-- =====================================================================
-- Wave 5.75 M5：音乐平台安全硬化增量迁移（幂等）
-- =====================================================================
-- 适用库   : bluewhale_music
-- 执行方式 : mysql -u<user> -p bluewhale_music < migration-5.75-m5.sql
-- 幂等说明 : 先查 information_schema 再决定是否 ALTER，可重复执行；
--            仅新增列，不修改/删除既有数据（历史歌曲 copyright_source 为 NULL）。
-- 背景     : M5-3 版权合规要求上传来源必填并可追溯；旧数据保留 NULL，
--            新上传由服务侧（UserSongAppServiceImpl）强制声明来源。
--            M5-2 上传白名单与大小上限为服务侧配置（whale.music.audio-formats /
--            cover-formats / upload-max-bytes），不新增表结构。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. mu_song 新增作品来源列
-- ---------------------------------------------------------------------
SET @has_source_column = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mu_song'
      AND COLUMN_NAME = 'copyright_source'
);
SET @ddl = IF(@has_source_column = 0,
    'ALTER TABLE mu_song ADD COLUMN copyright_source VARCHAR(16) DEFAULT NULL COMMENT ''作品来源：ORIGINAL 原创 / AUTHORIZED 已授权 / ROYALTY_FREE 无版权争议素材（M5-3 上传必填）'' AFTER copyright_status',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
