-- =====================================================================
-- BlueWhale 微服务平台 - 工作流服务（whale-workflow）数据库初始化脚本
-- =====================================================================
-- 建议库名   : bluewhale_workflow
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成）、create_time、update_time、
--              deleted TINYINT（逻辑删除：0 未删除 / 1 已删除）
-- 说明       : code 因逻辑删除无法建唯一索引，其业务唯一性由服务层前置校验保证
--              （存在则抛 BizException(PARAM_ERROR, "流程编码已存在")）；
--              definition_json 为流程定义 JSON（节点与流转），最长 64KB（65536 字符）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 流程定义表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS process_definition;
CREATE TABLE process_definition (
    id               BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    code             VARCHAR(64)   NOT NULL               COMMENT '流程编码（业务唯一，逻辑删除冲突由服务层前置校验）',
    name             VARCHAR(128)  NOT NULL               COMMENT '流程名称',
    definition_json  MEDIUMTEXT    NULL                   COMMENT '流程定义JSON（节点与流转，最长64KB）',
    version          INT           NOT NULL DEFAULT 1     COMMENT '版本号',
    status           TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 正常 / 0 禁用',
    remark           VARCHAR(255)  DEFAULT NULL           COMMENT '备注',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程定义表';
