-- =====================================================================
-- BlueWhale 微服务平台 - 音乐平台服务（whale-music）数据库初始化脚本
-- =====================================================================
-- 库名       : bluewhale_music（首次执行前先建库）
--               CREATE DATABASE IF NOT EXISTS bluewhale_music
--                 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成）、tenant_id、create_time、update_time、
--              deleted TINYINT（逻辑删除：0 未删除 / 1 已删除）
-- 幂等约定   : CREATE TABLE IF NOT EXISTS，可重复执行
-- 租户隔离   : tenant_id 由 starter 的 TenantLineInnerInterceptor 自动追加/填充
-- 业务口径   : 所有登录用户可上传歌曲，上传前必须签署《版权无争议协议》；
--              歌曲可见性 PUBLIC（租户内可见）/ PRIVATE（仅上传者自听）；
--              公开作品不设上传前置审核，运营事后复核并可下架。
-- 音频字节   : 音频与封面统一存 whale-file，本库只保存 file_id 与元数据。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 歌手
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mu_artist (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    name             VARCHAR(100)  NOT NULL                COMMENT '艺名（租户内唯一）',
    avatar_file_id   BIGINT        DEFAULT NULL            COMMENT '头像文件ID（whale-file）',
    region           VARCHAR(64)   DEFAULT NULL            COMMENT '地区',
    bio              VARCHAR(1000) DEFAULT NULL            COMMENT '简介',
    status           TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用（停用后不得新增可发布歌曲）',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mu_artist_name (tenant_id, name, deleted),
    KEY idx_mu_artist_status (tenant_id, status, deleted)
) COMMENT='音乐歌手表';

-- ---------------------------------------------------------------------
-- 2. 专辑
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mu_album (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    artist_id        BIGINT        NOT NULL                COMMENT '歌手ID（逻辑关联 mu_artist.id）',
    title            VARCHAR(200)  NOT NULL                COMMENT '专辑标题',
    cover_file_id    BIGINT        NOT NULL                COMMENT '封面文件ID（whale-file，必填）',
    release_date     DATE          DEFAULT NULL            COMMENT '发行日期',
    album_type       VARCHAR(16)   NOT NULL DEFAULT 'ALBUM' COMMENT '专辑类型：SINGLE / EP / ALBUM / COMPILATION',
    intro            VARCHAR(1000) DEFAULT NULL            COMMENT '专辑简介',
    publish_status   VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '发布状态：DRAFT / PUBLISHED / OFFLINE',
    sort_no          INT           NOT NULL DEFAULT 0      COMMENT '排序',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_mu_album_status (tenant_id, publish_status, deleted),
    KEY idx_mu_album_artist (tenant_id, artist_id, deleted)
) COMMENT='音乐专辑表';

