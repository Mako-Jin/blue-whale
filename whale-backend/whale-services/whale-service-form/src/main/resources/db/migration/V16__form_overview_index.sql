-- =====================================================================
-- 三维王城考功殿（T5.7）：表单台账概览所需的两个索引
-- ---------------------------------------------------------------------
-- 概览每次要问 6 个数：定义总数与启用数 / 今日提交 / 近 7 日趋势 / 待审批 /
-- 超时待审 / 待修差异。真正落在 form_data 上的只有两类形状：
--   ① 「本租户 + 时间窗」——今日提交、近 7 日趋势、提交 Top
--   ② 「本租户 + 业务状态（+ 多久没动）」——待审批、超时待审
--
-- V1 的 form_data 只有 idx_tenant_id / idx_definition_id / idx_biz_key /
-- idx_table_code_version / idx_form_definition_id 五个单列或与时间无关的组合，
-- V10 又补了 idx_tenant_biz_status。上面两类查询都落在 tenant_id 这一列上，
-- 单列索引只能把条件收窄到"本租户全部数据"，剩下的 create_time / biz_status
-- 全退化成回表过滤；form_data 是表单服务里唯一会长到千万级的表。
--
-- 两个索引各自对应一类查询：
--   1) form_data (tenant_id, create_time)                今日提交 / 按天趋势 / Top 表单
--   2) form_data (tenant_id, biz_status, update_time)    待审批累计 / 超时待审
--
-- 连带清掉两个被最左前缀完全覆盖的索引：idx_tenant_id、idx_tenant_biz_status。
-- 它们的存在只会让每次 insert 多维护一棵树，查询计划不会因此变好。
--
-- **不动** idx_definition_id / idx_biz_key / idx_table_code_version：它们服务的是
-- 业务侧按定义、按业务键、按物理表路由的等值查询，与租户 + 时间无关。
--
-- 幂等口径：先查 information_schema.STATISTICS 再决定是否 ADD / DROP，
--           可在任意已应用 V15 的库上重放。
-- =====================================================================

SET @has_tenant_create = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_data' AND INDEX_NAME = 'idx_tenant_create');
SET @ddl = IF(@has_tenant_create = 0,
    'ALTER TABLE form_data ADD INDEX idx_tenant_create (tenant_id, create_time)',
    'SELECT ''form_data.idx_tenant_create 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_tenant_biz_update = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_data' AND INDEX_NAME = 'idx_tenant_biz_update');
SET @ddl = IF(@has_tenant_biz_update = 0,
    'ALTER TABLE form_data ADD INDEX idx_tenant_biz_update (tenant_id, biz_status, update_time)',
    'SELECT ''form_data.idx_tenant_biz_update 已存在，跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 冗余单列索引：只有在新复合索引确实存在时才删，避免在只跑了一半的库上
-- 把 tenant_id 上唯一的索引删掉（那会让所有按租户的查询变成全表扫）
SET @has_tenant_create_ready = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_data' AND INDEX_NAME = 'idx_tenant_create');
SET @has_tenant_id_only = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_data' AND INDEX_NAME = 'idx_tenant_id');
SET @ddl = IF(@has_tenant_create_ready > 0 AND @has_tenant_id_only > 0,
    'ALTER TABLE form_data DROP INDEX idx_tenant_id',
    'SELECT ''form_data.idx_tenant_id 无需删除（不存在或复合索引未就绪），跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_tenant_biz_update_ready = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_data' AND INDEX_NAME = 'idx_tenant_biz_update');
SET @has_tenant_biz_only = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'form_data' AND INDEX_NAME = 'idx_tenant_biz_status');
SET @ddl = IF(@has_tenant_biz_update_ready > 0 AND @has_tenant_biz_only > 0,
    'ALTER TABLE form_data DROP INDEX idx_tenant_biz_status',
    'SELECT ''form_data.idx_tenant_biz_status 无需删除（不存在或复合索引未就绪），跳过''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
