-- =====================================================================
-- BlueWhale 客户关系应用（whale-crm）Flyway 增量 V11：M5 营销管理
-- =====================================================================
-- 适用库   : bluewhale_crm（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/crm-module-panorama.md
-- 计划     : docs/plans/2026-11-04-wave5.83-crm-panorama.md（M5）
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS；
--            2) 禁止 DROP / TRUNCATE / DELETE；
--            3) 表间逻辑外键，不建物理 FOREIGN KEY；
--            4) 不写业务种子数据。
-- 说明     : 本批一次建齐 M5 八个表（活动 / 人群 / 培育 / 触达 / 伙伴），
--            避免后续批次修改已发布迁移。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 营销活动
--    campaign_type: EXHIBITION / LIVE / PROMOTION / OTHER
--    status       : DRAFT / RUNNING / ENDED / CANCELLED
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_campaign (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    campaign_code         VARCHAR(64)   NOT NULL                COMMENT '活动编码（CAMP-yyyyMMdd-xxxx）',
    campaign_name         VARCHAR(255)  NOT NULL                COMMENT '活动名称',
    campaign_type         VARCHAR(16)   NOT NULL DEFAULT 'OTHER' COMMENT '活动类型：EXHIBITION / LIVE / PROMOTION / OTHER',
    status                VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '活动状态：DRAFT / RUNNING / ENDED / CANCELLED',
    channel               VARCHAR(32)   DEFAULT NULL            COMMENT '主要渠道',
    budget                DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '预算金额',
    actual_cost           DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '实际费用',
    planned_start         DATE          DEFAULT NULL            COMMENT '计划开始日期',
    planned_end           DATE          DEFAULT NULL            COMMENT '计划结束日期',
    audience_id           BIGINT        DEFAULT NULL            COMMENT '锁定人群快照ID',
    audience_count        INT           NOT NULL DEFAULT 0      COMMENT '目标人群数量',
    attributed_lead_count INT           NOT NULL DEFAULT 0      COMMENT '归因线索数量',
    attributed_won_amount DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '归因赢单金额',
    roi                   DECIMAL(18,4) NOT NULL DEFAULT 0      COMMENT '投入产出比（归因赢单金额 ÷ 实际费用）',
    owner_id              BIGINT        DEFAULT NULL            COMMENT '负责人（数据权限归属人）',
    dept_id               BIGINT        DEFAULT NULL            COMMENT '归属部门',
    lock_version          INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_campaign_code (tenant_id, campaign_code),
    KEY idx_crm_campaign_status (tenant_id, status)
) COMMENT='CRM 营销活动';

-- ---------------------------------------------------------------------
-- 2. 活动人群快照（锁定后成员不可变）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_campaign_audience (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    campaign_id           BIGINT        NOT NULL                COMMENT '营销活动ID',
    snapshot_no           VARCHAR(64)   NOT NULL                COMMENT '人群快照编号',
    criteria_json         TEXT          DEFAULT NULL            COMMENT '人群筛选条件 JSON',
    member_count          INT           NOT NULL DEFAULT 0      COMMENT '成员数量',
    locked                TINYINT       NOT NULL DEFAULT 0      COMMENT '是否锁定：0 否 / 1 是',
    locked_at             DATETIME      DEFAULT NULL            COMMENT '锁定时间',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_campaign_audience_no (tenant_id, snapshot_no),
    KEY idx_crm_campaign_audience_campaign (campaign_id)
) COMMENT='CRM 活动人群快照';

-- ---------------------------------------------------------------------
-- 3. 活动人群成员（锁定快照明细）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_campaign_member (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    audience_id           BIGINT        NOT NULL                COMMENT '人群快照ID',
    customer_id           BIGINT        NOT NULL                COMMENT 'CRM 客户ID',
    contact_id            BIGINT        DEFAULT NULL            COMMENT '联系人ID',
    contact_name          VARCHAR(128)  DEFAULT NULL            COMMENT '联系人姓名快照',
    email_hash            VARCHAR(128)  DEFAULT NULL            COMMENT '邮箱哈希（脱敏匹配）',
    phone_hash            VARCHAR(128)  DEFAULT NULL            COMMENT '手机号哈希（脱敏匹配）',
    excluded              TINYINT       NOT NULL DEFAULT 0      COMMENT '是否排除：0 否 / 1 是',
    exclude_reason        VARCHAR(64)   DEFAULT NULL            COMMENT '排除原因（退订 / 黑名单 / 无联系方式）',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_campaign_member (tenant_id, audience_id, customer_id, contact_id)
) COMMENT='CRM 活动人群成员';

