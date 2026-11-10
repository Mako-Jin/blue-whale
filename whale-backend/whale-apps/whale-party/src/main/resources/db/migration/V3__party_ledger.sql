-- =====================================================================
-- BlueWhale 党政工会应用（whale-party）M-P1 党建台账（Wave 5.88）
-- =====================================================================
-- 范围       : 支部 / 党员档案 / 组织生活 / 组织生活签到 / 党费标准 / 党费账单
-- 需求       : REQ-FUNC-091（党建 M-P1）
-- 边界       : 员工与组织主数据只读引用 ERP（员工编号与 orgCode 存字符串快照，
--              不建外键、不复制人事表）；附件字节归 whale-file，本服务只存引用。
-- 脚本约束   : 禁止 DROP / TRUNCATE / DELETE；可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 支部（聚合根）
--    branch_code : 租户内唯一，取号规则 BR{yyyy}{4 位流水}（pty_sequence 生成后写死）
--    parent_id   : 上级支部，0 表示一级支部；层级 level 由应用层回填并限制深度
--    org_code    : 弱关联 ERP 组织编码，只作展示与筛选，组织调整不自动跟随
--    status      : ACTIVE（在册）/ SUSPENDED（停用，不可再接收党员）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pty_branch (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    branch_code         VARCHAR(32)   NOT NULL                COMMENT '支部编码（租户内唯一）',
    branch_name         VARCHAR(128)  NOT NULL                COMMENT '支部名称',
    parent_id           BIGINT        NOT NULL DEFAULT 0      COMMENT '上级支部ID，0 为一级支部',
    branch_level        INT           NOT NULL DEFAULT 1      COMMENT '支部层级（1 起，限制深度由应用层校验）',
    org_code            VARCHAR(64)   DEFAULT NULL            COMMENT '关联 ERP 组织编码（弱关联，可为空）',
    secretary_name      VARCHAR(64)   DEFAULT NULL            COMMENT '支部书记姓名',
    secretary_user_id   BIGINT        DEFAULT NULL            COMMENT '支部书记用户ID（可为空，书记未开通账号）',
    established_date    DATE          DEFAULT NULL            COMMENT '成立日期',
    status              VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/SUSPENDED',
    sort_no             INT           NOT NULL DEFAULT 0      COMMENT '排序号（同级展示顺序）',
    remark              VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pty_branch_code (tenant_id, branch_code),
    KEY idx_pty_branch_parent (tenant_id, parent_id),
    KEY idx_pty_branch_status (tenant_id, status)
) COMMENT='党支部';

-- ---------------------------------------------------------------------
-- 2. 党员档案（一人一档）
--    member_no     : 租户内唯一（党员编号，取号规则 MB{yyyy}{4 位流水}）
--    employee_no   : 关联 ERP 员工编号；同一租户内一个员工只能有一份党员档案
--    political_status : FULL_MEMBER（正式党员）/ PROBATIONARY（预备党员）
--    status        : ACTIVE（在册）/ TRANSFERRED（已转出，只读）/ SUSPENDED（停籍）
--    dues_base     : 党费计算基数（月收入口径，应用层校验非负）
--    待转正       : political_status = PROBATIONARY 且 full_member_date 为空即待转正
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pty_member (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    member_no           VARCHAR(32)   NOT NULL                COMMENT '党员编号（租户内唯一）',
    employee_no         VARCHAR(64)   NOT NULL                COMMENT '员工编号（引用 ERP，一人一档）',
    user_id             BIGINT        DEFAULT NULL            COMMENT '平台用户ID（未开通账号为空）',
    member_name         VARCHAR(64)   NOT NULL                COMMENT '党员姓名',
    branch_id           BIGINT        NOT NULL                COMMENT '所属支部ID',
    political_status    VARCHAR(16)   NOT NULL DEFAULT 'FULL_MEMBER' COMMENT '政治面貌：FULL_MEMBER/PROBATIONARY',
    join_party_date     DATE          NOT NULL                COMMENT '入党日期（党龄起算）',
    full_member_date    DATE          DEFAULT NULL            COMMENT '转正日期（预备党员为空）',
    dues_base           DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '党费计算基数（月收入口径）',
    phone               VARCHAR(32)   DEFAULT NULL            COMMENT '联系电话',
    status              VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/TRANSFERRED/SUSPENDED',
    transfer_out_date   DATE          DEFAULT NULL            COMMENT '组织关系转出日期',
    remark              VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pty_member_no (tenant_id, member_no),
    UNIQUE KEY uk_pty_member_employee (tenant_id, employee_no),
    KEY idx_pty_member_branch (tenant_id, branch_id, status),
    KEY idx_pty_member_user (tenant_id, user_id)
) COMMENT='党员档案';

