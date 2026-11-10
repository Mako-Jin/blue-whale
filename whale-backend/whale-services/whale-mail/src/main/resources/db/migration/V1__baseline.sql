-- =====================================================================
-- BlueWhale 邮箱服务（whale-mail） Flyway 版本化基线 V1
-- =====================================================================
-- 适用库   : bluewhale_mail（MySQL 8.0+，utf8mb4）
-- 公共字段 : id BIGINT（雪花算法）、create_time、update_time、
--            deleted TINYINT（逻辑删除：0 未删除 / 1 已删除）
-- 租户隔离 : tenant_id 由 starter 的 TenantLineInnerInterceptor 自动追加/填充
-- 边界说明 : 本库只描述「邮箱域」——账号、文件夹、邮件索引、附件、草稿。
--            消息管控（模板/渠道路由/投递记录）在 bluewhale_message，不在此库。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 邮箱账号（平台用户 ↔ 邮箱地址 ↔ 协议端点）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mail_account (
    id              BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    user_id         BIGINT        NOT NULL                COMMENT '平台用户ID（whale-auth sys_user.id）',
    email           VARCHAR(190)  NOT NULL                COMMENT '邮箱地址（租户内唯一）',
    display_name    VARCHAR(96)   DEFAULT NULL            COMMENT '发件显示名',
    domain          VARCHAR(128)  DEFAULT NULL            COMMENT '邮箱域名',
    account_type    VARCHAR(16)   NOT NULL DEFAULT 'PERSONAL' COMMENT '账号类型：PERSONAL 个人 / SHARED 共享',
    status          TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用',
    sync_enabled    TINYINT       NOT NULL DEFAULT 1      COMMENT '是否参与增量同步：1 是 / 0 否',
    secret_ref      VARCHAR(190)  DEFAULT NULL            COMMENT '凭据引用（Nacos 配置键或环境变量名；禁存明文密码）',
    imap_host       VARCHAR(128)  DEFAULT NULL            COMMENT 'IMAP 主机',
    imap_port       INT           NOT NULL DEFAULT 993    COMMENT 'IMAP 端口（默认 993 SSL）',
    imap_ssl        TINYINT       NOT NULL DEFAULT 1      COMMENT 'IMAP 是否 SSL：1 是 / 0 否',
    smtp_host       VARCHAR(128)  DEFAULT NULL            COMMENT 'SMTP 主机',
    smtp_port       INT           NOT NULL DEFAULT 587    COMMENT 'SMTP 端口（默认 587 STARTTLS）',
    smtp_ssl        TINYINT       NOT NULL DEFAULT 0      COMMENT 'SMTP 是否 SSL：1 是（465）/ 0 STARTTLS（587）',
    quota_bytes     BIGINT        NOT NULL DEFAULT 0      COMMENT '配额（字节，0 表示未知）',
    used_bytes      BIGINT        NOT NULL DEFAULT 0      COMMENT '已用（字节）',
    last_sync_time  DATETIME      DEFAULT NULL            COMMENT '最近同步时间',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_email (tenant_id, email),
    KEY idx_tenant_user (tenant_id, user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='邮箱账号表';

-- ---------------------------------------------------------------------
-- 2. 邮箱文件夹（系统文件夹 + 自定义文件夹）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mail_folder (
    id              BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    account_id      BIGINT        NOT NULL                COMMENT '所属邮箱账号ID',
    folder_code     VARCHAR(32)   NOT NULL                COMMENT '文件夹编码：INBOX/SENT/DRAFT/TRASH/JUNK/CUSTOM',
    name            VARCHAR(96)   NOT NULL                COMMENT '展示名称',
    imap_name       VARCHAR(190)  NOT NULL                COMMENT 'IMAP 原始文件夹名（含层级分隔符）',
    folder_type     VARCHAR(16)   NOT NULL DEFAULT 'CUSTOM' COMMENT '类型：SYSTEM 系统 / CUSTOM 自定义',
    unread_count    INT           NOT NULL DEFAULT 0      COMMENT '未读数量（同步时重算）',
    total_count     INT           NOT NULL DEFAULT 0      COMMENT '邮件总数（同步时重算）',
    uid_validity    BIGINT        DEFAULT NULL            COMMENT 'IMAP UIDVALIDITY（按文件夹维护，变更即需重建该文件夹索引）',
    last_uid        BIGINT        NOT NULL DEFAULT 0      COMMENT '该文件夹已同步到的最大 UID（增量位点）',
    sort_no         INT           NOT NULL DEFAULT 0      COMMENT '排序号',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_account_imap (tenant_id, account_id, imap_name),
    KEY idx_tenant_account (tenant_id, account_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='邮箱文件夹表';

-- ---------------------------------------------------------------------
-- 3. 邮件索引（元数据 + 正文缓存；正文可由 IMAP 按需回源）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mail_message (
    id              BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    account_id      BIGINT        NOT NULL                COMMENT '所属邮箱账号ID',
    folder_id       BIGINT        NOT NULL                COMMENT '所属文件夹ID',
    uid             BIGINT        NOT NULL                COMMENT 'IMAP UID（文件夹内唯一）',
    uid_validity    BIGINT        DEFAULT NULL            COMMENT '同步时的 UIDVALIDITY 快照',
    message_id      VARCHAR(255)  DEFAULT NULL            COMMENT 'RFC822 Message-ID（跨文件夹去重/会话归并）',
    subject         VARCHAR(512)  DEFAULT NULL            COMMENT '主题',
    from_addr       VARCHAR(190)  DEFAULT NULL            COMMENT '发件人地址',
    from_name       VARCHAR(128)  DEFAULT NULL            COMMENT '发件人显示名',
    to_addrs        VARCHAR(1000) DEFAULT NULL            COMMENT '收件人（多值逗号分隔）',
    cc_addrs        VARCHAR(1000) DEFAULT NULL            COMMENT '抄送',
    bcc_addrs       VARCHAR(1000) DEFAULT NULL            COMMENT '密送',
    reply_to        VARCHAR(190)  DEFAULT NULL            COMMENT '回复地址',
    sent_time       DATETIME      DEFAULT NULL            COMMENT '发信时间',
    received_time   DATETIME      DEFAULT NULL            COMMENT '收信时间',
    size_bytes      BIGINT        NOT NULL DEFAULT 0      COMMENT '邮件大小（字节）',
    has_attachment  TINYINT       NOT NULL DEFAULT 0      COMMENT '是否含附件：1 是 / 0 否',
    seen            TINYINT       NOT NULL DEFAULT 0      COMMENT '已读：1 是 / 0 否',
    flagged         TINYINT       NOT NULL DEFAULT 0      COMMENT '星标：1 是 / 0 否',
    answered        TINYINT       NOT NULL DEFAULT 0      COMMENT '已回复：1 是 / 0 否',
    forwarded       TINYINT       NOT NULL DEFAULT 0      COMMENT '已转发：1 是 / 0 否',
    priority        TINYINT       NOT NULL DEFAULT 3      COMMENT '优先级：1 最高 / 3 普通 / 5 最低',
    preview         VARCHAR(512)  DEFAULT NULL            COMMENT '纯文本摘要（列表展示，避免读正文）',
    body_text       MEDIUMTEXT    DEFAULT NULL            COMMENT '纯文本正文（懒加载）',
    body_html       MEDIUMTEXT    DEFAULT NULL            COMMENT 'HTML 正文（渲染前必须净化）',
    thread_key      VARCHAR(190)  DEFAULT NULL            COMMENT '会话键（主题归一化或 References 根）',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_account_folder_uid (tenant_id, account_id, folder_id, uid),
    KEY idx_tenant_account_sent (tenant_id, account_id, sent_time),
    KEY idx_tenant_folder_seen (tenant_id, folder_id, seen, sent_time),
    KEY idx_tenant_message_id (tenant_id, message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='邮件索引表';

-- ---------------------------------------------------------------------
-- 4. 邮件附件（二进制落 whale-file，本表只存引用与元数据）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mail_attachment (
    id              BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    message_id      BIGINT        NOT NULL                COMMENT '所属邮件ID',
    file_id         BIGINT        DEFAULT NULL            COMMENT 'whale-file 文件ID（未上传完成时为空）',
    file_name       VARCHAR(255)  NOT NULL                COMMENT '文件名',
    content_type    VARCHAR(128)  DEFAULT NULL            COMMENT 'MIME 类型',
    size_bytes      BIGINT        NOT NULL DEFAULT 0      COMMENT '大小（字节）',
    inline_flag     TINYINT       NOT NULL DEFAULT 0      COMMENT '是否内联资源（cid 图片）：1 是 / 0 否',
    content_id      VARCHAR(190)  DEFAULT NULL            COMMENT 'Content-ID（内联资源定位）',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_message (tenant_id, message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='邮件附件表';

-- ---------------------------------------------------------------------
-- 5. 草稿（未发送，不占 IMAP 索引）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mail_draft (
    id              BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id       BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    account_id      BIGINT        NOT NULL                COMMENT '所属邮箱账号ID',
    draft_key       VARCHAR(64)   DEFAULT NULL            COMMENT '客户端草稿键（前端本地草稿合并用）',
    subject         VARCHAR(512)  DEFAULT NULL            COMMENT '主题',
    to_addrs        VARCHAR(1000) DEFAULT NULL            COMMENT '收件人',
    cc_addrs        VARCHAR(1000) DEFAULT NULL            COMMENT '抄送',
    bcc_addrs       VARCHAR(1000) DEFAULT NULL            COMMENT '密送',
    body_html       MEDIUMTEXT    DEFAULT NULL            COMMENT 'HTML 正文',
    body_text       MEDIUMTEXT    DEFAULT NULL            COMMENT '纯文本正文',
    attachments_json TEXT         DEFAULT NULL            COMMENT '附件草稿（JSON 数组：fileName/contentType/fileId）',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_tenant_account_update (tenant_id, account_id, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='邮件草稿表';

-- ---------------------------------------------------------------------
-- 6. 系统文件夹种子：账号创建时由服务端补建，此处不预置（依赖 account_id）
-- ---------------------------------------------------------------------
