-- ============================================================================
-- V7 · 客户端行为事件增加租户维度（H2 方言）
--
-- 与 db/migration/mysql/V7__*.sql、db/migration/dameng/V7__*.sql 结构一一对应，
-- 差别只在方言。
--
-- 【为什么是 V7】V6 已给 bw_client_log 加同一列。两表同属"客户端上报的观测数据"、
-- 同一批改动，但**分属两个模块**（本表在 sts-server-track），
-- 而 Flyway 的版本号是全仓共享的（见 docs/decisions/0009），因此只能顺延为 V7，
-- 不能两处都写 V6。
--
-- 【为什么可空、NULL 为何不可见、为何不回填历史数据】
--   与 V6 完全同理（上报接口公开、未登录时归属未知、编造的归属比查不到更危险），
--   完整论证见 db/migration/h2/V6__add_tenant_id_to_bw_client_log.sql 的文件头。
--
-- 【本表特有的一个陷阱 —— user_id 不是归属依据】
--   本表已有 user_id 列，且它由**客户端上报体**提供。看到"有用户了"很容易顺手写成
--   "按 user_id 过滤就够了"，但那个值可以随便填：伪造一个别人的 user_id
--   就能读到别人的行为记录。归属只能取服务端在接收时从会话里拿到的租户，
--   也就是本次新增的 tenant_id。两者的区别在正常数据上完全看不出来。
-- ============================================================================

ALTER TABLE bw_client_event
    ADD COLUMN tenant_id BIGINT DEFAULT NULL;

CREATE INDEX idx_bw_client_event_tenant_time ON bw_client_event (tenant_id, server_time DESC);

-- ============================================================================
-- 方言差异（相对 mysql/V7、dameng/V7）
--   1. H2 与 MySQL 的 ADD COLUMN 语法一致；达梦需用独立的 COMMENT ON COLUMN
--   2. 三者都支持复合索引与 DESC 写法
--   3. DDL 里不写 IF NOT EXISTS：Flyway 用 schema_history 保证"只执行一次"
-- ============================================================================
