-- =====================================================================
-- BlueWhale 微服务平台 - 表单服务（whale-form） Flyway V4：发布可靠性与 DDL 审计
-- =====================================================================
-- 背景   : MySQL 的 CREATE/ALTER 会隐式提交，发布事务无法保护「DDL 已生效、登记写入失败」
--          的场景。为此补充失败原因列（便于登记置为失败后排查）与 DDL 审计表（逐条留痕）。
-- 幂等   : 列与表均先判定存在性再创建，可重复执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 登记表：最近一次发布失败原因
-- ---------------------------------------------------------------------
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'form_table_registry'
        AND COLUMN_NAME = 'last_error') = 0,
    'ALTER TABLE form_table_registry ADD COLUMN last_error VARCHAR(500) DEFAULT NULL AFTER status',
    'SELECT 1');
PREPARE form_v4_stmt FROM @ddl;
EXECUTE form_v4_stmt;
DEALLOCATE PREPARE form_v4_stmt;

-- ---------------------------------------------------------------------
-- DDL 审计表：只追加，不参与逻辑删除
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS form_ddl_audit (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    tenant_id       BIGINT       DEFAULT NULL COMMENT '租户ID',
    definition_type VARCHAR(32)  NOT NULL COMMENT '定义类型：FORM 旧模型 / TABLE 元数据模型',
    definition_id   BIGINT       NOT NULL COMMENT '定义ID',
    physical_table  VARCHAR(128) NOT NULL COMMENT '物理表名',
    ddl_statement   TEXT         NOT NULL COMMENT '实际执行的 DDL 语句',
    success         TINYINT      NOT NULL DEFAULT 1 COMMENT '是否成功：1 成功 / 0 失败',
    error_message   VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
    operator        VARCHAR(64)  DEFAULT NULL COMMENT '操作人',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_ddl_audit_table (tenant_id, physical_table),
    KEY idx_ddl_audit_definition (definition_type, definition_id),
    KEY idx_ddl_audit_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '表单物理表 DDL 审计';
