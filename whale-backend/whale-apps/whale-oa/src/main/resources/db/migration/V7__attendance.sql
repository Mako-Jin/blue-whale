-- =====================================================================
-- BlueWhale 微服务平台 - OA 应用（whale-oa） Flyway 版本化迁移 V7
-- =====================================================================
-- 适用库   : bluewhale_oa（MySQL 8.0+，utf8mb4）
-- 上级计划 : docs/plans/2026-11-04-wave5.82-oa-panorama.md
-- 里程碑   : Wave 5.82 M2-5a 考勤（规则/班次/地点/设备/打卡/日汇总/异常/补卡）
-- 说明     : 1) 新建考勤主数据、打卡记录、日汇总、异常与补卡共 10 张表；
--            2) 种子默认班次与默认规则；
--            3) 种子补卡审批配置（ATT_CORRECTION → OA_ATT_CORRECTION）与部门经理审批规则。
-- 幂等     : CREATE TABLE IF NOT EXISTS；种子用 INSERT ... WHERE NOT EXISTS。
-- 规则     : 禁止 DROP / TRUNCATE / DELETE；表间逻辑外键，不建物理 FOREIGN KEY。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 班次（上下班时间、弹性分钟、休息时长）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_att_shift (
    id            BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code          VARCHAR(32)  NOT NULL            COMMENT '班次编码（租户内唯一）',
    name          VARCHAR(64)  NOT NULL            COMMENT '班次名称',
    work_start    TIME         NOT NULL            COMMENT '上班时间',
    work_end      TIME         NOT NULL            COMMENT '下班时间',
    flex_minutes  INT          NOT NULL DEFAULT 0  COMMENT '弹性分钟（上班晚到/下班早退在该范围内视为正常）',
    break_minutes INT          NOT NULL DEFAULT 60 COMMENT '休息时长（分钟）',
    enabled       TINYINT      NOT NULL DEFAULT 1  COMMENT '启停：1 启用 / 0 停用',
    remark        VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_att_shift_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 考勤班次表';

-- ---------------------------------------------------------------------
-- 2. 考勤规则（外勤/补卡/定位要求）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_att_rule (
    id                        BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id                 BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code                      VARCHAR(32)  NOT NULL            COMMENT '规则编码（租户内唯一）',
    name                      VARCHAR(64)  NOT NULL            COMMENT '规则名称',
    allow_external            TINYINT      NOT NULL DEFAULT 1  COMMENT '是否允许外勤打卡：1 允许 / 0 不允许',
    require_location          TINYINT      NOT NULL DEFAULT 0  COMMENT '是否要求定位：1 要求 / 0 不要求',
    require_device            TINYINT      NOT NULL DEFAULT 0  COMMENT '是否要求设备打卡：1 要求 / 0 不要求',
    max_corrections_per_month INT          NOT NULL DEFAULT 3  COMMENT '每月补卡次数上限',
    correction_window_days    INT          NOT NULL DEFAULT 7  COMMENT '补卡可追溯天数',
    enabled                   TINYINT      NOT NULL DEFAULT 1  COMMENT '启停：1 启用 / 0 停用',
    remark                    VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted                   TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_att_rule_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 考勤规则表';

-- ---------------------------------------------------------------------
-- 3. 打卡地点（经纬度与半径）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_att_location (
    id            BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code          VARCHAR(32)   NOT NULL            COMMENT '地点编码（租户内唯一）',
    name          VARCHAR(64)   NOT NULL            COMMENT '地点名称',
    latitude      DECIMAL(10,6) NOT NULL            COMMENT '纬度',
    longitude     DECIMAL(10,6) NOT NULL            COMMENT '经度',
    radius_meters INT           NOT NULL DEFAULT 300 COMMENT '允许半径（米）',
    enabled       TINYINT       NOT NULL DEFAULT 1  COMMENT '启停：1 启用 / 0 停用',
    remark        VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_att_location_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 打卡地点表';

-- ---------------------------------------------------------------------
-- 4. 打卡设备（设备码/类型）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_att_device (
    id          BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code        VARCHAR(64)  NOT NULL            COMMENT '设备编码（租户内唯一）',
    name        VARCHAR(64)  NOT NULL            COMMENT '设备名称',
    device_type VARCHAR(16)  NOT NULL DEFAULT 'FINGERPRINT'
                                                 COMMENT '设备类型：FINGERPRINT 指纹 / FACE 人脸 / CARD 刷卡 / OTHER 其他',
    location_id BIGINT       DEFAULT NULL        COMMENT '打卡地点ID（逻辑关联 oa_att_location.id）',
    enabled     TINYINT      NOT NULL DEFAULT 1  COMMENT '启停：1 启用 / 0 停用',
    remark      VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_att_device_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 打卡设备表';

-- ---------------------------------------------------------------------
-- 5. 考勤组（绑定班次/规则/地点/设备）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_att_group (
    id          BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code        VARCHAR(32)  NOT NULL            COMMENT '考勤组编码（租户内唯一）',
    name        VARCHAR(64)  NOT NULL            COMMENT '考勤组名称',
    shift_id    BIGINT       NOT NULL            COMMENT '班次ID（逻辑关联 oa_att_shift.id）',
    rule_id     BIGINT       DEFAULT NULL        COMMENT '规则ID（逻辑关联 oa_att_rule.id）',
    location_id BIGINT       DEFAULT NULL        COMMENT '打卡地点ID（逻辑关联 oa_att_location.id）',
    device_id   BIGINT       DEFAULT NULL        COMMENT '打卡设备ID（逻辑关联 oa_att_device.id）',
    enabled     TINYINT      NOT NULL DEFAULT 1  COMMENT '启停：1 启用 / 0 停用',
    remark      VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_att_group_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 考勤组表';

-- ---------------------------------------------------------------------
-- 6. 考勤组成员（同组内用户唯一）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_att_group_member (
    id          BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    group_id    BIGINT       NOT NULL            COMMENT '考勤组ID（逻辑关联 oa_att_group.id）',
    user_id     BIGINT       NOT NULL            COMMENT '用户ID（逻辑关联 sys_user.id）',
    user_name   VARCHAR(64)  DEFAULT NULL        COMMENT '用户姓名快照',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_att_group_member (tenant_id, group_id, user_id),
    KEY idx_oa_att_group_member_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 考勤组成员表';

-- ---------------------------------------------------------------------
-- 7. 原始打卡记录（同人同时间同类型幂等）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_att_record (
    id          BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    user_id     BIGINT        NOT NULL            COMMENT '用户ID（逻辑关联 sys_user.id）',
    user_name   VARCHAR(64)   DEFAULT NULL        COMMENT '用户姓名快照',
    punch_time  DATETIME      NOT NULL            COMMENT '打卡时间',
    punch_type  VARCHAR(8)    NOT NULL            COMMENT '打卡类型：IN 上班 / OUT 下班',
    source      VARCHAR(16)   NOT NULL DEFAULT 'WEB'
                                                  COMMENT '来源：APP / DEVICE 设备 / WEB',
    latitude    DECIMAL(10,6) DEFAULT NULL        COMMENT '纬度',
    longitude   DECIMAL(10,6) DEFAULT NULL        COMMENT '经度',
    address     VARCHAR(255)  DEFAULT NULL        COMMENT '打卡地址',
    device_code VARCHAR(64)   DEFAULT NULL        COMMENT '设备编码（source=DEVICE 时记录）',
    correction  TINYINT       NOT NULL DEFAULT 0  COMMENT '是否补卡写入：1 是 / 0 否',
    remark      VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_att_record_punch (tenant_id, user_id, punch_time, punch_type),
    KEY idx_oa_att_record_user_time (tenant_id, user_id, punch_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 原始打卡记录表';

-- ---------------------------------------------------------------------
-- 8. 考勤日汇总（同人同日唯一）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_att_daily (
    id            BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    user_id       BIGINT       NOT NULL            COMMENT '用户ID（逻辑关联 sys_user.id）',
    user_name     VARCHAR(64)  DEFAULT NULL        COMMENT '用户姓名快照',
    work_date     DATE         NOT NULL            COMMENT '考勤日期',
    group_id      BIGINT       DEFAULT NULL        COMMENT '考勤组ID（逻辑关联 oa_att_group.id）',
    shift_code    VARCHAR(32)  DEFAULT NULL        COMMENT '班次编码快照',
    shift_start   TIME         DEFAULT NULL        COMMENT '上班时间快照',
    shift_end     TIME         DEFAULT NULL        COMMENT '下班时间快照',
    first_in      DATETIME     DEFAULT NULL        COMMENT '最早上班打卡',
    last_out      DATETIME     DEFAULT NULL        COMMENT '最晚下班打卡',
    late_minutes  INT          NOT NULL DEFAULT 0  COMMENT '迟到分钟',
    early_minutes INT          NOT NULL DEFAULT 0  COMMENT '早退分钟',
    absent        TINYINT      NOT NULL DEFAULT 0  COMMENT '是否缺卡：1 是 / 0 否',
    status        VARCHAR(16)  NOT NULL DEFAULT 'NORMAL'
                                                  COMMENT '状态：NORMAL 正常 / LATE 迟到 / EARLY 早退 / LATE_AND_EARLY 迟到早退 / ABSENT 缺卡 / LEAVE 请假 / OFFICIAL 出差',
    remark        VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_att_daily_user_date (tenant_id, user_id, work_date),
    KEY idx_oa_att_daily_date (tenant_id, work_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 考勤日汇总表';

-- ---------------------------------------------------------------------
-- 9. 考勤异常（日汇总派生，可确认/豁免）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_att_exception (
    id           BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    user_id      BIGINT       NOT NULL            COMMENT '用户ID（逻辑关联 sys_user.id）',
    user_name    VARCHAR(64)  DEFAULT NULL        COMMENT '用户姓名快照',
    work_date    DATE         NOT NULL            COMMENT '考勤日期',
    type         VARCHAR(16)  NOT NULL            COMMENT '异常类型：LATE 迟到 / EARLY 早退 / ABSENT 缺卡',
    description  VARCHAR(255) DEFAULT NULL        COMMENT '异常说明',
    status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                                                 COMMENT '状态：PENDING 待处理 / CONFIRMED 已确认 / WAIVED 已豁免',
    handler      VARCHAR(64)  DEFAULT NULL        COMMENT '处理人账号',
    handled_time DATETIME     DEFAULT NULL        COMMENT '处理时间',
    remark       VARCHAR(255) DEFAULT NULL        COMMENT '处理备注',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_att_exception (tenant_id, user_id, work_date, type),
    KEY idx_oa_att_exception_date (tenant_id, work_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 考勤异常表';

-- ---------------------------------------------------------------------
-- 10. 补卡申请（单号租户内唯一）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_att_correction (
    id                   BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id            BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code                 VARCHAR(64)  NOT NULL            COMMENT '补卡单号（租户内唯一）',
    user_id              BIGINT       NOT NULL            COMMENT '用户ID（逻辑关联 sys_user.id）',
    user_name            VARCHAR(64)  DEFAULT NULL        COMMENT '用户姓名快照',
    work_date            DATE         NOT NULL            COMMENT '补卡日期',
    punch_time           DATETIME     NOT NULL            COMMENT '补卡时间',
    punch_type           VARCHAR(8)   NOT NULL            COMMENT '打卡类型：IN 上班 / OUT 下班',
    reason               VARCHAR(512) DEFAULT NULL        COMMENT '补卡原因',
    status               VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                                                          COMMENT '状态：PENDING 待审批 / APPROVED 已通过 / REJECTED 已驳回 / CANCELLED 已撤销',
    approval_instance_id BIGINT       DEFAULT NULL        COMMENT '审批实例ID（逻辑关联 oa_approval_instance.id）',
    handler              VARCHAR(64)  DEFAULT NULL        COMMENT '处理人账号',
    handled_time         DATETIME     DEFAULT NULL        COMMENT '处理时间',
    remark               VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted              TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_att_correction_code (tenant_id, code),
    KEY idx_oa_att_correction_user (tenant_id, user_id, work_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 补卡申请表';

-- ---------------------------------------------------------------------
-- 11. 默认班次与默认规则
-- ---------------------------------------------------------------------
INSERT INTO oa_att_shift
    (id, tenant_id, code, name, work_start, work_end, flex_minutes, break_minutes, enabled, remark, deleted)
SELECT 900000000000000701, 1, 'STD', '标准班（09:00-18:00）', '09:00:00', '18:00:00', 0, 60, 1,
       'M2-5a 种子：默认标准班', 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM oa_att_shift WHERE tenant_id = 1 AND code = 'STD');

INSERT INTO oa_att_rule
    (id, tenant_id, code, name, allow_external, require_location, require_device,
     max_corrections_per_month, correction_window_days, enabled, remark, deleted)
SELECT 900000000000000702, 1, 'DEFAULT', '默认考勤规则', 1, 0, 0, 3, 7, 1,
       'M2-5a 种子：默认规则', 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM oa_att_rule WHERE tenant_id = 1 AND code = 'DEFAULT');

-- ---------------------------------------------------------------------
-- 12. 补卡审批配置与部门经理审批规则
-- ---------------------------------------------------------------------
INSERT INTO oa_approval_config
    (id, tenant_id, business_type, business_name, definition_code, title_template,
     business_url, cc_user_ids, enabled, remark, deleted)
SELECT 900000000000000703, 1, 'ATT_CORRECTION', '补卡', 'OA_ATT_CORRECTION', '{applicant}的补卡申请',
       '/oa/attendance/correction', NULL, 1, 'M2-5a 种子：补卡审批配置', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_config WHERE tenant_id = 1 AND business_type = 'ATT_CORRECTION'
);

INSERT INTO oa_approval_route_rule
    (id, tenant_id, business_type, node_key, node_name, resolver_type, resolver_value,
     sort_no, enabled, remark, deleted)
SELECT 900000000000000704, 1, 'ATT_CORRECTION', 'dept_manager', '部门经理审批', 'DEPT_LEADER', NULL,
       10, 1, 'M2-5a 种子：补卡部门经理审批人', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_route_rule
    WHERE tenant_id = 1 AND business_type = 'ATT_CORRECTION' AND node_key = 'dept_manager'
);

-- =====================================================================
-- 完成。校验建议：
--   SHOW TABLES LIKE 'oa_att_%';
--   SELECT code, name FROM oa_att_shift;
--   SELECT business_type, definition_code FROM oa_approval_config WHERE business_type = 'ATT_CORRECTION';
-- =====================================================================
