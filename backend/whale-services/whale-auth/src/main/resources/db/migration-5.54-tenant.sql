-- =====================================================================
-- BlueWhale whale-auth 增量迁移脚本（Wave 5.54 Phase 0：最小租户模型）
-- =====================================================================
-- 适用场景 : 已按既有 Wave 部署、库中已存在 sys_user 表的运行库
--            （保留存量账号与授权数据，不重建表）。
-- 全新建库 : 请直接执行 init.sql，无需本脚本（init.sql 已同步租户表与 tenant_id）。
-- 说明     : 幂等（表/列/索引均先查 information_schema 再决定是否执行）。
--            · 新增 sys_tenant 租户表，并写入默认租户（id=1 / tenant_code=default）
--            · sys_user 增加 tenant_id（存量行统一回填 1）
--            · 唯一键 uk_username 升级为 uk_tenant_username(tenant_id, username)
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_tenant (
    id          BIGINT        NOT NULL                COMMENT '主键ID（雪花算法）',
    tenant_code VARCHAR(64)   NOT NULL                COMMENT '租户编码（唯一），登录时使用',
    tenant_name VARCHAR(128)  NOT NULL                COMMENT '租户名称',
    status      TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='租户表';

INSERT IGNORE INTO sys_tenant
    (id, tenant_code, tenant_name, status)
VALUES
    (1, 'default', '默认租户', 1);

-- 1. sys_user 增加 tenant_id（仅当不存在时执行）
SET @tenant_column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user'
      AND column_name = 'tenant_id'
);
SET @tenant_ddl := IF(@tenant_column_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT ''所属租户ID（逻辑关联 sys_tenant.id）'' AFTER id',
    'SELECT 1');
PREPARE tenant_stmt FROM @tenant_ddl;
EXECUTE tenant_stmt;
DEALLOCATE PREPARE tenant_stmt;

-- 2. 存量账号统一归属默认租户
UPDATE sys_user SET tenant_id = 1 WHERE tenant_id IS NULL;

-- 3. 删除旧的全库唯一键 uk_username（仅当存在时执行）
SET @old_uk_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user'
      AND index_name = 'uk_username'
);
SET @drop_uk_ddl := IF(@old_uk_exists > 0,
    'ALTER TABLE sys_user DROP INDEX uk_username',
    'SELECT 1');
PREPARE drop_uk_stmt FROM @drop_uk_ddl;
EXECUTE drop_uk_stmt;
DEALLOCATE PREPARE drop_uk_stmt;

-- 4. 新增租户内唯一键 uk_tenant_username（仅当不存在时执行）
SET @new_uk_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user'
      AND index_name = 'uk_tenant_username'
);
SET @add_uk_ddl := IF(@new_uk_exists = 0,
    'ALTER TABLE sys_user ADD UNIQUE KEY uk_tenant_username (tenant_id, username)',
    'SELECT 1');
PREPARE add_uk_stmt FROM @add_uk_ddl;
EXECUTE add_uk_stmt;
DEALLOCATE PREPARE add_uk_stmt;

-- 5. 租户索引（仅当不存在时执行）
SET @tenant_idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user'
      AND index_name = 'idx_tenant_id'
);
SET @add_idx_ddl := IF(@tenant_idx_exists = 0,
    'ALTER TABLE sys_user ADD KEY idx_tenant_id (tenant_id)',
    'SELECT 1');
PREPARE add_idx_stmt FROM @add_idx_ddl;
EXECUTE add_idx_stmt;
DEALLOCATE PREPARE add_idx_stmt;
