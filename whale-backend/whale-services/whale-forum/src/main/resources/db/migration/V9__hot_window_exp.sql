-- =====================================================================
-- 鲸议台 V9：热榜双档 + 论坛经验账本（P1 批次）
--
--   1) fm_hot_snapshot 加 hot_window：24h / 7d 两档并存。
--      唯一键从 (tenant_id, rank_no) 改为 (tenant_id, hot_window, rank_no)——
--      不改的话第二档写入直接撞键，且旧键挡不住「两档同名次」。
--   2) fm_exp_ledger：论坛经验流水（append-only）。可兑换积分归 whale-mall，
--      论坛经验不出域，本服务自记账本。
--      防刷关键：唯一键 (tenant_id, user_id, event, ref_thread_id)——
--      同帖只计一次（取消投花再投不会重复 +2，金榜同帖不会反复 +1000）。
--      匿名帖不计经验是应用层判断（发布前查 anonymous），不靠表约束。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 热榜快照：加窗口列，唯一键加窗口维度
-- ---------------------------------------------------------------------
ALTER TABLE fm_hot_snapshot
    ADD COLUMN hot_window VARCHAR(8) NOT NULL DEFAULT '7d'
        COMMENT '热榜窗口：24h / 7d（打分窗口不同，两档并存）' AFTER tenant_id;

ALTER TABLE fm_hot_snapshot DROP INDEX uk_fm_hot_snapshot_rank;
ALTER TABLE fm_hot_snapshot
    ADD UNIQUE KEY uk_fm_hot_snapshot_rank (tenant_id, hot_window, rank_no),
    ADD KEY idx_fm_hot_snapshot_window_time (tenant_id, hot_window, snapshot_time);

-- ---------------------------------------------------------------------
-- 论坛经验流水（append-only，无 update_time/deleted）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fm_exp_ledger (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    user_id        BIGINT        NOT NULL               COMMENT '获得经验的用户ID',
    event          VARCHAR(32)   NOT NULL               COMMENT '事件：THREAD_PUBLISH/LIKE_GIVEN/ESSENCE_AWARD/HOT_TOP1',
    amount         INT           NOT NULL               COMMENT '经验值（正数）',
    ref_thread_id  BIGINT        NOT NULL               COMMENT '关联帖子ID（事件锚点，幂等键组成部分）',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fm_exp_once (tenant_id, user_id, event, ref_thread_id),
    KEY idx_fm_exp_user (tenant_id, user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='论坛经验流水（发帖+5 被投花+2 精华+200 金榜+1000，匿名帖不计）';
