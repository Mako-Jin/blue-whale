-- =====================================================================
-- BlueWhale 微服务平台 - 表单服务（whale-form） Flyway V15：附件引用表
-- =====================================================================
-- 背景   : 附件以文件 ID 数组落在 dataJson / 物理表列中，无法反查「某附件被哪些表单数据引用」。
-- 方案   : 新增 form_attachment_ref 作为附件反查索引，提交/修改/导入时同步维护，删除数据时清理；
--          附件反查接口按 (tenant_id, attachment_id) 查询。
-- 边界   : 该表只是索引，不存文件本身；历史存量数据需通过一次性回填脚本补齐（见安全加固文档）。
-- 幂等   : CREATE TABLE IF NOT EXISTS。
-- =====================================================================

CREATE TABLE IF NOT EXISTS form_attachment_ref (
    id             BIGINT       NOT NULL              COMMENT '主键ID（雪花算法）',
    tenant_id      BIGINT       NOT NULL DEFAULT 1    COMMENT '租户ID（行级隔离）',
    data_id        BIGINT       NOT NULL              COMMENT '表单数据ID',
    definition_code VARCHAR(64) NOT NULL              COMMENT '表单编码（冗余，便于按表单查询）',
    field_key      VARCHAR(64)  NOT NULL              COMMENT '附件字段编码',
    attachment_id  BIGINT       NOT NULL              COMMENT '文件ID',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_data_field_attachment (tenant_id, data_id, field_key, attachment_id),
    KEY idx_tenant_attachment (tenant_id, attachment_id),
    KEY idx_tenant_data (tenant_id, data_id)
) COMMENT='表单附件引用表（附件反查索引）';
