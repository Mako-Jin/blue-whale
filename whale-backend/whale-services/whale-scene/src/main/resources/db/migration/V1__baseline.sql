-- =====================================================================
-- BlueWhale 城池场景导航服务（whale-scene）Flyway 基线脚本
-- =====================================================================
-- 库名       : bluewhale_scene（空库启动由 Flyway 自动建表）
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成）、tenant_id、create_time、update_time、
--              deleted TINYINT（逻辑删除：0 未删除 / 1 已删除）
-- 租户隔离   : tenant_id 由 starter 的 TenantLineInnerInterceptor 自动追加/填充；
--              跨租户一律按不存在处理，不泄露存在性。
-- 业务口径   : 场景只保存布局与入口元数据，不直连业务库、不保存业务数据与文件字节；
--              3D 为前端可选增强层（M3），本库 M1/M2 只存 2D 坐标与预留 z 高度。
-- 脚本约束   : 禁止 DROP / TRUNCATE；种子使用 INSERT ... WHERE NOT EXISTS，
--              可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 场景（城池）聚合根
--    status: DRAFT / PUBLISHED / OFFLINE
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sc_scene (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    scene_key           VARCHAR(64)   NOT NULL                COMMENT '场景标识（租户内唯一）',
    name                VARCHAR(128)  NOT NULL                COMMENT '场景名称',
    description         VARCHAR(500)  DEFAULT NULL            COMMENT '场景说明',
    status              VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT / PUBLISHED / OFFLINE',
    layout_version      INT           NOT NULL DEFAULT 0      COMMENT '已发布布局版本号（0 未发布）',
    published_at        DATETIME      DEFAULT NULL            COMMENT '最近发布时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sc_scene_key (tenant_id, scene_key),
    KEY idx_sc_scene_status (tenant_id, status, update_time)
) COMMENT='城池场景聚合根';

-- ---------------------------------------------------------------------
-- 2. 场景节点（区域 / 建筑 / 入口，树形结构）
--    coordinate_x / coordinate_y 取值 0–1000，前端换算为 2D 百分比坐标；
--    coordinate_z 预留给 M3 的 3D 高度，M1/M2 可为空。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sc_scene_node (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    scene_id            BIGINT        NOT NULL                COMMENT '所属场景ID',
    node_key            VARCHAR(64)   NOT NULL                COMMENT '节点标识（场景内唯一）',
    parent_key          VARCHAR(64)   DEFAULT NULL            COMMENT '父节点标识（根节点为空）',
    node_type           VARCHAR(16)   NOT NULL                COMMENT '节点类型：REGION / BUILDING / ENTRANCE',
    name                VARCHAR(64)   NOT NULL                COMMENT '节点名称',
    icon                VARCHAR(64)   DEFAULT NULL            COMMENT '图标标识',
    coordinate_x        INT           NOT NULL DEFAULT 500    COMMENT '2D 横坐标（0–1000）',
    coordinate_y        INT           NOT NULL DEFAULT 500    COMMENT '2D 纵坐标（0–1000）',
    coordinate_z        INT           DEFAULT NULL            COMMENT '3D 高度（M3 使用）',
    sort_no             INT           NOT NULL DEFAULT 1      COMMENT '同级排序号',
    status              VARCHAR(16)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED / DISABLED',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sc_node_key (tenant_id, scene_id, node_key),
    KEY idx_sc_node_tree (tenant_id, scene_id, parent_key, sort_no),
    KEY idx_sc_node_type (tenant_id, scene_id, node_type)
) COMMENT='场景节点树';

-- ---------------------------------------------------------------------
-- 3. 节点入口绑定（一个节点至多一个入口，重复设置即覆盖）
--    target_path 只允许平台内路径（/ 开头）或 http/https 外链，
--    禁止 javascript: / data: / 内网地址 / 开放重定向。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sc_node_binding (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    node_id             BIGINT        NOT NULL                COMMENT '节点ID',
    entry_type          VARCHAR(16)   NOT NULL                COMMENT '入口类型：SYSTEM / PAGE / KNOWLEDGE / EXTERNAL',
    target_path         VARCHAR(512)  NOT NULL                COMMENT '目标地址（平台路径或白名单外链）',
    permission_code     VARCHAR(128)  DEFAULT NULL            COMMENT '访问入口所需权限码，空表示登录即可',
    enabled             TINYINT       NOT NULL DEFAULT 1      COMMENT '是否启用：1 启用 / 0 停用',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sc_binding_node (tenant_id, node_id),
    KEY idx_sc_binding_node (node_id)
) COMMENT='节点入口绑定';

-- ---------------------------------------------------------------------
-- 4. 场景可见性（node_id = 0 为场景级规则；DENY 优先）
--    subject_type: ALL / ROLE / DEPT / USER
--    effect:       ALLOW / DENY
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sc_visibility (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    scene_id            BIGINT        NOT NULL                COMMENT '所属场景ID',
    node_id             BIGINT        NOT NULL DEFAULT 0      COMMENT '节点ID（0 表示场景级）',
    subject_type        VARCHAR(16)   NOT NULL                COMMENT '主体类型：ALL / ROLE / DEPT / USER',
    subject_id          VARCHAR(64)   DEFAULT NULL            COMMENT '主体标识（ALL 为空）',
    effect              VARCHAR(16)   NOT NULL                COMMENT '效果：ALLOW / DENY',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_sc_visibility_scene (tenant_id, scene_id, node_id),
    KEY idx_sc_visibility_subject (tenant_id, scene_id, subject_type, subject_id)
) COMMENT='场景与节点可见性规则';

