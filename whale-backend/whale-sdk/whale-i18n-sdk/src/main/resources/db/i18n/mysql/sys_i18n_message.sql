-- =====================================================================
-- whale-i18n-sdk 启动自动建表脚本（幂等）
-- =====================================================================
-- 说明：每个接入服务在自己的业务库中创建独立的 sys_i18n_message，
--       仅使用 CREATE TABLE IF NOT EXISTS，不删除数据、不写种子。
-- =====================================================================
CREATE TABLE IF NOT EXISTS sys_i18n_message (
    id              BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    message_key     VARCHAR(128) NOT NULL            COMMENT '消息编码，如 login.success',
    locale          VARCHAR(16)  NOT NULL            COMMENT '语言环境，如 zh_CN、en_US',
    message_content TEXT         NOT NULL            COMMENT '消息内容（对应语言环境的文案）',
    module          VARCHAR(64)  DEFAULT NULL        COMMENT '所属业务模块，便于分组管理与筛选',
    remark          VARCHAR(255) DEFAULT NULL        COMMENT '备注',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_key_locale (message_key, locale),
    KEY idx_locale (locale)
) COMMENT='国际化消息表';
