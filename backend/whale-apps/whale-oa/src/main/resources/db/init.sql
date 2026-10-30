-- =====================================================================
-- BlueWhale 微服务平台 - OA 办公自动化（whale-oa）数据库初始化脚本
-- =====================================================================
-- 库名       : bluewhale_oa
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成，无自增）、tenant_id、create_time、
--              update_time、deleted（逻辑删除 0/1，与 MyBatis-Plus 配置对齐）
-- 说明       : 表间关联均为逻辑外键（不建物理 FOREIGN KEY），便于微服务独立演进
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 请假申请表（REQ-FUNC-017）
--    code 业务唯一：因逻辑删除保留行，唯一约束按 (tenant_id, code) 建立。
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS oa_leave;
CREATE TABLE oa_leave (
    id          BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code        VARCHAR(64)  NOT NULL            COMMENT '申请单号（租户内唯一），如 OA-LV-202610-001',
    applicant   VARCHAR(64)  NOT NULL            COMMENT '申请人账号',
    leave_type  VARCHAR(16)  NOT NULL            COMMENT '请假类型：ANNUAL 年假 / SICK 病假 / PERSONAL 事假',
    start_date  DATE         NOT NULL            COMMENT '开始日期',
    end_date    DATE         NOT NULL            COMMENT '结束日期',
    days        DECIMAL(5,1) NOT NULL            COMMENT '请假天数（0.5 步进）',
    reason      VARCHAR(512) DEFAULT NULL        COMMENT '请假事由',
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                                                 COMMENT '状态：PENDING 待审批 / APPROVED 已通过 / REJECTED 已驳回 / CANCELLED 已撤销',
    remark      VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, code),
    KEY idx_tenant_applicant (tenant_id, applicant),
    KEY idx_status (status)
) COMMENT='请假申请表';

-- ---------------------------------------------------------------------
-- 2. 种子数据（便于接口冒烟与前端联调）
-- ---------------------------------------------------------------------
INSERT INTO oa_leave (id, tenant_id, code, applicant, leave_type, start_date, end_date,
                      days, reason, status, remark)
VALUES
    (1, 1, 'OA-LV-202610-001', 'admin', 'ANNUAL', '2026-10-26', '2026-10-27',
     2.0, '国庆调休后年假', 'PENDING', '内置种子'),
    (2, 1, 'OA-LV-202610-002', 'admin', 'SICK', '2026-10-20', '2026-10-20',
     1.0, '感冒就医', 'APPROVED', '内置种子');

-- ---------------------------------------------------------------------
-- 后端接口日志表（whale-spring-boot-starter ApiLogFilter 采集，结构与 bluewhale_auth.api_log 一致）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS api_log;
CREATE TABLE api_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-auth',
    request_uri    VARCHAR(255)  DEFAULT NULL           COMMENT '请求URI',
    http_method    VARCHAR(16)   DEFAULT NULL           COMMENT 'HTTP 方法：GET/POST/PUT/DELETE',
    query_string   VARCHAR(1024) DEFAULT NULL           COMMENT '查询串（敏感字段已脱敏）',
    request_params TEXT          NULL                   COMMENT '请求体（JSON，敏感字段已脱敏）',
    response_body  TEXT          NULL                   COMMENT '响应体（JSON，敏感字段已脱敏，超限截断）',
    response_code  INT           DEFAULT NULL           COMMENT '响应状态码（HTTP 状态）',
    biz_status     TINYINT       NOT NULL DEFAULT 1     COMMENT '业务结果：1 成功 / 0 失败',
    error_msg      VARCHAR(512)  DEFAULT NULL           COMMENT '失败原因（异常类名 + 消息）',
    ip             VARCHAR(64)   DEFAULT NULL           COMMENT '调用方IP（兼容 IPv6）',
    user_agent     VARCHAR(512)  DEFAULT NULL           COMMENT '用户代理（浏览器/客户端标识）',
    cost_ms        BIGINT        DEFAULT NULL           COMMENT '接口耗时（毫秒）',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_trace_id (trace_id),
    KEY idx_create_time (create_time),
    KEY idx_user_id (user_id),
    KEY idx_tenant_create (tenant_id, create_time),
    KEY idx_biz_status (biz_status)
) COMMENT='后端接口日志表';