-- ---------------------------------------------------------------------
-- 3. 组织生活（三会一课与主题党日）
--    activity_type : BRANCH_COMMITTEE 支委会 / MEMBER_ASSEMBLY 党员大会 /
--                    PARTY_GROUP 党小组会 / PARTY_LECTURE 党课 / THEME_DAY 主题党日
--    status        : PLANNED（计划）→ COMPLETED（已完成）→ ARCHIVED（已归档，终态）
--    已归档不可再改记录，也不可再签到（归档语义是「本次组织生活材料定稿」）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pty_activity (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    branch_id           BIGINT        NOT NULL                COMMENT '所属支部ID',
    activity_type       VARCHAR(32)   NOT NULL                COMMENT '类型：BRANCH_COMMITTEE/MEMBER_ASSEMBLY/PARTY_GROUP/PARTY_LECTURE/THEME_DAY',
    title               VARCHAR(200)  NOT NULL                COMMENT '活动主题',
    planned_date        DATE          NOT NULL                COMMENT '计划日期',
    held_date           DATE          DEFAULT NULL            COMMENT '实际召开日期（完成时必填）',
    location            VARCHAR(128)  DEFAULT NULL            COMMENT '活动地点',
    host_name           VARCHAR(64)   DEFAULT NULL            COMMENT '主持人',
    expected_count      INT           NOT NULL DEFAULT 0      COMMENT '应到人数（完成时与签到数对照）',
    status              VARCHAR(16)   NOT NULL DEFAULT 'PLANNED' COMMENT '状态：PLANNED/COMPLETED/ARCHIVED',
    summary             VARCHAR(2000) DEFAULT NULL            COMMENT '会议/活动纪要（完成时填写）',
    remark              VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_pty_activity_branch (tenant_id, branch_id, planned_date),
    KEY idx_pty_activity_status (tenant_id, status, planned_date)
) COMMENT='组织生活（三会一课与主题党日）';

-- ---------------------------------------------------------------------
-- 4. 组织生活签到
--    唯一键 (activity_id, member_id) 保证同一人在同一场活动只有一条签到记录，
--    重复签到走 upsert 更新而不是插第二条，否则「实到人数」会重复计数。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pty_activity_signin (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    activity_id         BIGINT        NOT NULL                COMMENT '组织生活ID',
    member_id           BIGINT        NOT NULL                COMMENT '党员档案ID',
    member_name         VARCHAR(64)   NOT NULL                COMMENT '党员姓名快照（防改名漂移）',
    present             TINYINT       NOT NULL DEFAULT 1      COMMENT '是否到会：1 到会 / 0 缺席',
    absent_reason       VARCHAR(255)  DEFAULT NULL            COMMENT '缺席原因（请假/出差等）',
    signed_at           DATETIME      DEFAULT NULL            COMMENT '签到时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pty_activity_signin (tenant_id, activity_id, member_id),
    KEY idx_pty_signin_member (tenant_id, member_id)
) COMMENT='组织生活签到';

-- ---------------------------------------------------------------------
-- 5. 党费标准（按基数区间匹配比例）
--    区间语义：min_base <= 基数 < max_base；max_base 为 NULL 表示该档无上限。
--    档位之间必须不重叠，由应用层在保存时校验（重叠会导致同一基数命中两条规则，
--    取哪条取决于数据库返回顺序，账单金额会随机漂移）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pty_fee_rule (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    rule_name           VARCHAR(64)   NOT NULL                COMMENT '档位名称（如「3000 元以下」）',
    min_base            DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '基数下限（含）',
    max_base            DECIMAL(12,2) DEFAULT NULL            COMMENT '基数上限（不含），NULL 表示无上限',
    rate                DECIMAL(6,4)  NOT NULL                COMMENT '缴费比例（如 0.0050 表示 0.5%）',
    status              VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    sort_no             INT           NOT NULL DEFAULT 0      COMMENT '排序号（按基数升序展示）',
    remark              VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_pty_fee_rule_status (tenant_id, status, min_base)
) COMMENT='党费标准（基数区间 → 比例）';

-- ---------------------------------------------------------------------
-- 6. 党费月度账单
--    period_month : yyyyMM；唯一键 (member_id, period_month) 保证同月不重复生成。
--    dues_base / rate 都是**生成时的快照**：党员调薪或标准调整后不回改历史账单，
--    否则去年已缴清的账单会被改成「欠缴」。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pty_fee_bill (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    member_id           BIGINT        NOT NULL                COMMENT '党员档案ID',
    branch_id           BIGINT        NOT NULL                COMMENT '支部ID（生成时快照）',
    member_name         VARCHAR(64)   NOT NULL                COMMENT '党员姓名快照',
    period_month        VARCHAR(8)   NOT NULL                COMMENT '所属月份 yyyyMM',
    dues_base           DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '党费基数（生成时快照）',
    rate                DECIMAL(6,4)  NOT NULL DEFAULT 0.0000 COMMENT '缴费比例（生成时快照）',
    due_amount          DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '应缴金额',
    paid_amount         DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '实缴金额（累计）',
    paid_date           DATE          DEFAULT NULL            COMMENT '最近缴费日期',
    status              VARCHAR(16)   NOT NULL DEFAULT 'UNPAID' COMMENT '状态：UNPAID/PARTIAL/PAID/WAIVED',
    remark              VARCHAR(255)  DEFAULT NULL            COMMENT '备注（补缴说明等）',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pty_fee_bill (tenant_id, member_id, period_month),
    KEY idx_pty_fee_bill_period (tenant_id, period_month, status),
    KEY idx_pty_fee_bill_branch (tenant_id, branch_id, period_month)
) COMMENT='党费月度账单';
