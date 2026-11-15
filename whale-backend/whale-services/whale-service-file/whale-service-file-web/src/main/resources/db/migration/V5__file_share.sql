-- =====================================================================
-- whale-file 批次 4：文件分享链接
-- =====================================================================
-- 背景：文件缺少对外分享能力，只能由登录用户逐个下载；分享需要过期、次数与密码控制。
-- 说明：令牌由服务端 32 字节随机数生成并持久化，需撤销/计数/密码校验，故使用服务端状态表，
--       不使用无状态签名令牌；tenant_id 由租户拦截器写入与过滤。
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行。
-- =====================================================================
CREATE TABLE IF NOT EXISTS file_share (
    id             BIGINT       NOT NULL               COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT       NOT NULL DEFAULT 1     COMMENT '租户ID（行级隔离）',
    file_id        BIGINT       NOT NULL               COMMENT '被分享的文件ID（逻辑关联 file_info.id）',
    file_name      VARCHAR(255) NOT NULL               COMMENT '创建时的原始文件名快照',
    token          VARCHAR(64)  NOT NULL               COMMENT '分享令牌（32 字节随机数的 base64url）',
    password_hash  VARCHAR(255) DEFAULT NULL           COMMENT '分享密码哈希；空表示无密码',
    expire_time    DATETIME     DEFAULT NULL           COMMENT '失效时间；空表示长期有效',
    max_downloads  INT          DEFAULT NULL           COMMENT '下载次数上限；空表示不限',
    download_count INT          NOT NULL DEFAULT 0     COMMENT '已下载次数',
    status         TINYINT      NOT NULL DEFAULT 1     COMMENT '状态：1 有效 / 0 已撤销',
    creator_id     BIGINT       DEFAULT NULL           COMMENT '创建人用户ID',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted        TINYINT      NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_share_token (token),
    KEY idx_file_share_file (file_id),
    KEY idx_file_share_creator (creator_id, create_time)
) COMMENT='文件分享链接';
