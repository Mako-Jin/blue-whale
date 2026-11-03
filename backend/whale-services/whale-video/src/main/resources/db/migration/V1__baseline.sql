-- =====================================================================
-- BlueWhale 视频平台服务（whale-video）Flyway 基线脚本
-- =====================================================================
-- 库名       : bluewhale_video（空库启动由 Flyway 自动建表）
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成）、tenant_id、create_time、update_time、
--              deleted TINYINT（逻辑删除：0 未删除 / 1 已删除）
-- 租户隔离   : tenant_id 由 starter 的 TenantLineInnerInterceptor 自动追加/填充；
--              跨租户一律按不存在处理，不泄露存在性。
-- 业务口径   : vd_content 为短视频 / 视频 / 电影共享的内容注册与生命周期表，
--              子类型表以 content_id 为主键；视频字节统一归 whale-file，
--              本库只保存 file_id 与元数据，公开 VO 不暴露 file_id。
-- 脚本约束   : 禁止 DROP / TRUNCATE；种子使用 INSERT ... WHERE NOT EXISTS，
--              可重复执行（REQ-SEC-022）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 共享内容注册与生命周期
--    publish_status: DRAFT / PENDING_REVIEW / PUBLISHED / OFFLINE
--    copyright_status: AUTHORIZED / TAKEDOWN（TAKEDOWN 优先级最高，禁止播放票据）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vd_content (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    content_type        VARCHAR(16)   NOT NULL                COMMENT '内容类型：SHORT_VIDEO / VIDEO / MOVIE',
    title               VARCHAR(200)  NOT NULL                COMMENT '标题',
    subtitle            VARCHAR(200)  DEFAULT NULL            COMMENT '副标题',
    description         VARCHAR(2000) DEFAULT NULL            COMMENT '简介',
    cover_file_id       BIGINT        DEFAULT NULL            COMMENT '封面文件ID（whale-file，公开 VO 不暴露）',
    video_file_id       BIGINT        NOT NULL                COMMENT '视频文件ID（whale-file，公开 VO 不暴露）',
    duration_ms         BIGINT        NOT NULL DEFAULT 0      COMMENT '时长（毫秒）',
    width               INT           NOT NULL DEFAULT 0      COMMENT '视频宽度（像素）',
    height              INT           NOT NULL DEFAULT 0      COMMENT '视频高度（像素）',
    format              VARCHAR(32)   DEFAULT NULL            COMMENT '视频格式：MP4 / WEBM',
    size_bytes          BIGINT        NOT NULL DEFAULT 0      COMMENT '文件大小（字节）',
    uploader_id         BIGINT        NOT NULL DEFAULT 0      COMMENT '上传者用户ID（电影为 0，由运营入库）',
    creator_id          BIGINT        NOT NULL DEFAULT 0      COMMENT '创作者ID（vd_creator.id，0 表示未登记）',
    category_id         BIGINT        NOT NULL DEFAULT 0      COMMENT '分类ID（vd_category.id，0 表示未分类）',
    series_id           BIGINT        NOT NULL DEFAULT 0      COMMENT '系列ID（vd_series.id，M3 启用，0 表示不属系列）',
    episode_no          INT           NOT NULL DEFAULT 0      COMMENT '集数（系列内从 1 开始，0 表示非系列内容）',
    visibility          VARCHAR(16)   NOT NULL DEFAULT 'PUBLIC' COMMENT '可见性：PUBLIC 租户内可见 / PRIVATE 仅上传者',
    publish_status      VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '发布状态：DRAFT / PENDING_REVIEW / PUBLISHED / OFFLINE',
    copyright_status    VARCHAR(16)   NOT NULL DEFAULT 'AUTHORIZED' COMMENT '版权状态：AUTHORIZED 有效 / TAKEDOWN 版权下架',
    copyright_source    VARCHAR(16)   DEFAULT NULL            COMMENT '内容来源：ORIGINAL 原创 / AUTHORIZED 已授权 / ROYALTY_FREE 无版权争议素材',
    copyright_expire_at DATETIME      DEFAULT NULL            COMMENT '版权 / 授权到期时间（到期自动下线）',
    takedown_reason     VARCHAR(500)  DEFAULT NULL            COMMENT '版权下架原因（留痕）',
    takedown_at         DATETIME      DEFAULT NULL            COMMENT '版权下架时间',
    play_count          BIGINT        NOT NULL DEFAULT 0      COMMENT '播放次数（Redis 聚合后回填，最终一致）',
    like_count          BIGINT        NOT NULL DEFAULT 0      COMMENT '点赞数',
    favorite_count      BIGINT        NOT NULL DEFAULT 0      COMMENT '收藏数',
    comment_count       BIGINT        NOT NULL DEFAULT 0      COMMENT '评论数（仅统计可见评论）',
    hot_score           DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '热度分（定时重算）',
    sort_weight         INT           NOT NULL DEFAULT 0      COMMENT '运营排序权重',
    published_at        DATETIME      DEFAULT NULL            COMMENT '最近上架时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_vd_content_feed (tenant_id, content_type, publish_status, visibility, published_at),
    KEY idx_vd_content_hot (tenant_id, content_type, publish_status, hot_score),
    KEY idx_vd_content_creator (tenant_id, creator_id, published_at),
    KEY idx_vd_content_category (tenant_id, category_id, publish_status),
    KEY idx_vd_content_series (tenant_id, series_id, episode_no),
    KEY idx_vd_content_uploader (tenant_id, uploader_id, deleted)
) COMMENT='视频共享内容表';

