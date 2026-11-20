-- V4: 审计作业心跳表（等保 2.0 8.1.4.3 b：审计记录应受到保护，避免未预期的删除、修改或覆盖）
--
-- 背景：归档 / 留存巡检挂在 whale.audit.retention.cron 上，默认每日 03:00 触发。这一层
-- 「作业有没有在跑」此前完全没有证据：留存不达标可以靠 audit.log.oldest.days 发现，
-- 但如果整个作业被停掉、cron 被写错、容器一直重启导致调度从未触发，指标只是「不更新」——
-- 而不更新和「一切正常」在 Prometheus 里长得一模一样。
--
-- 解法：作业每跑一次就把 last_start_at / last_success_at 落到本表，再用 Gauge
-- audit.job.stale.days{job="audit.log.retention"} 暴露「距上次成功多少天」。
-- 落库而不是只放内存：重启即清零的内存计时器在容器环境里会把「两周没跑」洗成「刚跑过」。
--
-- 幂等：CREATE TABLE IF NOT EXISTS，新环境按 V1→V4 顺序执行、存量库单跑、
--      手工 db/init.sql 建库后再跑本脚本，三条路径都不会报已存在。
--
-- 注意：本表<b>不是</b> append-only，每次执行都要更新同一行，因此
--      scripts/security/check-audit-append-only.py 的禁令范围不含它（只含 audit_log 与四张通道表）。

CREATE TABLE IF NOT EXISTS audit_job_heartbeat (
    job_code        VARCHAR(64)  NOT NULL                COMMENT '任务编码，如 audit.log.retention',
    last_start_at   DATETIME(3)  DEFAULT NULL            COMMENT '最近一次开始时间',
    last_end_at     DATETIME(3)  DEFAULT NULL            COMMENT '最近一次结束时间',
    last_success_at DATETIME(3)  DEFAULT NULL            COMMENT '最近一次成功时间（陈旧度判定基准）',
    last_status     VARCHAR(16)  DEFAULT NULL            COMMENT '最近一次结果 SUCCESS / FAILED',
    last_message    VARCHAR(512) DEFAULT NULL            COMMENT '最近一次执行摘要或异常简述（截断 512）',
    run_count       BIGINT       NOT NULL DEFAULT 0      COMMENT '累计执行次数',
    fail_count      BIGINT       NOT NULL DEFAULT 0      COMMENT '累计失败次数',
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                 ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '本行更新时间',
    PRIMARY KEY (job_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='审计作业心跳表：作业存活证据，供陈旧度告警';
