-- =====================================================================
-- BlueWhale 客户关系应用（whale-crm）Flyway 基线脚本
-- =====================================================================
-- 库名       : bluewhale_crm（空库启动由 Flyway 自动建表）
-- 数据库版本 : MySQL 8.0+
-- 状态       : 规划态基线。需求门禁（ADR-003）通过前不写入业务数据，
--              仅保留表结构与约束，避免门禁通过后二次评审与返工。
-- 公共字段   : id / tenant_id / create_time / update_time / deleted
-- 租户隔离   : tenant_id 由 starter 的租户行插件自动追加/填充
-- 边界       : 客户主数据归 CRM；供应商归 ERP / 未来 SRM；
--              合同只保存 crmCustomerId 快照，不复制客户主数据。
-- 脚本约束   : 禁止 DROP / TRUNCATE / DELETE；可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 客户（聚合根）
--    status: ACTIVE / INACTIVE / MERGED
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_customer (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    customer_code       VARCHAR(64)   NOT NULL                COMMENT '客户编码（租户内唯一）',
    name                VARCHAR(128)  NOT NULL                COMMENT '客户名称',
    status              VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / INACTIVE / MERGED',
    owner_id            BIGINT        DEFAULT NULL            COMMENT '客户负责人（数据权限归属人）',
    dept_id             BIGINT        DEFAULT NULL            COMMENT '归属部门（数据权限范围）',
    level               VARCHAR(16)   DEFAULT NULL            COMMENT '客户等级：KEY / NORMAL / POTENTIAL',
    source              VARCHAR(32)   DEFAULT NULL            COMMENT '客户来源',
    industry            VARCHAR(64)   DEFAULT NULL            COMMENT '所属行业',
    address             VARCHAR(255)  DEFAULT NULL            COMMENT '联系地址（不含敏感信息）',
    tags                VARCHAR(255)  DEFAULT NULL            COMMENT '标签（逗号分隔）',
    merged_into_id      BIGINT        DEFAULT NULL            COMMENT '合并后指向的目标客户ID（可追溯）',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_customer_code (tenant_id, customer_code),
    KEY idx_crm_customer_owner (tenant_id, owner_id),
    KEY idx_crm_customer_dept (tenant_id, dept_id),
    KEY idx_crm_customer_status (tenant_id, status, update_time)
) COMMENT='客户主数据聚合根';

-- ---------------------------------------------------------------------
-- 2. 联系人（每客户至多一个主联系人，由服务层校验）
--    手机号 / 邮箱按脱敏规则输出与审计，表中保存原值但不进入日志
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_contact (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    customer_id         BIGINT        NOT NULL                COMMENT '所属客户ID',
    name                VARCHAR(64)   NOT NULL                COMMENT '联系人姓名',
    phone               VARCHAR(32)   DEFAULT NULL            COMMENT '联系电话（输出脱敏）',
    email               VARCHAR(128)  DEFAULT NULL            COMMENT '邮箱（输出脱敏）',
    title               VARCHAR(64)   DEFAULT NULL            COMMENT '职务',
    is_primary          TINYINT       NOT NULL DEFAULT 0      COMMENT '是否主联系人：1 是 / 0 否',
    status              VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / INACTIVE',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_crm_contact_customer (tenant_id, customer_id, is_primary),
    KEY idx_crm_contact_phone (tenant_id, phone)
) COMMENT='客户联系人';

-- ---------------------------------------------------------------------
-- 3. 商机（阶段状态机：LEAD→QUALIFIED→PROPOSAL→NEGOTIATION→WON/LOST）
--    LOST 必须填写 lost_reason；WON 必须有金额与客户
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_opportunity (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    customer_id         BIGINT        NOT NULL                COMMENT '所属客户ID',
    name                VARCHAR(128)  NOT NULL                COMMENT '商机名称',
    amount              DECIMAL(18,2) DEFAULT NULL            COMMENT '预计金额',
    stage               VARCHAR(16)   NOT NULL DEFAULT 'LEAD' COMMENT '阶段：LEAD / QUALIFIED / PROPOSAL / NEGOTIATION / WON / LOST',
    expected_close_date DATE          DEFAULT NULL            COMMENT '预计关闭日期',
    owner_id            BIGINT        DEFAULT NULL            COMMENT '商机负责人',
    dept_id             BIGINT        DEFAULT NULL            COMMENT '归属部门',
    lost_reason         VARCHAR(255)  DEFAULT NULL            COMMENT '输单原因（LOST 必填）',
    closed_at           DATETIME      DEFAULT NULL            COMMENT '关闭时间（WON / LOST）',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_crm_opportunity_customer (tenant_id, customer_id, stage),
    KEY idx_crm_opportunity_owner (tenant_id, owner_id),
    KEY idx_crm_opportunity_close (tenant_id, expected_close_date)
) COMMENT='销售商机';

