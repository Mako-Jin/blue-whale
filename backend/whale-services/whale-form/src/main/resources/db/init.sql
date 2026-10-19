-- =====================================================================
-- BlueWhale 微服务平台 - 表单服务（whale-form）数据库初始化脚本
-- =====================================================================
-- 建议库名   : bluewhale_form
-- 数据库版本 : MySQL 8.0+
-- 字符集     : utf8mb4，排序规则 utf8mb4_general_ci
-- 存储引擎   : InnoDB
-- 公共字段   : id BIGINT（雪花算法生成）、create_time、update_time、
--              deleted TINYINT（逻辑删除：0 未删除 / 1 已删除）
-- 说明       : code 因逻辑删除无法建唯一索引，其业务唯一性由服务层前置校验保证
--              （存在则抛 BizException(PARAM_ERROR, "表单编码已存在")）；
--              schema_json 为表单结构定义 JSON，最长 64KB（65536 字符）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 表单定义表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS form_definition;
CREATE TABLE form_definition (
    id           BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    code         VARCHAR(64)   NOT NULL               COMMENT '表单编码（业务唯一，逻辑删除冲突由服务层前置校验）',
    name         VARCHAR(128)  NOT NULL               COMMENT '表单名称',
    schema_json  MEDIUMTEXT    NULL                   COMMENT '表单结构定义JSON（最长64KB）',
    version      INT           NOT NULL DEFAULT 1     COMMENT '版本号',
    status       TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 正常 / 0 禁用',
    remark       VARCHAR(255)  DEFAULT NULL           COMMENT '备注',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='表单定义表';

-- ---------------------------------------------------------------------
-- 表单数据表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS form_data;
CREATE TABLE form_data (
    id                  BIGINT        NOT NULL               COMMENT '主键ID（雪花算法）',
    definition_id       BIGINT        NOT NULL               COMMENT '表单定义ID',
    definition_code     VARCHAR(64)   NOT NULL               COMMENT '提交时的表单定义编码（冗余）',
    definition_version  INT           NOT NULL               COMMENT '提交时的表单定义版本（冗余）',
    biz_key             VARCHAR(64)   DEFAULT NULL           COMMENT '业务键（可选）',
    data_json           MEDIUMTEXT    NULL                   COMMENT '表单数据JSON（最长64KB）',
    submitter           VARCHAR(64)   DEFAULT NULL           COMMENT '提交人账号',
    status              TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 有效 / 0 作废',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT       NOT NULL DEFAULT 0     COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_definition_id (definition_id),
    KEY idx_biz_key (biz_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='表单数据表';
