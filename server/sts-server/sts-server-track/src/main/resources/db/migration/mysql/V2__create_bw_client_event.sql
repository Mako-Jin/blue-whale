-- ============================================================================
-- V2 · 客户端行为事件表（MySQL 8 方言）
--
-- 与 db/migration/h2/V2__create_bw_client_event.sql 结构一一对应，
-- 差别只在方言，逐条对照见文件末尾。
--
-- **为什么是 V2 而不是 V1**：Flyway 的 locations 是全模块共享的
-- classpath:db/migration/<vendor>（见 ADR-0009 第 2 条），因此**版本号在整个后端唯一**。
-- 日志域已占用 V1，事件域只能顺延。新增建表脚本请取全仓当前最大号 + 1。
--
-- 为什么按厂商分目录：MySQL 与 H2 各有自己的原生语法，硬凑一份"通用 DDL"
-- 只能取交集，结果两边都不是最佳形态（比如 MySQL 的列注释、存储引擎、
-- 字符集排序规则全都没法写）。分目录后各写各的，由 profile 决定跑哪一套。
--
-- 存储形态：一条事件一行，append-only。
--   props 以 JSON 字符串落库（列名 props_json），不建子表 ——
--   事件属性结构千变万化（不同事件的名字段完全不同），拆表既无查询收益
--   又会让写入放大数倍。代价是**不能对属性做条件查询**，这是有意接受的：
--   事件检索靠事件名、用户、设备、会话、时间窗，这些都有独立列与索引。
-- ============================================================================

CREATE TABLE bw_client_event
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',

    -- 幂等键。刻意**不建唯一索引**，理由见下方索引区 —— 这是有意为之，不是遗漏。
    event_id      VARCHAR(64)           DEFAULT NULL COMMENT '客户端生成的事件唯一 id（非唯一索引，见索引注释）',

    event_name    VARCHAR(128) NOT NULL COMMENT '事件名，小写点分至少两段，如 order.submit',
    app           VARCHAR(64)           DEFAULT NULL COMMENT '上报方应用标识',
    user_id       VARCHAR(64)           DEFAULT NULL COMMENT '登录用户标识，未登录为空',
    device_id     VARCHAR(64)           DEFAULT NULL COMMENT '匿名设备标识：登录前归因、登录后缝合身份',
    session_id    VARCHAR(64)           DEFAULT NULL COMMENT '会话标识',
    route         VARCHAR(512)          DEFAULT NULL COMMENT '触发事件时的路由',

    -- 列名刻意不叫 release。
    --   RELEASE 是 MySQL 8 的**保留字**（RELEASE SAVEPOINT），拿它当列名会报
    --   ERROR 1064 语法错误；而 H2 不认这个保留字，于是"H2 上跑得好好的、
    --   一换 MySQL 就建表失败"—— 这种只在其中一个库上暴露的差异最难查。
    --
    --   改名还有第二层原因：即便只把列名改成 app_release、Java 属性仍叫 release，
    --   MyBatis-Plus 会因为"列名与属性名不一致"而自动补别名 app_release AS release，
    --   别名又把保留字带回来，照样 1064。
    --   因此实体属性一并叫 appRelease，由下划线转驼峰推导列名，
    --   不产生别名（与 event_id → eventId 同构）。详见 ClientEventEntity 字段注释。
    --
    --   对外字段仍是 release，映射在 ClientEventService#toVO 里显式完成，契约不受影响。
    app_release   VARCHAR(64)           DEFAULT NULL COMMENT '发布版本号',

    props_json    TEXT COMMENT '事件属性（JSON 对象的字符串形式）',

    -- 属性结构版本：props 字段改名/改语义/删字段时必须递增，
    -- 否则同一列里两套含义不同的结构会被消费方无差别解读。
    props_version INT                   DEFAULT NULL COMMENT '事件属性结构版本，props 结构变化必须递增',

    -- 双时间戳：client_time 是客户端自报的，**不可信**，仅供展示；
    -- server_time 是服务端接收时间，排序/分页/时间窗的唯一依据。
    client_time   BIGINT                DEFAULT NULL COMMENT '客户端时间戳（毫秒）。不可信，仅供展示',
    server_time   BIGINT       NOT NULL COMMENT '服务端接收时间戳（毫秒）：排序/分页/时间窗的唯一依据',

    ip            VARCHAR(64)           DEFAULT NULL COMMENT '上报方 IP。属个人信息，生产需评估脱敏与保留期',

    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '客户端行为事件，append-only';

-- 索引按"最常用的过滤组合"建，不追求覆盖所有列 ——
-- 索引越多写入越慢，而事件是写多读少的场景。
-- MySQL 8 支持真正的降序索引（DESC），与 H2 行为一致。
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

-- ============================================================================
-- 方言差异（相对 h2/V2__create_bw_client_event.sql）
--   1. 列注释用 COMMENT '...'，H2 写在同一位置亦可，但 H2 不显示存储引擎
--   2. 表尾带 ENGINE / CHARSET / COLLATE —— H2 无此概念
--   3. utf8mb4 + utf8mb4_0900_ai_ci：中文属性值与 emoji 必须 utf8mb4，
--      建库时已指定，这里显式重申一次，避免落到别的默认字符集上
--   4. DDL 里不写 IF NOT EXISTS：Flyway 用 schema_history 表保证"只执行一次"，
--      再加 IF NOT EXISTS 反而会把"表已存在但结构不对"这种真问题静默吞掉
-- ============================================================================
