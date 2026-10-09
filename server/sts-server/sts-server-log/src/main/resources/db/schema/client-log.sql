-- ============================================================================
-- 客户端日志表
--
-- 方言：H2（开发 / 演示用内存库）。
--   生产库的建表不走这里，将由迁移工具（Flyway）管理 —— 见
--   docs/decisions/0006-日志服务契约与前端 SDK 生成.md 的"后续约束"。
--   `CREATE INDEX IF NOT EXISTS` 是 H2 / PostgreSQL 语法，MySQL 8 不支持，
--   这正是不能两边共用一份 DDL 的原因。
--
-- 存储形态：一条上报记录一行，append-only。
--   data / context 以 JSON 字符串落库（列名 *_json），不建子表 ——
--   日志的这两个字段结构千变万化，拆表既无查询收益又会让写入放大数倍。
--   代价是**不能对它们做条件查询**，这是有意接受的：日志检索靠级别、命名空间、
--   链路、时间窗，这些都有独立列与索引。
-- ============================================================================

CREATE TABLE IF NOT EXISTS bw_client_log
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- 上报方应用标识，如 platform-log-console
    app          VARCHAR(64),

    -- 级别小写：trace / debug / perf / info / success / warn / error
    level        VARCHAR(16)  NOT NULL,

    -- 命名空间，形如 app › auth › login
    namespace    VARCHAR(255),

    message      TEXT,

    -- 随消息附带的原始数据（JSON 数组的字符串形式）
    data_json    TEXT,

    -- 结构化上下文（JSON 对象的字符串形式）
    context_json TEXT,

    -- 链路标识：跨端串联同一次请求的全部日志
    trace_id     VARCHAR(64),

    session_id   VARCHAR(64),

    -- 触发日志时的路由
    route        VARCHAR(512),

    release      VARCHAR(64),

    -- 客户端时间戳（毫秒）。**不可信** —— 客户端时钟可能错乱或被人为篡改，
    -- 因此只作参考，所有排序与时间窗过滤一律用 server_time。
    client_time  BIGINT,

    -- 服务端接收时间戳（毫秒）：排序、分页、时间窗的唯一依据
    server_time  BIGINT       NOT NULL,

    -- 上报方 IP。属于个人信息，生产环境需评估脱敏（掩码）与保留期
    ip           VARCHAR(64)
);

-- 索引按"最常用的过滤组合"建，不追求覆盖所有列 ——
-- 索引越多写入越慢，而日志是写多读少的场景。
CREATE INDEX IF NOT EXISTS idx_bw_client_log_server_time ON bw_client_log (server_time DESC);

CREATE INDEX IF NOT EXISTS idx_bw_client_log_level_time ON bw_client_log (level, server_time DESC);

CREATE INDEX IF NOT EXISTS idx_bw_client_log_trace_id ON bw_client_log (trace_id);

CREATE INDEX IF NOT EXISTS idx_bw_client_log_app_time ON bw_client_log (app, server_time DESC);
