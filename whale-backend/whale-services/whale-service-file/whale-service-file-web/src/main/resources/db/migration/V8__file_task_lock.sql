-- =====================================================================
-- whale-file 批次 26：定时任务分布式互斥锁表
-- =====================================================================
-- 背景：回收站清理、存量版本清理、孤儿对账均为全量扫描任务，多实例部署时每个实例
--       都会同时执行，重复消耗数据库与存储资源。
-- 机制：租约（locked_until）+ 属主（owner）。抢锁为 INSERT ... ON DUPLICATE KEY UPDATE，
--       仅当锁不存在或租约过期时改写属主；释放按「锁名 + 属主」删除。
-- 幂等：CREATE TABLE IF NOT EXISTS，重复执行不报错。
-- =====================================================================
CREATE TABLE IF NOT EXISTS file_task_lock (
    lock_name    VARCHAR(64) NOT NULL               COMMENT '任务锁名称（全局唯一且稳定）',
    owner        VARCHAR(64) NOT NULL               COMMENT '持有者标识（随机串，用于校验归属）',
    locked_until DATETIME    NOT NULL               COMMENT '租约到期时间，过期后可被其他实例抢占',
    update_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (lock_name)
) COMMENT='定时任务分布式互斥锁表';
