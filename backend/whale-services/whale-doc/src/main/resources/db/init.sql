-- =====================================================================
-- BlueWhale 微服务平台 - 在线文档 / Wiki 服务（whale-doc）数据库初始化脚本
-- =====================================================================
-- 库名       : bluewhale_doc
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成）、create_time、update_time、
--              deleted TINYINT（逻辑删除：0 未删除 / 1 已删除）
-- 幂等约定   : CREATE TABLE IF NOT EXISTS，可重复执行
-- 租户隔离   : tenant_id 由 starter 的 TenantLineInnerInterceptor 自动追加/填充
-- 正文存储   : content_json 为 TipTap / ProseMirror JSON；content_text 由服务端
--              从 JSON 文本节点提取，仅用于搜索，不接受前端提交值作为权威数据
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 文档空间
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_space (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    space_key        VARCHAR(32)   NOT NULL                COMMENT '空间编码（租户内唯一，创建后不可改）',
    name             VARCHAR(100)  NOT NULL                COMMENT '空间名称',
    description      VARCHAR(500)  DEFAULT NULL            COMMENT '空间描述',
    icon             VARCHAR(64)   DEFAULT NULL            COMMENT '图标标识',
    visibility       VARCHAR(16)   NOT NULL DEFAULT 'MEMBER' COMMENT '可见性：PUBLIC / MEMBER',
    owner_id         BIGINT        NOT NULL                COMMENT '空间所有者ID（与 doc_space_member 的 OWNER 一致）',
    status           TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 正常 / 0 归档',
    sort_no          INT           NOT NULL DEFAULT 0      COMMENT '我的空间排序',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_space_key (tenant_id, space_key),
    KEY idx_doc_space_owner (tenant_id, owner_id, deleted),
    KEY idx_doc_space_visibility (tenant_id, visibility, status, deleted)
) COMMENT='文档空间表';

-- ---------------------------------------------------------------------
-- 2. 空间成员
--    移除成员为逻辑删除（deleted=1）；重新添加复用同一行（deleted=0 + 新角色），
--    避免唯一键冲突。OWNER 转让需在同一事务内更新 doc_space.owner_id 与两行成员角色。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_space_member (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    space_id         BIGINT        NOT NULL                COMMENT '空间ID（逻辑关联 doc_space.id）',
    user_id          BIGINT        NOT NULL                COMMENT '成员用户ID',
    member_role      VARCHAR(16)   NOT NULL                COMMENT '空间角色：OWNER / ADMIN / EDITOR / READER',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_space_member (tenant_id, space_id, user_id),
    KEY idx_doc_member_user (tenant_id, user_id, deleted)
) COMMENT='文档空间成员表';

-- ---------------------------------------------------------------------
-- 3. 文档页面
--    M2 删除为「子树移入回收站」：deleted=1 并记录 deleted_at/deleted_by/trash_root_id；
--    slug 改写为 {slug}-trash-{id}，保证唯一键不与新页面冲突；恢复时重算可用 slug
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_page (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    space_id            BIGINT        NOT NULL                COMMENT '空间ID（逻辑关联 doc_space.id）',
    parent_id           BIGINT        NOT NULL DEFAULT 0      COMMENT '父页面ID，0 表示根节点',
    title               VARCHAR(200)  NOT NULL                COMMENT '页面标题',
    slug                VARCHAR(128)  NOT NULL                COMMENT '页面路径（空间内唯一）',
    page_type           VARCHAR(8)    NOT NULL DEFAULT 'PAGE' COMMENT '节点类型：PAGE / FOLDER',
    content_json        LONGTEXT      DEFAULT NULL            COMMENT '正文 JSON（TipTap / ProseMirror，应用层 ≤2MB）',
    content_text        LONGTEXT      DEFAULT NULL            COMMENT '服务端提取的纯文本投影（搜索用，≤1MB）',
    status              VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT / PUBLISHED / ARCHIVED / PENDING_APPROVAL',
    revision            BIGINT        NOT NULL DEFAULT 0      COMMENT '乐观锁版本，每次保存 +1',
    published_revision  BIGINT        DEFAULT NULL            COMMENT '最近发布时的 revision',
    sort_no             BIGINT        NOT NULL DEFAULT 0      COMMENT '同级排序',
    icon                VARCHAR(64)   DEFAULT NULL            COMMENT '图标标识',
    author_id           BIGINT        DEFAULT NULL            COMMENT '创建人ID',
    last_editor_id      BIGINT        DEFAULT NULL            COMMENT '最近编辑人ID',
    published_at        DATETIME      DEFAULT NULL            COMMENT '最近发布时间',
    archived_at         DATETIME      DEFAULT NULL            COMMENT '归档时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除（回收站）',
    deleted_at          DATETIME      DEFAULT NULL            COMMENT '移入回收站时间（M2）',
    deleted_by          BIGINT        DEFAULT NULL            COMMENT '移入回收站操作人ID（M2）',
    trash_root_id       BIGINT        DEFAULT NULL            COMMENT '回收站根页面ID，根页面等于自身 id（M2）',
    approval_instance_id BIGINT       DEFAULT NULL            COMMENT 'M3 流程实例ID',
    approval_status     VARCHAR(16)   DEFAULT NULL            COMMENT 'M3 审批状态：PENDING / APPROVED / REJECTED',
    approval_submitted_at DATETIME    DEFAULT NULL            COMMENT 'M3 审批提交时间',
    archive_item_id     VARCHAR(64)   DEFAULT NULL            COMMENT 'M3 档案条目ID',
    archive_file_id     BIGINT        DEFAULT NULL            COMMENT 'M3 归档快照文件ID',
    archive_at          DATETIME      DEFAULT NULL            COMMENT 'M3 归档入档时间',
    is_restricted       TINYINT       NOT NULL DEFAULT 0      COMMENT 'M2 页面级权限：0 继承空间 / 1 仅 ACL 授权用户可见',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_page_slug (tenant_id, space_id, slug),
    KEY idx_doc_page_tree (tenant_id, space_id, parent_id, deleted, sort_no),
    KEY idx_doc_page_status (tenant_id, space_id, status, deleted),
    KEY idx_doc_page_update (tenant_id, update_time),
    KEY idx_doc_page_recycle (tenant_id, space_id, deleted, deleted_at),
    KEY idx_doc_page_approval (tenant_id, status, approval_instance_id),
    FULLTEXT KEY ft_doc_page_text (title, content_text) WITH PARSER ngram
) COMMENT='文档页面表';

