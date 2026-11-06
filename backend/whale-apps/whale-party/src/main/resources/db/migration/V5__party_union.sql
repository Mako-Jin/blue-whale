-- =====================================================================
-- BlueWhale 党政工会应用（whale-party）M-P3 工会（Wave 5.88）
-- =====================================================================
-- 范围       : 工会会员 / 慰问帮扶 / 节日福利（批次与明细）/ 工会活动（含报名）/ 职代会提案
-- 需求       : REQ-FUNC-092（工会 M-P3）
-- 边界       : 员工与组织主数据只读引用 ERP（工号与部门存字符串快照，不建外键）；
--              慰问审批走 whale-workflow（本表只存实例ID与审批状态）；
--              会费不建逐笔流水，只在会员档案记基数与缴至月份。
-- 脚本约束   : 禁止 DROP / TRUNCATE / DELETE；可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 工会会员（入会 / 退会 / 会费）
--    member_no    : 租户内唯一（取号规则 UN{yyyy}{4 位流水}）
--    employee_no  : 关联 ERP 员工编号；**刻意不建唯一键**——退会后可再入会，
--                   一人多段会籍是正常事实，唯一键会把再入会直接拒掉。
--                   「同一人同时只能有一份在册会籍」由应用层按 status=ACTIVE 校验。
--    dues_paid_to : 会费缴至月份（yyyy-MM），按月汇缴口径，不在本波拆分逐笔流水。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pty_union_member (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    member_no           VARCHAR(32)   NOT NULL                COMMENT '会员编号（租户内唯一）',
    employee_no         VARCHAR(64)   NOT NULL                COMMENT '员工编号（引用 ERP）',
    user_id             BIGINT        DEFAULT NULL            COMMENT '平台用户ID（未开通账号为空）',
    member_name         VARCHAR(64)   NOT NULL                COMMENT '会员姓名',
    dept_name           VARCHAR(128)  DEFAULT NULL            COMMENT '所属部门名称（ERP 快照）',
    join_date           DATE          NOT NULL                COMMENT '入会日期',
    status              VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/WITHDRAWN',
    withdraw_date       DATE          DEFAULT NULL            COMMENT '退会日期',
    dues_base           DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '会费基数（月收入口径）',
    dues_paid_to        VARCHAR(7)    DEFAULT NULL            COMMENT '会费缴至月份（yyyy-MM）',
    remark              VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pty_union_member_no (tenant_id, member_no),
    KEY idx_union_member_employee (tenant_id, employee_no, status),
    KEY idx_union_member_user (tenant_id, user_id)
) COMMENT='工会会员';

-- ---------------------------------------------------------------------
-- 2. 慰问与困难帮扶（workflow UNION_RELIEF）
--    year            : 申请年度，**独立落库**——上限按业务年度计，不按提交时点现算，
--                      否则跨年补录会把去年的额度算到今年。
--    status          : DRAFT（草稿，含审批不可用降级）/ SUBMITTED / APPROVED / REJECTED / PAID
--    approval_hint   : 降级提示（「审批服务不可用，已保存为草稿」），列表直接展示。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pty_union_relief (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    relief_no           VARCHAR(32)   NOT NULL                COMMENT '申请编号（租户内唯一）',
    union_member_id     BIGINT        NOT NULL                COMMENT '工会会员ID',
    applicant_user_id   BIGINT        DEFAULT NULL            COMMENT '申请人用户ID',
    relief_type         VARCHAR(16)   NOT NULL                COMMENT '类型：DIFFICULTY/CONDOLENCE/MEDICAL/EDUCATION/OTHER',
    apply_year          INT           NOT NULL                COMMENT '申请年度（计上限）',
    amount              DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '申请金额',
    reason              VARCHAR(500)  NOT NULL                COMMENT '申请事由',
    status              VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/SUBMITTED/APPROVED/REJECTED/PAID',
    approval_instance_id BIGINT       DEFAULT NULL            COMMENT '工作流实例ID',
    approval_status     VARCHAR(16)   DEFAULT NULL            COMMENT '工作流状态快照：RUNNING/APPROVED/REJECTED/CANCELLED',
    approval_hint       VARCHAR(255)  DEFAULT NULL            COMMENT '审批降级/同步提示',
    submit_time         DATETIME      DEFAULT NULL            COMMENT '提交时间',
    approved_at         DATETIME      DEFAULT NULL            COMMENT '审批通过时间',
    paid_at             DATETIME      DEFAULT NULL            COMMENT '发放时间',
    remark              VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pty_union_relief_no (tenant_id, relief_no),
    KEY idx_union_relief_cap (tenant_id, union_member_id, relief_type, apply_year, status),
    KEY idx_union_relief_status (tenant_id, status, create_time)
) COMMENT='工会慰问与困难帮扶';

-- ---------------------------------------------------------------------
-- 3. 节日福利批次
--    standard_amount : 发放标准，在发放时快照到明细——后续调标不改历史名单金额。
--    status          : DRAFT（可改）/ ISSUED（已发放，可签收）/ CLOSED（已关闭，终态）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pty_union_welfare (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    welfare_no          VARCHAR(32)   NOT NULL                COMMENT '福利批次编号（租户内唯一）',
    title               VARCHAR(128)  NOT NULL                COMMENT '福利名称',
    welfare_year        INT           NOT NULL                COMMENT '福利年度',
    standard_amount     DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '发放标准（元/人）',
    status              VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/ISSUED/CLOSED',
    issued_at           DATETIME      DEFAULT NULL            COMMENT '发放时间',
    closed_at           DATETIME      DEFAULT NULL            COMMENT '关闭时间',
    remark              VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pty_union_welfare_no (tenant_id, welfare_no),
    KEY idx_union_welfare_year (tenant_id, welfare_year, status)
) COMMENT='工会节日福利批次';

