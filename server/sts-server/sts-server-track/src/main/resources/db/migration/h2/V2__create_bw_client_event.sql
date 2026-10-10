-- ============================================================================
-- V2 · 客户端行为事件表（H2 方言）
--
-- 与 db/migration/mysql/V2__create_bw_client_event.sql 结构一一对应，
-- 差别只在方言（详见那个文件末尾的对照表）。
--
-- **为什么是 V2 而不是 V1**：Flyway 的 locations 是全模块共享的
-- classpath:db/migration/<vendor>（见 ADR-0009 第 2 条），因此**版本号在整个后端唯一**，
-- 而不是"每个模块各从 V1 开始"。日志域已占用 V1，事件域只能顺延。
-- 新增建表脚本请取全仓当前最大号 + 1：各自从 V1 起编号会让 Flyway 在启动时直接报
-- "Found more than one migration with version 1"，而且是在**应用启动阶段**才暴露。
--
-- H2 用于本地开发与演示：内存库，零外部依赖，clone 下来就能跑。
-- 它跑在 MODE=MySQL 下，目的是让 SQL 尽量贴近 MySQL 语义，
-- 减少"本地能跑、线上报表不存在"那类意外 —— 但**兼容模式不是等价**，
-- 保留字、字符集、索引实现上仍有差异，所以迁移脚本才要分厂商维护。
--
-- 与 bw_client_log 的区别（为什么另起一张表而不是复用日志表）：
--   日志是技术切片，回答"系统在干什么"，量大、可采样、可丢弃；
--   事件是业务事实，回答"用户做了什么"，可去重、要长期留存、被产品/增长侧消费。
--   两者的保留期、采样策略、消费方、索引组合全都不一样，硬塞进一张表
--   会让"日志的清理任务误删事件"这类事故变得可能。
-- ============================================================================

CREATE TABLE bw_client_event
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- 客户端生成的事件唯一 id，**幂等键**。刻意**不建唯一索引**，理由见下方索引区。
    event_id      VARCHAR(64),

    event_name    VARCHAR(128)  NOT NULL,
    app           VARCHAR(64),
    user_id       VARCHAR(64),

    -- 匿名设备标识：登录前靠它归因，登录后靠它把"匿名期"与"登录期"缝成同一个人。
    -- 允许为空是因为某些端（如受限的 WebView）拿不到稳定设备号，
    -- 硬要求非空会把这类端的事件整体挡在门外。
    device_id     VARCHAR(64),

    session_id    VARCHAR(64),
    route         VARCHAR(512),

    -- 列名与 MySQL 版保持一致（app_release 而非 release）：
    -- 两份脚本必须"同构"，只是语法不同；否则换了库就要改代码里的映射。
    -- 不用 release 的原因见 MySQL 版脚本的注释 —— RELEASE 是那边的保留字。
    app_release   VARCHAR(64),

    props_json    TEXT,

    -- 属性结构版本：props 的字段一旦改名/改语义/删字段，必须递增此值。
    -- 否则同一列里会同时躺着两套含义不同的结构，消费方无差别解读得到静默错误的结果。
    props_version INT,

    -- 双时间戳，各司其职：
    --   client_time 是客户端自己报的，客户端时钟可能被改、可能是错的，**不可信**，仅供展示；
    --   server_time 是服务端收到的那一刻，是排序、分页、时间窗过滤的**唯一依据**。
    -- 若拿 client_time 排序，一台时钟偏了的机器就能把它的全部事件插到别人的序列中间。
    client_time   BIGINT,
    server_time   BIGINT        NOT NULL,

    ip            VARCHAR(64)
);

-- 索引按"最常用的过滤组合"建，不追求覆盖所有列 ——
-- 索引越多写入越慢，而事件是写多读少的场景。
CREATE INDEX idx_bw_client_event_server_time ON bw_client_event (server_time DESC);

CREATE INDEX idx_bw_client_event_name_time ON bw_client_event (event_name, server_time DESC);

CREATE INDEX idx_bw_client_event_session_id ON bw_client_event (session_id);

CREATE INDEX idx_bw_client_event_user_id ON bw_client_event (user_id);

-- event_id 索引**刻意不唯一** —— 这是有意的设计，不是遗漏。
--
-- 事件通道是"尽力而为"的：一次上报是一个多值 INSERT，一次最多 500 条。
-- 若给 event_id 建唯一索引，那么只要批内出现一条重复（客户端重试、网络重发、
-- 客户端本地队列拼接出错都会造成），**整批插入就会失败** ——
-- 一条重复拖垮整批，另外 499 条正常事件一起丢。
--
-- 两害相权：宁可在落库后发现少量重复，事后按 event_id 去重（查询/聚合时 DISTINCT，
-- 或异步补偿任务清理），也不能让一条重复毁掉整批。因此这里用普通索引：
-- 它仍能加速"按 event_id 反查/去重"的操作，只是不承担唯一性约束。
--
-- 唯一性若确实需要，位置应在**消费侧**（按 event_id 做幂等），而不是写入侧的硬约束 ——
-- 写入侧要为可用性让路，消费侧才谈得上强一致。
CREATE INDEX idx_bw_client_event_event_id ON bw_client_event (event_id);
