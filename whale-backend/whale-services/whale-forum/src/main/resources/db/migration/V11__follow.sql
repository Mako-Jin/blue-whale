-- =====================================================================
-- 鲸议台 V11：结契（关注作者 / 版块，M7-3 / F-05-03）
-- =====================================================================
-- 说明 : 1) 关系表不设 deleted / update_time：取关就是物理删行。
--          软删会让唯一键挡住「取关后再关注」，还得额外写恢复分支，
--          而关注本身没有审计价值。
--        2) target_type 只允许 USER / BOARD，用 CHECK 兜住：
--          「关注帖子」这类需求应由藏帖承担，混进来后通知派发会变成全表扫描。
--        3) 反查关注者（发新帖通知）走 idx_fm_follow_target，
--          正查关注列表走 idx_fm_follow_user。
-- =====================================================================

CREATE TABLE IF NOT EXISTS fm_follow (
    id           BIGINT       NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT       NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    user_id      BIGINT       NOT NULL               COMMENT '关注人用户ID',
    target_type  VARCHAR(8)   NOT NULL               COMMENT '关注对象类型：USER 作者 / BOARD 版块',
    target_id    BIGINT       NOT NULL               COMMENT '关注对象ID（用户ID 或 版块ID）',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fm_follow (tenant_id, user_id, target_type, target_id),
    KEY idx_fm_follow_target (tenant_id, target_type, target_id),
    KEY idx_fm_follow_user (tenant_id, user_id, create_time),
    CONSTRAINT ck_fm_follow_target CHECK (target_type IN ('USER', 'BOARD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='论坛关注关系（结契：关注作者 / 版块）';
