-- =============================================================================
-- 文件        : V18__social_binding.sql
-- 说明        : 权限中心扩展 M2 —— 三方登录绑定（钉钉 / 飞书 / 企业微信）
--
-- 背景：输入脚本中不存在「三方凭证」表（auth_tbl_user_credential 是证件而非第三方
--       身份），按计划 §1.2 决策新建 sys_user_social_binding：
--       ① (provider, provider_user_id) 全局唯一 —— 同一三方身份至多绑定一个账号；
--       ② 同一用户在同一 provider 下至多一条有效绑定；
--       ③ 解绑后（status=0）不得再作为登录入口。
--
-- 同时按计划 §1.2（auth_tbl_user_info 处置矩阵 M0/M2 行）为 sys_user 补
-- register_source / registered_at 两列：自动建号时落 SOCIAL 来源标记。
--
-- 本脚本（幂等，可安全重放）：
--   1. 建表 sys_user_social_binding；
--   2. sys_user 补列 register_source / registered_at；
--   3. 注册源登记键 sys:register:source:list（供租户配置层读取，无行级种子）。
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1. 三方登录绑定表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user_social_binding (
    id               BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT        NOT NULL                COMMENT '用户归属租户ID（逻辑关联 sys_tenant.id）',
    user_id          BIGINT        NOT NULL                COMMENT '绑定用户ID（逻辑关联 sys_user.id）',
    provider         VARCHAR(32)   NOT NULL                COMMENT '三方渠道：DINGTALK 钉钉 / FEISHU 飞书 / WECOM 企业微信',
    provider_user_id VARCHAR(128)  NOT NULL                COMMENT '三方用户唯一标识（openid/userid）',
    union_id         VARCHAR(128)  DEFAULT NULL            COMMENT '三方 unionId（跨应用唯一，可空）',
    nickname         VARCHAR(128)  DEFAULT NULL            COMMENT '三方昵称（绑定时的快照）',
    avatar           VARCHAR(512)  DEFAULT NULL            COMMENT '三方头像URL（绑定时的快照）',
    status           TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 绑定生效 / 0 已解绑（解绑后不得登录）',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_provider_user (provider, provider_user_id),
    KEY idx_tenant_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='三方登录绑定表';

-- ---------------------------------------------------------------------
-- 2. sys_user 补列：注册来源与注册时间（auth_tbl_user_info 处置矩阵 M0/M2）
--    register_source：SYSTEM 管理员创建 / IMPORT 批量导入（M4）/ SOCIAL 三方自动建号
-- ---------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'register_source');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN register_source VARCHAR(16) NOT NULL DEFAULT ''SYSTEM'' COMMENT ''注册来源：SYSTEM 管理员创建 / IMPORT 导入 / SOCIAL 三方自动建号'' AFTER last_login_time',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'registered_at');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN registered_at DATETIME DEFAULT NULL COMMENT ''注册时间（自动建号时写入；管理员创建为 NULL）'' AFTER register_source',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
