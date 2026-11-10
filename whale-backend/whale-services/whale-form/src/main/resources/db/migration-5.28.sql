-- =====================================================================
-- BlueWhale whale-form 增量迁移脚本（Wave 5.28：动态表单元数据模型 · 三层分离）
-- =====================================================================
-- 适用场景 : 已按 Wave 5.27 部署、库中已存在 form_definition / form_data /
--            form_table_registry 旧结构的运行库（保留存量数据与物理表）。
-- 全新建库 : 请直接执行 init.sql，无需本脚本。
-- 说明     : 本脚本为一次性执行脚本（MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS）。
--            迁移不改动存量物理表 form_data_{code}_v{version} 及其数据：
--            迁移后 table_code == form.code、table_version == form.version，
--            物理表名与 Wave 5.27 完全同构。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 表定义层：表定义表 + 列元数据表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS form_table_definition (
    id           BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    table_code   VARCHAR(64)   NOT NULL               COMMENT '表编码（业务唯一；物理表名来源，^[a-z][a-z0-9_]{0,39}$）',
    table_name   VARCHAR(128)  NOT NULL               COMMENT '表名称',
    version      INT           NOT NULL DEFAULT 1     COMMENT '版本号',
    remark       VARCHAR(255)  DEFAULT NULL           COMMENT '备注',
    status       TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 正常 / 0 禁用',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_table_code (table_code)
) COMMENT='表定义表（表定义层）';

CREATE TABLE IF NOT EXISTS form_column_meta (
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
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_table_definition_id (table_definition_id)
) COMMENT='列元数据表（表定义层 · 动态列详情）';

-- ---------------------------------------------------------------------
-- 2. 表单视图层：字段配置表 + 选项层：字段选项表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS form_field_config (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    form_id        BIGINT        NOT NULL               COMMENT '表单定义ID',
    column_meta_id BIGINT        DEFAULT NULL           COMMENT '关联列元数据ID；NULL 为纯布局字段（标题/分割线/说明）',
    field_label    VARCHAR(128)  NOT NULL               COMMENT '字段显示名',
    widget_type    VARCHAR(32)   DEFAULT NULL           COMMENT '控件类型：text/textarea/number/select/radio/checkbox/date/datetime/switch',
    show_in_list   TINYINT       NOT NULL DEFAULT 1     COMMENT '列表是否显示：1 显示 / 0 隐藏',
    show_in_form   TINYINT       NOT NULL DEFAULT 1     COMMENT '表单是否显示：1 显示 / 0 隐藏',
    editable       TINYINT       NOT NULL DEFAULT 1     COMMENT '是否允许编辑：1 可编辑 / 0 只读',
    sort_no        INT           NOT NULL DEFAULT 0     COMMENT '排序号',
    placeholder    VARCHAR(255)  DEFAULT NULL           COMMENT '占位提示',
    default_value  VARCHAR(255)  DEFAULT NULL           COMMENT '默认值（视图级覆盖）',
    validate_json  VARCHAR(1024) DEFAULT NULL           COMMENT '校验规则JSON（如 {"min":0,"max":150}）',
    remark         VARCHAR(255)  DEFAULT NULL           COMMENT '备注',
    status         TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 正常 / 0 禁用',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_form_id (form_id),
    KEY idx_column_meta_id (column_meta_id)
) COMMENT='表单字段配置表（表单视图层 · 字段属性详情）';

CREATE TABLE IF NOT EXISTS form_field_option (
    id              BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    field_config_id BIGINT        NOT NULL               COMMENT '表单字段配置ID',
    option_label    VARCHAR(128)  NOT NULL               COMMENT '选项显示名',
    option_value    VARCHAR(128)  NOT NULL               COMMENT '选项值',
    sort_no         INT           NOT NULL DEFAULT 0     COMMENT '排序号',
    status          TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 正常 / 0 禁用',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_field_config_id (field_config_id)
) COMMENT='字段选项表（选项层）';

-- ---------------------------------------------------------------------
-- 3. 存量表兼容列（历史行保留原值）
-- ---------------------------------------------------------------------

-- 表单定义表：新增所属表定义ID（表:表单 = 1:N，迁移后回填）
ALTER TABLE form_definition
    ADD COLUMN table_definition_id BIGINT DEFAULT NULL COMMENT '所属表定义ID（表:表单 1:N，迁移后回填；NULL 为未迁移的旧定义）' AFTER name,
    ADD KEY idx_table_definition_id (table_definition_id);

-- 物理表登记表：新增表维度列；definition_* 放宽为可空（元数据模型下为 NULL）
ALTER TABLE form_table_registry
    ADD COLUMN table_definition_id BIGINT DEFAULT NULL COMMENT '表定义ID（元数据模型；NULL 为旧模型）' AFTER id,
    ADD COLUMN table_code VARCHAR(64) DEFAULT NULL COMMENT '表编码（元数据模型；旧模型回落 definition_code）' AFTER table_definition_id,
    ADD COLUMN table_version INT DEFAULT NULL COMMENT '表版本（元数据模型；旧模型回落 definition_version）' AFTER table_code,
    MODIFY COLUMN definition_id BIGINT DEFAULT NULL COMMENT '表单定义ID（旧模型；元数据模型为 NULL）',
    MODIFY COLUMN definition_code VARCHAR(64) DEFAULT NULL COMMENT '表单定义编码（旧模型）',
    MODIFY COLUMN definition_version INT DEFAULT NULL COMMENT '表单定义版本（旧模型）',
    ADD KEY idx_table_code_version (table_code, table_version),
    ADD KEY idx_table_definition_id (table_definition_id);

-- 表单数据表：新增表维度与来源视图列
ALTER TABLE form_data
    ADD COLUMN table_definition_id BIGINT DEFAULT NULL COMMENT '所属表定义ID（元数据模型；NULL 为旧模型）' AFTER definition_version,
    ADD COLUMN table_code VARCHAR(64) DEFAULT NULL COMMENT '所属表编码（路由物理表；缺省回落 definition_code）' AFTER table_definition_id,
    ADD COLUMN table_version INT DEFAULT NULL COMMENT '所属表版本（路由物理表；缺省回落 definition_version）' AFTER table_code,
    ADD COLUMN form_definition_id BIGINT DEFAULT NULL COMMENT '提交来源的表单视图ID（元数据模型；NULL 为旧模型）' AFTER table_version,
    ADD KEY idx_table_code_version (table_code, table_version),
    ADD KEY idx_form_definition_id (form_definition_id);

-- ---------------------------------------------------------------------
-- 4. 权限种子（bluewhale_auth.sys_permission，id 53–57，与 whale-form 注解一一对应）
--    注：本段在 bluewhale_auth 库执行；如该库已由 auth init.sql 建好可跳过。
-- ---------------------------------------------------------------------
-- INSERT INTO sys_permission
--     (id, parent_id, perm_code, perm_name, perm_type, path, method, sort_no, status)
-- VALUES
--     (53, 0, 'sys:form:table:add', '新增表定义', 'API', '/form/table-definition', 'POST', 53, 1),
--     (54, 0, 'sys:form:table:edit', '修改表定义', 'API', '/form/table-definition/**', 'PUT', 54, 1),
--     (55, 0, 'sys:form:table:delete', '删除表定义', 'API', '/form/table-definition/{id}', 'DELETE', 55, 1),
--     (56, 0, 'sys:form:table:list', '查询表定义', 'API', '/form/table-definition/**', 'GET', 56, 1),
--     (57, 0, 'sys:form:table:publish', '发布表定义', 'API', '/form/table-definition/{id}/publish', 'POST', 57, 1);
