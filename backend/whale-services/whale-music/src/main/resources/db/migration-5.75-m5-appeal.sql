-- =====================================================================
-- Wave 5.75 M5 待做补齐：音乐版权下架申诉表与榜单聚合索引（幂等）
-- =====================================================================
-- 适用库   : bluewhale_music
-- 执行方式 : mysql -u<user> -p bluewhale_music < migration-5.75-m5-appeal.sql
-- 幂等说明 : 建表使用 IF NOT EXISTS；索引先查 information_schema.STATISTICS 再决定
--            是否 ALTER，可重复执行；不修改/删除既有数据。
-- 背景     : M5-3 申诉接口落地需要持久化申诉单（mu_song_appeal）；
--            近 7 天榜单按 mu_play_history 的 played_at 聚合，需要
--            (tenant_id, played_at, song_id) 组合索引避免全表扫描。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 版权下架申诉表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mu_song_appeal (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    song_id          BIGINT        NOT NULL                COMMENT '歌曲ID',
    applicant_id     BIGINT        NOT NULL                COMMENT '申诉人用户ID（作品上传者）',
    reason           VARCHAR(500)  NOT NULL                COMMENT '申诉理由（纯文本，≤500 字）',
    status           VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING / APPROVED / REJECTED',
    review_opinion   VARCHAR(500)  DEFAULT NULL            COMMENT '审核意见',
    reviewer_id      BIGINT        DEFAULT NULL            COMMENT '复核人用户ID',
    reviewed_at      DATETIME      DEFAULT NULL            COMMENT '复核时间',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_mu_appeal_song (tenant_id, song_id, status),
    KEY idx_mu_appeal_audit (tenant_id, status, create_time),
    KEY idx_mu_appeal_applicant (tenant_id, applicant_id, create_time)
) COMMENT='音乐版权下架申诉表';

-- ---------------------------------------------------------------------
-- 2. 播放历史榜单聚合索引（近 7 天热榜）
-- ---------------------------------------------------------------------
SET @has_played_index = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mu_play_history'
      AND INDEX_NAME = 'idx_mu_history_played'
);
SET @ddl = IF(@has_played_index = 0,
    'ALTER TABLE mu_play_history ADD INDEX idx_mu_history_played (tenant_id, played_at, song_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
