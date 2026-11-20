package com.bluewhale.audit.retention.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计作业心跳实体，对应 {@code audit_job_heartbeat} 表（等保 2.0 8.1.4.3 b）。
 *
 * <p>解决的问题：归档与留存巡检挂在 {@code whale.audit.retention.cron} 上，默认每日 03:00。
 * 留存不达标可以用 {@code audit.log.oldest.days} 发现，但「作业根本没跑」这件事在指标上
 * 表现为「指标不更新」——和「一切正常」完全无法区分。本表把每次执行的开始/成功时间落库，
 * 由 {@code audit.job.stale.days{job=...}} 暴露「距上次成功多少天」。</p>
 *
 * <p>为什么落库而不是只放内存：内存计时器重启即清零，容器每晚重建的环境里，
 * 「两周没跑」会被洗成「刚跑过」，恰好把要发现的问题掩盖掉。</p>
 *
 * <p>主键即任务编码，一个任务一行；本表<b>不是</b> append-only，每次执行更新同一行。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("audit_job_heartbeat")
public class AuditJobHeartbeatPo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务编码（主键，如 {@code audit.log.retention}），由调用方显式指定，不生成雪花 */
    @TableId(type = IdType.INPUT)
    private String jobCode;

    /** 最近一次开始时间 */
    private LocalDateTime lastStartAt;

    /** 最近一次结束时间 */
    private LocalDateTime lastEndAt;

    /** 最近一次成功时间（陈旧度判定的基准，非成功执行不覆盖此列） */
    private LocalDateTime lastSuccessAt;

    /** 最近一次结果：{@code SUCCESS} / {@code FAILED} */
    private String lastStatus;

    /** 最近一次执行摘要或异常简述（超过 512 字符截断） */
    private String lastMessage;

    /** 累计执行次数（含失败） */
    private Long runCount;

    /** 累计失败次数 */
    private Long failCount;

    /** 本行更新时间（DB 侧 ON UPDATE 维护，应用侧不写） */
    private LocalDateTime updateTime;
}
