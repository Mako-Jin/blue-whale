-- =====================================================================
-- BlueWhale - whale-workflow Flyway 增量 V11：注释口径修正与冗余索引清理
-- =====================================================================
-- 适用库   : bluewhale_workflow
-- 说明     : 1) V3 的 process_cc.recipient 注释仍为「账号或用户ID文本」，与实体统一账号口径不一致；
--              历史脚本已执行不可修改（checksum 校验），这里用 V11 覆盖列注释。
--            2) process_urge.window_bucket 保持可空：服务端写入必非空，唯一键对新增记录生效；
--              NULL 仅为 V8 存量去重残留，这里在列注释中固化该口径。
--            3) process_instance.idx_status 与 idx_status_finished(status, finished_at) 前缀重叠，
--              等值 status 查询可走后者，删除前者减少写放大。
-- 幂等     : MODIFY COLUMN 重复执行结果一致；删除索引前用 information_schema 判定。
-- =====================================================================

-- 1) 修正抄送收件人注释为账号口径（V3 已执行，不能直接改历史脚本）
ALTER TABLE process_cc
    MODIFY COLUMN recipient VARCHAR(64) NOT NULL
    COMMENT '收件人账号（发起级与节点级统一口径）';

-- 2) 固化频控时间窗的可空语义
ALTER TABLE process_urge
    MODIFY COLUMN window_bucket VARCHAR(16) DEFAULT NULL
    COMMENT '频控时间窗（创建时间截断到小时 yyyyMMddHH；服务端强制非空，NULL 仅为 V8 历史去重残留）';

-- 3) 删除与 idx_status_finished 前缀重叠的 idx_status
SET @has_idx_status := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'process_instance'
      AND INDEX_NAME = 'idx_status'
);
SET @ddl := IF(@has_idx_status > 0,
    'ALTER TABLE process_instance DROP INDEX idx_status',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