-- ---------------------------------------------------------------------
-- 2. 短视频子类型（content_id 主键，1:1 扩展 vd_content）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vd_short_video (
    content_id          BIGINT        NOT NULL                COMMENT '内容ID（vd_content.id）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    caption             VARCHAR(500)  DEFAULT NULL            COMMENT '短视频文案',
    aspect_ratio        VARCHAR(16)   NOT NULL DEFAULT '9:16' COMMENT '画幅：9:16 / 16:9 / 1:1',
    cover_frame_ms      BIGINT        NOT NULL DEFAULT 0      COMMENT '封面截帧时间点（毫秒）',
    feed_weight         INT           NOT NULL DEFAULT 0      COMMENT 'feed 排序权重',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (content_id),
    KEY idx_vd_short_feed (tenant_id, feed_weight)
) COMMENT='短视频子类型表';

-- ---------------------------------------------------------------------
-- 3. 视频子类型（content_id 主键，1:1 扩展 vd_content）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vd_video (
    content_id          BIGINT        NOT NULL                COMMENT '内容ID（vd_content.id）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    video_kind          VARCHAR(16)   NOT NULL DEFAULT 'UGC'  COMMENT '视频类型：UGC 用户内容 / PGC 专业内容',
    allow_comment       TINYINT       NOT NULL DEFAULT 1      COMMENT '是否允许评论：1 允许 / 0 关闭',
    allow_download      TINYINT       NOT NULL DEFAULT 0      COMMENT '是否允许下载：1 允许 / 0 禁止',
    source_note         VARCHAR(500)  DEFAULT NULL            COMMENT '来源说明（版权合规留痕）',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (content_id)
) COMMENT='视频子类型表';

-- ---------------------------------------------------------------------
-- 4. 电影子类型（content_id 主键，仅运营入库）
--    授权到期后由定时任务自动 OFFLINE，并删除可播状态缓存。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vd_movie (
    content_id          BIGINT        NOT NULL                COMMENT '内容ID（vd_content.id）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    original_title      VARCHAR(200)  DEFAULT NULL            COMMENT '原始片名',
    director            VARCHAR(200)  DEFAULT NULL            COMMENT '导演（多人逗号分隔）',
    actors              VARCHAR(500)  DEFAULT NULL            COMMENT '主演（多人逗号分隔）',
    region              VARCHAR(64)   DEFAULT NULL            COMMENT '地区',
    language            VARCHAR(64)   DEFAULT NULL            COMMENT '语言',
    release_date        DATE          DEFAULT NULL            COMMENT '上映日期',
    rating              DECIMAL(3,1)  DEFAULT NULL            COMMENT '评分（0.0–10.0）',
    license_source      VARCHAR(16)   NOT NULL DEFAULT 'OWNED' COMMENT '授权来源：OWNED 自有 / LICENSED 已授权 / PUBLIC_DOMAIN 公有领域',
    license_no          VARCHAR(128)  DEFAULT NULL            COMMENT '授权编号（留痕）',
    license_expire_at   DATETIME      DEFAULT NULL            COMMENT '授权到期时间（到期自动下线）',
    poster_file_id      BIGINT        DEFAULT NULL            COMMENT '海报文件ID（whale-file，公开 VO 不暴露）',
    backdrop_file_id    BIGINT        DEFAULT NULL            COMMENT '背景图文件ID（whale-file，公开 VO 不暴露）',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (content_id),
    KEY idx_vd_movie_license (tenant_id, license_expire_at)
) COMMENT='电影子类型表';

-- ---------------------------------------------------------------------
-- 5. 分类树
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vd_category (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    parent_id           BIGINT        NOT NULL DEFAULT 0      COMMENT '父分类ID，0 表示顶级',
    name                VARCHAR(64)   NOT NULL                COMMENT '分类名称',
    code                VARCHAR(64)   NOT NULL                COMMENT '分类编码（租户内唯一）',
    content_type_scope  VARCHAR(32)   NOT NULL DEFAULT 'ALL'  COMMENT '适用范围：ALL / SHORT_VIDEO / VIDEO / MOVIE',
    sort_no             INT           NOT NULL DEFAULT 0      COMMENT '排序',
    status              TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_vd_category_name (tenant_id, parent_id, name, deleted),
    UNIQUE KEY uk_vd_category_code (tenant_id, code, deleted)
) COMMENT='视频分类表';

