-- ============================================================================
-- V4 · 审计日志表（达梦 DM8 方言）
--
-- 与下面两份脚本结构一一对应，差别只在方言：
--   db/migration/h2/V4__create_bw_audit_log.sql
--   db/migration/mysql/V4__create_bw_audit_log.sql
-- 三份的**列名必须完全一致** —— 列名一旦不同，Java 侧的映射就得跟着分叉。
--
-- ----------------------------------------------------------------------------
-- 【执行方式】达梦 profile 下 Flyway 是关闭的（Flyway 官方无达梦模块，
-- 见 application-dameng.yml 与 docs/decisions/0010），本脚本作为**交付脚本**
-- 在部署时执行一次。辅助脚本：scripts/init-dameng.sh
--
-- 【编号仍用 V4__】不是给 Flyway 看的，是为了三套脚本能按同一条时间线对照：
-- 将来若在达梦上恢复自动迁移，文件无需改名挪位就能被接管。
-- ----------------------------------------------------------------------------
--
-- 达梦方言相对 MySQL 的差异：
--   1. 自增列是 IDENTITY(种子, 增量)，不是 AUTO_INCREMENT
--   2. 列注释必须用独立的 COMMENT ON COLUMN 语句，不支持 inline COMMENT '...'
--   3. 没有 ENGINE / DEFAULT CHARSET / COLLATE 这些表选项，写了直接语法错误
--   4. 标识符不加引号会被自动转成大写存储（与 Oracle 一致）
--   5. VARCHAR 的长度默认按**字节**计，中文场景需要显式声明 CHAR 语义
-- ============================================================================

CREATE TABLE bw_audit_log
(
    id            BIGINT IDENTITY(1, 1) NOT NULL,

    -- ------------------------------------------------------------------------
    -- 长度一律显式写 CHAR（按字符计）。
    --
    -- 达梦的 VARCHAR(n) 默认按**字节**算，而 Java 侧（AuditInterceptor 的 truncate）
    -- 是按**字符**算的。两者不一致会造成一类很难查的故障：一个 64 字符的登录名
    -- 在 Java 看来完全合法，按 UTF-8 每汉字 3 字节算是 192 字节，塞进
    -- VARCHAR(64 字节) 会直接插入失败，而报错只提"数据超长"。
    -- 显式声明 CHAR 后，两端口径一致。
    -- ------------------------------------------------------------------------
    action        VARCHAR(64 CHAR)  NOT NULL,
    resource      VARCHAR(128 CHAR) NOT NULL,
    target_id     VARCHAR(64 CHAR),
    operator_id   BIGINT            NOT NULL,
    operator_name VARCHAR(64 CHAR)  NOT NULL,
    tenant_id     BIGINT,

    result        VARCHAR(16 CHAR)  NOT NULL,
    error_code    INT,
    error_message VARCHAR(512 CHAR),

    http_method   VARCHAR(16 CHAR)  NOT NULL,
    request_uri   VARCHAR(512 CHAR) NOT NULL,
    ip            VARCHAR(64 CHAR),
    user_agent    VARCHAR(512 CHAR),
    trace_id      VARCHAR(64 CHAR),

    duration_ms   BIGINT,

    server_time   BIGINT            NOT NULL,

    -- 命名约束而非匿名：将来出错时，报错信息里能直接看到是哪个约束
    CONSTRAINT pk_bw_audit_log PRIMARY KEY (id)
);

-- ----------------------------------------------------------------------------
-- 索引：与另外两份脚本保持同一组。
--
-- DESC 的说明：MySQL 8 与 H2 支持真正的降序索引。达梦此处**未经实测**（见未验证
-- 清单）。若目标版本不接受 DESC 语法，删掉 DESC 即可 —— 这些索引的 server_time
-- 都处在等值列之后（或本身就是单列），B 树可以反向扫描，去掉 DESC 后上述查询的
-- 索引可用性不变，只是索引的物理有序方向反转。
-- ----------------------------------------------------------------------------
CREATE INDEX idx_bw_audit_log_server_time ON bw_audit_log (server_time DESC);

