-- =====================================================================
-- BlueWhale - whale-service-auth 幂等迁移 5.85：OA 审批人解析（部门负责人用户ID）
-- =====================================================================
-- 适用库   : bluewhale_auth
-- 上级计划 : Wave 5.82 M0-5（Auth 增强）
-- 说明     : 1) sys_dept 增加 leader_user_id（逻辑关联 sys_user.id，允许为空）；
--            2) 增加 (tenant_id, leader_user_id) 索引；
--            3) 按 (tenant_id, username) 回填既有 leader 账号对应的启用用户ID。
-- 幂等     : MySQL 不支持 ADD COLUMN / ADD INDEX IF NOT EXISTS，统一使用
--            information_schema 守卫 + PREPARE/EXECUTE，空库/存量库重复执行均安全；
--            回填仅更新 leader_user_id IS NULL 的行，不会覆盖已维护的负责人。
-- =====================================================================

SET @has_leader_user_id := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dept'
      AND COLUMN_NAME = 'leader_user_id'
);
SET @ddl := IF(@has_leader_user_id = 0,
    'ALTER TABLE sys_dept ADD COLUMN leader_user_id BIGINT NULL COMMENT ''负责人用户ID（逻辑关联 sys_user.id）''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx_tenant_leader := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dept'
      AND INDEX_NAME = 'idx_tenant_leader'
);
SET @ddl := IF(@has_idx_tenant_leader = 0,
    'ALTER TABLE sys_dept ADD KEY idx_tenant_leader (tenant_id, leader_user_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 存量回填：leader 字段存的是账号，按 (tenant_id, username) 匹配启用且未删除的用户
-- ---------------------------------------------------------------------
UPDATE sys_dept d
JOIN sys_user u ON u.tenant_id = d.tenant_id AND u.username = d.leader
SET d.leader_user_id = u.id
WHERE d.leader_user_id IS NULL
  AND u.status = 1
  AND u.deleted = 0;
