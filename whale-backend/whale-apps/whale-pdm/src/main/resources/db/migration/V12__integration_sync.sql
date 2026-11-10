-- =====================================================================
-- Wave 5.80 M5：CAD/ERP/MES/OA 集成同步记录与对账结果
-- =====================================================================
-- 适用库   : bluewhale_pdm（MySQL 8.0+，utf8mb4）
-- 幂等说明 : 新表 CREATE TABLE IF NOT EXISTS；本脚本不含 DROP / TRUNCATE / DELETE。
-- 边界     : 文件字节仍归 whale-file，ERP 物料与 MES 路线归各自服务；
--            PDM 只记录同步日志、对账结论与幂等键，不回写外部主数据。
-- 幂等键   : 同步日志按 (tenant_id, channel, idempotency_key) 唯一；
--            对账结果按 (tenant_id, channel, biz_type, biz_no, version_no) 唯一。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 集成同步日志（CAD 检入/EBOM 导入、ERP 物料/BOM、MES 路线、OA 回调）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pdm_int_sync_log (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
    channel         VARCHAR(16)  NOT NULL COMMENT '通道：CAD/ERP/MES/OA',
    action          VARCHAR(32)  NOT NULL COMMENT '动作：CAD_CHECKIN/CAD_EBOM_IMPORT/PART_UPSERT/BOM_PULL/BOM_NOTIFY/ROUTE_PUSH/OA_CALLBACK/OA_COMPENSATE',
    direction       VARCHAR(16)  NOT NULL COMMENT '方向：OUTBOUND/INBOUND',
    biz_type        VARCHAR(16)  NULL COMMENT '业务类型：PART/DOCUMENT/BOM/ROUTE/CHANGE',
    biz_id          BIGINT       NULL COMMENT '业务主键',
    biz_no          VARCHAR(64)  NULL COMMENT '业务编号快照',
    version_no      VARCHAR(32)  NULL COMMENT '版本号快照',
    idempotency_key VARCHAR(160) NOT NULL COMMENT '幂等键',
    status          VARCHAR(16)  NOT NULL COMMENT '状态：SUCCESS/FAILED',
    retry_count     INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    last_error      VARCHAR(500) NULL COMMENT '最近失败原因',
    payload         TEXT         NULL COMMENT '请求内容快照',
    external_ref    VARCHAR(128) NULL COMMENT '外部引用（ERP 物料ID/MES 路线ID等）',
    checksum        VARCHAR(64)  NULL COMMENT '同步内容校验和',
    operator_id     BIGINT       NULL COMMENT '发起人',
    requested_at    DATETIME     NULL COMMENT '发起时间',
    finished_at     DATETIME     NULL COMMENT '完成时间',
    create_time     DATETIME     NULL COMMENT '创建时间',
    update_time     DATETIME     NULL COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 / 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pdm_int_sync_key (tenant_id, channel, idempotency_key),
    KEY idx_pdm_int_sync_query (tenant_id, channel, status, create_time),
    KEY idx_pdm_int_sync_biz (tenant_id, biz_type, biz_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PDM 集成同步日志';

-- ---------------------------------------------------------------------
-- 2. 集成对账结果（BOM 拉取、MES 下发、OA 状态一致性）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pdm_int_reconcile (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
    channel         VARCHAR(16)  NOT NULL COMMENT '通道：ERP/MES/OA',
    biz_type        VARCHAR(16)  NOT NULL COMMENT '业务类型：BOM/ROUTE/CHANGE',
    biz_no          VARCHAR(64)  NOT NULL COMMENT '业务编号',
    biz_id          BIGINT       NULL COMMENT '业务主键（BOM发布ID/路线ID/变更ID）',
    version_no      VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '版本号，OA 场景为空串',
    local_status    VARCHAR(32)  NULL COMMENT 'PDM 侧状态',
    remote_status   VARCHAR(32)  NULL COMMENT '外部侧状态',
    local_checksum  VARCHAR(64)  NULL COMMENT 'PDM 校验和',
    remote_checksum VARCHAR(64)  NULL COMMENT '外部校验和（取自最近同步日志）',
    match_status    VARCHAR(16)  NOT NULL COMMENT '对账结论：MATCH/DIFF/MISSING',
    diff_summary    VARCHAR(500) NULL COMMENT '差异说明',
    checked_at      DATETIME     NULL COMMENT '对账时间',
    create_time     DATETIME     NULL COMMENT '创建时间',
    update_time     DATETIME     NULL COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否 / 1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pdm_int_reconcile (tenant_id, channel, biz_type, biz_no, version_no),
    KEY idx_pdm_int_reconcile_query (tenant_id, channel, match_status, checked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PDM 集成对账结果';
