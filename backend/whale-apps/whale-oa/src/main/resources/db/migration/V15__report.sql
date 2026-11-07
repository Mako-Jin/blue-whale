-- =====================================================================
-- BlueWhale 微服务平台 - OA 应用（whale-oa） Flyway 版本化迁移 V15
-- =====================================================================
-- 适用库   : bluewhale_oa（MySQL 8.0+，utf8mb4）
-- 里程碑   : Wave 5.82 M6 数据报表试点（9 类报表 + 月度快照 + CSV 导出）
-- 说明     : 新建报表快照与导出记录 2 张表。报表本体按请求只读聚合，
--            不复制业务表；快照保存某一期间的聚合结果，供复盘与报表直达。
--            导出记录仅登记行为（报表、期间、行数、操作人），文件本体不入库。
-- 幂等     : CREATE TABLE IF NOT EXISTS；无种子数据。
-- 规则     : 禁止 DROP / TRUNCATE / DELETE；表间逻辑外键，不建物理 FOREIGN KEY。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 报表快照（同一租户同一报表同一期间唯一，重跑覆盖）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_report_snapshot (
    id               BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    report_code      VARCHAR(64)  NOT NULL            COMMENT '报表编码',
    report_name      VARCHAR(128) NOT NULL            COMMENT '报表名称快照',
    period           VARCHAR(16)  NOT NULL            COMMENT '统计期间，格式 YYYY-MM',
    from_date        DATE         NOT NULL            COMMENT '起始日期',
    to_date          DATE         NOT NULL            COMMENT '截止日期',
    row_count        INT          NOT NULL DEFAULT 0  COMMENT '数据行数',
    payload          LONGTEXT     NOT NULL            COMMENT '快照内容（JSON：列定义与数据行）',
    generated_by     BIGINT       DEFAULT NULL        COMMENT '生成人用户ID',
    generated_by_name VARCHAR(64) DEFAULT NULL        COMMENT '生成人姓名快照',
    generated_at     DATETIME     NOT NULL            COMMENT '生成时间',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 否 / 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_report_snapshot (tenant_id, report_code, period),
    KEY idx_oa_report_snapshot_time (tenant_id, generated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'OA 报表快照';

-- ---------------------------------------------------------------------
-- 2. 报表导出记录（仅登记行为，不保存导出的文件本体）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS oa_report_export_log (
    id               BIGINT       NOT NULL            COMMENT '主键ID（雪花算法）',
    tenant_id        BIGINT       NOT NULL DEFAULT 1  COMMENT '租户ID（行级隔离）',
    report_code      VARCHAR(64)  NOT NULL            COMMENT '报表编码',
    report_name      VARCHAR(128) NOT NULL            COMMENT '报表名称快照',
    period           VARCHAR(16)  DEFAULT NULL        COMMENT '统计期间（月度快照导出时填写）',
    from_date        DATE         DEFAULT NULL        COMMENT '起始日期',
    to_date          DATE         DEFAULT NULL        COMMENT '截止日期',
    export_format    VARCHAR(16)  NOT NULL DEFAULT 'CSV' COMMENT '导出格式：CSV',
    row_count        INT          NOT NULL DEFAULT 0  COMMENT '导出行数',
    exported_by      BIGINT       DEFAULT NULL        COMMENT '导出人用户ID',
    exported_by_name VARCHAR(64)  DEFAULT NULL        COMMENT '导出人姓名快照',
    exported_at      DATETIME     NOT NULL            COMMENT '导出时间',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT      NOT NULL DEFAULT 0  COMMENT '逻辑删除：0 否 / 1 是',
    PRIMARY KEY (id),
    KEY idx_oa_report_export_log_time (tenant_id, exported_at),
    KEY idx_oa_report_export_log_code (tenant_id, report_code, exported_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'OA 报表导出记录';

-- =====================================================================
-- 完成。校验建议：
--   SHOW CREATE TABLE oa_report_snapshot;
--   SHOW CREATE TABLE oa_report_export_log;
-- =====================================================================
