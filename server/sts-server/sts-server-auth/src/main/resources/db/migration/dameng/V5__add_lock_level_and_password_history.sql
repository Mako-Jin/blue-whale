-- ============================================================================
-- V5 · 口令与登录安全加固（达梦 DM8 方言）
--
-- 【执行方式】达梦 profile 下 Flyway 是关闭的（Flyway 官方无达梦模块，
-- 见 application-dameng.yml 与 docs/decisions/0010），本脚本作为**交付脚本**
-- 在部署时执行一次，辅助脚本 scripts/init-dameng.sh。
--
-- 【编号仍用 V5__】不是给 Flyway 看的，是为了三套脚本能按同一条时间线对照；
-- 将来若在达梦上恢复自动迁移，文件无需改名挪位即可被 Flyway 接管。
--
-- 达梦方言相对 MySQL 的差异（与 V3/V4 同）：
--   1. 自增列是 IDENTITY(种子, 增量)，不是 AUTO_INCREMENT
--   2. 列注释必须用独立的 COMMENT ON COLUMN，不支持 inline COMMENT '...'
--   3. 没有 ENGINE / DEFAULT CHARSET / COLLATE 表选项
--   4. 标识符不加引号会被自动转成大写存储（与 Oracle 一致）
--   5. VARCHAR 长度默认按**字节**计 → 一律显式写 CHAR 语义
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 用户：锁定层级（指数退避的指数）
-- ---------------------------------------------------------------------------
ALTER TABLE bw_auth_user ADD COLUMN lock_level INT;

-- ---------------------------------------------------------------------------
-- 2. 口令历史（禁止重用）
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_password_history
(
    id                 BIGINT IDENTITY(1, 1) NOT NULL,
    tenant_id          BIGINT           NOT NULL,
    user_id            BIGINT           NOT NULL,
    password_hash      VARCHAR(255 CHAR) NOT NULL,
    password_algorithm VARCHAR(32 CHAR),
    change_source      VARCHAR(32 CHAR)  NOT NULL,
    change_time        BIGINT           NOT NULL,
    CONSTRAINT pk_bw_auth_password_history PRIMARY KEY (id)
);

CREATE INDEX idx_bw_auth_password_history_user ON bw_auth_password_history (user_id, change_time);

COMMENT ON COLUMN bw_auth_password_history.id IS '主键';
COMMENT ON COLUMN bw_auth_password_history.tenant_id IS '所属租户主键';
COMMENT ON COLUMN bw_auth_password_history.user_id IS '所属用户主键';
COMMENT ON COLUMN bw_auth_password_history.password_hash IS '该口令的哈希，与 bw_auth_user.password_hash 同一套格式';
COMMENT ON COLUMN bw_auth_password_history.password_algorithm IS '哈希算法标识，如 PBKDF2-SHA256';
COMMENT ON COLUMN bw_auth_password_history.change_source IS '改密来源：CREATE / ADMIN_RESET';
COMMENT ON COLUMN bw_auth_password_history.change_time IS '写入时间（毫秒）';
COMMENT ON COLUMN bw_auth_user.lock_level IS '连续失败被锁定的层级，用于指数退避计算；0/NULL 表示未被锁过';

-- ============================================================================
-- 保留字核对（同 mysql 版）：lock_level / change_source / change_time /
--   password_hash / password_algorithm 均不在 V$RESERVED_WORDS 的 RESERVED='Y' 内。
--   本次**未在真实达梦实例上验证过**（与 V3/V4 同），请在部署前重跑：
--     SELECT KEYWORD FROM V$RESERVED_WORDS WHERE RESERVED='Y'
-- ============================================================================