-- ---------------------------------------------------------------------
-- 3.1 文档模板（M3）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_template (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    space_id         BIGINT        NOT NULL DEFAULT 0      COMMENT '归属空间ID，0 表示租户级模板',
    name             VARCHAR(100)  NOT NULL                COMMENT '模板名称',
    description      VARCHAR(500)  DEFAULT NULL            COMMENT '模板说明',
    page_type        VARCHAR(8)    NOT NULL DEFAULT 'PAGE' COMMENT '节点类型：PAGE / FOLDER',
    icon             VARCHAR(64)   DEFAULT NULL            COMMENT '图标标识',
    content_json     LONGTEXT      DEFAULT NULL            COMMENT '模板正文 JSON',
    content_text     LONGTEXT      DEFAULT NULL            COMMENT '服务端提取的纯文本投影',
    status           TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用',
    sort_no          INT           NOT NULL DEFAULT 0      COMMENT '排序',
    author_id        BIGINT        DEFAULT NULL            COMMENT '创建人ID',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_doc_template_space (tenant_id, space_id, status, deleted, sort_no),
    KEY idx_doc_template_name (tenant_id, name)
) COMMENT='文档模板表';

-- ---------------------------------------------------------------------
-- 3.2 页面登录内分享（M3）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_page_share (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    page_id          BIGINT        NOT NULL                COMMENT '页面ID（逻辑关联 doc_page.id）',
    share_token      VARCHAR(64)   NOT NULL                COMMENT '分享令牌（不可猜随机串）',
    share_role       VARCHAR(16)   NOT NULL DEFAULT 'READER' COMMENT '分享角色：READER',
    expires_at       DATETIME      DEFAULT NULL            COMMENT '过期时间，NULL 表示不限期',
    status           TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 有效 / 0 已撤销',
    created_by       BIGINT        NOT NULL                COMMENT '创建人ID',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_share_token (tenant_id, share_token),
    KEY idx_doc_share_page (tenant_id, page_id, status, deleted)
) COMMENT='文档页面分享表';

-- ---------------------------------------------------------------------
-- 4. 页面版本快照（append-only：不设 update_time / deleted）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_page_version (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    page_id          BIGINT        NOT NULL                COMMENT '页面ID（逻辑关联 doc_page.id）',
    revision_no      BIGINT        NOT NULL                COMMENT '快照对应的 revision',
    title            VARCHAR(200)  NOT NULL                COMMENT '快照标题',
    content_json     LONGTEXT      DEFAULT NULL            COMMENT '快照正文 JSON',
    content_text     LONGTEXT      DEFAULT NULL            COMMENT '快照纯文本投影',
    version_kind     VARCHAR(16)   NOT NULL                COMMENT '快照类型：MANUAL / PUBLISH / AUTO / RESTORE / IMPORT',
    change_summary   VARCHAR(200)  DEFAULT NULL            COMMENT '变更说明',
    editor_id        BIGINT        DEFAULT NULL            COMMENT '编辑人ID',
    content_hash     CHAR(64)      DEFAULT NULL            COMMENT 'SHA-256(content_json)，用于去重',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_version (tenant_id, page_id, revision_no),
    KEY idx_doc_version_time (tenant_id, page_id, create_time)
) COMMENT='文档页面版本表';

