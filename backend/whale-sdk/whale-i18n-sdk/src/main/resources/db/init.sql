-- =====================================================================
-- BlueWhale 微服务平台 - 国际化 SDK（whale-i18n-sdk）数据库初始化脚本
-- =====================================================================
-- 建议库名   : bluewhale_i18n
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci（完整支持多语言字符与 emoji）
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成）、create_time、update_time、
--              deleted TINYINT（逻辑删除：0 未删除 / 1 已删除）
-- 说明       : 本表供 I18nDatabaseProvider SPI 实现读取，
--              按 (message_key, locale) 定位唯一一条文案
-- =====================================================================

-- ---------------------------------------------------------------------
-- 国际化消息表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS sys_i18n_message;
CREATE TABLE sys_i18n_message (
    id              BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    message_key     VARCHAR(128) NOT NULL            COMMENT '消息编码，如 login.success',
    locale          VARCHAR(16)  NOT NULL            COMMENT '语言环境，如 zh_CN、en_US',
    message_content TEXT         NOT NULL            COMMENT '消息内容（对应语言环境的文案）',
    module          VARCHAR(64)  DEFAULT NULL        COMMENT '所属业务模块，便于分组管理与筛选',
    remark          VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_key_locale (message_key, locale),
    KEY idx_locale (locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='国际化消息表';
