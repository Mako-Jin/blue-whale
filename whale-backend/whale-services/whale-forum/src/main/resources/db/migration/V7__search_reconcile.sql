-- =====================================================================
-- BlueWhale 员工论坛（whale-forum） Flyway V7：检索与计数对账（M4）
-- =====================================================================
-- 适用库   : bluewhale_forum（MySQL 8.0+，utf8mb4）
-- 说明     : 1) 检索口径对齐 whale-doc：正文另存纯文本列 content_text，全文索引建在
--               「标题 + 纯文本」上，而不是直接建在富文本源码 content 上。
--               否则搜 "div" / "href" / "p" 会命中全部带标签的帖子，命中数虚高且
--               摘要高亮到标签里；纯文本列也让「重建索引」有了可幂等的产物。
--            2) ngram 解析器由 MySQL 内置提供，ngram_token_size 默认 2：
--               关键词长度 < 2 时索引查不到（单字），应用层回退 LIKE，不是缺陷。
--            3) 对账轨迹表 append-only：不设 update_time / deleted（规范 §2.2 例外），
--               每次核对都留一行，便于回答「这个计数是什么时候被谁改回来的」。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 检索纯文本列
-- ---------------------------------------------------------------------
ALTER TABLE fm_thread
    ADD COLUMN content_text MEDIUMTEXT NULL
        COMMENT '正文纯文本（检索用，写入时由消毒后的 HTML 提取，上限见 search-text-length）'
        AFTER content;

-- 全文索引：标题 + 纯文本正文
ALTER TABLE fm_thread
    ADD FULLTEXT KEY ft_fm_thread_search (title, content_text) WITH PARSER ngram;

-- ---------------------------------------------------------------------
-- 2. 计数对账轨迹（append-only）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fm_count_reconcile (
    id             BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT       NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    thread_id      BIGINT       NOT NULL                COMMENT '帖子ID',
    metric         VARCHAR(16)  NOT NULL                COMMENT '计数项：REPLY / LIKE / COLLECT',
    value_before   INT          NOT NULL DEFAULT 0      COMMENT '核对前展示值',
    value_actual   INT          NOT NULL DEFAULT 0      COMMENT '按明细表 COUNT 得到的真实值',
    fixed          TINYINT      NOT NULL DEFAULT 0      COMMENT '是否已按真实值修正：1 是 / 0 只报告未修',
    operator_id    BIGINT       DEFAULT NULL            COMMENT '触发人（手工核对为操作者ID，定时任务为 NULL）',
    batch_no       VARCHAR(64)  DEFAULT NULL            COMMENT '批次号（一次核对一个批次，便于按次追溯）',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_fm_reconcile_batch (batch_no),
    KEY idx_fm_reconcile_thread (tenant_id, thread_id, metric),
    KEY idx_fm_reconcile_time (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
    COMMENT='帖子计数对账轨迹（append-only，差异与修正各留一行）';
