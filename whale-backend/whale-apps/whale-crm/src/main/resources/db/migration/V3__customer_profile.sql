-- =====================================================================
-- BlueWhale 客户关系应用（whale-crm）Flyway 增量 V3：客户画像与公海
-- =====================================================================
-- 适用库   : bluewhale_crm（MySQL 8.0+，utf8mb4）
-- 设计稿   : docs/design/crm-module-panorama.md
-- 计划     : docs/plans/2026-11-04-wave5.83-crm-panorama.md（M1）
-- 规则     : 1) 仅 CREATE TABLE IF NOT EXISTS；
--            2) 禁止 DROP / TRUNCATE / DELETE（基线测试强校验）；
--            3) 表间逻辑外键，不建物理 FOREIGN KEY。
-- 说明     : 客户地址、客户画像、公海规则与负责人变更留痕；手机/邮箱/税号
--            保存原值但输出与审计必须脱敏（REQ-SEC-034）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 客户地址（每客户至多一个默认收货地址、一个默认账单地址，服务层校验）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_customer_address (
    id                    BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    customer_id           BIGINT        NOT NULL                COMMENT '所属客户ID',
    contact_name          VARCHAR(64)   DEFAULT NULL            COMMENT '收件人',
    phone                 VARCHAR(32)   DEFAULT NULL            COMMENT '联系电话（输出脱敏）',
    province              VARCHAR(64)   DEFAULT NULL            COMMENT '省',
    city                  VARCHAR(64)   DEFAULT NULL            COMMENT '市',
    district              VARCHAR(64)   DEFAULT NULL            COMMENT '区县',
    detail                VARCHAR(255)  NOT NULL                COMMENT '详细地址',
    is_default_shipping   TINYINT       NOT NULL DEFAULT 0      COMMENT '是否默认收货地址：1 是 / 0 否',
    is_default_billing    TINYINT       NOT NULL DEFAULT 0      COMMENT '是否默认账单地址：1 是 / 0 否',
    status                VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / INACTIVE',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_crm_address_customer (tenant_id, customer_id, status),
    KEY idx_crm_address_default (tenant_id, customer_id, is_default_shipping, is_default_billing)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户地址';

-- ---------------------------------------------------------------------
-- 2. 客户画像（每客户一条，版本递增留痕；敏感备注输出脱敏）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_customer_profile (
    id                BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    customer_id       BIGINT        NOT NULL                COMMENT '所属客户ID',
    preference        VARCHAR(500)  DEFAULT NULL            COMMENT '客户偏好',
    pain_points       VARCHAR(500)  DEFAULT NULL            COMMENT '痛点',
    decision_chain    TEXT          NULL                    COMMENT '决策链（JSON 数组：联系人/角色/影响力）',
    key_person        VARCHAR(255)  DEFAULT NULL            COMMENT '关键人说明',
    sensitive_remark  VARCHAR(1000) DEFAULT NULL            COMMENT '敏感备注（输出脱敏）',
    version           INT           NOT NULL DEFAULT 0      COMMENT '版本号（每次保存递增）',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_profile_customer (tenant_id, customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户画像';

-- ---------------------------------------------------------------------
-- 3. 客户公海规则（每租户一个启用规则，服务层按规则回收）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_customer_pool_rule (
    id                   BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id            BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    rule_name            VARCHAR(64)   NOT NULL DEFAULT '默认公海规则' COMMENT '规则名称',
    inactive_days        INT           NOT NULL DEFAULT 30     COMMENT '未跟进天数阈值（0 表示不限制）',
    no_opportunity_days  INT           NOT NULL DEFAULT 0      COMMENT '无商机天数阈值（0 表示不限制）',
    levels               VARCHAR(32)   NOT NULL DEFAULT 'C,D'  COMMENT '适用客户等级（逗号分隔）',
    dept_id              BIGINT        DEFAULT NULL            COMMENT '适用部门（NULL 表示全部）',
    daily_claim_limit    INT           NOT NULL DEFAULT 0      COMMENT '每人每日领取上限（0 表示不限制）',
    enabled              TINYINT       NOT NULL DEFAULT 1      COMMENT '是否启用：1 是 / 0 否',
    last_run_at          DATETIME      DEFAULT NULL            COMMENT '最近回收执行时间',
    recycle_count        INT           NOT NULL DEFAULT 0      COMMENT '最近一次回收数量',
    create_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted              TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crm_pool_rule_name (tenant_id, rule_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户公海规则';

-- ---------------------------------------------------------------------
-- 4. 客户负责人变更留痕（领取/释放/分配/回收/新建，append-only）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS crm_customer_owner_log (
    id            BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    customer_id   BIGINT        NOT NULL                COMMENT '客户ID',
    action        VARCHAR(16)   NOT NULL                COMMENT '动作：CREATE/CLAIM/RELEASE/ASSIGN/RECYCLE',
    from_owner_id BIGINT        DEFAULT NULL            COMMENT '原负责人',
    to_owner_id   BIGINT        DEFAULT NULL            COMMENT '新负责人',
    operator_id   BIGINT        DEFAULT NULL            COMMENT '操作人',
    remark        VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_crm_owner_log_customer (tenant_id, customer_id, create_time),
    KEY idx_crm_owner_log_operator (tenant_id, operator_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户负责人变更留痕';
