-- =====================================================================
-- Wave 5.80 M3：PDM 工艺路线（轻量）、工序、工装、SOP 与 MES 下发记录
-- =====================================================================
-- 适用库   : bluewhale_pdm（MySQL 8.0+，utf8mb4）
-- 幂等说明 : 新表 CREATE TABLE IF NOT EXISTS；本脚本不含 DROP / TRUNCATE / DELETE。
-- 边界     : PDM 只保存工艺路线业务事实；车间执行、报工、设备采集归 MES。
-- 状态机   : 路线 DRAFT → RELEASED → OBSOLETE；下发 SUCCESS/FAILED/WITHDRAWN
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 工艺路线
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pdm_prc_route (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
    route_no        VARCHAR(32)  NOT NULL COMMENT '工艺路线编号（租户内与版本组合唯一）',
    part_id         BIGINT       NOT NULL COMMENT '零件/产品ID',
    part_code       VARCHAR(64)  NOT NULL COMMENT '零件编码快照',
    part_name       VARCHAR(128) NULL COMMENT '零件名称快照',
    version_no      VARCHAR(32)  NOT NULL COMMENT '版本号，如 V1.0',
    version_seq     INT          NOT NULL COMMENT '版本排序值',
    status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/RELEASED/OBSOLETE',
    remark          VARCHAR(255) NULL COMMENT '备注',
    created_by      BIGINT       NULL COMMENT '创建人',
    released_by     BIGINT       NULL COMMENT '发布人',
    released_at     DATETIME     NULL COMMENT '发布时间',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pdm_prc_route_no_ver (tenant_id, route_no, version_no),
    UNIQUE KEY uk_pdm_prc_route_part_ver (tenant_id, part_id, version_no),
    KEY idx_pdm_prc_route_status (tenant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM 工艺路线';

-- ---------------------------------------------------------------------
-- 2. 工序与工时
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pdm_prc_step (
    id              BIGINT        NOT NULL COMMENT '主键',
    tenant_id       BIGINT        NOT NULL DEFAULT 1 COMMENT '租户ID',
    route_id        BIGINT        NOT NULL COMMENT '工艺路线ID',
    step_no         INT           NOT NULL COMMENT '工序序号（路线内唯一）',
    name            VARCHAR(128)  NOT NULL COMMENT '工序名称',
    work_center     VARCHAR(128)  NULL COMMENT '工作中心',
    standard_hours  DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '标准工时（小时）',
    setup_hours     DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '准备工时（小时）',
    description     VARCHAR(500)  NULL COMMENT '工序说明',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pdm_prc_step_no (tenant_id, route_id, step_no),
    KEY idx_pdm_prc_step_route (tenant_id, route_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM 工艺工序';

-- ---------------------------------------------------------------------
-- 3. 工装夹具
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pdm_prc_tooling (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
    route_id        BIGINT       NOT NULL COMMENT '工艺路线ID',
    step_id         BIGINT       NULL COMMENT '工序ID（为空表示路线级工装）',
    tooling_type    VARCHAR(16)  NOT NULL DEFAULT 'TOOL' COMMENT '类型：TOOL/FIXTURE/MOLD/GAUGE',
    tooling_code    VARCHAR(64)  NOT NULL COMMENT '工装编码',
    spec            VARCHAR(128) NULL COMMENT '规格',
    quantity        INT          NOT NULL DEFAULT 1 COMMENT '数量',
    remark          VARCHAR(255) NULL COMMENT '备注',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    KEY idx_pdm_prc_tooling_route (tenant_id, route_id),
    KEY idx_pdm_prc_tooling_step (tenant_id, step_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM 工艺工装夹具';

-- ---------------------------------------------------------------------
-- 4. SOP（只引用已发布文档版本）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pdm_prc_sop (
    id                   BIGINT       NOT NULL COMMENT '主键',
    tenant_id            BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
    route_id             BIGINT       NOT NULL COMMENT '工艺路线ID',
    step_id              BIGINT       NOT NULL COMMENT '工序ID',
    document_id          BIGINT       NOT NULL COMMENT '文档ID',
    document_version_no  VARCHAR(32)  NOT NULL COMMENT '文档版本号',
    document_title       VARCHAR(255) NULL COMMENT '文档标题快照',
    create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted              TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pdm_prc_sop_step (tenant_id, route_id, step_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM 工序 SOP 引用';

-- ---------------------------------------------------------------------
-- 5. MES 下发记录（幂等键 pdmRouteNo + version）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pdm_prc_publish (
    id               BIGINT       NOT NULL COMMENT '主键',
    tenant_id        BIGINT       NOT NULL DEFAULT 1 COMMENT '租户ID',
    route_id         BIGINT       NOT NULL COMMENT '工艺路线ID',
    route_no         VARCHAR(32)  NOT NULL COMMENT '工艺路线编号快照',
    version_no       VARCHAR(32)  NOT NULL COMMENT '版本号快照',
    status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：SUCCESS/FAILED/WITHDRAWN',
    mes_route_no     VARCHAR(64)  NULL COMMENT 'MES 侧路线编号',
    retry_count      INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    last_error       VARCHAR(500) NULL COMMENT '最近失败原因',
    idempotency_key  VARCHAR(80)  NOT NULL COMMENT '幂等键（routeNo:version）',
    pushed_at        DATETIME     NULL COMMENT '最近下发时间',
    withdrawn_at     DATETIME     NULL COMMENT '撤回时间',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pdm_prc_publish_key (tenant_id, idempotency_key),
    UNIQUE KEY uk_pdm_prc_publish_route (tenant_id, route_id),
    KEY idx_pdm_prc_publish_status (tenant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM 工艺路线 MES 下发记录';

-- =====================================================================
-- 完成。校验建议：
--   SELECT table_name FROM information_schema.tables
--   WHERE table_schema = DATABASE() AND table_name LIKE 'pdm_prc_%';
--   -- 期望：pdm_prc_route / pdm_prc_step / pdm_prc_tooling / pdm_prc_sop / pdm_prc_publish。
-- =====================================================================
