-- ============================================================================
-- V1 · 客户端日志表（MySQL 8 方言）
--
-- 与 db/migration/h2/V1__create_bw_client_log.sql 结构一一对应，
-- 差别只在方言，逐条对照见文件末尾。
--
-- 为什么按厂商分目录：MySQL 与 H2 各有自己的原生语法，硬凑一份"通用 DDL"
-- 只能取交集，结果两边都不是最佳形态（比如 MySQL 的列注释、存储引擎、
-- 字符集排序规则全都没法写）。分目录后各写各的，由 profile 决定跑哪一套。
--
-- 存储形态：一条上报记录一行，append-only。
--   data / context 以 JSON 字符串落库（列名 *_json），不建子表 ——
--   这两个字段结构千变万化，拆表既无查询收益又会让写入放大数倍。
--   代价是**不能对它们做条件查询**，这是有意接受的：日志检索靠级别、
--   命名空间、链路、时间窗，这些都有独立列与索引。
-- ============================================================================

CREATE TABLE bw_client_log
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    app          VARCHAR(64)           DEFAULT NULL COMMENT '上报方应用标识，如 platform-log-console',
    level        VARCHAR(16)  NOT NULL COMMENT '级别小写：trace/debug/perf/info/success/warn/error',
    namespace    VARCHAR(255)          DEFAULT NULL COMMENT '命名空间，形如 app › auth › login',
    message      TEXT COMMENT '日志正文',
    data_json    TEXT COMMENT '随消息附带的原始数据（JSON 数组的字符串形式）',
    context_json TEXT COMMENT '结构化上下文（JSON 对象的字符串形式）',
    trace_id     VARCHAR(64)           DEFAULT NULL COMMENT '链路标识：跨端串联同一次请求的全部日志',
    session_id   VARCHAR(64)           DEFAULT NULL COMMENT '会话标识',
    route        VARCHAR(512)          DEFAULT NULL COMMENT '触发日志时的路由',

    -- 列名刻意不叫 release。
    --   RELEASE 是 MySQL 8 的**保留字**（RELEASE SAVEPOINT），拿它当列名会报
    --   ERROR 1064 语法错误；而 H2 不认这个保留字，于是"H2 上跑得好好的、
    --   一换 MySQL 就建表失败"—— 这种只在其中一个库上暴露的差异最难查。
    --
    --   改名还有第二层原因：即便只把列名改成 app_release、Java 属性仍叫 release，
    --   MyBatis-Plus 会因为"列名与属性名不一致"而自动补别名 app_release AS release，
    --   别名又把保留字带回来，照样 1064。
    --   因此实体属性一并叫 appRelease，由下划线转驼峰推导列名，
    --   不产生别名（与 traceId → trace_id 同构）。详见 ClientLogEntity 字段注释。
    --
    --   对外字段仍是 release，映射在 ClientLogService#toVO 里显式完成，契约不受影响。
    app_release  VARCHAR(64)           DEFAULT NULL COMMENT '发布版本号',

    client_time  BIGINT                DEFAULT NULL COMMENT '客户端时间戳（毫秒）。不可信，仅供展示',
    server_time  BIGINT       NOT NULL COMMENT '服务端接收时间戳（毫秒）：排序/分页/时间窗的唯一依据',
    ip           VARCHAR(64)           DEFAULT NULL COMMENT '上报方 IP。属个人信息，生产需评估脱敏与保留期',

    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '客户端技术日志，append-only';

-- 索引按"最常用的过滤组合"建，不追求覆盖所有列 ——
-- 索引越多写入越慢，而日志是写多读少的场景。
-- MySQL 8 支持真正的降序索引（DESC），与 H2 行为一致。
CREATE INDEX idx_bw_client_log_server_time ON bw_client_log (server_time DESC);

CREATE INDEX idx_bw_client_log_level_time ON bw_client_log (level, server_time DESC);

CREATE INDEX idx_bw_client_log_trace_id ON bw_client_log (trace_id);

CREATE INDEX idx_bw_client_log_app_time ON bw_client_log (app, server_time DESC);

-- ============================================================================
-- 方言差异（相对 h2/V1__create_bw_client_log.sql）
--   1. 列注释用 COMMENT '...'，H2 写在同一位置亦可，但 H2 不显示存储引擎
--   2. 表尾带 ENGINE / CHARSET / COLLATE —— H2 无此概念
--   3. utf8mb4 + utf8mb4_0900_ai_ci：中文日志正文与 emoji 必须 utf8mb4，
--      建库时已指定，这里显式重申一次，避免落到别的默认字符集上
--   4. DDL 里不写 IF NOT EXISTS：Flyway 用 schema_history 表保证"只执行一次"，
--      再加 IF NOT EXISTS 反而会把"表已存在但结构不对"这种真问题静默吞掉
-- ============================================================================
