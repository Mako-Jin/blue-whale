-- =====================================================================
-- BlueWhale 学习培训考试应用（whale-lms）M5 集成收口（Wave 5.87）
-- =====================================================================
-- 范围       : ERP 员工只读快照 / 通知投递留痕
-- 需求       : REQ-FUNC-090（集成收口，M5）
-- 边界       : 快照是 ERP 员工主数据的只读副本，LMS 绝不回写；保留离职员工
--              以便学习与考试记录仍可追溯，只是不再纳入催办范围。
-- 脚本约束   : 禁止 DROP / TRUNCATE / DELETE；可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 员工只读快照
--    uk_lr_member_snapshot: 按员工编号唯一，而不是按 user_id ——
--                  ERP 里存在尚未开通平台账号的员工（user_id 为空），
--                 用 user_id 作唯一键会让这些行全部撞在 NULL 上互相覆盖
--    status      : 直接沿用 ERP 取值 ACTIVE / LEFT，不做二次翻译，
--                 避免两侧枚举漂移后出现「LMS 认为在职、ERP 认为离职」
--    last_synced_at: 最近一次同步覆盖的时间，用于判断快照是否新鲜；
--                 不与 update_time 混用（后者任何一次写都会变，含人工修正）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_member_snapshot (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    employee_no         VARCHAR(64)   NOT NULL                COMMENT '员工编号（ERP 主键口径）',
    user_id             BIGINT        DEFAULT NULL            COMMENT '平台账号ID（未开通为空）',
    name                VARCHAR(64)   DEFAULT NULL            COMMENT '姓名',
    org_code            VARCHAR(64)   DEFAULT NULL            COMMENT '组织编码',
    position            VARCHAR(128)  DEFAULT NULL            COMMENT '岗位',
    status              VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '在职状态：ACTIVE/LEFT',
    hire_date           DATE          DEFAULT NULL            COMMENT '入职日期',
    leave_date          DATE          DEFAULT NULL            COMMENT '离职日期',
    last_synced_at      DATETIME      NOT NULL                COMMENT '最近同步时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lr_member_snapshot (tenant_id, employee_no),
    KEY idx_lr_member_snapshot_user (tenant_id, user_id),
    KEY idx_lr_member_snapshot_status (tenant_id, status, org_code)
) COMMENT='员工只读快照（ERP 单向同步，LMS 不回写）';

-- ---------------------------------------------------------------------
-- 2. 通知投递留痕
--    uk_lr_notify_log: 同一幂等键在同一接收人上只发一次。
--                 幂等键由应用按场景生成（如 AUTO_URGE:{classId}:{date}），
--                 消息服务侧虽也去重，但本地留痕才能回答「到底发过没有、
--                 发给谁、失败原因是什么」——只依赖下游去重时，
--                 下游一次抖动就会让运维无法复盘漏发
--    send_status : SENT 成功 / FAILED 失败（FAILED 可被下一轮重试覆盖为 SENT）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_notify_log (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    scene               VARCHAR(32)   NOT NULL                COMMENT '通知场景：ENROLL/CLASS_START/URGE/CERT_ISSUE',
    idempotency_key     VARCHAR(128)  NOT NULL                COMMENT '幂等键（场景内唯一）',
    biz_type            VARCHAR(32)   DEFAULT NULL            COMMENT '业务类型（冗余检索）',
    biz_id              BIGINT        DEFAULT NULL            COMMENT '业务ID（冗余检索）',
    user_id             BIGINT        NOT NULL                COMMENT '接收人用户ID',
    template_code       VARCHAR(64)   DEFAULT NULL            COMMENT '消息模板编码',
    title               VARCHAR(200)  DEFAULT NULL            COMMENT '通知标题',
    channel             VARCHAR(16)   NOT NULL DEFAULT 'INAPP' COMMENT '投递渠道：INAPP',
    send_status         VARCHAR(16)   NOT NULL DEFAULT 'SENT' COMMENT '投递状态：SENT/FAILED',
    fail_reason         VARCHAR(500)  DEFAULT NULL            COMMENT '失败原因（截断留存）',
    sent_at             DATETIME      NOT NULL                COMMENT '投递时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lr_notify_log (tenant_id, idempotency_key, user_id),
    KEY idx_lr_notify_log_scene (tenant_id, scene, sent_at),
    KEY idx_lr_notify_log_user (tenant_id, user_id, sent_at)
) COMMENT='通知投递留痕（幂等键去重）';