-- ---------------------------------------------------------------------
-- 5. 页面附件引用（文件实体在 whale-file，本表只登记引用关系）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_page_attachment (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    page_id          BIGINT        NOT NULL                COMMENT '页面ID（逻辑关联 doc_page.id）',
    file_id          BIGINT        NOT NULL                COMMENT 'whale-file 文件ID',
    original_name    VARCHAR(255)  DEFAULT NULL            COMMENT '原始文件名',
    content_type     VARCHAR(128)  DEFAULT NULL            COMMENT 'MIME 类型',
    file_size        BIGINT        DEFAULT NULL            COMMENT '文件字节数',
    usage_type       VARCHAR(16)   NOT NULL DEFAULT 'ATTACHMENT' COMMENT '用途：ATTACHMENT / INLINE',
    sort_no          INT           NOT NULL DEFAULT 0      COMMENT '排序',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_attachment (tenant_id, page_id, file_id),
    KEY idx_doc_attachment_file (tenant_id, file_id)
) COMMENT='文档页面附件引用表';

-- ---------------------------------------------------------------------
-- 6. 页面评论
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_comment (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    page_id          BIGINT        NOT NULL                COMMENT '页面ID（逻辑关联 doc_page.id）',
    parent_id        BIGINT        NOT NULL DEFAULT 0      COMMENT '父评论ID，0 表示主评论',
    anchor_block_id  VARCHAR(64)   DEFAULT NULL            COMMENT 'TipTap 块ID（行内锚点）',
    anchor_text      VARCHAR(500)  DEFAULT NULL            COMMENT '锚点摘要',
    content          VARCHAR(2000) NOT NULL                COMMENT '评论内容',
    author_id        BIGINT        NOT NULL                COMMENT '评论人ID',
    resolved         TINYINT       NOT NULL DEFAULT 0      COMMENT '解决状态：0 未解决 / 1 已解决',
    resolved_by      BIGINT        DEFAULT NULL            COMMENT '解决人ID',
    resolved_at      DATETIME      DEFAULT NULL            COMMENT '解决时间',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_doc_comment_page (tenant_id, page_id, resolved, create_time),
    KEY idx_doc_comment_parent (tenant_id, page_id, parent_id)
) COMMENT='文档页面评论表';

-- ---------------------------------------------------------------------
-- 7. 页面收藏（取消收藏置 deleted=1，重新收藏复用行）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_page_star (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    user_id          BIGINT        NOT NULL                COMMENT '收藏用户ID',
    page_id          BIGINT        NOT NULL                COMMENT '页面ID（逻辑关联 doc_page.id）',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_star (tenant_id, user_id, page_id),
    KEY idx_doc_star_user (tenant_id, user_id, create_time)
) COMMENT='文档页面收藏表';

-- ---------------------------------------------------------------------
-- 7.1 页面级权限（M2-4）
--     页面 is_restricted=1 时，仅空间 ADMIN/OWNER 与 ACL 授权用户可见；
--     ACL 角色：READ（只读）/ EDIT（可编辑）。空间管理员始终可读写并管理授权。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_page_acl (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    page_id          BIGINT        NOT NULL                COMMENT '页面ID（逻辑关联 doc_page.id）',
    user_id          BIGINT        NOT NULL                COMMENT '被授权用户ID',
    acl_role         VARCHAR(8)    NOT NULL DEFAULT 'READ' COMMENT '页面角色：READ / EDIT',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_page_acl (tenant_id, page_id, user_id),
    KEY idx_doc_page_acl_user (tenant_id, user_id, deleted)
) COMMENT='文档页面级权限表';

-- ---------------------------------------------------------------------
-- 7.2 空间标签与页面标签（M2-5）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_tag (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    space_id         BIGINT        NOT NULL                COMMENT '空间ID（逻辑关联 doc_space.id）',
    name             VARCHAR(64)   NOT NULL                COMMENT '标签名称（空间内唯一）',
    color            VARCHAR(16)   DEFAULT NULL            COMMENT '标签颜色（十六进制）',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_tag_name (tenant_id, space_id, name),
    KEY idx_doc_tag_space (tenant_id, space_id, deleted, name)
) COMMENT='文档标签表';

