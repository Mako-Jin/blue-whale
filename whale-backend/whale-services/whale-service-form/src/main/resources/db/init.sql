-- =====================================================================
-- BlueWhale 微服务平台 - 表单服务（whale-form）数据库初始化脚本
-- =====================================================================
-- 建议库名   : bluewhale_form
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成）、create_time、update_time、
--              deleted TINYINT（逻辑删除：0 未删除 / 1 已删除）
-- 说明       : code / table_code 因逻辑删除无法建唯一索引，其业务唯一性由服务层前置校验保证；
--              三层模型：表定义层（form_table_definition + form_column_meta）
--                        表单视图层（form_definition + form_field_config）
--                        选项层（form_field_option）
--              schema_json 为表单结构定义 JSON（发布快照），最长 64KB（65536 字符）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 表单定义表（表单视图层）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS form_definition;
CREATE TABLE form_definition (
    id                  BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    code                VARCHAR(64)   NOT NULL               COMMENT '表单编码（租户内唯一，逻辑删除冲突由服务层前置校验）',
    name                VARCHAR(128)  NOT NULL               COMMENT '表单名称',
    table_definition_id BIGINT        DEFAULT NULL           COMMENT '所属表定义ID（表:表单 1:N，迁移后回填；NULL 为未迁移的旧定义）',
    schema_json         MEDIUMTEXT    NULL                   COMMENT '表单结构定义JSON（由字段配置编译的发布快照，最长64KB）',
    version             INT           NOT NULL DEFAULT 1     COMMENT '版本号',
    status              TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 正常 / 0 禁用',
    remark              VARCHAR(255)  DEFAULT NULL           COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_code (tenant_id, code),
    KEY idx_table_definition_id (table_definition_id)
) COMMENT='表单定义表（表单视图层）';

-- ---------------------------------------------------------------------
-- 表单数据表
-- ---------------------------------------------------------------------
-- 本表是 form 库里唯一会长到千万级的表，列与索引按已应用的最新迁移写全
-- （client_request_id 来自 V5、biz_status / process_instance_id 来自 V10、
-- 两个复合索引来自 V16），保证本脚本在空库上独立可执行。
DROP TABLE IF EXISTS form_data;
CREATE TABLE form_data (
    id                  BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离；取自 X-Tenant-Id，缺省 1）',
    definition_id       BIGINT        NOT NULL               COMMENT '表单定义ID',
    definition_code     VARCHAR(64)   NOT NULL               COMMENT '提交时的表单定义编码（冗余）',
    definition_version  INT           NOT NULL               COMMENT '提交时的表单定义版本（冗余）',
    table_definition_id BIGINT        DEFAULT NULL           COMMENT '所属表定义ID（元数据模型；NULL 为旧模型）',
    table_code          VARCHAR(64)   DEFAULT NULL           COMMENT '所属表编码（路由物理表；缺省回落 definition_code）',
    table_version       INT           DEFAULT NULL           COMMENT '所属表版本（路由物理表；缺省回落 definition_version）',
    form_definition_id  BIGINT        DEFAULT NULL           COMMENT '提交来源的表单视图ID（元数据模型；NULL 为旧模型）',
    biz_key             VARCHAR(64)   DEFAULT NULL           COMMENT '业务键（可选）',
    client_request_id   VARCHAR(64)   DEFAULT NULL           COMMENT '客户端幂等键（可选，租户 + 表单编码内唯一）',
    data_json           MEDIUMTEXT    NULL                   COMMENT '表单数据JSON（最长64KB；过渡期双写，结构化真值见物理表）',
    submitter           VARCHAR(64)   DEFAULT NULL           COMMENT '提交人账号',
    status              TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 有效 / 0 作废',
    biz_status          TINYINT       NOT NULL DEFAULT 3     COMMENT '业务状态：1 草稿 / 2 审批中 / 3 已通过 / 4 已驳回（存量默认 3）',
    process_instance_id VARCHAR(64)   DEFAULT NULL           COMMENT '外部工作流实例ID（审批中时非空）',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_definition_client_req (tenant_id, definition_code, client_request_id),
    KEY idx_definition_id (definition_id),
    KEY idx_biz_key (biz_key),
    KEY idx_table_code_version (table_code, table_version),
    KEY idx_form_definition_id (form_definition_id),
    KEY idx_process_instance (process_instance_id),
    /* 考功殿（T5.7）：① 本租户 + 时间窗（今日提交 / 近 7 日趋势 / Top 表单）
       ② 本租户 + 业务状态 + 多久没动（待审批累计 / 超时待审）。
       这两个复合索引的最左前缀同时覆盖了原来的 idx_tenant_id 与
       idx_tenant_biz_status，V16 已把那两个单列索引删掉。 */
    KEY idx_tenant_create (tenant_id, create_time),
    KEY idx_tenant_biz_update (tenant_id, biz_status, update_time)
) COMMENT='表单数据表（主记录；结构化字段落各版本物理表 form_data_{code}_v{version}）';

