-- ============================================================================
-- V6 · 客户端技术日志增加租户维度（达梦 DM8 方言）
--
-- 【执行方式】达梦 profile 下 Flyway 是关闭的（Flyway 官方无达梦模块，
-- 见 application-dameng.yml 与 docs/decisions/0010），本脚本作为**交付脚本**
-- 在部署时执行一次，辅助脚本 scripts/init-dameng.sh。
--
-- 【编号仍用 V6__】不是给 Flyway 看的，是为了三套脚本能按同一条时间线对照；
-- 将来若在达梦上恢复自动迁移，文件无需改名挪位即可被 Flyway 接管。
--
-- 设计理由（为什么可空、为什么 NULL 不可见、为什么不回填历史数据）见 h2 版文件头。
--
-- 达梦方言相对 MySQL 的差异（与 V3/V4/V5 同）：
--   1. 列注释必须用独立的 COMMENT ON COLUMN，不支持 inline COMMENT '...'
--   2. 标识符不加引号会被自动转成大写存储（与 Oracle 一致）
-- ============================================================================

ALTER TABLE bw_client_log
    ADD COLUMN tenant_id BIGINT DEFAULT NULL;

COMMENT ON COLUMN bw_client_log.tenant_id IS '所属租户主键；NULL 表示上报时未登录，不属于任何租户';

CREATE INDEX idx_bw_client_log_tenant_time ON bw_client_log (tenant_id, server_time DESC);