CREATE TABLE IF NOT EXISTS doc_page_tag (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    page_id          BIGINT        NOT NULL                COMMENT '页面ID（逻辑关联 doc_page.id）',
    tag_id           BIGINT        NOT NULL                COMMENT '标签ID（逻辑关联 doc_tag.id）',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_page_tag (tenant_id, page_id, tag_id),
    KEY idx_doc_page_tag_tag (tenant_id, tag_id, deleted)
) COMMENT='文档页面标签关联表';

-- ---------------------------------------------------------------------
-- 7.3 页面引用关系（M2-5 反向链接）
--     服务端从正文 TipTap JSON 的链接/attrs 中提取 pageId，保存页面时重建。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_page_link (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    source_page_id   BIGINT        NOT NULL                COMMENT '引用方页面ID',
    target_page_id   BIGINT        NOT NULL                COMMENT '被引用页面ID',
    link_text        VARCHAR(200)  DEFAULT NULL            COMMENT '链接文本/锚点摘要',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_doc_link_target (tenant_id, target_page_id, deleted),
    KEY idx_doc_link_source (tenant_id, source_page_id, deleted)
) COMMENT='文档页面引用表';

-- ---------------------------------------------------------------------
-- 7.4 页面动态（M2-5，append-only：不设 update_time / deleted）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_page_activity (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    page_id          BIGINT        NOT NULL                COMMENT '页面ID（逻辑关联 doc_page.id）',
    action           VARCHAR(16)   NOT NULL                COMMENT '动作：CREATE/SAVE/PUBLISH/ARCHIVE/RESTORE/MOVE/TRASH/COMMENT/TAG/ACL/IMPORT',
    actor_id         BIGINT        DEFAULT NULL            COMMENT '操作人ID',
    detail           VARCHAR(500)  DEFAULT NULL            COMMENT '动作摘要（不含正文）',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_doc_activity_page (tenant_id, page_id, create_time),
    KEY idx_doc_activity_time (tenant_id, create_time)
) COMMENT='文档页面动态表';

-- ---------------------------------------------------------------------
-- 7.5 最近访问（M2-5，服务端按用户记录，重新访问复用行）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_page_recent (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    user_id          BIGINT        NOT NULL                COMMENT '访问用户ID',
    page_id          BIGINT        NOT NULL                COMMENT '页面ID（逻辑关联 doc_page.id）',
    visited_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近访问时间',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_recent (tenant_id, user_id, page_id),
    KEY idx_doc_recent_user (tenant_id, user_id, visited_at)
) COMMENT='文档最近访问表';

-- ---------------------------------------------------------------------
-- 7.6 评论已读状态（M2-7，按用户/页面记录最后已读时间）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS doc_comment_read (
    id                    BIGINT      NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id             BIGINT      NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    page_id               BIGINT      NOT NULL                COMMENT '页面ID（逻辑关联 doc_page.id）',
    user_id               BIGINT      NOT NULL                COMMENT '用户ID',
    last_read_comment_id  BIGINT      NOT NULL DEFAULT 0      COMMENT '最后已读评论ID',
    last_read_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后已读时间',
    create_time           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted               TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_comment_read (tenant_id, page_id, user_id),
    KEY idx_doc_comment_read_user (tenant_id, user_id, last_read_at)
) COMMENT='文档评论已读状态表';

-- ---------------------------------------------------------------------
-- 8. 操作审计日志表（whale-spring-boot-starter @AuditLog 切面采集，
--    结构与 bluewhale_auth.audit_log 一致）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名（冗余存储，用户删除后仍可追溯）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-doc',
    operation      VARCHAR(128)  DEFAULT NULL           COMMENT '操作描述',
    operate_type   VARCHAR(32)   DEFAULT NULL           COMMENT '操作类型：CREATE/UPDATE/DELETE/QUERY/OTHER 等',
    method         VARCHAR(255)  DEFAULT NULL           COMMENT '请求方法（全限定类名.方法名）',
    request_uri    VARCHAR(255)  DEFAULT NULL           COMMENT '请求URI',
    http_method    VARCHAR(16)   DEFAULT NULL           COMMENT 'HTTP 方法：GET/POST/PUT/DELETE',
    target_type    VARCHAR(64)   DEFAULT NULL           COMMENT '操作对象类型，如 doc_space / doc_page',
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
-- 9. 后端接口日志表（whale-spring-boot-starter ApiLogFilter 采集，
--    结构与 bluewhale_auth.api_log 一致）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS api_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-doc',
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