-- ---------------------------------------------------------------------
-- 4. 跟进记录（append-only，不提供修改/删除接口）
--    channel: CALL / VISIT / EMAIL / IM / OTHER
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_follow_up (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    customer_id         BIGINT        NOT NULL                COMMENT '所属客户ID',
    opportunity_id      BIGINT        DEFAULT NULL            COMMENT '关联商机ID（可空）',
    channel             VARCHAR(16)   NOT NULL                COMMENT '跟进渠道：CALL / VISIT / EMAIL / IM / OTHER',
    content             VARCHAR(1000) NOT NULL                COMMENT '跟进内容（不得记录敏感信息）',
    next_action_at      DATETIME      DEFAULT NULL            COMMENT '下次跟进时间',
    created_by          BIGINT        DEFAULT NULL            COMMENT '记录人',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_crm_follow_customer (tenant_id, customer_id, create_time),
    KEY idx_crm_follow_opportunity (tenant_id, opportunity_id)
) COMMENT='客户跟进记录（append-only）';

-- ---------------------------------------------------------------------
-- 5. 操作审计日志表（whale-spring-boot-starter @AuditLog 切面采集）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名（冗余存储，用户删除后仍可追溯）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-crm',
    operation      VARCHAR(128)  DEFAULT NULL           COMMENT '操作描述',
    operate_type   VARCHAR(32)   DEFAULT NULL           COMMENT '操作类型：CREATE/UPDATE/DELETE/QUERY/OTHER 等',
    method         VARCHAR(255)  DEFAULT NULL           COMMENT '请求方法（全限定类名.方法名）',
    request_uri    VARCHAR(255)  DEFAULT NULL           COMMENT '请求URI',
    http_method    VARCHAR(16)   DEFAULT NULL           COMMENT 'HTTP 方法：GET/POST/PUT/DELETE',
    target_type    VARCHAR(64)   DEFAULT NULL           COMMENT '操作对象类型，如 crm_customer',
    target_id      VARCHAR(64)   DEFAULT NULL           COMMENT '操作对象ID',
    request_params TEXT          NULL                   COMMENT '请求参数（JSON，敏感字段已脱敏）',
    result_body    TEXT          NULL                   COMMENT '返回体（JSON，脱敏 + 截断）',
    response_code  INT           DEFAULT NULL           COMMENT '响应状态码',
    biz_status     TINYINT       NOT NULL DEFAULT 1     COMMENT '业务结果：1 成功 / 0 失败',
    error_msg      VARCHAR(512)  DEFAULT NULL           COMMENT '失败原因（异常类名 + 消息，已脱敏）',
    ip             VARCHAR(64)   DEFAULT NULL           COMMENT '操作IP地址（兼容 IPv6）',
    user_agent     VARCHAR(512)  DEFAULT NULL           COMMENT '用户代理（浏览器/客户端标识）',
    cost_ms        BIGINT        DEFAULT NULL           COMMENT '接口耗时（毫秒）',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（即操作发生时间）',
    PRIMARY KEY (id),
    KEY idx_trace_id (trace_id),
    KEY idx_user_id (user_id),
    KEY idx_module (module),
    KEY idx_operate_type (operate_type),
    KEY idx_biz_status (biz_status),
    KEY idx_tenant_time (tenant_id, create_time),
    KEY idx_create_time (create_time)
) COMMENT='操作审计日志表';

-- ---------------------------------------------------------------------
-- 6. 后端接口日志表（whale-spring-boot-starter ApiLogFilter 采集）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS api_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-crm',
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
