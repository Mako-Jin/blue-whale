-- =====================================================================
-- Wave 5.80 M2-2：PDM 变更申请（ECR）
-- =====================================================================
-- 适用库   : bluewhale_pdm（MySQL 8.0+，utf8mb4）
-- 幂等说明 : 新表 CREATE TABLE IF NOT EXISTS；本脚本不含 DROP / TRUNCATE / DELETE。
-- 边界     : PDM 只保存变更业务事实；审批流转在 M2-5 对接 OA。
-- 状态机   : DRAFT → SUBMITTED → APPROVED / REJECTED → CLOSED
-- =====================================================================

CREATE TABLE IF NOT EXISTS pdm_chg_request (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       NOT NULL COMMENT '租户ID',
    change_no       VARCHAR(32)  NOT NULL COMMENT '变更申请编号（租户内唯一）',
    change_reason   VARCHAR(1000) NOT NULL COMMENT '变更原因',
    change_scope    VARCHAR(500) DEFAULT NULL COMMENT '变更范围',
    impact_json     LONGTEXT     DEFAULT NULL COMMENT '影响对象 JSON（[{objectType,objectId,objectCode,objectName}]）',
    status          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/SUBMITTED/APPROVED/REJECTED/CLOSED',
    initiator_id    BIGINT       DEFAULT NULL COMMENT '发起人',
    submit_time     DATETIME     DEFAULT NULL COMMENT '提交时间',
    approve_by      BIGINT       DEFAULT NULL COMMENT '审批人',
    approve_time    DATETIME     DEFAULT NULL COMMENT '审批时间',
    approve_comment VARCHAR(500) DEFAULT NULL COMMENT '审批意见',
    close_time      DATETIME     DEFAULT NULL COMMENT '关闭时间',
    create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_time     DATETIME     DEFAULT NULL COMMENT '更新时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_chg_request_no (tenant_id, change_no),
    KEY idx_chg_request_status (tenant_id, status, create_time),
    KEY idx_chg_request_initiator (tenant_id, initiator_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM 变更申请（ECR）';

-- =====================================================================
-- 完成。校验建议：
--   SHOW CREATE TABLE pdm_chg_request;
--   -- 期望：状态默认 DRAFT，uk_chg_request_no 与两个查询索引存在。
-- =====================================================================