-- ---------------------------------------------------------------------
-- 4. 客户培育任务
--    status: OPEN / DONE / CANCELLED
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_nurture_task (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    task_no               VARCHAR(64)   NOT NULL                COMMENT '培育任务号（NURTURE-yyyyMMdd-xxxx）',
    customer_id           BIGINT        NOT NULL                COMMENT 'CRM 客户ID',
    customer_name         VARCHAR(255)  DEFAULT NULL            COMMENT '客户名称快照',
    lead_id               BIGINT        DEFAULT NULL            COMMENT '关联线索ID（逻辑外键）',
    content_doc_id        BIGINT        DEFAULT NULL            COMMENT 'whale-doc 内容文档ID',
    content_doc_version   INT           DEFAULT NULL            COMMENT '内容文档版本',
    channel               VARCHAR(32)   DEFAULT NULL            COMMENT '触达渠道',
    plan_date             DATE          DEFAULT NULL            COMMENT '计划触达日期',
    status                VARCHAR(16)   NOT NULL DEFAULT 'OPEN' COMMENT '任务状态：OPEN / DONE / CANCELLED',
    assignee_id           BIGINT        DEFAULT NULL            COMMENT '执行人ID',
    assignee_name         VARCHAR(128)  DEFAULT NULL            COMMENT '执行人姓名快照',
    note                  VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    owner_id              BIGINT        DEFAULT NULL            COMMENT '负责人（数据权限归属人）',
    dept_id               BIGINT        DEFAULT NULL            COMMENT '归属部门',
    lock_version          INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_nurture_task_no (tenant_id, task_no),
    KEY idx_crm_nurture_task_status (tenant_id, status, plan_date)
) COMMENT='CRM 客户培育任务';

-- ---------------------------------------------------------------------
-- 5. 批量触达任务
--    channel: EMAIL / SMS / INBOX
--    status : DRAFT / EXECUTING / COMPLETED / FAILED
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_marketing_send (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    send_no               VARCHAR(64)   NOT NULL                COMMENT '触达任务号（SEND-yyyyMMdd-xxxx）',
    campaign_id           BIGINT        DEFAULT NULL            COMMENT '营销活动ID',
    audience_id           BIGINT        NOT NULL                COMMENT '人群快照ID',
    channel               VARCHAR(16)   NOT NULL                COMMENT '触达渠道：EMAIL / SMS / INBOX',
    template_code         VARCHAR(64)   DEFAULT NULL            COMMENT '消息模板编码',
    subject               VARCHAR(255)  DEFAULT NULL            COMMENT '消息主题',
    content_ref_doc_id    BIGINT        DEFAULT NULL            COMMENT 'whale-doc 内容文档ID',
    status                VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '任务状态：DRAFT / EXECUTING / COMPLETED / FAILED',
    total_count           INT           NOT NULL DEFAULT 0      COMMENT '收件人总数',
    sent_count            INT           NOT NULL DEFAULT 0      COMMENT '成功数量',
    skipped_count         INT           NOT NULL DEFAULT 0      COMMENT '跳过数量',
    failed_count          INT           NOT NULL DEFAULT 0      COMMENT '失败数量',
    executed_at           DATETIME      DEFAULT NULL            COMMENT '执行时间',
    last_error            VARCHAR(1000) DEFAULT NULL            COMMENT '最近一次失败原因',
    owner_id              BIGINT        DEFAULT NULL            COMMENT '负责人（数据权限归属人）',
    dept_id               BIGINT        DEFAULT NULL            COMMENT '归属部门',
    lock_version          INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_marketing_send_no (tenant_id, send_no),
    KEY idx_crm_marketing_send_status (tenant_id, campaign_id, status)
) COMMENT='CRM 批量触达任务';

