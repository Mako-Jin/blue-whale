-- ============================================================================
-- V7 · 客户端行为事件增加租户维度（达梦 DM8 方言）
--
-- 【执行方式】达梦 profile 下 Flyway 是关闭的（Flyway 官方无达梦模块，
-- 见 application-dameng.yml 与 docs/decisions/0010），本脚本作为**交付脚本**
-- 在部署时执行一次，辅助脚本 scripts/init-dameng.sh。
--
-- 【编号仍用 V7__】不是给 Flyway 看的，是为了三套脚本能按同一条时间线对照。
--
-- 设计理由见 h2 版文件头（同一批改动，与 V6 同理）。
--
-- 达梦方言相对 MySQL 的差异（与 V3~V6 同）：
--   1. 列注释必须用独立的 COMMENT ON COLUMN，不支持 inline COMMENT '...'
--   2. 标识符不加引号会被自动转成大写存储（与 Oracle 一致）
-- ============================================================================

ALTER TABLE bw_client_event
    ADD COLUMN tenant_id BIGINT DEFAULT NULL;

COMMENT ON COLUMN bw_client_event.tenant_id IS '所属租户主键；NULL 表示上报时未登录，不属于任何租户';

CREATE INDEX idx_bw_client_event_tenant_time ON bw_client_event (tenant_id, server_time DESC);
