-- =====================================================================
-- 鲸议台 V10：标签聚合表 + 历史标签回填（M7-1 发现页）
--
--   1) 新增 fm_thread_tag：标签以「行」存储，供 /forum/tag/list 聚合与
--      /forum/thread/page?tag= 精确筛选。fm_thread.tags 逗号串保留为展示快照。
--      逗号串无法 GROUP BY，`LIKE '%tag%'` 还会把「后端」命中「前端后端」。
--   2) 回填用递归 CTE 拆逗号串，INSERT IGNORE 幂等：
--      唯一键 (tenant_id, thread_id, tag) 兜住重复执行；主键用 UUID_SHORT()
--      （≈1e16 量级，与雪花 ID ≈1.9e18 不重叠），避免拆分逻辑里再引入序号计算。
--   3) append-only：无 update_time / deleted，改标签走「删旧行 + 插新行」。
-- =====================================================================

CREATE TABLE IF NOT EXISTS fm_thread_tag (
    id           BIGINT       NOT NULL                  COMMENT '主键ID（应用侧雪花；迁移回填用 UUID_SHORT）',
    tenant_id    BIGINT       NOT NULL DEFAULT 1        COMMENT '租户ID（行级隔离）',
    thread_id    BIGINT       NOT NULL                  COMMENT '帖子ID（fm_thread.id）',
    tag          VARCHAR(32)  NOT NULL                  COMMENT '标签（单帖单标签唯一，长度上限与 whale.forum.max-tag-length 对齐）',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fm_thread_tag (tenant_id, thread_id, tag),
    KEY idx_fm_thread_tag_tag (tenant_id, tag, thread_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='论坛帖子标签表（聚合与精确筛选的数据源）';

-- ---------------------------------------------------------------------
-- 历史标签回填：按逗号逐段拆出，首尾空白剔除，空段丢弃
-- ---------------------------------------------------------------------
INSERT IGNORE INTO fm_thread_tag (id, tenant_id, thread_id, tag, create_time)
WITH RECURSIVE tag_split AS (
    SELECT t.id AS thread_id,
           t.tenant_id AS tenant_id,
           SUBSTRING_INDEX(t.tags, ',', 1) AS tag,
           CASE WHEN LOCATE(',', t.tags) > 0
                THEN SUBSTRING(t.tags, LOCATE(',', t.tags) + 1)
                ELSE '' END AS rest
    FROM fm_thread t
    WHERE t.tags IS NOT NULL AND TRIM(t.tags) <> ''
    UNION ALL
    SELECT s.thread_id,
           s.tenant_id,
           SUBSTRING_INDEX(s.rest, ',', 1),
           CASE WHEN LOCATE(',', s.rest) > 0
                THEN SUBSTRING(s.rest, LOCATE(',', s.rest) + 1)
                ELSE '' END
    FROM tag_split s
    WHERE s.rest <> ''
)
SELECT UUID_SHORT(), s.tenant_id, s.thread_id, TRIM(s.tag), NOW()
FROM tag_split s
WHERE TRIM(s.tag) <> '';
