package com.bluewhale.job.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 执行日志统计视图对象：按天趋势 + 失败/耗时 TopN。
 *
 * @author BlueWhale
 */
@Data
public class JobLogStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 实际统计开始时间（含） */
    private LocalDateTime from;

    /** 实际统计结束时间（含） */
    private LocalDateTime to;

    /** 是否因样本上限被截断（true 时均值与 P95 为近似值） */
    private boolean sampled;

    /** 窗口内参与统计的执行总数 */
    private long totalCount;

    /** 按天聚合（日期升序） */
    private List<JobLogDailyStatVO> daily;

    /** 失败次数 Top10（指定 jobId 时为空） */
    private List<JobFailureTopVO> failureTop;

    /** 平均耗时 Top10（指定 jobId 时为空） */
    private List<JobDurationTopVO> durationTop;
}
