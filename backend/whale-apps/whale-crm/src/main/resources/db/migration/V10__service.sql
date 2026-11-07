-- =====================================================================
-- BlueWhale 客户关系应用（whale-crm）Flyway 增量 V10：M4 售后服务
-- =====================================================================
-- 适用库   : bluewhale_crm（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/crm-module-panorama.md
-- 计划     : docs/plans/2026-11-04-wave5.83-crm-panorama.md（M4 / 7f-4）
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) 表间逻辑外键，不建物理 FOREIGN KEY；
--            4) 不写业务种子数据。
-- 说明     : 本批一次建齐 M4 九个表（4a 工单与 SLA / 4b 知识引用与满意度 /
--            4c 退换货），避免后续批次修改已发布迁移。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 工单（4a）
--    priority: P1 / P2 / P3
--    status  : NEW / ASSIGNED / PROCESSING / WAITING_CUSTOMER /
--              RESOLVED / CLOSED / REOPENED / CANCELLED
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_ticket (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    ticket_no             VARCHAR(64)   NOT NULL                COMMENT '工单号（TICKET-yyyyMMdd-xxxx）',
    customer_id           BIGINT        NOT NULL                COMMENT 'CRM 客户ID',
    customer_code         VARCHAR(64)   DEFAULT NULL            COMMENT '客户编码快照',
    customer_name         VARCHAR(255)  DEFAULT NULL            COMMENT '客户名称快照',
    contact_id            BIGINT        DEFAULT NULL            COMMENT '联系人ID',
    contact_name          VARCHAR(128)  DEFAULT NULL            COMMENT '联系人姓名快照',
    contract_id           BIGINT        DEFAULT NULL            COMMENT '合同台账ID',
    order_id              BIGINT        DEFAULT NULL            COMMENT '商务订单ID',
    erp_order_no          VARCHAR(64)   DEFAULT NULL            COMMENT 'ERP 销售订单号快照',
    product_code          VARCHAR(64)   DEFAULT NULL            COMMENT '产品编码',
    serial_no             VARCHAR(128)  DEFAULT NULL            COMMENT '设备序列号',
    category              VARCHAR(64)   DEFAULT NULL            COMMENT '问题分类',
    priority              VARCHAR(8)    NOT NULL DEFAULT 'P3'   COMMENT '优先级：P1 / P2 / P3',
    title                 VARCHAR(255)  NOT NULL                COMMENT '工单标题',
    description           TEXT          DEFAULT NULL            COMMENT '问题描述',
    status                VARCHAR(20)   NOT NULL DEFAULT 'NEW' COMMENT '工单状态',
    assignee_id           BIGINT        DEFAULT NULL            COMMENT '处理人ID',
    assignee_name         VARCHAR(128)  DEFAULT NULL            COMMENT '处理人姓名快照',
    owner_id              BIGINT        DEFAULT NULL            COMMENT '负责人（数据权限归属人）',
    dept_id               BIGINT        DEFAULT NULL            COMMENT '归属部门',
    response_deadline     DATETIME      DEFAULT NULL            COMMENT '响应截止时间（SLA 快照）',
    resolve_deadline      DATETIME      DEFAULT NULL            COMMENT '解决截止时间（SLA 快照）',
    first_responded_at    DATETIME      DEFAULT NULL            COMMENT '首次响应时间',
    paused_at             DATETIME      DEFAULT NULL            COMMENT '暂停开始时间',
    paused_seconds        BIGINT        NOT NULL DEFAULT 0      COMMENT '累计暂停秒数',
    resolved_at           DATETIME      DEFAULT NULL            COMMENT '解决时间',
    closed_at             DATETIME      DEFAULT NULL            COMMENT '关闭时间',
    solution              VARCHAR(2000) DEFAULT NULL            COMMENT '解决方案',
    reopen_count          INT           NOT NULL DEFAULT 0      COMMENT '重开次数',
    lock_version          INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_ticket_no (tenant_id, ticket_no),
    KEY idx_crm_ticket_status (tenant_id, status, priority),
    KEY idx_crm_ticket_customer (tenant_id, customer_id),
    KEY idx_crm_ticket_assignee (tenant_id, assignee_id, status)
) COMMENT='CRM 售后工单';

