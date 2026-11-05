-- ---------------------------------------------------------------------
-- M5 人员绩效（workforce）基础表
-- 说明：本脚本仅新增表结构，禁止任何 DROP/TRUNCATE/DELETE。
--       全部表统一 InnoDB + utf8mb4，并携带 tenant_id 预留逻辑字段。
-- ---------------------------------------------------------------------

-- ---------------------------------------------------------------------
-- 1. 班组成员
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_wf_team_member (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    team_id BIGINT NOT NULL COMMENT '班组 ID',
    team_code VARCHAR(64) NOT NULL COMMENT '班组编码',
    employee_id BIGINT NOT NULL COMMENT '员工 ID',
    employee_name VARCHAR(64) NOT NULL COMMENT '员工姓名',
    primary_operator TINYINT NOT NULL DEFAULT 0 COMMENT '是否主操作员：0 否，1 是',
    skill_level VARCHAR(16) NOT NULL DEFAULT 'JUNIOR' COMMENT '技能等级：JUNIOR/MIDDLE/SENIOR/EXPERT',
    join_date DATE NULL COMMENT '入组日期',
    leave_date DATE NULL COMMENT '离组日期',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_by BIGINT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_wf_team_member (tenant_id, team_id, employee_id),
    KEY idx_mes_wf_team_member_employee (tenant_id, employee_id, status),
    KEY idx_mes_wf_team_member_team (tenant_id, team_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 班组成员';

-- ---------------------------------------------------------------------
-- 2. 排班分配
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_wf_shift_assignment (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    shift_date DATE NOT NULL COMMENT '排班日期',
    shift_code VARCHAR(64) NOT NULL COMMENT '班次编码',
    team_id BIGINT NOT NULL COMMENT '班组 ID',
    team_code VARCHAR(64) NOT NULL COMMENT '班组编码',
    station_id BIGINT NOT NULL COMMENT '工位 ID',
    station_code VARCHAR(64) NOT NULL COMMENT '工位编码',
    work_center_id BIGINT NULL COMMENT '工作中心 ID',
    work_center_code VARCHAR(64) NULL COMMENT '工作中心编码',
    employee_id BIGINT NOT NULL COMMENT '员工 ID',
    employee_name VARCHAR(64) NOT NULL COMMENT '员工姓名',
    primary_operator TINYINT NOT NULL DEFAULT 0 COMMENT '是否主操作：0 否，1 是',
    start_at DATETIME NULL COMMENT '班次开始时间',
    end_at DATETIME NULL COMMENT '班次结束时间',
    status VARCHAR(16) NOT NULL DEFAULT 'ASSIGNED' COMMENT '状态：ASSIGNED/CANCELLED',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_by BIGINT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_wf_shift_assignment (tenant_id, station_id, shift_date, shift_code, employee_id),
    KEY idx_mes_wf_shift_assignment_team (tenant_id, team_id, shift_date),
    KEY idx_mes_wf_shift_assignment_employee (tenant_id, employee_id, shift_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 排班分配';

-- ---------------------------------------------------------------------
-- 3. 交接班
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_wf_handover (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    handover_no VARCHAR(64) NOT NULL COMMENT '交接单号',
    team_id BIGINT NOT NULL COMMENT '班组 ID',
    team_code VARCHAR(64) NOT NULL COMMENT '班组编码',
    from_shift_code VARCHAR(64) NOT NULL COMMENT '交班班次编码',
    to_shift_code VARCHAR(64) NOT NULL COMMENT '接班班次编码',
    handover_date DATE NOT NULL COMMENT '交接日期',
    from_employee_id BIGINT NULL COMMENT '交班人 ID',
    from_employee_name VARCHAR(64) NULL COMMENT '交班人姓名',
    to_employee_id BIGINT NULL COMMENT '接班人 ID',
    to_employee_name VARCHAR(64) NULL COMMENT '接班人姓名',
    content VARCHAR(1000) NOT NULL COMMENT '交接内容',
    pending_issues VARCHAR(1000) NULL COMMENT '遗留问题',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/CONFIRMED',
    confirmed_by VARCHAR(64) NULL COMMENT '确认人',
    confirmed_at DATETIME NULL COMMENT '确认时间',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_by BIGINT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_wf_handover_no (tenant_id, handover_no),
    KEY idx_mes_wf_handover_team (tenant_id, team_id, handover_date),
    KEY idx_mes_wf_handover_status (tenant_id, status, handover_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 交接班记录';

-- ---------------------------------------------------------------------
-- 4. 工时记录
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_wf_work_hour (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    employee_id BIGINT NOT NULL COMMENT '员工 ID',
    employee_name VARCHAR(64) NOT NULL COMMENT '员工姓名',
    work_date DATE NOT NULL COMMENT '工作日期',
    task_id BIGINT NOT NULL COMMENT '任务 ID',
    task_no VARCHAR(64) NULL COMMENT '任务编号快照',
    work_order_id BIGINT NULL COMMENT '工单 ID',
    work_order_code VARCHAR(64) NULL COMMENT '工单编码快照',
    start_at DATETIME NOT NULL COMMENT '开始时间',
    end_at DATETIME NOT NULL COMMENT '结束时间',
    pause_seconds BIGINT NOT NULL DEFAULT 0 COMMENT '暂停秒数',
    net_seconds BIGINT NOT NULL DEFAULT 0 COMMENT '净工时秒数',
    source VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT '来源：REPORT/MANUAL',
    status VARCHAR(16) NOT NULL DEFAULT 'VALID' COMMENT '状态：VALID/VOID',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_by BIGINT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_wf_work_hour (tenant_id, employee_id, task_id, start_at),
    KEY idx_mes_wf_work_hour_employee_date (tenant_id, employee_id, work_date),
    KEY idx_mes_wf_work_hour_task (tenant_id, task_id, work_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 工时记录';

-- ---------------------------------------------------------------------
-- 5. 绩效统计（日 / 月）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_wf_performance_stat (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    stat_level VARCHAR(16) NOT NULL COMMENT '统计层级：DAILY/MONTHLY',
    employee_id BIGINT NOT NULL COMMENT '员工 ID',
    employee_name VARCHAR(64) NOT NULL COMMENT '员工姓名',
    team_id BIGINT NULL COMMENT '班组 ID',
    team_code VARCHAR(64) NULL COMMENT '班组编码',
    work_date DATE NOT NULL COMMENT '统计日期（月统计取期间首日）',
    period_code VARCHAR(16) NOT NULL COMMENT '期间编码：日 YYYYMMDD，月 YYYYMM',
    production_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '产量',
    qualified_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '合格数量',
    defect_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '不良数量',
    work_seconds BIGINT NOT NULL DEFAULT 0 COMMENT '净工时秒数',
    standard_seconds BIGINT NOT NULL DEFAULT 0 COMMENT '标准工时秒数（工艺路线快照口径）',
    efficiency DECIMAL(9, 6) NOT NULL DEFAULT 0 COMMENT '效率：标准工时/净工时',
    pass_rate DECIMAL(9, 6) NOT NULL DEFAULT 0 COMMENT '合格率：合格/产量',
    locked TINYINT NOT NULL DEFAULT 0 COMMENT '月度锁定：0 否，1 是',
    locked_at DATETIME NULL COMMENT '锁定时间',
    locked_by VARCHAR(64) NULL COMMENT '锁定人',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_by BIGINT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_wf_performance_stat (tenant_id, stat_level, employee_id, period_code, work_date),
    KEY idx_mes_wf_performance_team (tenant_id, team_id, period_code),
    KEY idx_mes_wf_performance_period (tenant_id, stat_level, period_code, locked)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 人员绩效统计';

-- ---------------------------------------------------------------------
-- 6. 技能矩阵
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mes_wf_skill_matrix (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户 ID',
    employee_id BIGINT NOT NULL COMMENT '员工 ID',
    employee_name VARCHAR(64) NOT NULL COMMENT '员工姓名',
    skill_code VARCHAR(64) NOT NULL COMMENT '技能编码',
    skill_name VARCHAR(128) NOT NULL COMMENT '技能名称',
    skill_level VARCHAR(16) NOT NULL DEFAULT 'JUNIOR' COMMENT '技能等级：JUNIOR/MIDDLE/SENIOR/EXPERT',
    certified_at DATE NULL COMMENT '认证日期',
    expiry_at DATE NULL COMMENT '失效日期',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/EXPIRED/DISABLED',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_by BIGINT NULL COMMENT '创建人',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by BIGINT NULL COMMENT '更新人',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_wf_skill_matrix (tenant_id, employee_id, skill_code),
    KEY idx_mes_wf_skill_employee (tenant_id, employee_id, status),
    KEY idx_mes_wf_skill_code (tenant_id, skill_code, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 员工技能矩阵';
