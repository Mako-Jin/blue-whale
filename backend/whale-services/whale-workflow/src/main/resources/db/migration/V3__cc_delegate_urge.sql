-- =====================================================================
-- BlueWhale - whale-workflow Flyway 增量 V3：抄送 / 代理 / 催办 / 审批人覆盖
-- =====================================================================
-- 适用库   : bluewhale_workflow
-- 上级计划 : Wave 5.82 M0-4（Workflow 缺口补齐）
-- 说明     : 1) 实例表补 assignee_overrides_json（发起时审批人覆盖快照）；
--            2) 新增 process_cc（抄送，不产生待办）；
--            3) 新增 process_delegate（代理规则，任务创建时单次改派）；
--            4) 新增 process_urge（催办留痕 + 频控）。
-- 幂等     : CREATE TABLE IF NOT EXISTS；MySQL 不支持 ADD COLUMN IF NOT EXISTS，
--            使用 information_schema 守卫，保证空库/存量库重复执行均安全。
-- =====================================================================

SET @has_assignee_overrides := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_instance'
      AND COLUMN_NAME = 'assignee_overrides_json'
);
SET @ddl := IF(@has_assignee_overrides = 0,
    'ALTER TABLE process_instance ADD COLUMN assignee_overrides_json TEXT DEFAULT NULL COMMENT ''发起时审批人覆盖快照（nodeKey -> 账号列表 JSON）''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 抄送表：CC 节点与发起级抄送统一落库，不生成待办
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS process_cc (
    id           BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    instance_id  BIGINT        NOT NULL               COMMENT '所属流程实例ID',
    node_key     VARCHAR(64)   NOT NULL               COMMENT '抄送节点键；发起级抄送固定为 START',
    node_name    VARCHAR(128)  DEFAULT NULL           COMMENT '抄送节点名称',
    recipient    VARCHAR(64)   NOT NULL               COMMENT '收件人（账号或用户ID文本）',
    read_flag    TINYINT       NOT NULL DEFAULT 0     COMMENT '已读标记：0 未读 / 1 已读',
    read_time    DATETIME      DEFAULT NULL           COMMENT '已读时间',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cc (tenant_id, instance_id, node_key, recipient),
    KEY idx_cc_recipient (tenant_id, recipient, read_flag),
    KEY idx_cc_instance (tenant_id, instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程抄送表';

-- ---------------------------------------------------------------------
-- 代理规则表：按流程 + 时间段，任务创建时单次改派（禁止链式）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS process_delegate (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    definition_code  VARCHAR(64)   NOT NULL               COMMENT '流程编码（精确匹配）',
    from_account     VARCHAR(64)   NOT NULL               COMMENT '委托人账号',
    to_account       VARCHAR(64)   NOT NULL               COMMENT '代理人账号',
    start_time       DATETIME      NOT NULL               COMMENT '生效开始时间',
    end_time         DATETIME      NOT NULL               COMMENT '生效结束时间',
    enabled          TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 启用 / 0 停用',
    remark           VARCHAR(255)  DEFAULT NULL           COMMENT '备注',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_delegate_from (tenant_id, from_account, enabled),
    KEY idx_delegate_def (tenant_id, definition_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程代理规则表';

-- ---------------------------------------------------------------------
-- 催办表：留痕 + 频控（消息下发在 M1A-4）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS process_urge (
    id              BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    instance_id     BIGINT        NOT NULL               COMMENT '流程实例ID',
    task_id         BIGINT        DEFAULT NULL           COMMENT '指定催办任务ID；为空表示实例级催办',
    urger           VARCHAR(64)   NOT NULL               COMMENT '催办人账号',
    target_account  VARCHAR(64)   DEFAULT NULL           COMMENT '被催办人账号；为空表示全部待办',
    comment         VARCHAR(255)  DEFAULT NULL           COMMENT '催办说明',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_urge_freq (tenant_id, instance_id, urger, create_time),
    KEY idx_urge_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程催办记录表';
