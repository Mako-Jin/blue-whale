-- =====================================================================
-- BlueWhale 微服务平台 - 统一消息服务（whale-message）数据库初始化脚本
-- =====================================================================
-- 库名       : bluewhale_message
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成）、create_time、update_time、
--              deleted TINYINT（逻辑删除：0 未删除 / 1 已删除）
-- 幂等约定   : CREATE TABLE IF NOT EXISTS；模板种子使用 INSERT IGNORE，可重复执行
-- 租户隔离   : tenant_id 由 starter 的 TenantLineInnerInterceptor 自动追加/填充
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 消息模板
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS msg_template (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    template_code    VARCHAR(64)   NOT NULL                COMMENT '模板编码（租户内逻辑唯一）',
    template_name    VARCHAR(128)  NOT NULL                COMMENT '模板名称',
    title_template   VARCHAR(255)  DEFAULT NULL            COMMENT '标题模板，支持 ${key} 占位符',
    content_template TEXT          DEFAULT NULL            COMMENT '内容模板，支持 ${key} 占位符',
    status           TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用',
    remark           VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_template_code (tenant_id, template_code),
    KEY idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='消息模板表';

-- ---------------------------------------------------------------------
-- 2. 逻辑消息记录
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS msg_record (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    biz_type         VARCHAR(32)   DEFAULT NULL            COMMENT '业务类型，如 PM_TASK / CONTRACT_EXPIRE',
    biz_id           VARCHAR(64)   DEFAULT NULL            COMMENT '业务标识',
    template_code    VARCHAR(64)   DEFAULT NULL            COMMENT '模板编码',
    idempotency_key  VARCHAR(96)   DEFAULT NULL            COMMENT '幂等键（租户内唯一；NULL 不参与唯一约束）',
    title            VARCHAR(128)  DEFAULT NULL            COMMENT '消息标题',
    content          TEXT          DEFAULT NULL            COMMENT '消息正文',
    payload_json     TEXT          DEFAULT NULL            COMMENT '模板变量 JSON',
    channels         VARCHAR(128)  DEFAULT NULL            COMMENT '渠道列表（大写逗号分隔，如 INAPP,IM）',
    status           VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '消息状态：PENDING / SENT / PARTIAL / FAILED / MOCKED / SKIPPED',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_idempotency (tenant_id, idempotency_key),
    KEY idx_tenant_biz (tenant_id, biz_type, biz_id),
    KEY idx_tenant_create_time (tenant_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='逻辑消息记录表';

-- ---------------------------------------------------------------------
-- 3. 单渠道投递记录
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS msg_delivery (
    id                 BIGINT       NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id          BIGINT       NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    message_id         BIGINT       NOT NULL                COMMENT '消息ID（逻辑关联 msg_record.id）',
    channel            VARCHAR(16)  NOT NULL                COMMENT '渠道：INAPP / EMAIL / SMS / IM',
    recipient          VARCHAR(128) NOT NULL                COMMENT '接收标识：用户ID / 邮箱 / 手机号',
    status             VARCHAR(16)  NOT NULL                COMMENT '投递状态：SENT / FAILED / SKIPPED / MOCKED',
    provider_message_id VARCHAR(128) DEFAULT NULL           COMMENT '渠道回执标识（可选）',
    error_message      VARCHAR(512) DEFAULT NULL            COMMENT '失败/跳过原因摘要',
    attempts           INT          NOT NULL DEFAULT 1      COMMENT '尝试次数',
    sent_at            DATETIME     DEFAULT NULL            COMMENT '投递时间',
    read_flag          TINYINT      NOT NULL DEFAULT 0      COMMENT '站内信已读标记：0 未读 / 1 已读（非 INAPP 渠道恒为 0）',
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted            TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_message (tenant_id, message_id),
    KEY idx_tenant_status (tenant_id, status),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='消息投递记录表';

-- ---------------------------------------------------------------------
-- 4. 内置模板（平台租户共享）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO msg_template
    (id, tenant_id, template_code, template_name, title_template, content_template, status, remark)
VALUES
    (900000000000000001, 1, 'SYSTEM_NOTICE', '系统通知', '系统通知', '${content}', 1,
     '内置示例模板：标题固定，正文取请求 content 或 payload.content');
