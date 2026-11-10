-- =====================================================================
-- BlueWhale 微服务平台 - OA 应用（whale-oa） Flyway 版本化迁移 V6
-- =====================================================================
-- 适用库   : bluewhale_oa（MySQL 8.0+，utf8mb4）
-- 上级计划 : docs/plans/2026-11-04-wave5.82-oa-panorama.md
-- 里程碑   : Wave 5.82 M1B-2 出差申请、行程、预支与出差报告
-- 说明     : 1) 新建 oa_trip 出差申请、oa_trip_itinerary 行程、oa_trip_advance 预支；
--            2) 种子 TRIP 审批配置与部门经理审批规则。
-- 幂等     : CREATE TABLE IF NOT EXISTS；种子用 INSERT ... WHERE NOT EXISTS。
-- 规则     : 禁止 DROP / TRUNCATE / DELETE；表间逻辑外键，不建物理 FOREIGN KEY。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 出差申请表（单号租户内唯一；自然日天数由服务端计算）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_trip (
    id                   BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id            BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    code                 VARCHAR(64)   NOT NULL            COMMENT '申请单号（租户内唯一）',
    applicant            VARCHAR(64)   NOT NULL            COMMENT '申请人账号',
    applicant_id         BIGINT        DEFAULT NULL        COMMENT '申请人用户ID（逻辑关联 sys_user.id）',
    destination          VARCHAR(128)  NOT NULL            COMMENT '出差目的地',
    start_date           DATE          NOT NULL            COMMENT '出差开始日期',
    end_date             DATE          NOT NULL            COMMENT '出差结束日期',
    days                 INT           NOT NULL            COMMENT '出差天数（自然日，含首尾，服务端计算）',
    transport_type       VARCHAR(16)   NOT NULL DEFAULT 'TRAIN'
                                                          COMMENT '交通方式：TRAIN 火车 / PLANE 飞机 / CAR 用车 / OTHER 其他',
    budget_amount        DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '预算金额（元）',
    advance_amount       DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '预支金额（元，不超过预算）',
    report_status        VARCHAR(16)   NOT NULL DEFAULT 'PENDING'
                                                          COMMENT '报告状态：PENDING 待提交 / SUBMITTED 已提交',
    report_content       VARCHAR(2048) DEFAULT NULL        COMMENT '出差报告内容',
    report_time          DATETIME      DEFAULT NULL        COMMENT '报告提交时间',
    status               VARCHAR(16)   NOT NULL DEFAULT 'PENDING'
                                                          COMMENT '状态：PENDING 待审批 / APPROVED 已通过 / REJECTED 已驳回 / CANCELLED 已撤销',
    approval_instance_id BIGINT        DEFAULT NULL        COMMENT '审批实例ID（逻辑关联 oa_approval_instance.id）',
    remark               VARCHAR(255)  DEFAULT NULL        COMMENT '备注',
    create_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted              TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_trip_code (tenant_id, code),
    KEY idx_oa_trip_applicant_date (tenant_id, applicant, start_date),
    KEY idx_oa_trip_status (tenant_id, status, start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 出差申请表';

-- ---------------------------------------------------------------------
-- 2. 出差行程表（同一出差单内 day_no 唯一）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_trip_itinerary (
    id          BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    trip_id     BIGINT       NOT NULL            COMMENT '出差申请ID（逻辑关联 oa_trip.id）',
    day_no      INT          NOT NULL            COMMENT '行程序号（从 1 开始）',
    trip_date   DATE         NOT NULL            COMMENT '行程日期',
    from_place  VARCHAR(128) NOT NULL            COMMENT '出发地',
    to_place    VARCHAR(128) NOT NULL            COMMENT '到达地',
    transport   VARCHAR(16)  NOT NULL DEFAULT 'TRAIN'
                                                 COMMENT '交通方式：TRAIN / PLANE / CAR / OTHER',
    remark      VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_trip_itinerary_day (tenant_id, trip_id, day_no),
    KEY idx_oa_trip_itinerary_trip (tenant_id, trip_id, trip_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 出差行程表';

-- ---------------------------------------------------------------------
-- 3. 出差预支记录（一笔出差单最多一条，审批通过后幂等生成）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_trip_advance (
    id           BIGINT        NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT        NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    trip_id      BIGINT        NOT NULL            COMMENT '出差申请ID（逻辑关联 oa_trip.id）',
    applicant_id BIGINT        DEFAULT NULL        COMMENT '申请人用户ID',
    amount       DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '预支金额（元）',
    status       VARCHAR(16)   NOT NULL DEFAULT 'PENDING'
                                                 COMMENT '预支状态：PENDING 待发放 / PAID 已发放',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT       NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_trip_advance_trip (tenant_id, trip_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='OA 出差预支记录表';

-- ---------------------------------------------------------------------
-- 4. 出差审批业务配置（business_type = TRIP → OA_TRIP）
-- ---------------------------------------------------------------------
INSERT INTO oa_approval_config
    (id, tenant_id, business_type, business_name, definition_code, title_template,
     business_url, cc_user_ids, enabled, remark, deleted)
SELECT 900000000000000601, 1, 'TRIP', '出差', 'OA_TRIP', '{applicant}的出差申请',
       '/oa/hr/trip', NULL, 1, 'M1B-2 种子：出差审批配置', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_config
    WHERE tenant_id = 1 AND business_type = 'TRIP'
);

-- ---------------------------------------------------------------------
-- 5. 出差审批人解析规则：部门经理审批（按发起人部门负责人解析）
-- ---------------------------------------------------------------------
INSERT INTO oa_approval_route_rule
    (id, tenant_id, business_type, node_key, node_name, resolver_type, resolver_value,
     sort_no, enabled, remark, deleted)
SELECT 900000000000000602, 1, 'TRIP', 'dept_manager', '部门经理审批', 'DEPT_LEADER', NULL,
       10, 1, 'M1B-2 种子：出差部门经理审批人', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM oa_approval_route_rule
    WHERE tenant_id = 1 AND business_type = 'TRIP' AND node_key = 'dept_manager'
);

-- =====================================================================
-- 完成。校验建议：
--   SHOW COLUMNS FROM oa_trip;
--   SELECT business_type, definition_code FROM oa_approval_config WHERE business_type = 'TRIP';
--   SELECT trip_id, amount, status FROM oa_trip_advance ORDER BY create_time DESC LIMIT 10;
-- =====================================================================
