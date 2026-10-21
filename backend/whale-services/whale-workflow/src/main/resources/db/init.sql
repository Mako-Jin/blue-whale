-- =====================================================================
-- BlueWhale 微服务平台 - 工作流服务（whale-workflow）数据库初始化脚本
-- =====================================================================
-- 建议库名   : bluewhale_workflow
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成）、create_time、update_time、
--              deleted TINYINT（逻辑删除：0 未删除 / 1 已删除）
-- 说明       : code 因逻辑删除无法建唯一索引，其业务唯一性由服务层前置校验保证
--              （存在则抛 BizException(PARAM_ERROR, "流程编码已存在")）；
--              definition_json 为流程定义 JSON（节点与流转），最长 64KB（65536 字符）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 流程定义表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS process_definition;
CREATE TABLE process_definition (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    code             VARCHAR(64)   NOT NULL               COMMENT '流程编码（业务唯一，逻辑删除冲突由服务层前置校验）',
    name             VARCHAR(128)  NOT NULL               COMMENT '流程名称',
    definition_json  MEDIUMTEXT    NULL                   COMMENT '流程定义JSON（节点与流转，最长64KB）',
    version          INT           NOT NULL DEFAULT 1     COMMENT '版本号',
    status           TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 正常 / 0 禁用',
    remark           VARCHAR(255)  DEFAULT NULL           COMMENT '备注',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程定义表';

-- ---------------------------------------------------------------------
-- 流程定义版本快照表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS process_definition_version;
CREATE TABLE process_definition_version (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    definition_id    BIGINT        NOT NULL               COMMENT '流程定义ID',
    version          INT           NOT NULL               COMMENT '版本号（与定义表 version 对应）',
    definition_json  MEDIUMTEXT    NULL                   COMMENT '该版本的流程定义JSON快照',
    publish_status   TINYINT       NOT NULL DEFAULT 0     COMMENT '发布状态：0 草稿 / 1 已发布',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_definition_version (definition_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程定义版本快照表';

-- ---------------------------------------------------------------------
-- 流程实例表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS process_instance;
CREATE TABLE process_instance (
    id                  BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    definition_id       BIGINT        NOT NULL               COMMENT '流程定义ID',
    definition_code     VARCHAR(64)   NOT NULL               COMMENT '发起时的流程定义编码（冗余）',
    definition_version  INT           NOT NULL               COMMENT '发起时的流程定义版本（冗余）',
    title               VARCHAR(128)  NOT NULL               COMMENT '实例标题',
    business_key        VARCHAR(64)   DEFAULT NULL           COMMENT '业务键（可选）',
    status              TINYINT       NOT NULL DEFAULT 0     COMMENT '状态：0 运行中 / 1 已通过 / 2 已驳回 / 3 已撤销',
    current_index       INT           NOT NULL DEFAULT 0     COMMENT '当前审批节点下标（0 起）',
    current_node_key    VARCHAR(64)   DEFAULT NULL           COMMENT '当前审批节点键（规范化定义；为空时回落 current_index）',
    active_node_keys    VARCHAR(512)  DEFAULT NULL           COMMENT '并行分支活动节点键（逗号分隔；为空表示单待办）',
    variables_json      TEXT          DEFAULT NULL           COMMENT '流程变量快照（JSON，供条件选路重放）',
    initiator           VARCHAR(64)   DEFAULT NULL           COMMENT '发起人账号',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_definition_id (definition_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程实例表';

-- ---------------------------------------------------------------------
-- 待办任务表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS process_task;
CREATE TABLE process_task (
    id           BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    instance_id  BIGINT        NOT NULL               COMMENT '所属流程实例ID',
    node_index   INT           NOT NULL               COMMENT '审批节点下标（0 起）',
    node_name    VARCHAR(128)  DEFAULT NULL           COMMENT '节点名称',
    node_key     VARCHAR(64)   DEFAULT NULL           COMMENT '建任务时的节点键（规范化模型用 key；legacy 用合成 key n{i+1}）',
    form_id      VARCHAR(64)   DEFAULT NULL           COMMENT '节点绑定的表单定义 ID（字符串承载，可空）',
    form_code    VARCHAR(64)   DEFAULT NULL           COMMENT '节点绑定的表单编码（可空）',
    assignee     VARCHAR(64)   NOT NULL               COMMENT '审批人账号',
    status       TINYINT       NOT NULL DEFAULT 0     COMMENT '状态：0 待办 / 1 已同意 / 2 已驳回 / 3 已取消',
    comment      VARCHAR(255)  DEFAULT NULL           COMMENT '审批意见',
    parent_node_key VARCHAR(64) DEFAULT NULL          COMMENT '并行分支父节点键（fork 网关键；非并行任务为空）',
    sign_type    VARCHAR(32)   DEFAULT NULL           COMMENT '任务类型：COUNTER_SIGN 会签 / 空 普通审批',
    sign_policy  VARCHAR(16)   DEFAULT NULL           COMMENT '会签策略快照：ALL / ANY / RATIO:n',
    owner_id     VARCHAR(64)   DEFAULT NULL           COMMENT '任务归属人（转办后仍记录原审批人）',
    delegate_from VARCHAR(64)  DEFAULT NULL           COMMENT '委派来源人（委派任务记录原审批人）',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_instance_id (instance_id),
    KEY idx_assignee (assignee),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='待办任务表';
