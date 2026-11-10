-- =====================================================================
-- BlueWhale 学习培训考试应用（whale-lms）M3-2 考试与判分（Wave 5.87）
-- =====================================================================
-- 范围       : 考试（引用已发布试卷）/ 作答记录 / 作答明细 / 成绩版本
-- 需求       : REQ-FUNC-089（题库与考试，M3）
-- 边界       : 答案与抽题种子只在交卷后经独立权限码查询；未交卷不回显标准答案。
--              强反作弊（人脸/摄像头）按计划 §8 明确不做，仅记时长与切屏次数。
-- 脚本约束   : 禁止 DROP / TRUNCATE / DELETE；可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 考试（聚合根）
--    status:      DRAFT / PUBLISHED / CLOSED
--    shuffle:     是否按每份作答的 seed 打乱题序（判分只按 question_id 比对，
--                 与呈现次序无关）
--    switch_limit:允许的切屏次数上限，0 表示不限制（仅记次，不强制交卷）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_exam (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    exam_code           VARCHAR(64)   NOT NULL                COMMENT '考试编码（租户内唯一，大写归一）',
    title               VARCHAR(128)  NOT NULL                COMMENT '考试名称',
    paper_id            BIGINT        NOT NULL                COMMENT '试卷ID（须为已发布状态）',
    course_id           BIGINT        DEFAULT NULL            COMMENT '关联课程ID（可空）',
    start_time          DATETIME      NOT NULL                COMMENT '开考时间',
    end_time            DATETIME      NOT NULL                COMMENT '截止时间（须晚于开考时间）',
    duration_minutes    INT           NOT NULL DEFAULT 60     COMMENT '单次作答时长（分钟）',
    max_attempts        INT           NOT NULL DEFAULT 1      COMMENT '最大作答次数（>=1）',
    shuffle             TINYINT       NOT NULL DEFAULT 1      COMMENT '是否乱序：0 否 / 1 是',
    pass_score          DECIMAL(6,1)  NOT NULL DEFAULT 60.0   COMMENT '及格分',
    switch_limit        INT           NOT NULL DEFAULT 0      COMMENT '切屏次数上限（0 不限）',
    status              VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED/CLOSED',
    remark              VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lr_exam_code (tenant_id, exam_code),
    KEY idx_lr_exam_status (tenant_id, status, start_time)
) COMMENT='考试';

-- ---------------------------------------------------------------------
-- 2. 作答记录（一人一次一条）
--    status:      IN_PROGRESS / SUBMITTED（已交卷待人工）/ GRADED（已判分）/ EXPIRED（超时未交）
--    deadline_at: min(开始时间 + 时长, 考试截止时间)，提交与保存均以此为准
--    seed:        本份作答的乱序种子（题序与选项序由此派生，判分不受影响）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_exam_attempt (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    exam_id             BIGINT        NOT NULL                COMMENT '考试ID',
    paper_id            BIGINT        NOT NULL                COMMENT '试卷ID（快照，考试换卷不影响历史）',
    user_id             BIGINT        NOT NULL                COMMENT '学员用户ID',
    username            VARCHAR(64)   DEFAULT NULL            COMMENT '学员用户名（冗余展示）',
    attempt_no          INT           NOT NULL DEFAULT 1      COMMENT '第几次作答（从 1 起）',
    seed                VARCHAR(64)   NOT NULL                COMMENT '本份作答乱序种子',
    started_at          DATETIME      NOT NULL                COMMENT '开始作答时间',
    deadline_at         DATETIME      NOT NULL                COMMENT '本次截止时间',
    submitted_at        DATETIME      DEFAULT NULL            COMMENT '交卷时间',
    status              VARCHAR(16)   NOT NULL DEFAULT 'IN_PROGRESS' COMMENT '状态：IN_PROGRESS/SUBMITTED/GRADED/EXPIRED',
    objective_score     DECIMAL(6,1)  DEFAULT NULL            COMMENT '客观题得分（交卷时计算）',
    subjective_score    DECIMAL(6,1)  DEFAULT NULL            COMMENT '主观题得分（人工判分后累计）',
    total_score         DECIMAL(6,1)  DEFAULT NULL            COMMENT '总分',
    passed              TINYINT       DEFAULT NULL            COMMENT '是否及格：0 否 / 1 是',
    switch_count        INT           NOT NULL DEFAULT 0      COMMENT '切屏次数（仅记次）',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lr_attempt (tenant_id, exam_id, user_id, attempt_no),
    KEY idx_lr_attempt_user (tenant_id, user_id, started_at),
    KEY idx_lr_attempt_status (tenant_id, exam_id, status)
) COMMENT='考试作答记录';

-- ---------------------------------------------------------------------
-- 3. 作答明细（一题一条）
--    graded: 0 待判（主观题交卷后置 0）/ 1 已判
--    score:  本题得分；客观题交卷即写入，主观题由人工判分写入
--    sort_no:本份作答的呈现次序（按 seed 派生，与判分无关）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_exam_answer (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    attempt_id          BIGINT        NOT NULL                COMMENT '作答记录ID',
    question_id         BIGINT        NOT NULL                COMMENT '题目ID',
    question_type       VARCHAR(16)   NOT NULL                COMMENT '题型快照',
    answer              VARCHAR(2000) DEFAULT NULL            COMMENT '学员作答（多选题为逗号分隔，已归一）',
    correct             TINYINT       DEFAULT NULL            COMMENT '是否正确：0 否 / 1 是（主观题为空）',
    score               DECIMAL(5,1)  DEFAULT NULL            COMMENT '本题得分',
    graded              TINYINT       NOT NULL DEFAULT 0      COMMENT '是否已判：0 待判 / 1 已判',
    sort_no             INT           NOT NULL DEFAULT 1      COMMENT '呈现次序',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lr_exam_answer (tenant_id, attempt_id, question_id),
    KEY idx_lr_exam_answer_pending (tenant_id, attempt_id, graded)
) COMMENT='考试作答明细';

-- ---------------------------------------------------------------------
-- 4. 成绩（按版本留存）
--    每次人工改分写新版本，历史版本不覆盖，便于申诉追溯
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_score (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    attempt_id          BIGINT        NOT NULL                COMMENT '作答记录ID',
    exam_id             BIGINT        NOT NULL                COMMENT '考试ID',
    user_id             BIGINT        NOT NULL                COMMENT '学员用户ID',
    version_no          INT           NOT NULL DEFAULT 1      COMMENT '成绩版本（从 1 起，重判递增）',
    objective_score     DECIMAL(6,1)  NOT NULL DEFAULT 0.0    COMMENT '客观题得分',
    subjective_score    DECIMAL(6,1)  NOT NULL DEFAULT 0.0    COMMENT '主观题得分',
    total_score         DECIMAL(6,1)  NOT NULL DEFAULT 0.0    COMMENT '总分',
    passed              TINYINT       NOT NULL DEFAULT 0      COMMENT '是否及格：0 否 / 1 是',
    graded_by           BIGINT        DEFAULT NULL            COMMENT '判分人ID',
    graded_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '判分时间',
    remark              VARCHAR(500)  DEFAULT NULL            COMMENT '判分备注（重判原因）',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lr_score (tenant_id, attempt_id, version_no),
    KEY idx_lr_score_user (tenant_id, user_id, graded_at)
) COMMENT='考试成绩（版本化）';
