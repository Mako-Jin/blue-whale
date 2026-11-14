-- =====================================================================
-- BlueWhale whale-workflow 增量迁移脚本（Wave 5.38：定义版本快照 / 发布 / 实例迁移）
-- =====================================================================
-- 适用场景 : 已按 Wave 5.35 部署、库中已存在 process_definition / process_instance
--             表的运行库（保留存量流程定义与在途实例）。
-- 全新建库 : 请直接执行 init.sql，无需本脚本（init.sql 已包含本表）。
-- 说明     : 本脚本幂等（通过 information_schema.TABLES 存在性守卫，规避
--             MySQL 8.0 不支持 CREATE TABLE IF NOT EXISTS 与后续索引维护的冲突）。
--             新增表记录每次定义 JSON 变更的版本快照；实例发起时锁定
--             definition_version，审批与回退按快照解析，避免定义变更影响在途实例。
--             存量定义首次保存或发布时会补写快照，无需回填数据。
-- =====================================================================

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'CREATE TABLE process_definition_version ('
        'id BIGINT NOT NULL COMMENT ''主键ID（雪花算法）'', '
        'definition_id BIGINT NOT NULL COMMENT ''流程定义ID'', '
        'version INT NOT NULL COMMENT ''版本号（与定义表 version 对应）'', '
        'definition_json MEDIUMTEXT NULL COMMENT ''该版本的流程定义JSON快照'', '
        'publish_status TINYINT NOT NULL DEFAULT 0 COMMENT ''发布状态：0 草稿 / 1 已发布'', '
        'create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'', '
        'update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''更新时间'', '
        'deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''逻辑删除：0 未删除 / 1 已删除'', '
        'PRIMARY KEY (id), KEY idx_definition_version (definition_id, version)'
        ') COMMENT=''流程定义版本快照表''',
    'SELECT 1')
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_definition_version');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
