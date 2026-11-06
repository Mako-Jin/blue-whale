-- =====================================================================
-- BlueWhale 党政工会应用（whale-party）M-P4 看板与收口
-- =====================================================================
-- 库名       : bluewhale_party
-- 状态       : M-P4。统计快照 / 通知留痕 / ERP 员工只读快照 3 张表，
--              并给党员档案补出生日期与学历两个统计维度。
-- 脚本约束   : 禁止 DROP / TRUNCATE / DELETE；建表必须 IF NOT EXISTS（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 统计快照
--    只存分子与分母，不存比率：
--      比率是展示口径（保留位数、分母为 0 的展示规则）会随时间调整，
--      落库比率等于把当时的展示规则固化进历史，后续改口径就对不上。
--    唯一键覆盖写而不是追加：
--      同一 stat_date 重刷只更新该行，历史曲线按日期天然分段；
--      若追加，重刷一次就多一条同日快照，趋势图会出现锯齿。
--    分布类指标把分桶编码并入 metric_type（如 MEMBER_AGE_30_39），
--      避免再引入 bucket 列并改动唯一键。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pty_metric_snapshot (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    stat_date           DATE          NOT NULL                COMMENT '统计日期',
    metric_type         VARCHAR(40)   NOT NULL                COMMENT '指标编码（分桶指标把桶号并入编码）',
    scope_type          VARCHAR(16)   NOT NULL DEFAULT 'ALL'   COMMENT '统计范围：ALL 全量 / BRANCH 支部',
    scope_id            BIGINT        NOT NULL DEFAULT 0      COMMENT '范围ID：ALL 固定 0，BRANCH 为支部ID',
    scope_name          VARCHAR(128)  DEFAULT NULL            COMMENT '范围名称快照（支部改名不漂移历史）',
    numerator           DECIMAL(14,2) NOT NULL DEFAULT 0.00   COMMENT '分子（计数或金额）',
    denominator         DECIMAL(14,2) NOT NULL DEFAULT 0.00   COMMENT '分母（纯计数指标固定 0）',
    computed_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '计算时刻',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pty_metric_snapshot (tenant_id, stat_date, metric_type, scope_type, scope_id),
    KEY idx_pty_metric_lookup (tenant_id, stat_date, scope_type, scope_id)
) COMMENT='业务统计快照（分子/分母，覆盖写）';

-- ---------------------------------------------------------------------
-- 2. 通知投递留痕
--    与 LMS 的 lr_notify_log 结构对齐：本地留痕 + 下游幂等键双层去重。
--    唯一键 (tenant_id, idempotency_key, user_id)：
--      同一场景同一业务对象对同一接收人只留一行；失败重试时把 FAILED
--      覆盖为 SENT，而不是再插一行「又失败了一次」。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pty_notify_log (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    scene               VARCHAR(32)   NOT NULL                COMMENT '场景：PROBATION_REVIEW/FEE_URGE/ACTIVITY_NOTICE/PROPOSAL_FEEDBACK/BRANCH_TERM',
    idempotency_key     VARCHAR(160)  NOT NULL                COMMENT '幂等键（场景内唯一）',
    biz_type            VARCHAR(32)   DEFAULT NULL            COMMENT '业务类型：MEMBER/FEE_BILL/ACTIVITY/PROPOSAL/BRANCH',
    biz_id              BIGINT        DEFAULT NULL            COMMENT '业务主键',
    user_id             BIGINT        NOT NULL                COMMENT '接收人用户ID',
    template_code       VARCHAR(64)   DEFAULT NULL            COMMENT '消息模板编码',
    title               VARCHAR(200)  DEFAULT NULL            COMMENT '消息标题',
    channel             VARCHAR(16)   NOT NULL DEFAULT 'INAPP' COMMENT '渠道：INAPP',
    send_status         VARCHAR(16)   NOT NULL DEFAULT 'SENT' COMMENT '状态：SENT/FAILED',
    fail_reason         VARCHAR(255)  DEFAULT NULL            COMMENT '失败原因',
    sent_at             DATETIME      DEFAULT NULL            COMMENT '投递成功时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pty_notify_log (tenant_id, idempotency_key, user_id),
    KEY idx_pty_notify_scene (tenant_id, scene, sent_at),
    KEY idx_pty_notify_user (tenant_id, user_id, sent_at)
) COMMENT='通知投递留痕（本地幂等）';

-- ---------------------------------------------------------------------
-- 3. ERP 员工只读快照
--    工会会员覆盖率的分母是「在职员工数」，工会会员表只有入会的人，
--      没有全体员工，因此需要一份只读宽带快照。
--    同步失败保留旧快照、不清表：把网络故障当成空目录会让覆盖率
--      瞬间变成 100%，比暂时不更新更危险。
--    last_synced_at 独立于 update_time：判断数据新鲜度看同步时刻，
--      而不是任何一次行更新（例如修备注也会刷新 update_time）。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pty_employee_snapshot (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    employee_no         VARCHAR(64)   NOT NULL                COMMENT '员工编号（ERP 来源，租户内唯一）',
    user_id             BIGINT        DEFAULT NULL            COMMENT '平台用户ID（未开通账号为空）',
    employee_name       VARCHAR(64)   NOT NULL                COMMENT '员工姓名',
    org_code            VARCHAR(64)   DEFAULT NULL            COMMENT 'ERP 组织编码',
    position_name       VARCHAR(128)  DEFAULT NULL            COMMENT '岗位名称',
    status              VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/LEFT（沿用 ERP 原值）',
    hire_date           DATE          DEFAULT NULL            COMMENT '入职日期',
    leave_date          DATE          DEFAULT NULL            COMMENT '离职日期',
    last_synced_at      DATETIME      DEFAULT NULL            COMMENT '最近同步时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pty_employee_snapshot (tenant_id, employee_no),
    KEY idx_pty_employee_status (tenant_id, status)
) COMMENT='ERP 员工只读快照（工会覆盖率分母）';

-- ---------------------------------------------------------------------
-- 4. 党员档案补充统计维度
--    birth_date / education 均可空：历史档案不回填、不阻断建档；
--      为空在统计时归入「未知」桶，而不是悄悄塞进某一档。
--    MySQL 不支持 ADD COLUMN IF NOT EXISTS，本脚本是版本化迁移，
--      由 Flyway 保证只执行一次；与建表语句的 IF NOT EXISTS 约束无关。
-- ---------------------------------------------------------------------
ALTER TABLE pty_member
    ADD COLUMN birth_date DATE DEFAULT NULL COMMENT '出生日期（年龄结构统计，可空）',
    ADD COLUMN education  VARCHAR(32) DEFAULT NULL COMMENT '学历编码：DOCTOR/MASTER/BACHELOR/COLLEGE/SECONDARY/OTHER，可空';
