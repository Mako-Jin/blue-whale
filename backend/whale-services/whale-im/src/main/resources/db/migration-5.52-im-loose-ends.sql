-- =====================================================================
-- BlueWhale WebIM 既有库增量迁移（wave 5.52 收口）
-- =====================================================================
-- 适用场景：已按旧版 db/init.sql 建过 bluewhale_im 的库。
-- 幂等说明：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，执行前请先用
--           information_schema 确认列/索引是否已存在，避免重复执行报错。
-- =====================================================================

-- 1) 会话幂等键：单聊（用户对）与机器人会话（用户）唯一，群聊为 NULL。
--    唯一索引允许多个 NULL，不影响既有群聊。
ALTER TABLE im_conversation
    ADD COLUMN pair_key VARCHAR(64) DEFAULT NULL
        COMMENT '幂等键：SINGLE=min:max 用户对；BOT=B:用户ID；GROUP 为空' AFTER owner_id,
    ADD UNIQUE KEY uk_pair_key (pair_key);

-- 2) 回填既有单聊：同一对用户保留最早一条，其余历史会话需人工确认后再处理。
UPDATE im_conversation c
JOIN (
    SELECT m1.conversation_id,
           CONCAT('S:', LEAST(m1.user_id, m2.user_id), ':', GREATEST(m1.user_id, m2.user_id)) AS pair_key
    FROM im_conversation_member m1
    JOIN im_conversation_member m2
      ON m1.conversation_id = m2.conversation_id AND m1.user_id < m2.user_id
    JOIN im_conversation c2 ON c2.id = m1.conversation_id AND c2.type = 'SINGLE' AND c2.deleted = 0
    JOIN (
        SELECT before.conversation_id, MIN(before.id) AS keep_id
        FROM im_conversation_member before
        GROUP BY before.conversation_id
        HAVING COUNT(*) = 2
    ) k ON k.conversation_id = m1.conversation_id AND k.keep_id = m1.id
) p ON p.conversation_id = c.id
SET c.pair_key = p.pair_key
WHERE c.pair_key IS NULL;

-- 3) 回填既有机器人会话：每个用户保留最早一条（owner_id 即用户ID）。
UPDATE im_conversation c
JOIN (
    SELECT c2.owner_id, MIN(c2.id) AS keep_id
    FROM im_conversation c2
    WHERE c2.type = 'BOT' AND c2.deleted = 0
    GROUP BY c2.owner_id
) b ON b.keep_id = c.id
SET c.pair_key = CONCAT('B:', c.owner_id)
WHERE c.type = 'BOT' AND c.pair_key IS NULL;

-- 4) 机器人回答评价（👍/👎）持久化。
ALTER TABLE im_agent_run
    ADD COLUMN feedback VARCHAR(8) DEFAULT NULL
        COMMENT '机器人回答评价：UP 有用 / DOWN 无用 / NULL 未评价' AFTER confirmation_json;
