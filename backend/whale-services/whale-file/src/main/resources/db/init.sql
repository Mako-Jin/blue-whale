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
    file_name     VARCHAR(255)  NOT NULL               COMMENT '存储文件名（系统生成的唯一名称）',
    original_name VARCHAR(255)  NOT NULL               COMMENT '原始文件名（用户上传时的文件名）',
    bucket        VARCHAR(64)   NOT NULL               COMMENT '对象存储桶名称',
    object_key    VARCHAR(512)  NOT NULL               COMMENT '对象存储键（桶内完整路径）',
    file_size     BIGINT        NOT NULL DEFAULT 0     COMMENT '文件大小（字节）',
    content_type  VARCHAR(128)  DEFAULT NULL           COMMENT '文件MIME类型，如 image/png',
    md5           VARCHAR(32)   DEFAULT NULL           COMMENT '文件MD5摘要（用于去重/秒传/完整性校验）',
    status        TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 正常 / 0 禁用',
    uploader_id   BIGINT        DEFAULT NULL           COMMENT '上传用户ID（逻辑关联 sys_user.id）',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_md5 (md5),
    KEY idx_uploader_id (uploader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文件信息表';
