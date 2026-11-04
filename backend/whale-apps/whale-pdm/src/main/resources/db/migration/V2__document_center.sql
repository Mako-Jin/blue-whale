-- Wave 5.80 M0：PDM 图文档中心
-- 说明：文件字节仍由 whale-file 保存，本表只存文档元数据与版本引用。
-- 兼容：存量 pdm_product / api_log 不改动；旧 /pdm/product/** 行为保持不变。

CREATE TABLE IF NOT EXISTS pdm_doc_folder (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '上级目录ID，0 表示根目录',
    name VARCHAR(128) NOT NULL COMMENT '目录名称',
    path VARCHAR(512) NOT NULL COMMENT '目录路径，如 /1/2/',
    folder_type VARCHAR(32) NOT NULL DEFAULT 'CUSTOM' COMMENT '目录类型：PRODUCT/PROJECT/DOC_TYPE/CUSTOM',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    create_time DATETIME DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME DEFAULT NULL COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_folder_name (tenant_id, parent_id, name),
    KEY idx_doc_folder_parent (tenant_id, parent_id),
    KEY idx_doc_folder_path (tenant_id, path)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM 图文档目录';

CREATE TABLE IF NOT EXISTS pdm_doc_document (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    folder_id BIGINT NOT NULL COMMENT '所属目录',
    doc_no VARCHAR(64) NOT NULL COMMENT '文档编号',
    title VARCHAR(255) NOT NULL COMMENT '文档标题',
    doc_type VARCHAR(32) NOT NULL COMMENT '文档类型：DRAWING/DOC/SPEC/REPORT',
    part_no VARCHAR(64) DEFAULT NULL COMMENT '关联零件号',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/RELEASED',
    owner_id BIGINT NOT NULL COMMENT '创建人/设计人',
    owner_name VARCHAR(64) DEFAULT NULL COMMENT '创建人/设计人姓名快照',
    description VARCHAR(500) DEFAULT NULL COMMENT '说明',
    tags VARCHAR(255) DEFAULT NULL COMMENT '标签，逗号分隔',
    latest_version_id BIGINT DEFAULT NULL COMMENT '最新版本ID（含草稿）',
    latest_version_no VARCHAR(32) DEFAULT NULL COMMENT '最新版本号',
    current_version_id BIGINT DEFAULT NULL COMMENT '当前正式版本ID',
    checked_out_by BIGINT DEFAULT NULL COMMENT '检出人',
    checked_out_time DATETIME DEFAULT NULL COMMENT '检出时间',
    create_time DATETIME DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME DEFAULT NULL COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_no (tenant_id, doc_no),
    KEY idx_doc_folder (tenant_id, folder_id, doc_type, status),
    KEY idx_doc_part (tenant_id, part_no),
    KEY idx_doc_owner (tenant_id, owner_id, update_time),
    KEY idx_doc_title (tenant_id, title)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM 图文档主档';

CREATE TABLE IF NOT EXISTS pdm_doc_version (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    document_id BIGINT NOT NULL COMMENT '文档ID',
    version_no VARCHAR(32) NOT NULL COMMENT '版本号，如 V0.1 / V1.0 / V1.1',
    version_seq INT NOT NULL COMMENT '版本排序值，major*10000+minor',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/RELEASED/OBSOLETE',
    file_id BIGINT NOT NULL COMMENT 'whale-file 文件ID',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名',
    file_size BIGINT DEFAULT NULL COMMENT '文件大小（字节）',
    content_type VARCHAR(128) DEFAULT NULL COMMENT '内容类型',
    checksum VARCHAR(64) DEFAULT NULL COMMENT 'SHA-256 校验和',
    change_note VARCHAR(500) DEFAULT NULL COMMENT '变更说明',
    source VARCHAR(16) NOT NULL DEFAULT 'UPLOAD' COMMENT '来源：UPLOAD/CHECKIN/ROLLBACK',
    created_by BIGINT DEFAULT NULL COMMENT '创建人',
    released_by BIGINT DEFAULT NULL COMMENT '发布人',
    released_at DATETIME DEFAULT NULL COMMENT '发布时间',
    create_time DATETIME DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME DEFAULT NULL COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_version_no (tenant_id, document_id, version_no),
    KEY idx_doc_version_doc (tenant_id, document_id, status, version_seq)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM 图文档版本';

CREATE TABLE IF NOT EXISTS pdm_doc_acl (
    id BIGINT NOT NULL COMMENT '主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    resource_type VARCHAR(16) NOT NULL COMMENT '资源类型：FOLDER/DOCUMENT',
    resource_id BIGINT NOT NULL COMMENT '资源ID',
    subject_type VARCHAR(16) NOT NULL COMMENT '主体类型：USER/ROLE/ALL',
    subject_id BIGINT NOT NULL DEFAULT 0 COMMENT '主体ID，ALL 时为 0',
    action VARCHAR(16) NOT NULL COMMENT '动作：VIEW/EDIT/DOWNLOAD/DELETE/MANAGE',
    effect VARCHAR(8) NOT NULL COMMENT '效果：ALLOW/DENY',
    create_time DATETIME DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME DEFAULT NULL COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_acl (tenant_id, resource_type, resource_id, subject_type, subject_id, action, effect),
    KEY idx_doc_acl_resource (tenant_id, resource_type, resource_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM 图文档权限（M0 建表，M0-B 启用）';

-- 平台租户默认目录；其他租户首次访问时由服务自动初始化
INSERT IGNORE INTO pdm_doc_folder
(id, tenant_id, parent_id, name, path, folder_type, sort_no, remark, create_time, update_time, deleted)
VALUES
(1, 1, 0, '产品图纸', '/1/', 'PRODUCT', 10, '按产品/项目归档的图纸目录', NOW(), NOW(), 0),
(2, 1, 0, '技术文档', '/2/', 'DOC_TYPE', 20, '技术说明、规范与手册', NOW(), NOW(), 0),
(3, 1, 0, '测试报告', '/3/', 'CUSTOM', 30, '测试与验证报告', NOW(), NOW(), 0);
