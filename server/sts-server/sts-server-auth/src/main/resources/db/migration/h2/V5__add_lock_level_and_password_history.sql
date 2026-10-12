-- ============================================================================
-- V5 · 口令与登录安全加固（H2 方言）
--
-- 与 db/migration/mysql/V5__*.sql、db/migration/dameng/V5__*.sql 结构一一对应，
-- 差别只在方言（见文件末尾）。
--
-- 【为什么是 V5】Flyway 的 locations 是全模块共享的 classpath:db/migration/<vendor>，
-- 版本号**全仓唯一**：V1 日志域、V2 行为事件域、V3 权限域、V4 审计域，本片接 V5。
-- 从 V1 起编号会让 Flyway 在启动阶段直接失败（Found more than one migration with version 1）。
-- 见 docs/decisions/0009 与 0020。
--
-- 【本次两件事】二者同属"口令与登录加固"，落在权限域，因此共用一个版本号。
--
--   1) bw_auth_user 增加 lock_level —— 指数退避锁定要记住"被锁过几次"。
--      **只加一列，不加新表**：登录失败状态本就落在用户行上
--      （failed_attempts / locked_until / lock_reason），为它单开一张
--      login_attempt 表是参照实现 SystemTerminator 的做法
--      （LoginAttemptEntity 按 user+tenant 一聚合），在本仓的单库模型下没有收益，
--      只会让"判断是否锁定"从一次主键查询变成一次 JOIN。见 docs/decisions/0020。
--
--   2) 新增 bw_auth_password_history —— 禁止重用最近若干个历史口令。
--
-- 【password_history 刻意不带审计五列、不带 deleted】
--   它是 append-only 的事实记录（与 bw_audit_log 同性质）：没有"更新人"，
--   而删除它等于抹掉"这个口令被用过"的事实 —— 那正是防重用检查赖以成立的依据。
--   因此它**不继承 BaseEntity**，是 RefreshTokenEntity 之后第二例（见 ADR-0016 后续约束）。
--   增长由应用侧控制：每次写入后只保留最近 N 条（N 取配置里的 historyCount）。
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
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id          BIGINT       NOT NULL,
    user_id            BIGINT       NOT NULL,
    password_hash      VARCHAR(255) NOT NULL,
    password_algorithm VARCHAR(32),
    change_source      VARCHAR(32)  NOT NULL,
    change_time        BIGINT       NOT NULL
);

-- 唯一的查询形态是"取某用户最近 N 条"，因此索引直接覆盖该访问路径。
-- 刻意**不加** DESC（H2 与达梦对索引排序方向的支持不一致，见 V4 的同款取舍）。
CREATE INDEX idx_bw_auth_password_history_user ON bw_auth_password_history (user_id, change_time);

-- ============================================================================
-- 方言差异（相对 mysql/V5__*.sql）
--   1. H2 无列注释，列语义以本文件的 -- 注释与实体字段注释为准
--   2. H2 跑在 MODE=MySQL 下，ALTER TABLE ADD COLUMN 与 TINYINT 语义与 MySQL 接近，
--      但**兼容模式不等于等价** —— 三套脚本必须分厂商维护，不能合一份
--   3. 三份脚本都刻意不写 IF NOT EXISTS：Flyway 用 schema_history 保证只执行一次，
--      加上它只会把"表已存在但结构不对"静默吞掉，那是比建表失败更糟的结果
-- ============================================================================
