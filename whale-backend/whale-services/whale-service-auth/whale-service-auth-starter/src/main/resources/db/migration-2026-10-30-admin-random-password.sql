-- =====================================================================
-- T-03 安全整改：去除默认管理员口令 —— sys_user 强制首登改密标记（幂等）
-- =====================================================================
-- 适用场景 : 已按既有 Wave 部署、库中已存在 sys_user 表的运行库
--            （保留存量账号与授权数据，不重建表）。
-- 全新建库 : 请直接执行 init.sql，无需本脚本（init.sql 已同步该列）。
-- 变更内容 : sys_user 增加 must_change_password（是否强制首登改密，1=是）。
--            种子 admin（id=1）行不在此处改密：whale-service-auth 启动时
--            AdminPasswordInitializer 检测到 init.sql 出厂默认密文会自动
--            轮换为随机口令并置 must_change_password=1，初始口令仅打印一次。
-- 说明     : 幂等（列先查 information_schema 再决定是否执行，可重复运行）。
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-2026-10-30-admin-random-password.sql
-- =====================================================================

-- 1. sys_user 增加 must_change_password（仅当不存在时执行）
SET @must_change_column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user'
      AND column_name = 'must_change_password'
);
SET @must_change_ddl := IF(@must_change_column_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN must_change_password TINYINT NOT NULL DEFAULT 0 COMMENT ''是否强制首登改密（1=是）'' AFTER password',
    'SELECT 1');
PREPARE must_change_stmt FROM @must_change_ddl;
EXECUTE must_change_stmt;
DEALLOCATE PREPARE must_change_stmt;

-- 2. 存量行补默认值说明：新增列默认 0（不强制改密），此处无需回填；
--    如历史上手工把种子 admin 改过密，则其密文已非出厂默认值，启动轮换器不会触碰。
--    校验建议：
--      SELECT id, username, must_change_password FROM sys_user WHERE id = 1;
-- =====================================================================
-- 完成。轮换动作由服务启动时的 AdminPasswordInitializer 完成（开关
-- whale.auth.admin-password.rotate-on-default，默认开启）。
-- =====================================================================
