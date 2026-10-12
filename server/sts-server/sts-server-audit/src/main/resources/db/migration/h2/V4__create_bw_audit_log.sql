-- ============================================================================
-- V4 · 审计日志表（H2 方言）
--
-- 与 db/migration/mysql/V4__create_bw_audit_log.sql 结构一一对应，
-- 差别只在方言（对照见文件末尾）。
--
-- 【为什么是 V4】Flyway 的 locations 是全模块共享的 classpath:db/migration/<vendor>，
-- 版本号**全仓唯一**：日志域 V1、行为事件域 V2、权限域 V3，审计域接 V4。
-- 新增建表脚本请取全仓当前最大号 + 1 —— 各自从 V1 起编号会让 Flyway 在
-- **应用启动阶段**直接报 "Found more than one migration with version 1"。
-- 详见 docs/decisions/0009。
--
-- 【这张表与另两张的本质差别：它不由前端写】
--   bw_client_log / bw_client_event 都是"前端产生 → 服务端落库"，因此各有一个
--   上报接口、有客户端自报的时间戳、要防客户端时钟不可信。
--   bw_audit_log 由**后端拦截器在处理真实请求时自动写下**：
--     · 没有上报接口 —— 一个"接受写入审计"的入口就等于给伪造审计开了门；
--     · 只有服务端时间 —— 时间戳的来源只有一个，双时间戳在这里没有意义；
--     · append-only 且**没有逻辑删除** —— 可删的历史不是历史。
--
-- 【为什么不需要 create_by / update_by 这一套审计列】
--   本仓的业务表统一带 create_by / create_time / update_by / update_time / deleted。
--   审计记录自己**就是**审计，再套一层审计字段是同一事实的两个副本，
--   而且会误导后来者以为它可以被"更新"或"删除"。
--   "谁写的"由 operator_id 承担，"什么时候写的"由 server_time 承担。
-- ============================================================================

CREATE TABLE bw_audit_log
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- 动作标识，形如 auth.user.create。由 @Audited 声明，是**永久契约**：
    -- 一旦有记录落库，看板/告警/合规报表就按它查询，改名等于把历史数据劈成两半。
    action        VARCHAR(64)  NOT NULL,

    -- 被操作资源标识，与权限码同形（auth:user）。与 action 分列不是冗余：
    -- 前者回答"涉及用户权限的操作有哪些"，后者回答"用户被删过几次"。
    resource      VARCHAR(128) NOT NULL,

    -- 操作对象的主键或业务编码。由业务代码通过 AuditContext.target(...) 补充 ——
    -- 新建时主键是自增出来的，只有业务代码跑完才知道。
    target_id     VARCHAR(64),

    -- 操作人。**只有已认证的请求才会产生审计**，因此两列都不为空：
    -- 匿名请求记不下"谁做的"，而公网扫描探测会灌满这张表、淹没真正的管理操作。
    -- "已登录但无权限"不在此列 —— 那种情况有明确的操作人，必须记（而且很有价值）。
    operator_id   BIGINT       NOT NULL,
    operator_name VARCHAR(64)  NOT NULL,   -- 取自令牌的登录名，**不查库**，见 AuditInterceptor
    tenant_id     BIGINT,

    -- 结果只有两个取值：SUCCESS / FAILURE。
    -- 刻意不做三档（成功/业务拒绝/系统异常）：后两者在审计语境里是同一件事
    -- ——这次操作没有生效；细分由 error_code 承担，它本来就是分好类的错误码。
    result        VARCHAR(16)  NOT NULL,

    -- 失败时的错误码与原因。**这里的值可能来自"业务失败但 HTTP 200"的响应**：
    -- 本仓约定业务结果只由响应体里的 code 表达，因此拦截器读的是
    -- GlobalExceptionHandler 留下的标记，而不是 HTTP 状态码 ——
    -- 否则"越权被拒"会被记成"某人成功执行了操作"。
    error_code    INT,
    error_message VARCHAR(512),

    http_method   VARCHAR(16)  NOT NULL,
    request_uri   VARCHAR(512) NOT NULL,   -- 不含查询串：查询串里可能带口令、令牌等敏感值
    ip            VARCHAR(64),             -- 属个人信息，生产需评估脱敏与保留期
    user_agent    VARCHAR(512),

    -- 链路标识，取自请求头 X-Trace-Id（前端 common-tools/http 注入，见 ADR-0004 第 6 条）。
    -- 本域**只读取、不生成**：后端自己造一个 id 串不起任何东西。
    trace_id      VARCHAR(64),

    duration_ms   BIGINT,                  -- 从进入拦截器到请求结束

    -- 服务端记录时间（毫秒）。排序/分页/时间窗的**唯一依据**，且只有一个来源 ——
    -- 与另两条管道的双时间戳不同，审计不由客户端产生。
    server_time   BIGINT       NOT NULL
);

-- 索引按"最常用的过滤组合"建，覆盖审计最常被问的四类问题：
--   某段时间发生了什么 / 某类动作 / 某个人做过什么 / 某条数据被谁动过
-- 不追求覆盖所有列 —— 索引越多写入越慢，而审计是写多读少的场景。
CREATE INDEX idx_bw_audit_log_server_time ON bw_audit_log (server_time DESC);

CREATE INDEX idx_bw_audit_log_action_time ON bw_audit_log (action, server_time DESC);

CREATE INDEX idx_bw_audit_log_operator_time ON bw_audit_log (operator_id, server_time DESC);

CREATE INDEX idx_bw_audit_log_target_time ON bw_audit_log (target_id, server_time DESC);

CREATE INDEX idx_bw_audit_log_resource_time ON bw_audit_log (resource, server_time DESC);

-- 刻意**没有** result 索引：它的取值只有两个，选择性极低（一次查询能命中一半的表），
-- 数据库通常不会选它，维护成本却是每次写入都要更新的 B 树。
-- 真要按"失败"过滤，组合条件（如 action + result）走上面的 action 索引反而更快。
