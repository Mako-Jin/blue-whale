-- =====================================================================
-- BlueWhale 学习培训考试应用（whale-lms）M6 学习看板指标快照（Wave 5.87）
-- =====================================================================
-- 范围       : 学习覆盖率 / 完成率 / 考试通过率 / 学分分布所需的计数快照
-- 需求       : REQ-FUNC-090（学习看板与试点复盘，M6）
-- 边界       : 只存计数，不存明细；明细仍归各业务上下文，看板不复制业务数据。
-- 脚本约束   : 禁止 DROP / TRUNCATE / DELETE；可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 指标快照
--    uk_lr_metric_snapshot: 同一统计日 + 同一范围只保留一条，重跑覆盖（不是追加）。
--                 追加会让同一天出现多份快照，看板不知道该信哪一份；
--                 覆盖则「补跑」天然幂等，而不同统计日的行各自保留，形成趋势曲线。
--
--    scope_type / scope_id: 统计范围。ALL 全院（scope_id 固定 0）/
--                 COURSE 单课程 / CLASS 单培训班。范围行是「下钻用」，
--                 报表头部的总量另存一条 ALL 行——不从范围行加总得出：
--                 一个人可能同时在多门课里，加总会把人数重复计数。
--
--    scope_name: 范围名称在统计当时落库。课程改名后历史快照不该跟着变，
--                 否则上个月的报告会「自己改名」，对不上已发出去的复盘材料。
--
--    只存分子分母、不存比率：比率现算。
--                 存了比率就得连精度一起存，而四舍五入后的比率既不能加也不能平均，
--                 将来换分母口径还要回填历史值。
--
--    分母不追溯：member_total 是统计日当时的在职人数。
--                 人员离职后不重算历史覆盖率——历史报告应当反映当时的事实，
--                 否则每次有人离职，去年的覆盖率都会悄悄变一次。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_metric_snapshot (
    id                  BIGINT         NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT         NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    stat_date           DATE           NOT NULL                COMMENT '统计日（趋势维度，同日重跑覆盖）',
    scope_type          VARCHAR(16)    NOT NULL DEFAULT 'ALL'  COMMENT '统计范围：ALL 全院 / COURSE 课程 / CLASS 培训班',
    scope_id            BIGINT         NOT NULL DEFAULT 0      COMMENT '范围对象ID（ALL 固定 0）',
    scope_name          VARCHAR(128)   NOT NULL                COMMENT '范围名称快照（防改名导致历史报告漂移）',
    member_total        INT            NOT NULL DEFAULT 0      COMMENT '在职人数（覆盖率分母，统计日快照）',
    learner_total       INT            NOT NULL DEFAULT 0      COMMENT '学习人数（有报名记录的去重学员数）',
    learner_completed   INT            NOT NULL DEFAULT 0      COMMENT '完成人数（名下报名全部完成章节的去重学员数）',
    exam_taker_total    INT            NOT NULL DEFAULT 0      COMMENT '参考人数（已交卷的去重学员数）',
    exam_passed_total   INT            NOT NULL DEFAULT 0      COMMENT '通过人数（存在及格成绩的去重学员数，补考通过计一次）',
    credit_total        DECIMAL(12,1)  NOT NULL DEFAULT 0.0    COMMENT '学分总量',
    credit_earners      INT            NOT NULL DEFAULT 0      COMMENT '获学分人数',
    computed_at         DATETIME       NOT NULL                COMMENT '计算时间',
    create_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT        NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lr_metric_snapshot (tenant_id, stat_date, scope_type, scope_id),
    KEY idx_lr_metric_snapshot_date (tenant_id, stat_date, scope_type)
) COMMENT='学习看板指标快照';
