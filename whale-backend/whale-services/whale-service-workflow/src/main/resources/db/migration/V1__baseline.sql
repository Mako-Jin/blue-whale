-- =====================================================================
-- BlueWhale 微服务平台 - 流程引擎服务（whale-workflow） Flyway 版本化基线 V1
-- =====================================================================
-- 适用库   : bluewhale_workflow（MySQL 8.0+，utf8mb4）
-- 来源     : 由 db/init.sql 转换导出（2026-11-01 阶段 4 铺开）
-- 转换规则 : 1) 跳过 DROP TABLE；2) CREATE TABLE 统一 IF NOT EXISTS；
--            3) INSERT 统一 INSERT IGNORE（种子幂等）；4) 表尾统一 ENGINE/CHARSET/COLLATE。
-- 说明     : db/init.sql 为手工空库初始化入口，历史 migration-*.sql 仅供追溯，
--            二者均不在 spring.flyway.locations 扫描路径内（详见 db/README.md）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 流程定义表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS process_definition (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    code             VARCHAR(64)   NOT NULL               COMMENT '流程编码（租户内唯一，逻辑删除冲突由服务层前置校验）',
    name             VARCHAR(128)  NOT NULL               COMMENT '流程名称',
    definition_json  MEDIUMTEXT    NULL                   COMMENT '流程定义JSON（节点与流转，最长64KB）',
    version          INT           NOT NULL DEFAULT 1     COMMENT '版本号',
    status           TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 正常 / 0 禁用',
    remark           VARCHAR(255)  DEFAULT NULL           COMMENT '备注',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程定义表';

-- ---------------------------------------------------------------------
-- 流程定义版本快照表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS process_definition_version (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    definition_id    BIGINT        NOT NULL               COMMENT '流程定义ID',
    version          INT           NOT NULL               COMMENT '版本号（与定义表 version 对应）',
    definition_json  MEDIUMTEXT    NULL                   COMMENT '该版本的流程定义JSON快照',
    publish_status   TINYINT       NOT NULL DEFAULT 0     COMMENT '发布状态：0 草稿 / 1 已发布',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_definition_version (definition_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程定义版本快照表';

-- ---------------------------------------------------------------------
-- 流程实例表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS process_instance (
    id                  BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
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
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_definition_id (definition_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程实例表';

-- ---------------------------------------------------------------------
-- 待办任务表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS process_task (
    id           BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
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
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_instance_id (instance_id),
    KEY idx_assignee (assignee),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='待办任务表';

-- ---------------------------------------------------------------------
-- 后端接口日志表（whale-spring-boot-starter ApiLogFilter 采集，结构与 bluewhale_auth.api_log 一致）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS api_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-auth',
    request_uri    VARCHAR(255)  DEFAULT NULL           COMMENT '请求URI',
    http_method    VARCHAR(16)   DEFAULT NULL           COMMENT 'HTTP 方法：GET/POST/PUT/DELETE',
    query_string   VARCHAR(1024) DEFAULT NULL           COMMENT '查询串（敏感字段已脱敏）',
    request_params TEXT          NULL                   COMMENT '请求体（JSON，敏感字段已脱敏）',
    response_body  TEXT          NULL                   COMMENT '响应体（JSON，敏感字段已脱敏，超限截断）',
    response_code  INT           DEFAULT NULL           COMMENT '响应状态码（HTTP 状态）',
    biz_status     TINYINT       NOT NULL DEFAULT 1     COMMENT '业务结果：1 成功 / 0 失败',
    error_msg      VARCHAR(512)  DEFAULT NULL           COMMENT '失败原因（异常类名 + 消息）',
    ip             VARCHAR(64)   DEFAULT NULL           COMMENT '调用方IP（兼容 IPv6）',
    user_agent     VARCHAR(512)  DEFAULT NULL           COMMENT '用户代理（浏览器/客户端标识）',
    cost_ms        BIGINT        DEFAULT NULL           COMMENT '接口耗时（毫秒）',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_trace_id (trace_id),
    KEY idx_create_time (create_time),
    KEY idx_user_id (user_id),
    KEY idx_tenant_create (tenant_id, create_time),
    KEY idx_biz_status (biz_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='后端接口日志表';
