-- =====================================================================
-- BlueWhale 微服务平台 - OA 应用（whale-oa） Flyway 版本化迁移 V14
-- =====================================================================
-- 适用库   : bluewhale_oa（MySQL 8.0+，utf8mb4）
-- 里程碑   : Wave 5.82 M5-7d-2 人员异动（入职/转正/调岗/离职/返聘）
-- 说明     : 新建异动单与异动任务（离职清单）2 张表；种子 HR_CHANGE
--            审批配置与部门负责人/HR 两级审批人规则。
-- 幂等     : CREATE TABLE IF NOT EXISTS；种子 INSERT ... WHERE NOT EXISTS。
-- 规则     : 禁止 DROP / TRUNCATE / DELETE；表间逻辑外键，不建物理 FOREIGN KEY。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 人员异动单（草稿 → 审批中 → 审批通过/驳回 → 执行成功/失败）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_employee_change (
    id                   BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id            BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    change_no            VARCHAR(64)  NOT NULL            COMMENT '异动单号',
    change_type          VARCHAR(16)  NOT NULL            COMMENT '类型：ONBOARD 入职 / REGULAR 转正 / TRANSFER 调岗 / LEAVE 离职 / REHIRE 返聘',
    user_id              BIGINT       DEFAULT NULL        COMMENT '异动目标用户ID（入职未建号时为空）',
    employee_name        VARCHAR(64)  NOT NULL            COMMENT '员工姓名快照',
    username             VARCHAR(64)  DEFAULT NULL        COMMENT '登录账号（入职建号时填写）',
    from_dept_id         BIGINT       DEFAULT NULL        COMMENT '原部门ID',
    to_dept_id           BIGINT       DEFAULT NULL        COMMENT '目标部门ID',
    from_status          VARCHAR(16)  DEFAULT NULL        COMMENT '原在职状态（ACTIVE/PROBATION/REGULAR/LEFT）',
    to_status            VARCHAR(16)  DEFAULT NULL        COMMENT '目标在职状态',
    effective_date       DATE         DEFAULT NULL        COMMENT '生效日期',
    reason               VARCHAR(512) DEFAULT NULL        COMMENT '异动原因',
    status               VARCHAR(16)  NOT NULL DEFAULT 'DRAFT'
                                                          COMMENT '状态：DRAFT/SUBMITTED/APPROVED/REJECTED/EXECUTED/EXEC_FAILED',
    approval_instance_id BIGINT       DEFAULT NULL        COMMENT '审批实例ID（逻辑关联 oa_approval_instance）',
    applicant_id         BIGINT       NOT NULL            COMMENT '申请人用户ID',
    applicant_name       VARCHAR(64)  DEFAULT NULL        COMMENT '申请人姓名快照',
    executor_id          BIGINT       DEFAULT NULL        COMMENT '执行人用户ID',
    executor_name        VARCHAR(64)  DEFAULT NULL        COMMENT '执行人姓名快照',
    executed_time        DATETIME     DEFAULT NULL        COMMENT '执行时间',
    execute_result       VARCHAR(255) DEFAULT NULL        COMMENT '执行结果摘要',
    remark               VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted              TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 否 / 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_employee_change_no (tenant_id, change_no),
    KEY idx_oa_employee_change_user (tenant_id, user_id),
    KEY idx_oa_employee_change_status (tenant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'OA 人员异动单';

-- ---------------------------------------------------------------------
-- 2. 人员异动任务（离职清单：交接/账号/资产/档案）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_employee_change_task (
    id            BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    change_id     BIGINT       NOT NULL            COMMENT '异动单ID（逻辑关联 oa_employee_change.id）',
    task_code     VARCHAR(32)  NOT NULL            COMMENT '任务编码：HANDOVER/ACCOUNT/ASSET/FILE',
    task_name     VARCHAR(64)  NOT NULL            COMMENT '任务名称',
    assignee_id   BIGINT       DEFAULT NULL        COMMENT '负责人用户ID',
    assignee_name VARCHAR(64)  DEFAULT NULL        COMMENT '负责人姓名快照',
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待办 / DONE 已完成',
    done_time     DATETIME     DEFAULT NULL        COMMENT '完成时间',
    remark        VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 否 / 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_employee_change_task (tenant_id, change_id, task_code),
    KEY idx_oa_employee_change_task_change (tenant_id, change_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'OA 人员异动任务（离职清单）';

-- ---------------------------------------------------------------------
-- 3. 异动审批配置：HR_CHANGE → OA_HR_CHANGE
-- ---------------------------------------------------------------------
INSERT INTO oa_approval_config
    (id, tenant_id, business_type, business_name, definition_code, title_template,
     business_url, cc_user_ids, enabled, remark, deleted)
SELECT 900000000000000401, 1, 'HR_CHANGE', '人事异动', 'OA_HR_CHANGE', '{applicant}的人事异动申请',
       '/oa/hr/change', NULL, 1, 'M5-7d-2 种子：人事异动审批配置', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_config
    WHERE tenant_id = 1 AND business_type = 'HR_CHANGE'
);

-- ---------------------------------------------------------------------
-- 4. 异动审批人规则：部门负责人 → HR 管理员
-- ---------------------------------------------------------------------
INSERT INTO oa_approval_route_rule
    (id, tenant_id, business_type, node_key, node_name, resolver_type, resolver_value,
     sort_no, enabled, remark, deleted)
SELECT 900000000000000402, 1, 'HR_CHANGE', 'dept_manager', '部门负责人审批', 'DEPT_LEADER', NULL,
       10, 1, 'M5-7d-2 种子：异动部门负责人审批人', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_route_rule
    WHERE tenant_id = 1 AND business_type = 'HR_CHANGE' AND node_key = 'dept_manager'
);

INSERT INTO oa_approval_route_rule
    (id, tenant_id, business_type, node_key, node_name, resolver_type, resolver_value,
     sort_no, enabled, remark, deleted)
SELECT 900000000000000403, 1, 'HR_CHANGE', 'hr', 'HR审批', 'ROLE', 'oa_hr_admin',
       20, 1, 'M5-7d-2 种子：异动 HR 审批人（oa_hr_admin 角色）', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_route_rule
    WHERE tenant_id = 1 AND business_type = 'HR_CHANGE' AND node_key = 'hr'
);