-- ---------------------------------------------------------------------
-- 6. 批量触达明细
--    status: PENDING / SENT / SKIPPED / FAILED
--    uk_crm_send_item 用于幂等：每活动每联系人每渠道只触达一次
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_marketing_send_item (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    send_id               BIGINT        NOT NULL                COMMENT '触达任务ID',
    campaign_id           BIGINT        DEFAULT NULL            COMMENT '营销活动ID（幂等键组成部分）',
    customer_id           BIGINT        NOT NULL                COMMENT 'CRM 客户ID',
    contact_id            BIGINT        DEFAULT NULL            COMMENT '联系人ID',
    contact_name          VARCHAR(128)  DEFAULT NULL            COMMENT '联系人姓名快照',
    recipient             VARCHAR(255)  DEFAULT NULL            COMMENT '收件人（邮箱 / 手机号 / 用户标识）',
    channel               VARCHAR(16)   NOT NULL                COMMENT '触达渠道',
    status                VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '明细状态：PENDING / SENT / SKIPPED / FAILED',
    error                 VARCHAR(500)  DEFAULT NULL            COMMENT '失败原因',
    sent_at               DATETIME      DEFAULT NULL            COMMENT '发送时间',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_send_item (tenant_id, campaign_id, contact_id, channel),
    KEY idx_crm_send_item_send (tenant_id, send_id, status)
) COMMENT='CRM 批量触达明细';

-- ---------------------------------------------------------------------
-- 7. 渠道伙伴档案
--    partner_type: AGENT / DEALER / OTHER
--    status      : ENABLED / DISABLED
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_partner (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    partner_code          VARCHAR(64)   NOT NULL                COMMENT '伙伴编码',
    partner_name          VARCHAR(255)  NOT NULL                COMMENT '伙伴名称',
    partner_type          VARCHAR(16)   NOT NULL DEFAULT 'OTHER' COMMENT '伙伴类型：AGENT / DEALER / OTHER',
    level                 VARCHAR(16)   DEFAULT NULL            COMMENT '伙伴等级',
    region                VARCHAR(64)   DEFAULT NULL            COMMENT '所属区域',
    contact_name          VARCHAR(128)  DEFAULT NULL            COMMENT '联系人姓名',
    contact_phone         VARCHAR(32)   DEFAULT NULL            COMMENT '联系人电话',
    status                VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED / DISABLED',
    owner_id              BIGINT        DEFAULT NULL            COMMENT '负责人（数据权限归属人）',
    dept_id               BIGINT        DEFAULT NULL            COMMENT '归属部门',
    lock_version          INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_partner_code (tenant_id, partner_code)
) COMMENT='CRM 渠道伙伴档案';

-- ---------------------------------------------------------------------
-- 8. 渠道报单
--    status: PENDING / APPROVED / REJECTED / CONFLICT
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_partner_report (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    report_no             VARCHAR(64)   NOT NULL                COMMENT '报单号（PR-yyyyMMdd-xxxx）',
    partner_id            BIGINT        NOT NULL                COMMENT '渠道伙伴ID',
    partner_name          VARCHAR(255)  DEFAULT NULL            COMMENT '渠道伙伴名称快照',
    customer_id           BIGINT        NOT NULL                COMMENT 'CRM 客户ID',
    customer_name         VARCHAR(255)  DEFAULT NULL            COMMENT '客户名称快照',
    project_name          VARCHAR(255)  NOT NULL                COMMENT '项目名称',
    estimated_amount      DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '预计金额',
    status                VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '报单状态：PENDING / APPROVED / REJECTED / CONFLICT',
    conflict_reason       VARCHAR(500)  DEFAULT NULL            COMMENT '冲突原因',
    approved_amount       DECIMAL(18,2) DEFAULT NULL            COMMENT '核准金额',
    resolved_by           BIGINT        DEFAULT NULL            COMMENT '裁决人ID',
    resolved_at           DATETIME      DEFAULT NULL            COMMENT '裁决时间',
    owner_id              BIGINT        DEFAULT NULL            COMMENT '负责人（数据权限归属人）',
    dept_id               BIGINT        DEFAULT NULL            COMMENT '归属部门',
    lock_version          INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_partner_report_no (tenant_id, report_no),
    KEY idx_crm_partner_report_status (tenant_id, partner_id, status, customer_id)
) COMMENT='CRM 渠道报单';
