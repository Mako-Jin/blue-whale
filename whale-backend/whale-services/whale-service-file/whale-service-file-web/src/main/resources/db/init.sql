-- =====================================================================
-- BlueWhale 微服务平台 - 文件服务（whale-file）数据库初始化脚本
-- =====================================================================
-- 建议库名   : bluewhale_file
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成）、create_time、update_time、
--              deleted TINYINT（逻辑删除：0 未删除 / 1 已删除）
-- 说明       : 本表仅记录文件元数据，文件实体存放在对象存储
--              （bucket + object_key 定位）；uploader_id 为逻辑外键
-- =====================================================================

-- ---------------------------------------------------------------------
-- 文件信息表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS file_info;
CREATE TABLE file_info (
    id            BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id     BIGINT        NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    file_name     VARCHAR(255)  NOT NULL               COMMENT '存储文件名（系统生成的唯一名称）',
    original_name VARCHAR(255)  NOT NULL               COMMENT '原始文件名（用户上传时的文件名）',
    bucket        VARCHAR(64)   NOT NULL               COMMENT '对象存储桶名称',
    storage_type  VARCHAR(32)   DEFAULT NULL           COMMENT '落盘时的存储插件类型（local/minio）；存量由启动期回填',
    object_key    VARCHAR(512)  NOT NULL               COMMENT '对象存储键（桶内完整路径）',
    file_size     BIGINT        NOT NULL DEFAULT 0     COMMENT '文件大小（字节）',
    content_type  VARCHAR(128)  DEFAULT NULL           COMMENT '文件MIME类型，如 image/png',
    md5           VARCHAR(32)   DEFAULT NULL           COMMENT '文件MD5摘要（用于去重/秒传/完整性校验）',
    status        TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 正常 / 0 禁用 / 2 归档锁定',
    uploader_id   BIGINT        DEFAULT NULL           COMMENT '上传用户ID（逻辑关联 sys_user.id）',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    delete_time   DATETIME      DEFAULT NULL           COMMENT '逻辑删除时间；恢复时置空',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_tenant_md5 (tenant_id, md5),
    KEY idx_tenant_bucket (tenant_id, bucket),
    KEY idx_uploader_id (uploader_id)
) COMMENT='文件信息表';

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