-- ---------------------------------------------------------------------
-- 4. 节日福利发放明细
--    唯一键保证同一批次同一会员只有一条：重复发放是幂等跳过，不追加行。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pty_union_welfare_item (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    welfare_id          BIGINT        NOT NULL                COMMENT '福利批次ID',
    union_member_id     BIGINT        NOT NULL                COMMENT '工会会员ID',
    employee_no         VARCHAR(64)   NOT NULL                COMMENT '员工编号（发放时快照）',
    member_name         VARCHAR(64)   NOT NULL                COMMENT '会员姓名（发放时快照）',
    amount              DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '发放金额（标准快照）',
    sign_status         VARCHAR(16)   NOT NULL DEFAULT 'UNSIGNED' COMMENT '签收状态：UNSIGNED/SIGNED',
    signed_at           DATETIME      DEFAULT NULL            COMMENT '签收时间',
    signed_by           BIGINT        DEFAULT NULL            COMMENT '签收操作人用户ID',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pty_union_welfare_item (tenant_id, welfare_id, union_member_id),
    KEY idx_union_welfare_item_sign (tenant_id, welfare_id, sign_status)
) COMMENT='工会节日福利发放明细';

-- ---------------------------------------------------------------------
-- 5. 工会活动
--    quota  : 0 表示不限名额；发布后时间、地点与名额冻结。
--    status : DRAFT → PUBLISHED → CLOSED（终态）；DRAFT/PUBLISHED → CANCELLED（终态）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pty_union_activity (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    activity_no         VARCHAR(32)   NOT NULL                COMMENT '活动编号（租户内唯一）',
    title               VARCHAR(128)  NOT NULL                COMMENT '活动名称',
    activity_type       VARCHAR(32)   DEFAULT NULL            COMMENT '活动类型（文体/团建/公益等，自由文本）',
    start_time          DATETIME      NOT NULL                COMMENT '开始时间',
    end_time            DATETIME      NOT NULL                COMMENT '结束时间',
    location            VARCHAR(255)  DEFAULT NULL            COMMENT '活动地点',
    quota               INT           NOT NULL DEFAULT 0      COMMENT '名额上限，0 表示不限',
    status              VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED/CLOSED/CANCELLED',
    remark              VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pty_union_activity_no (tenant_id, activity_no),
    KEY idx_union_activity_time (tenant_id, start_time, status)
) COMMENT='工会活动';

-- ---------------------------------------------------------------------
-- 6. 工会活动报名
--    唯一键 (activity_id, union_member_id)：取消后再报名只把状态改回 SIGNED，
--    不新增行——否则反复取消/报名会在名单里留下多条自相矛盾的记录。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pty_union_signup (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    activity_id         BIGINT        NOT NULL                COMMENT '活动ID',
    union_member_id     BIGINT        NOT NULL                COMMENT '工会会员ID',
    user_id             BIGINT        DEFAULT NULL            COMMENT '报名用户ID',
    status              VARCHAR(16)   NOT NULL DEFAULT 'SIGNED' COMMENT '状态：SIGNED/CANCELLED',
    signup_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
    cancelled_at        DATETIME      DEFAULT NULL            COMMENT '取消时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pty_union_signup (tenant_id, activity_id, union_member_id),
    KEY idx_union_signup_user (tenant_id, user_id, status)
) COMMENT='工会活动报名';

-- ---------------------------------------------------------------------
-- 7. 职代会提案
--    status : SUBMITTED → FILED → HANDLING → FEEDBACK → RATED（终态，单向不可跳步）
--            每次流转只写当前节点字段与时间，不覆盖前序节点。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pty_union_proposal (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    proposal_no         VARCHAR(32)   NOT NULL                COMMENT '提案编号（租户内唯一）',
    title               VARCHAR(128)  NOT NULL                COMMENT '提案标题',
    content             TEXT          NOT NULL                COMMENT '提案内容',
    submitter_user_id   BIGINT        DEFAULT NULL            COMMENT '提交人用户ID',
    submitter_name      VARCHAR(64)   DEFAULT NULL            COMMENT '提交人姓名快照',
    status              VARCHAR(16)   NOT NULL DEFAULT 'SUBMITTED' COMMENT '状态：SUBMITTED/FILED/HANDLING/FEEDBACK/RATED',
    handler             VARCHAR(64)   DEFAULT NULL            COMMENT '承办人',
    filed_at            DATETIME      DEFAULT NULL            COMMENT '立案时间',
    handled_at          DATETIME      DEFAULT NULL            COMMENT '办理时间',
    feedback            VARCHAR(500)  DEFAULT NULL            COMMENT '办理反馈',
    feedback_at         DATETIME      DEFAULT NULL            COMMENT '反馈时间',
    rating              TINYINT       DEFAULT NULL            COMMENT '评价分值 1–5',
    rated_at            DATETIME      DEFAULT NULL            COMMENT '评价时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pty_union_proposal_no (tenant_id, proposal_no),
    KEY idx_union_proposal_status (tenant_id, status, create_time),
    KEY idx_union_proposal_submitter (tenant_id, submitter_user_id)
) COMMENT='职代会提案';