-- ---------------------------------------------------------------------
-- 6. 标签
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vd_tag (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    name                VARCHAR(64)   NOT NULL                COMMENT '标签名称',
    tag_type            VARCHAR(16)   NOT NULL DEFAULT 'TOPIC' COMMENT '标签类型：TOPIC 主题 / GENRE 类型 / LANGUAGE 语言',
    sort_no             INT           NOT NULL DEFAULT 0      COMMENT '排序',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_vd_tag_name (tenant_id, name, tag_type, deleted)
) COMMENT='视频标签表';

-- ---------------------------------------------------------------------
-- 7. 内容标签关系
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vd_content_tag (
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    content_id          BIGINT        NOT NULL                COMMENT '内容ID',
    tag_id              BIGINT        NOT NULL                COMMENT '标签ID',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (content_id, tag_id),
    KEY idx_vd_content_tag_tag (tenant_id, tag_id)
) COMMENT='视频内容标签关系表';

-- ---------------------------------------------------------------------
-- 8. 创作者资料
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vd_creator (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    user_id             BIGINT        NOT NULL                COMMENT '关联用户ID',
    display_name        VARCHAR(64)   NOT NULL                COMMENT '创作者昵称',
    avatar_file_id      BIGINT        DEFAULT NULL            COMMENT '头像文件ID（whale-file）',
    bio                 VARCHAR(500)  DEFAULT NULL            COMMENT '简介',
    status              TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用（停用后不得发布新内容）',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_vd_creator_user (tenant_id, user_id, deleted)
) COMMENT='视频创作者表';

-- ---------------------------------------------------------------------
-- 9. 上传声明签署记录
--    同一用户同一版本只记录一次；未签署当前版本不能创建短视频 / 视频。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vd_upload_declaration (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    user_id             BIGINT        NOT NULL                COMMENT '签署用户ID',
    declaration_version VARCHAR(32)   NOT NULL                COMMENT '声明版本号，如 V1',
    accepted_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '签署时间',
    client_ip           VARCHAR(64)   DEFAULT NULL            COMMENT '客户端IP（签署留痕）',
    user_agent          VARCHAR(512)  DEFAULT NULL            COMMENT '客户端标识（签署留痕）',
    content_source      VARCHAR(16)   NOT NULL DEFAULT 'ORIGINAL' COMMENT '内容来源：ORIGINAL / AUTHORIZED / ROYALTY_FREE',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_vd_declaration_user_version (tenant_id, user_id, declaration_version)
) COMMENT='视频上传声明签署表';

-- ---------------------------------------------------------------------
-- 10. 内容点赞
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vd_like (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    user_id             BIGINT        NOT NULL                COMMENT '用户ID',
    content_id          BIGINT        NOT NULL                COMMENT '内容ID',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_vd_like_user_content (tenant_id, user_id, content_id),
    KEY idx_vd_like_content (tenant_id, content_id)
) COMMENT='视频内容点赞表';

-- ---------------------------------------------------------------------
-- 11. 内容收藏
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vd_favorite (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    user_id             BIGINT        NOT NULL                COMMENT '用户ID',
    content_id          BIGINT        NOT NULL                COMMENT '内容ID',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_vd_favorite_user_content (tenant_id, user_id, content_id),
    KEY idx_vd_favorite_content (tenant_id, content_id)
) COMMENT='视频内容收藏表';

-- ---------------------------------------------------------------------
-- 12. 观看进度 / 最近观看
--     按用户保留 180 天，超期由定时任务清理。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vd_watch_history (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    user_id             BIGINT        NOT NULL                COMMENT '用户ID',
    content_id          BIGINT        NOT NULL                COMMENT '内容ID',
    progress_ms         BIGINT        NOT NULL DEFAULT 0      COMMENT '观看进度（毫秒）',
    duration_ms         BIGINT        NOT NULL DEFAULT 0      COMMENT '内容时长（毫秒，用于续播比例）',
    source              VARCHAR(32)   DEFAULT NULL            COMMENT '来源：HOME / FEED / SEARCH / SERIES / COLLECTION / MY',
    device              VARCHAR(32)   DEFAULT NULL            COMMENT '设备标识：WEB / MOBILE / OTHER',
    watched_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近观看时间',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_vd_history_user_content (tenant_id, user_id, content_id),
    KEY idx_vd_history_user (tenant_id, user_id, watched_at),
    KEY idx_vd_history_content (tenant_id, content_id)
) COMMENT='视频观看历史表';