-- ---------------------------------------------------------------------
-- 3. 歌曲
--    隐私口径：PRIVATE 仅 uploader_id 本人可见可播；PUBLIC 租户内可见。
--    TAKEDOWN 版权下架优先级最高：强制 publish_status=OFFLINE 并拒绝播放票据。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mu_song (
    id                BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id         BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    uploader_id       BIGINT        NOT NULL                COMMENT '上传者用户ID（自听作品唯一可见人）',
    visibility        VARCHAR(16)   NOT NULL DEFAULT 'PUBLIC' COMMENT '可见性：PUBLIC 租户内可见 / PRIVATE 仅上传者自听',
    album_id          BIGINT        NOT NULL DEFAULT 0      COMMENT '专辑ID，0 表示单曲',
    artist_id         BIGINT        NOT NULL                COMMENT '歌手ID（逻辑关联 mu_artist.id）',
    title             VARCHAR(120)  NOT NULL                COMMENT '歌曲标题',
    subtitle          VARCHAR(200)  DEFAULT NULL            COMMENT '副标题',
    audio_file_id     BIGINT        NOT NULL                COMMENT '音频文件ID（whale-file）',
    cover_file_id     BIGINT        DEFAULT NULL            COMMENT '封面文件ID（whale-file）',
    duration_ms       BIGINT        NOT NULL DEFAULT 0      COMMENT '时长（毫秒），发布时必须大于 0',
    lyric_file_id     BIGINT        DEFAULT NULL            COMMENT '歌词文件ID（whale-file，可选）',
    lyric_text        MEDIUMTEXT    NULL                    COMMENT '歌词纯文本（LRC 或纯文本，可选）',
    language          VARCHAR(16)   DEFAULT NULL            COMMENT '语言：ZH / EN / JA / OTHER',
    publish_status    VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '发布状态：DRAFT / PUBLISHED / OFFLINE',
    copyright_status  VARCHAR(16)   NOT NULL DEFAULT 'DECLARED' COMMENT '版权状态：DECLARED 已声明 / VALID 复核通过 / TAKEDOWN 版权下架',
    copyright_source  VARCHAR(16)   DEFAULT NULL            COMMENT '作品来源：ORIGINAL 原创 / AUTHORIZED 已授权 / ROYALTY_FREE 无版权争议素材（M5-3 上传必填）',
    play_count        BIGINT        NOT NULL DEFAULT 0      COMMENT '播放次数（Redis 聚合后回刷，最终一致）',
    like_count        BIGINT        NOT NULL DEFAULT 0      COMMENT '收藏数',
    comment_count     BIGINT        NOT NULL DEFAULT 0      COMMENT '评论数（仅统计可见评论）',
    sort_weight       INT           NOT NULL DEFAULT 0      COMMENT '排序权重（运营可调）',
    published_at      DATETIME      DEFAULT NULL            COMMENT '最近上架时间',
    takedown_reason   VARCHAR(500)  DEFAULT NULL            COMMENT '版权下架原因（留痕）',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_mu_song_visible (tenant_id, publish_status, visibility, deleted),
    KEY idx_mu_song_uploader (tenant_id, uploader_id, deleted),
    KEY idx_mu_song_album (tenant_id, album_id, deleted),
    KEY idx_mu_song_artist (tenant_id, artist_id, deleted),
    KEY idx_mu_song_publish (tenant_id, publish_status, published_at)
) COMMENT='音乐歌曲表';

-- ---------------------------------------------------------------------
-- 4. 版权无争议协议签署记录
--    同一用户同一版本只记录一次；未签署当前版本不能上传歌曲。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mu_copyright_agreement (
    id                  BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id           BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    user_id             BIGINT        NOT NULL                COMMENT '签署用户ID',
    agreement_version   VARCHAR(32)   NOT NULL                COMMENT '协议版本号，如 V1',
    accepted_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '签署时间',
    client_ip           VARCHAR(64)   DEFAULT NULL            COMMENT '客户端IP（签署留痕）',
    user_agent          VARCHAR(512)  DEFAULT NULL            COMMENT '客户端标识（签署留痕）',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mu_agreement_user_version (tenant_id, user_id, agreement_version)
) COMMENT='音乐版权无争议协议签署表';

-- ---------------------------------------------------------------------
-- 5. 标签
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mu_tag (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    name             VARCHAR(64)   NOT NULL                COMMENT '标签名称',
    tag_type         VARCHAR(16)   NOT NULL DEFAULT 'GENRE' COMMENT '标签类型：GENRE 曲风 / LANGUAGE 语言 / MOOD 情绪',
    sort_no          INT           NOT NULL DEFAULT 0      COMMENT '排序',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mu_tag_name (tenant_id, name, tag_type, deleted)
) COMMENT='音乐标签表';

-- ---------------------------------------------------------------------
-- 6. 歌曲标签关系
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mu_song_tag (
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    song_id          BIGINT        NOT NULL                COMMENT '歌曲ID',
    tag_id           BIGINT        NOT NULL                COMMENT '标签ID',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (song_id, tag_id),
    KEY idx_mu_song_tag_tag (tenant_id, tag_id)
) COMMENT='音乐歌曲标签关系表';

