-- ============================================================================
-- V1 · 客户端日志表（达梦 DM8 方言）
--
-- 与下面两份脚本结构一一对应，差别只在方言：
--   db/migration/h2/V1__create_bw_client_log.sql
--   db/migration/mysql/V1__create_bw_client_log.sql
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

CREATE TABLE bw_client_log
(
    -- IDENTITY(1, 1) = 从 1 开始、每次 +1。达梦的自增列只支持 INT / BIGINT 两种类型。
    id           BIGINT IDENTITY(1, 1) NOT NULL,

    -- ------------------------------------------------------------------------
    -- 长度一律显式写 CHAR（按字符计）。
    --
    -- 达梦的 VARCHAR(n) 默认按**字节**算，而 Java 侧的长度校验（ClientLogService
    -- 的 truncate）是按**字符**算的。两者不一致会造成一类很难查的故障：
    -- 一个 200 字的 namespace 在 Java 看来完全合法（上限 255 字符），
    -- 但按 UTF-8 每汉字 3 字节算是 600 字节，塞进 VARCHAR(255 字节) 会直接插入失败，
    -- 且报错只提"数据超长"，不会告诉你差在字符与字节的换算上。
    -- 显式声明 CHAR 后，两端口径一致。
    --
    -- 若目标实例不支持该语法，退路是把长度乘以 3（UTF-8 单字符最大字节数），
    -- 或把实例参数 LENGTH_IN_CHAR 设为 1 让 VARCHAR 默认按字符。
    -- 这两条都**未经实测**，见"未验证清单"。
    -- ------------------------------------------------------------------------
    app          VARCHAR(64 CHAR),
    level        VARCHAR(16 CHAR) NOT NULL,
    namespace    VARCHAR(255 CHAR),

    -- TEXT 在达梦中等价于 CLOB，最大 100G-1 字节。日志正文与两个 JSON 字段
    -- 都用它：结构千变万化，拆子表既无查询收益又会让写入放大数倍。
    message      TEXT,
    data_json    TEXT,
    context_json TEXT,

    trace_id     VARCHAR(64 CHAR),
    session_id   VARCHAR(64 CHAR),
    route        VARCHAR(512 CHAR),

    -- 列名与 h2 / mysql 两份脚本一致（app_release 而非 release）。
    -- 有意思的是原因恰好相反：RELEASE 是 MySQL 8 的保留字，达梦却不认 ——
    -- 所以这个库名在达梦上本来是合法的，改名是为了让三套脚本同构。
    app_release  VARCHAR(64 CHAR),

    client_time  BIGINT,
    server_time  BIGINT NOT NULL,
    ip           VARCHAR(64 CHAR),

    -- 命名约束而非匿名：将来出错时，报错信息里能直接看到是哪个约束，
    -- 而匿名约束只会给一个系统生成的代号。
    CONSTRAINT pk_bw_client_log PRIMARY KEY (id)
);

-- ----------------------------------------------------------------------------
-- 索引：与另外两份脚本保持同一组，覆盖"最常用的过滤组合"。
-- 不追求覆盖所有列 —— 索引越多写入越慢，而日志是写多读少的场景。
--
-- DESC 的说明：MySQL 8 与 H2 支持真正的降序索引。达梦此处**未经实测**（见未验证
-- 清单）。若目标版本不接受 DESC 语法，删掉 DESC 即可 —— 这些索引的 server_time
-- 都处在等值列之后（或本身就是单列），B 树可以反向扫描，去掉 DESC 后上述查询
-- 的索引可用性不变，只是索引的物理有序方向反转。
-- ----------------------------------------------------------------------------
CREATE INDEX idx_bw_client_log_server_time ON bw_client_log (server_time DESC);

CREATE INDEX idx_bw_client_log_level_time ON bw_client_log (level, server_time DESC);

CREATE INDEX idx_bw_client_log_trace_id ON bw_client_log (trace_id);

CREATE INDEX idx_bw_client_log_app_time ON bw_client_log (app, server_time DESC);

-- ----------------------------------------------------------------------------
-- 注释：达梦不支持 MySQL 建表语句里的 inline COMMENT，必须拆成独立语句。
-- 少了这段不影响建表，但会让库变成"没有文档的库"—— 直接查表的后来者
-- 只能靠列名猜语义，比如 ctx 到底是上下文还是内容。
-- ----------------------------------------------------------------------------
COMMENT ON TABLE bw_client_log IS '客户端技术日志，append-only';

COMMENT ON COLUMN bw_client_log.id IS '主键';
COMMENT ON COLUMN bw_client_log.app IS '上报方应用标识，如 platform-log-console';
COMMENT ON COLUMN bw_client_log.level IS '级别小写：trace/debug/perf/info/success/warn/error';
COMMENT ON COLUMN bw_client_log.namespace IS '命名空间，形如 app > auth > login';
COMMENT ON COLUMN bw_client_log.message IS '日志正文';
COMMENT ON COLUMN bw_client_log.data_json IS '随消息附带的原始数据（JSON 数组的字符串形式）';
COMMENT ON COLUMN bw_client_log.context_json IS '结构化上下文（JSON 对象的字符串形式）';
COMMENT ON COLUMN bw_client_log.trace_id IS '链路标识：跨端串联同一次请求的全部日志';
COMMENT ON COLUMN bw_client_log.session_id IS '会话标识';
COMMENT ON COLUMN bw_client_log.route IS '触发日志时的路由';
COMMENT ON COLUMN bw_client_log.app_release IS '发布版本号';
COMMENT ON COLUMN bw_client_log.client_time IS '客户端时间戳（毫秒）。不可信，仅供展示';
COMMENT ON COLUMN bw_client_log.server_time IS '服务端接收时间戳（毫秒）：排序/分页/时间窗的唯一依据';
COMMENT ON COLUMN bw_client_log.ip IS '上报方 IP。属个人信息，生产需评估脱敏与保留期';

-- ============================================================================
-- 保留字核对结果（本表在 DM8 上无冲突，记录依据以备后续加列时复查）
--
-- 达梦的关键字/保留字可从系统视图 V$RESERVED_WORDS 查：
--   SELECT KEYWORD, RESERVED, RES_SQL, RES_FIXED FROM V$RESERVED_WORDS;
-- RESERVED = 'Y' 的才不能直接用作标识符（不加双引号就报语法错误）。
--
-- 逐项核对（对照 DM8 关键字列表）：
--   表名 bw_client_log、4 个索引名、约束名 pk_bw_client_log —— 均不在关键字表中
--   14 个列名中唯一出现在达梦关键字表里的是 LEVEL ——
--     但它是**关键字而非保留字**（达梦关键字表中 LEVEL 未标记为保留），可以合法用作列名。
--     仍建议留意：达梦版本升级会新增关键字（8.1.3 升 8.1.4 就新增了 78 个），
--     将来加列前请重跑上面那句查询，而不是凭印象判断。
--   反面例子：RELEASE 是 MySQL 8 的保留字、达梦却完全不认 —— 保留字风险是按库各算的，
--     在一个库上跑通不能证明在另一个库上安全。
-- ============================================================================

-- ============================================================================
-- 方言差异对照（相对 db/migration/mysql/V1__create_bw_client_log.sql）
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
