-- ============================================================================
-- V2 · 客户端行为事件表（达梦 DM8 方言）
--
-- 与下面两份脚本结构一一对应，差别只在方言：
--   db/migration/h2/V2__create_bw_client_event.sql
--   db/migration/mysql/V2__create_bw_client_event.sql
--
-- 版本号取 V2 与另两份一致：达梦下 Flyway 是关闭的（ADR-0010），编号不影响执行，
-- 但三套脚本必须能按同一条时间线对照阅读 —— 否则将来恢复自动迁移就要全部挪位。
-- 三份的**列名必须完全一致** —— 列名一旦不同，Java 侧的映射就得跟着分叉。
--
-- ----------------------------------------------------------------------------
-- 【执行方式】达梦 profile 下 Flyway 是关闭的（Flyway 官方无达梦模块，
-- 见 application-dameng.yml 与 docs/decisions/0010），本脚本作为**交付脚本**
-- 在部署时执行一次。辅助脚本：scripts/init-dameng.sh
--
-- 【编号仍用 V1__】不是给 Flyway 看的，是为了三套脚本能按同一条时间线对照：
-- 将来若在达梦上恢复自动迁移（引第三方扩展或自研 DatabaseType），
-- 文件无需改名挪位，直接就能被 Flyway 接管。
-- ----------------------------------------------------------------------------
--
-- 达梦方言相对 MySQL 的差异（逐条对照见文件末尾）：
--   1. 自增列是 IDENTITY(种子, 增量)，不是 AUTO_INCREMENT
--   2. 列注释必须用独立的 COMMENT ON COLUMN 语句，不支持 inline COMMENT '...'
--   3. 没有 ENGINE / DEFAULT CHARSET / COLLATE 这些表选项，写了直接语法错误
--   4. 标识符不加引号会被自动转成大写存储（与 Oracle 一致）
--   5. VARCHAR 的长度默认按**字节**计，中文场景需要显式声明 CHAR 语义
-- ============================================================================

CREATE TABLE bw_client_event
(
    -- IDENTITY(1, 1) = 从 1 开始、每次 +1。达梦的自增列只支持 INT / BIGINT 两种类型。
    id            BIGINT IDENTITY(1, 1) NOT NULL,

    -- ------------------------------------------------------------------------
    -- 长度一律显式写 CHAR（按字符计）。
    --
    -- 达梦的 VARCHAR(n) 默认按**字节**算，而 Java 侧的长度校验（ClientEventService
    -- 的 truncate）是按**字符**算的。两者不一致会造成一类很难查的故障：
    -- 一个 200 字的 route 在 Java 看来完全合法（上限 512 字符），
    -- 但按 UTF-8 每汉字 3 字节算是 600 字节，塞进 VARCHAR(512 字节) 会直接插入失败，
    -- 且报错只提"数据超长"，不会告诉你差在字符与字节的换算上。
    -- 显式声明 CHAR 后，两端口径一致。
    --
    -- 若目标实例不支持该语法，退路是把长度乘以 3（UTF-8 单字符最大字节数），
    -- 或把实例参数 LENGTH_IN_CHAR 设为 1 让 VARCHAR 默认按字符。
    -- 这两条都**未经实测**，见"未验证清单"。
    -- ------------------------------------------------------------------------
    event_id      VARCHAR(64 CHAR),
    event_name    VARCHAR(128 CHAR) NOT NULL,
    app           VARCHAR(64 CHAR),
    user_id       VARCHAR(64 CHAR),
    device_id     VARCHAR(64 CHAR),
    session_id    VARCHAR(64 CHAR),
    route         VARCHAR(512 CHAR),

    -- 列名与 h2 / mysql 两份脚本一致（app_release 而非 release）。
    -- 有意思的是原因恰好相反：RELEASE 是 MySQL 8 的保留字，达梦却不认 ——
    -- 所以这个库名在达梦上本来是合法的，改名是为了让三套脚本同构。
    app_release   VARCHAR(64 CHAR),

    -- TEXT 在达梦中等价于 CLOB，最大 100G-1 字节。事件属性用它：
    -- 结构千变万化，拆子表既无查询收益又会让写入放大数倍。
    props_json    TEXT,

    props_version INT,

    client_time   BIGINT,
    server_time   BIGINT NOT NULL,
    ip            VARCHAR(64 CHAR),

    -- 命名约束而非匿名：将来出错时，报错信息里能直接看到是哪个约束，
    -- 而匿名约束只会给一个系统生成的代号。
    CONSTRAINT pk_bw_client_event PRIMARY KEY (id)
);

