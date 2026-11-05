-- =====================================================================
-- BlueWhale 微服务平台 - OA 应用（whale-oa） Flyway 版本化迁移 V4
-- =====================================================================
-- 适用库   : bluewhale_oa（MySQL 8.0+，utf8mb4）
-- 上级计划 : docs/plans/2026-11-04-wave5.82-oa-panorama.md
-- 里程碑   : Wave 5.82 M1A-2 / M1A-3 后端切片（请假闭环额度 + 员工扩展档案）
-- 说明     : 1) 新建员工扩展档案、假期类型、额度、额度流水、工作日历 5 张表；
--            2) oa_leave 逐列幂等扩展申请人/假期类型/半天时段/交接人/审批实例；
--            3) 种子 3 类假期类型（ANNUAL / SICK / PERSONAL），tenant_id = 1。
-- 幂等     : CREATE TABLE IF NOT EXISTS；扩展列用 information_schema 守卫后
--            PREPARE/EXECUTE；种子用 INSERT ... WHERE NOT EXISTS。
-- 规则     : 禁止 DROP / TRUNCATE / DELETE；表间逻辑外键，不建物理 FOREIGN KEY。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 员工扩展档案（一人一条；姓名/部门/手机号仍归 Auth，不复制）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_employee_profile (
    id                BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    user_id           BIGINT       NOT NULL            COMMENT '用户ID（逻辑关联 sys_user.id）',
    employee_no       VARCHAR(64)  DEFAULT NULL        COMMENT '工号',
    dept_id           BIGINT       DEFAULT NULL        COMMENT '部门ID（逻辑关联 sys_dept.id）',
    hire_date         DATE         DEFAULT NULL        COMMENT '入职日期',
    regular_date      DATE         DEFAULT NULL        COMMENT '转正日期',
    birthday          DATE         DEFAULT NULL        COMMENT '生日',
    employment_status VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'
                                                      COMMENT '在职状态：ACTIVE 在职 / PROBATION 试用 / REGULAR 正式 / LEFT 离职',
    leave_date        DATE         DEFAULT NULL        COMMENT '离职日期',
    culture_visible   TINYINT      NOT NULL DEFAULT 1  COMMENT '文化墙是否公开展示：1 展示 / 0 不展示',
    remark            VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_employee_profile_user (tenant_id, user_id),
    KEY idx_oa_employee_profile_dept (tenant_id, dept_id),
    KEY idx_oa_employee_profile_no (tenant_id, employee_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 员工扩展档案表';

-- ---------------------------------------------------------------------
-- 2. 假期类型（租户内编码唯一；额度默认按年）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_leave_type (
    id                  BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code                VARCHAR(32)  NOT NULL            COMMENT '假期类型编码（租户内唯一），如 ANNUAL',
    name                VARCHAR(64)  NOT NULL            COMMENT '假期类型名称',
    unit                VARCHAR(16)  NOT NULL DEFAULT 'DAY' COMMENT '计量单位：DAY 天 / HOUR 小时',
    annual_quota        DECIMAL(6,1) NOT NULL DEFAULT 0.0 COMMENT '年度默认额度',
    min_unit            DECIMAL(4,2) NOT NULL DEFAULT 0.5 COMMENT '最小请假单位（0.5 表示半天）',
    requires_attachment TINYINT      NOT NULL DEFAULT 0  COMMENT '是否必须上传附件：1 是 / 0 否',
    allow_negative      TINYINT      NOT NULL DEFAULT 0  COMMENT '是否允许负额度：1 允许 / 0 不允许',
    enabled             TINYINT      NOT NULL DEFAULT 1  COMMENT '是否启用：1 启用 / 0 停用',
    sort_no             INT          NOT NULL DEFAULT 0  COMMENT '排序号',
    remark              VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_leave_type_code (tenant_id, code),
    KEY idx_oa_leave_type_enabled (tenant_id, enabled, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 假期类型表';

-- ---------------------------------------------------------------------
-- 3. 年度假期额度（一人一类型一年一条）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_leave_balance (
    id            BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    user_id       BIGINT       NOT NULL            COMMENT '用户ID',
    leave_type_id BIGINT       NOT NULL            COMMENT '假期类型ID',
    balance_year  INT          NOT NULL            COMMENT '额度年份',
    total_days    DECIMAL(6,1) NOT NULL DEFAULT 0.0 COMMENT '总额度',
    used_days     DECIMAL(6,1) NOT NULL DEFAULT 0.0 COMMENT '已使用（审批通过后扣减）',
    frozen_days   DECIMAL(6,1) NOT NULL DEFAULT 0.0 COMMENT '审批中冻结',
    remark        VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_leave_balance (tenant_id, user_id, leave_type_id, balance_year),
    KEY idx_oa_leave_balance_user_year (tenant_id, user_id, balance_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 年度假期额度表';

-- ---------------------------------------------------------------------
-- 4. 额度流水（只追加；action 决定方向，days 恒为正数）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_leave_balance_log (
    id             BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    balance_id     BIGINT       NOT NULL            COMMENT '额度ID',
    user_id        BIGINT       NOT NULL            COMMENT '用户ID',
    leave_type_id  BIGINT       NOT NULL            COMMENT '假期类型ID',
    balance_year   INT          NOT NULL            COMMENT '额度年份',
    action         VARCHAR(16)  NOT NULL            COMMENT '动作：INIT 初始化 / ADJUST 调整 / FREEZE 冻结 / RELEASE 释放 / DEDUCT 扣减',
    days           DECIMAL(6,1) NOT NULL            COMMENT '变动天数（正数，方向由 action 决定）',
    before_total   DECIMAL(6,1) NOT NULL DEFAULT 0.0 COMMENT '变更前总额度',
    after_total    DECIMAL(6,1) NOT NULL DEFAULT 0.0 COMMENT '变更后总额度',
    before_used    DECIMAL(6,1) NOT NULL DEFAULT 0.0 COMMENT '变更前已使用',
    after_used     DECIMAL(6,1) NOT NULL DEFAULT 0.0 COMMENT '变更后已使用',
    before_frozen  DECIMAL(6,1) NOT NULL DEFAULT 0.0 COMMENT '变更前冻结',
    after_frozen   DECIMAL(6,1) NOT NULL DEFAULT 0.0 COMMENT '变更后冻结',
    biz_type       VARCHAR(32)  DEFAULT NULL        COMMENT '业务类型，如 LEAVE',
    biz_id         BIGINT       DEFAULT NULL        COMMENT '业务单据ID',
    operator_id    BIGINT       DEFAULT NULL        COMMENT '操作人用户ID（系统任务为空）',
    remark         VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_oa_leave_balance_log_balance (tenant_id, balance_id, create_time),
    KEY idx_oa_leave_balance_log_user (tenant_id, user_id, balance_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 假期额度流水表';

-- ---------------------------------------------------------------------
-- 5. 工作日历（租户内日期唯一；无记录时按周一至周五默认工作）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_work_calendar (
    id            BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    calendar_date DATE         NOT NULL            COMMENT '日历日期',
    workday       TINYINT      NOT NULL DEFAULT 1  COMMENT '是否工作日：1 是 / 0 否（节假日/调休）',
    work_hours    DECIMAL(4,1) NOT NULL DEFAULT 8.0 COMMENT '当日标准工时',
    holiday_name  VARCHAR(64)  DEFAULT NULL        COMMENT '节假日名称',
    remark        VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_work_calendar (tenant_id, calendar_date),
    KEY idx_oa_work_calendar_range (tenant_id, calendar_date, workday)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 工作日历表';

-- ---------------------------------------------------------------------
-- 6. oa_leave 扩展列（逐列 information_schema 守卫，缺失才加）
-- ---------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oa_leave' AND COLUMN_NAME = 'applicant_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE oa_leave ADD COLUMN applicant_id BIGINT DEFAULT NULL COMMENT ''申请人用户ID（逻辑关联 sys_user.id）'' AFTER applicant',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oa_leave' AND COLUMN_NAME = 'leave_type_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE oa_leave ADD COLUMN leave_type_id BIGINT DEFAULT NULL COMMENT ''假期类型ID（逻辑关联 oa_leave_type.id）'' AFTER leave_type',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oa_leave' AND COLUMN_NAME = 'start_period');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE oa_leave ADD COLUMN start_period VARCHAR(8) NOT NULL DEFAULT ''AM'' COMMENT ''开始时段：AM 上午 / PM 下午'' AFTER start_date',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oa_leave' AND COLUMN_NAME = 'end_period');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE oa_leave ADD COLUMN end_period VARCHAR(8) NOT NULL DEFAULT ''PM'' COMMENT ''结束时段：AM 上午 / PM 下午'' AFTER end_date',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oa_leave' AND COLUMN_NAME = 'handover_user_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE oa_leave ADD COLUMN handover_user_id BIGINT DEFAULT NULL COMMENT ''工作交接人用户ID'' AFTER end_period',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'oa_leave' AND COLUMN_NAME = 'approval_instance_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE oa_leave ADD COLUMN approval_instance_id BIGINT DEFAULT NULL COMMENT ''审批实例ID（逻辑关联 oa_approval_instance.id）'' AFTER handover_user_id',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 7. 种子假期类型（默认租户 tenant_id = 1）
-- ---------------------------------------------------------------------
INSERT INTO oa_leave_type
    (id, tenant_id, code, name, unit, annual_quota, min_unit, requires_attachment,
     allow_negative, enabled, sort_no, remark, deleted)
SELECT 900000000000000401, 1, 'ANNUAL', '年假', 'DAY', 5.0, 0.5, 0, 0, 1, 1,
       'M1A-2 种子：年假（默认 5 天）', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_leave_type WHERE tenant_id = 1 AND code = 'ANNUAL'
);

INSERT INTO oa_leave_type
    (id, tenant_id, code, name, unit, annual_quota, min_unit, requires_attachment,
     allow_negative, enabled, sort_no, remark, deleted)
SELECT 900000000000000402, 1, 'SICK', '病假', 'DAY', 10.0, 0.5, 1, 1, 1, 2,
       'M1A-2 种子：病假（需附件、允许负额度）', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_leave_type WHERE tenant_id = 1 AND code = 'SICK'
);

INSERT INTO oa_leave_type
    (id, tenant_id, code, name, unit, annual_quota, min_unit, requires_attachment,
     allow_negative, enabled, sort_no, remark, deleted)
SELECT 900000000000000403, 1, 'PERSONAL', '事假', 'DAY', 0.0, 0.5, 0, 1, 1, 3,
       'M1A-2 种子：事假（无默认额度、允许负额度）', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_leave_type WHERE tenant_id = 1 AND code = 'PERSONAL'
);

-- =====================================================================
-- 完成。校验建议：
--   SHOW COLUMNS FROM oa_leave LIKE '%period%';
--   -- 期望：start_period / end_period / handover_user_id / approval_instance_id。
--   SELECT code, name, annual_quota FROM oa_leave_type WHERE tenant_id = 1 ORDER BY sort_no;
--   -- 期望：ANNUAL / SICK / PERSONAL 三行。
-- =====================================================================
