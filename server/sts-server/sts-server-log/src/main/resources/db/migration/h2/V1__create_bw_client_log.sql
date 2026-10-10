-- ============================================================================
-- V1 · 客户端日志表（H2 方言）
--
-- 与 db/migration/mysql/V1__create_bw_client_log.sql 结构一一对应，
-- 差别只在方言（详见那个文件末尾的对照表）。
--
-- H2 用于本地开发与演示：内存库，零外部依赖，clone 下来就能跑。
-- 它跑在 MODE=MySQL 下，目的是让 SQL 尽量贴近 MySQL 语义，
-- 减少"本地能跑、线上报表不存在"那类意外 —— 但**兼容模式不是等价**，
-- 保留字、字符集、索引实现上仍有差异，所以迁移脚本才要分厂商维护。
-- ============================================================================

CREATE TABLE bw_client_log
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    app          VARCHAR(64),
    level        VARCHAR(16)  NOT NULL,
    namespace    VARCHAR(255),
    message      TEXT,
    data_json    TEXT,
    context_json TEXT,
    trace_id     VARCHAR(64),
    session_id   VARCHAR(64),
    route        VARCHAR(512),

    -- 列名与 MySQL 版保持一致（app_release 而非 release）：
    -- 两份脚本必须"同构"，只是语法不同；否则换了库就要改代码里的映射。
    -- 不用 release 的原因见 MySQL 版脚本的注释 —— RELEASE 是那边的保留字。
    app_release  VARCHAR(64),

    client_time  BIGINT,
    server_time  BIGINT       NOT NULL,
    ip           VARCHAR(64)
);

-- 索引按"最常用的过滤组合"建，不追求覆盖所有列 ——
-- 索引越多写入越慢，而日志是写多读少的场景。
CREATE INDEX idx_bw_client_log_server_time ON bw_client_log (server_time DESC);

CREATE INDEX idx_bw_client_log_level_time ON bw_client_log (level, server_time DESC);

CREATE INDEX idx_bw_client_log_trace_id ON bw_client_log (trace_id);

CREATE INDEX idx_bw_client_log_app_time ON bw_client_log (app, server_time DESC);
