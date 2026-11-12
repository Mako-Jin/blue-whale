-- =====================================================================
-- BlueWhale 员工论坛（whale-forum） Flyway V8：@提及记录（M5 / F-03-10）
-- =====================================================================
-- 适用库   : bluewhale_forum（MySQL 8.0+，utf8mb4）
-- 说明     : 1) 提及记录独立成表而不是只写 outbox：outbox 是投递队列，
--               投递成功后内容会被清理/重试逻辑改写，回答不了「谁在哪层 @过我」，
--               也就没有后续的「@我的」列表。
--            2) post_id 用 0 表示主帖（1 楼由 fm_thread 代表，不落 fm_post）。
--               不写 NULL：MySQL 唯一键对 NULL 不去重，同一帖同一人被 @ 两次
--               会落两条，正好踩中「判重键含 NULL 等于没判重」这个静默失效模式。
--            3) 唯一键 (tenant_id, thread_id, post_id, user_id) 不含主键——主键是雪花 ID，
--               含主键的唯一键永远不冲突，等于没建。
--               thread_id 必须在键里：主帖的 post_id 恒为 0，不带帖子维度的话
--               「不同帖子 @ 同一个人」会互相当成重复，第二帖起静默不落库。
--            4) append-only：不设 update_time / deleted（规范 §2.2 例外）。
-- =====================================================================

CREATE TABLE IF NOT EXISTS fm_mention (
    id            BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT       NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    thread_id     BIGINT       NOT NULL                COMMENT '帖子ID',
    post_id       BIGINT       NOT NULL DEFAULT 0      COMMENT '楼层ID（0 表示主帖/1 楼）',
    user_id       BIGINT       NOT NULL                COMMENT '被提及用户ID',
    mentioned_by  BIGINT       NOT NULL                COMMENT '发起提及的用户ID',
    source        VARCHAR(16)  NOT NULL DEFAULT 'POST' COMMENT '来源：THREAD 发帖 / POST 回帖',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fm_mention_target (tenant_id, thread_id, post_id, user_id),
    KEY idx_fm_mention_user (tenant_id, user_id, create_time),
    KEY idx_fm_mention_thread (tenant_id, thread_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='论坛 @提及记录（append-only）';
