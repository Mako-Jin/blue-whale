-- =====================================================================
-- BlueWhale 学习培训考试应用（whale-lms）M2 培训计划（Wave 5.87）
-- =====================================================================
-- 范围       : 培训班聚合（培训班 / 场次）+ 必修分配 + 面授签到 + 讲师课时台账
-- 需求       : REQ-FUNC-088（培训计划，M2）
-- 边界       : 学员身份引用 Auth / ERP 员工主数据，本库不建人事表；
--              必修分配落「名单快照 + 范围标记」，M5 接 ERP 员工目录后按
--              scope_type/scope_value 做增量补人，不在本库解析组织架构。
-- 脚本约束   : 禁止 DROP / TRUNCATE / DELETE；可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 培训班（聚合根）
--    status:     DRAFT / PUBLISHED / CLOSED（CLOSED 终态，仅可查）
--    mode:       ONLINE / OFFLINE / BLENDED
--    course_id:  线上培训可挂课程（lr_course），面授可为空
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_training_class (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    class_code          VARCHAR(64)   NOT NULL                COMMENT '培训班编码（租户内唯一，大写归一）',
    title               VARCHAR(128)  NOT NULL                COMMENT '培训班名称',
    course_id           BIGINT        DEFAULT NULL            COMMENT '关联课程ID（可空）',
    mode                VARCHAR(16)   NOT NULL DEFAULT 'OFFLINE' COMMENT '形式：ONLINE / OFFLINE / BLENDED',
    lecturer            VARCHAR(64)   DEFAULT NULL            COMMENT '主讲讲师',
    start_date          DATE          NOT NULL                COMMENT '开班日期',
    end_date            DATE          NOT NULL                COMMENT '结班日期（不得早于开班日期）',
    location            VARCHAR(256)  DEFAULT NULL            COMMENT '培训地点',
    capacity            INT           DEFAULT NULL            COMMENT '计划人数（空为不限）',
    status              VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT / PUBLISHED / CLOSED',
    remark              VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lr_class_code (tenant_id, class_code),
    KEY idx_lr_class_status (tenant_id, status, start_date),
    KEY idx_lr_class_course (tenant_id, course_id)
) COMMENT='培训班（培训主数据聚合根）';

-- ---------------------------------------------------------------------
-- 2. 培训班场次（聚合内实体；课时以场次为最小计量单位）
--    hours: 本节课时，讲师课时台账按场次累加
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_class_session (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    class_id            BIGINT        NOT NULL                COMMENT '所属培训班ID',
    session_no          INT           NOT NULL                COMMENT '场次序号（班内唯一，从 1 起）',
    title               VARCHAR(128)  NOT NULL                COMMENT '场次主题',
    start_time          DATETIME      NOT NULL                COMMENT '开始时间',
    end_time            DATETIME      NOT NULL                COMMENT '结束时间（必须晚于开始时间）',
    location            VARCHAR(256)  DEFAULT NULL            COMMENT '场次地点',
    lecturer            VARCHAR(64)   DEFAULT NULL            COMMENT '场次讲师（空则继承班级讲师）',
    hours               DECIMAL(5,1)  NOT NULL DEFAULT 0.0    COMMENT '本节课时（0-999.9，一位小数）',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lr_session_no (tenant_id, class_id, session_no),
    KEY idx_lr_session_time (tenant_id, class_id, start_time)
) COMMENT='培训班场次（课时最小计量单位）';

-- ---------------------------------------------------------------------
-- 3. 必修分配（名单快照 + 范围标记）
--    scope_type: ALL / DEPT / ROLE / USER（仅用于追溯与 M5 增量补人，
--                本期名单由调用方解析后随请求提交，服务端只做批量落库）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_assignment (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    class_id            BIGINT        NOT NULL                COMMENT '培训班ID',
    scope_type          VARCHAR(16)   NOT NULL DEFAULT 'USER' COMMENT '分配范围：ALL / DEPT / ROLE / USER',
    scope_value         VARCHAR(128)  DEFAULT NULL            COMMENT '范围取值（部门编码/角色编码，USER 为空）',
    user_id             BIGINT        NOT NULL                COMMENT '学员用户ID',
    username            VARCHAR(64)   DEFAULT NULL            COMMENT '学员用户名（冗余展示）',
    assigned_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分配时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lr_assignment (tenant_id, class_id, user_id),
    KEY idx_lr_assignment_user (tenant_id, user_id, assigned_at)
) COMMENT='必修分配（一人一班一条，批量报名幂等兜底）';

-- ---------------------------------------------------------------------
-- 4. 签到记录（场次 × 学员，租户内唯一 → 重复签到幂等兜底）
--    checkin_type: SELF（时间窗内自助）/ SUPPLEMENT（补签，必须留 operator 与备注）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_checkin (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    class_id            BIGINT        NOT NULL                COMMENT '培训班ID（冗余，便于按班统计）',
    session_id          BIGINT        NOT NULL                COMMENT '场次ID',
    user_id             BIGINT        NOT NULL                COMMENT '学员用户ID',
    username            VARCHAR(64)   DEFAULT NULL            COMMENT '学员用户名（冗余展示）',
    checkin_type        VARCHAR(16)   NOT NULL DEFAULT 'SELF' COMMENT '签到方式：SELF / SUPPLEMENT',
    checkin_at          DATETIME      NOT NULL                COMMENT '签到时间',
    operator_id         BIGINT        DEFAULT NULL            COMMENT '操作人ID（补签必填）',
    remark              VARCHAR(200)  DEFAULT NULL            COMMENT '备注（补签必填，留痕）',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lr_checkin (tenant_id, session_id, user_id),
    KEY idx_lr_checkin_class (tenant_id, class_id, checkin_at)
) COMMENT='签到记录（补签留操作人与备注）';

-- ---------------------------------------------------------------------
-- 5. 讲师课时台账（场次 × 讲师，一台账行一笔入账）
--    合计口径：某班级某讲师课时 = 其名下各场次 hours 之和
--    说明：不建唯一键——场次改讲师时旧台账行按逻辑删除保留痕迹，
--          若用 (session_id, lecturer) 唯一键，删后重加同讲师会撞键。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_lecturer_hours (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    class_id            BIGINT        NOT NULL                COMMENT '培训班ID',
    session_id          BIGINT        NOT NULL                COMMENT '场次ID',
    lecturer            VARCHAR(64)   NOT NULL                COMMENT '讲师',
    hours               DECIMAL(5,1)  NOT NULL DEFAULT 0.0    COMMENT '本节课时（与场次一致）',
    accumulated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入账时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_lr_lecturer_session (tenant_id, session_id, lecturer),
    KEY idx_lr_lecturer_class (tenant_id, class_id, lecturer)
) COMMENT='讲师课时台账（场次维度入账）';
