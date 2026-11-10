-- =====================================================================
-- BlueWhale 学习培训考试应用（whale-lms）M4-2 学分规则与台账（Wave 5.87）
-- =====================================================================
-- 范围       : 学分规则 / 学分台账（年度汇总按台账聚合）
-- 需求       : REQ-FUNC-090（证书与学分，M4）
-- 边界       : 学分是「已达成事实」的记账结果，不含学习过程数据；
--              同一业务事件只记一次，由唯一键保证而不是靠调用方自觉。
-- 脚本约束   : 禁止 DROP / TRUNCATE / DELETE；可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 学分规则
--    source_type: COURSE 课程 / EXAM 考试 / TRAINING 培训
--    match_id   : 0 表示该来源下的全部对象（通配），>0 为具体课程/考试/培训班 ID
--    credit     : 达成一次记多少学分（固定值，不做按学时折算——各来源学时口径
--                 尚未统一，按学时折算会得到互相矛盾的结果，留待 M5 统一口径）
--    uk_lr_credit_rule_match: 同一来源下的同一对象只允许一条规则。
--                 否则「精确匹配」也会有多条命中，记账时被迫随机取一条，
--                 同一批学员拿到的学分可能不一样
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_credit_rule (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    rule_code           VARCHAR(64)   NOT NULL                COMMENT '规则编码（租户内唯一，大写归一）',
    name                VARCHAR(128)  NOT NULL                COMMENT '规则名称',
    source_type         VARCHAR(16)   NOT NULL                COMMENT '来源类型：COURSE/EXAM/TRAINING',
    match_id            BIGINT        NOT NULL DEFAULT 0      COMMENT '匹配对象ID（0 为该来源全部）',
    credit              DECIMAL(6,1)  NOT NULL DEFAULT 0.0    COMMENT '单次达成所得学分',
    status              VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    remark              VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lr_credit_rule_code (tenant_id, rule_code),
    UNIQUE KEY uk_lr_credit_rule_match (tenant_id, source_type, match_id),
    KEY idx_lr_credit_rule_status (tenant_id, status, source_type)
) COMMENT='学分规则';

-- ---------------------------------------------------------------------
-- 2. 学分台账
--    uk_lr_credit_ledger_source: 同一学员在同一业务事件上只记一次学分。
--                 注意 source_id 的语义按来源区分——EXAM 用考试 ID 而不是作答记录 ID，
--                 否则补考通过会再记一次学分（补考是同一门课，不该重复得分）
--    period_year: 年度汇总的聚合维度，落库而非按 occurred_at 现算，
--                 便于跨年补记时仍归入业务发生年度
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_credit_ledger (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    user_id             BIGINT        NOT NULL                COMMENT '学员用户ID',
    username            VARCHAR(64)   DEFAULT NULL            COMMENT '学员用户名（冗余展示）',
    source_type         VARCHAR(16)   NOT NULL                COMMENT '来源类型：COURSE/EXAM/TRAINING',
    source_id           BIGINT        NOT NULL                COMMENT '来源业务ID（课程/考试/培训班）',
    rule_id             BIGINT        NOT NULL                COMMENT '命中规则ID',
    rule_code           VARCHAR(64)   NOT NULL                COMMENT '规则编码快照',
    credit              DECIMAL(6,1)  NOT NULL DEFAULT 0.0    COMMENT '本次学分',
    occurred_at         DATETIME      NOT NULL                COMMENT '业务发生时间',
    period_year         INT           NOT NULL                COMMENT '归属年度（汇总维度）',
    recorded_by         BIGINT        DEFAULT NULL            COMMENT '记账人ID',
    remark              VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lr_credit_ledger_source (tenant_id, user_id, source_type, source_id),
    KEY idx_lr_credit_ledger_user (tenant_id, user_id, period_year),
    KEY idx_lr_credit_ledger_period (tenant_id, period_year, source_type)
) COMMENT='学分台账';
