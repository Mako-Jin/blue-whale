-- =====================================================================
-- BlueWhale 学习培训考试应用（whale-lms）M1 课程与学习（Wave 5.87）
-- =====================================================================
-- 范围       : 课程聚合（课程 / 章节 / 课件）+ 学习记录 + 章节进度
-- 需求       : REQ-FUNC-087（课程与学习，M1）
-- 边界       : 课件字节归 whale-file（lr_courseware.file_ref_id 只存引用）；
--              学员身份引用 Auth / ERP 员工主数据，本库不建人事表。
-- 脚本约束   : 禁止 DROP / TRUNCATE / DELETE；可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 课程（聚合根）
--    status: DRAFT / PUBLISHED / ARCHIVED
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_course (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    course_code         VARCHAR(64)   NOT NULL                COMMENT '课程编码（租户内唯一，大写归一）',
    title               VARCHAR(128)  NOT NULL                COMMENT '课程名称',
    category            VARCHAR(64)   DEFAULT NULL            COMMENT '分类',
    description         VARCHAR(1000) DEFAULT NULL            COMMENT '简介',
    lecturer            VARCHAR(64)   DEFAULT NULL            COMMENT '讲师',
    status              VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT / PUBLISHED / ARCHIVED',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lr_course_code (tenant_id, course_code),
    KEY idx_lr_course_status (tenant_id, status, update_time),
    KEY idx_lr_course_category (tenant_id, category)
) COMMENT='课程（学习主数据聚合根）';

-- ---------------------------------------------------------------------
-- 2. 课程章节（聚合内实体）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_course_chapter (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    course_id           BIGINT        NOT NULL                COMMENT '所属课程ID',
    title               VARCHAR(128)  NOT NULL                COMMENT '章节标题',
    sort_no             INT           NOT NULL DEFAULT 0      COMMENT '排序号（升序展示）',
    remark              VARCHAR(500)  DEFAULT NULL            COMMENT '简介备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_lr_chapter_course (tenant_id, course_id, sort_no)
) COMMENT='课程章节';

-- ---------------------------------------------------------------------
-- 3. 课件（聚合内实体，挂在章节下）
--    ware_type: DOCUMENT / VIDEO / LINK
--    file_ref_id 归 whale-file；LINK 类型仅存 link_url
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_courseware (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    course_id           BIGINT        NOT NULL                COMMENT '所属课程ID',
    chapter_id          BIGINT        NOT NULL                COMMENT '所属章节ID',
    name                VARCHAR(128)  NOT NULL                COMMENT '课件名称',
    ware_type           VARCHAR(16)   NOT NULL                COMMENT '类型：DOCUMENT / VIDEO / LINK',
    file_ref_id         VARCHAR(64)   DEFAULT NULL            COMMENT 'whale-file 文件引用ID（LINK 为空）',
    link_url            VARCHAR(512)  DEFAULT NULL            COMMENT '外部链接（仅 LINK）',
    sort_no             INT           NOT NULL DEFAULT 0      COMMENT '排序号',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_lr_ware_chapter (tenant_id, chapter_id, sort_no),
    KEY idx_lr_ware_course (tenant_id, course_id)
) COMMENT='课件（字节归 whale-file，仅存引用）';

-- ---------------------------------------------------------------------
-- 4. 学习报名记录（学员 × 课程，租户内唯一 → 报名幂等的数据库兜底）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_enrollment (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    course_id           BIGINT        NOT NULL                COMMENT '课程ID',
    user_id             BIGINT        NOT NULL                COMMENT '学员用户ID（引用 Auth / ERP 员工）',
    username            VARCHAR(64)   DEFAULT NULL            COMMENT '学员用户名（冗余展示）',
    enrolled_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lr_enrollment (tenant_id, course_id, user_id),
    KEY idx_lr_enroll_user (tenant_id, user_id, enrolled_at)
) COMMENT='学习报名记录（一人一课一条，幂等兜底）';

-- ---------------------------------------------------------------------
-- 5. 章节学习进度（学习记录 × 章节，租户内唯一）
--    percent 只增不减：应用层取历史与上报的较大值
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lr_progress (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    enrollment_id       BIGINT        NOT NULL                COMMENT '学习记录ID',
    chapter_id          BIGINT        NOT NULL                COMMENT '章节ID',
    percent             INT           NOT NULL DEFAULT 0      COMMENT '完成百分比（0-100，只增不减）',
    last_learned_at     DATETIME      DEFAULT NULL            COMMENT '最近学习时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lr_progress (tenant_id, enrollment_id, chapter_id),
    KEY idx_lr_progress_enroll (tenant_id, enrollment_id)
) COMMENT='章节学习进度（只增不减）';