-- ---------------------------------------------------------------------
-- 13. 评论
--     默认先发后审：新评论 VISIBLE 并进入审核队列；隐藏后立即不可见。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vd_comment (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    content_id          BIGINT        NOT NULL                COMMENT '内容ID',
    user_id             BIGINT        NOT NULL                COMMENT '评论用户ID',
    parent_id           BIGINT        NOT NULL DEFAULT 0      COMMENT '父评论ID，0 表示顶级评论',
    content             VARCHAR(500)  NOT NULL                COMMENT '评论正文（纯文本，≤500 字）',
    status              VARCHAR(16)   NOT NULL DEFAULT 'VISIBLE' COMMENT '状态：PENDING / VISIBLE / HIDDEN',
    like_count          INT           NOT NULL DEFAULT 0      COMMENT '点赞数',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_vd_comment_content (tenant_id, content_id, status, create_time),
    KEY idx_vd_comment_user (tenant_id, user_id, create_time)
) COMMENT='视频评论表';

-- ---------------------------------------------------------------------
-- 14. 评论点赞
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vd_comment_like (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    comment_id          BIGINT        NOT NULL                COMMENT '评论ID',
    user_id             BIGINT        NOT NULL                COMMENT '用户ID',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_vd_comment_like_user (tenant_id, comment_id, user_id)
) COMMENT='视频评论点赞表';

-- ---------------------------------------------------------------------
-- 15. 操作审计日志表（whale-spring-boot-starter @AuditLog 切面采集）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名（冗余存储，用户删除后仍可追溯）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-video',
    operation      VARCHAR(128)  DEFAULT NULL           COMMENT '操作描述',
    operate_type   VARCHAR(32)   DEFAULT NULL           COMMENT '操作类型：CREATE/UPDATE/DELETE/QUERY/OTHER 等',
    method         VARCHAR(255)  DEFAULT NULL           COMMENT '请求方法（全限定类名.方法名）',
    request_uri    VARCHAR(255)  DEFAULT NULL           COMMENT '请求URI',
    http_method    VARCHAR(16)   DEFAULT NULL           COMMENT 'HTTP 方法：GET/POST/PUT/DELETE',
    target_type    VARCHAR(64)   DEFAULT NULL           COMMENT '操作对象类型，如 vd_content / vd_movie',
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
-- 16. 后端接口日志表（whale-spring-boot-starter ApiLogFilter 采集）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS api_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-video',
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
-- 17. 基线种子：默认分类与标签（仅平台租户 1，幂等）
-- ---------------------------------------------------------------------
INSERT INTO vd_category (id, tenant_id, parent_id, name, code, content_type_scope, sort_no, status, deleted)
SELECT 9001, 1, 0, '短视频', 'SHORT_VIDEO', 'SHORT_VIDEO', 1, 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM vd_category WHERE id = 9001);
INSERT INTO vd_category (id, tenant_id, parent_id, name, code, content_type_scope, sort_no, status, deleted)
SELECT 9002, 1, 0, '视频', 'VIDEO', 'VIDEO', 2, 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM vd_category WHERE id = 9002);
INSERT INTO vd_category (id, tenant_id, parent_id, name, code, content_type_scope, sort_no, status, deleted)
SELECT 9003, 1, 0, '电影', 'MOVIE', 'MOVIE', 3, 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM vd_category WHERE id = 9003);

INSERT INTO vd_tag (id, tenant_id, name, tag_type, sort_no, deleted)
SELECT 9011, 1, '热门', 'TOPIC', 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM vd_tag WHERE id = 9011);
INSERT INTO vd_tag (id, tenant_id, name, tag_type, sort_no, deleted)
SELECT 9012, 1, '原创', 'TOPIC', 2, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM vd_tag WHERE id = 9012);
INSERT INTO vd_tag (id, tenant_id, name, tag_type, sort_no, deleted)
SELECT 9013, 1, '生活', 'TOPIC', 3, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM vd_tag WHERE id = 9013);
INSERT INTO vd_tag (id, tenant_id, name, tag_type, sort_no, deleted)
SELECT 9014, 1, '影视', 'GENRE', 4, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM vd_tag WHERE id = 9014);
INSERT INTO vd_tag (id, tenant_id, name, tag_type, sort_no, deleted)
SELECT 9015, 1, '音乐', 'GENRE', 5, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM vd_tag WHERE id = 9015);
INSERT INTO vd_tag (id, tenant_id, name, tag_type, sort_no, deleted)
SELECT 9016, 1, '搞笑', 'TOPIC', 6, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM vd_tag WHERE id = 9016);

-- =====================================================================
-- 完成。M0 基线为 14 张 vd_* 业务表 + audit_log / api_log。
-- M3 追加 vd_series，M4 追加 vd_collection / vd_collection_item，
-- M5 可选追加 vd_play_stat_daily / vd_report（各自独立增量脚本）。
-- =====================================================================