-- ---------------------------------------------------------------------
-- 表单物理表登记表（表定义 ↔ 物理表映射 + 发布时 schema 快照）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS form_table_registry;
CREATE TABLE form_table_registry (
    id                  BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离；物理表按租户独立）',
    table_definition_id BIGINT        DEFAULT NULL           COMMENT '表定义ID（元数据模型；NULL 为旧模型）',
    table_code          VARCHAR(64)   DEFAULT NULL           COMMENT '表编码（元数据模型；旧模型回落 definition_code）',
    table_version       INT           DEFAULT NULL           COMMENT '表版本（元数据模型；旧模型回落 definition_version）',
    definition_id       BIGINT        DEFAULT NULL           COMMENT '表单定义ID（旧模型；元数据模型为 NULL）',
    definition_code     VARCHAR(64)   DEFAULT NULL           COMMENT '表单定义编码（旧模型）',
    definition_version  INT           DEFAULT NULL           COMMENT '表单定义版本（旧模型）',
    physical_table      VARCHAR(64)   NOT NULL               COMMENT '物理表名（默认租户 form_data_{code}_v{version}；其他租户 form_data_t{tenantId}_{code}_v{version}）',
    field_count         INT           NOT NULL DEFAULT 0     COMMENT '动态字段数量',
    schema_json         MEDIUMTEXT    NULL                   COMMENT '发布时的表单结构定义快照（JSON）',
    status              TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 生效 / 0 废弃',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_tenant_code_version (tenant_id, definition_code, definition_version),
    KEY idx_tenant_table_code_version (tenant_id, table_code, table_version),
    KEY idx_definition_id (definition_id),
    KEY idx_table_definition_id (table_definition_id)
) COMMENT='表单物理表登记表';

-- ---------------------------------------------------------------------
-- 表定义表（表定义层）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS form_table_definition;
CREATE TABLE form_table_definition (
    id           BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    table_code   VARCHAR(64)   NOT NULL               COMMENT '表编码（租户内唯一；物理表名来源，^[a-z][a-z0-9_]{0,39}$）',
    table_name   VARCHAR(128)  NOT NULL               COMMENT '表名称',
    version      INT           NOT NULL DEFAULT 1     COMMENT '版本号',
    remark       VARCHAR(255)  DEFAULT NULL           COMMENT '备注',
    status       TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 正常 / 0 禁用',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_table_code (tenant_id, table_code)
) COMMENT='表定义表（表定义层）';

-- ---------------------------------------------------------------------
-- 列元数据表（表定义层 · 动态列详情）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS form_column_meta;
CREATE TABLE form_column_meta (
    id                  BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    table_definition_id BIGINT        NOT NULL               COMMENT '表定义ID',
    table_version       INT           NOT NULL DEFAULT 1     COMMENT '该列所属表版本（发布 version=N 取 table_version=N）',
    column_code         VARCHAR(64)   NOT NULL               COMMENT '列编码（物理列名；^[a-z][a-z0-9_]{0,63}$，不得命中保留列）',
    column_name         VARCHAR(128)  NOT NULL               COMMENT '列名称（业务名）',
    data_type           VARCHAR(32)   NOT NULL               COMMENT '存储类型：text/textarea/number/date/datetime/select/bool',
    length              INT           DEFAULT NULL           COMMENT '长度（text 生效；缺省 255，范围 1..2000）',
    `precision`         INT           DEFAULT NULL           COMMENT '精度（number 生效；缺省 18）',
    scale               INT           DEFAULT NULL           COMMENT '小数位（number 生效；缺省 4）',
    nullable            TINYINT       NOT NULL DEFAULT 1     COMMENT '能否为空：1 可空 / 0 非空',
    required            TINYINT       NOT NULL DEFAULT 0     COMMENT '是否必填：1 必填 / 0 非必填（服务层校验）',
    default_value       VARCHAR(255)  DEFAULT NULL           COMMENT '默认值',
    remark              VARCHAR(255)  DEFAULT NULL           COMMENT '备注',
    sort_no             INT           NOT NULL DEFAULT 0     COMMENT '排序号',
    status              TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 正常 / 0 禁用',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_table_definition_id (table_definition_id)
) COMMENT='列元数据表（表定义层 · 动态列详情）';

