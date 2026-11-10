-- =====================================================================
-- BlueWhale 微服务平台 - MES 应用（whale-mes） Flyway 版本化基线 V1
-- =====================================================================
-- 适用库   : bluewhale_mes（MySQL 8.0+，utf8mb4）
-- 来源     : 由 db/init.sql 转换导出（2026-11-01 阶段 4 铺开）
-- 转换规则 : 1) 跳过 DROP TABLE；2) CREATE TABLE 统一 IF NOT EXISTS；
--            3) INSERT 统一 INSERT IGNORE（种子幂等）；4) 表尾统一 ENGINE/CHARSET/COLLATE。
-- 说明     : db/init.sql 为手工空库初始化入口，历史 migration-*.sql 仅供追溯，
--            二者均不在 spring.flyway.locations 扫描路径内（详见 db/README.md）。
-- =====================================================================

-- =====================================================================
-- BlueWhale 微服务平台 - MES 制造执行系统（whale-mes）数据库初始化脚本
-- =====================================================================
-- 库名       : bluewhale_mes
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成，无自增）、tenant_id、create_time、
--              update_time、deleted（逻辑删除 0/1，与 MyBatis-Plus 配置对齐）
-- 说明       : 表间关联均为逻辑外键（不建物理 FOREIGN KEY），便于微服务独立演进
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 生产工单表
--    code 业务唯一：因逻辑删除保留行，唯一约束按 (tenant_id, code) 建立。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_work_order (
    id          BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code        VARCHAR(64)  NOT NULL            COMMENT '工单编码（租户内唯一）',
    name        VARCHAR(128) NOT NULL            COMMENT '工单名称',
    status      VARCHAR(16)  NOT NULL DEFAULT 'ENABLED'
                                                 COMMENT '状态：ENABLED 启用 / DISABLED 禁用',
    remark      VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, code),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='生产工单表';

-- ---------------------------------------------------------------------
-- 2. 种子数据（便于接口冒烟与前端联调）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO mes_work_order (id, tenant_id, code, name, status, remark)
VALUES
    (1, 1, 'MES-001', '示例工单 A', 'ENABLED', '内置种子'),
    (2, 1, 'MES-002', '示例工单 B', 'DISABLED', '内置种子');

-- ---------------------------------------------------------------------
-- 后端接口日志表（whale-spring-boot-starter ApiLogFilter 采集，结构与 bluewhale_auth.api_log 一致）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS api_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-mes',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='后端接口日志表';
