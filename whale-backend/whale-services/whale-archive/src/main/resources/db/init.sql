-- =====================================================================
-- BlueWhale 微服务平台 - 档案服务（whale-archive）数据库初始化脚本
-- =====================================================================
-- 建议库名   : bluewhale_archive（先执行 create-database.sql）
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成）、create_time、update_time、
--              deleted TINYINT（逻辑删除：0 未删除 / 1 已删除）
-- 幂等约定   : CREATE TABLE IF NOT EXISTS，可在既有库上重复执行
-- 说明       : 文件实体仍由 whale-file 保管（archive_file.file_id 为逻辑外键），
--              本库只存档案条目、引用、锁定状态与同步台账，不存文件二进制。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 档案门类表：与档案分类树
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS archive_category (
    id              BIGINT       NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT       NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    code            VARCHAR(32)  NOT NULL               COMMENT '门类编码（租户内唯一，如 GL/WS）',
    name            VARCHAR(128) NOT NULL               COMMENT '门类名称',
    parent_id       BIGINT       NOT NULL DEFAULT 0     COMMENT '上级门类ID（0 表示根节点）',
    level           INT          NOT NULL DEFAULT 1     COMMENT '层级（根节点为 1）',
    retention_years INT          DEFAULT NULL           COMMENT '默认保管期限（年；NULL 表示长期）',
    security_level  TINYINT      NOT NULL DEFAULT 2     COMMENT '密级：1 公开 / 2 内部 / 3 秘密',
    sort            INT          NOT NULL DEFAULT 0     COMMENT '排序号（同级升序）',
    status          TINYINT      NOT NULL DEFAULT 1     COMMENT '状态：1 启用 / 0 停用',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, code),
    KEY idx_parent_id (parent_id)
) COMMENT='档案门类表';

-- ---------------------------------------------------------------------
-- 归档规则表：工作流定义编码 → 归档门类映射
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS archive_rule (
    id              BIGINT       NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT       NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    definition_code VARCHAR(64)  NOT NULL               COMMENT '工作流定义编码（租户内唯一）',
    category_id     BIGINT       NOT NULL               COMMENT '归档门类ID（逻辑关联 archive_category.id）',
    retention_years INT          DEFAULT NULL           COMMENT '保管期限（年；NULL 表示长期）',
    security_level  TINYINT      NOT NULL DEFAULT 2     COMMENT '密级：1 公开 / 2 内部 / 3 秘密',
    auto_archive    TINYINT      NOT NULL DEFAULT 0     COMMENT '是否自动归档：1 命中即归档 / 0 进待整理',
    enabled         TINYINT      NOT NULL DEFAULT 1     COMMENT '是否启用：1 启用 / 0 停用',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_definition_code (tenant_id, definition_code),
    KEY idx_category_id (category_id)
) COMMENT='归档规则表';

-- ---------------------------------------------------------------------
-- 档案条目表：一个已审批实例对应一条档案
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS archive_item (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    archive_no       VARCHAR(64)   DEFAULT NULL           COMMENT '档号（确认归档时分配，租户内唯一；BW-门类-年度-流水）',
    title            VARCHAR(255)  NOT NULL               COMMENT '档案标题',
    category_id      BIGINT        DEFAULT NULL           COMMENT '门类ID（逻辑关联 archive_category.id）',
    category_path    VARCHAR(512)  DEFAULT NULL           COMMENT '门类路径（编码以 / 连接，便于检索）',
    fonds_code       VARCHAR(64)   DEFAULT NULL           COMMENT '全宗号',
    year             INT           DEFAULT NULL           COMMENT '归档年度',
    retention_years  INT           DEFAULT NULL           COMMENT '保管期限（年；NULL 表示长期）',
    expire_at        DATETIME      DEFAULT NULL           COMMENT '到期时间（长期为空）',
    security_level   TINYINT       NOT NULL DEFAULT 2     COMMENT '密级：1 公开 / 2 内部 / 3 秘密',
    source_type      VARCHAR(32)   NOT NULL               COMMENT '来源类型：WORKFLOW 工作流实例',
    source_id        VARCHAR(64)   NOT NULL               COMMENT '来源实例ID（字符串承载，避免前端精度丢失）',
    definition_code  VARCHAR(64)   DEFAULT NULL           COMMENT '流程定义编码',
    business_key     VARCHAR(128)  DEFAULT NULL           COMMENT '业务单号 / 表单数据ID',
    initiator        VARCHAR(64)   DEFAULT NULL           COMMENT '发起人账号（工作流 process_instance.initiator）',
    finished_at      DATETIME      DEFAULT NULL           COMMENT '来源实例办结时间',
    item_status      TINYINT       NOT NULL DEFAULT 0     COMMENT '状态：0 待整理 / 1 已归档 / 2 已销毁',
    summary          VARCHAR(2000) DEFAULT NULL           COMMENT '档案摘要',
    keywords         VARCHAR(512)  DEFAULT NULL           COMMENT '关键词（逗号分隔）',
    snapshot_file_id BIGINT        DEFAULT NULL           COMMENT '不可变快照文件ID（逻辑关联 file_info.id）',
    snapshot_hash    VARCHAR(64)   DEFAULT NULL           COMMENT '快照 SHA-256 摘要',
    archived_by      BIGINT        DEFAULT NULL           COMMENT '确认归档人ID（逻辑关联 sys_user.id）',
    archived_at      DATETIME      DEFAULT NULL           COMMENT '确认归档时间',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_archive_no (tenant_id, archive_no),
    UNIQUE KEY uk_tenant_source (tenant_id, source_type, source_id),
    KEY idx_category_year (category_id, year),
    KEY idx_item_status (item_status),
    KEY idx_finished_at (finished_at)
) COMMENT='档案条目表';

