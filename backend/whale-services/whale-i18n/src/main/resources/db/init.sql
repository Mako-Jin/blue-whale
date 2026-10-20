-- =====================================================================
-- BlueWhale 微服务平台 - 国际化消息服务（whale-i18n）数据库初始化脚本
-- =====================================================================
-- 库名       : bluewhale_i18n
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci
-- 存储引擎   : InnoDB
-- 说明       : 表结构与 whale-i18n-sdk 的 db/init.sql 逐字段一致，
--              供 I18nDatabaseProvider 读取 + 本服务的消息 CRUD 接口读写。
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

-- ---------------------------------------------------------------------
-- 种子文案（便于接口冒烟与三级回退演示）
-- ---------------------------------------------------------------------
INSERT INTO sys_i18n_message (id, message_key, locale, message_content, module, remark)
VALUES
    (1, 'login.success', 'zh_CN', '登录成功', 'auth', '内置种子'),
    (2, 'login.success', 'en_US', 'Login succeeded', 'auth', '内置种子');
