-- =====================================================================
-- BlueWhale 微服务平台 - OA 应用（whale-oa） Flyway 版本化迁移 V2
-- =====================================================================
-- 适用库   : bluewhale_oa（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/plans/2026-11-04-wave5.82-oa-panorama.md
-- 里程碑   : M0 审批底座 + 文件引用 + 序列 + Outbox
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS / INSERT IGNORE；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) 表间逻辑外键，不建物理 FOREIGN KEY；
--            4) 全部业务表含 tenant_id，由 MyBatis-Plus 租户插件行级隔离。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 审批业务配置（business_type → Workflow 定义 + 标题模板 + 默认抄送）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_approval_config (
    id              BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    business_type   VARCHAR(64)   NOT NULL            COMMENT '业务类型：LEAVE / OVERTIME / TRIP / EXPENSE / SEAL ...',
    business_name   VARCHAR(64)   NOT NULL            COMMENT '业务名称（展示用）',
    definition_code VARCHAR(64)   NOT NULL            COMMENT 'Workflow 流程定义编码，如 OA_LEAVE',
    title_template  VARCHAR(255)  DEFAULT NULL        COMMENT '标题模板，如 {applicant}的请假申请',
    business_url    VARCHAR(255)  DEFAULT NULL        COMMENT '业务详情前端路由，如 /oa/hr/leave',
    cc_user_ids     VARCHAR(512)  DEFAULT NULL        COMMENT '默认抄送人ID，逗号分隔',
    enabled         TINYINT       NOT NULL DEFAULT 1  COMMENT '是否启用：1 启用 / 0 停用',
    remark          VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_business (tenant_id, business_type),
    KEY idx_config_definition (definition_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 审批业务配置表';

-- ---------------------------------------------------------------------
-- 2. 审批实例（OA 侧业务与 Workflow 实例的映射，状态机只前进不后退）
--    business_type + business_id 唯一：同一业务单据只允许一个审批实例；
--    workflow_instance_id 允许为空（先建 OA 实例、再调 Workflow 发起并回填）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_approval_instance (
    id                   BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id            BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    business_type        VARCHAR(64)   NOT NULL            COMMENT '业务类型',
    business_id          BIGINT        NOT NULL            COMMENT '业务单据ID',
    business_code        VARCHAR(64)   DEFAULT NULL        COMMENT '业务单号（展示/检索）',
    definition_code      VARCHAR(64)   NOT NULL            COMMENT 'Workflow 流程定义编码',
    workflow_instance_id BIGINT        DEFAULT NULL        COMMENT 'Workflow 流程实例ID（发起后回填）',
    title                VARCHAR(255)  NOT NULL            COMMENT '审批标题',
    initiator_id         BIGINT        DEFAULT NULL        COMMENT '发起人用户ID',
    initiator_name       VARCHAR(64)   DEFAULT NULL        COMMENT '发起人姓名快照',
    status               VARCHAR(16)   NOT NULL DEFAULT 'RUNNING'
                                                        COMMENT '状态：RUNNING 审批中 / APPROVED 已通过 / REJECTED 已驳回 / CANCELLED 已撤销 / BROKEN 异常中断',
    current_node_key     VARCHAR(64)   DEFAULT NULL        COMMENT '当前节点标识',
    current_node_name    VARCHAR(64)   DEFAULT NULL        COMMENT '当前节点名称',
    current_assignees    VARCHAR(512)  DEFAULT NULL        COMMENT '当前审批人账号快照，逗号分隔',
    submitted_at         DATETIME      DEFAULT NULL        COMMENT '提交时间',
    finished_at          DATETIME      DEFAULT NULL        COMMENT '完成时间',
    business_url         VARCHAR(255)  DEFAULT NULL        COMMENT '业务详情前端路由',
    remark               VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted              TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_instance_business (tenant_id, business_type, business_id),
    UNIQUE KEY uk_instance_workflow (tenant_id, workflow_instance_id),
    KEY idx_instance_initiator (tenant_id, initiator_id, status),
    KEY idx_instance_status (tenant_id, status, create_time),
    KEY idx_instance_business_code (business_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 审批实例表';

-- ---------------------------------------------------------------------
-- 3. 审批人解析规则（业务类型 + 节点 → 解析器；发起时解析并快照到 oa_approval_route）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_approval_route_rule (
    id                      BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id               BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    business_type           VARCHAR(64)   NOT NULL            COMMENT '业务类型',
    node_key                VARCHAR(64)   NOT NULL            COMMENT 'Workflow 节点标识',
    node_name               VARCHAR(64)   DEFAULT NULL        COMMENT '节点名称',
    resolver_type           VARCHAR(32)   NOT NULL            COMMENT '解析器：USER / ROLE / DEPT_LEADER / PARENT_DEPT_LEADER / INITIATOR / FIXED_ACCOUNT',
    resolver_value          VARCHAR(255)  DEFAULT NULL        COMMENT '解析器参数（账号/角色编码/固定账号）',
    fallback_resolver_type  VARCHAR(32)   DEFAULT NULL        COMMENT '兜底解析器类型（主解析为空时使用）',
    fallback_resolver_value VARCHAR(255)  DEFAULT NULL        COMMENT '兜底解析器参数',
    sort_no                 INT           NOT NULL DEFAULT 0  COMMENT '排序号（同一节点多规则时按序解析）',
    enabled                 TINYINT       NOT NULL DEFAULT 1  COMMENT '是否启用：1 启用 / 0 停用',
    remark                  VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted                 TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_rule_business_node (tenant_id, business_type, node_key),
    KEY idx_rule_business (tenant_id, business_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 审批人解析规则表';

-- ---------------------------------------------------------------------
-- 4. 审批人快照（在途实例不受后续组织变更影响）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_approval_route (
    id                  BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    approval_instance_id BIGINT       NOT NULL            COMMENT '审批实例ID',
    node_key            VARCHAR(64)   NOT NULL            COMMENT 'Workflow 节点标识',
    node_name           VARCHAR(64)   DEFAULT NULL        COMMENT '节点名称',
    assignee_id         BIGINT        DEFAULT NULL        COMMENT '审批人用户ID',
    assignee_account    VARCHAR(64)   NOT NULL            COMMENT '审批人账号',
    resolver_type       VARCHAR(32)   DEFAULT NULL        COMMENT '解析来源类型',
    sort_no             INT           NOT NULL DEFAULT 0  COMMENT '排序号',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_route_instance_node_account (tenant_id, approval_instance_id, node_key, assignee_account),
    KEY idx_route_instance (tenant_id, approval_instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 审批人快照表';

-- ---------------------------------------------------------------------
-- 5. 审批业务副作用（幂等执行；同一实例同一副作用只执行一次）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_approval_effect (
    id                  BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    approval_instance_id BIGINT       NOT NULL            COMMENT '审批实例ID',
    effect_type         VARCHAR(64)   NOT NULL            COMMENT '副作用类型：LEAVE_BALANCE_DEDUCT / ERP_PAYABLE / ARCHIVE_PUSH ...',
    effect_status       VARCHAR(16)   NOT NULL DEFAULT 'PENDING'
                                                        COMMENT '执行状态：PENDING 待执行 / DONE 已完成 / FAILED 失败',
    payload             TEXT          DEFAULT NULL        COMMENT '执行参数快照（JSON）',
    executed_at         DATETIME      DEFAULT NULL        COMMENT '执行时间',
    retry_count         INT           NOT NULL DEFAULT 0  COMMENT '重试次数',
    error_msg           VARCHAR(512)  DEFAULT NULL        COMMENT '失败原因',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_effect_instance_type (tenant_id, approval_instance_id, effect_type),
    KEY idx_effect_status (tenant_id, effect_status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 审批业务副作用表';

-- ---------------------------------------------------------------------
-- 6. 文件引用（文件字节归 whale-service-file；OA 只存引用与业务归属）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_file_ref (
    id           BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    biz_type     VARCHAR(64)   NOT NULL            COMMENT '业务类型：LEAVE / EXPENSE / SEAL / DOC ...',
    biz_id       BIGINT        NOT NULL            COMMENT '业务单据ID',
    file_id      BIGINT        NOT NULL            COMMENT 'whale-service-file 文件ID',
    file_name    VARCHAR(255)  DEFAULT NULL        COMMENT '原始文件名（展示用）',
    file_size    BIGINT        DEFAULT NULL        COMMENT '文件大小（字节）',
    content_type VARCHAR(128)  DEFAULT NULL        COMMENT 'MIME 类型',
    uploaded_by  BIGINT        DEFAULT NULL        COMMENT '上传人用户ID',
    purpose      VARCHAR(64)   DEFAULT NULL        COMMENT '用途：ATTACHMENT 附件 / INVOICE 发票 / SEAL_FILE 用章文件',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_ref_biz (tenant_id, biz_type, biz_id, file_id),
    KEY idx_file_ref_file (tenant_id, file_id),
    KEY idx_file_ref_biz (tenant_id, biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 文件引用表';

-- ---------------------------------------------------------------------
-- 7. 业务序列（按 租户 + 业务类型 + 期间 行级递增；由 INSERT ... ON DUPLICATE KEY 保证并发安全）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_sequence (
    id            BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    biz_type      VARCHAR(64)  NOT NULL            COMMENT '业务类型：LEAVE / EXPENSE / DOC_OUT ...',
    period        CHAR(6)      NOT NULL            COMMENT '期间 yyyyMM',
    current_value BIGINT       NOT NULL DEFAULT 0  COMMENT '当前序号',
    version       BIGINT       NOT NULL DEFAULT 0  COMMENT '版本号（每次递增 +1）',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sequence_biz_period (tenant_id, biz_type, period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 业务序列号表';

-- ---------------------------------------------------------------------
-- 8. 集成 Outbox（本地事务内落库，提交后异步推送；event_key 幂等）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_integration_outbox (
    id             BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    event_key      VARCHAR(128)  NOT NULL            COMMENT '事件幂等键（租户内唯一）',
    event_type     VARCHAR(64)   NOT NULL            COMMENT '事件类型：OA_APPROVAL_APPROVED / OA_APPROVAL_REJECTED ...',
    aggregate_type VARCHAR(64)   NOT NULL            COMMENT '聚合类型：APPROVAL_INSTANCE / EXPENSE ...',
    aggregate_id   VARCHAR(64)   NOT NULL            COMMENT '聚合ID',
    payload        TEXT          DEFAULT NULL        COMMENT '事件体（JSON）',
    status         VARCHAR(16)   NOT NULL DEFAULT 'PENDING'
                                                    COMMENT '状态：PENDING 待发送 / SENT 已发送 / FAILED 失败待重试 / DEAD 死信',
    retry_count    INT           NOT NULL DEFAULT 0  COMMENT '重试次数',
    next_retry_time DATETIME     DEFAULT NULL        COMMENT '下次重试时间',
    sent_at        DATETIME      DEFAULT NULL        COMMENT '发送成功时间',
    error_msg      VARCHAR(512)  DEFAULT NULL        COMMENT '失败原因',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_key (tenant_id, event_key),
    KEY idx_outbox_status (tenant_id, status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 集成事件 Outbox 表';
