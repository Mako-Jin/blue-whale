-- =====================================================================
-- BlueWhale 学习培训考试应用（whale-lms）M3-1 题库与试卷（Wave 5.87）
-- =====================================================================
-- 范围       : 题库题目 / 题目选项 / 试卷 / 试卷题目（含规则抽题快照）
-- 需求       : REQ-FUNC-089（题库与考试，M3）
-- 边界       : 题目与试卷只存结构化内容，不存附件字节（图片题走 lr_question.stem 中的
--              fileRefId 引用，由 whale-file 托管）；考试作答与判分在 M3-2 单独迁移。
-- 脚本约束   : 禁止 DROP / TRUNCATE / DELETE；可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 题库题目（聚合根）
--    question_type: SINGLE / MULTIPLE / JUDGE / SHORT_ANSWER
--    difficulty:    EASY / NORMAL / HARD
--    status:        DRAFT（可编辑、可不完整）/ ACTIVE（启用，须通过完整性校验）
--    standard_answer: 单选题为选项键；多选题为逗号分隔且升序归一；判断题为 TRUE / FALSE；
--                     简答题可空（仅作参考要点）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_question (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    question_code       VARCHAR(64)   NOT NULL                COMMENT '题目编码（租户内唯一，大写归一）',
    category            VARCHAR(64)   NOT NULL                COMMENT '题目分类',
    question_type       VARCHAR(16)   NOT NULL                COMMENT '题型：SINGLE/MULTIPLE/JUDGE/SHORT_ANSWER',
    difficulty          VARCHAR(16)   NOT NULL DEFAULT 'NORMAL' COMMENT '难度：EASY/NORMAL/HARD',
    tags                VARCHAR(255)  DEFAULT NULL            COMMENT '标签（逗号分隔）',
    stem                VARCHAR(2000) NOT NULL                COMMENT '题干',
    standard_answer     VARCHAR(1000) DEFAULT NULL            COMMENT '标准答案（已归一）',
    analysis            VARCHAR(2000) DEFAULT NULL            COMMENT '答案解析',
    default_score       DECIMAL(5,1)  NOT NULL DEFAULT 1.0    COMMENT '默认分值（>0）',
    status              VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/ACTIVE',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lr_question_code (tenant_id, question_code),
    KEY idx_lr_question_pick (tenant_id, status, category, question_type, difficulty)
) COMMENT='题库题目';

-- ---------------------------------------------------------------------
-- 2. 题目选项（仅 SINGLE / MULTIPLE 使用；JUDGE 与简答题无选项）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_question_option (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    question_id         BIGINT        NOT NULL                COMMENT '题目ID',
    option_key          VARCHAR(8)    NOT NULL                COMMENT '选项键（A/B/C…，题内唯一）',
    content             VARCHAR(500)  NOT NULL                COMMENT '选项内容',
    is_correct          TINYINT       NOT NULL DEFAULT 0      COMMENT '是否正确答案：0 否 / 1 是',
    sort_no             INT           NOT NULL DEFAULT 1      COMMENT '排序号',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lr_question_option (tenant_id, question_id, option_key)
) COMMENT='题目选项';

-- ---------------------------------------------------------------------
-- 3. 试卷（聚合根）
--    status:        DRAFT / PUBLISHED / ARCHIVED（PUBLISHED 后题目冻结）
--    total_score / question_count 由题目明细重算回写，不接受前端直填，避免与明细不一致
--    seed:          抽题种子；同一 seed + 同一题库 => 同一套题（可复现）
--    rule_config:   抽题规则 JSON 快照，便于事后追查「这套题是怎么抽出来的」
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_paper (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    paper_code          VARCHAR(64)   NOT NULL                COMMENT '试卷编码（租户内唯一，大写归一）',
    title               VARCHAR(128)  NOT NULL                COMMENT '试卷名称',
    course_id           BIGINT        DEFAULT NULL            COMMENT '关联课程ID（可空）',
    duration_minutes    INT           NOT NULL DEFAULT 60     COMMENT '考试时长（分钟，>0）',
    total_score         DECIMAL(6,1)  NOT NULL DEFAULT 0.0    COMMENT '总分（明细累加）',
    pass_score          DECIMAL(6,1)  NOT NULL DEFAULT 60.0   COMMENT '及格分（0..总分）',
    question_count      INT           NOT NULL DEFAULT 0      COMMENT '题目数（明细计数）',
    seed                VARCHAR(64)   DEFAULT NULL            COMMENT '抽题种子（固定则可复现）',
    rule_config         TEXT          DEFAULT NULL            COMMENT '抽题规则 JSON 快照',
    status              VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PUBLISHED/ARCHIVED',
    remark              VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lr_paper_code (tenant_id, paper_code),
    KEY idx_lr_paper_status (tenant_id, status)
) COMMENT='试卷';

-- ---------------------------------------------------------------------
-- 4. 试卷题目（一题一条；同一试卷内同一题不得重复）
--    source: MANUAL（手工添加）/ RULE（规则抽取）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_paper_question (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    paper_id            BIGINT        NOT NULL                COMMENT '试卷ID',
    question_id         BIGINT        NOT NULL                COMMENT '题目ID',
    score               DECIMAL(5,1)  NOT NULL DEFAULT 1.0    COMMENT '本题分值（>0）',
    sort_no             INT           NOT NULL DEFAULT 1      COMMENT '题序（从 1 起）',
    source              VARCHAR(16)   NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL/RULE',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lr_paper_question (tenant_id, paper_id, question_id),
    KEY idx_lr_paper_question_order (tenant_id, paper_id, sort_no)
) COMMENT='试卷题目';