-- ---------------------------------------------------------------------
-- 表单字段配置表（表单视图层 · 字段属性详情）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS form_field_config;
CREATE TABLE form_field_config (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    form_id        BIGINT        NOT NULL               COMMENT '表单定义ID',
    column_meta_id BIGINT        DEFAULT NULL           COMMENT '关联列元数据ID；NULL 为纯布局字段（标题/分割线/说明）',
    field_label    VARCHAR(128)  NOT NULL               COMMENT '字段显示名',
    widget_type    VARCHAR(32)   DEFAULT NULL           COMMENT '控件类型：text/textarea/number/select/radio/checkbox/date/datetime/switch',
    show_in_list   TINYINT       NOT NULL DEFAULT 1     COMMENT '列表是否显示：1 显示 / 0 隐藏',
    show_in_form   TINYINT       NOT NULL DEFAULT 1     COMMENT '表单是否显示：1 显示 / 0 隐藏',
    editable       TINYINT       NOT NULL DEFAULT 1     COMMENT '是否允许编辑：1 可编辑 / 0 只读',
    sort_no        INT           NOT NULL DEFAULT 0     COMMENT '排序号',
    col_span       INT           NOT NULL DEFAULT 24    COMMENT '栅格宽度：12 半宽 / 24 整宽',
    placeholder    VARCHAR(255)  DEFAULT NULL           COMMENT '占位提示',
    default_value  VARCHAR(255)  DEFAULT NULL           COMMENT '默认值（视图级覆盖）',
    validate_json  VARCHAR(1024) DEFAULT NULL           COMMENT '校验规则JSON（如 {"min":0,"max":150}）',
    remark         VARCHAR(255)  DEFAULT NULL           COMMENT '备注',
    status         TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 正常 / 0 禁用',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_form_id (form_id),
    KEY idx_column_meta_id (column_meta_id)
) COMMENT='表单字段配置表（表单视图层 · 字段属性详情）';

-- ---------------------------------------------------------------------
-- 字段选项表（选项层）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS form_field_option;
CREATE TABLE form_field_option (
    id              BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    field_config_id BIGINT        NOT NULL               COMMENT '表单字段配置ID',
    option_label    VARCHAR(128)  NOT NULL               COMMENT '选项显示名',
    option_value    VARCHAR(128)  NOT NULL               COMMENT '选项值',
    sort_no         INT           NOT NULL DEFAULT 0     COMMENT '排序号',
    status          TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 正常 / 0 禁用',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_field_config_id (field_config_id)
) COMMENT='字段选项表（选项层）';

-- ---------------------------------------------------------------------
-- 表单定义版本快照表（版本回滚）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS form_definition_version;
CREATE TABLE form_definition_version (
  id BIGINT NOT NULL COMMENT '主键ID（雪花算法）',
  tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID（行级隔离）',
  definition_id BIGINT NOT NULL COMMENT '表单定义ID',
  version INT NOT NULL COMMENT '版本号',
  snapshot_json MEDIUMTEXT NOT NULL COMMENT '定义+字段+选项的完整快照JSON',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_tenant_id (tenant_id),
  KEY idx_definition_version (definition_id, version)
) COMMENT='表单定义版本快照表';

-- ---------------------------------------------------------------------
-- 后端接口日志表（whale-spring-boot-starter ApiLogFilter 采集，结构与 bluewhale_auth.api_log 一致）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS api_log;
CREATE TABLE api_log (
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
) COMMENT='后端接口日志表';