-- ---------------------------------------------------------------------
-- 档案文件引用表：原件 / 附件 / 快照，文件实体在 whale-file
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS archive_file (
    id            BIGINT       NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT       NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    item_id       BIGINT       NOT NULL               COMMENT '档案条目ID（逻辑关联 archive_item.id）',
    file_id       BIGINT       NOT NULL               COMMENT '文件ID（逻辑关联 whale-file 的 file_info.id）',
    file_role     VARCHAR(16)  NOT NULL               COMMENT '文件角色：ORIGINAL 原件 / ATTACHMENT 附件 / SNAPSHOT 快照',
    original_name VARCHAR(255) DEFAULT NULL           COMMENT '原始文件名（冗余，便于展示与检索）',
    file_size     BIGINT       DEFAULT NULL           COMMENT '文件大小（字节）',
    content_type  VARCHAR(128) DEFAULT NULL           COMMENT 'MIME 类型',
    md5           VARCHAR(32)  DEFAULT NULL           COMMENT '文件 MD5（冗余校验）',
    sort          INT          NOT NULL DEFAULT 0     COMMENT '排序号',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_file_role (item_id, file_id, file_role),
    KEY idx_tenant_id (tenant_id),
    KEY idx_file_id (file_id)
) COMMENT='档案文件引用表';

-- ---------------------------------------------------------------------
-- 同步台账表：保证同一来源实例只归档一次，并记录失败重试
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS archive_source_record (
    id           BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id    BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    source_type  VARCHAR(32)   NOT NULL               COMMENT '来源类型：WORKFLOW 工作流实例',
    source_id    VARCHAR(64)   NOT NULL               COMMENT '来源实例ID',
    sync_status  TINYINT       NOT NULL DEFAULT 0     COMMENT '同步状态：0 待处理 / 1 成功 / 2 失败',
    retry_count  INT           NOT NULL DEFAULT 0     COMMENT '已重试次数',
    last_error   VARCHAR(1000) DEFAULT NULL           COMMENT '最近一次失败原因（已脱敏）',
    last_sync_at DATETIME      DEFAULT NULL           COMMENT '最近一次同步时间',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_source (tenant_id, source_type, source_id),
    KEY idx_sync_status (sync_status)
) COMMENT='档案同步台账表';

-- ---------------------------------------------------------------------
-- 档号流水表：按「门类编码 + 年度」原子自增
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS archive_sequence (
    id            BIGINT      NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT      NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    category_code VARCHAR(32) NOT NULL               COMMENT '门类编码',
    year          INT         NOT NULL               COMMENT '归档年度',
    current_value BIGINT      NOT NULL DEFAULT 0     COMMENT '当前已分配流水号',
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT     NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_category_year (tenant_id, category_code, year)
) COMMENT='档号流水表';

-- ---------------------------------------------------------------------
-- 同步游标表：记录工作流增量拉取的办结时间水位（服务重启后继续，不重扫全量）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS archive_sync_state (
    id          BIGINT       NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id   BIGINT       NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离；游标按租户独立）',
    name        VARCHAR(64)  NOT NULL               COMMENT '游标名称（如 workflow_approved）',
    cursor_time DATETIME     DEFAULT NULL           COMMENT '已处理到的工作流实例更新时间水位',
    last_run_at DATETIME     DEFAULT NULL           COMMENT '最近一次同步开始时间',
    last_result VARCHAR(1000) DEFAULT NULL          COMMENT '最近一次同步结果摘要',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_name (tenant_id, name)
) COMMENT='档案同步游标表';

-- ---------------------------------------------------------------------
-- 操作审计日志表（与 whale-audit 同构，append-only）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（扩展预留）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-archive',
    operation      VARCHAR(128)  DEFAULT NULL           COMMENT '操作描述',
    operate_type   VARCHAR(32)   DEFAULT NULL           COMMENT '操作类型：CREATE/UPDATE/DELETE/QUERY/OTHER 等',
    method         VARCHAR(255)  DEFAULT NULL           COMMENT '请求方法（全限定类名.方法名）',
    request_uri    VARCHAR(255)  DEFAULT NULL           COMMENT '请求URI',
    http_method    VARCHAR(16)   DEFAULT NULL           COMMENT 'HTTP 方法',
    target_type    VARCHAR(64)   DEFAULT NULL           COMMENT '操作对象类型，如 archive_item',
    target_id      VARCHAR(64)   DEFAULT NULL           COMMENT '操作对象ID',
    request_params TEXT          NULL                   COMMENT '请求参数（敏感字段脱敏后写入）',
    result_body    TEXT          NULL                   COMMENT '返回体（脱敏 + 截断）',
    response_code  INT           DEFAULT NULL           COMMENT '响应状态码',
    biz_status     TINYINT       NOT NULL DEFAULT 1     COMMENT '业务结果：1 成功 / 0 失败',
    error_msg      VARCHAR(512)  DEFAULT NULL           COMMENT '失败原因（已脱敏）',
    ip             VARCHAR(64)   DEFAULT NULL           COMMENT '操作IP地址',
    user_agent     VARCHAR(512)  DEFAULT NULL           COMMENT '用户代理',
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