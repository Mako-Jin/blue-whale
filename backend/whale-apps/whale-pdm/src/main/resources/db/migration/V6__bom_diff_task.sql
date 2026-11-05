-- =====================================================================
-- Wave 5.80 M1B-2：PDM BOM 大规模异步对比任务
-- =====================================================================
-- 适用库   : bluewhale_pdm（MySQL 8.0+，utf8mb4）
-- 幂等说明 : 新表 CREATE TABLE IF NOT EXISTS；本脚本不含 DROP / TRUNCATE / DELETE。
-- 兼容     : 同步对比 GET /pdm/bom/diff 行为与 2000 行上限保持不变；
--            超限场景由前端创建异步任务后轮询结果。
-- 边界     : 对比只读，任务结果仅存 JSON，不回写 BOM 结构。
-- =====================================================================

CREATE TABLE IF NOT EXISTS pdm_bom_diff_task (
    id            BIGINT       NOT NULL COMMENT '主键',
    tenant_id     BIGINT       NOT NULL COMMENT '租户ID',
    base_bom_id   BIGINT       NOT NULL COMMENT '基线 BOM ID',
    target_bom_id BIGINT       NOT NULL COMMENT '目标 BOM ID',
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/SUCCESS/FAILED',
    result_json   LONGTEXT     DEFAULT NULL COMMENT '对比结果 JSON（PdmBomDiffVO）',
    error_message VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
    created_by    BIGINT       DEFAULT NULL COMMENT '创建人',
    create_time   DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_time   DATETIME     DEFAULT NULL COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 1 已删',
    PRIMARY KEY (id),
    KEY idx_bom_diff_task_created (tenant_id, created_by, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PDM BOM 异步对比任务';

-- =====================================================================
-- 完成。校验建议：
--   SHOW CREATE TABLE pdm_bom_diff_task;
--   -- 期望：状态默认 PENDING，result_json 为 LONGTEXT，索引 idx_bom_diff_task_created 存在。
-- =====================================================================