-- ----------------------------------------------------------------------------
-- 索引：与另外两份脚本保持同一组，覆盖"最常用的过滤组合"。
-- 不追求覆盖所有列 —— 索引越多写入越慢，而事件是写多读少的场景。
--
-- DESC 的说明：MySQL 8 与 H2 支持真正的降序索引。达梦此处**未经实测**（见未验证
-- 清单）。若目标版本不接受 DESC 语法，删掉 DESC 即可 —— 这些索引的 server_time
-- 都处在等值列之后（或本身就是单列），B 树可以反向扫描，去掉 DESC 后上述查询
-- 的索引可用性不变，只是索引的物理有序方向反转。
-- ----------------------------------------------------------------------------
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
-- 唯一性若确实需要，位置应在**消费侧**（按 event_id 做幂等），而不是写入侧的硬约束。
CREATE INDEX idx_bw_client_event_event_id ON bw_client_event (event_id);

-- ----------------------------------------------------------------------------
-- 注释：达梦不支持 MySQL 建表语句里的 inline COMMENT，必须拆成独立语句。
-- 少了这段不影响建表，但会让库变成"没有文档的库"—— 直接查表的后来者
-- 只能靠列名猜语义，比如 props 到底是属性还是展示参数。
-- ----------------------------------------------------------------------------
COMMENT ON TABLE bw_client_event IS '客户端行为事件，append-only';

COMMENT ON COLUMN bw_client_event.id IS '主键';
COMMENT ON COLUMN bw_client_event.event_id IS '客户端生成的事件唯一 id（非唯一索引，见索引注释）';
COMMENT ON COLUMN bw_client_event.event_name IS '事件名，小写点分至少两段，如 order.submit';
COMMENT ON COLUMN bw_client_event.app IS '上报方应用标识';
COMMENT ON COLUMN bw_client_event.user_id IS '登录用户标识，未登录为空';
COMMENT ON COLUMN bw_client_event.device_id IS '匿名设备标识：登录前归因、登录后缝合身份';
COMMENT ON COLUMN bw_client_event.session_id IS '会话标识';
COMMENT ON COLUMN bw_client_event.route IS '触发事件时的路由';
COMMENT ON COLUMN bw_client_event.app_release IS '发布版本号';
COMMENT ON COLUMN bw_client_event.props_json IS '事件属性（JSON 对象的字符串形式）';
COMMENT ON COLUMN bw_client_event.props_version IS '事件属性结构版本，props 结构变化必须递增';
COMMENT ON COLUMN bw_client_event.client_time IS '客户端时间戳（毫秒）。不可信，仅供展示';
COMMENT ON COLUMN bw_client_event.server_time IS '服务端接收时间戳（毫秒）：排序/分页/时间窗的唯一依据';
COMMENT ON COLUMN bw_client_event.ip IS '上报方 IP。属个人信息，生产需评估脱敏与保留期';

-- ============================================================================
-- 保留字核对结果（本表在 DM8 上无冲突，记录依据以备后续加列时复查）
--
-- 达梦的关键字/保留字可从系统视图 V$RESERVED_WORDS 查：
--   SELECT KEYWORD, RESERVED, RES_SQL, RES_FIXED FROM V$RESERVED_WORDS;
-- RESERVED = 'Y' 的才不能直接用作标识符（不加双引号就报语法错误）。
--
-- 逐项核对（对照 DM8 关键字列表）：
--   表名 bw_client_event、5 个索引名、约束名 pk_bw_client_event —— 均不在关键字表中
--   14 个列名中需要留意的是 USER_ID 里含关键字 USER，但列名整体是 user_id，
--     并非单独的保留字，可以合法用作列名。（对照日志表的 LEVEL：同样只是关键字而非保留字。）
--     仍建议留意：达梦版本升级会新增关键字（8.1.3 升 8.1.4 就新增了 78 个），
--     将来加列前请重跑上面那句查询，而不是凭印象判断。
--   反面例子：RELEASE 是 MySQL 8 的保留字、达梦却完全不认 —— 保留字风险是按库各算的，
--     在一个库上跑通不能证明在另一个库上安全。
-- ============================================================================

-- ============================================================================
-- 方言差异对照（相对 db/migration/mysql/V2__create_bw_client_event.sql）
--
--   项目              MySQL 8                         达梦 DM8
--   ---------------   -----------------------------   -----------------------------
--   自增主键          BIGINT NOT NULL AUTO_INCREMENT   BIGINT IDENTITY(1, 1) NOT NULL
--   变长字符串        VARCHAR(64)                     VARCHAR(64 CHAR)
--   长文本            TEXT                            TEXT（等价 CLOB）
--   列注释            COMMENT '...'（inline）          COMMENT ON COLUMN ... IS '...'
--   表选项            ENGINE=InnoDB / CHARSET / COLLATE 无（不支持，写了报错）
--   标识符大小写      受 lower_case_table_names 影响   不加引号一律转大写
--   建表语法差异      无 IF NOT EXISTS（同上）
--
-- 三份脚本都刻意不写 IF NOT EXISTS：Flyway 用 schema_history 保证只执行一次，
-- 达梦这边由部署脚本人工保证。加上 IF NOT EXISTS 只会把"表已存在但结构不对"
-- 这种真问题静默吞掉 —— 那是比建表失败更糟的结果。
--
-- 未在真实实例验证的部分，逐条见 docs/decisions/0010 的"未验证清单"。
-- ============================================================================
