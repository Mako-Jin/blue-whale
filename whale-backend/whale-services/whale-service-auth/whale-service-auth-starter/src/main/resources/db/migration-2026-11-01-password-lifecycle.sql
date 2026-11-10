-- =====================================================================
-- REQ-SEC-023 口令生命周期：sys_user 改密时间/提醒时间 + 口令历史表（幂等）
-- =====================================================================
-- 适用场景 : 已按既有 Wave 部署、库中已存在 sys_user 表的运行库
--            （保留存量账号与授权数据，不重建表）。
-- 全新建库 : 请直接执行 init.sql，无需本脚本（init.sql 已同步该结构与表）。
-- 变更内容 : 1) sys_user 增加 password_updated_time / password_notice_time；
--            2) 存量账号改密时间回填为 create_time（避免上线即全员到期）；
--            3) 新建 sys_password_history（最近 N 次口令不可复用，仅存 BCrypt 密文）。
-- 说明     : 幂等（列先查 information_schema 再决定是否执行；表用 IF NOT EXISTS，可重复运行）。
-- 执行方式 : mysql -u<user> -p bluewhale_auth < migration-2026-11-01-password-lifecycle.sql
-- =====================================================================

-- 1. sys_user.password_updated_time（仅当不存在时执行）
SET @pwd_updated_column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user'
      AND column_name = 'password_updated_time'
);
SET @pwd_updated_ddl := IF(@pwd_updated_column_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN password_updated_time DATETIME DEFAULT NULL COMMENT ''最近一次改密时间（口令年龄与到期判定）'' AFTER must_change_password',
    'SELECT 1');
PREPARE pwd_updated_stmt FROM @pwd_updated_ddl;
EXECUTE pwd_updated_stmt;
DEALLOCATE PREPARE pwd_updated_stmt;

-- 2. sys_user.password_notice_time（仅当不存在时执行）
SET @pwd_notice_column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user'
      AND column_name = 'password_notice_time'
);
SET @pwd_notice_ddl := IF(@pwd_notice_column_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN password_notice_time DATETIME DEFAULT NULL COMMENT ''最近一次口令到期提醒时间'' AFTER password_updated_time',
    'SELECT 1');
PREPARE pwd_notice_stmt FROM @pwd_notice_ddl;
EXECUTE pwd_notice_stmt;
DEALLOCATE PREPARE pwd_notice_stmt;

-- 3. 存量账号改密时间回填：以创建时间作为口令年龄起点，避免升级后立即全部判为到期
UPDATE sys_user
SET password_updated_time = COALESCE(create_time, NOW())
WHERE password_updated_time IS NULL;

-- 4. 口令历史表（幂等建表）
CREATE TABLE IF NOT EXISTS sys_password_history (
    id          BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT        NOT NULL                COMMENT '所属租户ID（逻辑关联 sys_tenant.id）',
    user_id     BIGINT        NOT NULL                COMMENT '用户ID（逻辑关联 sys_user.id）',
    password    VARCHAR(100)  NOT NULL                COMMENT '历史口令（BCrypt 密文）',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '写入时间',
    PRIMARY KEY (id),
    KEY idx_history_user (tenant_id, user_id, create_time)
) COMMENT='用户口令历史表';

-- 校验建议：
--   SELECT id, username, password_updated_time, password_notice_time FROM sys_user LIMIT 10;
--   SHOW CREATE TABLE sys_password_history;
-- =====================================================================
-- 完成。口令强度、历史复用与到期策略由 whale.auth.password.* 配置驱动。
-- =====================================================================