-- ---------------------------------------------------------------------
-- 7. 歌单
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mu_playlist (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    owner_id         BIGINT        NOT NULL DEFAULT 0      COMMENT '所有者用户ID，官方歌单为 0',
    title            VARCHAR(120)  NOT NULL                COMMENT '歌单标题',
    cover_file_id    BIGINT        DEFAULT NULL            COMMENT '封面文件ID（whale-file）',
    description      VARCHAR(1000) DEFAULT NULL            COMMENT '歌单简介',
    playlist_type    VARCHAR(16)   NOT NULL DEFAULT 'USER' COMMENT '歌单类型：USER 用户歌单 / OFFICIAL 官方歌单',
    visibility       VARCHAR(16)   NOT NULL DEFAULT 'PUBLIC' COMMENT '可见性：PUBLIC 租户内可见 / PRIVATE 仅所有者',
    song_count       INT           NOT NULL DEFAULT 0      COMMENT '歌曲数量（上限 500）',
    play_count       BIGINT        NOT NULL DEFAULT 0      COMMENT '播放次数',
    status           TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 正常 / 0 停用',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_mu_playlist_visibility (tenant_id, visibility, status, deleted),
    KEY idx_mu_playlist_owner (tenant_id, owner_id, deleted),
    KEY idx_mu_playlist_type (tenant_id, playlist_type, deleted)
) COMMENT='音乐歌单表';

-- ---------------------------------------------------------------------
-- 8. 歌单歌曲
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mu_playlist_song (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    playlist_id      BIGINT        NOT NULL                COMMENT '歌单ID',
    song_id          BIGINT        NOT NULL                COMMENT '歌曲ID',
    sort_no          INT           NOT NULL DEFAULT 0      COMMENT '歌单内排序',
    added_by         BIGINT        NOT NULL                COMMENT '加入歌单的用户ID',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mu_playlist_song (playlist_id, song_id),
    KEY idx_mu_playlist_song_sort (tenant_id, playlist_id, sort_no)
) COMMENT='音乐歌单歌曲表';

-- ---------------------------------------------------------------------
-- 9. 歌曲收藏
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mu_song_like (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    user_id          BIGINT        NOT NULL                COMMENT '用户ID',
    song_id          BIGINT        NOT NULL                COMMENT '歌曲ID',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mu_song_like (tenant_id, user_id, song_id),
    KEY idx_mu_song_like_song (tenant_id, song_id)
) COMMENT='音乐歌曲收藏表';

-- ---------------------------------------------------------------------
-- 10. 播放历史
--     仅记录可播放歌曲；按用户保留 90 天，超期由定时任务清理。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mu_play_history (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    user_id          BIGINT        NOT NULL                COMMENT '用户ID',
    song_id          BIGINT        NOT NULL                COMMENT '歌曲ID',
    progress_ms      BIGINT        NOT NULL DEFAULT 0      COMMENT '播放进度（毫秒）',
    source           VARCHAR(32)   DEFAULT NULL            COMMENT '播放来源：HOME / SEARCH / PLAYLIST / ALBUM / ARTIST / MY',
    device           VARCHAR(32)   DEFAULT NULL            COMMENT '设备标识：WEB / MOBILE / OTHER',
    played_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近播放时间',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mu_history_user_song (tenant_id, user_id, song_id),
    KEY idx_mu_history_user (tenant_id, user_id, played_at),
    KEY idx_mu_history_song (tenant_id, song_id),
    KEY idx_mu_history_played (tenant_id, played_at, song_id)
) COMMENT='音乐播放历史表';