-- ---------------------------------------------------------------------
-- 5. 布局发布快照（发布时生成，支持回滚比对与完整性校验）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sc_layout_version (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    scene_id            BIGINT        NOT NULL                COMMENT '所属场景ID',
    version             INT           NOT NULL                COMMENT '版本号（场景内递增）',
    layout_json         LONGTEXT      NOT NULL                COMMENT '布局JSON（节点树 + 入口）',
    checksum            VARCHAR(64)   NOT NULL                COMMENT '布局内容 SHA-256 校验和',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sc_layout_version (tenant_id, scene_id, version),
    KEY idx_sc_layout_scene (tenant_id, scene_id, version)
) COMMENT='场景布局发布快照';

-- ---------------------------------------------------------------------
-- 6. 操作审计日志表（whale-spring-boot-starter @AuditLog 切面采集）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名（冗余存储，用户删除后仍可追溯）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-scene',
    operation      VARCHAR(128)  DEFAULT NULL           COMMENT '操作描述',
    operate_type   VARCHAR(32)   DEFAULT NULL           COMMENT '操作类型：CREATE/UPDATE/DELETE/QUERY/OTHER 等',
    method         VARCHAR(255)  DEFAULT NULL           COMMENT '请求方法（全限定类名.方法名）',
    request_uri    VARCHAR(255)  DEFAULT NULL           COMMENT '请求URI',
    http_method    VARCHAR(16)   DEFAULT NULL           COMMENT 'HTTP 方法：GET/POST/PUT/DELETE',
    target_type    VARCHAR(64)   DEFAULT NULL           COMMENT '操作对象类型，如 sc_scene / sc_scene_node',
    target_id      VARCHAR(64)   DEFAULT NULL           COMMENT '操作对象ID',
    request_params TEXT          NULL                   COMMENT '请求参数（JSON，敏感字段已脱敏）',
    result_body    TEXT          NULL                   COMMENT '返回体（JSON，脱敏 + 截断）',
    response_code  INT           DEFAULT NULL           COMMENT '响应状态码',
    biz_status     TINYINT       NOT NULL DEFAULT 1     COMMENT '业务结果：1 成功 / 0 失败',
    error_msg      VARCHAR(512)  DEFAULT NULL           COMMENT '失败原因（异常类名 + 消息，已脱敏）',
    ip             VARCHAR(64)   DEFAULT NULL           COMMENT '操作IP地址（兼容 IPv6）',
    user_agent     VARCHAR(512)  DEFAULT NULL           COMMENT '用户代理（浏览器/客户端标识）',
    cost_ms        BIGINT        DEFAULT NULL           COMMENT '接口耗时（毫秒）',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（即操作发生时间）',
    PRIMARY KEY (id),
    KEY idx_trace_id (trace_id),
    KEY idx_user_id (user_id),
    KEY idx_module (module),
    KEY idx_operate_type (operate_type),
    KEY idx_biz_status (biz_status),
    KEY idx_tenant_time (tenant_id, create_time),
    KEY idx_create_time (create_time)
) COMMENT='操作审计日志表';

-- ---------------------------------------------------------------------
-- 7. 后端接口日志表（whale-spring-boot-starter ApiLogFilter 采集）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS api_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-scene',
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

-- ---------------------------------------------------------------------
-- 8. 基线种子：平台租户 1 的示例城池（2D 导航，3 个系统入口，幂等）
--    OA / ERP / 知识库三个入口按权限码控制可见性。
-- ---------------------------------------------------------------------
INSERT INTO sc_scene (id, tenant_id, scene_key, name, description, status, layout_version, published_at, deleted)
SELECT 5001, 1, 'bluewhale-city', '蓝鲸城', '平台默认 2D 城池导航示例', 'PUBLISHED', 1, CURRENT_TIMESTAMP, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene WHERE id = 5001);

INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, sort_no, status, deleted)
SELECT 5101, 1, 5001, 'city-core', NULL, 'REGION', '蓝鲸城中心', 'city', 500, 500, 1, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5101);

INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, sort_no, status, deleted)
SELECT 5102, 1, 5001, 'oa-hall', 'city-core', 'ENTRANCE', '协同办公', 'oa', 250, 300, 1, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5102);

INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, sort_no, status, deleted)
SELECT 5103, 1, 5001, 'erp-yard', 'city-core', 'ENTRANCE', '经营管理', 'erp', 500, 300, 2, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5103);

INSERT INTO sc_scene_node (id, tenant_id, scene_id, node_key, parent_key, node_type, name, icon,
                           coordinate_x, coordinate_y, sort_no, status, deleted)
SELECT 5104, 1, 5001, 'doc-tower', 'city-core', 'ENTRANCE', '知识库', 'doc', 750, 300, 3, 'ENABLED', 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_scene_node WHERE id = 5104);

INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5201, 1, 5102, 'SYSTEM', '/oa', 'sys:oa:leave:list', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5201);

INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5202, 1, 5103, 'SYSTEM', '/erp', 'sys:erp:item:list', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5202);

INSERT INTO sc_node_binding (id, tenant_id, node_id, entry_type, target_path, permission_code, enabled, deleted)
SELECT 5203, 1, 5104, 'KNOWLEDGE', '/doc', 'sys:doc:space:list', 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sc_node_binding WHERE id = 5203);