-- ---------------------------------------------------------------------
-- 2. 工单处理记录（4a，追加式，无更新 / 删除）
--    action_type: CREATE / ASSIGN / PROCESS / COMMENT / PAUSE / RESUME /
--                 ESCALATE / RESOLVE / CLOSE / REOPEN / CANCEL / FOLLOW_UP
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_ticket_action (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    ticket_id             BIGINT        NOT NULL                COMMENT '工单ID',
    action_type           VARCHAR(16)   NOT NULL                COMMENT '动作类型',
    from_status           VARCHAR(20)   DEFAULT NULL            COMMENT '变更前状态',
    to_status             VARCHAR(20)   DEFAULT NULL            COMMENT '变更后状态',
    operator_id           BIGINT        DEFAULT NULL            COMMENT '操作人ID',
    operator_name         VARCHAR(128)  DEFAULT NULL            COMMENT '操作人姓名快照',
    content               VARCHAR(2000) DEFAULT NULL            COMMENT '动作内容',
    created_at            DATETIME      NOT NULL                COMMENT '记录时间',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_crm_ticket_action_ticket (tenant_id, ticket_id, created_at)
) COMMENT='CRM 工单处理记录';

-- ---------------------------------------------------------------------
-- 3. SLA 策略（4a，按优先级三档；无行时用 D7 代码默认值）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_ticket_sla (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    priority              VARCHAR(8)    NOT NULL                COMMENT '优先级：P1 / P2 / P3',
    response_hours        INT           NOT NULL DEFAULT 0      COMMENT '响应时限（工作小时）',
    resolve_hours         INT           NOT NULL DEFAULT 0      COMMENT '解决时限（工作小时）',
    enabled               TINYINT       NOT NULL DEFAULT 1      COMMENT '是否启用：0 否 / 1 是',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_ticket_sla_priority (tenant_id, priority)
) COMMENT='CRM 工单 SLA 策略';

-- ---------------------------------------------------------------------
-- 4. 售后服务工作日历（4a；无行时默认 09:00–18:00 / 60 / 1–7）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_service_calendar (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    calendar_code         VARCHAR(32)   NOT NULL                COMMENT '日历编码',
    workday_start         TIME          NOT NULL                COMMENT '上班时间',
    workday_end           TIME          NOT NULL                COMMENT '下班时间',
    break_minutes         INT           NOT NULL DEFAULT 60     COMMENT '午休分钟数',
    weekdays              VARCHAR(32)   NOT NULL DEFAULT '1,2,3,4,5,6,7' COMMENT '工作日（ISO 1–7，逗号分隔）',
    enabled               TINYINT       NOT NULL DEFAULT 1      COMMENT '是否启用：0 否 / 1 是',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_service_calendar_code (tenant_id, calendar_code)
) COMMENT='CRM 售后服务工作日历';

-- ---------------------------------------------------------------------
-- 5. 知识引用元数据（4b；正文归 whale-doc，CRM 只存引用与命中）
--    visible_scope: PUBLIC / INTERNAL
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_kb_ref (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    title                 VARCHAR(255)  NOT NULL                COMMENT 'FAQ 标题',
    category              VARCHAR(64)   DEFAULT NULL            COMMENT '分类',
    product_tags          VARCHAR(255)  DEFAULT NULL            COMMENT '产品标签（逗号分隔）',
    fault_tags            VARCHAR(255)  DEFAULT NULL            COMMENT '故障标签（逗号分隔）',
    visible_scope         VARCHAR(16)   NOT NULL DEFAULT 'INTERNAL' COMMENT '可见范围：PUBLIC / INTERNAL',
    doc_id                BIGINT        DEFAULT NULL            COMMENT 'whale-doc 文档ID（正文引用）',
    doc_version           INT           DEFAULT NULL            COMMENT '文档版本',
    doc_available         TINYINT       NOT NULL DEFAULT 0      COMMENT '文档可用性：0 不可用 / 1 可用',
    hit_count             INT           NOT NULL DEFAULT 0      COMMENT '引用命中次数',
    status                VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED / DISABLED',
    owner_id              BIGINT        DEFAULT NULL            COMMENT '负责人（数据权限归属人）',
    dept_id               BIGINT        DEFAULT NULL            COMMENT '归属部门',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_kb_ref_doc (tenant_id, doc_id, doc_version),
    KEY idx_crm_kb_ref_category (tenant_id, category)
) COMMENT='CRM 知识引用元数据';

-- ---------------------------------------------------------------------
-- 6. 满意度问卷（4b；一次性令牌只存哈希）
--    status: SENT / USED / EXPIRED / REVOKED
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_satisfaction_survey (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    survey_no             VARCHAR(64)   NOT NULL                COMMENT '问卷编号（CSAT-yyyyMMdd-xxxx）',
    ticket_id             BIGINT        NOT NULL                COMMENT '工单ID',
    token_hash            VARCHAR(128)  NOT NULL                COMMENT '一次性令牌哈希（HMAC-SHA256）',
    status                VARCHAR(16)   NOT NULL DEFAULT 'SENT' COMMENT '问卷状态',
    sent_at               DATETIME      DEFAULT NULL            COMMENT '生成 / 发送时间',
    expires_at            DATETIME      DEFAULT NULL            COMMENT '令牌有效期截止时间',
    used_at               DATETIME      DEFAULT NULL            COMMENT '答卷提交时间',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_survey_no (tenant_id, survey_no),
    UNIQUE KEY uk_crm_survey_token (tenant_id, token_hash),
    KEY idx_crm_survey_ticket (tenant_id, ticket_id, status)
) COMMENT='CRM 满意度问卷';

