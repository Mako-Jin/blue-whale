-- =====================================================================
-- BlueWhale whale-form 增量迁移脚本（P3：表单版本快照与回滚）
-- =====================================================================
-- 适用场景 : 已部署表单服务的运行库；本脚本可重复执行（幂等）。
-- 全新建库 : 请直接执行 init.sql，无需本脚本。
-- 说明     : 新增 form_definition_version 表，记录表单定义各版本的
--            定义+字段+选项完整快照，供版本回滚使用。
-- =====================================================================

CREATE TABLE IF NOT EXISTS form_definition_version (
  id BIGINT NOT NULL COMMENT '主键ID（雪花算法）',
  definition_id BIGINT NOT NULL COMMENT '表单定义ID',
  version INT NOT NULL COMMENT '版本号',
  snapshot_json MEDIUMTEXT NOT NULL COMMENT '定义+字段+选项的完整快照JSON',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_definition_version (definition_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='表单定义版本快照表';