-- ---------------------------------------------------------------------
-- 11. 歌曲评论
--     默认先发后审：新评论 VISIBLE 并进入审核队列；隐藏后立即不可见。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mu_song_comment (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    song_id          BIGINT        NOT NULL                COMMENT '歌曲ID',
    user_id          BIGINT        NOT NULL                COMMENT '评论用户ID',
    parent_id        BIGINT        NOT NULL DEFAULT 0      COMMENT '父评论ID，0 表示顶级评论',
    content          VARCHAR(500)  NOT NULL                COMMENT '评论正文（纯文本，≤500 字）',
    status           VARCHAR(16)   NOT NULL DEFAULT 'VISIBLE' COMMENT '状态：PENDING / VISIBLE / HIDDEN',
    like_count       INT           NOT NULL DEFAULT 0      COMMENT '点赞数',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_mu_comment_song (tenant_id, song_id, status, create_time),
    KEY idx_mu_comment_user (tenant_id, user_id, create_time),
    KEY idx_mu_comment_audit (tenant_id, status, create_time)
) COMMENT='音乐歌曲评论表';

-- ---------------------------------------------------------------------
-- 12. 版权下架申诉
--     仅上传者本人可对自己处于版权下架的作品提交；同一作品同时只允许一条
--     待复核申诉。复核通过仅恢复版权为有效并保持下架，需运营重新上架。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mu_song_appeal (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL DEFAULT 1      COMMENT '租户ID（行级隔离）',
    song_id          BIGINT        NOT NULL                COMMENT '歌曲ID',
    applicant_id     BIGINT        NOT NULL                COMMENT '申诉人用户ID（作品上传者）',
    reason           VARCHAR(500)  NOT NULL                COMMENT '申诉理由（纯文本，≤500 字）',
    status           VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING / APPROVED / REJECTED',
    review_opinion   VARCHAR(500)  DEFAULT NULL            COMMENT '审核意见',
    reviewer_id      BIGINT        DEFAULT NULL            COMMENT '复核人用户ID',
    reviewed_at      DATETIME      DEFAULT NULL            COMMENT '复核时间',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_mu_appeal_song (tenant_id, song_id, status),
    KEY idx_mu_appeal_audit (tenant_id, status, create_time),
    KEY idx_mu_appeal_applicant (tenant_id, applicant_id, create_time)
) COMMENT='音乐版权下架申诉表';

-- ---------------------------------------------------------------------
-- 13. 操作审计日志表（whale-spring-boot-starter @AuditLog 切面采集，
--     结构与 bluewhale_auth.audit_log 一致）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名（冗余存储，用户删除后仍可追溯）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-music',
    operation      VARCHAR(128)  DEFAULT NULL           COMMENT '操作描述',
    operate_type   VARCHAR(32)   DEFAULT NULL           COMMENT '操作类型：CREATE/UPDATE/DELETE/QUERY/OTHER 等',
    method         VARCHAR(255)  DEFAULT NULL           COMMENT '请求方法（全限定类名.方法名）',
    request_uri    VARCHAR(255)  DEFAULT NULL           COMMENT '请求URI',
    http_method    VARCHAR(16)   DEFAULT NULL           COMMENT 'HTTP 方法：GET/POST/PUT/DELETE',
    target_type    VARCHAR(64)   DEFAULT NULL           COMMENT '操作对象类型，如 mu_song / mu_playlist',
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
-- 14. 后端接口日志表（whale-spring-boot-starter ApiLogFilter 采集，
--     结构与 bluewhale_auth.api_log 一致）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS api_log (
    id             BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    trace_id       VARCHAR(64)   DEFAULT NULL           COMMENT '链路追踪ID（跨服务调用串联）',
    user_id        BIGINT        DEFAULT NULL           COMMENT '操作用户ID（匿名访问为 NULL）',
    username       VARCHAR(64)   DEFAULT NULL           COMMENT '操作用户名',
    tenant_id      BIGINT        DEFAULT NULL           COMMENT '租户ID（网关可信注入）',
    module         VARCHAR(64)   DEFAULT NULL           COMMENT '业务模块，如 whale-music',
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
