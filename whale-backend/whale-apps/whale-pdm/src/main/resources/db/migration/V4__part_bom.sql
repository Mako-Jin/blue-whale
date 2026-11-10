-- =====================================================================
-- Wave 5.80 M1A：PDM 零件主档 + EBOM 结构 + 发布快照
-- =====================================================================
-- 适用库   : bluewhale_pdm（MySQL 8.0+，utf8mb4）
-- 幂等说明 : 扩展列逐列检查 information_schema，缺失才 ADD COLUMN；
--            新表统一 CREATE TABLE IF NOT EXISTS；种子 INSERT IGNORE。
--            本脚本不含 DROP / TRUNCATE / DELETE。
-- 兼容     : 存量 pdm_product / api_log 与旧 /pdm/product/** 行为不变。
-- 边界     : 采购/生产 BOM 归 ERP；本表只存设计 EBOM 与发布快照。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 扩展 pdm_product 为零件/产品主档（逐列幂等）
-- ---------------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pdm_product' AND COLUMN_NAME = 'part_type');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE pdm_product ADD COLUMN part_type VARCHAR(16) NOT NULL DEFAULT ''PART'' COMMENT ''零件类型：PRODUCT/ASSEMBLY/PART/RAW_MATERIAL'' AFTER name',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pdm_product' AND COLUMN_NAME = 'material');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE pdm_product ADD COLUMN material VARCHAR(64) DEFAULT NULL COMMENT ''材质'' AFTER part_type',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pdm_product' AND COLUMN_NAME = 'spec');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE pdm_product ADD COLUMN spec VARCHAR(128) DEFAULT NULL COMMENT ''规格'' AFTER material',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pdm_product' AND COLUMN_NAME = 'uom');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE pdm_product ADD COLUMN uom VARCHAR(16) NOT NULL DEFAULT ''PCS'' COMMENT ''计量单位'' AFTER spec',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pdm_product' AND COLUMN_NAME = 'category_code');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE pdm_product ADD COLUMN category_code VARCHAR(64) DEFAULT NULL COMMENT ''物料分类编码'' AFTER uom',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pdm_product' AND COLUMN_NAME = 'erp_item_code');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE pdm_product ADD COLUMN erp_item_code VARCHAR(64) DEFAULT NULL COMMENT ''ERP 物料编码映射'' AFTER category_code',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pdm_product' AND COLUMN_NAME = 'source_type');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE pdm_product ADD COLUMN source_type VARCHAR(16) NOT NULL DEFAULT ''UNIQUE'' COMMENT ''来源类型：STANDARD/COMMON/UNIQUE'' AFTER erp_item_code',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. 物料分类树
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pdm_part_category (
    id          BIGINT       NOT NULL COMMENT '主键',
    tenant_id   BIGINT       NOT NULL COMMENT '租户ID',
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '上级分类ID，0 表示根',
    code        VARCHAR(64)  NOT NULL COMMENT '分类编码（租户内唯一）',
    name        VARCHAR(128) NOT NULL COMMENT '分类名称',
    path        VARCHAR(512) NOT NULL COMMENT '分类路径，如 /1/2/',
    sort_no     INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    remark      VARCHAR(255) DEFAULT NULL COMMENT '备注',
    create_time DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME     DEFAULT NULL COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_part_category_code (tenant_id, code),
    UNIQUE KEY uk_part_category_name (tenant_id, parent_id, name),
    KEY idx_part_category_path (tenant_id, path)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM 物料分类';

-- ---------------------------------------------------------------------
-- 3. BOM 头（一个 bom_no 下多版本，每版本一行）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pdm_bom (
    id           BIGINT       NOT NULL COMMENT '主键',
    tenant_id    BIGINT       NOT NULL COMMENT '租户ID',
    bom_no       VARCHAR(64)  NOT NULL COMMENT 'BOM 编号（租户内业务键）',
    product_id   BIGINT       NOT NULL COMMENT '成品/部件零件ID（pdm_product.id）',
    product_code VARCHAR(64)  NOT NULL COMMENT '产品编码快照',
    view         VARCHAR(16)  NOT NULL DEFAULT 'EBOM' COMMENT '视图：EBOM/PBOM/MBOM',
    version_no   VARCHAR(32)  NOT NULL COMMENT '版本号，如 V1.0 / V2.0',
    version_seq  INT          NOT NULL COMMENT '版本排序值，major*10000+minor',
    status       VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/RELEASED/OBSOLETE',
    remark       VARCHAR(255) DEFAULT NULL COMMENT '备注',
    create_time  DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_time  DATETIME     DEFAULT NULL COMMENT '更新时间',
    deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bom_no_version (tenant_id, bom_no, version_no),
    KEY idx_bom_product (tenant_id, product_id, view, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM BOM 头';

-- ---------------------------------------------------------------------
-- 4. BOM 行（父件 -> 子件边）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pdm_bom_line (
    id             BIGINT        NOT NULL COMMENT '主键',
    tenant_id      BIGINT        NOT NULL COMMENT '租户ID',
    bom_id         BIGINT        NOT NULL COMMENT 'BOM 头ID',
    parent_part_id BIGINT        NOT NULL COMMENT '父件ID',
    child_part_id  BIGINT        NOT NULL COMMENT '子件ID',
    line_no        INT           NOT NULL COMMENT '同级行序号',
    qty            DECIMAL(18,6) NOT NULL COMMENT '用量',
    uom            VARCHAR(16)   NOT NULL DEFAULT 'PCS' COMMENT '单位',
    loss_rate      DECIMAL(9,4)  NOT NULL DEFAULT 0 COMMENT '损耗率（0-100）',
    remark         VARCHAR(255)  DEFAULT NULL COMMENT '备注',
    create_time    DATETIME      DEFAULT NULL COMMENT '创建时间',
    update_time    DATETIME      DEFAULT NULL COMMENT '更新时间',
    deleted        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bom_line_no (tenant_id, bom_id, parent_part_id, line_no),
    UNIQUE KEY uk_bom_line_child (tenant_id, bom_id, parent_part_id, child_part_id),
    KEY idx_bom_line_parent (tenant_id, bom_id, parent_part_id),
    KEY idx_bom_line_child (tenant_id, child_part_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM BOM 行';

-- ---------------------------------------------------------------------
-- 5. BOM 发布快照（不可变）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pdm_bom_release (
    id           BIGINT      NOT NULL COMMENT '主键',
    tenant_id    BIGINT      NOT NULL COMMENT '租户ID',
    bom_id       BIGINT      NOT NULL COMMENT 'BOM 头ID',
    bom_no       VARCHAR(64) NOT NULL COMMENT 'BOM 编号',
    version_no   VARCHAR(32) NOT NULL COMMENT '版本号',
    product_code VARCHAR(64) NOT NULL COMMENT '产品编码',
    view         VARCHAR(16) NOT NULL DEFAULT 'EBOM' COMMENT '视图',
    checksum     CHAR(64)    NOT NULL COMMENT '稳定 JSON 的 SHA-256',
    line_count   INT         NOT NULL DEFAULT 0 COMMENT '快照行数',
    payload      LONGTEXT    NOT NULL COMMENT '稳定 JSON 快照',
    released_by  BIGINT      DEFAULT NULL COMMENT '发布人',
    released_at  DATETIME    DEFAULT NULL COMMENT '发布时间',
    create_time  DATETIME    DEFAULT NULL COMMENT '创建时间',
    update_time  DATETIME    DEFAULT NULL COMMENT '更新时间',
    deleted      TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bom_release (tenant_id, bom_no, version_no, checksum),
    KEY idx_bom_release_no (tenant_id, bom_no, version_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM BOM 发布快照';

-- ---------------------------------------------------------------------
-- 6. 种子分类（平台租户）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO pdm_part_category
(id, tenant_id, parent_id, code, name, path, sort_no, remark, create_time, update_time, deleted)
VALUES
(1, 1, 0, 'STD', '标准件', '/1/', 10, '螺栓/螺母/轴承等标准件', NOW(), NOW(), 0),
(2, 1, 0, 'COMMON', '通用件', '/2/', 20, '公司内部重复使用零件', NOW(), NOW(), 0),
(3, 1, 0, 'RAW', '原材料', '/3/', 30, '板材/型材/棒料等原材料', NOW(), NOW(), 0);

-- =====================================================================
-- 完成。校验建议：
--   SHOW COLUMNS FROM pdm_product LIKE 'part%';
--   -- 期望：出现 part_type / category_code 等新列。
--   SELECT COUNT(*) FROM pdm_part_category WHERE tenant_id = 1;
--   -- 期望：3。
-- =====================================================================
