-- =====================================================================
-- Wave 5.80 M2-3：PDM 变更单（ECO）与变更任务
-- =====================================================================
-- 适用库   : bluewhale_pdm（MySQL 8.0+，utf8mb4）
-- 幂等说明 : 新表 CREATE TABLE IF NOT EXISTS；本脚本不含 DROP / TRUNCATE / DELETE。
-- 边界     : PDM 只保存 ECO 业务事实与执行任务；审批流转在 M2-5 对接 OA。
-- 状态机   : DRAFT → ASSESSING → APPROVING → APPROVED → EXECUTING → COMPLETED → CLOSED
--            旁支 REJECTED / CANCELLED
-- =====================================================================

CREATE TABLE IF NOT EXISTS pdm_chg_order (
    id                BIGINT        NOT NULL COMMENT '主键',
    tenant_id         BIGINT        NOT NULL COMMENT '租户ID',
    eco_no            VARCHAR(32)   NOT NULL COMMENT '变更单编号（租户内唯一）',
    ecr_id            BIGINT        NOT NULL COMMENT '来源变更申请ID',
    title             VARCHAR(200)  NOT NULL COMMENT '变更标题',
    impact_assessment TEXT          DEFAULT NULL COMMENT '影响评估',
    impact_json       LONGTEXT      DEFAULT NULL COMMENT '影响对象快照 JSON（复制自 ECR）',
    execute_plan      TEXT          DEFAULT NULL COMMENT '执行计划',
    status            VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/ASSESSING/APPROVING/APPROVED/EXECUTING/COMPLETED/CLOSED/REJECTED/CANCELLED',
    initiator_id      BIGINT        DEFAULT NULL COMMENT '发起人',
    submit_time       DATETIME      DEFAULT NULL COMMENT '提交时间',
    approve_by        BIGINT        DEFAULT NULL COMMENT '审批人',
    approve_time      DATETIME      DEFAULT NULL COMMENT '审批时间',
    approve_comment   VARCHAR(500)  DEFAULT NULL COMMENT '审批意见',
    execute_time      DATETIME      DEFAULT NULL COMMENT '执行开始时间',
    complete_time     DATETIME      DEFAULT NULL COMMENT '完成时间',
    close_time        DATETIME      DEFAULT NULL COMMENT '关闭时间',
    create_time       DATETIME      DEFAULT NULL COMMENT '创建时间',
    update_time       DATETIME      DEFAULT NULL COMMENT '更新时间',
    deleted           TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_chg_order_no (tenant_id, eco_no),
    KEY idx_chg_order_status (tenant_id, status, create_time),
    KEY idx_chg_order_ecr (tenant_id, ecr_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM 变更单（ECO）';

CREATE TABLE IF NOT EXISTS pdm_chg_task (
    id                BIGINT       NOT NULL COMMENT '主键',
    tenant_id         BIGINT       NOT NULL COMMENT '租户ID',
    eco_id            BIGINT       NOT NULL COMMENT '所属变更单ID',
    task_name         VARCHAR(200) NOT NULL COMMENT '任务名称',
    task_type         VARCHAR(32)  NOT NULL COMMENT '任务类型',
    target_type       VARCHAR(16)  DEFAULT NULL COMMENT '目标对象类型：DOCUMENT/BOM/PART',
    target_id         BIGINT       DEFAULT NULL COMMENT '目标对象ID',
    target_code       VARCHAR(64)  DEFAULT NULL COMMENT '目标对象编码',
    plan_desc         VARCHAR(500) DEFAULT NULL COMMENT '单任务执行说明',
    owner_id          BIGINT       DEFAULT NULL COMMENT '负责人',
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/COMPLETED',
    result_version_no VARCHAR(32)  DEFAULT NULL COMMENT '执行结果版本号（V{major}.{minor}）',
    sort_no           INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    complete_time     DATETIME     DEFAULT NULL COMMENT '完成时间',
    create_time       DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_time       DATETIME     DEFAULT NULL COMMENT '更新时间',
    deleted           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_chg_task_eco (tenant_id, eco_id, status),
    KEY idx_chg_task_sort (tenant_id, eco_id, sort_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM 变更任务';

-- =====================================================================
-- 完成。校验建议：
--   SHOW CREATE TABLE pdm_chg_order;
--   SHOW CREATE TABLE pdm_chg_task;
--   -- 期望：状态默认 DRAFT/PENDING，uk_chg_order_no 与 4 个查询索引存在。
-- =====================================================================
