-- =====================================================================
-- Wave 5.80 M3-5：MES 工艺路线接收表
-- =====================================================================
-- 适用库   : bluewhale_mes（MySQL 8.0+，utf8mb4）
-- 执行方式 : Flyway 自动执行；或 mysql -u<user> -p bluewhale_mes < V9__mes_process_route.sql
-- 幂等说明 : 新表 CREATE TABLE IF NOT EXISTS；本脚本不含 DROP / TRUNCATE / DELETE。
-- 契约来源 : whale-pdm 发布工艺路线后调用 POST /mes/internal/process-route，
--            以 pdm_route_no + version_no 幂等；撤回调用 POST /mes/internal/process-route/withdraw。
-- =====================================================================

CREATE TABLE IF NOT EXISTS mes_process_route (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户 ID',
    pdm_route_no    VARCHAR(32)  NOT NULL COMMENT 'PDM 工艺路线编号',
    version_no      VARCHAR(32)  NOT NULL COMMENT 'PDM 工艺路线版本号',
    part_code       VARCHAR(64)  NULL COMMENT '零件编码快照',
    part_name       VARCHAR(128) NULL COMMENT '零件名称快照',
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/WITHDRAWN',
    checksum        VARCHAR(64)  NULL COMMENT 'PDM 下发内容校验和',
    step_count      INT          NOT NULL DEFAULT 0 COMMENT '工序数量',
    payload         LONGTEXT     NULL COMMENT '完整下发内容（JSON）',
    pushed_at       DATETIME     NULL COMMENT 'PDM 下发时间',
    withdrawn_at    DATETIME     NULL COMMENT 'PDM 撤回时间',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mes_process_route (tenant_id, pdm_route_no, version_no),
    KEY idx_mes_process_route_status (tenant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MES 工艺路线接收记录';
