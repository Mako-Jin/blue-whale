-- =====================================================================
-- whale-file：藏书殿概览（三维王城 T5.4）聚合查询索引
-- =====================================================================
-- 背景：GET /file/overview 要打 5 次库——库存总量、今日新增、近 7 日按天新增、
--       回收站（含"超期未清理"）、按桶分布。此前 file_info 只有 tenant_id 单列索引
--       与 (tenant_id, bucket)，这些聚合都会退化成"租户内全表扫"，文件多的租户
--       一眼卡住概览的 5 秒超时。
-- 口径：
--   1) (tenant_id, deleted)                —— 库存总量 / 回收站总数（按删除标记切分）
--   2) (tenant_id, create_time)            —— 今日新增 / 近 7 日趋势（范围条件，不套函数）
--   3) (tenant_id, deleted, delete_time)   —— 回收站超期未清理；同时服务 RecyclePurgeTask 的到期清理
--   4) 建好 (tenant_id, deleted) 之后，单列 idx_tenant_id 成为其最左前缀的冗余，
--      一并删除以减少上传链路的索引维护开销（与 V8 删除被唯一索引覆盖的
--      idx_parent_version 同一处理方式）。
-- 幂等：先查 information_schema.STATISTICS，索引已存在时跳过，重复执行不报错。
-- =====================================================================

SET @has_tenant_deleted = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_info' AND INDEX_NAME = 'idx_tenant_deleted');
SET @ddl = IF(@has_tenant_deleted = 0,
    'ALTER TABLE file_info ADD INDEX idx_tenant_deleted (tenant_id, deleted)',
    'SELECT ''file_info.idx_tenant_deleted 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_tenant_create = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_info' AND INDEX_NAME = 'idx_tenant_create_time');
SET @ddl = IF(@has_tenant_create = 0,
    'ALTER TABLE file_info ADD INDEX idx_tenant_create_time (tenant_id, create_time)',
    'SELECT ''file_info.idx_tenant_create_time 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_tenant_delete_time = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_info' AND INDEX_NAME = 'idx_tenant_delete_time');
SET @ddl = IF(@has_tenant_delete_time = 0,
    'ALTER TABLE file_info ADD INDEX idx_tenant_delete_time (tenant_id, deleted, delete_time)',
    'SELECT ''file_info.idx_tenant_delete_time 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 冗余单列索引：只有在新复合索引确实存在时才删，避免老库上直接把唯一的租户索引删掉。
-- 这里必须重新查一次：上面的 @has_tenant_deleted 是"加索引之前"的快照，
-- 新库刚建完复合索引时它仍是 0，直接拿来判断会把删除步骤永远跳过。
SET @tenant_deleted_now = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_info' AND INDEX_NAME = 'idx_tenant_deleted');
SET @has_tenant_only = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_info' AND INDEX_NAME = 'idx_tenant_id');
SET @ddl = IF(@tenant_deleted_now > 0 AND @has_tenant_only > 0,
    'ALTER TABLE file_info DROP INDEX idx_tenant_id',
    'SELECT ''file_info.idx_tenant_id 无需删除（不存在或复合索引未就绪），跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
