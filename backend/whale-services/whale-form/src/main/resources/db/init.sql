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
    code                VARCHAR(64)   NOT NULL               COMMENT '表单编码（业务唯一，逻辑删除冲突由服务层前置校验）',
    name                VARCHAR(128)  NOT NULL               COMMENT '表单名称',
    table_definition_id BIGINT        DEFAULT NULL           COMMENT '所属表定义ID（表:表单 1:N，迁移后回填；NULL 为未迁移的旧定义）',
    schema_json         MEDIUMTEXT    NULL                   COMMENT '表单结构定义JSON（由字段配置编译的发布快照，最长64KB）',
    version             INT           NOT NULL DEFAULT 1     COMMENT '版本号',
    status              TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 正常 / 0 禁用',
    remark              VARCHAR(255)  DEFAULT NULL           COMMENT '备注',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_code (code),
    KEY idx_table_definition_id (table_definition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='表单定义表（表单视图层）';

-- ---------------------------------------------------------------------
-- 表单数据表
-- ---------------------------------------------------------------------
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
    data_json           MEDIUMTEXT    NULL                   COMMENT '表单数据JSON（最长64KB；过渡期双写，结构化真值见物理表）',
    submitter           VARCHAR(64)   DEFAULT NULL           COMMENT '提交人账号',
    status              TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 有效 / 0 作废',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_definition_id (definition_id),
    KEY idx_biz_key (biz_key),
    KEY idx_tenant_id (tenant_id),
    KEY idx_table_code_version (table_code, table_version),
    KEY idx_form_definition_id (form_definition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='表单数据表（主记录；结构化字段落各版本物理表 form_data_{code}_v{version}）';

-- ---------------------------------------------------------------------
-- 表单物理表登记表（表定义 ↔ 物理表映射 + 发布时 schema 快照）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS form_table_registry;
CREATE TABLE form_table_registry (
    id                  BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    table_definition_id BIGINT        DEFAULT NULL           COMMENT '表定义ID（元数据模型；NULL 为旧模型）',
    table_code          VARCHAR(64)   DEFAULT NULL           COMMENT '表编码（元数据模型；旧模型回落 definition_code）',
    table_version       INT           DEFAULT NULL           COMMENT '表版本（元数据模型；旧模型回落 definition_version）',
    definition_id       BIGINT        DEFAULT NULL           COMMENT '表单定义ID（旧模型；元数据模型为 NULL）',
    definition_code     VARCHAR(64)   DEFAULT NULL           COMMENT '表单定义编码（旧模型）',
    definition_version  INT           DEFAULT NULL           COMMENT '表单定义版本（旧模型）',
    physical_table      VARCHAR(64)   NOT NULL               COMMENT '物理表名（form_data_{code}_v{version}）',
    field_count         INT           NOT NULL DEFAULT 0     COMMENT '动态字段数量',
    schema_json         MEDIUMTEXT    NULL                   COMMENT '发布时的表单结构定义快照（JSON）',
    status              TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 生效 / 0 废弃',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_code_version (definition_code, definition_version),
    KEY idx_table_code_version (table_code, table_version),
    KEY idx_definition_id (definition_id),
    KEY idx_table_definition_id (table_definition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='表单物理表登记表';

-- ---------------------------------------------------------------------
-- 表定义表（表定义层）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS form_table_definition;
CREATE TABLE form_table_definition (
    id           BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    table_code   VARCHAR(64)   NOT NULL               COMMENT '表编码（业务唯一；物理表名来源，^[a-z][a-z0-9_]{0,39}$）',
    table_name   VARCHAR(128)  NOT NULL               COMMENT '表名称',
    version      INT           NOT NULL DEFAULT 1     COMMENT '版本号',
    remark       VARCHAR(255)  DEFAULT NULL           COMMENT '备注',
    status       TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 正常 / 0 禁用',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_table_code (table_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='表定义表（表定义层）';

-- ---------------------------------------------------------------------
-- 列元数据表（表定义层 · 动态列详情）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS form_column_meta;
CREATE TABLE form_column_meta (
    id                  BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
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
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_table_definition_id (table_definition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='列元数据表（表定义层 · 动态列详情）';

-- ---------------------------------------------------------------------
-- 表单字段配置表（表单视图层 · 字段属性详情）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS form_field_config;
CREATE TABLE form_field_config (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
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
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_form_id (form_id),
    KEY idx_column_meta_id (column_meta_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='表单字段配置表（表单视图层 · 字段属性详情）';

-- ---------------------------------------------------------------------
-- 字段选项表（选项层）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS form_field_option;
CREATE TABLE form_field_option (
    id              BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    field_config_id BIGINT        NOT NULL               COMMENT '表单字段配置ID',
    option_label    VARCHAR(128)  NOT NULL               COMMENT '选项显示名',
    option_value    VARCHAR(128)  NOT NULL               COMMENT '选项值',
    sort_no         INT           NOT NULL DEFAULT 0     COMMENT '排序号',
    status          TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 正常 / 0 禁用',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_field_config_id (field_config_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字段选项表（选项层）';

-- ---------------------------------------------------------------------
-- 表单定义版本快照表（版本回滚）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS form_definition_version;
CREATE TABLE form_definition_version (
  id BIGINT NOT NULL COMMENT '主键ID（雪花算法）',
  definition_id BIGINT NOT NULL COMMENT '表单定义ID',
  version INT NOT NULL COMMENT '版本号',
  snapshot_json MEDIUMTEXT NOT NULL COMMENT '定义+字段+选项的完整快照JSON',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_definition_version (definition_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='表单定义版本快照表';
