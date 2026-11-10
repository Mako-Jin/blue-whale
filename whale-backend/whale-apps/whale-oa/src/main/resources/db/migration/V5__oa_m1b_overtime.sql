-- =====================================================================
-- BlueWhale 微服务平台 - OA 应用（whale-oa） Flyway 版本化迁移 V5
-- =====================================================================
-- 适用库   : bluewhale_oa（MySQL 8.0+，utf8mb4）
-- 上级计划 : docs/plans/2026-11-04-wave5.82-oa-panorama.md
-- 里程碑   : Wave 5.82 M1B-1 加班申请与审批闭环
-- 说明     : 1) 新建 oa_overtime 加班申请表；
--            2) 种子 OVERTIME 审批配置（部门经理审批）；
--            3) 种子 COMP_TIME 调休假期类型，作为加班调休入账目标。
-- 幂等     : CREATE TABLE IF NOT EXISTS；种子用 INSERT ... WHERE NOT EXISTS。
-- 规则     : 禁止 DROP / TRUNCATE / DELETE；表间逻辑外键，不建物理 FOREIGN KEY。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 加班申请表（单号租户内唯一；同人同日时段不可重叠）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_overtime (
    id                   BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id            BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code                 VARCHAR(64)  NOT NULL            COMMENT '申请单号（租户内唯一）',
    applicant            VARCHAR(64)  NOT NULL            COMMENT '申请人账号',
    applicant_id         BIGINT       DEFAULT NULL        COMMENT '申请人用户ID（逻辑关联 sys_user.id）',
    overtime_date        DATE         NOT NULL            COMMENT '加班日期',
    start_time           TIME         NOT NULL            COMMENT '加班开始时间',
    end_time             TIME         NOT NULL            COMMENT '加班结束时间',
    hours                DECIMAL(4,1) NOT NULL            COMMENT '加班时长（小时，服务端按起止时间计算）',
    compensation         VARCHAR(16)  NOT NULL DEFAULT 'COMP_TIME'
                                                          COMMENT '补偿方式：COMP_TIME 调休 / PAY 加班费',
    project_name         VARCHAR(128) DEFAULT NULL        COMMENT '关联项目名称',
    reason               VARCHAR(512) DEFAULT NULL        COMMENT '加班事由',
    status               VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                                                          COMMENT '状态：PENDING 待审批 / APPROVED 已通过 / REJECTED 已驳回 / CANCELLED 已撤销',
    approval_instance_id BIGINT       DEFAULT NULL        COMMENT '审批实例ID（逻辑关联 oa_approval_instance.id）',
    remark               VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted              TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_overtime_code (tenant_id, code),
    KEY idx_oa_overtime_applicant_date (tenant_id, applicant, overtime_date),
    KEY idx_oa_overtime_status (tenant_id, status, overtime_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 加班申请表';

-- ---------------------------------------------------------------------
-- 2. 加班审批业务配置（business_type = OVERTIME → OA_OVERTIME）
-- ---------------------------------------------------------------------
INSERT INTO oa_approval_config
    (id, tenant_id, business_type, business_name, definition_code, title_template,
     business_url, cc_user_ids, enabled, remark, deleted)
SELECT 900000000000000501, 1, 'OVERTIME', '加班', 'OA_OVERTIME', '{applicant}的加班申请',
       '/oa/hr/overtime', NULL, 1, 'M1B-1 种子：加班审批配置', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_config
    WHERE tenant_id = 1 AND business_type = 'OVERTIME'
);

-- ---------------------------------------------------------------------
-- 3. 加班审批人解析规则：部门经理审批（按发起人部门负责人解析）
-- ---------------------------------------------------------------------
INSERT INTO oa_approval_route_rule
    (id, tenant_id, business_type, node_key, node_name, resolver_type, resolver_value,
     sort_no, enabled, remark, deleted)
SELECT 900000000000000502, 1, 'OVERTIME', 'dept_manager', '部门经理审批', 'DEPT_LEADER', NULL,
       10, 1, 'M1B-1 种子：加班部门经理审批人', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_route_rule
    WHERE tenant_id = 1 AND business_type = 'OVERTIME' AND node_key = 'dept_manager'
);

-- ---------------------------------------------------------------------
-- 4. 调休假期类型（默认无额度，由加班审批通过后按 8 小时 = 1 天调入）
-- ---------------------------------------------------------------------
INSERT INTO oa_leave_type
    (id, tenant_id, code, name, unit, annual_quota, min_unit, requires_attachment,
     allow_negative, enabled, sort_no, remark, deleted)
SELECT 900000000000000404, 1, 'COMP_TIME', '调休', 'DAY', 0.0, 0.5, 0, 0, 1, 4,
       'M1B-1 种子：调休（加班审批通过后调入，8 小时 = 1 天，按 0.5 天向下取整）', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_leave_type WHERE tenant_id = 1 AND code = 'COMP_TIME'
);

-- =====================================================================
-- 完成。校验建议：
--   SHOW COLUMNS FROM oa_overtime;
--   SELECT business_type, definition_code FROM oa_approval_config WHERE business_type = 'OVERTIME';
--   SELECT code, name, annual_quota FROM oa_leave_type WHERE code = 'COMP_TIME';
-- =====================================================================