-- ---------------------------------------------------------------------
-- 7. 满意度答卷（4b；一问卷一答卷）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_satisfaction_response (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    survey_id             BIGINT        NOT NULL                COMMENT '问卷ID',
    ticket_id             BIGINT        NOT NULL                COMMENT '工单ID',
    score                 INT           NOT NULL                COMMENT '总分（1–5）',
    comment               VARCHAR(2000) DEFAULT NULL            COMMENT '客户评论',
    speed_score           INT           DEFAULT NULL            COMMENT '响应速度评分（1–5）',
    attitude_score        INT           DEFAULT NULL            COMMENT '服务态度评分（1–5）',
    professional_score    INT           DEFAULT NULL            COMMENT '专业程度评分（1–5）',
    low_score             TINYINT       NOT NULL DEFAULT 0      COMMENT '是否低分：0 否 / 1 是',
    follow_up_required    TINYINT       NOT NULL DEFAULT 0      COMMENT '是否需回访：0 否 / 1 是',
    submitted_at          DATETIME      NOT NULL                COMMENT '提交时间',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_survey_response (tenant_id, survey_id),
    KEY idx_crm_survey_response_low (tenant_id, low_score, submitted_at)
) COMMENT='CRM 满意度答卷';

-- ---------------------------------------------------------------------
-- 8. 退换货申请（4c；本期仅退货执行，换货字段预留）
--    request_type: RETURN / EXCHANGE
--    status      : DRAFT / PENDING_APPROVAL / APPROVED / REJECTED /
--                  EXECUTING / COMPLETED / FAILED / CANCELLED
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_return_request (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    return_no             VARCHAR(64)   NOT NULL                COMMENT '退换货申请单号（RET-yyyyMMdd-xxxx）',
    request_type          VARCHAR(16)   NOT NULL DEFAULT 'RETURN' COMMENT '申请类型：RETURN / EXCHANGE',
    order_id              BIGINT        NOT NULL                COMMENT '商务订单ID',
    erp_order_no          VARCHAR(64)   NOT NULL                COMMENT 'ERP 销售订单号',
    customer_id           BIGINT        NOT NULL                COMMENT 'CRM 客户ID',
    customer_code         VARCHAR(64)   DEFAULT NULL            COMMENT '客户编码快照',
    customer_name         VARCHAR(255)  DEFAULT NULL            COMMENT '客户名称快照',
    reason                VARCHAR(500)  DEFAULT NULL            COMMENT '退货原因',
    status                VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '申请状态',
    approval_instance_id  BIGINT        DEFAULT NULL            COMMENT '审批流程实例ID（whale-workflow）',
    erp_return_no         VARCHAR(64)   DEFAULT NULL            COMMENT 'ERP 退货单号（下发回填）',
    erp_status            VARCHAR(20)   DEFAULT NULL            COMMENT 'ERP 退货单状态快照',
    push_attempts         INT           NOT NULL DEFAULT 0      COMMENT '下发 ERP 尝试次数',
    last_error            VARCHAR(1000) DEFAULT NULL            COMMENT '最近一次下发失败原因',
    owner_id              BIGINT        DEFAULT NULL            COMMENT '负责人（数据权限归属人）',
    dept_id               BIGINT        DEFAULT NULL            COMMENT '归属部门',
    lock_version          INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_return_no (tenant_id, return_no),
    UNIQUE KEY uk_crm_return_order_approval (tenant_id, order_id, approval_instance_id),
    KEY idx_crm_return_status (tenant_id, status, update_time)
) COMMENT='CRM 退换货申请';

-- ---------------------------------------------------------------------
-- 9. 退换货申请行（4c）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_return_item (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    request_id            BIGINT        NOT NULL                COMMENT '退换货申请ID',
    line_no               INT           NOT NULL                COMMENT '行号（从 1 起）',
    order_line_no         INT           NOT NULL                COMMENT '来源订单行号',
    item_code             VARCHAR(64)   DEFAULT NULL            COMMENT '物料编码快照',
    item_name             VARCHAR(255)  DEFAULT NULL            COMMENT '物料名称快照',
    quantity              DECIMAL(18,4) NOT NULL DEFAULT 0      COMMENT '退货数量',
    unit_price            DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '单价（含税）',
    amount                DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '行金额',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_return_item_no (tenant_id, request_id, line_no),
    KEY idx_crm_return_item_request (tenant_id, request_id)
) COMMENT='CRM 退换货申请行';