CREATE INDEX idx_bw_audit_log_action_time ON bw_audit_log (action, server_time DESC);

CREATE INDEX idx_bw_audit_log_operator_time ON bw_audit_log (operator_id, server_time DESC);

CREATE INDEX idx_bw_audit_log_target_time ON bw_audit_log (target_id, server_time DESC);

CREATE INDEX idx_bw_audit_log_resource_time ON bw_audit_log (resource, server_time DESC);

-- ----------------------------------------------------------------------------
-- 注释：达梦不支持 inline COMMENT，必须拆成独立语句。
-- 少了这段不影响建表，但会让库变成"没有文档的库"。
-- ----------------------------------------------------------------------------
COMMENT ON TABLE bw_audit_log IS '审计日志，由后端拦截器自动产生，append-only，不可改亦不可删';

COMMENT ON COLUMN bw_audit_log.id IS '主键';
COMMENT ON COLUMN bw_audit_log.action IS '动作标识，形如 auth.user.create；永久契约，改名即历史数据断裂';
COMMENT ON COLUMN bw_audit_log.resource IS '被操作资源标识，形如 auth:user，与权限码同形';
COMMENT ON COLUMN bw_audit_log.target_id IS '操作对象的主键或业务编码';
COMMENT ON COLUMN bw_audit_log.operator_id IS '操作人用户主键（只有已认证的请求才会产生审计）';
COMMENT ON COLUMN bw_audit_log.operator_name IS '操作人登录名，取自令牌，不查库';
COMMENT ON COLUMN bw_audit_log.tenant_id IS '操作人所属租户主键';
COMMENT ON COLUMN bw_audit_log.result IS '结果：SUCCESS / FAILURE。细分由 error_code 承担';
COMMENT ON COLUMN bw_audit_log.error_code IS '失败时的业务错误码（可能来自"业务失败但 HTTP 200"的响应）';
COMMENT ON COLUMN bw_audit_log.error_message IS '失败原因（对外返回的那句提示，不含内部异常信息）';
COMMENT ON COLUMN bw_audit_log.http_method IS 'HTTP 方法';
COMMENT ON COLUMN bw_audit_log.request_uri IS '请求路径（不含查询串，查询串可能带敏感值）';
COMMENT ON COLUMN bw_audit_log.ip IS '来源 IP。属个人信息，生产需评估脱敏与保留期';
COMMENT ON COLUMN bw_audit_log.user_agent IS 'User-Agent';
COMMENT ON COLUMN bw_audit_log.trace_id IS '链路标识，取自请求头 X-Trace-Id';
COMMENT ON COLUMN bw_audit_log.duration_ms IS '耗时（毫秒）';
COMMENT ON COLUMN bw_audit_log.server_time IS '服务端记录时间（毫秒）：排序/分页/时间窗的唯一依据';

-- ============================================================================
-- 保留字核对（**本表在达梦上未实测**，以下是需要复查的项，不要当成结论）
--
-- 达梦的关键字/保留字可从系统视图 V$RESERVED_WORDS 查：
--   SELECT KEYWORD, RESERVED, RES_SQL, RES_FIXED FROM V$RESERVED_WORDS;
-- RESERVED = 'Y' 的才不能直接用作标识符（不加双引号就报语法错误）。
--
-- 需要重点复查的列名（凭印象判断过的，必须在目标实例上验证）：
--   result / action / resource —— 这三个在其它数据库里都是"非保留关键字"，
--   但**保留字风险是按库各算的**，不能互推：RELEASE 是 MySQL 8 的保留字、
--   达梦却完全不认；反过来 LEVEL 是达梦的关键字（非保留）而 MySQL 也不拦。
--   本仓已经因为这件事吃过一次亏（见 ADR-0009 第 3 条），所以这里如实标注未验证，
--   而不是照抄 MySQL 版的结论。
--   完整的未验证清单见 docs/decisions/0010。
-- ============================================================================
