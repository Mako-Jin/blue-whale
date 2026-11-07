-- =====================================================================
-- BlueWhale 微服务平台 - OA 应用（whale-oa） Flyway 版本化迁移 V12
-- =====================================================================
-- 适用库   : bluewhale_oa（MySQL 8.0+，utf8mb4）
-- 里程碑   : Wave 5.82 M4 公文与档案（发文 / 收文 / 模板 / 文号规则 / 归档映射）
-- 说明     : 本脚本新建公文 6 张表；附件只存 whale-file 的 fileId 与元数据快照，
--            归档只记录 whale-archive 条目ID，不复制档案数据。
-- 幂等     : CREATE TABLE IF NOT EXISTS + 种子 NOT EXISTS；无破坏性语句。
-- 规则     : 禁止 DROP / TRUNCATE / DELETE；表间逻辑外键，不建物理 FOREIGN KEY。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 公文模板（红头 / 版记 / 正文占位符）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_doc_template (
    id            BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code          VARCHAR(64)  NOT NULL            COMMENT '模板编码（租户内按版本唯一）',
    name          VARCHAR(128) NOT NULL            COMMENT '模板名称',
    doc_type      VARCHAR(16)  NOT NULL DEFAULT 'OUTGOING'
                                                   COMMENT '适用范围：OUTGOING 发文 / INCOMING 收文',
    version       INT          NOT NULL DEFAULT 1  COMMENT '版本号',
    status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT'
                                                   COMMENT '状态：DRAFT 草稿 / RELEASED 已发布 / DISABLED 停用',
    header_text   VARCHAR(512) DEFAULT NULL        COMMENT '红头文字',
    footer_text   VARCHAR(512) DEFAULT NULL        COMMENT '版记文字',
    body_template TEXT         DEFAULT NULL        COMMENT '正文模板（支持占位符）',
    remark        VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_doc_template_code_version (tenant_id, code, version),
    KEY idx_oa_doc_template_type (tenant_id, doc_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 公文模板表';

-- ---------------------------------------------------------------------
-- 2. 文号规则（前缀 + 期间 + 分隔符 + 流水位数）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_doc_no_rule (
    id            BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code          VARCHAR(64)  NOT NULL            COMMENT '规则编码',
    name          VARCHAR(128) NOT NULL            COMMENT '规则名称',
    doc_type      VARCHAR(16)  NOT NULL DEFAULT 'OUTGOING'
                                                   COMMENT '适用范围：OUTGOING 发文 / INCOMING 收文',
    prefix        VARCHAR(64)  NOT NULL DEFAULT 'BW-DOC-' COMMENT '文号前缀',
    period_format VARCHAR(16)  NOT NULL DEFAULT 'MONTH'
                                                   COMMENT '期间格式：YEAR / MONTH / NONE',
    serial_length INT          NOT NULL DEFAULT 3  COMMENT '流水位数（3–6）',
    `separator`   VARCHAR(8)   NOT NULL DEFAULT '-' COMMENT '前缀与流水之间分隔符',
    enabled       TINYINT      NOT NULL DEFAULT 1  COMMENT '是否启用：1 启用 / 0 停用',
    remark        VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_doc_no_rule_code (tenant_id, code),
    KEY idx_oa_doc_no_rule_type (tenant_id, doc_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 公文文号规则表';

-- ---------------------------------------------------------------------
-- 3. 发文（拟稿 → 审核 → 签发 → 下发 → 归档）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_doc_outgoing (
    id                  BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    doc_no              VARCHAR(128) NOT NULL            COMMENT '文号（租户内唯一）',
    title               VARCHAR(255) NOT NULL            COMMENT '公文标题',
    doc_type            VARCHAR(32)  NOT NULL DEFAULT 'NOTICE'
                                                         COMMENT '文种：NOTICE 通知 / REQUEST 请示 / REPLY 批复 / LETTER 函 / MINUTES 纪要',
    security_level      INT          NOT NULL DEFAULT 2  COMMENT '密级：1 公开 / 2 内部 / 3 秘密',
    urgency             INT          NOT NULL DEFAULT 2  COMMENT '紧急度：1 平件 / 2 急件 / 3 特急',
    template_id         BIGINT       DEFAULT NULL        COMMENT '模板ID（逻辑关联 oa_doc_template.id）',
    template_name       VARCHAR(128) DEFAULT NULL        COMMENT '模板名称快照',
    drafter_id          BIGINT       NOT NULL            COMMENT '拟稿人用户ID（逻辑关联 sys_user.id）',
    drafter_name        VARCHAR(64)  DEFAULT NULL        COMMENT '拟稿人姓名快照',
    dept_id             BIGINT       DEFAULT NULL        COMMENT '拟稿部门ID（逻辑关联 sys_dept.id）',
    main_recipients     VARCHAR(512) DEFAULT NULL        COMMENT '主送单位/人员',
    copy_recipients     VARCHAR(512) DEFAULT NULL        COMMENT '抄送单位/人员',
    content             MEDIUMTEXT   DEFAULT NULL        COMMENT '正文内容',
    signer              VARCHAR(64)  DEFAULT NULL        COMMENT '签发人',
    sign_date           DATE         DEFAULT NULL        COMMENT '签发日期',
    issued_at           DATETIME     DEFAULT NULL        COMMENT '下发时间',
    status              VARCHAR(16)  NOT NULL DEFAULT 'DRAFT'
                                                         COMMENT '状态：DRAFT / PENDING / APPROVED / REJECTED / SIGNED / ISSUED / ARCHIVED',
    approval_instance_id BIGINT      DEFAULT NULL        COMMENT 'OA 审批实例ID（逻辑关联 oa_approval_instance.id）',
    archive_item_id     VARCHAR(64)  DEFAULT NULL        COMMENT '档案条目ID（逻辑关联 archive_item.id 字符串）',
    remark              VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_doc_outgoing_no (tenant_id, doc_no),
    KEY idx_oa_doc_outgoing_status (tenant_id, status, create_time),
    KEY idx_oa_doc_outgoing_drafter (tenant_id, drafter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 发文表';

-- ---------------------------------------------------------------------
-- 4. 收文（登记 → 批阅 → 转发 → 办理 → 归档）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_doc_incoming (
    id             BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    incoming_no    VARCHAR(128) NOT NULL            COMMENT '收文号（租户内唯一）',
    source_unit    VARCHAR(128) NOT NULL            COMMENT '来文单位',
    source_no      VARCHAR(128) DEFAULT NULL        COMMENT '来文文号',
    title          VARCHAR(255) NOT NULL            COMMENT '公文标题',
    security_level INT          NOT NULL DEFAULT 2  COMMENT '密级：1 公开 / 2 内部 / 3 秘密',
    urgency        INT          NOT NULL DEFAULT 2  COMMENT '紧急度：1 平件 / 2 急件 / 3 特急',
    received_date  DATE         DEFAULT NULL        COMMENT '收文日期',
    due_date       DATE         DEFAULT NULL        COMMENT '办理期限',
    assignee_id    BIGINT       DEFAULT NULL        COMMENT '承办人用户ID（逻辑关联 sys_user.id）',
    assignee_name  VARCHAR(64)  DEFAULT NULL        COMMENT '承办人姓名快照',
    instruction    VARCHAR(1024) DEFAULT NULL       COMMENT '批阅意见',
    handled_at     DATETIME     DEFAULT NULL        COMMENT '办结时间',
    status         VARCHAR(16)  NOT NULL DEFAULT 'REGISTERED'
                                                    COMMENT '状态：REGISTERED / REVIEWED / FORWARDED / COMPLETED / ARCHIVED',
    archive_item_id VARCHAR(64) DEFAULT NULL        COMMENT '档案条目ID（逻辑关联 archive_item.id 字符串）',
    remark         VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_doc_incoming_no (tenant_id, incoming_no),
    KEY idx_oa_doc_incoming_status (tenant_id, status, received_date),
    KEY idx_oa_doc_incoming_assignee (tenant_id, assignee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 收文表';

-- ---------------------------------------------------------------------
-- 5. 公文附件（只登记 whale-file 的 fileId 与元数据快照）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_doc_attachment (
    id           BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    doc_type     VARCHAR(16)  NOT NULL            COMMENT '单据类型：OUTGOING 发文 / INCOMING 收文',
    doc_id       BIGINT       NOT NULL            COMMENT '单据ID',
    file_id      BIGINT       NOT NULL            COMMENT 'whale-file 文件ID',
    file_name    VARCHAR(255) DEFAULT NULL        COMMENT '文件名快照',
    file_size    BIGINT       NOT NULL DEFAULT 0  COMMENT '文件字节数快照',
    content_type VARCHAR(128) DEFAULT NULL        COMMENT 'MIME 类型快照',
    sort_no      INT          NOT NULL DEFAULT 0  COMMENT '排序号',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_oa_doc_attachment_doc (tenant_id, doc_type, doc_id),
    KEY idx_oa_doc_attachment_file (tenant_id, file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 公文附件表';

-- ---------------------------------------------------------------------
-- 6. 公文归档映射（(doc_type, doc_id) 唯一，失败可重试）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_doc_archive_ref (
    id              BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    doc_type        VARCHAR(16)  NOT NULL            COMMENT '单据类型：OUTGOING / INCOMING',
    doc_id          BIGINT       NOT NULL            COMMENT '单据ID',
    source_type     VARCHAR(32)  NOT NULL DEFAULT 'OA_DOC'
                                                     COMMENT '档案来源类型（whale-archive 白名单）',
    source_id       VARCHAR(64)  NOT NULL            COMMENT '档案来源业务ID，如 OUT-123 / IN-456',
    archive_item_id VARCHAR(64)  DEFAULT NULL        COMMENT '档案条目ID',
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                                                     COMMENT '状态：PENDING 待推送 / ARCHIVED 已归档 / FAILED 失败',
    error_message   VARCHAR(1000) DEFAULT NULL       COMMENT '失败原因（截断至 1000 字）',
    pushed_at       DATETIME     DEFAULT NULL        COMMENT '推送成功时间',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_doc_archive_doc (tenant_id, doc_type, doc_id),
    KEY idx_oa_doc_archive_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 公文归档映射表';

-- ---------------------------------------------------------------------
-- 7. 种子：默认发文 / 收文文号规则
-- ---------------------------------------------------------------------
INSERT INTO oa_doc_no_rule
    (id, tenant_id, code, name, doc_type, prefix, period_format, serial_length, `separator`,
     enabled, remark, deleted)
SELECT 900000000000001201, 1, 'OUT_DEFAULT', '默认发文文号', 'OUTGOING', 'BW-OUT-', 'YEAR', 3, '-',
       1, 'M4 种子：默认发文文号规则', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_doc_no_rule WHERE tenant_id = 1 AND code = 'OUT_DEFAULT'
);
INSERT INTO oa_doc_no_rule
    (id, tenant_id, code, name, doc_type, prefix, period_format, serial_length, `separator`,
     enabled, remark, deleted)
SELECT 900000000000001202, 1, 'IN_DEFAULT', '默认收文文号', 'INCOMING', 'BW-IN-', 'YEAR', 3, '-',
       1, 'M4 种子：默认收文文号规则', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_doc_no_rule WHERE tenant_id = 1 AND code = 'IN_DEFAULT'
);

-- ---------------------------------------------------------------------
-- 8. 种子：发文审批配置与部门经理审批人规则
-- ---------------------------------------------------------------------
INSERT INTO oa_approval_config
    (id, tenant_id, business_type, business_name, definition_code, title_template,
     business_url, cc_user_ids, enabled, remark, deleted)
SELECT 900000000000001211, 1, 'DOC_OUTGOING', '发文', 'OA_DOC_OUTGOING', '{applicant}的发文审批',
       '/oa/document/outgoing', NULL, 1, 'M4 种子：发文审批配置', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_config WHERE tenant_id = 1 AND business_type = 'DOC_OUTGOING'
);
INSERT INTO oa_approval_route_rule
    (id, tenant_id, business_type, node_key, node_name, resolver_type, resolver_value,
     sort_no, enabled, remark, deleted)
SELECT 900000000000001212, 1, 'DOC_OUTGOING', 'dept_manager', '部门经理审核', 'DEPT_LEADER', NULL,
       10, 1, 'M4 种子：发文部门经理审批人', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_route_rule
    WHERE tenant_id = 1 AND business_type = 'DOC_OUTGOING' AND node_key = 'dept_manager'
);
