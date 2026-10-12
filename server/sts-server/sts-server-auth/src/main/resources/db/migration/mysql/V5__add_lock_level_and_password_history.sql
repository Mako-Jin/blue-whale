-- ============================================================================
-- V5 · 口令与登录安全加固（MySQL 8 方言）
--
-- 与 db/migration/h2/V5__*.sql 结构一一对应，差别只在方言。
-- 版本号与理由见 h2 版文件头（V1 日志 / V2 事件 / V3 权限 / V4 审计 / V5 本片）。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 用户：锁定层级（指数退避的指数）
-- ---------------------------------------------------------------------------
ALTER TABLE bw_auth_user
    ADD COLUMN lock_level INT DEFAULT NULL COMMENT '连续失败被锁定的层级，用于指数退避计算；0/NULL 表示未被锁过';

-- ---------------------------------------------------------------------------
-- 2. 口令历史（禁止重用）
-- ---------------------------------------------------------------------------
CREATE TABLE bw_auth_password_history
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id          BIGINT       NOT NULL COMMENT '所属租户主键',
    user_id            BIGINT       NOT NULL COMMENT '所属用户主键',
    password_hash      VARCHAR(255) NOT NULL COMMENT '该口令的哈希，与 bw_auth_user.password_hash 同一套格式',
    password_algorithm VARCHAR(32)           DEFAULT NULL COMMENT '哈希算法标识，如 PBKDF2-SHA256',
    change_source      VARCHAR(32)  NOT NULL COMMENT '改密来源：CREATE / ADMIN_RESET',
    change_time        BIGINT       NOT NULL COMMENT '写入时间（毫秒）',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '口令历史（禁止重用），append-only';

CREATE INDEX idx_bw_auth_password_history_user ON bw_auth_password_history (user_id, change_time);

-- ============================================================================
-- 保留字核对（新增列前必须复查，且 MySQL 与达梦的结论**不可互推**）
--
-- 本次新增的列名：lock_level / password_hash / password_algorithm /
--   change_source / change_time / tenant_id / user_id
--   · lock_level 用前缀化的 level，避开裸 level（LEVEL 是达梦的关键字，
--     虽属"非保留"可作列名，但前缀化后彻底无歧义）
--   · change_source 不用裸 source / change_type（TYPE 是 MySQL 保留字）
--   · 其余列名均不在 MySQL 8 保留字集合内
--
-- 达梦侧同样核对：上述列名均不在 V$RESERVED_WORDS 的 RESERVED='Y' 集合内。
--   仍建议在真实达梦实例上重跑：
--     SELECT KEYWORD FROM V$RESERVED_WORDS WHERE RESERVED='Y'
-- ============================================================================
