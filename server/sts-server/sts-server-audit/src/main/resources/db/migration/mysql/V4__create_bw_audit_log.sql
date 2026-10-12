-- ============================================================================
-- V4 · 审计日志表（MySQL 8 方言）
--
-- 与 db/migration/h2/V4__create_bw_audit_log.sql 结构一一对应，
-- 差别只在方言，逐条对照见文件末尾。列名三份必须**完全一致** ——
-- 列名一旦分叉，Java 侧的映射就得跟着分叉。
--
-- 版本号取全仓最大号 +1（日志域 V1、行为事件域 V2、权限域 V3），详见 docs/decisions/0009。
--
-- ============================================================================
-- 【保留字核对（本表最要紧的一步）】
--
-- 本仓已经因为列名撞保留字踩过一次：日志与事件表的发布版本号列不能叫 release
-- （RELEASE 是 MySQL 8 保留字，见 RELEASE SAVEPOINT），改名 app_release 才建得出表。
-- 那次教训在 ADR-0009 里记着，这里逐列复查一遍：
--
--   action        非保留关键字（用于 ON DELETE NO ACTION 等动作短语）
--   resource      非保留关键字（MySQL 8 的 RESOURCE GROUP 里出现）
--   result        非保留
--   target_id     两词组合，安全
--   error_code / error_message / http_method / request_uri / user_agent / duration_ms
--                 均不在关键字表中
--   ip            不在关键字表中
--   trace_id      不在关键字表中
--   server_time   不在关键字表中
--
-- 结论：本表在 MySQL 8 上无冲突。**但不要凭这份注释代替实测** ——
-- 建表语句会被 Flyway 在启动时真实执行，语法错误会当场暴露，而不是留到运行期。
-- ============================================================================

CREATE TABLE bw_audit_log
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',

    action        VARCHAR(64)  NOT NULL COMMENT '动作标识，形如 auth.user.create；永久契约，改名即历史数据断裂',
    resource      VARCHAR(128) NOT NULL COMMENT '被操作资源标识，形如 auth:user，与权限码同形',
    target_id     VARCHAR(64)           DEFAULT NULL COMMENT '操作对象的主键或业务编码',
    operator_id   BIGINT       NOT NULL COMMENT '操作人用户主键（只有已认证的请求才会产生审计）',
    operator_name VARCHAR(64)  NOT NULL COMMENT '操作人登录名，取自令牌，不查库',
    tenant_id     BIGINT                DEFAULT NULL COMMENT '操作人所属租户主键',

    result        VARCHAR(16)  NOT NULL COMMENT '结果：SUCCESS / FAILURE。细分由 error_code 承担',

    -- 这里存的可能是"业务失败但 HTTP 200"的错误码：
    -- 本仓约定业务结果只由响应体里的 code 表达，所以拦截器读的是 GlobalExceptionHandler
    -- 留下的标记而不是 HTTP 状态码 —— 否则"越权被拒"会被记成"某人成功执行了操作"。
    error_code    INT                   DEFAULT NULL COMMENT '失败时的业务错误码',
    error_message VARCHAR(512)          DEFAULT NULL COMMENT '失败原因（对外返回的那句提示，不含内部异常信息）',

    http_method   VARCHAR(16)  NOT NULL COMMENT 'HTTP 方法',
    request_uri   VARCHAR(512) NOT NULL COMMENT '请求路径（不含查询串，查询串可能带敏感值）',
    ip            VARCHAR(64)           DEFAULT NULL COMMENT '来源 IP。属个人信息，生产需评估脱敏与保留期',
    user_agent    VARCHAR(512)          DEFAULT NULL COMMENT 'User-Agent',
    trace_id      VARCHAR(64)           DEFAULT NULL COMMENT '链路标识，取自请求头 X-Trace-Id',

    duration_ms   BIGINT                DEFAULT NULL COMMENT '耗时（毫秒）',

    -- 只有一个时间戳，且来自服务端：审计不由客户端产生，不存在"客户端时钟不可信"的问题。
    server_time   BIGINT       NOT NULL COMMENT '服务端记录时间（毫秒）：排序/分页/时间窗的唯一依据',

    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '审计日志，append-only，不可改亦不可删';

-- 索引覆盖审计最常被问的四类问题：
--   某段时间发生了什么 / 某类动作 / 某个人做过什么 / 某条数据被谁动过
CREATE INDEX idx_bw_audit_log_server_time ON bw_audit_log (server_time DESC);

CREATE INDEX idx_bw_audit_log_action_time ON bw_audit_log (action, server_time DESC);

CREATE INDEX idx_bw_audit_log_operator_time ON bw_audit_log (operator_id, server_time DESC);

CREATE INDEX idx_bw_audit_log_target_time ON bw_audit_log (target_id, server_time DESC);

CREATE INDEX idx_bw_audit_log_resource_time ON bw_audit_log (resource, server_time DESC);

-- 刻意**没有** result 索引：取值只有两个，选择性极低（一次查询命中近一半表），
-- 优化器通常不会选它，而每次写入都要维护这棵 B 树。真要按失败过滤，
-- 组合条件（action + result）走 action 索引更快。

-- ============================================================================
-- 方言差异（相对 h2/V4__create_bw_audit_log.sql）
--   1. 列注释用 inline COMMENT '...'，H2 写在同一位置亦可（但不带存储引擎）
--   2. 表尾带 ENGINE / CHARSET / COLLATE —— H2 无此概念
--   3. utf8mb4 + utf8mb4_0900_ai_ci：中文与 emoji 必须 utf8mb4，
--      建库时已指定，这里显式重申一次，避免落到别的默认字符集上
--   4. 显式写 NOT NULL / DEFAULT NULL：H2 与 MySQL 对"未声明时是否允许为空"
--      的默认行为不完全一致，写清楚可以免掉一类只在其中一个库上出现的差异
--   5. DDL 里不写 IF NOT EXISTS：Flyway 用 schema_history 表保证"只执行一次"，
--      再加 IF NOT EXISTS 反而会把"表已存在但结构不对"这种真问题静默吞掉
-- ============================================================================
