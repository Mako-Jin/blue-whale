-- =====================================================================
-- 鲸议台 V6：互动计数的两块地基（浏览去重 + 热榜快照）
--
--   1) fm_thread_view：浏览去重。同人同帖滚动 24h 只 +1。
--      去重放库不放 Redis：论坛浏览是低频写，且详情接口不该因缓存不可用而报错；
--      一行 = 一个 (租户, 帖子, 用户) 组合，行数有上界，历史行可按 last_view_time 清理。
--   2) fm_hot_snapshot：热榜快照。租户内每 30 分钟整表覆盖，portal/首页只读快照，
--      不在列表页现算——现算会把 7 天窗口的聚合压到每次浏览上。
--
-- 两张表都不挂 deleted：快照是派生数据（刷新即整表物理覆盖），
-- 浏览去重行则靠唯一键覆盖写，软删只会让唯一键失效。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 浏览去重表（同人同帖 24h 只计一次）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fm_thread_view (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    thread_id        BIGINT        NOT NULL               COMMENT '帖子ID',
    user_id          BIGINT        NOT NULL               COMMENT '浏览人用户ID',
    last_view_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近一次计入浏览的时间（滚动 24h 窗口基准）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fm_thread_view (tenant_id, thread_id, user_id),
    KEY idx_fm_thread_view_time (last_view_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='论坛浏览去重表（同人同帖滚动24小时只计一次）';

-- ---------------------------------------------------------------------
-- 热榜快照（租户内每 30 分钟整体覆盖，只读）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fm_hot_snapshot (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    rank_no          INT           NOT NULL               COMMENT '名次（1 起，同租户内唯一）',
    thread_id        BIGINT        NOT NULL               COMMENT '帖子ID',
    board_id         BIGINT        DEFAULT NULL           COMMENT '版块ID',
    title            VARCHAR(120)  NOT NULL               COMMENT '标题（快照时点）',
    summary          VARCHAR(200)  DEFAULT NULL           COMMENT '摘要（快照时点）',
    author_id        BIGINT        DEFAULT NULL           COMMENT '作者用户ID',
    reply_count      INT           NOT NULL DEFAULT 0     COMMENT '回复数（快照时点）',
    like_count       INT           NOT NULL DEFAULT 0     COMMENT '投花数（快照时点）',
    view_count       INT           NOT NULL DEFAULT 0     COMMENT '浏览数（快照时点）',
    score            DECIMAL(12,2) NOT NULL DEFAULT 0.00  COMMENT '热度分（回复/投花/浏览加权 − 处置扣分）',
    snapshot_time    DATETIME      NOT NULL               COMMENT '快照生成时间',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fm_hot_snapshot_rank (tenant_id, rank_no),
    KEY idx_fm_hot_snapshot_thread (tenant_id, thread_id),
    KEY idx_fm_hot_snapshot_time (tenant_id, snapshot_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='论坛热榜快照（租户内整体覆盖，只读）';
