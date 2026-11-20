-- =====================================================================
-- Wave 5.48 / AUTH-09：审计日志归档水位表（增量迁移，幂等可重跑）
-- =====================================================================
-- 背景：audit_log 为 append-only 表，归档任务只导出不删除。若无水位，
--       每轮 cron 都会把同一批「早于保留期」的记录重复导出、重复上传。
-- 用途：记录上一轮成功归档批次的最后一条 create_time + id，下一轮增量续查。
-- 库   ：bluewhale_audit
-- =====================================================================

CREATE TABLE IF NOT EXISTS audit_archive_watermark (
    id               BIGINT   NOT NULL               COMMENT '固定单行主键，恒为 1',
    last_create_time DATETIME DEFAULT NULL           COMMENT '已归档批次的最后一条 audit_log.create_time',
    last_id          BIGINT   DEFAULT NULL           COMMENT '已归档批次的最后一条 audit_log.id（同一 create_time 内去重）',
    update_time      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '水位更新时间',
    PRIMARY KEY (id)
) COMMENT='审计日志归档水位表';
